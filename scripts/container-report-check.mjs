#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";

const reportDir = process.argv[2] || process.env.CREST_CONTAINER_SCAN_REPORT_DIR || "reports/container";
const requiredSeverities = (process.env.CREST_CONTAINER_SCAN_BLOCK_SEVERITIES
  || process.env.CREST_TRIVY_SEVERITY
  || "HIGH,CRITICAL")
  .split(",")
  .map((item) => item.trim().toUpperCase())
  .filter(Boolean);
const failures = [];
const totals = new Map();

function fail(message) {
  failures.push(message);
}

if (requiredSeverities.length === 0) {
  fail("at least one blocked container severity is required");
}

if (!existsSync(reportDir)) {
  fail(`missing container scan report directory: ${reportDir}`);
} else {
  const reportFiles = readdirSync(reportDir)
    .filter((name) => /^trivy-.+\.json$/u.test(name))
    .sort();

  if (reportFiles.length === 0) {
    fail(`missing Trivy JSON reports under ${reportDir}`);
  }

  for (const fileName of reportFiles) {
    const filePath = path.join(reportDir, fileName);
    let report;
    try {
      report = JSON.parse(readFileSync(filePath, "utf8"));
    } catch (error) {
      fail(`${fileName} must be valid JSON: ${error.message}`);
      continue;
    }

    const isRootfsFallback = report.ArtifactType === "filesystem"
      && report.Metadata?.CrestScanMode === "rootfs-fallback"
      && report.Metadata?.CrestContainerImage;
    if (report.ArtifactType !== "container_image" && !isRootfsFallback) {
      fail(`${fileName} must describe a container_image artifact or an approved rootfs-fallback filesystem artifact`);
    }
    if (!report.ArtifactName) {
      fail(`${fileName} must include ArtifactName`);
    }
    if (Object.hasOwn(report.Metadata || {}, "CrestJavaArtifactScanSkipped")
      && typeof report.Metadata.CrestJavaArtifactScanSkipped !== "boolean") {
      fail(`${fileName} Metadata.CrestJavaArtifactScanSkipped must be a boolean`);
    }
    if (report.Metadata?.CrestJavaArtifactScanSkipped === true
      && !report.Metadata.CrestJavaArtifactScanReason) {
      fail(`${fileName} must explain CrestJavaArtifactScanReason when Java artifact scanning is skipped`);
    }
    if (!Array.isArray(report.Results)) {
      fail(`${fileName} must include a Results array`);
      continue;
    }

    const counts = new Map();
    for (const result of report.Results) {
      for (const vulnerability of result.Vulnerabilities || []) {
        const severity = String(vulnerability.Severity || "UNKNOWN").toUpperCase();
        counts.set(severity, (counts.get(severity) || 0) + 1);
        totals.set(severity, (totals.get(severity) || 0) + 1);
      }
    }

    const blocked = requiredSeverities
      .map((severity) => [severity, counts.get(severity) || 0])
      .filter(([, count]) => count > 0);
    if (blocked.length > 0) {
      fail(`${fileName} has blocked vulnerabilities: ${blocked.map(([severity, count]) => `${severity}=${count}`).join(", ")}`);
    }

    const summary = requiredSeverities
      .map((severity) => `${severity}=${counts.get(severity) || 0}`)
      .join(",");
    console.log(`container-report-check: ${fileName} ${summary}`);
  }
}

const totalSummary = requiredSeverities
  .map((severity) => `${severity}=${totals.get(severity) || 0}`)
  .join(",");
console.log(`container-report-check: total ${totalSummary}`);

if (failures.length > 0) {
  for (const failure of failures) {
    console.error(`container-report-check: ${failure}`);
  }
  process.exit(1);
}

console.log("container-report-check: passed");
