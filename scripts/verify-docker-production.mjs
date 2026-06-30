#!/usr/bin/env node

import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
let deliveryDir = "deploy/docker";
let strictConfigFile = "";

for (let index = 0; index < args.length; index += 1) {
  const arg = args[index];
  if (arg === "--strict-config") {
    strictConfigFile = args[index + 1] || "";
    index += 1;
  } else if (arg.startsWith("--")) {
    fail(`unknown option: ${arg}`);
  } else {
    deliveryDir = arg;
  }
}

function fail(message) {
  console.error(`docker-production-check: ${message}`);
  process.exit(1);
}

function info(message) {
  console.log(`docker-production-check: ${message}`);
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function readRelative(filePath) {
  const absolutePath = path.resolve(repoRoot, filePath);
  assert(existsSync(absolutePath), `${filePath} must exist`);
  return readFileSync(absolutePath, "utf8");
}

function assertIncludes(text, needle, message) {
  assert(text.includes(needle), message);
}

function hasExactLine(text, expectedLine) {
  return text.split(/\r?\n/u).includes(expectedLine);
}

function hasLinePrefix(text, prefix) {
  return text.split(/\r?\n/u).some((line) => line.startsWith(prefix));
}

function serviceBlock(compose, serviceName) {
  const marker = `  ${serviceName}:\n`;
  const start = compose.indexOf(marker);
  assert(start >= 0, `compose.yaml must define service ${serviceName}`);
  const bodyStart = start + marker.length;
  const next = compose.slice(bodyStart).search(/\n  [A-Za-z0-9_-]+:\n|\nnetworks:\n|\nvolumes:\n/u);
  return next >= 0 ? compose.slice(start, bodyStart + next) : compose.slice(start);
}

function serviceNames(compose) {
  const match = compose.match(/^services:\n([\s\S]*?)\nnetworks:\n/mu);
  assert(match, "compose.yaml must define services before networks");
  return [...match[1].matchAll(/^  ([A-Za-z0-9_-]+):\s*$/gmu)].map((item) => item[1]);
}

function assertEnvironmentKey(block, key) {
  assert(hasLinePrefix(block, `      ${key}:`), `crest-core-service environment must include ${key}`);
}

function containsPlaceholder(value) {
  return /<[^>]+>|CHANGE_ME|changeme|change-me|example(?:\.|$)|sample|demo|template|placeholder/iu.test(String(value || ""));
}

function localhostOrPlaceholder(value) {
  return /(localhost|127\.0\.0\.1|0\.0\.0\.0|CHANGE_ME|change-me|example|<[^>]+>)/iu.test(String(value || ""));
}

function parseEnvFile(filePath) {
  const absolutePath = path.resolve(repoRoot, filePath);
  assert(existsSync(absolutePath), `strict config env file must exist: ${filePath}`);
  const result = {};
  const raw = readFileSync(absolutePath, "utf8");
  raw.split(/\r?\n/u).forEach((line, index) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      return;
    }
    assert(!/^export\s+/u.test(trimmed), `${filePath}:${index + 1} must use KEY=value format, not export`);
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$/u);
    assert(match, `${filePath}:${index + 1} must use KEY=value format`);
    let value = match[2].trim();
    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    result[match[1]] = value;
  });
  return result;
}

function requireEnv(env, key) {
  assert(Object.prototype.hasOwnProperty.call(env, key) && env[key] !== "", `strict config must set ${key}`);
  assert(!containsPlaceholder(env[key]), `${key} must not contain placeholders or example values`);
  return env[key];
}

function requireLength(env, key, minLength) {
  const value = requireEnv(env, key);
  assert(value.length >= minLength, `${key} must be at least ${minLength} characters`);
  assert(!/(password|changeme|123456|admin|secret)/iu.test(value), `${key} must not use weak or descriptive secret text`);
  return value;
}

function parseHttpsOrigins(value, key) {
  const origins = String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  assert(origins.length > 0, `${key} must contain at least one HTTPS origin`);
  for (const origin of origins) {
    let url;
    try {
      url = new URL(origin);
    } catch {
      fail(`${key} must contain valid origins`);
    }
    assert(url.protocol === "https:", `${key} must use https origins`);
    assert(!url.pathname || url.pathname === "/", `${key} origins must not include paths`);
    assert(!url.search && !url.hash, `${key} origins must not include query strings or fragments`);
    assert(!localhostOrPlaceholder(url.hostname), `${key} must not use localhost, wildcard, or placeholder hosts`);
  }
}

