#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-runtime-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_PRODUCTION_RUNTIME_DIR:-.local/production-runtime-check-test-$$}"
fake_bin="${test_root}/bin"
rm -rf "${test_root}"
mkdir -p "${fake_bin}"

cat > "${fake_bin}/kubectl" <<'EOF'
#!/usr/bin/env node
const args = process.argv.slice(2);

function stripGlobal(argv) {
  const copy = [...argv];
  for (let index = 0; index < copy.length;) {
    if (copy[index] === "--context" || copy[index] === "-n") {
      copy.splice(index, 2);
    } else {
      index += 1;
    }
  }
  return copy;
}

function b64(value) {
  return Buffer.from(value, "utf8").toString("base64");
}

function container(name, role) {
  const isFrontend = name === "crest";
  const envFrom = isFrontend ? undefined : [
    { configMapRef: { name: "crest-env" } },
    {
      secretRef: {
        name: "crest-db-secret",
        ...(process.env.CREST_RUNTIME_TEST_OPTIONAL_SECRET === "true" && name === "crest-service"
          ? { optional: true }
          : {}),
      },
    },
    { secretRef: { name: "crest-redis-secret" } },
  ];
  return {
    name,
    image: isFrontend ? "ghcr.io/sevoniva/crest-web:v1.0.0" : "ghcr.io/sevoniva/crest-service:v1.0.0",
    imagePullPolicy: "IfNotPresent",
    env: isFrontend ? [] : [
      { name: "CREST_RUNTIME_ROLE", value: role },
      { name: "CREST_WORKER_ID", valueFrom: { fieldRef: { fieldPath: "metadata.name" } } },
    ],
    envFrom,
    startupProbe: isFrontend ? undefined : { httpGet: { path: "/api/v1/actuator/health/readiness", port: "http" } },
    readinessProbe: { httpGet: { path: isFrontend ? "/healthz" : "/api/v1/actuator/health/readiness", port: "http" } },
    livenessProbe: { httpGet: { path: isFrontend ? "/healthz" : "/api/v1/actuator/health/liveness", port: "http" } },
    lifecycle: { preStop: { exec: { command: ["/bin/sh", "-c", "sleep 10"] } } },
    securityContext: {
      allowPrivilegeEscalation: false,
      readOnlyRootFilesystem: true,
      capabilities: { drop: ["ALL"] },
    },
    resources: {
      requests: { cpu: "100m", memory: "128Mi", "ephemeral-storage": "128Mi" },
      limits: { cpu: "500m", memory: "512Mi", "ephemeral-storage": "512Mi" },
    },
    volumeMounts: isFrontend ? [
      { name: "nginx-cache", mountPath: "/var/cache/nginx" },
      { name: "nginx-run", mountPath: "/var/run/nginx" },
      { name: "tmp", mountPath: "/tmp" },
    ] : [
      { name: "crest-data", mountPath: "/opt/crest/data" },
      { name: "crest-cache", mountPath: "/opt/crest/cache" },
      { name: "crest-logs", mountPath: "/opt/crest/logs" },
      { name: "tmp", mountPath: "/tmp" },
    ],
  };
}

function deployment(name) {
  const mapping = {
    crest: { component: "frontend", role: null, replicas: 2 },
    "crest-service": { component: "backend", role: "all", replicas: 2 },
  };
  const item = mapping[name];
  const isFrontend = name === "crest";
  return {
    metadata: { name, generation: 1 },
    spec: {
      replicas: item.replicas,
      revisionHistoryLimit: 3,
      progressDeadlineSeconds: 600,
      strategy: {
        type: "RollingUpdate",
        rollingUpdate: { maxSurge: 0, maxUnavailable: 1 },
      },
      selector: { matchLabels: { "app.kubernetes.io/name": name, "app.kubernetes.io/component": item.component } },
      template: {
        spec: {
          serviceAccountName: "crest",
          automountServiceAccountToken: false,
          terminationGracePeriodSeconds: 60,
          topologySpreadConstraints: [{
            maxSkew: 1,
            topologyKey: "kubernetes.io/hostname",
            whenUnsatisfiable: "ScheduleAnyway",
            labelSelector: { matchLabels: { "app.kubernetes.io/name": name, "app.kubernetes.io/component": item.component } },
          }],
          securityContext: {
            ...(isFrontend ? {} : { fsGroup: 10001 }),
            runAsNonRoot: true,
            runAsUser: 10001,
            runAsGroup: 10001,
            seccompProfile: { type: "RuntimeDefault" },
          },
          containers: [container(name, item.role)],
          volumes: isFrontend ? [
            { name: "nginx-cache", emptyDir: { sizeLimit: "256Mi" } },
            { name: "nginx-run", emptyDir: { sizeLimit: "64Mi" } },
            { name: "tmp", emptyDir: { sizeLimit: "128Mi" } },
          ] : [
            { name: "crest-data", persistentVolumeClaim: { claimName: "crest-data" } },
            { name: "crest-cache", emptyDir: { sizeLimit: "1Gi" } },
            { name: "crest-logs", emptyDir: { sizeLimit: "1Gi" } },
            { name: "tmp", emptyDir: { sizeLimit: "512Mi" } },
          ],
        },
      },
    },
    status: { observedGeneration: 1, availableReplicas: item.replicas },
  };
}

