# FOR-445: M60 F16 runtime integer-lane mask probe

## Result

Classification: `runtime-integer-lane-refutes-float-mask`.

The FOR-445 diagnostic is strictly opt-in through
`kanvas.webgpu.m60F16RuntimeIntegerLaneMaskProbeFor445.enabled`. It does not
change default rendering, thresholds, scoring, fallback policy, PipelineKey
selection, production WGSL resources, or wgsl4k behavior.

Source draft memory:
`global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-sonde-integer-lane-runtime-mask-et-covered-count`

Source finding memory:
`global/kanvas/findings/for-444-runtime-mask-source-field-remains-ambiguous-against-low-level-zero-masks`

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-runtime-integer-lane-mask-probe-for445/m60-f16-runtime-integer-lane-mask-probe-for445.json`

## Evidence

The six CPU-excluded M60 F16 pixels were sampled:

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

Summary:

- CPU green mask zero count: 6 / 6.
- FOR-442 float runtime mask available count: 2 / 6.
- FOR-442 nonzero float masks: `(92,75)=0x005C`, `(89,78)=0x0058`.
- FOR-445 integer runtime mask available count: 6 / 6.
- FOR-445 nonzero integer masks: 0 / 6.
- FOR-445 covered count available count: 6 / 6.
- FOR-445 mask popcount mismatch count: 0.
- FOR-443 low-level exact mask available count: 6 / 6.
- FOR-443 nonzero low-level masks: 0 / 6.
- FOR-445 vs FOR-442 mismatch count: 2.
- FOR-445 vs FOR-443 mismatch count: 0.

The FOR-445 integer lane writes `0x0000` with covered count `0` for all six
coordinates. This matches FOR-443 and refutes the two nonzero FOR-442 float
storage masks.

## Layout

FOR-445 uses diagnostic storage `array<vec4u, 18>`.

Per pixel:

- stride: 48 bytes;
- coordinate: offsets 0 and 4;
- mask: offset 8;
- covered count: offset 12;
- valid flag: offset 16;
- subdraw ordinal: offset 20;
- side/role: offset 24;
- edge count echo: offset 28;
- fill type echo: offset 32;
- probe tag: offset 36.

The bit order remains row-major 4x4 subsamples, with the least significant bit
at `sx=0, sy=0`.

## Interpretation

FOR-445 removes the ambiguity left by FOR-444 for the two nonzero FOR-442
float masks. The integer-lane path is valid, internally consistent, and agrees
with the FOR-443 low-level zero masks.

The next correction work should not use the FOR-442 float mask field as a
coverage source. The likely next step is to audit the FOR-442 float storage
field/source tuple or retire that field from the decision chain before deriving
any rendering correction.

## Validation

Commands:

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16RuntimeIntegerLaneMaskProbeFor445.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py`
- `rtk python3 scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py`
- `rtk python3 scripts/validate_for443_m60_f16_webgpu_low_level_exact_mask_probe.py`
- `rtk python3 scripts/validate_for442_m60_f16_webgpu_runtime_exact_mask_probe.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for445-pycache python3 -m py_compile scripts/validate_for445_m60_f16_runtime_integer_lane_mask_probe.py scripts/validate_for444_m60_f16_runtime_mask_packing_vs_low_level_probe.py`
- `rtk git diff --check`
