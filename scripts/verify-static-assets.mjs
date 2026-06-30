#!/usr/bin/env node

import { createHash } from "node:crypto";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const frontendDist = path.join(repoRoot, "core/core-frontend/dist");
const backendStatic = path.join(repoRoot, "core/core-backend/src/main/resources/static");

function fail(message) {
  console.error(`static-check: ${message}`);
  process.exit(1);
}

function assertDirectory(directory) {
  if (!existsSync(directory) || !statSync(directory).isDirectory()) {
    fail(`missing directory ${path.relative(repoRoot, directory)}`);
  }
}

function filesUnder(root) {
  const files = [];
  function walk(directory) {
    for (const name of readdirSync(directory)) {
      const filePath = path.join(directory, name);
      const stat = statSync(filePath);
      if (stat.isDirectory()) {
        walk(filePath);
      } else if (stat.isFile()) {
        files.push(path.relative(root, filePath).split(path.sep).join("/"));
      }
    }
  }
  walk(root);
  return files.sort();
}

function digest(filePath) {
  return createHash("sha256").update(readFileSync(filePath)).digest("hex");
}

assertDirectory(frontendDist);
assertDirectory(backendStatic);

const distFiles = filesUnder(frontendDist);
const staticFiles = filesUnder(backendStatic);
const distSet = new Set(distFiles);
const staticSet = new Set(staticFiles);

const missing = distFiles.filter((file) => !staticSet.has(file));
const stale = staticFiles.filter((file) => !distSet.has(file));
const changed = distFiles.filter((file) => staticSet.has(file)
  && digest(path.join(frontendDist, file)) !== digest(path.join(backendStatic, file)));

if (missing.length || stale.length || changed.length) {
  if (missing.length) {
    console.error(`static-check: missing in backend static: ${missing.join(", ")}`);
  }
  if (stale.length) {
    console.error(`static-check: stale in backend static: ${stale.join(", ")}`);
  }
  if (changed.length) {
    console.error(`static-check: content mismatch: ${changed.join(", ")}`);
  }
  fail("frontend dist and backend static resources are not identical");
}

console.log(`static-check: ${distFiles.length} files match`);
