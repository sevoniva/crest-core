#!/usr/bin/env node

import { execFileSync } from "node:child_process";

const args = process.argv.slice(2);

function argValue(name, envName, fallback) {
  const index = args.indexOf(name);
  if (index >= 0) {
    const value = args[index + 1];
    if (!value || value.startsWith("--")) {
      fail(`${name} requires a value`);
    }
    return value;
  }
  return process.env[envName] || fallback;
}

function flag(name, envName) {
  return args.includes(name) || process.env[envName] === "true";
}

function usage() {
  console.log(`Usage: node scripts/production-runtime-check.mjs [--namespace crest] [--context kube-context] [--timeout 300s]

Checks a live Crest Core Kubernetes deployment after production or pre-production apply.

Environment:
  CREST_K8S_NAMESPACE             Namespace to inspect, default: crest
  CREST_KUBE_CONTEXT              Optional kubectl context
  CREST_K8S_ROLLOUT_TIMEOUT       Rollout wait timeout, default: 300s
  CREST_REQUIRE_INGRESS_ADDRESS   Set true to fail if Ingress has no load balancer address
`);
}

if (args.includes("--help") || args.includes("-h")) {
  usage();
  process.exit(0);
}

const namespace = argValue("--namespace", "CREST_K8S_NAMESPACE", "crest");
const context = argValue("--context", "CREST_KUBE_CONTEXT", "");
const timeout = argValue("--timeout", "CREST_K8S_ROLLOUT_TIMEOUT", "300s");
const requireIngressAddress = flag("--require-ingress-address", "CREST_REQUIRE_INGRESS_ADDRESS");

function fail(message) {
  console.error(`runtime-check: ${message}`);
  process.exit(1);
}

function info(message) {
  console.log(`runtime-check: ${message}`);
}

function warn(message) {
  console.warn(`runtime-check: warning: ${message}`);
}

function assert(condition, message) {
  if (!condition) {
    fail(message);
  }
}

function kubectl(params, { namespaced = true, json = false, quiet = false } = {}) {
  const base = [];
  if (context) {
    base.push("--context", context);
  }
  if (namespaced) {
    base.push("-n", namespace);
  }
  try {
    const output = execFileSync("kubectl", [...base, ...params], {
      encoding: "utf8",
      stdio: quiet ? ["ignore", "pipe", "pipe"] : ["ignore", "pipe", "pipe"],
    });
    return json ? JSON.parse(output) : output.trim();
  } catch (error) {
    const stderr = error.stderr?.toString?.() || error.message;
    fail(`kubectl ${[...base, ...params].join(" ")} failed: ${stderr.trim()}`);
  }
}

function get(kind, name, options = {}) {
  return kubectl(["get", kind, name, "-o", "json"], { ...options, json: true });
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
      fail(`${resourceName} must contain valid HTTPS origins`);
    }
    assert(url.protocol === "https:", `${resourceName} must use HTTPS origins`);
    assert(!url.pathname || url.pathname === "/", `${resourceName} origins must not include paths`);
    assert(!url.search && !url.hash, `${resourceName} origins must not include query strings or fragments`);
    assert(!localhostOrPlaceholder(url.hostname), `${resourceName} must not use localhost or placeholder hosts`);
    return url;
  });
}

function redisHashTag(value) {
  const match = String(value || "").match(/\{([^}]+)\}/u);
  return match?.[1] || "";
}

function validateRedisNamespace(hashTag, resourceName) {
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
  assert(!looksLikeExample,
    `${resourceName} hash tag looks like an example value; replace it with a real organization/environment namespace`);
}

function secretValue(secret, key) {
  const encoded = secret?.data?.[key];
  assert(encoded, `${secret.metadata?.name || "Secret"} must contain ${key}`);
  return Buffer.from(encoded, "base64").toString("utf8");
}

function secretHasKey(secret, key) {
  return Object.prototype.hasOwnProperty.call(secret?.data || {}, key);
}

function requireSecretText(secret, key, minLength) {
  const value = secretValue(secret, key);
  assert(value.length >= minLength, `${secret.metadata?.name} ${key} must be at least ${minLength} characters`);
  assert(!containsPlaceholder(value), `${secret.metadata?.name} ${key} must not contain placeholder values`);
  return value;
}

function checkResources(containerSpec, name) {
  const requests = containerSpec?.resources?.requests || {};
  const limits = containerSpec?.resources?.limits || {};
  for (const key of ["cpu", "memory", "ephemeral-storage"]) {
    assert(requests[key], `${name} must configure ${key} resource requests`);
    assert(limits[key], `${name} must configure ${key} resource limits`);
  }
}

