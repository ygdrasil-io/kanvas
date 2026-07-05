# Conformance Dashboard Generation

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`
Historical milestone: M41 -- Generated Conformance Dashboard
Current role: delivered evidence infrastructure retained by the active target.

## Purpose

Move the scene dashboard from static registry evidence toward generated
conformance evidence produced by tests and report tasks.

The dashboard remains PM-readable, but the source of truth should become
machine-generated scene results where possible. Static rows may remain during
migration, but they must not be confused with generated conformance.

## Inputs

Each generated scene result must provide:

- scene id and title;
- source test or report task;
- reference artifact and `referenceKind`;
- CPU render, diff, route JSON, and stats;
- GPU render, diff, route JSON, and stats when GPU renders;
- stable fallback reason when GPU refuses;
- optional `performanceTrend`;
- raw evidence links.
- stable taxonomy tags as defined in `13-scene-tag-taxonomy.md`.

## Scene Status Rules

`pass` requires:

- reference artifact;
- CPU render and route diagnostics;
- GPU render and route diagnostics for GPU-eligible scenes;
- diff and stats artifacts;
- `fallbackReason=none` for supported GPU rows.

`tracked-gap` requires:

- at least one meaningful lane of evidence;
- an explicit missing artifact or environment reason;
- a closeout note or follow-up ticket.

`expected-unsupported` requires:

- a GPU section;
- stable fallback reason;
- CPU/reference evidence when possible;
- inventory or spec reference explaining why the refusal is intentional.

`fail` is reserved for scenes that are claimed supported but miss thresholds,
artifacts, or route invariants.


## Generated Scene Result Schema

Generated scene results are normalized into the existing `scenes.json` row
shape. Static rows remain valid, but generated rows must identify their origin
so support claims can distinguish hand-authored registry evidence from test or
report output.

A generated row adds this optional top-level block:

```json
"generation": {
  "mode": "generated",
  "producer": "pipelineSceneExport",
  "sourceTask": ":gpu-renderer:test",
  "sourceTest": "org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest",
  "generatedAt": "2026-05-28T10:00:00Z",
  "commit": "<git-sha>",
  "artifactRoot": "artifacts/bitmap-rect-nearest",
  "schema": "generated-scene-result.v1"
}
```

Allowed `generation.mode` values:

- `static`: hand-authored registry row or historical checked-in artifact row.
- `generated`: row materialized from a test or report task output.
- `mixed`: row with generated artifacts plus retained static notes or legacy
  evidence links.

Generated result fields map to the dashboard row as follows:

| Generated field | Dashboard field | Required for generated `pass` |
|---|---|---|
| `scene.id` | `id` | yes |
| `scene.title` | `title` | yes |
| `scene.priority` | `priority` | yes |
| `scene.status` | `status` | yes |
| `reference.kind` | `referenceKind` | yes |
| `reference.path` | `reference` | yes |
| `cpu.image` | `cpu.image` | yes |
| `cpu.diff` | `cpu.diff` | yes |
| `cpu.route` | `cpu.route` and `routeDiagnostics.cpu` | yes |
| `cpu.stats` | `cpu.stats` and scene `stats` | yes |
| `gpu.image` | `gpu.image` | yes when GPU status is not `expected-unsupported` |
| `gpu.diff` | `gpu.diff` | yes when GPU status is not `expected-unsupported` |
| `gpu.route` | `gpu.route` and `routeDiagnostics.gpu` | yes |
| `gpu.stats` | `gpu.stats` | yes when GPU status is not `expected-unsupported` |
| `performanceTrend` | `cpu.performanceTrend` / `gpu.performanceTrend` | optional passthrough |
| `evidence[]` | `evidence[]` | yes for source task/report traceability |

For `expected-unsupported`, generated rows must keep a GPU lane with route
identity and a stable non-`none` `fallbackReason`. GPU image and GPU diff may be
absent only in that status.

For `tracked-gap`, generated rows must name the missing artifact or environment
reason in `generation.missing[]` or in the GPU/CPU fallback diagnostics. A row
cannot use `tracked-gap` as a generic placeholder.

For `fail`, generated rows must still preserve the produced artifacts and stats
so the failure is reviewable.

### Sample Generated Row

```json
{
  "id": "bitmap-rect-nearest",
  "title": "Bitmap rect nearest sampling",
  "priority": "P0",
  "status": "pass",
  "generation": {
    "mode": "generated",
    "producer": "pipelineSceneExport",
    "sourceTask": ":gpu-renderer:gpuSmokeTest",
    "sourceTest": "org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest",
    "commit": "<git-sha>",
    "artifactRoot": "artifacts/bitmap-rect-nearest",
    "schema": "generated-scene-result.v1"
  },
  "referenceKind": "skia-upstream",
  "reference": "artifacts/bitmap-rect-nearest/skia.png",
  "tags": [
    "source.generated",
    "feature.image.bitmap",
    "route.gpu.webgpu",
    "reference.skia-upstream",
    "maturity.generated-evidence",
    "risk.none"
  ],
  "cpu": {
    "status": "pass",
    "image": "artifacts/bitmap-rect-nearest/cpu.png",
    "diff": "artifacts/bitmap-rect-nearest/cpu-diff.png",
    "route": { "selectedRoute": "cpu.image-rect.strict-nearest", "fallbackReason": "none" },
    "stats": { "pixels": 4096, "matchingPixels": 4096, "maxChannelDelta": 0, "threshold": 99.95, "backend": "CPU", "command": ":gpu-renderer:gpuSmokeTest" }
  },
  "gpu": {
    "status": "pass",
    "image": "artifacts/bitmap-rect-nearest/gpu.png",
    "diff": "artifacts/bitmap-rect-nearest/gpu-diff.png",
    "route": { "selectedRoute": "webgpu.image-rect.strict-nearest", "pipelineKey": "imageRect.strictNearest.promotedSmoke", "fallbackReason": "none" },
    "stats": { "pixels": 4096, "matchingPixels": 4096, "maxChannelDelta": 0, "threshold": 99.95, "backend": "WebGPU", "command": ":gpu-renderer:gpuSmokeTest" }
  },
  "diffs": { "cpu": "artifacts/bitmap-rect-nearest/cpu-diff.png", "gpu": "artifacts/bitmap-rect-nearest/gpu-diff.png" },
  "routeDiagnostics": { "cpu": "artifacts/bitmap-rect-nearest/route-cpu.json", "gpu": "artifacts/bitmap-rect-nearest/route-gpu.json" },
  "stats": { "pixels": 4096, "matchingPixels": 4096, "maxChannelDelta": 0, "threshold": 99.95 }
}
```

## Generated Artifact Layout

Generated outputs should keep the existing static shape:

```text
build/reports/wgsl-pipeline-scenes/
  index.html
  data/scenes.json
  artifacts/
    <scene-id>/
      skia.png
      cpu.png
      gpu.png
      cpu-diff.png
      gpu-diff.png
      route-cpu.json
      route-gpu.json
      stats.json
