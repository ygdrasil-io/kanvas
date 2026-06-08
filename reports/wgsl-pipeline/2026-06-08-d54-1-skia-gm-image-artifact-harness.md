# D54-1 - skia-gm-image artifact harness

Date: 2026-06-08
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d54-1-ajouter-un-harnais-artefacts-row-specific-pour-skia-gm-image`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json`
Previous refusal evidence: `reports/wgsl-pipeline/2026-06-06-d51-2-imagegm-row-specific-evidence.md`

## Resultat

D54-1 ajoute le harness de capture row-specific pour `ImageGM` /
`skia-gm-image`.

Le run local avec `-Dkanvas.sceneEvidence.write=true` a produit les artefacts
D54-1: reference, CPU, WebGPU, diffs, `stats.json`, `route-cpu.json` et
`route-gpu.json`.

La ligne `skia-gm-image` reste `expected-unsupported`: WebGPU produit un bitmap
sur `Apple M2 Max`, mais atteint `98.5962%`, sous le seuil local de promotion
`99.95%`.

La raison active est:
`image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required`.

## Mesures locales

| Backend | Statut | Similarite | Seuil | Decision |
|---|---|---:|---:|---|
| CPU | `pass` | `98.4826%` | `98.0%` | Artefact CPU row-specific utilisable. |
| WebGPU | `expected-unsupported` | `98.5962%` | `99.95%` | Pas de support revendique. |

Le paquet contient donc reference, CPU, WebGPU, diffs, routes et stats, mais le
score WebGPU ne permet pas de poser `fallbackReason=none`.

## Ce que le harness produit

Le test `org.skia.gpu.webgpu.ImageGmSceneCaptureTest` charge la reference
historique `image-surface.png`, rend `ImageGM()` par la voie CPU
`TestUtils.runGmTest`, puis tente le rendu WebGPU via `WebGpuSink.draw`.

Par defaut, le test ne materialise pas les artefacts. Avec:

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest
```

il ecrit sous `reports/wgsl-pipeline/scenes/artifacts/d54-skia-gm-image/`:

| Artefact | Condition |
|---|---|
| `skia.png` | toujours en mode ecriture |
| `cpu.png` | toujours en mode ecriture |
| `cpu-diff.png` | toujours en mode ecriture |
| `gpu.png` | produit dans le run local, car WebGPU a rendu un bitmap |
| `gpu-diff.png` | produit dans le run local, car WebGPU a rendu un bitmap |
| `route-cpu.json` | toujours en mode ecriture |
| `route-gpu.json` | toujours en mode ecriture |
| `stats.json` | toujours en mode ecriture |

## Politique de support

D54-1 ne promeut pas la ligne par structure seule et ne met pas a jour le
dashboard global.

Le `route-gpu.json` declare `status: expected-unsupported` et garde la raison
stable D51/D54 parce que la similarite WebGPU mesuree reste sous `99.95%`.
`fallbackReason=none` reste interdit pour cette ligne dans l'etat courant.

Si un run futur atteint `99.95%` avec `fallbackReason=none`, D54-1 documente
seulement que le harness sait produire une preuve passante. La promotion du
dashboard global reste hors scope tant qu'un ticket de promotion ne valide pas
explicitement `results.json`, `scenes.json` et les compteurs globaux.

## Boundary row-specific

`ImageGM` est la GM `image-surface`: elle fabrique des snapshots de surfaces
raster N32 premul 64x64, puis les redessine avec `drawImage`, `surf.draw` et
`drawImageRect` sur une sortie 960x1200. Le paquet D54-1 ne reutilise pas les
preuves de `bitmappremul`, `drawbitmaprect`, `drawminibitmaprect`,
`ImageSource`, `MakeRasterImageGM`, `BitmapImageGM`, ni d'autres scenes
voisines.

## Non-claims

- Aucun gain de score n'est revendique.
- Aucun support WebGPU pour `ImageGM` n'est revendique sans run d'artefacts
  WebGPU passant.
- Aucun support Skia-comparable supplementaire n'est revendique par structure
  seule.
- Aucun changement de `results.json`, `scenes.json`, manifestes D50/D51/D53,
  dashboard global, seuil global, scoring global, politique fallback globale,
  `PipelineKey`, WGSL de production, `wgsl4k`, source upstream ou rendu par
  defaut.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode,
  color-managed image decode, image decode arbitraire ou image large n'est
  revendique.

## Validation attendue

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest
rtk python3 scripts/validate_d54_skia_gm_image_artifact_harness.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d54-skia-gm-image-artifact-harness.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d54-image-pycache python3 -m py_compile scripts/validate_d54_skia_gm_image_artifact_harness.py
rtk git diff --check
```
