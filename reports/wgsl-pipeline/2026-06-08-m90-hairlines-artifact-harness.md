# M90 Hairlines Artifact Harness

Date: 2026-06-08

## Scope

`M90-PAA-3A-REF` now has a row-specific harness for `HairlinesGM` / `skia-gm-hairlines`.
The harness compares the upstream Skia reference against the CPU GM render and the WebGPU render path, and it can write route, PNG, diff, and stats artifacts when explicitly enabled.

## Support Status

This item does not promote `skia-gm-hairlines`.
The row remains `expected-unsupported` with `fallbackReason=coverage.hairline.row-specific-artifacts-required`.

No checked-in dashboard row, registry row, similarity threshold, edge budget, or fallback policy was changed by this item.
The harness is structural evidence only until an explicit artifact run produces row-specific CPU/WebGPU route, render, diff/stat, and performance evidence with `fallbackReason=none`.

## Artifact Contract

Default validation runs the test without writing rendered artifacts.
Artifact capture is opt-in:

```text
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.strokeCapJoin.experimentalRender=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest
```

When enabled, the harness writes under:

```text
reports/wgsl-pipeline/scenes/artifacts/skia-gm-hairlines/
```

Expected files are:

- `skia.png`
- `cpu.png`
- `cpu-diff.png`
- `route-cpu.json`
- `route-gpu.json`
- `stats.json`
- `cpu-performance.json`
- `gpu-performance.json`
- `gpu.png`
- `gpu-diff.png`

The M90 Hairlines intake classifies these checked-in files as non-promotional evidence.
The opt-in WebGPU visual capture now produces `gpu.png` and `gpu-diff.png`, but
the row remains non-promotional: WebGPU similarity is `98.9581`, below the
`99.95` promotion threshold, and the route keeps
`fallbackReason=coverage.hairline.row-specific-artifacts-required`.
The remaining support gate is tracked explicitly in
`reports/wgsl-pipeline/scenes/generated/m90-hairlines-adapter-backed-gate.json`.

## Non-Claims

- No broad Path AA support claim.
- No broad hairline/stroke/dash support claim.
- No Ganesh or Graphite port.
- No dynamic SkSL compiler, IR, or VM.
- No global threshold reduction.
- No support promotion from a below-threshold/tolerance-only case.

## Validation

```text
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m90-hairlines-harness-pycache python3 -m py_compile scripts/validate_m90_hairlines_artifact_harness.py
rtk python3 scripts/validate_m90_hairlines_artifact_harness.py
rtk ./gradlew --no-daemon pipelineM90PathAaHairlinesArtifactHarness
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.strokeCapJoin.experimentalRender=true :gpu-raster:test --tests org.skia.gpu.webgpu.HairlinesSceneCaptureTest
rtk git diff --check
```
