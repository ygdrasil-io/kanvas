# FOR-451 - Frontiere apres stencil via split de render pass opt-in

Date: 2026-06-06

## Resume

FOR-451 ajoute une instrumentation opt-in pour separer la passe
`StencilCoverAaPolygonDraw` M60 F16 en deux `render pass` (passes de rendu):

- premier pass: ecriture stencil uniquement ;
- lecture couleur intermediaire apres stencil et avant cover ;
- second pass: cover inside/outside avec stencil charge.

Le drapeau est:
`kanvas.webgpu.m60F16StencilRenderPassSplitFor451.enabled`.

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-render-pass-split-boundary-for451/m60-f16-stencil-render-pass-split-boundary-for451.json`.

## Source

- Brouillon memoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-exposer-la-frontiere-apres-stencil-via-split-de-render-pass-opt-in`
- Finding source: `global/kanvas/findings/for-450-boundary-audit-m60-f16-exige-un-split-de-render-pass-pour-preuve-stencil-directe`
- FOR-450: la frontiere apres stencil avant cover exigeait un split de render pass.
- FOR-442 est exclu comme source de decision.

## Classification

Classification obtenue:
`stencil-render-pass-split-color-boundary-only`.

Raison: le split opt-in expose bien une frontiere couleur apres l'ecriture
stencil et avant les draws cover pour les six pixels M60 F16 zero-mask. En
revanche, le depth/stencil reste en `GPUTextureUsage.RenderAttachment`, sans
`CopySrc` ni `TextureBinding`; la lecture directe du stencil reste donc
indisponible.

## Mesures

- Similarite courante: `95.914714`
- Pixels mismatch courants: `1004`
- Frontiere avant stencil: couleur observee `6/6`
- Frontiere apres stencil avant cover: couleur observee `6/6`
- Frontiere apres cover: couleur observee `6/6`
- Lecture directe stencil: indisponible

## Garanties

- Aucun rendu par defaut n'est change.
- Le split est inactif sans propriete systeme explicite.
- Aucune correction FOR-447 n'est activee.
- Aucun seuil, score, `fallback policy` (politique de repli), `PipelineKey`,
  `wgsl4k` ou WGSL de production n'est modifie.
- FOR-442 reste exclu.

## Decision

FOR-451 prouve l'ordre de pipeline avec une frontiere couleur intermediaire,
mais ne prouve pas le contenu stencil directement. Toute correction fondee sur
le stencil reste refusee tant qu'une extension backend ne fournit pas une
lecture stencil diagnostique sure.

Suite unique recommandee: ouvrir un ticket d'extension backend pour une voie
diagnostique stencil lisible ou copiable, uniquement si la preuve directe du
contenu stencil reste necessaire.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:compileKotlin :gpu-raster:compileTestKotlin
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilRenderPassSplitFor451.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py
rtk python3 scripts/validate_for450_m60_f16_stencil_boundary_audit.py
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for451-pycache python3 -m py_compile scripts/validate_for451_m60_f16_stencil_render_pass_split_boundary.py scripts/validate_for450_m60_f16_stencil_boundary_audit.py
rtk git diff --check
```
