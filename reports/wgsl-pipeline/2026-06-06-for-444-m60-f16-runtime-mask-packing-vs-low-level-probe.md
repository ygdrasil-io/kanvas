# FOR-444 M60 F16 runtime mask packing vs low-level probe

Date: 2026-06-06

## Scope

FOR-444 adds a strict opt-in diagnostic audit behind
`kanvas.webgpu.m60F16RuntimeMaskPackingVsLowLevelProbeFor444.enabled`.
It compares the FOR-442 runtime mask storage field with the FOR-443 low-level
compute probe for exactly the six M60 F16 CPU-excluded pixels:

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

Source memory:
`global/kanvas/findings/for-443-web-gpu-low-level-exact-masks-are-zero-for-m60-f16-six-pixel-set`.

## Result

Classification: `runtime-mask-source-field-ambiguous`.

The audit keeps the default rendering path unchanged. It does not change
thresholds, scoring, fallback policy, `PipelineKey`, production WGSL,
`wgsl4k`, or FOR-431 behavior.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-runtime-mask-packing-vs-low-level-probe-for444/m60-f16-runtime-mask-packing-vs-low-level-probe-for444.json`

FOR-442 runtime masks:

- `(92,75)` has runtime mask `0x005C`
- `(89,78)` has runtime mask `0x0058`

FOR-443 low-level masks:

- all six pixels have low-level mask `0x0000`

The FOR-442 runtime layout is audited as:

- storage declaration: `array<vec4f, 224>`
- sample stride: `112` bytes
- mask field offset inside sample: `96` bytes
- numeric read: `f32 rounded to Int`

The FOR-443 low-level layout is audited as:

- storage declaration: `array<vec4u, 12>`
- sample stride: `32` bytes
- mask field offset inside sample: `8` bytes
- numeric read: `u32 masked with 0xFFFF`

## Interpretation

The two nonzero FOR-442 runtime masks are present on valid runtime storage
tuples, but the FOR-443 low-level probe returns `0x0000` for the same
coordinates. The runtime mask source field is still ambiguous because the
runtime field is unavailable for four of the six pixels and the available
field is not enough to justify a rendering correction.

Do not correct the stencil-cover predicate from FOR-442 masks alone. The next
useful step is a narrower source-field probe that writes the runtime mask and
covered count as integer lanes, so the runtime field can be separated from the
fragment coverage path and float packing.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16RuntimeMaskPackingVsLowLevelProbeFor444.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py
rtk python3 scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py
rtk python3 scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for444-pycache python3 -m py_compile scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py
rtk git diff --check
```
