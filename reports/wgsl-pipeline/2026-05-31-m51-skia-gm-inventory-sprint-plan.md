# M51 Sprint Plan: Skia GM Inventory Coverage

Date: 2026-05-31
Status: Proposed
Target: `archives/target-closeout-2026-05-31/rendering-conformance-performance-target.md`
Backlog: `archives/target-closeout-2026-05-31/post-mvp-conformance-backlog.md`

## Goal

M51 should make the full Skia GM/sample surface visible before Kanvas tries to
promote many more scenes into the support dashboard.

The objective is inventory coverage, not broad support. Every upstream Skia GM
should become findable, classifiable, and linkable from PM/release evidence with
a clear status such as promoted, candidate, expected unsupported,
dependency-gated, duplicate/variant, non-rendering, or not triaged.

## Starting Point

M50 closed with:

| Signal | Count |
|---|---:|
| Dashboard scene rows | 28 |
| `pass` | 21 |
| `expected-unsupported` | 7 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 26 |
| Static policy rows | 2 |
| Adapter-backed rows | 17 |

Local source inventory at sprint planning time:

| Source | Count | Meaning |
|---|---:|---|
| `/Users/chaos/workspace/kanvas-forge/skia-main/gm/*.cpp` | 437 | Upstream GM C++ files, excluding `BUILD.bazel`. |
| `skia-integration-tests/src/main/kotlin/org/skia/tests/*GM.kt` | 751 | Kotlin GM source files currently present in the repo. |

The counts are not expected to match one-to-one: one upstream C++ file may
define multiple GM variants, and Kotlin may contain split or generated wrappers.
M51 must make those mismatches explicit instead of hiding them.

## Readiness Target

M51 may move PM readiness from 80% to about 82% only if the inventory becomes
release-visible evidence. It should not claim broad Skia parity or broad new
rendering support.

| PM area | Weight | M50 | M51 target | Why |
|---|---:|---:|---:|---|
| Evidence foundation | 25% | 100% | 100% | Preserve dashboard gate, deterministic exports, 0 `tracked-gap`, and 0 `fail`. |
| Skia integration coverage | 25% | 65% | 70% | Full GM inventory visibility improves coverage planning, but support does not move without generated artifacts. |
| CI and release gates | 20% | 85% | 85% | Existing release gate remains required; M51 inventory validation should be additive. |
| Performance readiness | 15% | 60% | 60% | No performance threshold movement in this sprint. |
| PM demo and reporting workflow | 15% | 85% | 88% | PM bundle gains a complete inventory view and next-promotion backlog. |

Weighted target: about 82%.

## Work Lanes

### M51-A Inventory Schema And Scanner

Create a deterministic inventory source for upstream GM files and Kotlin GM
sources.

Definition of Done:

- Inventory records every upstream `gm/*.cpp` file under the configured Skia
  checkout.
- Inventory records every Kotlin `*GM.kt` source under
  `skia-integration-tests/src/main/kotlin/org/skia/tests/`.
- Each record has stable id, source kind, upstream path, Kotlin path if known,
  source file name, normalized display name, family tags, and initial status.
- Mismatches between upstream and Kotlin sources are reported explicitly.
- Output is generated as machine-readable JSON and PM-readable Markdown.

Suggested artifacts:

- `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json`;
- `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.md`;
- committed closeout report under `reports/wgsl-pipeline/`.

### M51-B Classification Taxonomy

Define how inventory rows map to rendering planning status without changing
support claims.

Allowed status values:

- `dashboard-promoted`: already represented by a generated or policy dashboard
  scene;
- `promotion-candidate`: good candidate for M52+ generated scene work;
- `expected-unsupported`: intentionally refused with stable fallback reason;
- `dependency-gated`: blocked by font, codec, platform, or missing upstream
  dependency delivery;
- `not-triaged`: visible but not yet classified;
- `non-rendering-or-utility`: source exists but is not a rendered scene target;
- `duplicate-or-variant`: useful as evidence but should roll up under another
  canonical target first.

Definition of Done:

- Every inventory row has exactly one status.
- Every non-`not-triaged` status has a short reason.
- `expected-unsupported` and `dependency-gated` rows preserve stable fallback or
  dependency reasons.
- Dashboard support rows are not broadened by taxonomy alone.

### M51-C PM Inventory View

Expose inventory evidence to PM/release review without diluting the scene
dashboard support counters.

Definition of Done:

- PM bundle links the inventory Markdown and JSON.
- Dashboard or bundle has a clear notice that inventory rows are not support
  claims.
- Inventory can be filtered or grouped by status, family, upstream source, and
  Kotlin source presence.
- Current promoted dashboard scene ids link back to inventory rows where
  possible.
- Existing scene dashboard counters remain about rendered/refused scene
  evidence only.

### M51-D M52 Promotion Candidate Backlog

Produce the next implementation backlog from the inventory.

Definition of Done:

- Select 25-40 high-value promotion candidates for M52+.
- Candidates cover multiple families: paint/blend, bitmap/image, gradients,
  clip/transform, Path AA, image filters, runtime effects, text/font, and codec
  or image decode boundaries where applicable.
- Each candidate includes upstream C++ source, Kotlin source if present,
  reference availability, expected CPU route, expected GPU route or refusal,
  dependency risk, and suggested validation command.
- No candidate is marked as supported without generated evidence.

### M51-E Inventory Gate

Add validation that prevents the inventory from silently drifting.

Definition of Done:

- Validation fails on duplicate inventory ids.
- Validation fails when a row lacks source path, status, or family.
- Validation reports but does not necessarily fail on upstream/Kotlin mismatch
  count changes until the owner decides the policy.
- Validation output is archived or included in the PM bundle.

### M51-F Sprint Review And Score Sync

Close M51 with evidence and update readiness only to the justified score.

Definition of Done:

- Sprint review reports upstream GM count, Kotlin GM count, classified count,
  not-triaged count, promoted count, candidate count, dependency-gated count,
  and expected-unsupported count.
- README, target, and backlog agree on final M51 score.
- Linear closeout links tickets, PRs, validation commands, inventory artifacts,
  PM bundle, and known limitations.

## Validation

Baseline validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```

M51 should add and run an inventory-specific task, for example:

```bash
rtk ./gradlew --no-daemon pipelineSkiaGmInventory
rtk ./gradlew --no-daemon pipelineSkiaGmInventoryGate
```

The exact task names can change during implementation, but the closeout must
record the final commands.

## Non-Goals

- Do not claim every Skia GM is integrated as rendered support.
- Do not add hundreds of scene dashboard rows without generated artifacts.
- Do not mark `not-triaged` rows as supported or unsupported.
- Do not hide dependency-gated font, codec, emoji, shaping, SDF, LCD, or
  glyph-mask gaps.
- Do not weaken the M50 dashboard gate to accommodate inventory rows.
