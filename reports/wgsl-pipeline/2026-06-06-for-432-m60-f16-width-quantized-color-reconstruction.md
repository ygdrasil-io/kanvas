# FOR-432 M60 F16 width-quantized color reconstruction

Date: 2026-06-06

## Classification

`reconstruction-matches-regression`

FOR-432 is diagnostic-only and applies no correction. The opt-in FOR-431
variant still regresses the same six M60 F16 pixels from current WebGPU
`[181, 191, 230, 255]` to opt-in `[111, 147, 129, 255]`, while the CPU
reference remains `[133, 150, 214, 255]`.

## Evidence

- Linear: FOR-432.
- Flag: `kanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled`.
- Source finding: `global/kanvas/findings/for-431-web-gpu-width-quantized-opt-in-render-fix-regresses-m60-f16`.
- Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-width-quantized-color-reconstruction-for432/m60-f16-width-quantized-color-reconstruction-for432.json`.
- Pixel set: exactly `(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

## Key Numbers

- Coverage: current `36/96`, CPU width-quantized `60/96`, opt-in `60/96`.
- Per-pixel coverage: current `6/16`, opt-in `10/16`.
- Total residual: `2014 -> 2044`.
- Per-pixel residual: `105 -> 110`.
- Changed pixels: six targeted pixels only, inherited from FOR-431.
- Trace completeness: `0` incomplete pixels.
- Final classification count: six `reconstruction-matches-regression`.

## Reconstruction Result

For each selected pixel, FOR-432 captures:

- shader-return source from the width-quantized diagnostic path;
- destination before the effective opt-in drawIndex 3, `[181, 191, 230, 255]`;
- destination after the opt-in pass, `[111, 147, 129, 255]`;
- selected single stencil-gated subdraw with coverage `10/16`;
- test-side premultiplied `SrcOver` reconstruction;
- delta between reconstruction and the observed opt-in image.

The diagnostic image matches the opt-in image. The replay is bounded to the
effective opt-in drawIndex 3, then evaluates three candidate reconstructions:
single `inside`, single `outside`, and `inside+outside`. The AA cover pipelines
use opposite stencil compare operations, so a fragment is expected to contribute
through one subdraw, not both. Both single-subdraw candidates produce the same
source color and reconstruct the observed opt-in output within byte tolerance;
the forced `inside+outside` replay is retained only as a rejected candidate.

This means FOR-432 no longer supports a fixed-function blend suspicion. The
regressed FOR-431 color is coherent with applying one stencil-gated `10/16`
source over the captured destination `[181, 191, 230, 255]`; the next fix should
target the coverage/source model or stencil routing, not attachment blend.

## Non-Goals

No default rendering change, support claim, promotion, threshold change,
scoring change, fallback policy change, `PipelineKey` change, production WGSL
file change, or `wgsl4k` change is made. The added shader variant is an
in-memory diagnostic path selected only when FOR-431, FOR-432, and the existing
shader-return diagnostic bind group are active.

## Validation

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16WidthQuantizedColorReconstructionFor432.enabled=true -Dkanvas.webgpu.m60F16WidthQuantizedRenderFixFor431.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py
rtk python3 scripts/validate_for431_m60_f16_webgpu_width_quantized_render_fix.py
rtk python3 scripts/validate_for430_m60_f16_webgpu_cpu_width_quantization_alignment.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for432-pycache python3 -m py_compile scripts/validate_for432_m60_f16_width_quantized_color_reconstruction.py
rtk git diff --check
```