function volumeByName(deployment, name) {
  return (deployment?.spec?.template?.spec?.volumes || []).find((item) => item.name === name);
}

function hasVolumeMount(containerSpec, name, mountPath) {
  return (containerSpec?.volumeMounts || []).some((item) => item.name === name && item.mountPath === mountPath);
}

function checkWritableEmptyDir(deployment, containerSpec, deploymentName, volumeName, mountPath) {
  const volume = volumeByName(deployment, volumeName);
  assert(volume?.emptyDir, `${deploymentName} must define writable emptyDir ${volumeName}`);
  assert(volume.emptyDir.sizeLimit, `${deploymentName} emptyDir ${volumeName} must set sizeLimit`);
  assert(hasVolumeMount(containerSpec, volumeName, mountPath),
    `${deploymentName} must mount ${volumeName} at ${mountPath}`);
}

function checkWritableStorage(deployment, containerSpec, name) {
  if (name === "crest") {
    checkWritableEmptyDir(deployment, containerSpec, name, "nginx-cache", "/var/cache/nginx");
    checkWritableEmptyDir(deployment, containerSpec, name, "nginx-run", "/var/run/nginx");
    checkWritableEmptyDir(deployment, containerSpec, name, "tmp", "/tmp");
    return;
  }
  assert(volumeByName(deployment, "crest-data")?.persistentVolumeClaim?.claimName === "crest-data",
    `${name} must mount crest-data PVC`);
  assert(hasVolumeMount(containerSpec, "crest-data", "/opt/crest/data"),
    `${name} must mount persistent data path`);
  checkWritableEmptyDir(deployment, containerSpec, name, "crest-cache", "/opt/crest/cache");
  checkWritableEmptyDir(deployment, containerSpec, name, "crest-logs", "/opt/crest/logs");
  checkWritableEmptyDir(deployment, containerSpec, name, "tmp", "/tmp");
}

function checkContainerHardening(containerSpec, name) {
  const securityContext = containerSpec?.securityContext || {};
  assert(securityContext.allowPrivilegeEscalation === false, `${name} must disable privilege escalation`);
  assert(securityContext.readOnlyRootFilesystem === true, `${name} must use a read-only root filesystem`);
  assert((securityContext.capabilities?.drop || []).includes("ALL"), `${name} must drop all Linux capabilities`);
}

function envFromRef(containerSpec, type, name) {
  return (containerSpec?.envFrom || []).find((item) => item[type]?.name === name);
}

function requireEnvFrom(containerSpec, type, name, owner) {
  const ref = envFromRef(containerSpec, type, name);
  assert(ref, `${owner} must load ${name}`);
  assert(ref[type]?.optional !== true, `${owner} must require ${name}`);
}

function checkDeploymentRollout(deployment, name, strategyType, expectedRollingUpdate = { maxSurge: 1, maxUnavailable: 0 }) {
  assert(deployment.spec?.revisionHistoryLimit === 3, `${name} must keep revisionHistoryLimit=3`);
  assert(deployment.spec?.progressDeadlineSeconds === 600, `${name} must set progressDeadlineSeconds=600`);
  assert(deployment.spec?.strategy?.type === strategyType, `${name} must use ${strategyType} rollout strategy`);
  if (strategyType === "RollingUpdate") {
    const rollingUpdate = deployment.spec.strategy.rollingUpdate || {};
    assert(rollingUpdate.maxSurge === expectedRollingUpdate.maxSurge,
      `${name} rolling update must set maxSurge=${expectedRollingUpdate.maxSurge}`);
    assert(rollingUpdate.maxUnavailable === expectedRollingUpdate.maxUnavailable,
      `${name} rolling update must set maxUnavailable=${expectedRollingUpdate.maxUnavailable}`);
  }
}

function checkPodHardening(deployment, name, requireFsGroup) {
  const podSpec = deployment.spec?.template?.spec || {};
  const securityContext = podSpec.securityContext || {};
  assert(podSpec.terminationGracePeriodSeconds === 60, `${name} must set terminationGracePeriodSeconds=60`);
  assert(podSpec.automountServiceAccountToken === false, `${name} must not automount ServiceAccount tokens`);
  assert(podSpec.serviceAccountName === "crest", `${name} must use ServiceAccount crest`);
  assert(securityContext.runAsNonRoot === true, `${name} must run as non-root`);
  assert(securityContext.runAsUser === 10001, `${name} must set runAsUser=10001`);
  assert(securityContext.runAsGroup === 10001, `${name} must set runAsGroup=10001`);
  assert(securityContext.seccompProfile?.type === "RuntimeDefault", `${name} must use RuntimeDefault seccomp`);
  if (requireFsGroup) {
    assert(securityContext.fsGroup === 10001, `${name} must set fsGroup=10001`);
  }
}

