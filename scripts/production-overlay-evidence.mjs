#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const [overlayArg, outputArg] = process.argv.slice(2);
const overlay = overlayArg || process.env.CREST_PRODUCTION_OVERLAY_DIR || ".local/production-overlay";
const output = outputArg || process.env.CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR || "reports/readiness/production-overlay-evidence";

function fail(message) {
  console.error(`production-overlay-evidence: ${message}`);
  process.exit(1);
}

function info(message) {
  console.log(`production-overlay-evidence: ${message}`);
}

function sha256(file) {
  return createHash("sha256").update(readFileSync(file)).digest("hex");
}

function normalize(target) {
  return path.resolve(repoRoot, target);
}

function insideRepo(normalized) {
  return normalized === repoRoot || normalized.startsWith(`${repoRoot}${path.sep}`);
}

function assertSafeOverlayDir(target) {
  if (!target || target === "." || target === ".." || target === "/") {
    fail(`CREST_PRODUCTION_OVERLAY_DIR is too broad to read overlay from: ${target}`);
  }
  const normalized = normalize(target);
  const forbiddenTree = [
    path.join(repoRoot, ".git"),
    path.join(repoRoot, "deploy"),
    path.join(repoRoot, "reports"),
  ];
  if (!insideRepo(normalized)) {
    fail(`CREST_PRODUCTION_OVERLAY_DIR must stay inside the repository: ${target}`);
  }
  if (normalized === repoRoot
    || normalized === path.dirname(repoRoot)
    || forbiddenTree.some((item) => normalized === item || normalized.startsWith(`${item}${path.sep}`))) {
    fail(`CREST_PRODUCTION_OVERLAY_DIR is too broad to read overlay from: ${target}`);
  }
  return normalized;
}

function assertSafeOutputDir(target) {
  if (!target || target === "." || target === ".." || target === "/") {
    fail(`CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR is too broad to overwrite: ${target}`);
  }
  const normalized = normalize(target);
  const forbiddenExact = [
    repoRoot,
    path.dirname(repoRoot),
    path.join(repoRoot, ".local"),
    path.join(repoRoot, "reports"),
    path.join(repoRoot, "reports", "readiness"),
  ];
  const forbiddenTree = [
    path.join(repoRoot, ".git"),
    path.join(repoRoot, "deploy"),
  ];
  if (!insideRepo(normalized)) {
    fail(`CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR must stay inside the repository: ${target}`);
  }
  if (forbiddenExact.includes(normalized)
    || forbiddenTree.some((item) => normalized === item || normalized.startsWith(`${item}${path.sep}`))) {
    fail(`CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR is too broad to overwrite: ${target}`);
  }
  return normalized;
}

