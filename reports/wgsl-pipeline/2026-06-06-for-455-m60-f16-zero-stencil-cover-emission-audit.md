# FOR-455 M60 F16 zero-stencil cover/source emission audit

## Scope

FOR-455 follows FOR-454 from the memory-first ticket draft
`global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-audit-cover-source-zero-stencil-emission-apres-for-454`.
The run is diagnostic only. It keeps default rendering, scoring, fallback policy,
PipelineKey selection, production WGSL, wgsl4k, FOR-442, and FOR-447 unchanged.

Opt-in flag:

`kanvas.webgpu.m60F16ZeroStencilCoverEmissionAuditFor455.enabled`

Artifact:

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-zero-stencil-cover-emission-audit-for455/m60-f16-zero-stencil-cover-emission-audit-for455.json`

## Result

Classification:

`zero-stencil-cover-emission-stencil-reference-or-compare-mismatch`

The six FOR-454 residual pixels still read `stencilValue=0` from the FOR-453
diagnostic stencil texture. The same six pixels are observed in the inside
cover subdraw with a non-zero source color and an isolated color target write.
For the audited cover pass, the inside role uses
`GPUCompareFunction.NotEqual` against stencil reference `0`, so a zero stencil
value is expected to reject the inside subdraw.

Counts:

- `stencilZeroTargetCount=6`
- `insideShaderEmissionOnZeroStencilCount=6`
- `isolatedColorTargetEmissionCount=6`
- `postCoverAvailableTargetCount=6`
- `for442DecisionSourceUsedCount=0`

FOR-442 is excluded as a decision source, and FOR-447 is not promoted. This is
not a support claim and not a rendering fix.

## Conclusion

FOR-455 narrows the issue to the boundary between the separate FOR-453
diagnostic stencil texture and the production cover-pass stencil attachment.
The shader/source and isolated target evidence show cover/source emission at
coordinates where the diagnostic stencil evidence says the inside cover subdraw
should be rejected.

Suite unique:

Open a focused ticket comparing the production cover-pass stencil attachment
contents against the separate diagnostic stencil texture at the exact inside
subdraw boundary.

## Validation

Commands used or required:

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ZeroStencilCoverEmissionAuditFor455.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py
rtk python3 scripts/validate_for454_m60_f16_cover_source_attribution.py
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for455-pycache python3 -m py_compile scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py scripts/validate_for454_m60_f16_cover_source_attribution.py
rtk git diff --check
```