```

Additional raw files may be linked from `evidence`, but the dashboard must keep
the canonical artifact names stable when a file exists.

## Generated Exporter Contract

`pipelineGeneratedSceneExport` materializes generated scene result rows before
the dashboard is rendered. The task reads:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

and writes:

```text
build/reports/wgsl-pipeline-generated-scenes/
  data/generated-scenes.json
  artifacts/<scene-id>/...
```

The manifest uses this shape:

```json
{
  "schemaVersion": 1,
  "generatedBy": "pipelineGeneratedSceneExport",
  "scenes": [
    { "...": "normalized dashboard scene row" }
  ]
}
```

Each generated scene row must:

- use `generation.mode=generated` or `generation.mode=mixed`;
- set `generation.artifactRoot` to `artifacts/<scene-id>`;
- include producer, commit, schema, and source task/test/report traceability;
- include at least one `source.*`, `feature.*`, `route.*`, `reference.*`, and
  `maturity.*` tag;
- expose non-empty raw `evidence` links;
- reference canonical artifact paths under `artifacts/<scene-id>/`.

The exporter fails before dashboard rendering when a generated row references a
missing artifact, report, or data file. The error must include both the scene id
and the JSON field path, for example:

```text
bitmap-rect-nearest: missing generated artifact for `cpu.image`
```

`pipelineSceneDashboard` depends on the exporter, merges generated rows with the
static `data/scenes.json` rows, validates the combined set, copies generated
artifacts into `build/reports/wgsl-pipeline-scenes/`, and writes the final merged
`data/scenes.json`. Duplicate scene ids are rejected, so converting a static row
to generated evidence must remove or replace the static row in the source
registry.

## Static-To-Generated Promotion Policy

Converting a static row to generated evidence is a support-evidence migration,
not a feature promotion by itself. The generated row must preserve the selected
scene contract before the static row is removed:

- same scene id unless a deliberate rename is documented;
- same or stricter `status`;
- same `priority` and `referenceKind` unless the closeout explains why the
  oracle changed;
- same CPU and GPU selected route families, or a documented route improvement
  with equivalent rendered evidence;
- same threshold or stricter threshold;
- same fallback reason semantics for unsupported rows;
- all existing raw evidence that remains relevant linked from `evidence[]`;
- tags updated from `source.static` / `maturity.static-evidence` to
  `source.generated` / `maturity.generated-evidence`.

Rows with measured performance payloads must keep links to the measured raw
metrics when converted. Rows with adapter-backed GPU evidence must keep
`maturity.adapter-backed` only when the generated GPU stats include a concrete
adapter name.

Every conversion closeout must state which static row was removed, which task
now produces the row, and what command regenerates the artifacts.

## Reference Policy

References must be explicit:

- `skia-upstream`: preferred when upstream or imported Skia evidence exists;
- `cpu-oracle`: valid when no upstream image exists and the CPU route is the
  comparison oracle;
- `test-oracle`: valid when a test fixture provides a deterministic reference.

The dashboard must explain the reference type inline and must not imply full
Skia parity from one scene row.

## Route Diagnostics

Every generated row must expose:

- CPU selected route;
- GPU selected route or coverage strategy;
- compatibility fallback route if used;
- `CoveragePlan` or `PipelineIR` participation when applicable;
- `PipelineKey` or shader/module identity when GPU WGSL participates;
- stable fallback reason or `none`;
- source test name.

Route diagnostics are not enough for support claims without rendered evidence.

## Filters And PM Readability

The dashboard should provide filters for:

- status;
- priority;
- reference type;
- GPU fallback reason;
- performance status;
- text search across scene id, title, route, and fallback.

Artifact lists should be collapsed by default. The primary visual evidence
should show reference, CPU, GPU, CPU diff, and GPU diff panels directly.

## Validation

Required validation:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Generated scene tasks must also validate that every referenced artifact exists
and that unsupported GPU rows include stable fallback reasons. The dashboard
export also rejects duplicate, empty, uppercase, whitespace, or slash-containing
tags; generated rows missing required tag namespaces; `maturity.adapter-backed`
without `gpu.stats.adapter`; and `route.gpu.expected-unsupported` without a
stable GPU fallback reason.

## Non-Goals

- Do not build the native live editor in M41.
- Do not require all rows to be generated before allowing mixed static/generated
  evidence.
- Do not make adapter-missing rows pass.
- Do not lower image similarity floors to hide regressions.
