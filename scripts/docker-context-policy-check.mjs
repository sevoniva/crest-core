#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(process.argv[2] || path.join(__dirname, '..'));
const dockerignorePath = path.join(repoRoot, '.dockerignore');
const dockerfiles = ['Dockerfile.frontend', 'Dockerfile.backend'];

const requiredIgnorePatterns = [
  '.git',
  '.github',
  '.cache',
  '.crest-local',
  '.local',
  'reports',
  'private',
  'private-tests',
  'secrets',
  'tmp',
  'temp',
  '.tmp',
  '.temp',
  'node_modules',
  '**/node_modules',
  'runtime',
  'extensions',
  'config',
  'docs',
  'installer',
  'scripts',
  'sdk',
  'core/*',
  'core/core-frontend/*',
  'core/core-backend/*',
  'core/core-backend/target/*',
  '**/target',
];

const allowedNegations = new Set([
  '!core/core-frontend',
  '!core/core-frontend/dist',
  '!core/core-frontend/dist/**',
  '!core/core-backend',
  '!core/core-backend/target',
  '!core/core-backend/target/CoreApplication.jar',
  '!drivers/oceanbase-client-2.4.17.jar',
]);

const allowedCopySources = [
  'deploy/nginx/nginx.conf',
  'deploy/nginx/default.conf',
  'core/core-frontend/dist/',
  'core/core-backend/target/CoreApplication.jar',
  'drivers',
  'staticResource',
];

const violations = [];

function fail(message) {
  violations.push(message);
}

function readRequiredFile(filePath) {
  if (!fs.existsSync(filePath)) {
    fail(`missing required file: ${path.relative(repoRoot, filePath) || filePath}`);
    return '';
  }
  return fs.readFileSync(filePath, 'utf8');
}

function meaningfulLines(content) {
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'));
}

function normalizePattern(pattern) {
  return pattern.replace(/\/+$/, '');
}

function parseDockerfileInstructions(content) {
  const instructions = [];
  let current = '';
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.replace(/\s+#.*$/, '').trimEnd();
    if (!line.trim() || line.trimStart().startsWith('#')) {
      continue;
    }
    current += current ? ` ${line.trim()}` : line.trim();
    if (current.endsWith('\\')) {
      current = current.slice(0, -1).trimEnd();
      continue;
    }
    instructions.push(current);
    current = '';
  }
  if (current) {
    instructions.push(current);
  }
  return instructions;
}

function splitInstructionArgs(args) {
  const trimmed = args.trim();
  if (trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      return [];
    }
  }
  return trimmed
    .split(/\s+/)
    .map((token) => token.replace(/^['"]|['"]$/g, ''))
    .filter(Boolean);
}

function isAllowedCopySource(source) {
  const normalized = source.replace(/^\.\/+/, '');
  return allowedCopySources.some((allowed) => {
    if (allowed.endsWith('/')) {
      return normalized === allowed || normalized.startsWith(allowed);
    }
    return normalized === allowed || normalized.startsWith(`${allowed}/`);
  });
}

const dockerignore = readRequiredFile(dockerignorePath);
const dockerignoreLines = meaningfulLines(dockerignore);
const dockerignorePatterns = new Set(dockerignoreLines.map(normalizePattern));

for (const pattern of requiredIgnorePatterns) {
  if (!dockerignorePatterns.has(normalizePattern(pattern))) {
    fail(`.dockerignore must include ${pattern}`);
  }
}

for (const line of dockerignoreLines) {
  if (!line.startsWith('!')) {
    continue;
  }
  const normalized = normalizePattern(line);
  if (!allowedNegations.has(normalized)) {
    fail(`.dockerignore must not reopen broad or sensitive paths: ${line}`);
  }
}

for (const dockerfile of dockerfiles) {
  const dockerfilePath = path.join(repoRoot, dockerfile);
  const content = readRequiredFile(dockerfilePath);
  for (const instruction of parseDockerfileInstructions(content)) {
    const match = instruction.match(/^(COPY|ADD)\s+(.+)$/i);
    if (!match) {
      continue;
    }
    const command = match[1].toUpperCase();
    const args = match[2];
    if (command === 'ADD') {
      fail(`${dockerfile} must use COPY instead of ADD for reproducible local build context`);
      continue;
    }
    if (/\s--from=/.test(` ${args}`)) {
      continue;
    }
    const tokens = splitInstructionArgs(args).filter((token) => !token.startsWith('--'));
    if (tokens.length < 2) {
      fail(`${dockerfile} has an unparsable COPY instruction: ${instruction}`);
      continue;
    }
    const sources = tokens.slice(0, -1);
    for (const source of sources) {
      if (source === '.' || source === './' || source === '/') {
        fail(`${dockerfile} must not COPY the entire repository build context`);
        continue;
      }
      if (!isAllowedCopySource(source)) {
        fail(`${dockerfile} COPY source is not in the approved runtime artifact allowlist: ${source}`);
      }
    }
  }
}

if (violations.length > 0) {
  console.error('docker-context-policy-check: failed');
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log('docker-context-policy-check: passed');