function pods(label) {
  const name = /app\.kubernetes\.io\/name=([^,]+)/.exec(label)?.[1] || "crest";
  const component = /app\.kubernetes\.io\/component=([^,]+)/.exec(label)?.[1] || "frontend";
  const replicas = 2;
  return {
    items: Array.from({ length: replicas }, (_, index) => ({
      metadata: { name: `${name}-${index}`, labels: { "app.kubernetes.io/name": name, "app.kubernetes.io/component": component } },
      status: { phase: "Running", conditions: [{ type: "Ready", status: "True" }] },
    })),
  };
}

function configMap() {
  const prefix = "{ops01-prod-crest-core}:prod";
  return { data: {
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
    CREST_PROMETHEUS_ENABLED: "false",
    CREST_ORIGIN_LIST: "https://crest.example.com",
    CREST_DB_HOST: "obproxy.example.internal",
    CREST_DB_URL: "jdbc:oceanbase://obproxy.example.internal:2883",
    CREST_REDIS_CLUSTER_NODES: "redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379",
    CREST_REDIS_KEY_PREFIX: prefix,
    CREST_REDIS_CACHE_KEY_PREFIX: `${prefix}:cache:`,
    CREST_LOCK_KEY_PREFIX: `${prefix}:lock`,
    CREST_WEBSOCKET_BROADCAST_CHANNEL: `${prefix}:pubsub:websocket`,
    CREST_EXPORT_TASK_STREAM: `${prefix}:stream:export-task`,
    CREST_EXPORT_TASK_CONSUMER_GROUP: `${prefix}:group:export-workers`,
    CREST_SYNC_TASK_STREAM: `${prefix}:stream:dataset-sync-task`,
    CREST_SYNC_TASK_CONSUMER_GROUP: `${prefix}:group:dataset-sync-workers`,
    CREST_DATASOURCE_SYNC_TASK_STREAM: `${prefix}:stream:datasource-sync-task`,
    CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP: `${prefix}:group:datasource-sync-workers`,
    CREST_SCHEDULED_TASK_STREAM: `${prefix}:stream:scheduled-task`,
    CREST_SCHEDULED_TASK_CONSUMER_GROUP: `${prefix}:group:scheduled-workers`,
  } };
}

