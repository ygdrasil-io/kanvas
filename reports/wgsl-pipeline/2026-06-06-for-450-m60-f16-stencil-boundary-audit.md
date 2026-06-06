# FOR-450 - Audit de frontière stencil-write M60 F16

Date: 2026-06-06

## Résumé

FOR-450 ajoute un audit opt-in des frontières disponibles autour de
`StencilCoverAaPolygonDraw` pour les six pixels M60 F16 zero-mask.

Le drapeau est:
`kanvas.webgpu.m60F16StencilBoundaryAuditFor450.enabled`.

Artefact:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-boundary-audit-for450/m60-f16-stencil-boundary-audit-for450.json`.

## Source

- Brouillon mémoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-exposer-une-frontiere-de-preuve-stencil-write-sure-apres-for-449`
- Finding source: `global/kanvas/findings/for-449-trace-stencil-subpass-m60-f16-reste-inconclusive-sans-lecture-stencil-directe`
- FOR-449: les fragments inside/outside sont observés, mais la lecture stencil directe reste indisponible.
- FOR-442 est exclu comme source de décision.

## Classification

Classification attendue:
`stencil-boundary-requires-render-pass-split`.

Raison: Kanvas expose déjà une frontière couleur avant le draw et une frontière
couleur après le cover, mais le stencil write et les deux cover draws sont
encodés dans un seul render pass. Une observation après stencil et avant cover
nécessite donc un split de render pass ou une extension backend dédiée.

## Garanties

- Aucun rendu par défaut n'est changé.
- Aucune correction FOR-447 n'est activée.
- Aucun seuil, score, `fallback policy` (politique de repli), `PipelineKey`,
  `wgsl4k` ou WGSL de production n'est modifié.
- FOR-442 reste exclu.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilBoundaryAuditFor450.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for450_m60_f16_stencil_boundary_audit.py
rtk python3 scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for450-pycache python3 -m py_compile scripts/validate_for450_m60_f16_stencil_boundary_audit.py scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py
rtk git diff --check
```
