# M46 Solid Rect Generated Evidence

Date: 2026-05-30
Issue: GRA-225

## Outcome

`solid-rect` was converted from static dashboard evidence to generated evidence
through `pipelineGeneratedSceneExport` while preserving the M42 adapter-backed
P0 support claim.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `solid-rect`, so the merged dashboard keeps the same public
row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P0` |
| Reference kind | `cpu-oracle` |
| CPU route | `cpu.descriptor.coverage-plan.solid-rect` |
| GPU route | `webgpu.coverage.analytic-rect` |
| GPU fallback reason | `none` |
| Threshold | `99.95` |
| GPU similarity | `100.0%` |
| Matching pixels | `64 / 64` |
| Max channel delta | `0` |
| Adapter | `Apple M2 Max` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. `maturity.adapter-backed`
was retained because `gpu.stats.adapter` is concrete.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/solid-rect/
```

Generated export source:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/solid-rect/stats.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
```

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.SolidRectSceneCaptureTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
