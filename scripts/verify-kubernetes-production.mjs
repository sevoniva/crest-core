#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const strictConfig = args.includes("--strict-config") || process.env.CREST_K8S_STRICT_CONFIG === "true";
const overlay = args.find((arg) => !arg.startsWith("--")) || "deploy/kubernetes";

function fail(message) {
  console.error(`k8s-check: ${message}`);
  process.exit(1);
}

function info(message) {
  console.log(`k8s-check: ${message}`);
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function containsPlaceholder(value) {
  return typeof value === "string" && /CHANGE_ME|change-me|<[^>]+>/iu.test(value);
}

function localhostOrPlaceholder(value) {
  return /(localhost|127\.0\.0\.1|CHANGE_ME|change-me)/iu.test(value || "");
}

function parseHttpsOrigins(value, resourceName) {
  const origins = String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  assert(origins.length > 0, `${resourceName} must contain at least one origin`);
  return origins.map((origin) => {
    let url;
    try {
      url = new URL(origin);
    } catch {
      fail(`${resourceName} must contain valid https origins`);
    }
    assert(url.protocol === "https:", `${resourceName} must use https origins in strict config mode`);
    assert(!url.pathname || url.pathname === "/", `${resourceName} origins must not include paths`);
    assert(!url.search && !url.hash, `${resourceName} origins must not include query strings or fragments`);
    assert(!localhostOrPlaceholder(url.hostname), `${resourceName} must not use localhost or placeholder hosts`);
    return url;
  });
}

function validateNoPlaceholders(resourceName, values) {
  for (const [key, value] of Object.entries(values || {})) {
    assert(!containsPlaceholder(value), `${resourceName} ${key} must not contain placeholder values in strict config mode`);
  }
}

function redisHashTag(value, resourceName) {
  const match = String(value || "").match(/\{([^}]+)\}/u);
  assert(match?.[1], `${resourceName} must use a Redis Cluster hash tag like {<org>-<env>-crest-core}:prod`);
  return match[1];
}

function validateRedisNamespace(hashTag, resourceName, { rejectExamples = false } = {}) {
  assert(/^[a-z0-9][a-z0-9._-]{7,63}$/u.test(hashTag),
    `${resourceName} hash tag must be an 8-64 character lowercase namespace`);
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
  assert(!reserved.has(hashTag),
    `${resourceName} hash tag is too generic for shared Redis; use an environment-specific namespace`);
  const looksLikeExample = hashTag === "acme-crest-core-prod"
    || /changeme|change-me|example|sample|demo|template|placeholder/u.test(hashTag);
  assert(!rejectExamples || !looksLikeExample,
    `${resourceName} hash tag looks like an example value; replace it with a real organization/environment namespace`);
}

function validateRedisAclUser(user, resourceName, { rejectExamples = false } = {}) {
  assert(user !== "default",
    `${resourceName} must not use the shared default Redis user in strict config mode`);
  assert(/^[a-z0-9][a-z0-9._-]{7,63}$/u.test(user),
    `${resourceName} must be an 8-64 character lowercase environment-specific ACL user`);
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
  assert(!reserved.has(user),
    `${resourceName} is too generic for shared Redis; use an environment-specific ACL user`);
  const looksLikeExample = user === "acme-crest-production-acl-user"
    || /changeme|change-me|example|sample|demo|template|placeholder/u.test(user);
  assert(!rejectExamples || !looksLikeExample,
    `${resourceName} looks like an example value; replace it with a real organization/environment ACL user`);
}

function secretValue(secret, key) {
  if (Object.prototype.hasOwnProperty.call(secret?.stringData || {}, key)) {
    return secret.stringData[key];
  }
  if (Object.prototype.hasOwnProperty.call(secret?.data || {}, key)) {
    try {
      return Buffer.from(secret.data[key], "base64").toString("utf8");
    } catch {
      fail(`${secret.metadata?.name || "Secret"} ${key} must be valid base64 when using data`);
    }
  }
  return undefined;
}

function secretHasKey(secret, key) {
  return Object.prototype.hasOwnProperty.call(secret?.stringData || {}, key)
    || Object.prototype.hasOwnProperty.call(secret?.data || {}, key);
}

function requireSecretText(secret, key, minLength) {
  const value = secretValue(secret, key);
  assert(typeof value === "string" && value.length >= minLength,
    `${secret.metadata?.name || "Secret"} ${key} must be at least ${minLength} characters in strict config mode`);
  assert(!containsPlaceholder(value),
    `${secret.metadata?.name || "Secret"} ${key} must not contain placeholder values in strict config mode`);
  return value;
}