function validateImageReference(value, key) {
  assert(!containsPlaceholder(value), `${key} must not contain placeholders`);
  assert(!/(^|:)latest($|@)/iu.test(value), `${key} must not use latest`);
  assert(!/(local-check|snapshot|dev|test)$/iu.test(value), `${key} must not use local, dev, test, or snapshot tags`);
  const lastSlash = value.lastIndexOf("/");
  const lastColon = value.lastIndexOf(":");
  const hasDigest = /@sha256:[0-9a-f]{64}$/iu.test(value);
  const hasTag = lastColon > lastSlash && lastColon < value.length - 1;
  assert(hasDigest || hasTag, `${key} must include an immutable release tag or sha256 digest`);
}

function validateHostPortList(value, key) {
  const nodes = String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  assert(nodes.length >= 3, `${key} must contain at least 3 Redis Cluster nodes`);
  assert(new Set(nodes).size >= 3, `${key} must contain at least 3 distinct Redis Cluster nodes`);
  for (const node of nodes) {
    const match = node.match(/^([A-Za-z0-9.-]+):([0-9]{2,5})$/u);
    assert(match, `${key} node must be host:port: ${node}`);
    assert(!localhostOrPlaceholder(match[1]), `${key} node must not use localhost or placeholders: ${node}`);
    const port = Number(match[2]);
    assert(Number.isInteger(port) && port > 0 && port <= 65535, `${key} node port is invalid: ${node}`);
  }
}

function redisHashTag(value, key) {
  const match = String(value || "").match(/\{([^}]+)\}/u);
  assert(match?.[1], `${key} must use a Redis Cluster hash tag like {org-env-crest-core}:prod`);
  return match[1];
}

function validateRedisNamespace(hashTag, key) {
  assert(/^[a-z0-9][a-z0-9._-]{7,63}$/u.test(hashTag), `${key} hash tag must be an 8-64 character lowercase namespace`);
  assert(hashTag.includes("crest-core"), `${key} hash tag must include crest-core for shared Redis isolation`);
  const reserved = new Set([
    "app",
    "application",
    "cache",
    "crest",
    "crest-core",
    "dataease",
    "default",
    "prod",
    "production",
    "redis",
    "shared",
    "system",
  ]);
  assert(!reserved.has(hashTag), `${key} hash tag is too generic for shared Redis`);
  assert(!/^(acme-|example-|sample-)|changeme|change-me|example|sample|demo|template|placeholder/u.test(hashTag),
    `${key} hash tag looks like an example value`);
}

function validateRedisAclUser(value, key) {
  assert(value !== "default", `${key} must not use the shared default Redis user`);
  assert(/^[a-z0-9][a-z0-9._-]{7,63}$/u.test(value), `${key} must be an 8-64 character lowercase environment-specific ACL user`);
  assert(value.includes("crest-core"), `${key} must include crest-core for shared Redis isolation`);
  const reserved = new Set([
    "app",
    "application",
    "cache",
    "crest",
    "crest-core",
    "dataease",
    "prod",
    "production",
    "redis",
    "shared",
    "system",
  ]);
  assert(!reserved.has(value), `${key} is too generic for shared Redis`);
  assert(!/^(acme-|example-|sample-)|changeme|change-me|example|sample|demo|template|placeholder/u.test(value),
    `${key} looks like an example value`);
}

