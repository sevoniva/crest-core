#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";

const [productionEvidenceDir, containerReportDir] = process.argv.slice(2);

function fail(message) {
  console.error(`verify-production-image-scan-coverage: ${message}`);
  process.exit(1);
}

if (!productionEvidenceDir || !containerReportDir) {
  fail("usage: verify-production-image-scan-coverage.mjs <production-evidence-dir> <container-report-dir>");
}

if (!existsSync(productionEvidenceDir)) {
  fail(`missing production evidence directory: ${productionEvidenceDir}`);
}

if (!existsSync(containerReportDir)) {
  fail(`missing container report directory: ${containerReportDir}`);
}

function readJson(filePath, description) {
  try {
    return JSON.parse(readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`${description} must be valid JSON: ${error.message}`);
  }
}

const workloadFiles = [
  "statefulset-crest.json",
  "statefulset-crest-service.json",
];

const deployedImages = new Set();
for (const fileName of workloadFiles) {
  const filePath = path.join(productionEvidenceDir, fileName);
  if (!existsSync(filePath)) {
    fail(`production evidence missing ${fileName}`);
  }
  const workload = readJson(filePath, fileName);
  if (workload?.kind !== "StatefulSet") {
    fail(`${fileName} must describe a StatefulSet`);
  }
  const containers = workload?.spec?.template?.spec?.containers;
  if (!Array.isArray(containers) || containers.length === 0) {
    fail(`${fileName} must include StatefulSet containers`);
  }
  for (const container of containers) {
    const image = container?.image;
    if (!image || typeof image !== "string") {
      fail(`${fileName} contains a container without an image`);
    }
    if (image.endsWith(":latest")) {
      fail(`${fileName} must not deploy latest image tags`);
    }
    deployedImages.add(image);
  }
}

const reportFiles = readdirSync(containerReportDir)
  .filter((name) => /^trivy-.+\.json$/u.test(name))
  .sort();
if (reportFiles.length === 0) {
  fail(`missing Trivy JSON reports under ${containerReportDir}`);
}

const scannedImages = new Set();
for (const fileName of reportFiles) {
  const report = readJson(path.join(containerReportDir, fileName), fileName);
  const metadataImage = report.Metadata?.CrestContainerImage;
  let scannedImage = metadataImage || report.ArtifactName;
  if (report.ArtifactType === "filesystem") {
    if (report.Metadata?.CrestScanMode !== "rootfs-fallback") {
      fail(`${fileName} must describe a container_image artifact or rootfs-fallback scan`);
    }
    scannedImage = metadataImage;
  } else if (report.ArtifactType !== "container_image") {
    fail(`${fileName} must describe a container_image artifact or rootfs-fallback scan`);
  }
  if (!scannedImage || typeof scannedImage !== "string") {
    fail(`${fileName} must include the scanned image reference`);
  }
  scannedImages.add(scannedImage);
}

const missing = [...deployedImages].filter((image) => !scannedImages.has(image)).sort();
if (missing.length > 0) {
  fail(`deployed images missing Trivy scan coverage: ${missing.join(", ")}`);
}

console.log(`verify-production-image-scan-coverage: passed deployed_images=${deployedImages.size} scanned_images=${scannedImages.size}`);