function checkTopologySpread(deployment, name) {
  const selector = deployment.spec?.selector?.matchLabels || {};
  const constraints = deployment.spec?.template?.spec?.topologySpreadConstraints || [];
  const hasHostSpread = constraints.some((constraint) => {
    const labels = constraint.labelSelector?.matchLabels || {};
    return constraint.maxSkew === 1
      && constraint.topologyKey === "kubernetes.io/hostname"
      && constraint.whenUnsatisfiable === "ScheduleAnyway"
      && Object.entries(selector).every(([key, value]) => labels[key] === value);
  });
  assert(hasHostSpread, `${name} must prefer spreading replicas across kubernetes.io/hostname`);
}

function checkImage(containerSpec, name) {
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

function checkBackendProbes(containerSpec, name) {
  assert(containerSpec?.startupProbe?.httpGet && containerSpec?.readinessProbe?.httpGet && containerSpec?.livenessProbe?.httpGet,
    `${name} probes must use HTTP GET startup/readiness/liveness checks`);
  assert(containerSpec.startupProbe.httpGet.path === "/api/v1/actuator/health/readiness",
    `${name} startupProbe must use actuator readiness`);
  assert(containerSpec.readinessProbe.httpGet.path === "/api/v1/actuator/health/readiness",
    `${name} readinessProbe must use actuator readiness`);
  assert(containerSpec.livenessProbe.httpGet.path === "/api/v1/actuator/health/liveness",
    `${name} livenessProbe must use actuator liveness`);
}

function checkFrontendProbes(containerSpec, name) {
  assert(containerSpec?.readinessProbe?.httpGet && containerSpec?.livenessProbe?.httpGet,
    `${name} probes must use HTTP GET readiness/liveness checks`);
  assert(containerSpec.readinessProbe.httpGet.path === "/healthz", `${name} readinessProbe must use /healthz`);
  assert(containerSpec.livenessProbe.httpGet.path === "/healthz", `${name} livenessProbe must use /healthz`);
}

function checkDeployment(name, component, role, minReplicas, requireFsGroup, strategyType = "RollingUpdate",
                         expectedRollingUpdate = { maxSurge: 1, maxUnavailable: 0 }) {
  info(`checking rollout ${name}`);
  kubectl(["rollout", "status", `deployment/${name}`, `--timeout=${timeout}`], { quiet: true });

  const deployment = get("deployment", name);
  assert((deployment.spec?.replicas || 0) === minReplicas, `${name} must run exactly ${minReplicas} replica(s)`);
  assert((deployment.status?.availableReplicas || 0) >= minReplicas, `${name} must have at least ${minReplicas} available replica(s)`);
  assert(deployment.status?.observedGeneration >= deployment.metadata?.generation, `${name} controller has not observed latest generation`);
  checkDeploymentRollout(deployment, name, strategyType, expectedRollingUpdate);
  checkPodHardening(deployment, name, requireFsGroup);
  if (minReplicas >= 2) {
    checkTopologySpread(deployment, name);
  }

  const appContainer = deployment.spec?.template?.spec?.containers?.[0];
  assert(appContainer, `${name} must define an application container`);
  checkImage(appContainer, name);
  checkPreStopDrain(appContainer, name);
  checkResources(appContainer, name);
  checkContainerHardening(appContainer, name);
  checkWritableStorage(deployment, appContainer, name);
  if (name === "crest") {
    checkFrontendProbes(appContainer, name);
  } else {
    checkBackendProbes(appContainer, name);
  }
  if (role) {
    const env = Object.fromEntries((appContainer.env || []).map((item) => [item.name, item.value]));
    assert(env.CREST_RUNTIME_ROLE === role, `${name} must set CREST_RUNTIME_ROLE=${role}`);
    requireEnvFrom(appContainer, "configMapRef", "crest-env", name);
    requireEnvFrom(appContainer, "secretRef", "crest-db-secret", name);
    requireEnvFrom(appContainer, "secretRef", "crest-redis-secret", name);
  }

  const pods = kubectl(["get", "pods", "-l", `app.kubernetes.io/name=${name},app.kubernetes.io/component=${component}`, "-o", "json"], { json: true });
  assert((pods.items || []).length >= minReplicas, `${name} must have at least ${minReplicas} matching pod(s)`);
  for (const pod of pods.items || []) {
    assert(pod.status?.phase === "Running", `${pod.metadata?.name} must be Running`);
    const ready = (pod.status?.conditions || []).some((condition) => condition.type === "Ready" && condition.status === "True");
    assert(ready, `${pod.metadata?.name} must be Ready`);
  }
}

function ruleAllowsPort(rule, port) {
  return (rule.ports || []).some((item) => item.protocol === "TCP" && item.port === port);
}

function podSelectorMatches(selector, labels) {
  return Object.entries(labels).every(([key, value]) => selector?.matchLabels?.[key] === value);
}

function checkPodDisruptionBudget(name, minAvailable, labels) {
  const pdb = get("pdb", name);
  assert(pdb.spec?.minAvailable === minAvailable, `${name} PDB must set minAvailable=${minAvailable}`);
  assert(podSelectorMatches(pdb.spec?.selector, labels), `${name} PDB must select the expected pods`);
  assert((pdb.status?.expectedPods || 0) >= minAvailable, `${name} PDB must observe at least ${minAvailable} pod(s)`);
  assert((pdb.status?.currentHealthy || 0) >= minAvailable, `${name} PDB must have at least ${minAvailable} healthy pod(s)`);
}

function checkNetworkPolicies() {
  const webPolicy = get("networkpolicy", "crest-web");
  assert(podSelectorMatches(webPolicy.spec?.podSelector, {
    "app.kubernetes.io/name": "crest",
    "app.kubernetes.io/component": "frontend",
  }), "crest-web NetworkPolicy must select frontend pods");
  assert((webPolicy.spec?.policyTypes || []).includes("Ingress"),
    "crest-web NetworkPolicy must apply ingress policy");
  assert((webPolicy.spec?.ingress || []).some((rule) => ruleAllowsPort(rule, 8080)
    && (!rule.from || rule.from.length === 0)),
  "crest-web NetworkPolicy must allow public ingress to frontend port 8080");

  const servicePolicy = get("networkpolicy", "crest-service");
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

}

function checkService(name, component) {
  const service = get("service", name);
  assert(service.spec?.type === "ClusterIP", `${name} Service must be ClusterIP`);
  assert(!(service.spec?.ports || []).some((port) => port.nodePort), `${name} Service must not expose nodePort`);
  assert(service.spec?.selector?.["app.kubernetes.io/name"] === name, `${name} Service must select app name ${name}`);
  assert(service.spec?.selector?.["app.kubernetes.io/component"] === component, `${name} Service must select component ${component}`);
  assert((service.spec?.ports || []).some((port) => port.port === 8100), `${name} Service must expose service port 8100`);

  const endpoints = get("endpoints", name);
  const readyAddresses = (endpoints.subsets || []).flatMap((subset) => subset.addresses || []);
  assert(readyAddresses.length > 0, `${name} Service must have ready endpoints`);
}

function checkConfigAndSecrets() {
  const config = get("configmap", "crest-env");
  const data = config.data || {};
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
    CREST_ALLOWED_DATASOURCE_TYPES: "obOracle,Excel,ExcelRemote,API",
    CREST_REDIS_DATABASE: "0",
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
  parseHttpsOrigins(data.CREST_ORIGIN_LIST, "crest-env CREST_ORIGIN_LIST");
  assert(!containsPlaceholder(data.CREST_DB_HOST), "crest-env CREST_DB_HOST must not contain placeholders");
  assert(!containsPlaceholder(data.CREST_DB_URL), "crest-env CREST_DB_URL must not contain placeholders");
  assert((data.CREST_REDIS_CLUSTER_NODES || "").split(",").filter(Boolean).length >= 3,
    "crest-env CREST_REDIS_CLUSTER_NODES must contain at least 3 nodes");
  assert(!data.CREST_REDIS_CLUSTER_NODES.split(",").some((node) => containsPlaceholder(node) || !/^[^:\s]+:\d+$/u.test(node)),
    "crest-env CREST_REDIS_CLUSTER_NODES must contain real host:port values");
  const redisPrefix = data.CREST_REDIS_KEY_PREFIX;
  assert(/^\{[^}]+\}:[a-z0-9][a-z0-9._-]*$/u.test(redisPrefix),
    "crest-env CREST_REDIS_KEY_PREFIX must look like {<org>-<env>-crest-core}:prod");
  const expectedRedisTag = redisHashTag(redisPrefix);
  assert(expectedRedisTag, "crest-env CREST_REDIS_KEY_PREFIX must use a Redis Cluster hash tag");
  validateRedisNamespace(expectedRedisTag, "crest-env CREST_REDIS_KEY_PREFIX");
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
    assert(redisHashTag(value) === expectedRedisTag, `crest-env ${key} must use the same Redis Cluster hash tag`);
  }

  const dbSecret = get("secret", "crest-db-secret");
  requireSecretText(dbSecret, "CREST_DB_USERNAME", 3);
  requireSecretText(dbSecret, "CREST_DB_PASSWORD", 12);
  assert(requireSecretText(dbSecret, "CREST_AES_KEY", 32).length === 32, "CREST_AES_KEY must be exactly 32 characters");
  assert(requireSecretText(dbSecret, "CREST_AES_IV", 16).length === 16, "CREST_AES_IV must be exactly 16 characters");
  requireSecretText(dbSecret, "CREST_INITIAL_PASSWORD", 12);
  requireSecretText(dbSecret, "CREST_TOKEN_SECRET", 32);
  if (data.CREST_PROMETHEUS_ENABLED === "true") {
    requireSecretText(dbSecret, "CREST_PROMETHEUS_TOKEN", 32);
  } else {
    assert(!secretHasKey(dbSecret, "CREST_PROMETHEUS_TOKEN"),
      "crest-db-secret must not include CREST_PROMETHEUS_TOKEN when CREST_PROMETHEUS_ENABLED=false");
  }

  const redisSecret = get("secret", "crest-redis-secret");
  const redisUsername = requireSecretText(redisSecret, "CREST_REDIS_USERNAME", 3);
  assert(redisUsername !== "default", "CREST_REDIS_USERNAME must not use the shared default Redis user");
  const redisPassword = requireSecretText(redisSecret, "CREST_REDIS_PASSWORD", 12);
  assert(redisPassword !== "password" && redisPassword !== "changeme", "CREST_REDIS_PASSWORD must not be a weak common value");
}

