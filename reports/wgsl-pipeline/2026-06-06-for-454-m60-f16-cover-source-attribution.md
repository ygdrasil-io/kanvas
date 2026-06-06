# FOR-454 - M60 F16 attribution cover/source apres FOR-453

Date: 2026-06-06

## Objet

FOR-454 attribue la contribution qui allume encore les six pixels residuels M60
F16 alors que FOR-453 a prouve une valeur stencil diagnostique a zero.

Pixels cibles :

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

L'instrumentation est inactive par defaut et controlee par :

```text
kanvas.webgpu.m60F16CoverSourceAttributionFor454.enabled
```

Source memoire relue avant Linear :

```text
global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-attribution-cover-source-apres-for-453
```

Source de preuve precedente :

```text
global/kanvas/findings/for-453-diagnostic-stencil-texture-direct-copy-available-and-zero-target-stencil-values
```

## Resultat

Classification :

```text
cover-source-attribution-cover-writes-zero-stencil-pixels
```

FOR-454 active un drapeau composite opt-in qui reutilise les diagnostics
existants : predraw destination, frontiere apres stencil avant cover, texture
stencil diagnostique FOR-453, shader-return cover, isolated color target, et
post-cover readback.

Les six pixels ont :

- `stencilValue=0` ;
- `stencilCovered=false` ;
- predraw disponible ;
- frontiere apres stencil avant cover disponible ;
- shader-return observe ;
- isolated color target disponible ;
- post-cover disponible.

## Artefact

```text
reports/wgsl-pipeline/scenes/artifacts/m60-f16-cover-source-attribution-for454/m60-f16-cover-source-attribution-for454.json
```

Resume de l'artefact :

- `stencilZeroTargetCount=6`
- `predrawAvailableTargetCount=6`
- `afterStencilBeforeCoverAvailableTargetCount=6`
- `shaderReturnObservedTargetCount=6`
- `isolatedColorTargetAvailableTargetCount=6`
- `postCoverAvailableTargetCount=6`
- `for442DecisionSourceUsedCount=0`

## Exclusions

FOR-442 est exclu comme source de decision. FOR-454 ne promeut pas FOR-447, ne
modifie pas les seuils, le scoring, la fallback policy, `PipelineKey`, `wgsl4k`,
ni le WGSL de production. Le rendu par defaut n'est pas modifie.

## Conclusion

Suite unique : ouvrir un ticket cible pour auditer pourquoi le chemin
cover/source emet une couleur sur des coordonnees dont l'appartenance stencil
diagnostique est nulle.

FOR-454 ne corrige pas le rendu. Il deplace la question depuis "peut-on lire le
stencil ?" vers "pourquoi le chemin cover/source emet-il encore sur ces
coordonnees stencil zero ?".

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16CoverSourceAttributionFor454.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for454_m60_f16_cover_source_attribution.py
rtk python3 scripts/validate_for453_m60_f16_diagnostic_stencil_texture.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for454-pycache python3 -m py_compile scripts/validate_for454_m60_f16_cover_source_attribution.py scripts/validate_for453_m60_f16_diagnostic_stencil_texture.py
rtk git diff --check
```
