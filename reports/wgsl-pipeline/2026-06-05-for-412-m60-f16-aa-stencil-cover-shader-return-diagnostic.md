# FOR-412 M60 F16 AA stencil-cover shader-return diagnostic

Date: 2026-06-05

## Result

- Linear: FOR-412
- Classification: `shader-return-zero-but-post-pass-colored`
- Guard: `kanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled`
- Guard default: disabled
- Scene: `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`

FOR-412 adds an opt-in diagnostic that captures the actual `vec4f` returned by
the bounded M60 F16 `StencilCoverAaPolygonDraw` fragment shader immediately
before `@location(0)` is handed to fixed-function blend.

## Evidence

Artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412/m60-f16-aa-stencil-cover-shader-return-diagnostic-for412.json`

Summary from the artifact:

- 16 FOR-401 coordinates inspected.
- 16 pixels have a non-synthetic shader-return capture.
- 44 shader-return subdraw samples were observed.
- 16 FOR-410 `dstBeforeRgbaFloat` values were consumed.
- 16 FOR-405 post-pass colors were available.
- 0 pixels replay to the post-pass color with the shader return.
- 16 pixels classify as `shader-return-zero-but-post-pass-colored`.

The captured `sourceColorSentToBlend` values are zero for the observed
subdraws, while the FOR-405 post-pass pixels remain colored. This means the
colored post-pass value is not explained by the observed bounded shader return.
`correctedColorBeforeCoverage` is derived from the captured color-filter or
target-colorspace value using the captured shader branch; the direct return
proof remains `sourceColorSentToBlend`.
The FOR-411 source-over replay is consumed as a prevalidated source artifact,
not rerun inside the FOR-412 evidence capture.

## Scope

The diagnostic is strictly scoped to:

- M60 F16 bounded stroke cap/join evidence;
- `StencilCoverAaPolygonDraw`;
- the 16 FOR-401 coordinates;
- explicit evidence runs with the FOR-412 guard enabled.

No rendering default, threshold, score, promotion status, or support claim is
changed. FOR-400 remains context only and is not used as direct write proof.

## Source Links

The FOR-412 artifact links and validates these exact sources:

- FOR-401 final residual origin map.
- FOR-405 AA stencil-cover post-pass readback.
- FOR-408 per-subdraw contribution hook.
- FOR-410 predraw destination readback.
- FOR-411 source-over replay with predraw destination.

## Validation

- `rtk python3 scripts/validate_for412_m60_f16_aa_stencil_cover_shader_return_diagnostic.py`
- `rtk python3 scripts/validate_for411_m60_f16_source_over_replay_with_predraw_dst.py`
- `rtk python3 scripts/validate_for410_m60_f16_aa_stencil_cover_predraw_dst_readback.py`
- `rtk python3 scripts/validate_for408_m60_f16_aa_stencil_cover_per_subdraw_hook.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverShaderReturnDiagnostic.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverPredrawDstReadback.enabled=true -Dkanvas.webgpu.m60F16DirectPassWriteHook.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverContributionIsolation.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