function checkIngress() {
  const ingress = get("ingress", "crest");
  assert(ingress.spec?.ingressClassName, "crest Ingress must set ingressClassName");
  const tls = ingress.spec?.tls || [];
  assert(tls.length === 1, "crest Ingress must configure exactly one TLS entry");
  const host = ingress.spec?.rules?.[0]?.host;
  assert(host && tls[0].hosts?.[0] === host, "crest Ingress TLS host must match rule host");
  assert(!localhostOrPlaceholder(host), "crest Ingress host must not use localhost or placeholder hosts");

  const config = get("configmap", "crest-env");
  const originHosts = parseHttpsOrigins(config.data?.CREST_ORIGIN_LIST, "crest-env CREST_ORIGIN_LIST")
    .map((origin) => origin.hostname);
  assert(originHosts.includes(host), "crest Ingress host must be included in CREST_ORIGIN_LIST");

  const tlsSecret = get("secret", tls[0].secretName);
  assert(tlsSecret.type === "kubernetes.io/tls", `${tls[0].secretName} must be a kubernetes.io/tls Secret`);
  assert(tlsSecret.data?.["tls.crt"] && tlsSecret.data?.["tls.key"], `${tls[0].secretName} must contain tls.crt and tls.key`);

  const addresses = ingress.status?.loadBalancer?.ingress || [];
  if (requireIngressAddress) {
    assert(addresses.length > 0, "crest Ingress must have a load balancer address");
  } else if (addresses.length === 0) {
    warn("crest Ingress has no load balancer address yet");
  }
}

