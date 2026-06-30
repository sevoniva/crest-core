#!/usr/bin/env node

import fs from "node:fs";

const [file, ...requiredSecretNames] = process.argv.slice(2);

function fail(message) {
  console.error(`verify-sanitized-kubernetes-secrets: ${message}`);
  process.exit(1);
}

if (!file) {
  fail("usage: verify-sanitized-kubernetes-secrets.mjs <secrets-sanitized.json> [required-secret...]");
}

let parsed;
try {
  parsed = JSON.parse(fs.readFileSync(file, "utf8"));
} catch (error) {
  fail(`invalid JSON in ${file}: ${error.message}`);
}

const items = parsed.items;
if (!Array.isArray(items) || items.length === 0) {
  fail("sanitized secret evidence must contain a non-empty items array");
}

const seenNames = new Set();
for (const [index, secret] of items.entries()) {
  const name = secret?.metadata?.name;
  if (!name || typeof name !== "string") {
    fail(`secret item ${index + 1} must preserve metadata.name`);
  }
  seenNames.add(name);
  if (Object.prototype.hasOwnProperty.call(secret, "data")) {
    fail(`${name} must not contain Kubernetes Secret data`);
  }
  if (Object.prototype.hasOwnProperty.call(secret, "stringData")) {
    fail(`${name} must not contain Kubernetes Secret stringData`);
  }
  const sanitizedData = secret.sanitizedData;
  if (!sanitizedData || typeof sanitizedData !== "object" || Array.isArray(sanitizedData)) {
    fail(`${name} must contain sanitizedData metadata`);
  }
  for (const [key, value] of Object.entries(sanitizedData)) {
    if (!key) {
      fail(`${name} contains an empty secret key name`);
    }
    if (!value || typeof value !== "object" || Array.isArray(value)) {
      fail(`${name}.${key} sanitized value must be metadata`);
    }
    if (value.present !== true) {
      fail(`${name}.${key} must record present=true`);
    }
    if (!Number.isInteger(value.decodedLength) || value.decodedLength < 0) {
      fail(`${name}.${key} must record a non-negative decodedLength`);
    }
    for (const forbiddenField of ["value", "raw", "encoded", "base64", "secret"]) {
      if (Object.prototype.hasOwnProperty.call(value, forbiddenField)) {
        fail(`${name}.${key} must not contain ${forbiddenField}`);
      }
    }
  }
}

for (const requiredName of requiredSecretNames) {
  if (!seenNames.has(requiredName)) {
    fail(`sanitized secret evidence missing ${requiredName}`);
  }
}

console.log("verify-sanitized-kubernetes-secrets: passed");
