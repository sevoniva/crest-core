# AGENTS.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 0. Crest Branch and Release Rules

Before making code changes, classify the request and choose the branch path from [docs/release-process.md](./docs/release-process.md).

- Released-version bug, production bug, or "repackage after bug fix": use `hotfix/<next-version>-<short-name>` from the matching `release/<major.minor>` branch; bump to the next patch version and add release notes. Do not move or overwrite an existing released tag.
- New feature or planned product change: use `feat/<short-name>` into `dev`.
- Unreleased ordinary bug: use `fix/<short-name>` into `dev`.
- CI, release package, or attachment-only issue with unchanged code and images: use `ci/<short-name>` or `chore/<short-name>` and rerun the relevant workflow; do not bump the product version unless the delivered artifact content changes.
- Database, system-setting, initialization-data, or schema changes for a released line require an incremental SQL file under `installer/upgrade-sql/`.
- Published release notes and upgrade SQL for existing tags are immutable. Create a new patch version for behavioral fixes.

When in doubt, stop and state the branch/release classification before editing. Never suggest force-pushing, deleting, or moving `v*` tags unless the user explicitly asks for an incident rollback and accepts the release-audit risk.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
