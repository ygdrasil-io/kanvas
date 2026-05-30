# M46 SrcOver Stack Measured Generated Evidence

Date: 2026-05-30
Issue: GRA-229

## Outcome

`src-over-stack` was selected as the M46 measured-performance conversion row
because it was explicitly preferred by the inventory and was not blocked. The
row was converted from static dashboard evidence to generated evidence through
`pipelineGeneratedSceneExport` without dropping the M43 measured CPU/GPU
`performanceTrend` payloads.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `src-over-stack`, so the merged dashboard keeps the same
public row identity without duplicate scene ids.

`bitmap-shader-local-matrix` remains static because `src-over-stack` was not
blocked and satisfied the preferred measured-performance row selection.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `test-oracle` |
| CPU route | `cpu.blend.src-over-stack` |
| GPU route | `webgpu.blend.src-over.fixed-function` |
| GPU coverage strategy | `webgpu.coverage.analytic-rect-stack` |
| GPU pipeline key | `state=[blendMode=kSrc] + state=[blendMode=kSrcOver] fixedFunctionBlend` |
| Fallback reason | `none` |
| Threshold | `99.95` |
| CPU/GPU similarity | `100.0%` |
| Matching pixels | `4096 / 4096` |
| Max channel delta | `0` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. Existing route, feature,
and risk tags were preserved.

## Preserved Measured Performance Payloads

| Lane | Status | Command | Baseline | Gate | Raw metrics |
|---|---|---|---|---|---|
| CPU | `measured` | `rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance` | `m43-cpu-measured-local` / `698a8c8e7554fad0d33271765f0442f038e2cbae` | `reporting-only` | `artifacts/src-over-stack/cpu-performance.json` |
| GPU | `measured` | `rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance` | `m43-gpu-cache-measured-local` / `698a8c8e7554fad0d33271765f0442f038e2cbae` | `reporting-only` | `artifacts/src-over-stack/gpu-performance.json` |

The measured status remains reporting-only. This conversion does not introduce a
required CI performance gate, budget gate, or rollback threshold.

Host/JDK/backend metadata, adapter metadata, counters, raw sample arrays,
checksum fields, baseline labels, and regression labels remain in the generated
row's CPU/GPU `performanceTrend` payloads.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/src-over-stack/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/stats.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json`
- `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json`

## Generation Commands

```bash
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
```

## Validation

```bash
rtk ./gradlew --no-daemon pipelineMeasuredCpuPerformance
rtk ./gradlew --no-daemon -Pkanvas.gpu.performance.adapter="Apple M2 Max" pipelineMeasuredGpuPerformance
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
