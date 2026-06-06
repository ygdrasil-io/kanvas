# FOR-431 M60 F16 WebGPU width-quantized render fix

Date: 2026-06-06

Classification: `opt-in-render-fix-regresses-scene`

FOR-431 implements an experimental WebGPU render variant behind
`kanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled`. The flag is
disabled by default. This ticket records evidence only; no promotion, no
activation par defaut, no threshold/scoring change, no fallback-policy change,
and no `PipelineKey` change are claimed.

## Result

The opt-in path changes exactly the six FOR-430 partial pixels and no pixels
outside the targeted set. It does move the coverage model for those pixels from
`36/96` to `60/96`, matching the FOR-430 CPU width-quantized total. However,
the full-scene image gets worse:

| Metric | Current WebGPU | FOR-431 opt-in |
|---|---:|---:|
| Similarity | 95.914714 | 95.914714 |
| Matching pixels | 23572 | 23572 |
| Mismatch pixels | 1004 | 1004 |
| Max channel delta | 48 | 85 |
| Total residual | 2014 | 2044 |
| Residual delta vs current | 0 | +30 |
| Changed pixels | 0 | 6 targeted / 0 outside target |
| Improved pixels | 0 | 0 |
| Regressed pixels | 0 | 6 |

Each targeted pixel changed from current `[181, 191, 230, 255]` to opt-in
`[111, 147, 129, 255]` while the CPU reference is `[133, 150, 214, 255]`.
Per-pixel residual therefore changes from `105` to `110` for all six pixels.

## Evidence

Primary artifact:

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/m60-f16-webgpu-width-quantized-render-fix-for431.json`

Images:

- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/reference-cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/current-webgpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/current-webgpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/opt-in-webgpu-width-quantized.png`
- `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-width-quantized-render-fix-for431/opt-in-webgpu-width-quantized-diff.png`

Source evidence:

- FOR-430 finding: `global/kanvas/findings/for-430-web-gpu-cpu-width-quantization-diagnostic-matches-cpu-for-m60-f16-1`
- FOR-430 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-webgpu-cpu-width-quantization-alignment-for430/m60-f16-webgpu-cpu-width-quantization-alignment-for430.json`
- FOR-429 artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-cpu-span-quantization-for429/m60-f16-cpu-span-quantization-for429.json`

## Decision

The render experiment is kept strictly opt-in and should not be promoted. The
coverage-count alignment is real (`36/96` -> `60/96`), but the image evidence
shows `opt-in-render-fix-regresses-scene`: the targeted pixels all regress and
the scene residual increases from `2014` to `2044`.

Any activation by default or broader coverage strategy remains a separate
ticket.

## Validation

Commands run:

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py`
- `rtk python3 scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py`
- `rtk python3 scripts/validate_for429_m60_f16_cpu_span_quantization.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for431-pycache python3 -m py_compile scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py`
- `rtk git diff --check`
