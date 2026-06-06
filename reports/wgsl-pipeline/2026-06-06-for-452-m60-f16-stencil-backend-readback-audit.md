# FOR-452 - M60 F16 stencil backend readback audit

Date: 2026-06-06

## Objet

FOR-452 audite si le backend WebGPU Kanvas peut transformer la frontière couleur
FOR-451 en preuve directe du stencil pour les six pixels M60 F16 :

- `(92,75)`
- `(91,76)`
- `(90,77)`
- `(89,78)`
- `(88,79)`
- `(87,80)`

L'instrumentation est inactive par défaut et contrôlée par :

```text
kanvas.webgpu.m60F16StencilBackendReadbackAuditFor452.enabled
```

Source mémoire relue avant Linear :

```text
global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-auditer-une-extension-backend-stencil-lisible-ou-copiable-apres-for-451
```

Source de preuve précédente :

```text
global/kanvas/findings/for-451-split-render-pass-m60-f16-expose-une-frontiere-couleur-sans-lecture-stencil-directe
```

## Résultat

Classification :

```text
stencil-backend-diagnostic-texture-required
```

FOR-451 est réutilisé comme frontière d'ordonnancement : stencil écrit, lecture
couleur intermédiaire, puis cover. FOR-452 ne change pas le rendu par défaut et
ne tente pas de copier la texture stencil principale.

## Audit backend

Voies auditées :

| Voie | Décision |
|---|---|
| Texture depth/stencil principale avec `CopySrc` | Refusée : la texture principale est créée avec `GPUTextureUsage.RenderAttachment` uniquement. |
| Texture depth/stencil principale avec `TextureBinding` | Refusée : la texture principale n'est pas créée avec `TextureBinding`. |
| Copie stencil vers buffer | Refusée sur la texture principale : `TexelCopyTextureInfo` expose un aspect et `GPUTextureAspect.StencilOnly` existe, mais la source n'a pas `CopySrc`. |
| Lecture shader/compute | Refusée sur la texture principale : pas de `TextureBinding` et pas de chemin shader stencil existant. |
| Texture depth/stencil diagnostique séparée | Suite possible : à implémenter dans un ticket dédié si une preuve stencil directe reste nécessaire. |

## Artefact

```text
reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-backend-readback-audit-for452/m60-f16-stencil-backend-readback-audit-for452.json
```

L'artefact doit contenir :

- les six pixels ciblés ;
- les frontières couleur avant stencil, après stencil avant cover, et après cover ;
- `directStencilReadbackAvailable=false` ;
- `stencilValue=null` et `stencilCovered=null` pour chaque pixel ;
- les routes backend auditées et leur raison de refus.

## Exclusions

FOR-442 est exclu comme source de décision. FOR-452 ne promeut pas FOR-447, ne
modifie pas les seuils, le scoring, la fallback policy, `PipelineKey`, `wgsl4k`,
ni le WGSL de production.

## Conclusion

Suite unique : ouvrir un ticket de texture diagnostique séparée avec un stencil
copiable ou bindable. FOR-452 abandonne la lecture directe depuis la texture
depth/stencil principale actuelle, mais ne conclut pas encore sur les
hypothèses couverture, géométrie, blend, destination ou couleur source.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilBackendReadbackAuditFor452.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py
rtk python3 scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for452-pycache python3 -m py_compile scripts/validate_for452_m60_f16_stencil_backend_readback_audit.py scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py
rtk git diff --check
```
