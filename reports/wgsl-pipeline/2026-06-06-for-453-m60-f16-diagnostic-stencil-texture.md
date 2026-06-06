# FOR-453 - M60 F16 texture stencil diagnostique separee

Date: 2026-06-06

## Objet

FOR-453 implemente une texture depth/stencil diagnostique separee pour obtenir
une preuve directe du stencil sur les six pixels residuels M60 F16 :

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

L'instrumentation est inactive par defaut et controlee par :

```text
kanvas.webgpu.m60F16DiagnosticStencilTextureFor453.enabled
```

Source memoire relue avant Linear :

```text
global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-texture-stencil-diagnostique-separee-apres-for-452
```

Source de preuve precedente :

```text
global/kanvas/findings/for-452-backend-stencil-readback-audit-conclut-texture-diagnostique-separee
```

FOR-452 fournit la decision de ne pas lire la texture depth/stencil de
production et d'ouvrir une voie diagnostique separee.

## Resultat

Classification :

```text
diagnostic-stencil-texture-direct-copy-available
```

FOR-453 rejoue la passe stencil M60 F16 dans une ressource diagnostique separee
creee avec :

```text
GPUTextureUsage.RenderAttachment | GPUTextureUsage.CopySrc
```

La texture depth/stencil de production reste limitee a :

```text
GPUTextureUsage.RenderAttachment
```

La copie `GPUTextureAspect.StencilOnly` vers buffer fonctionne sur l'adaptateur
Apple M2 Max. Les six pixels cibles sont lus avec `stencilValue=0` et
`stencilCovered=false`.

## Artefact

```text
reports/wgsl-pipeline/scenes/artifacts/m60-f16-diagnostic-stencil-texture-for453/m60-f16-diagnostic-stencil-texture-for453.json
```

L'artefact contient :

- trois evenements de texture diagnostique creee ;
- trois copies stencil reussies ;
- six pixels cibles lus directement ;
- `directStencilReadbackAvailable=true` ;
- `stencilValuesAvailableTargetCount=6` ;
- `for442DecisionSourceUsedCount=0`.

## Exclusions

FOR-442 est exclu comme source de decision. FOR-453 ne promeut pas FOR-447, ne
modifie pas les seuils, le scoring, la fallback policy, `PipelineKey`, `wgsl4k`,
ni le WGSL de production. Le rendu par defaut n'est pas modifie.

## Conclusion

Suite unique : ouvrir un ticket d'attribution cover/source qui utilise les six
valeurs stencil a zero pour identifier quelle passe ou quelle contribution
couleur allume encore les pixels residuels M60 F16.

FOR-453 clot la question de faisabilite de lecture stencil diagnostique separee :
la voie directe est disponible en mode opt-in, mais les valeurs lues ne justifient
pas une correction fondee sur une appartenance stencil positive pour ces six
pixels.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16DiagnosticStencilTextureFor453.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for453_m60_f16_diagnostic_stencil_texture.py
rtk python3 scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for453-pycache python3 -m py_compile scripts/validate_for453_m60_f16_diagnostic_stencil_texture.py scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py
rtk git diff --check
```