function validateStrictConfig(env) {
  const requiredKeys = [
    "CREST_BACKEND_IMAGE",
    "CREST_FRONTEND_IMAGE",
    "CREST_ORIGIN_LIST",
    "CREST_DB_HOST",
    "CREST_DB_PORT",
    "CREST_DB_URL",
    "CREST_DB_USERNAME",
    "CREST_DB_PASSWORD",
    "CREST_AES_KEY",
    "CREST_AES_IV",
    "CREST_INITIAL_PASSWORD",
    "CREST_TOKEN_SECRET",
    "CREST_REDIS_CLUSTER_NODES",
    "CREST_REDIS_USERNAME",
    "CREST_REDIS_PASSWORD",
    "CREST_REDIS_KEY_PREFIX",
    "CREST_REDIS_CACHE_KEY_PREFIX",
    "CREST_LOCK_KEY_PREFIX",
    "CREST_WEBSOCKET_BROADCAST_CHANNEL",
    "CREST_EXPORT_TASK_STREAM",
    "CREST_EXPORT_TASK_CONSUMER_GROUP",
    "CREST_SYNC_TASK_STREAM",
    "CREST_SYNC_TASK_CONSUMER_GROUP",
    "CREST_DATASOURCE_SYNC_TASK_STREAM",
    "CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP",
    "CREST_SCHEDULED_TASK_STREAM",
    "CREST_SCHEDULED_TASK_CONSUMER_GROUP",
  ];
  for (const key of requiredKeys) {
    requireEnv(env, key);
  }

  validateImageReference(env.CREST_BACKEND_IMAGE, "CREST_BACKEND_IMAGE");
  validateImageReference(env.CREST_FRONTEND_IMAGE, "CREST_FRONTEND_IMAGE");
  parseHttpsOrigins(env.CREST_ORIGIN_LIST, "CREST_ORIGIN_LIST");

  assert(!localhostOrPlaceholder(env.CREST_DB_HOST), "CREST_DB_HOST must not use localhost or placeholders");
  assert(/^jdbc:oceanbase:\/\//u.test(env.CREST_DB_URL), "CREST_DB_URL must be an OceanBase JDBC URL");
  assert(!localhostOrPlaceholder(env.CREST_DB_URL), "CREST_DB_URL must not use localhost or placeholders");
  const dbPort = Number(env.CREST_DB_PORT);
  assert(Number.isInteger(dbPort) && dbPort > 0 && dbPort <= 65535, "CREST_DB_PORT must be a valid TCP port");

  requireLength(env, "CREST_DB_PASSWORD", 16);
  assert(env.CREST_AES_KEY.length === 32, "CREST_AES_KEY must be exactly 32 characters");
  assert(env.CREST_AES_IV.length === 16, "CREST_AES_IV must be exactly 16 characters");
  requireLength(env, "CREST_INITIAL_PASSWORD", 12);
  requireLength(env, "CREST_TOKEN_SECRET", 32);
  requireLength(env, "CREST_REDIS_PASSWORD", 16);

  validateHostPortList(env.CREST_REDIS_CLUSTER_NODES, "CREST_REDIS_CLUSTER_NODES");
  validateRedisAclUser(env.CREST_REDIS_USERNAME, "CREST_REDIS_USERNAME");

  const scopedKeys = [
    "CREST_REDIS_KEY_PREFIX",
    "CREST_REDIS_CACHE_KEY_PREFIX",
    "CREST_LOCK_KEY_PREFIX",
    "CREST_WEBSOCKET_BROADCAST_CHANNEL",
    "CREST_EXPORT_TASK_STREAM",
    "CREST_EXPORT_TASK_CONSUMER_GROUP",
    "CREST_SYNC_TASK_STREAM",
    "CREST_SYNC_TASK_CONSUMER_GROUP",
    "CREST_DATASOURCE_SYNC_TASK_STREAM",
    "CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP",
    "CREST_SCHEDULED_TASK_STREAM",
    "CREST_SCHEDULED_TASK_CONSUMER_GROUP",
  ];
  const tags = scopedKeys.map((key) => redisHashTag(env[key], key));
  const namespace = tags[0];
  validateRedisNamespace(namespace, scopedKeys[0]);
  for (let index = 1; index < tags.length; index += 1) {
    assert(tags[index] === namespace, `${scopedKeys[index]} must share Redis hash tag {${namespace}}`);
  }

  if (Object.prototype.hasOwnProperty.call(env, "CREST_HTTP_BIND")) {
    assert(["127.0.0.1", "0.0.0.0"].includes(env.CREST_HTTP_BIND), "CREST_HTTP_BIND must be 127.0.0.1 or 0.0.0.0");
  }
  if (Object.prototype.hasOwnProperty.call(env, "CREST_HTTP_PORT")) {
    const httpPort = Number(env.CREST_HTTP_PORT);
    assert(Number.isInteger(httpPort) && httpPort > 0 && httpPort <= 65535, "CREST_HTTP_PORT must be a valid TCP port");
  }
}

const composePath = path.join(deliveryDir, "compose.yaml");
const nginxPath = path.join(deliveryDir, "nginx/default.conf");
const exampleEnvPath = path.join(deliveryDir, "production.env.example");
const compose = readRelative(composePath);
const nginx = readRelative(nginxPath);
const exampleEnv = readRelative(exampleEnvPath);

assert(JSON.stringify(serviceNames(compose)) === JSON.stringify(["crest-core-service", "crest-core-web"]),
  "compose.yaml must define exactly two services: crest-core-service and crest-core-web");
for (const banned of ["redis", "redis-cluster", "oceanbase", "ob", "mysql", "postgres", "postgresql", "oracle"]) {
  assert(!hasExactLine(compose, `  ${banned}:`), `compose.yaml must not start external dependency service ${banned}`);
}
assert(!/^\s*container_name:/mu.test(compose), "compose.yaml must not use container_name because crest-core-service must scale");

const backend = serviceBlock(compose, "crest-core-service");
const frontend = serviceBlock(compose, "crest-core-web");

for (const [name, block] of [["crest-core-service", backend], ["crest-core-web", frontend]]) {
  assertIncludes(block, "restart: unless-stopped", `${name} must restart unless stopped`);
  assertIncludes(block, "init: true", `${name} must run with init`);
  assertIncludes(block, 'user: "10001:10001"', `${name} must run as the non-root crest user`);
  assertIncludes(block, "read_only: true", `${name} must use a read-only root filesystem`);
  assertIncludes(block, "cap_drop:", `${name} must drop Linux capabilities`);
  assertIncludes(block, "- ALL", `${name} must drop all Linux capabilities`);
  assertIncludes(block, "no-new-privileges:true", `${name} must disable privilege escalation`);
  assertIncludes(block, "healthcheck:", `${name} must define a healthcheck`);
  assertIncludes(block, "pids_limit:", `${name} must define pids_limit`);
  assertIncludes(block, "mem_limit:", `${name} must define mem_limit`);
  assertIncludes(block, "cpus:", `${name} must define cpus`);
  assertIncludes(block, "tmpfs:", `${name} must put runtime scratch paths on tmpfs`);
}

assertIncludes(backend, "$${CREST_WORKER_ID:-$$(hostname)}", "crest-core-service must derive CREST_WORKER_ID from container hostname by default");
assertIncludes(backend, "expose:", "crest-core-service must expose only the internal backend port");
assert(!/^    ports:/mu.test(backend), "crest-core-service must not publish host ports directly");
assertIncludes(backend, "crest-core-data:/opt/crest/data", "crest-core-service must persist business data in crest-core-data volume");
assertIncludes(backend, "crest-core-service-logs:/opt/crest/logs", "crest-core-service must persist service logs in a named volume");

for (const key of [
  "CREST_RUNTIME_ROLE",
  "CREST_PRODUCTION_MODE",
  "CREST_LOAD_DEMO",
  "CREST_FLYWAY_ENABLED",
  "CREST_INTERNAL_LITE_ENABLED",
  "CREST_API_DOCS_ENABLED",
  "CREST_KNIFE4J_ENABLED",
  "CREST_FEATURE_AI_ENABLED",
  "CREST_FEATURE_SQLBOT_ENABLED",
  "CREST_FEATURE_TEMPLATE_MARKET_ENABLED",
  "CREST_FEATURE_FONT_MANAGEMENT_ENABLED",
  "CREST_FEATURE_VISUALIZATION_BACKGROUND_ENABLED",
  "CREST_ALLOWED_DATASOURCE_TYPES",
  "CREST_DB_TYPE",
  "CREST_DB_DRIVER_CLASS_NAME",
  "CREST_DB_HOST",
  "CREST_DB_PORT",
  "CREST_DB_URL",
  "CREST_DB_USERNAME",
  "CREST_DB_PASSWORD",
  "CREST_AES_KEY",
  "CREST_AES_IV",
  "CREST_INITIAL_PASSWORD",
  "CREST_TOKEN_SECRET",
  "CREST_REDIS_CLUSTER_NODES",
  "CREST_REDIS_USERNAME",
  "CREST_REDIS_PASSWORD",
  "CREST_REDIS_DATABASE",
  "CREST_REDIS_KEY_PREFIX",
  "CREST_REDIS_CACHE_KEY_PREFIX",
  "CREST_LOCK_KEY_PREFIX",
  "CREST_WEBSOCKET_BROADCAST_CHANNEL",
  "CREST_TASK_QUEUE_ENABLED",
  "CREST_EXPORT_TASK_STREAM",
  "CREST_EXPORT_TASK_CONSUMER_GROUP",
  "CREST_SYNC_TASK_STREAM",
  "CREST_SYNC_TASK_CONSUMER_GROUP",
  "CREST_DATASOURCE_SYNC_TASK_STREAM",
  "CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP",
  "CREST_SCHEDULED_TASK_STREAM",
  "CREST_SCHEDULED_TASK_CONSUMER_GROUP",
  "CREST_WEBSOCKET_BROADCAST_ENABLED",
  "CREST_QUARTZ_CLUSTERED",
  "CREST_QUARTZ_INSTANCE_ID",
  "CREST_HEALTH_REDIS_ENABLED",
  "CREST_DATASOURCE_POOL_PRELOAD_ENABLED",
]) {
  assertEnvironmentKey(backend, key);
}

assertIncludes(frontend, 'condition: service_healthy', "crest-core-web must wait for crest-core-service readiness");
assertIncludes(frontend, "ports:", "crest-core-web must publish the single HTTP entrypoint");
assertIncludes(frontend, "${CREST_HTTP_BIND:-127.0.0.1}:${CREST_HTTP_PORT:-8080}:8080",
  "crest-core-web must bind to localhost by default and use a configurable HTTP port");
assertIncludes(frontend, "./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro",
  "crest-core-web must use the Docker-specific Nginx config");

assertIncludes(nginx, "resolver 127.0.0.11", "Docker Nginx config must use Docker DNS");
assertIncludes(nginx, "upstream crest_core_backend", "Docker Nginx config must define a fixed backend upstream");
assertIncludes(nginx, "server crest-core-service:8100;", "Docker Nginx config must target crest-core-service by DNS name");
assertIncludes(nginx, "charset utf-8;", "Docker Nginx config must force UTF-8 to prevent Chinese mojibake");
assertIncludes(nginx, "location ^~ /api/v1/", "Docker Nginx config must proxy API requests");
assertIncludes(nginx, "location /websocket", "Docker Nginx config must proxy websocket requests");
assertIncludes(nginx, "location ^~ /api/v1/actuator/", "Docker Nginx config must hide actuator endpoints");
assertIncludes(nginx, "return 404;", "Docker Nginx config must return 404 for hidden locations");
assertIncludes(nginx, "location ~* \\.map$", "Docker Nginx config must block source map files");
assertIncludes(nginx, "proxy_set_header Host crest-core-service;", "Docker Nginx config must not forward client-controlled Host values");
assertIncludes(nginx, "proxy_pass http://crest_core_backend;", "Docker Nginx config must preserve full request URIs when proxying");

assert(!/^export\s+/mu.test(exampleEnv), "production.env.example must use KEY=value format without export");
for (const key of [
  "CREST_BACKEND_IMAGE",
  "CREST_FRONTEND_IMAGE",
  "CREST_HTTP_BIND",
  "CREST_HTTP_PORT",
  "CREST_ORIGIN_LIST",
  "CREST_DB_HOST",
  "CREST_DB_PORT",
  "CREST_DB_URL",
  "CREST_DB_USERNAME",
  "CREST_DB_PASSWORD",
  "CREST_REDIS_CLUSTER_NODES",
  "CREST_REDIS_USERNAME",
  "CREST_REDIS_PASSWORD",
  "CREST_REDIS_KEY_PREFIX",
  "CREST_REDIS_CACHE_KEY_PREFIX",
  "CREST_LOCK_KEY_PREFIX",
  "CREST_WEBSOCKET_BROADCAST_CHANNEL",
]) {
  assert(hasLinePrefix(exampleEnv, `${key}=`), `production.env.example must document ${key}`);
}

if (strictConfigFile) {
  validateStrictConfig(parseEnvFile(strictConfigFile));
}

info(strictConfigFile ? "passed with strict config" : "passed");
