# FOR-457 - M60 F16 production-bound cover stencil diagnostic

## Conclusion

Classification: `production-bound-cover-stencil-matches-for453-diagnostic-zero`.

FOR-457 ajoute une ressource depth/stencil diagnostique separee, rejoue le passage stencil puis les pipelines cover de production, et copie l'aspect `StencilOnly` vers un buffer de lecture. Le diagnostic reste opt-in via `kanvas.webgpu.m60F16ProductionBoundCoverStencilDiagnosticFor457.enabled`.

Resultat observe: les 6 pixels cibles M60 F16 sont lisibles dans le replay FOR-457 et valent tous `0`, comme dans la texture diagnostique FOR-453. La divergence restante ne vient donc pas d'une difference visible entre la texture stencil diagnostique FOR-453 et un replay cover lie a une ressource diagnostique de production.

## Preuves

- Artefact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-production-bound-cover-stencil-diagnostic-for457/m60-f16-production-bound-cover-stencil-diagnostic-for457.json`
- Source precedente: FOR-456, qui concluait que le stencil de production direct n'etait pas lisible car la texture principale reste `GPUTextureUsage.RenderAttachment`.
- Reference diagnostique: FOR-453, qui fournit une texture depth/stencil separee avec `GPUTextureUsage.RenderAttachment | GPUTextureUsage.CopySrc`.
- Drapeau opt-in: `kanvas.webgpu.m60F16ProductionBoundCoverStencilDiagnosticFor457.enabled`

Compteurs principaux:

- `for453DiagnosticStencilZeroTargetCount`: 6
- `productionBoundDiagnosticStencilAvailableTargetCount`: 6
- `for453ComparisonAvailableTargetCount`: 6
- `matchingFor453DiagnosticZeroTargetCount`: 6
- `differingStencilTargetCount`: 0
- `insideShaderEmissionOnDiagnosticZeroStencilCount`: 6
- `isolatedColorTargetEmissionCount`: 6
- `postCoverAvailableTargetCount`: 6
- `for442DecisionSourceUsedCount`: 0

## Exclusions

Ce n'est pas un correctif de rendu. Le rendu par defaut, les seuils, le scoring, la politique de fallback, `PipelineKey`, le WGSL de production et `wgsl4k` ne changent pas. FOR-442 reste exclu comme source de decision, et FOR-447 n'est pas promu.

La texture depth/stencil de production principale reste `GPUTextureUsage.RenderAttachment`, sans `CopySrc`, et le rendu normal n'utilise pas la ressource diagnostique.

## Suite unique

Ouvrir un ticket cible pour expliquer pourquoi le passage cover de production emet encore la couleur source inside alors qu'un replay cover diagnostique avec les pipelines de production confirme les memes valeurs stencil `0` que FOR-453.

## Validation

- `rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin`
- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16ProductionBoundCoverStencilDiagnosticFor457.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest`