function secret(name) {
  if (name === "crest-db-secret") {
    return { metadata: { name }, data: {
      CREST_DB_USERNAME: b64("crest_app"),
      CREST_DB_PASSWORD: b64("database-secret-value"),
      CREST_AES_KEY: b64("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
      CREST_AES_IV: b64("BBBBBBBBBBBBBBBB"),
      CREST_INITIAL_PASSWORD: b64("initial-admin-secret"),
      CREST_TOKEN_SECRET: b64("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT"),
    } };
  }
  if (name === "crest-redis-secret") {
    return { metadata: { name }, data: {
      CREST_REDIS_USERNAME: b64("ops01-prod-crest-core-acl"),
      CREST_REDIS_PASSWORD: b64("redis-secret-value"),
    } };
  }
  return { metadata: { name }, type: "kubernetes.io/tls", data: { "tls.crt": b64("crt"), "tls.key": b64("key") } };
}

function service(name) {
  const component = name === "crest" ? "frontend" : "backend";
  return {
    spec: {
      type: "ClusterIP",
      selector: { "app.kubernetes.io/name": name, "app.kubernetes.io/component": component },
      ports: [{ port: 8100 }],
    },
  };
}

function pdb(name) {
  const mapping = {
    crest: ["frontend", 1],
    "crest-service": ["backend", 1],
  };
  const [component, minAvailable] = mapping[name];
  return {
    spec: {
      minAvailable,
      selector: { matchLabels: { "app.kubernetes.io/name": name, "app.kubernetes.io/component": component } },
    },
    status: { expectedPods: minAvailable, currentHealthy: minAvailable },
  };
}

function networkPolicy(name) {
  if (name === "crest-web") {
    return {
      spec: {
        podSelector: { matchLabels: { "app.kubernetes.io/name": "crest", "app.kubernetes.io/component": "frontend" } },
        policyTypes: ["Ingress"],
        ingress: [{ ports: [{ protocol: "TCP", port: 8080 }] }],
      },
    };
  }
  if (name === "crest-service") {
    return {
      spec: {
        podSelector: { matchLabels: { "app.kubernetes.io/name": "crest-service", "app.kubernetes.io/component": "backend" } },
        policyTypes: ["Ingress"],
        ingress: [
          { from: [{ podSelector: { matchLabels: { "app.kubernetes.io/name": "crest", "app.kubernetes.io/component": "frontend" } } }], ports: [{ protocol: "TCP", port: 8100 }] },
          { from: [{ namespaceSelector: { matchLabels: { "kubernetes.io/metadata.name": "monitoring" } }, podSelector: { matchLabels: { "app.kubernetes.io/name": "prometheus" } } }], ports: [{ protocol: "TCP", port: 8100 }] },
        ],
      },
    };
  }
  console.error(`unexpected networkpolicy: ${name}`);
  process.exit(2);
}

const argv = stripGlobal(args);
if (argv[0] === "rollout" && argv[1] === "status") {
  process.exit(0);
}
if (argv[0] !== "get") {
  console.error(`unexpected kubectl args: ${args.join(" ")}`);
  process.exit(2);
}

const kind = argv[1];
const name = argv[2];
let output;
if (kind === "namespace") output = { metadata: { name } };
else if (kind === "configmap") output = configMap();
else if (kind === "secret") output = secret(name);
else if (kind === "deployment") output = deployment(name);
else if (kind === "pods") output = pods(argv[argv.indexOf("-l") + 1]);
else if (kind === "service") output = service(name);
else if (kind === "endpoints") output = { subsets: [{ addresses: [{ ip: "10.0.0.1" }] }] };
else if (kind === "ingress") output = { spec: { ingressClassName: "nginx", tls: [{ hosts: ["crest.example.com"], secretName: "crest-tls" }], rules: [{ host: "crest.example.com" }] }, status: { loadBalancer: { ingress: [{ hostname: "lb.example.com" }] } } };
else if (kind === "serviceaccount") output = { automountServiceAccountToken: false };
else if (kind === "pvc") output = { status: { phase: "Bound" }, spec: { accessModes: ["ReadWriteMany"] } };
else if (kind === "pdb") output = pdb(name);
else if (kind === "networkpolicy") output = networkPolicy(name);
else {
  console.error(`unexpected kind: ${kind}`);
  process.exit(2);
}

process.stdout.write(JSON.stringify(output));
EOF
chmod +x "${fake_bin}/kubectl"

PATH="${fake_bin}:${PATH}" \
  node scripts/production-runtime-check.mjs --namespace crest-runtime-test --context kind-runtime-test >/dev/null

negative_log="${test_root}/optional-secret.log"
if PATH="${fake_bin}:${PATH}" CREST_RUNTIME_TEST_OPTIONAL_SECRET=true \
  node scripts/production-runtime-check.mjs --namespace crest-runtime-test --context kind-runtime-test >"${negative_log}" 2>&1; then
  fail "runtime check should reject optional DB Secret refs"
fi

grep -q "crest-service must require crest-db-secret" "${negative_log}" \
  || fail "optional DB Secret ref failure did not explain the runtime policy"

echo "test-production-runtime-check: passed"
