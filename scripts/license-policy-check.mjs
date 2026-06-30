#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const repoRoot = process.cwd();
const policyPath = process.env.CREST_LICENSE_POLICY_FILE || "config/license-policy.json";
const mavenBomPath = process.env.CREST_MAVEN_BOM_FILE || "reports/security/crest-bom.json";
const frontendLicensesPath = process.env.CREST_FRONTEND_LICENSES_FILE || "reports/security/frontend-licenses.json";
const reportPath = process.env.CREST_LICENSE_POLICY_REPORT || "reports/security/license-policy.txt";

function fail(message, reportLines = []) {
  writeReport("failed", message, reportLines);
  console.error(`license-policy-check: ${message}`);
  process.exit(1);
}

function readJson(path) {
  if (!existsSync(path)) {
    fail(`missing required JSON file: ${path}`);
  }
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch (error) {
    fail(`invalid JSON in ${path}: ${error.message}`);
  }
}

function normalizeLicense(license) {
  return String(license || "")
    .replace(/\s+/gu, " ")
    .trim();
}

function mavenLicense(component) {
  const licenses = Array.isArray(component.licenses) ? component.licenses : [];
  const values = licenses.map((entry) => {
    if (entry?.license?.id) return entry.license.id;
    if (entry?.license?.name) return entry.license.name;
    if (entry?.expression) return entry.expression;
    return "NOASSERTION";
  });
  return normalizeLicense(values.join(" OR ") || "NOASSERTION");
}

function exceptionKey(ecosystem, name, version, license) {
  return `${ecosystem}\u0000${name}\u0000${version || ""}\u0000${license}`;
}

function hasException(exceptions, ecosystem, name, version, license) {
  return exceptions.has(exceptionKey(ecosystem, name, version, license));
}

function normalizeComparable(value) {
  return normalizeLicense(value).toLowerCase();
}

function isAllowed(license, allowedLicenses) {
  return allowedLicenses.has(license);
}

function isDenied(license, deniedKeywords) {
  const comparable = normalizeComparable(license);
  return deniedKeywords.some((keyword) => comparable.includes(keyword));
}

function evaluate(item, allowedLicenses, deniedKeywords, exceptions, reviewed) {
  const license = normalizeLicense(item.license);
  const hasReviewedException = hasException(exceptions, item.ecosystem, item.name, item.version, license);
  if (hasReviewedException) {
    reviewed.push(item);
    return null;
  }
  if (isDenied(license, deniedKeywords)) {
    return `${item.ecosystem}:${item.name}:${item.version} has denied license '${license}'`;
  }
  if (!isAllowed(license, allowedLicenses)) {
    return `${item.ecosystem}:${item.name}:${item.version} has unreviewed license '${license}'`;
  }
  return null;
}

function writeReport(status, message, lines) {
  mkdirSync(dirname(reportPath), { recursive: true });
  const body = [
    `status=${status}`,
    `policy_file=${policyPath}`,
    `maven_bom=${mavenBomPath}`,
    `frontend_licenses=${frontendLicensesPath}`,
    ...lines,
  ];
  if (message) {
    body.push(`message=${message}`);
  }
  writeFileSync(reportPath, `${body.join("\n")}\n`);
}

const policy = readJson(policyPath);
if (!Array.isArray(policy.allowedLicenses)) {
  fail("allowedLicenses must be an array");
}
if (!Array.isArray(policy.deniedLicenseKeywords)) {
  fail("deniedLicenseKeywords must be an array");
}
const allowedLicenses = new Set(policy.allowedLicenses.map(normalizeLicense));
const deniedKeywords = policy.deniedLicenseKeywords.map(normalizeComparable).filter(Boolean);
const rawExceptions = Array.isArray(policy.reviewedExceptions) ? policy.reviewedExceptions : [];
const exceptions = new Set();
for (const exception of rawExceptions) {
  for (const field of ["ecosystem", "name", "version", "license", "rationale"]) {
    if (!exception[field]) {
      fail(`reviewed exception for ${exception.name || "unknown"} is missing ${field}`);
    }
  }
  exceptions.add(exceptionKey(exception.ecosystem, exception.name, exception.version, normalizeLicense(exception.license)));
}

const mavenBom = readJson(mavenBomPath);
const frontendLicenses = readJson(frontendLicensesPath);
const items = [];

for (const component of mavenBom.components || []) {
  items.push({
    ecosystem: "maven",
    name: component.group ? `${component.group}:${component.name}` : component.name,
    version: component.version || "",
    license: mavenLicense(component),
  });
}

for (const [license, packages] of Object.entries(frontendLicenses)) {
  if (!Array.isArray(packages)) {
    fail(`frontend license group '${license}' must be an array`);
  }
  for (const pkg of packages) {
    for (const version of pkg.versions || [""]) {
      items.push({
        ecosystem: "npm",
        name: pkg.name,
        version,
        license,
      });
    }
  }
}

const violations = [];
const reviewed = [];
for (const item of items) {
  const violation = evaluate(item, allowedLicenses, deniedKeywords, exceptions, reviewed);
  if (violation) {
    violations.push(violation);
  }
}

const reportLines = [
  `maven_components=${(mavenBom.components || []).length}`,
  `frontend_license_groups=${Object.keys(frontendLicenses).length}`,
  `license_items=${items.length}`,
  `reviewed_exceptions=${reviewed.length}`,
  `violations=${violations.length}`,
];
if (reviewed.length > 0) {
  reportLines.push("reviewed_exception_items:");
  for (const item of reviewed) {
    reportLines.push(`${item.ecosystem}:${item.name}:${item.version}:${normalizeLicense(item.license)}`);
  }
}
if (violations.length > 0) {
  reportLines.push("violation_items:");
  reportLines.push(...violations);
  writeReport("failed", "license policy has unreviewed or denied licenses", reportLines);
  console.error(violations.join("\n"));
  process.exit(1);
}

writeReport("passed", "", reportLines);
console.log(`license-policy-check: passed; report written to ${reportPath}`);