function renderOverlay() {
  execFileSync("kubectl", ["create", "--dry-run=client", "--validate=false", "-f", overlay], {
    cwd: repoRoot,
    stdio: ["ignore", "ignore", "pipe"],
  });

  const overlayPath = path.resolve(repoRoot, overlay);
  const files = manifestFiles(overlayPath);
  assert(files.length > 0, `${overlay} must contain Kubernetes YAML files`);
  if (files.length > 1) {
    execFileSync("ruby", [
      "-ryaml",
      "-e",
      "ARGV.each { |f| docs = YAML.load_stream(File.read(f)).compact; abort(\"#{f} must contain exactly one Kubernetes resource\") unless docs.length == 1 }",
      ...files,
    ], {
      cwd: repoRoot,
      stdio: ["ignore", "ignore", "pipe"],
    });
  }
  const json = execFileSync("ruby", [
    "-ryaml",
    "-rjson",
    "-e",
    "items = ARGV.flat_map { |f| YAML.load_stream(File.read(f)).compact }; puts({kind: 'List', items: items}.to_json)",
    ...files,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  return JSON.parse(json);
}

function manifestFiles(targetPath) {
  const stat = statSync(targetPath);
  if (stat.isFile()) {
    return yamlFile(targetPath) ? [targetPath] : [];
  }
  return readdirSync(targetPath)
    .flatMap((name) => manifestFiles(path.join(targetPath, name)))
    .sort();
}

function yamlFile(filePath) {
  return filePath.endsWith(".yaml") || filePath.endsWith(".yml");
}

function byKindAndName(items, kind, name) {
  return items.find((item) => item.kind === kind && item.metadata?.name === name);
}

function allByKind(items, kind) {
  return items.filter((item) => item.kind === kind);
}

function container(workload) {
  return workload?.spec?.template?.spec?.containers?.[0];
}

function envMap(containerSpec) {
  return Object.fromEntries((containerSpec?.env || []).map((item) => [item.name, item]));
}

function envValue(containerSpec, name) {
  return envMap(containerSpec)[name]?.value;
}

function envFromRef(containerSpec, type, name) {
  return (containerSpec?.envFrom || []).find((item) => item[type]?.name === name);
}

function requireEnvFrom(containerSpec, type, name, owner) {
  const ref = envFromRef(containerSpec, type, name);
  assert(ref, `${owner} must load ${name}`);
  assert(ref[type]?.optional !== true, `${owner} must require ${name}`);
}

function volumeByName(workload, name) {
  return (workload?.spec?.template?.spec?.volumes || []).find((item) => item.name === name);
}

function hasVolume(workload, name, type) {
  const volume = volumeByName(workload, name);
  return Boolean(volume?.[type]);
}

function hasEmptyDir(workload, name) {
  return hasVolume(workload, name, "emptyDir");
}

function hasVolumeMount(containerSpec, name, mountPath) {
  return (containerSpec?.volumeMounts || []).some((item) => item.name === name && item.mountPath === mountPath);
}

function checkWritableEmptyDir(workload, containerSpec, workloadName, volumeName, mountPath) {
  const volume = volumeByName(workload, volumeName);
  assert(volume?.emptyDir, `${workloadName} must define writable emptyDir ${volumeName}`);
  assert(volume.emptyDir.sizeLimit, `${workloadName} emptyDir ${volumeName} must set sizeLimit`);
  assert(hasVolumeMount(containerSpec, volumeName, mountPath),
    `${workloadName} must mount ${volumeName} at ${mountPath}`);
}

function checkContainerHardening(containerSpec, name) {
  const securityContext = containerSpec?.securityContext || {};
  assert(securityContext.allowPrivilegeEscalation === false,
    `${name} must disable privilege escalation`);
  assert(securityContext.readOnlyRootFilesystem === true,
    `${name} must use a read-only root filesystem`);
  assert((securityContext.capabilities?.drop || []).includes("ALL"),
    `${name} must drop all Linux capabilities`);
}

function checkStatefulSetRollout(statefulSet, name, serviceName) {
  assert(statefulSet.spec?.serviceName === serviceName, `${name} must use headless Service ${serviceName}`);
  assert(statefulSet.spec?.revisionHistoryLimit === 3, `${name} must keep revisionHistoryLimit=3`);
  assert(statefulSet.spec?.podManagementPolicy === "OrderedReady", `${name} must use OrderedReady pod management`);
  assert(statefulSet.spec?.updateStrategy?.type === "RollingUpdate", `${name} must use StatefulSet RollingUpdate`);
  assert(!statefulSet.spec?.strategy, `${name} must not use Deployment rollout strategy fields`);
}

function checkPodHardening(workload, name, requireFsGroup) {
  const podSpec = workload.spec?.template?.spec || {};
  const securityContext = podSpec.securityContext || {};
  assert(podSpec.terminationGracePeriodSeconds === 60, `${name} must set terminationGracePeriodSeconds=60`);
  assert(securityContext.runAsNonRoot === true, `${name} must run as non-root`);
  assert(securityContext.runAsUser === 10001, `${name} must set runAsUser=10001`);
  assert(securityContext.runAsGroup === 10001, `${name} must set runAsGroup=10001`);
  assert(securityContext.seccompProfile?.type === "RuntimeDefault", `${name} must use seccompProfile RuntimeDefault`);
  if (requireFsGroup) {
    assert(securityContext.fsGroup === 10001, `${name} must set fsGroup=10001 for persistent volume write access`);
  }
}

function checkTopologySpread(workload, name) {
  const selector = workload.spec?.selector?.matchLabels || {};
  const constraints = workload.spec?.template?.spec?.topologySpreadConstraints || [];
  const hasHostSpread = constraints.some((constraint) => {
    const labels = constraint.labelSelector?.matchLabels || {};
    return constraint.maxSkew === 1
      && constraint.topologyKey === "kubernetes.io/hostname"
      && constraint.whenUnsatisfiable === "ScheduleAnyway"
      && Object.entries(selector).every(([key, value]) => labels[key] === value);
  });
  assert(hasHostSpread, `${name} must prefer spreading replicas across kubernetes.io/hostname`);
}

function checkContainerImage(containerSpec, name) {
  const image = containerSpec?.image || "";
  assert(image && !image.endsWith(":latest"), `${name} image must not use latest tag`);
  const hasDigest = image.includes("@sha256:");
  const tag = image.includes(":") ? image.substring(image.lastIndexOf(":") + 1) : "";
  assert(hasDigest || /^v\d+\.\d+\.\d+$/u.test(tag) || /^sha-[0-9a-f]{7,40}$/u.test(tag),
    `${name} image must use an immutable vX.Y.Z tag, sha-<commit> tag, or digest`);
  assert(["IfNotPresent", "Always"].includes(containerSpec?.imagePullPolicy),
    `${name} imagePullPolicy must be explicit`);
}

function checkPreStopDrain(containerSpec, name) {
  const command = containerSpec?.lifecycle?.preStop?.exec?.command || [];
  assert(JSON.stringify(command) === JSON.stringify(["/bin/sh", "-c", "sleep 10"]),
    `${name} must configure preStop sleep 10 for rolling update drain`);
}

function checkResources(containerSpec, name) {
  const requests = containerSpec?.resources?.requests || {};
  const limits = containerSpec?.resources?.limits || {};
  for (const key of ["cpu", "memory", "ephemeral-storage"]) {
    assert(requests[key], `${name} must configure ${key} resource requests`);
    assert(limits[key], `${name} must configure ${key} resource limits`);
  }
}

function checkBackendProbes(containerSpec, name) {
  assert(containerSpec?.readinessProbe && containerSpec?.livenessProbe,
    `${name} must configure readiness/liveness probes`);
  assert(containerSpec.startupProbe?.httpGet && containerSpec.readinessProbe?.httpGet && containerSpec.livenessProbe?.httpGet,
    `${name} probes must use HTTP GET startup/readiness/liveness checks`);
  assert(containerSpec.startupProbe.httpGet.path === "/api/v1/actuator/health/readiness",
    `${name} startupProbe must use actuator readiness`);
  assert(containerSpec.readinessProbe.httpGet.path === "/api/v1/actuator/health/readiness",
    `${name} readinessProbe must use actuator readiness`);
  assert(containerSpec.livenessProbe.httpGet.path === "/api/v1/actuator/health/liveness",
    `${name} livenessProbe must use actuator liveness`);
}

function checkConfig(items) {
  const configMap = byKindAndName(items, "ConfigMap", "crest-env");
  assert(configMap, "missing ConfigMap crest-env");
  const data = configMap.data || {};
  const expected = {
    CREST_DB_TYPE: "ob-oracle",
    CREST_PRODUCTION_MODE: "true",
    CREST_QUARTZ_INSTANCE_ID: "AUTO",
    CREST_QUARTZ_CLUSTERED: "true",
    CREST_QUARTZ_CLUSTER_CHECKIN_INTERVAL: "10000",
    CREST_QUARTZ_MISFIRE_THRESHOLD: "60000",
    CREST_LOAD_DEMO: "false",
    CREST_FLYWAY_ENABLED: "false",
    CREST_INTERNAL_LITE_ENABLED: "true",
    CREST_API_DOCS_ENABLED: "false",
    CREST_KNIFE4J_ENABLED: "false",
    CREST_FEATURE_AI_ENABLED: "false",
    CREST_FEATURE_SQLBOT_ENABLED: "false",
    CREST_FEATURE_TEMPLATE_MARKET_ENABLED: "false",
    CREST_FEATURE_FONT_MANAGEMENT_ENABLED: "false",
    CREST_FEATURE_VISUALIZATION_BACKGROUND_ENABLED: "false",
    SPRING_PROFILES_ACTIVE: "standalone",
    CREST_REDIS_DATABASE: "0",
    CREST_ALLOWED_DATASOURCE_TYPES: "obOracle,Excel,ExcelRemote,API",
    CREST_TASK_QUEUE_ENABLED: "true",
    CREST_WEBSOCKET_BROADCAST_ENABLED: "true",
    CREST_HEALTH_REDIS_ENABLED: "true",
    CREST_SHUTDOWN_TIMEOUT: "45s",
    CREST_DATASOURCE_POOL_PRELOAD_ENABLED: "false",
  };
  for (const [key, value] of Object.entries(expected)) {
    assert(data[key] === value, `crest-env ${key} must be ${value}`);
  }
  assert(["false", "true"].includes(data.CREST_PROMETHEUS_ENABLED),
    "crest-env CREST_PROMETHEUS_ENABLED must be true or false");

  assert(data.CREST_REDIS_CLUSTER_NODES, "crest-env must configure external CREST_REDIS_CLUSTER_NODES");
  assert(data.CREST_REDIS_CLUSTER_NODES.split(",").filter(Boolean).length >= 3,
    "CREST_REDIS_CLUSTER_NODES must contain at least 3 cluster nodes");
  assert(data.CREST_REDIS_CLUSTER_MAX_REDIRECTS === "5", "crest-env CREST_REDIS_CLUSTER_MAX_REDIRECTS must be 5");
  assert(data.CREST_REDIS_CLUSTER_REFRESH_ADAPTIVE === "true",
    "crest-env CREST_REDIS_CLUSTER_REFRESH_ADAPTIVE must be true");
  assert(data.CREST_REDIS_CLUSTER_REFRESH_PERIOD === "30s",
    "crest-env CREST_REDIS_CLUSTER_REFRESH_PERIOD must be 30s");
  assert(data.CREST_REDIS_CLUSTER_DYNAMIC_REFRESH_SOURCES === "true",
    "crest-env CREST_REDIS_CLUSTER_DYNAMIC_REFRESH_SOURCES must be true");

  const redisPrefix = data.CREST_REDIS_KEY_PREFIX;
  assert(redisPrefix && !/\s/.test(redisPrefix), "crest-env CREST_REDIS_KEY_PREFIX must be a nonblank namespace");
  assert(/^\{[^}]+\}:[a-z0-9][a-z0-9._-]*$/u.test(redisPrefix),
    "crest-env CREST_REDIS_KEY_PREFIX must look like {<org>-<env>-crest-core}:prod");
  const redisTag = redisHashTag(redisPrefix, "crest-env CREST_REDIS_KEY_PREFIX");
  if (containsPlaceholder(redisTag)) {
    assert(!strictConfig,
      "crest-env CREST_REDIS_KEY_PREFIX must replace the template hash tag in strict config mode");
  } else {
    validateRedisNamespace(redisTag, "crest-env CREST_REDIS_KEY_PREFIX", { rejectExamples: strictConfig });
  }

  const prefixed = {
    CREST_REDIS_CACHE_KEY_PREFIX: `${redisPrefix}:cache:`,
    CREST_LOCK_KEY_PREFIX: `${redisPrefix}:lock`,
    CREST_WEBSOCKET_BROADCAST_CHANNEL: `${redisPrefix}:pubsub:websocket`,
    CREST_EXPORT_TASK_STREAM: `${redisPrefix}:stream:export-task`,
    CREST_EXPORT_TASK_CONSUMER_GROUP: `${redisPrefix}:group:export-workers`,
    CREST_SYNC_TASK_STREAM: `${redisPrefix}:stream:dataset-sync-task`,
    CREST_SYNC_TASK_CONSUMER_GROUP: `${redisPrefix}:group:dataset-sync-workers`,
    CREST_DATASOURCE_SYNC_TASK_STREAM: `${redisPrefix}:stream:datasource-sync-task`,
    CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP: `${redisPrefix}:group:datasource-sync-workers`,
    CREST_SCHEDULED_TASK_STREAM: `${redisPrefix}:stream:scheduled-task`,
    CREST_SCHEDULED_TASK_CONSUMER_GROUP: `${redisPrefix}:group:scheduled-workers`,
  };
  for (const [key, value] of Object.entries(prefixed)) {
    assert(data[key] === value, `crest-env ${key} must be ${value}`);
  }

  for (const key of ["CREST_DB_NAME", "CREST_DB_PARAMS", "CREST_REDIS_HOST", "CREST_REDIS_PORT"]) {
    assert(!(key in data), `crest-env must not require optional key ${key}`);
  }

  if (strictConfig) {
    validateNoPlaceholders("crest-env", data);
    parseHttpsOrigins(data.CREST_ORIGIN_LIST, "crest-env CREST_ORIGIN_LIST");
    assert(!data.CREST_REDIS_CLUSTER_NODES.split(",").some((node) => containsPlaceholder(node) || !/^[^:\s]+:\d+$/u.test(node)),
      "crest-env CREST_REDIS_CLUSTER_NODES must contain real host:port values in strict config mode");
  }
}

function checkBackendStatefulSet(items) {
  const statefulSet = byKindAndName(items, "StatefulSet", "crest-service");
  assert(statefulSet, "missing backend StatefulSet crest-service");
  assert(statefulSet.spec?.replicas === 2, "crest-service combined backend StatefulSet must run exactly 2 replicas");
  checkStatefulSetRollout(statefulSet, "crest-service", "crest-service-headless");
  checkPodHardening(statefulSet, "crest-service", true);
  checkTopologySpread(statefulSet, "crest-service");
  assert((statefulSet.spec?.template?.spec?.initContainers || []).length === 0,
    "crest-service must not use initContainers in production manifest");
  assert(statefulSet.spec?.template?.spec?.serviceAccountName === "crest",
    "crest-service must use crest ServiceAccount");
  assert(statefulSet.spec?.template?.spec?.automountServiceAccountToken === false,
    "crest-service must not automount service account tokens");

  const appContainer = container(statefulSet);
  checkContainerImage(appContainer, "crest-service");
  checkPreStopDrain(appContainer, "crest-service");
  checkResources(appContainer, "crest-service");
  checkContainerHardening(appContainer, "crest-service");
  assert(!appContainer.command && !appContainer.args, "crest-service must not override command or args");
  assert(envValue(appContainer, "CREST_RUNTIME_ROLE") === "all",
    "crest-service must set CREST_RUNTIME_ROLE=all");
  assert(envMap(appContainer).CREST_WORKER_ID?.valueFrom?.fieldRef?.fieldPath === "metadata.name",
    "crest-service must derive CREST_WORKER_ID from pod name");
  requireEnvFrom(appContainer, "configMapRef", "crest-env", "crest-service");
  requireEnvFrom(appContainer, "secretRef", "crest-db-secret", "crest-service");
  requireEnvFrom(appContainer, "secretRef", "crest-redis-secret", "crest-service");
  checkBackendProbes(appContainer, "crest-service");
  assert(hasVolume(statefulSet, "crest-data", "persistentVolumeClaim"), "crest-service must mount crest-data PVC");
  assert(hasVolumeMount(appContainer, "crest-data", "/opt/crest/data"),
    "crest-service must mount persistent data path");
  checkWritableEmptyDir(statefulSet, appContainer, "crest-service", "crest-cache", "/opt/crest/cache");
  checkWritableEmptyDir(statefulSet, appContainer, "crest-service", "crest-logs", "/opt/crest/logs");
  checkWritableEmptyDir(statefulSet, appContainer, "crest-service", "tmp", "/tmp");
}

function checkFrontendStatefulSet(items) {
  const statefulSet = byKindAndName(items, "StatefulSet", "crest");
  assert(statefulSet, "missing frontend StatefulSet crest");
  assert(statefulSet.spec?.replicas === 2, "frontend StatefulSet must run exactly 2 replicas");
  checkStatefulSetRollout(statefulSet, "crest", "crest-headless");
  checkPodHardening(statefulSet, "crest", false);
  checkTopologySpread(statefulSet, "crest");
  assert((statefulSet.spec?.template?.spec?.initContainers || []).length === 0,
    "frontend must not use initContainers in production manifest");
  assert(statefulSet.spec?.template?.spec?.serviceAccountName === "crest",
    "frontend must use crest ServiceAccount");
  assert(statefulSet.spec?.template?.spec?.automountServiceAccountToken === false,
    "frontend must not automount service account tokens");
  const appContainer = container(statefulSet);
  checkContainerImage(appContainer, "crest");
  checkPreStopDrain(appContainer, "crest");
  checkResources(appContainer, "crest");
  checkContainerHardening(appContainer, "crest");
  assert(!appContainer.command && !appContainer.args, "frontend must not override command or args");
  assert(appContainer?.readinessProbe?.httpGet && appContainer?.livenessProbe?.httpGet,
    "frontend probes must use HTTP GET");
  checkWritableEmptyDir(statefulSet, appContainer, "crest", "nginx-cache", "/var/cache/nginx");
  checkWritableEmptyDir(statefulSet, appContainer, "crest", "nginx-run", "/var/run/nginx");
  checkWritableEmptyDir(statefulSet, appContainer, "crest", "tmp", "/tmp");
}

function readNginxConfig(relativePath) {
  return readFileSync(path.join(repoRoot, relativePath), "utf8")
    .replace(/\r\n/g, "\n")
    .replace(/\s+$/u, "\n");
}

function checkNginxConfig(items) {
  const configMap = byKindAndName(items, "ConfigMap", "crest-nginx-config");
  assert(configMap, "missing ConfigMap crest-nginx-config");
  const data = configMap.data || {};
  assert(data["nginx.conf"] === readNginxConfig("deploy/nginx/nginx.conf"),
    "crest-nginx-config nginx.conf must match deploy/nginx/nginx.conf");
  assert(data["default.conf"] === readNginxConfig("deploy/nginx/default.conf"),
    "crest-nginx-config default.conf must match deploy/nginx/default.conf");
  assert(data["default.conf"].includes("charset utf-8;"),
    "crest-nginx-config default.conf must force utf-8 charset for browser text assets");
}

function ruleAllowsPort(rule, port) {
  return (rule.ports || []).some((item) => item.protocol === "TCP" && item.port === port);
}

function podSelectorMatches(selector, labels) {
  return Object.entries(labels).every(([key, value]) => selector?.matchLabels?.[key] === value);
}

function checkNetworkPolicies(items) {
  const policies = allByKind(items, "NetworkPolicy").map((item) => item.metadata?.name).sort();
  assert(JSON.stringify(policies) === JSON.stringify(["crest-service", "crest-web"]),
    `production deployment must include only crest-service and crest-web NetworkPolicies, got ${policies.join(", ")}`);

  const webPolicy = byKindAndName(items, "NetworkPolicy", "crest-web");
  assert(podSelectorMatches(webPolicy.spec?.podSelector, {
    "app.kubernetes.io/name": "crest",
    "app.kubernetes.io/component": "frontend",
  }), "crest-web NetworkPolicy must select frontend pods");
  assert((webPolicy.spec?.policyTypes || []).includes("Ingress"),
    "crest-web NetworkPolicy must apply ingress policy");
  assert((webPolicy.spec?.ingress || []).some((rule) => ruleAllowsPort(rule, 8080) && !rule.from),
    "crest-web NetworkPolicy must allow public ingress to frontend port 8080");

  const servicePolicy = byKindAndName(items, "NetworkPolicy", "crest-service");
  assert(podSelectorMatches(servicePolicy.spec?.podSelector, {
    "app.kubernetes.io/name": "crest-service",
    "app.kubernetes.io/component": "backend",
  }), "crest-service NetworkPolicy must select backend pods");
  assert((servicePolicy.spec?.policyTypes || []).includes("Ingress"),
    "crest-service NetworkPolicy must apply ingress policy");
  assert((servicePolicy.spec?.ingress || []).some((rule) => ruleAllowsPort(rule, 8100)
    && (rule.from || []).some((source) => podSelectorMatches(source.podSelector, {
      "app.kubernetes.io/name": "crest",
      "app.kubernetes.io/component": "frontend",
    }))), "crest-service NetworkPolicy must allow frontend pods to backend port 8100");
  assert((servicePolicy.spec?.ingress || []).some((rule) => ruleAllowsPort(rule, 8100)
    && (rule.from || []).some((source) => source.namespaceSelector?.matchLabels?.["kubernetes.io/metadata.name"] === "monitoring"
      && podSelectorMatches(source.podSelector, { "app.kubernetes.io/name": "prometheus" }))),
    "crest-service NetworkPolicy must allow Prometheus in the monitoring namespace to scrape backend port 8100");

  assert(!byKindAndName(items, "NetworkPolicy", "crest-worker"),
    "two-workload production deployment must not include crest-worker NetworkPolicy");
}

function checkIngress(items) {
  const ingress = byKindAndName(items, "Ingress", "crest");
  assert(ingress, "production deployment must include frontend Ingress crest");
  assert(ingress.spec?.ingressClassName, "crest Ingress must set ingressClassName");

  const tls = ingress.spec?.tls || [];
  assert(tls.length === 1, "crest Ingress must configure exactly one TLS entry");
  assert(tls[0].secretName === "crest-tls", "crest Ingress must use TLS secret crest-tls");
  assert((tls[0].hosts || []).length === 1, "crest Ingress TLS must list exactly one host");

  const rules = ingress.spec?.rules || [];
  assert(rules.length === 1, "crest Ingress must configure exactly one host rule");
  const host = rules[0].host;
  assert(host && tls[0].hosts[0] === host, "crest Ingress TLS host must match rule host");
  const paths = rules[0].http?.paths || [];
  assert(paths.length === 1, "crest Ingress must configure one root route");
  const route = paths[0];
  assert(route.path === "/" && route.pathType === "Prefix", "crest Ingress must route / with Prefix pathType");
  assert(route.backend?.service?.name === "crest", "crest Ingress must route to frontend Service crest");
  assert(route.backend?.service?.port?.number === 8100, "crest Ingress must route to frontend Service port 8100");

  if (strictConfig) {
    assert(!containsPlaceholder(ingress.spec.ingressClassName),
      "crest Ingress ingressClassName must not contain placeholders in strict config mode");
    assert(!localhostOrPlaceholder(host),
      "crest Ingress host must not use localhost or placeholder hosts in strict config mode");
    const configMap = byKindAndName(items, "ConfigMap", "crest-env");
    const originHosts = parseHttpsOrigins(configMap?.data?.CREST_ORIGIN_LIST, "crest-env CREST_ORIGIN_LIST")
      .map((origin) => origin.hostname);
    assert(originHosts.includes(host),
      "crest Ingress host must be included in crest-env CREST_ORIGIN_LIST in strict config mode");
  }
}

function checkPodDisruptionBudget(items, name, minAvailable, labels) {
  const pdb = byKindAndName(items, "PodDisruptionBudget", name);
  assert(pdb, `production deployment must include ${name} PodDisruptionBudget`);
  assert(pdb.spec?.minAvailable === minAvailable,
    `${name} PDB must set minAvailable=${minAvailable}`);
  const selector = pdb.spec?.selector?.matchLabels || {};
  for (const [key, value] of Object.entries(labels)) {
    assert(selector[key] === value, `${name} PDB selector must match ${key}=${value}`);
  }
}

function checkSupportResources(items) {
  const deployments = allByKind(items, "Deployment").map((item) => item.metadata?.name).sort();
  assert(deployments.length === 0,
    `production deployment must not include legacy Deployments, got ${deployments.join(", ")}`);

  const statefulSets = allByKind(items, "StatefulSet").map((item) => item.metadata?.name).sort();
  assert(JSON.stringify(statefulSets) === JSON.stringify(["crest", "crest-service"]),
    `production deployment must contain only crest and crest-service StatefulSets, got ${statefulSets.join(", ")}`);

  assert(allByKind(items, "HorizontalPodAutoscaler").length === 0, "minimal deployment must not include HPA");
  const pdbs = allByKind(items, "PodDisruptionBudget").map((item) => item.metadata?.name).sort();
  assert(JSON.stringify(pdbs) === JSON.stringify(["crest", "crest-service"]),
    `production deployment must include only crest and crest-service PDBs, got ${pdbs.join(", ")}`);
  checkPodDisruptionBudget(items, "crest", 1, {
    "app.kubernetes.io/name": "crest",
    "app.kubernetes.io/component": "frontend",
  });
  checkPodDisruptionBudget(items, "crest-service", 1, {
    "app.kubernetes.io/name": "crest-service",
    "app.kubernetes.io/component": "backend",
  });
  for (const removedName of ["crest-worker", "crest-scheduler"]) {
    assert(!byKindAndName(items, "Deployment", removedName),
      `two-workload production deployment must not include ${removedName} Deployment`);
    assert(!byKindAndName(items, "StatefulSet", removedName),
      `two-workload production deployment must not include ${removedName} StatefulSet`);
    assert(!byKindAndName(items, "PodDisruptionBudget", removedName),
      `two-workload production deployment must not include ${removedName} PDB`);
  }
  assert(!byKindAndName(items, "ConfigMap", "crest-app-config"),
    "minimal deployment must not mount application.yml ConfigMap");
  assert(allByKind(items, "Namespace").length === 0, "minimal deployment must not create Namespace");
  const serviceAccount = byKindAndName(items, "ServiceAccount", "crest");
  assert(serviceAccount, "production deployment must include crest ServiceAccount");
  assert(serviceAccount.automountServiceAccountToken === false,
    "crest ServiceAccount must not automount tokens");

  const pvc = byKindAndName(items, "PersistentVolumeClaim", "crest-data");
  assert(pvc, "missing PVC crest-data");
  assert((pvc.spec?.accessModes || []).includes("ReadWriteMany"), "crest-data PVC must use ReadWriteMany for multi-replica app pods");
  if (strictConfig) {
    assert(pvc.spec?.storageClassName && !containsPlaceholder(pvc.spec.storageClassName),
      "crest-data PVC must set an explicit non-placeholder storageClassName in strict config mode");
  }

  assert(!byKindAndName(items, "Deployment", "crest-redis"), "production manifest must use external shared Redis Cluster");
  assert(!byKindAndName(items, "StatefulSet", "crest-redis"), "production manifest must use external shared Redis Cluster");
  assert(!byKindAndName(items, "PersistentVolumeClaim", "crest-redis-data"),
    "production manifest must not create internal Redis PVC");
  assert(!byKindAndName(items, "ConfigMap", "crest-redis-config"),
    "production manifest must not create internal Redis ConfigMap");
  assert(!byKindAndName(items, "Service", "crest-redis"), "production manifest must not create internal Redis Service");

  const dbSecret = byKindAndName(items, "Secret", "crest-db-secret");
  const redisSecret = byKindAndName(items, "Secret", "crest-redis-secret");
  const configMap = byKindAndName(items, "ConfigMap", "crest-env");
  const prometheusEnabled = configMap?.data?.CREST_PROMETHEUS_ENABLED;
  assert(dbSecret, "missing DB Secret crest-db-secret");
  assert(redisSecret, "missing Redis Secret crest-redis-secret");
  assert("CREST_REDIS_PASSWORD" in (redisSecret.stringData || {}),
    "crest-redis-secret must provide CREST_REDIS_PASSWORD for shared Redis");
  assert(["false", "true"].includes(prometheusEnabled),
    "crest-env CREST_PROMETHEUS_ENABLED must be true or false");
  if (prometheusEnabled === "true") {
    requireSecretText(dbSecret, "CREST_PROMETHEUS_TOKEN", 32);
  } else {
    assert(!secretHasKey(dbSecret, "CREST_PROMETHEUS_TOKEN"),
      "crest-db-secret must not include CREST_PROMETHEUS_TOKEN when CREST_PROMETHEUS_ENABLED=false");
  }
  for (const key of ["CREST_SM4_KEY", "CREST_CRYPTO_MODE"]) {
    assert(!(key in (dbSecret.stringData || {})), `crest-db-secret must not require optional key ${key}`);
  }
  if (strictConfig) {
    validateNoPlaceholders("crest-db-secret", dbSecret.stringData || {});
    validateNoPlaceholders("crest-redis-secret", redisSecret.stringData || {});
    requireSecretText(dbSecret, "CREST_DB_USERNAME", 3);
    requireSecretText(dbSecret, "CREST_DB_PASSWORD", 12);
    const aesKey = requireSecretText(dbSecret, "CREST_AES_KEY", 32);
    assert(aesKey.length === 32, "crest-db-secret CREST_AES_KEY must be exactly 32 characters in strict config mode");
    const aesIv = requireSecretText(dbSecret, "CREST_AES_IV", 16);
    assert(aesIv.length === 16, "crest-db-secret CREST_AES_IV must be exactly 16 characters in strict config mode");
    requireSecretText(dbSecret, "CREST_INITIAL_PASSWORD", 12);
    requireSecretText(dbSecret, "CREST_TOKEN_SECRET", 32);
    const redisUsername = requireSecretText(redisSecret, "CREST_REDIS_USERNAME", 3);
    validateRedisAclUser(redisUsername, "crest-redis-secret CREST_REDIS_USERNAME", { rejectExamples: true });
    const redisPassword = requireSecretText(redisSecret, "CREST_REDIS_PASSWORD", 12);
    assert(redisPassword !== "password" && redisPassword !== "changeme",
      "crest-redis-secret CREST_REDIS_PASSWORD must not use a common weak value in strict config mode");
  }

  const backendService = byKindAndName(items, "Service", "crest-service");
  assert(backendService?.spec?.type === "ClusterIP" || backendService?.spec?.type === undefined,
    "crest-service Service must stay ClusterIP");
  assert(backendService?.spec?.clusterIP !== "None", "crest-service traffic Service must not be headless");
  assert(backendService?.spec?.selector?.["app.kubernetes.io/name"] === "crest-service",
    "crest-service must route to backend pod");
  assert(backendService?.spec?.selector?.["app.kubernetes.io/component"] === "backend",
    "crest-service must select backend component");

  const frontendService = byKindAndName(items, "Service", "crest");
  assert(frontendService?.spec?.type === "ClusterIP" || frontendService?.spec?.type === undefined,
    "crest Service must stay ClusterIP and be exposed through Ingress/TLS");
  assert(frontendService?.spec?.clusterIP !== "None", "crest traffic Service must not be headless");
  assert(!(frontendService?.spec?.ports || []).some((port) => port.nodePort),
    "crest Service must not expose nodePort in production manifest");
  assert(frontendService?.spec?.selector?.["app.kubernetes.io/name"] === "crest",
    "crest Service must route to frontend pod");

  const backendHeadless = byKindAndName(items, "Service", "crest-service-headless");
  assert(backendHeadless?.spec?.clusterIP === "None", "crest-service-headless must be a headless Service");
  assert(backendHeadless?.spec?.selector?.["app.kubernetes.io/name"] === "crest-service",
    "crest-service-headless must select backend pods");
  assert(backendHeadless?.spec?.selector?.["app.kubernetes.io/component"] === "backend",
    "crest-service-headless must select backend component");

  const frontendHeadless = byKindAndName(items, "Service", "crest-headless");
  assert(frontendHeadless?.spec?.clusterIP === "None", "crest-headless must be a headless Service");
  assert(frontendHeadless?.spec?.selector?.["app.kubernetes.io/name"] === "crest",
    "crest-headless must select frontend pods");
  assert(frontendHeadless?.spec?.selector?.["app.kubernetes.io/component"] === "frontend",
    "crest-headless must select frontend component");
}

const rendered = renderOverlay();
const items = rendered.items || [];

checkConfig(items);
checkBackendStatefulSet(items);
checkFrontendStatefulSet(items);
checkNginxConfig(items);
checkNetworkPolicies(items);
checkIngress(items);
checkSupportResources(items);

info(`${overlay} passed production Kubernetes checks`);
