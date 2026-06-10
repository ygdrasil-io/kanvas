# KAN-035 HairlinesGM Root Cause

KAN-035 classifies the current `HairlinesGM` residual as a stable refusal, not
a support promotion. The bounded fix is diagnostic-only: GPU inventory now
classifies `coverage.stroke-cap-join-visual-parity-below-threshold` as `expected-unsupported-diagnostic` while
future unknown `coverage.*` codes still fail closed.

## Decision

| Field | Value |
|---|---|
| Closure | `stable-refusal-diagnostic-fix` |
| Primary bucket | `cap-join-parity` |
| Support claim | `False` |
| Renderer changed | `False` |
| Threshold changed | `False` |
| Edge budget changed | `False` |
| Readiness delta | `0` |

## Root Cause Priority

| Rank | Bucket | Decision | Evidence |
|---:|---|---|---|
| 1 | `cap-join-parity` | primary root cause for current WebGPU abort | Production refusal reports cap=butt, join=miter, route=webgpu.coverage.refuse, fallback=coverage.stroke-cap-join-visual-parity-below-threshold. |
| 2 | `coverage-stroke-aa-residual` | shared boundary blocks support promotion | M60 remains expected-unsupported at 99.95 with coverage.stroke-cap-join-aa-residual. |
| 3 | `hairline-row-specific-artifacts` | dashboard row remains policy-only expected unsupported | HairlinesGM lacks row-local WebGPU image and diff because the draw refuses before debug images. |

## HairlinesGM Replay Facts

| Fact | Value |
|---|---|
| Harness | `org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest` |
| Command | `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest` |
| Route | `webgpu.coverage.refuse` |
| Fallback | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| Path verbs | `75/96` |
| Coverage edges | `60/256` |
| Stroke facts | width `1.0`, caps `butt`, joins `miter` |
| Failure timing | before debug images: `True` |

## Artifact Availability

| Artifact | Available | Evidence |
|---|---:|---|
| Reference | `True` | `skia-integration-tests/src/test/resources/original-888/hairlines.png` |
| CPU stats | `True` | `skia-integration-tests/test-similarity-report.md`, similarity `97.678016` |
| WebGPU refusal | `True` | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| WebGPU image | `False` | `stable-refusal-before-debug-images` |
| Row-local diff image | `False` | `stable-refusal-before-debug-images` |

## Policy Rows

| Row | Status | Fallback |
|---|---|---|
| `skia-gm-hairlines` | `expected-unsupported` | `coverage.hairline.row-specific-artifacts-required` |
| `m60-bounded-stroke-cap-join` | `expected-unsupported` | `coverage.stroke-cap-join-visual-parity-below-threshold` |

`skia-gm-hairlines` stays `expected-unsupported` until row-local reference,
CPU, adapter-backed WebGPU, diff/stat, route diagnostics, and `99.95` evidence
exist without threshold or budget weakening.

## GPU Inventory Snapshot

| Category | Count |
|---|---:|
| `expected-unsupported-diagnostic` | 1 |
| `similarity-regression` | 0 |
| `unsupported-image-filter` | 0 |
| `adapter-skip` | 0 |
| `adapter-missing` | 0 |
| `unexpected-exception` | 0 |

## Validation

| Check | Status | Evidence |
|---|---|---|
| `hairlines-replay-stable-refusal` | `pass` | HairlinesCrossBackendTest refuses via coverage.stroke-cap-join-visual-parity-below-threshold under the current budgets. |
| `gpu-inventory-classifier-stable` | `pass` | GpuInventoryFailureReport classifies the Hairlines refusal as expected-unsupported-diagnostic and keeps future coverage codes fail-closed. |
| `root-cause-unique-primary` | `pass` | Primary bucket is cap-join-parity, with coverage stroke residual and row-specific artifact gaps as ordered follow-ups. |
| `policy-row-preserved` | `pass` | skia-gm-hairlines remains expected-unsupported with coverage.hairline.row-specific-artifacts-required. |
| `budgets-preserved` | `pass` | Path verb, edge, support threshold, renderer, and shader policies are unchanged. |

## Non-Claims

- KAN-035 does not claim HairlinesGM WebGPU support.
- KAN-035 does not claim broad hairline Path AA support.
- KAN-035 does not lower the 99.95 support threshold.
- KAN-035 does not increase the 256 WebGPU AA edge budget.
- KAN-035 does not change renderer, shader, selector, or PipelineKey behavior.
- KAN-035 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
