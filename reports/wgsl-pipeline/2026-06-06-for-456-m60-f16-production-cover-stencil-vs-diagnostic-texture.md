# FOR-456 M60 F16 production cover stencil vs diagnostic texture

## Scope

FOR-456 follows FOR-455 from the memory-first ticket draft
`global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-comparer-stencil-production-cover-et-texture-diagnostique-apres-for-455`.

The run is diagnostic only. It keeps default rendering, scoring, fallback
policy, PipelineKey selection, production WGSL, wgsl4k, FOR-442, and FOR-447
unchanged.

Opt-in flag:

`kanvas.webgpu.m60F16ProductionCoverStencilVsDiagnosticTextureFor456.enabled`

Artifact:

`reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-cover-stencil-vs-diagnostic-texture-for456/m60-f16-production-cover-stencil-vs-diagnostic-texture-for456.json`

## Result

Classification:

`production-cover-stencil-readback-unavailable`

FOR-456 confirms the FOR-453 diagnostic texture still reads six zero-stencil
targets at the FOR-455 coordinates. The production cover-pass depth/stencil
attachment remains `GPUTextureUsage.RenderAttachment` only, with no `CopySrc`
or texture-binding usage. FOR-456 therefore does not attempt to copy or shader
read the production depth/stencil texture.

Counts:

- `diagnosticStencilZeroTargetCount=6`
- `productionStencilReadbackAvailableTargetCount=0`
- `productionStencilComparisonAvailableTargetCount=0`
- `insideShaderEmissionOnDiagnosticZeroStencilCount=6`
- `isolatedColorTargetEmissionCount=6`
- `postCoverAvailableTargetCount=6`
- `for442DecisionSourceUsedCount=0`

This is not a support claim and not a rendering fix. FOR-442 is excluded as a
decision source, and FOR-447 is not promoted.

## Conclusion

FOR-456 narrows the gap one step further: the FOR-453 diagnostic texture can be
read, but the production cover-pass stencil attachment cannot be directly
compared without changing its usage or adding a separate production-bound
diagnostic resource. The current evidence is therefore an explicit readback
availability result, not a proof that production stencil matches or differs
from the diagnostic texture.

Suite unique:

Open a focused ticket that adds a separate production-bound diagnostic stencil resource
for the cover pass and compares it against the FOR-453 diagnostic texture.

## Validation

Commands used or required:

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ProductionCoverStencilVsDiagnosticTextureFor456.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for456_m60_f16_production_cover_stencil_vs_diagnostic_texture.py
rtk python3 scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for456-pycache python3 -m py_compile scripts/validate_for456_m60_f16_production_cover_stencil_vs_diagnostic_texture.py scripts/validate_for455_m60_f16_zero_stencil_cover_emission_audit.py
rtk git diff --check
```
