# FOR-402 M60 F16 pass-write probe

## Scope

FOR-402 adds a diagnostic-only, opt-in pass-write probe for the 16 final
residual pixels selected by FOR-401 in
`non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`.

The diagnostic is disabled by default behind
`kanvas.webgpu.m60F16PassWriteProbe.enabled`. It writes only the FOR-402
artifact during scene-evidence runs and does not change default rendering,
similarity thresholds, support policy, promotion state, or correction behavior.

## Source evidence reused

- Basic Memory source:
  `global/kanvas/findings/for-401-localise-le-residu-m60-f16-au-readback-final-sans-writer-identifie`
- FOR-401 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-401 classification: `residual-visible-only-at-final-readback`
- FOR-401 selected residual total: `1560`
- FOR-401 selected pixels in FOR-397 predicate: `6`
- FOR-401 selected pixels in FOR-400 window: `6`
- FOR-401 selected pixels outside FOR-400 window: `10`
- FOR-401 candidate attribution:
  `writtenByM60AaStencilCover=0`, `writtenByOtherPath=0`,
  `readbackOnlyUnknown=16`
- `supportClaim=false`, `promoted=false`

## FOR-402 result

- Linear: `FOR-402`
- Artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-pass-write-probe-for402/m60-f16-pass-write-probe-for402.json`
- Classification: `pass-write-probe-inconclusive`
- Closed classifications:
  `final-residual-written-by-m60-aa-stencil-cover`,
  `final-residual-written-by-other-draw`,
  `final-residual-not-observed-before-readback`,
  `pass-write-probe-inconclusive`
- Current total residual: `62748`
- Current mismatch pixels: `1615`
- Selected residual total: `1560`
- M60 AA stencil-cover writes observed: `0`
- Other draw writes observed: `0`
- Final residual not observed before readback: `0`
- Pass-write probe inconclusive: `16`

Each selected pixel records coordinates, final GPU color, reference color,
per-channel residual, total residual, FOR-397/FOR-400 membership, available
FOR-400 contribution evidence, and the unavailable draw id / pipeline family.

## Interpretation

FOR-402 preserves the exact 16 FOR-401 final residual coordinates. The current
instrumentation can reuse `SkWebGpuDevice.m60F16CoverageStencilContributionMapSnapshot()`
for the FOR-400 radius-1 M60 AA stencil-cover contribution window, but it does
not expose a per-draw framebuffer write hook before final readback.

The artifact therefore refuses to claim `final-residual-not-observed-before-readback`.
That classification would require direct evidence from a post-draw/pre-readback
texture boundary. With the available evidence, all 16 selected pixels remain
`pass-write-probe-inconclusive`.

## Non-goals preserved

- No correction was applied.
- M60 F16 remains unsupported: `supportClaim=false`.
- M60 F16 remains unpromoted: `promoted=false`.
- No similarity threshold or scoring rule changed.
- FOR-380 was not reintroduced.
- The diagnostic is not generalized beyond the M60 F16 scene.

## Next step

Instrument the `SkWebGpuDevice` render-pass draw submission or the
post-draw/pre-readback texture boundary for these 16 coordinates. The next
artifact should record, per sampled coordinate, draw id, pipeline family, and
the pixel value after each relevant draw before final readback packing.

Missing hook: `SkWebGpuDevice render-pass draw submission` or
`post-draw/pre-readback` sampled texture evidence.

## Validation commands

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16AaStencilCoverBandMetadataTransport.enabled=true -Dkanvas.webgpu.m60F16AaStencilCoverFragmentLaneDiagnostic.enabled=true -Dkanvas.webgpu.m60F16BoundedRuntimeCorrectionProbe.enabled=true -Dkanvas.webgpu.m60F16CoverageStencilContributionMap.enabled=true -Dkanvas.webgpu.m60F16FinalResidualOriginMap.enabled=true -Dkanvas.webgpu.m60F16PassWriteProbe.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
- `rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for402-pycache-agent python3 -m py_compile scripts/validate_for402_m60_f16_pass_write_probe.py`
- `rtk git diff --check`
- `rtk ./gradlew --no-daemon :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
