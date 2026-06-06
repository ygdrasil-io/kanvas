# D52-2 - DrawMiniBitmapRect artifact harness

Date: 2026-06-06
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d52-2-ajouter-harness-artefacts-drawminibitmaprect`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json`
Previous readiness packet: `reports/wgsl-pipeline/2026-06-06-d52-1-drawminibitmaprect-promotion-readiness.md`

## Resultat

D52-2 ajoute le harness de capture row-specific pour `DrawMiniBitmapRectGM`.

Le run local avec `-Dkanvas.sceneEvidence.write=true` a produit les artefacts
D52-2. La ligne `skia-gm-drawminibitmaprect` reste `expected-unsupported`:
WebGPU produit un bitmap sur `Apple M2 Max`, mais atteint `94.9305%`, sous le
seuil local de promotion `99.95%`.

La raison active reste:
`bitmap.drawminibitmaprect.rotated-fast-src-rect-webgpu-artifacts-required`.

## Mesures locales

| Backend | Statut | Similarite | Seuil | Decision |
|---|---|---:|---:|---|
| CPU | `pass` | `93.9580%` | `40.0%` | Artefact CPU utilisable pour diagnostic. |
| WebGPU | `expected-unsupported` | `94.9305%` | `99.95%` | Pas de support revendique. |

Le paquet contient donc reference, CPU, WebGPU, diffs, routes et stats, mais le
score WebGPU ne permet pas de poser `fallbackReason=none`.

## Ce que le harness produit

Le test `org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest` charge la
reference historique `drawminibitmaprect.png`, rend `DrawMiniBitmapRectGM()` par
la voie CPU `TestUtils.runGmTest`, puis tente le rendu WebGPU via
`WebGpuSink.draw`.

Par defaut, le test ne materialise pas les artefacts. Avec:

```bash
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest
```

il ecrit sous `reports/wgsl-pipeline/scenes/artifacts/d52-drawminibitmaprect/`:

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

D52-2 ne promeut pas la ligne par structure seule.

Le `route-gpu.json` declare `status: expected-unsupported` et garde la raison
stable D51/D52 parce que la similarite WebGPU mesuree reste sous `99.95%`.
`fallbackReason=none` reste interdit pour cette ligne dans l'etat courant.

`m66-bitmap-rect-nearest-skia` ne peut toujours pas etre herite comme preuve de
support: il couvre `skia-gm-drawbitmaprect` en smoke 64x64 strict-nearest, pas
la GM `DrawMiniBitmapRectGM` 1024x1024 avec atlas 2048, rectangles source
croissants, rotations deterministes, sampling par defaut et `kFast`.

## Non-claims

- Aucun gain de score n'est revendique.
- Aucun support WebGPU pour `DrawMiniBitmapRectGM` n'est revendique sans run
  d'artefacts WebGPU passant.
- Aucun support Skia-comparable supplementaire n'est revendique par structure
  seule.
- Aucun changement de `results.json`, `scenes.json`, manifestes D50/D51/D52-1,
  seuil global, scoring global, politique fallback globale, `PipelineKey`, WGSL
  de production, `wgsl4k`, source upstream ou rendu par defaut.
- Les tests CPU historiques `DrawMiniBitmapRectTest` et
  `DrawMiniBitmapRectAaTest` restent des tests de stress desactives.
- Aucun support large image, codec, mipmap, tile-mode, color-managed image,
  YUV, EXIF, animation, bicubic ou image arbitraire n'est revendique.

## Validation attendue

```bash
rtk python3 scripts/validate_d52_drawminibitmaprect_artifact_harness.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d52-drawminibitmaprect-artifact-harness.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-drawminibitmaprect-harness-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_artifact_harness.py
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.DrawMiniBitmapRectSceneCaptureTest
rtk python3 scripts/validate_d52_drawminibitmaprect_artifact_harness.py --require-artifacts
rtk git diff --check
```
