# M41 Generated Scene Result Schema

Date: 2026-05-28
Linear: GRA-196
Milestone: M41 Generated Conformance Dashboard

## Scope

GRA-196 defines the generated scene-result schema that feeds the existing
`reports/wgsl-pipeline/scenes/data/scenes.json` contract without breaking
static rows.

Existing rows remain valid because `generation` is optional. Generated rows add
traceability fields that identify the producer, source task/test, commit,
artifact root, and schema version.

## Generation Block

```json
"generation": {
  "mode": "generated",
  "producer": "pipelineSceneExport",
  "sourceTask": ":gpu-raster:gpuSmokeTest",
  "sourceTest": "org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest",
  "commit": "<git-sha>",
  "artifactRoot": "artifacts/bitmap-rect-nearest",
  "schema": "generated-scene-result.v1"
}
```

Allowed modes:

- `static`: checked-in static registry row.
- `generated`: row materialized from test/report output.
- `mixed`: generated artifacts with retained static notes or legacy evidence.

## Status Behavior

- `pass`: requires reference, CPU render/diff/route/stats, GPU render/diff/route/stats when GPU eligible, and `fallbackReason=none`.
- `tracked-gap`: must name the missing artifact or environment reason; it is not a placeholder.
- `expected-unsupported`: must keep GPU route diagnostics with stable non-`none` fallback reason; GPU image/diff may be absent.
- `fail`: must preserve produced artifacts/stats so the failure is reviewable.

## Mapping

The generated fields map directly onto the current dashboard row: metadata to
`id/title/priority/status`, reference to `referenceKind/reference`, CPU/GPU lanes
to `cpu` and `gpu`, raw route files to `routeDiagnostics`, image differences to
`diffs`, aggregate stats to `stats`, and traceability to `evidence`.

The detailed mapping and sample row are documented in
`.upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md`.

## Follow-Up Tickets

- GRA-197 implements the exporter/helper that materializes generated artifacts.
- GRA-198, GRA-199, and GRA-200 convert selected rows to generated or mixed evidence.
- GRA-201 closes M41 with generated/static row counts and validation evidence.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