function checkSupportResources() {
  const serviceAccount = get("serviceaccount", "crest");
  assert(serviceAccount.automountServiceAccountToken === false, "crest ServiceAccount must not automount tokens");

  const pvc = get("pvc", "crest-data");
  assert(pvc.status?.phase === "Bound", "crest-data PVC must be Bound");
  assert((pvc.spec?.accessModes || []).includes("ReadWriteMany"), "crest-data PVC must use ReadWriteMany");

  checkPodDisruptionBudget("crest", 1, {
    "app.kubernetes.io/name": "crest",
    "app.kubernetes.io/component": "frontend",
  });
  checkPodDisruptionBudget("crest-service", 1, {
    "app.kubernetes.io/name": "crest-service",
    "app.kubernetes.io/component": "backend",
  });
  checkNetworkPolicies();
}

kubectl(["get", "namespace", namespace], { namespaced: false, quiet: true });

checkConfigAndSecrets();
checkDeployment("crest", "frontend", null, 2, false);
checkDeployment("crest-service", "backend", "all", 2, true, "RollingUpdate", { maxSurge: 0, maxUnavailable: 1 });
checkService("crest", "frontend");
checkService("crest-service", "backend");
checkIngress();
checkSupportResources();

info(`namespace ${namespace} passed live production runtime checks`);
