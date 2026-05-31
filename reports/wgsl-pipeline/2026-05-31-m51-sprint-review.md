# M51 Sprint Review: Skia GM Inventory Coverage

Date: 2026-05-31
Status: Closed
Target: `.upstream/target/rendering-conformance-performance-target.md`
Plan: `reports/wgsl-pipeline/2026-05-31-m51-skia-gm-inventory-sprint-plan.md`

## Outcome

M51 made the Skia GM/sample surface release-visible without changing dashboard
support claims. The new inventory scans upstream Skia GM C++ files and Kotlin
GM sources, classifies each merged inventory row with one planning status, emits
JSON and Markdown artifacts, validates required fields, exposes the inventory in
the PM bundle, and selects an M52+ candidate backlog.

Inventory rows are not scene support claims. Dashboard counters remain scoped to
rows with generated/static evidence under `pipelineSceneDashboard`.

## Inventory Counts

| Signal | Count |
|---|---:|
| Upstream GM files | 437 |
| Kotlin GM files | 751 |
| Inventory rows | 802 |
| Classified rows | 183 |
| Not-triaged rows | 619 |
| Dashboard-promoted rows | 17 |
| Promotion-candidate rows | 35 |
| M52+ backlog candidates | 34 |
| Dependency-gated rows | 38 |
| Expected-unsupported rows | 29 |
| Duplicate/variant rows | 60 |
| Non-rendering/utility rows | 4 |
| Matched upstream/Kotlin rows | 386 |
| Upstream-only rows | 51 |
| Kotlin-only rows | 365 |

Family coverage is visible across paint/blend, bitmap/image, gradients,
clip/transform, Path AA, image filters, runtime effects, text/font, codec/image
decode, and remaining misc rendering rows.

## Artifacts

Generated artifacts:

- `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json`
- `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.md`
- `build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.json`
- `build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.md`
- `build/reports/wgsl-pipeline-pm-bundle/inventory/`
- `build/reports/wgsl-pipeline-pm-bundle/inventory-gate/`

Committed implementation/report artifacts:

- `scripts/skia_gm_inventory.py`
- `build.gradle.kts` tasks `pipelineSkiaGmInventory` and
  `pipelineSkiaGmInventoryGate`
- this sprint review

## PM Bundle Exposure

`pipelinePmBundle` now depends on the inventory gate and includes:

- inventory JSON and Markdown;
- inventory gate JSON and Markdown;
- inventory counters in `manifest.json`;
- dashboard-to-inventory links for currently promoted scenes where a stable
  inventory match is known;
- a manifest notice that inventory rows are planning/classification evidence,
  not support claims.

The bundle keeps existing dashboard counters unchanged: 28 scene rows, 21 pass,
7 expected-unsupported, 0 tracked-gap, 0 fail, 26 generated rows, and 17
adapter-backed rows.

## M52+ Promotion Backlog

The generated inventory selects 34 M52+ candidates. Each candidate records:

- upstream C++ source when present;
- Kotlin source when present;
- reference availability as unknown until candidate-specific capture/rebaseline;
- expected CPU route;
- expected GPU route or stable refusal path;
- risk or dependency;
- suggested validation command;
- explicit non-claim note.

The candidate set covers paint/blend, bitmap/image, gradients, clip/transform,
Path AA, image filters, runtime effects, text/font, and codec/image decode
boundaries. No candidate is marked supported without generated evidence.

## Validation

Commands run by the M51 implementation:

```bash
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate
```

Result: pass. The gate fails duplicate ids, rows missing both source paths, rows
without status, rows without family tags, and non-`not-triaged` rows without a
reason. It reports the mismatch snapshot: 51 upstream-only rows, 365 Kotlin-only
rows, and 0 duplicate normalized keys.

Reviewer closeout validation reran the full required set:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass. The rerun confirmed the inventory gate, scene dashboard gate, and
PM bundle all stay coherent after M51.

## Limits And Non-Claims

- M51 does not claim broad Skia GM support.
- M51 does not add hundreds of dashboard rows.
- M51 does not modify dashboard support counters.
- M51 does not clear font, codec, emoji, shaping, SDF, LCD, glyph-mask, broad
  image-filter DAG, arbitrary SkSL, or broad Path AA gaps.
- M51 does not port Ganesh, Graphite, SkSL compiler, Skia IR, or Skia VM.

## Score Sync

Post-MVP readiness moves from 80% to 82%.

Justification: Skia integration coverage moves from 65% to 70% because the full
GM surface is now visible, classified, mismatch-reported, and connected to an
M52+ promotion backlog. PM demo/reporting moves from 85% to 88% because the PM
bundle now contains inventory artifacts and gate output. Evidence foundation,
CI/release gates, and performance readiness stay unchanged. The movement is
small because inventory is not rendered support evidence.
