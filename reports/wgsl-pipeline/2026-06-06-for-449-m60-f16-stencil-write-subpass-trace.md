# FOR-449 - M60 F16 stencil-write et sous-passes

Date: 2026-06-06

## Résumé

FOR-449 ajoute une preuve opt-in pour les six pixels M60 F16 zero-mask:
`(92,75)`, `(91,76)`, `(90,77)`, `(89,78)`, `(88,79)`, `(87,80)`.

Le drapeau est:
`kanvas.webgpu.m60F16StencilWriteSubpassTraceFor449.enabled`.

L'artefact attendu est:
`reports/wgsl-pipeline/scenes/artifacts/m60-f16-stencil-write-subpass-trace-for449/m60-f16-stencil-write-subpass-trace-for449.json`.

## Source

- Brouillon mémoire: `global/kanvas/tickets/drafts/brouillon-ticket-m60-f16-tracer-directement-stencil-write-et-ecritures-fragmentaires-apres-for-448`
- Finding source: `global/kanvas/findings/for-448-zero-mask-neutral-path-trace-reste-inconclusive`
- FOR-448: la trace inside/outside/both reste neutre.
- FOR-447: la correction zero-mask opt-in reste neutre.
- FOR-442 est exclu comme source de décision.

## Classification

Classification attendue après génération:
`direct-stencil-subpass-trace-inconclusive`.

Raison: FOR-449 agrège les traces `shaderReturn`, `contributionIsolation`,
destination avant/après et variantes finales FOR-448, mais Kanvas n'expose pas
encore de frontière WebGPU sûre pour lire directement le stencil buffer. Le
rapport refuse donc de nommer une correction candidate.

## Garanties

- Aucun rendu par défaut n'est changé.
- FOR-447 n'est pas promu.
- Aucun seuil, score, `fallback policy` (politique de repli), `PipelineKey`,
  `wgsl4k` ou WGSL de production n'est modifié.
- La trace est uniquement une preuve opt-in.
- FOR-442 reste exclu.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true -Dkanvas.webgpu.m60F16StencilWriteSubpassTraceFor449.enabled=true :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.StrokeCapJoinSceneCaptureTest
rtk python3 scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py
rtk python3 scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for449-pycache python3 -m py_compile scripts/validate_for449_m60_f16_stencil_write_subpass_trace.py scripts/validate_for448_m60_f16_zero_mask_neutral_path_trace.py
rtk git diff --check
```
