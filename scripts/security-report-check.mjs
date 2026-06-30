#!/usr/bin/env node

import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

const reportDir = process.argv[2] || process.env.CREST_SECURITY_REPORT_DIR || "reports/security";
const failures = [];
const summaries = [];

function fail(message) {
  failures.push(message);
}

function readJson(fileName) {
  const filePath = path.join(reportDir, fileName);
  if (!existsSync(filePath)) {
    fail(`missing ${fileName}`);
    return undefined;
  }
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`${fileName} must be valid JSON: ${error.message}`);
    return undefined;
  }
}

function requireNonEmptyFile(fileName) {
  const filePath = path.join(reportDir, fileName);
  if (!existsSync(filePath)) {
    fail(`missing ${fileName}`);
    return;
  }
  if (statSync(filePath).size === 0) {
    fail(`${fileName} must not be empty`);
  }
}

function readKeyValueFile(fileName) {
  const filePath = path.join(reportDir, fileName);
  if (!existsSync(filePath)) {
    fail(`missing ${fileName}`);
    return new Map();
  }
  const fields = new Map();
  for (const line of readFileSync(filePath, "utf8").split(/\r?\n/u)) {
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const index = line.indexOf("=");
    fields.set(line.slice(0, index), line.slice(index + 1));
  }
  return fields;
}

function countOsvVulnerabilities(value) {
  if (Array.isArray(value)) {
    return value.reduce((sum, item) => sum + countOsvVulnerabilities(item), 0);
  }
  if (!value || typeof value !== "object") {
    return 0;
  }
  return Object.entries(value).reduce((sum, [key, item]) => {
    if (key === "vulnerabilities" && Array.isArray(item)) {
      return sum + item.length;
    }
    return sum + countOsvVulnerabilities(item);
  }, 0);
}

function checkSemgrep() {
  const data = readJson("semgrep.json");
  const findings = Array.isArray(data?.results) ? data.results.length : 0;
  summaries.push(`semgrep_findings=${findings}`);
  if (!Array.isArray(data?.results)) {
    fail("semgrep.json must contain a results array");
  } else if (findings !== 0) {
    fail(`Semgrep findings must be 0, got ${findings}`);
  }
}

function checkGitleaks() {
  const data = readJson("gitleaks.json");
  const findings = Array.isArray(data) ? data.length : 0;
  summaries.push(`gitleaks_findings=${findings}`);
  if (!Array.isArray(data)) {
    fail("gitleaks.json must be a JSON array");
  } else if (findings !== 0) {
    fail(`Gitleaks findings must be 0, got ${findings}`);
  }
}

function checkPnpmAudit() {
  const data = readJson("pnpm-audit.json");
  const vulnerabilities = data?.metadata?.vulnerabilities;
  if (!vulnerabilities || typeof vulnerabilities !== "object") {
    fail("pnpm-audit.json must contain metadata.vulnerabilities");
    summaries.push("pnpm_vulnerabilities=missing");
    return;
  }
  const severities = ["info", "low", "moderate", "high", "critical"];
  const counts = Object.fromEntries(
    severities.map((severity) => [severity, Number(vulnerabilities[severity] || 0)]),
  );
  const total = Number(vulnerabilities.total ?? Object.values(counts).reduce((sum, count) => sum + count, 0));
  summaries.push(`pnpm_vulnerabilities=${total}`);
  for (const [severity, count] of Object.entries(counts)) {
    if (!Number.isInteger(count) || count < 0) {
      fail(`pnpm ${severity} vulnerability count must be a non-negative integer`);
    }
  }
  if (!Number.isInteger(total) || total < 0) {
    fail("pnpm total vulnerability count must be a non-negative integer");
  } else if (total !== 0) {
    fail(`pnpm production audit vulnerabilities must be 0, got ${total}`);
  }
}

function checkOsvReport(fileName) {
  const data = readJson(fileName);
  const vulnerabilities = countOsvVulnerabilities(data);
  summaries.push(`${fileName.replace(/[^a-z0-9]+/gi, "_")}_vulnerabilities=${vulnerabilities}`);
  if (vulnerabilities !== 0) {
    fail(`${fileName} OSV vulnerabilities must be 0, got ${vulnerabilities}`);
  }
}

function checkCycloneDxBom() {
  const data = readJson("crest-bom.json");
  const components = Array.isArray(data?.components) ? data.components.length : 0;
  summaries.push(`cyclonedx_components=${components}`);
  if (!Array.isArray(data?.components) || components === 0) {
    fail("crest-bom.json must contain at least one CycloneDX component");
  }
}

function checkFrontendLicenses() {
  const data = readJson("frontend-licenses.json");
  const groups = data && typeof data === "object" && !Array.isArray(data) ? Object.keys(data).length : 0;
  summaries.push(`frontend_license_groups=${groups}`);
  if (!data || typeof data !== "object" || Array.isArray(data) || groups === 0) {
    fail("frontend-licenses.json must contain license groups");
  }
}

function checkLicensePolicy() {
  const fields = readKeyValueFile("license-policy.txt");
  const status = fields.get("status");
  const violations = fields.get("violations");
  summaries.push(`license_policy_status=${status || "missing"}`);
  summaries.push(`license_policy_violations=${violations || "missing"}`);
  if (status !== "passed") {
    fail(`license-policy.txt must record status=passed, got ${status || "missing"}`);
  }
  if (violations !== "0") {
    fail(`license-policy.txt must record violations=0, got ${violations || "missing"}`);
  }
}

checkSemgrep();
checkGitleaks();
requireNonEmptyFile("maven-dependency-tree.txt");
checkCycloneDxBom();
checkPnpmAudit();
checkFrontendLicenses();
checkOsvReport("osv-frontend.json");
checkOsvReport("osv-maven-sbom.json");
checkLicensePolicy();

for (const summary of summaries) {
  console.log(`security-report-check: ${summary}`);
}

if (failures.length > 0) {
  for (const failure of failures) {
    console.error(`security-report-check: ${failure}`);
  }
  process.exit(1);
}

console.log("security-report-check: passed");
