# M35 CI and Conformance Evidence

Date: 2026-05-27
Linear: GRA-114
Branch: gra-114-final-ci-conformance
Current master commit: `39f645167b22adc523b7dbd0c610ecd17e45f83b`

## Required CI Evidence

The latest code-affecting master commit is GRA-111 (`e6d8c7d17ae3912149fc245b28900b4f56311707`). GRA-112 and GRA-113 were docs/spec/report-only closeouts. The required master `Test matrix (raster + GPU)` run for GRA-111 passed:

- Run: https://github.com/ygdrasil-io/kanvas/actions/runs/26539964877
- `Raster tests (ubuntu)`: success, https://github.com/ygdrasil-io/kanvas/actions/runs/26539964877/job/78178701972
- `GPU tests (macos)`: success, https://github.com/ygdrasil-io/kanvas/actions/runs/26539964877/job/78178701990
- `GPU inventory (macos, non-blocking)`: failure, https://github.com/ygdrasil-io/kanvas/actions/runs/26539964877/job/78178880700

The non-blocking inventory failure is not a required CI gate. GRA-115 owns final inventory classification and blocker handling.

## Local Conformance Evidence on Current Master

Commands run on `39f645167b22adc523b7dbd0c610ecd17e45f83b`:

```bash
rtk ./gradlew --no-daemon pipelineConformance
rtk ./gradlew --no-daemon pipelineConformanceReport
```

Result: both passed.

Generated PM report:

```text
build/reports/pipeline-conformance/m24-pipeline-conformance-report.md
```

The conformance run included:

- `:gpu-raster:wgslValidateStrict`
- `:gpu-raster:wgslValidateAll` legacy diagnostic inventory
- `:gpu-raster:pipelineConformanceTest`
- `:cpu-raster:pipelineConformanceTest`
- `:render-pipeline:pipelineConformanceTest`
- `:kanvas-skia:pipelineConformanceTest`

Known legacy WGSL parser diagnostics remained part of the accepted diagnostic inventory; strict generated/registered modules passed with `failed=0`.

## Required GPU Smoke Evidence

Command run on `39f645167b22adc523b7dbd0c610ecd17e45f83b`:

```bash
rtk ./gradlew --no-daemon :gpu-raster:gpuSmokeTest --rerun-tasks
```

Result: passed on local adapter-backed lane.

Adapter evidence from JUnit XML:

```text
[WebGpuContext] adapter=/Apple M2 Max arch= desc=
```

Smoke JUnit summary:

| Metric | Value |
|---|---:|
| Test XML suites | 4 |
| Tests | 34 |
| Failures/errors | 0 |
| Skipped | 0 |

The smoke lane includes the required promoted fixtures and policy suites:

- `org.skia.gpu.webgpu.WebGpuCoveragePlanSelectorTest`
- `org.skia.gpu.webgpu.PipelineKeyTelemetryTest`
- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`
- `org.skia.gpu.webgpu.AnalyticAntialiasConvexWebGpuTest`

`validateGpuSmokePromotionPolicy` ran as part of `gpuSmokeTest` and passed, proving the M32/M33/M34 guard rules did not reject the required smoke set.

## Gate Summary

| Gate | Status | Evidence |
|---|---|---|
| Required raster CI | Pass | master Test matrix job `78178701972` |
| Required GPU CI | Pass | master Test matrix job `78178701990` |
| `pipelineConformance` | Pass | local command on `39f645167b22adc523b7dbd0c610ecd17e45f83b` |
| `pipelineConformanceReport` | Pass | `build/reports/pipeline-conformance/m24-pipeline-conformance-report.md` |
| Required `gpuSmokeTest` | Pass | local adapter-backed run, 34 tests, 0 failures, 0 skipped |
| Full GPU inventory | Pending M35 audit | GRA-115 |

## Follow-Up for GRA-115

The required gates above are green. The remaining release-readiness risk is the full GPU inventory audit: GRA-109 and the non-blocking master inventory both showed non-required inventory failures. GRA-115 must classify the final inventory and resolve or explicitly own every blocker category before the MVP RC can be marked ready.

## Validation

```bash
rtk git diff --check
```
