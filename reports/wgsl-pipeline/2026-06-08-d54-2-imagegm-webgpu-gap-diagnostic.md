# D54-2 - ImageGM WebGPU gap diagnostic

Date: 2026-06-08
Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d54-2-diagnostiquer-lecart-web-gpu-image-gm-apres-artefacts-row-specific`
D54-1 source finding: `global/kanvas/findings/d54-1-skia-gm-image-artifact-harness`
Evidence JSON: `reports/wgsl-pipeline/scenes/generated/d54-imagegm-webgpu-gap-diagnostic.json`
Region diagnostic: `reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap/region-diagnostic.json`

## Resultat

D54-2 ajoute un diagnostic de l'ecart WebGPU restant pour `ImageGM` /
`skia-gm-image`, sans modifier le rendu de production et sans promouvoir la
ligne du dashboard.

Le run local avec `-Dkanvas.imageGmGapDiagnostic.write=true` confirme:

| Backend | Statut | Similarite | Seuil | Decision |
|---|---|---:|---:|---|
| CPU | `pass` | `98.4826%` | `98.0%` | Reference row-specific maintenue. |
| WebGPU | `expected-unsupported` | `98.5962%` | `99.95%` | Pas de support revendique. |

La raison active reste:
`image.imagegm.surface-snapshot-drawimage-webgpu-artifacts-required`.

## Diagnostic

Le signal principal vient des comparaisons internes `SkBitmap`, pas des PNG de
visualisation. Les PNG sont utiles pour inspecter les formes, mais ils passent
par `SkBitmap.getPixelAsSrgb`; la decision de promotion reste donc basee sur les
metriques internes du harnais.

Le residu WebGPU n'est pas concentre dans la colonne GPU volontairement vide de
la GM. Il est distribue dans les 14 cellules raster snapshot:

| Region | Pixels differents | Similarite | Delta max |
|---|---:|---:|---:|
| `pre-alloc/full-crop` | `1046` | `93.9968%` | `152` |
| `new-alloc/full-crop` | `1046` | `93.9968%` | `152` |
| `pre-alloc/original-img` | `1004` | `94.2378%` | `69` |
| `new-alloc/original-img` | `1004` | `94.2378%` | `69` |
| `pre-alloc/upper-left` | `1002` | `94.2493%` | `195` |
| `new-alloc/upper-left` | `1002` | `94.2493%` | `195` |
| `pre-alloc/no-crop` | `968` | `94.4444%` | `181` |
| `new-alloc/no-crop` | `968` | `94.4444%` | `181` |
| `pre-alloc/modified-img` | `920` | `94.7199%` | `82` |
| `new-alloc/modified-img` | `920` | `94.7199%` | `82` |
| `pre-alloc/cur-surface` | `852` | `95.1102%` | `81` |
| `new-alloc/cur-surface` | `852` | `95.1102%` | `81` |
| `pre-alloc/over-crop` | `443` | `97.4575%` | `84` |
| `new-alloc/over-crop` | `443` | `97.4575%` | `82` |

Les points echantillonnes au centre des cellules montrent une parite CPU/WebGPU
sur ces points centraux. L'ecart restant est donc plus probablement lie aux
bords, au chemin d'upload/echantillonnage texture ou a la reconstruction des
pixels snapshot qu'a une erreur uniforme de couleur pleine.

## Correction

Aucune correction locale sure n'est identifiee par D54-2. Le ticket ne change
pas les chemins `WebGpuSink`, `SkWebGpuDevice`, `PipelineKey`, WGSL de
production, seuils, dashboards ou politiques fallback.

La prochaine action utile est un diagnostic plus etroit de l'upload et de
l'echantillonnage des images snapshot avant toute modification de rendu.

## Non-claims

- Aucun support WebGPU ImageGM n'est revendique.
- Aucun gain de score n'est revendique.
- Aucun changement de `results.json`, `scenes.json`, dashboard global, seuil
  global, scoring global ou politique fallback globale.
- Aucun changement de `PipelineKey`, WGSL de production, route de rendu par
  defaut, `wgsl4k` ou source upstream.
- Aucun support codec, YUV, animation, EXIF, mipmap, tile-mode,
  color-managed image decode, image decode arbitraire ou image large n'est
  revendique.
- Aucune preuve voisine n'est heritee depuis `bitmappremul`,
  `drawbitmaprect`, `drawminibitmaprect`, `ImageSource`, `MakeRasterImageGM`,
  `BitmapImageGM` ou une autre scene image.

## Validation attendue

```bash
rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.imageGmGapDiagnostic.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.ImageGmSceneCaptureTest
rtk python3 scripts/validate_d54_imagegm_webgpu_gap_diagnostic.py
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d54-imagegm-webgpu-gap-diagnostic.json
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/d54-2-imagegm-gap/region-diagnostic.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d54-2-imagegm-pycache python3 -m py_compile scripts/validate_d54_imagegm_webgpu_gap_diagnostic.py
rtk git diff --check
```
