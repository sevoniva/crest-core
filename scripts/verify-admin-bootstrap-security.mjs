#!/usr/bin/env node

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = dirname(dirname(fileURLToPath(import.meta.url)));
const legacyAdminMd5 = "21232f297a57a5a743894a0e4a801fc3";
const bootstrapSentinel = "{CREST_INITIAL_PASSWORD_REQUIRED}";

const files = [
  "core/core-backend/src/main/java/io/crest/substitute/permissions/user/CrestUserManage.java",
  "core/core-backend/src/main/resources/db/migration-ob-oracle/V1.0.0.1__initial_schema.sql",
  "installer/init-sql/ob-oracle/crest-core-schema.sql",
];

const read = (relativePath) =>
  readFileSync(join(repoRoot, relativePath), "utf8");

const violations = [];

for (const file of files) {
  const content = read(file);
  if (content.includes(legacyAdminMd5)) {
    violations.push(`${file} contains the legacy admin/admin MD5 hash`);
  }
}

const javaSource = read(files[0]);
if (!javaSource.includes(`UNINITIALIZED_ADMIN_PASSWORD_HASH = "${bootstrapSentinel}"`)) {
  violations.push("CrestUserManage must define the admin bootstrap sentinel constant");
}
if (!javaSource.includes("Strings.CS.equals(passwordHash, UNINITIALIZED_ADMIN_PASSWORD_HASH)")) {
  violations.push("CrestUserManage must only rotate the admin password when the sentinel is present");
}
if (!javaSource.includes("PasswordEncoder.encode(initialPassword())")) {
  violations.push("CrestUserManage must encode the configured initial admin password");
}
if (javaSource.includes("LEGACY_ADMIN_PASSWORD_HASH")) {
  violations.push("CrestUserManage must not keep a legacy admin hash constant");
}

for (const file of files.slice(1)) {
  const content = read(file);
  if (!content.includes(bootstrapSentinel)) {
    violations.push(`${file} must seed admin with the bootstrap sentinel`);
  }
}

if (violations.length > 0) {
  console.error("admin bootstrap security check failed:");
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("admin bootstrap security check passed");
