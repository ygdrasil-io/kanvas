# KAN-026 HairlinesGM Harness Evidence

KAN-026 closes the current HairlinesGM investigation as `visible non supportable`.
The repo already has a row-specific cross-backend harness for `HairlinesGM`, but
today it reaches the stable WebGPU production refusal before it can emit the
reference/CPU/GPU/diff/stat evidence needed for support.

Evidence pack:
`reports/wgsl-pipeline/scenes/artifacts/kan-026-hairlines-harness/kan-026-hairlines-harness.json`.

## Result

| Role | Scene or harness | Status | Evidence |
|---|---|---|---|
| Row-specific harness | `HairlinesCrossBackendTest` | present | Runs `HairlinesGM` through `runCrossBackendTest` with `original-888/hairlines.png`. |
| Local WebGPU execution | `HairlinesGM` | stable refusal | Fails with `coverage.stroke-cap-join-visual-parity-below-threshold` on route `webgpu.coverage.refuse`. |
| Dashboard policy row | `skia-gm-hairlines` | `expected-unsupported` | Keeps `coverage.hairline.row-specific-artifacts-required` and `policyOnlyArtifacts=true`. |
| Linked boundary | `m60-bounded-stroke-cap-join` | `expected-unsupported` | Keeps `coverage.stroke-cap-join-aa-residual` at threshold `99.95`. |
| Closure decision | KAN-026 | `visible non supportable` | Harness and refusal are visible; support proof is still missing. |

## Harness Facts

The selected harness is:

```text
gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/crossbackend/HairlinesCrossBackendTest.kt
```

It renders `HairlinesGM`, using the upstream-style reference:

```text
skia-integration-tests/src/test/resources/original-888/hairlines.png
```

The GM is a 1250 x 1250 stress scene with 14 paths, stroke widths `0`,
`0.5`, and `1.5`, AA on/off, and alpha values `0xFF` and `0x40`.
The harness would normally produce:

```text
gpu-raster/build/debug-images/hairlines-raster.png
gpu-raster/build/debug-images/hairlines-gpu.png
gpu-raster/build/debug-images/hairlines-diff.png
```

For the current production route, those files are not evidence of support,
because the WebGPU draw throws before the debug-image writes.

## Observed Refusal

The local adapter-backed command was:

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest
```

It fails as expected for this ticket with:

```text
route=webgpu.coverage.refuse
strategy=RefuseDiagnostic
diagnostic=backend=GPU,reason=coverage.stroke-cap-join-visual-parity-below-threshold
```

The selected row facts from the refusal are:

- `pathVerbCount=75/96`
- `coverageEdgeCount=60/256`
- `strokeWidth=1.0`
- `strokeCaps=butt`
- `strokeJoins=miter`
- `deviceBounds=260.0,4.947735,340.0,84.61401`

This is under the current numeric path/edge budgets, but still not supportable
because the cap/join AA residual boundary is unresolved.

## Policy Boundary

No renderer or shader change is included.

No threshold is lowered.

No edge budget is increased.

The dashboard row `skia-gm-hairlines` remains `expected-unsupported` with:

```text
coverage.hairline.row-specific-artifacts-required
```

The linked M60 cap/join boundary remains `expected-unsupported` with:

```text
coverage.stroke-cap-join-visual-parity-below-threshold
coverage.stroke-cap-join-aa-residual
```

FOR-267 still refuses the bounded correction because CPU/GPU coverage
equivalence for round cap/join boundary cells is not proven. FOR-318 still
keeps hairline `strokeWidth=0` blocked by the current M60 stroke-width budget
and preserves the 256 edge budget.

## Required Future Proof

A future support promotion must add row-local HairlinesGM evidence:

- Skia/reference artifact
- CPU render artifact
- adapter-backed WebGPU render artifact
- CPU/GPU diff and stats
- route diagnostics with fallback fields
- stable fallback policy outside the selected row
- proof at `99.95` without weakening thresholds or budgets

## Non-Claims

KAN-026 does not claim HairlinesGM WebGPU support.

KAN-026 does not claim broad hairline Path AA support.

KAN-026 does not claim subpixel hairline coverage support.

KAN-026 does not change shared renderer or shader behavior.

KAN-026 does not expand broad Path AA, hairline, cap/join, dash, or edge-budget
support.

KAN-026 does not port Ganesh, Graphite, a SkSL compiler, SkSL IR, or a SkSL VM.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.crossbackend.HairlinesCrossBackendTest
rtk ./gradlew --no-daemon pipelineDashHairlineStrokeDashboardVisibilityPack
rtk python3 scripts/validate_dash_hairline_stroke_dashboard_visibility.py
rtk python3 scripts/validate_for267_round_cap_join_coverage_equivalence.py
rtk python3 scripts/validate_kan026_hairlines_harness.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan026HairlinesHarness
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```

The `HairlinesCrossBackendTest` command is expected to fail for this closure
decision until a future renderer proof replaces the stable production refusal.