function yamlFile(file) {
  return file.endsWith(".yaml") || file.endsWith(".yml");
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

function renderOverlayJson(files) {
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

function decodedSecretLength(value, source) {
  if (source === "data") {
    return Buffer.from(String(value || ""), "base64").length;
  }
  return Buffer.byteLength(String(value || ""), "utf8");
}

function sanitizeSecret(secret) {
  const data = secret.data || {};
  const stringData = secret.stringData || {};
  const keys = [...new Set([...Object.keys(data), ...Object.keys(stringData)])].sort();
  return {
    apiVersion: secret.apiVersion,
    kind: secret.kind,
    metadata: {
      name: secret.metadata?.name,
      namespace: secret.metadata?.namespace,
      labels: secret.metadata?.labels,
      annotations: secret.metadata?.annotations,
    },
    type: secret.type,
    sanitizedData: Object.fromEntries(keys.map((key) => {
      const source = Object.prototype.hasOwnProperty.call(stringData, key) ? "stringData" : "data";
      const value = source === "stringData" ? stringData[key] : data[key];
      return [key, {
        present: true,
        decodedLength: decodedSecretLength(value, source),
      }];
    })),
  };
}

function sanitizeResource(resource) {
  if (resource.kind === "Secret") {
    return sanitizeSecret(resource);
  }
  return resource;
}

function collectSecretMaterial(secrets) {
  const values = [];
  for (const secret of secrets) {
    for (const value of Object.values(secret.stringData || {})) {
      const text = String(value || "");
      if (text.length >= 8) {
        values.push(text);
      }
    }
    for (const value of Object.values(secret.data || {})) {
      const encoded = String(value || "");
      if (encoded.length >= 8) {
        values.push(encoded);
      }
      const decoded = Buffer.from(encoded, "base64").toString("utf8");
      if (decoded.length >= 8) {
        values.push(decoded);
      }
    }
  }
  return values;
}

function writeManifest(outputDir, files) {
  const manifest = path.join(outputDir, "overlay-evidence-manifest.sha256");
  const lines = files
    .map((file) => `${sha256(path.join(outputDir, file))}  ${file}`)
    .join("\n");
  writeFileSync(manifest, `${lines}\n`);
  return manifest;
}

const overlayPath = assertSafeOverlayDir(overlay);
const outputDir = assertSafeOutputDir(output);

if (!statSync(overlayPath, { throwIfNoEntry: false })?.isDirectory()) {
  fail(`missing production overlay directory: ${overlay}`);
}

const files = manifestFiles(overlayPath);
if (files.length === 0) {
  fail(`production overlay contains no YAML manifests: ${overlay}`);
}

let rendered;
try {
  rendered = renderOverlayJson(files);
} catch (error) {
  fail(`failed to parse Kubernetes YAML: ${error.stderr?.toString?.().trim() || error.message}`);
}

const items = rendered.items || [];
const secrets = items.filter((item) => item.kind === "Secret");
if (secrets.length === 0) {
  fail("production overlay evidence requires at least one Secret resource");
}

rmSync(outputDir, { recursive: true, force: true });
mkdirSync(outputDir, { recursive: true });

const sanitizedSecrets = { items: secrets.map(sanitizeSecret) };
const sanitizedResources = {
  kind: "List",
  items: items.map(sanitizeResource),
};

const resourceFile = "resources-sanitized.json";
const secretFile = "secrets-sanitized.json";
writeFileSync(path.join(outputDir, resourceFile), `${JSON.stringify(sanitizedResources, null, 2)}\n`);
writeFileSync(path.join(outputDir, secretFile), `${JSON.stringify(sanitizedSecrets, null, 2)}\n`);

const serializedEvidence = [
  readFileSync(path.join(outputDir, resourceFile), "utf8"),
  readFileSync(path.join(outputDir, secretFile), "utf8"),
].join("\n");
for (const value of collectSecretMaterial(secrets)) {
  if (serializedEvidence.includes(value)) {
    fail("sanitized overlay evidence leaked Secret material");
  }
}

execFileSync("node", [
  "scripts/verify-sanitized-kubernetes-secrets.mjs",
  path.join(outputDir, secretFile),
  "crest-db-secret",
  "crest-redis-secret",
], {
  cwd: repoRoot,
  stdio: ["ignore", "pipe", "pipe"],
});

const counts = items.reduce((accumulator, item) => {
  accumulator[item.kind] = (accumulator[item.kind] || 0) + 1;
  return accumulator;
}, {});
const summaryFile = "summary.txt";
const summaryLines = [
  "Crest Core production overlay sanitized evidence",
  `timestamp_utc=${new Date().toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z")}`,
  `overlay_dir=${overlay}`,
  `resource_count=${items.length}`,
  `secret_count=${secrets.length}`,
  ...Object.entries(counts).sort().map(([kind, count]) => `resource_kind_${kind}=${count}`),
  ...secrets.map((secret) => `secret_name=${secret.metadata?.name || "unknown"}`),
  `sanitized_resources=${resourceFile}`,
  `sanitized_resources_sha256=${sha256(path.join(outputDir, resourceFile))}`,
  `sanitized_secrets=${secretFile}`,
  `sanitized_secrets_sha256=${sha256(path.join(outputDir, secretFile))}`,
  "evidence_manifest=overlay-evidence-manifest.sha256",
];
writeFileSync(path.join(outputDir, summaryFile), `${summaryLines.join("\n")}\n`);

const manifest = writeManifest(outputDir, [resourceFile, secretFile, summaryFile]);

info(`wrote sanitized production overlay evidence to ${outputDir}`);
info(`manifest=${manifest}`);
