# FOR-276 CPU Mask Filter Clip Order

Linear: `FOR-276`

Scene cible: `m60-bounded-nested-rrect-clip`

Decision: `KEEP_EXPECTED_UNSUPPORTED`

FOR-276 corrige de facon bornee l'ordre CPU du `mask filter` (filtre de
masque) et du `clip` (decoupe) pour les AA clips actifs. Le masque source du
blur est maintenant genere dans les bornes device + marge du filtre pour ce
chemin, sans tronquer au clip final avant filtrage. La composition reste
limitee au clip courant et a l'AA clip actif. Les clips rectangulaires simples
gardent le quick reject existant.

## Mesure Avant/Apres

| Mesure | Avant correction | Apres correction |
|---|---:|---:|
| Support rouge non clippe dans le clip de halo | 10 | 10 |
| Pixels rouges conserves dans le clip de halo | 0 | 8 |
| Pixels rouges perdus dans le clip de halo | 10 | 2 |
| Part recuperee | 0.0% | 80.0% |

## Fixture

| Element | Valeur |
|---|---:|
| Dimensions | 96 x 72 |
| Sigma | 1.366025 |
| Pixels du clip de halo | 48 |
| Clip de halo | `{'left': 14, 'top': 30, 'right': 18, 'bottom': 42}` |

## Sample Signe

| Pixel | Unclipped RGBA | Clip RGBA apres correction | Clip-unclipped |
|---|---|---|---|
| 17,31 | `[255, 202, 202, 255]` | `[255, 232, 232, 255]` | `[0, 30, 30, 0]` |

## Preservation

- M60 reste `expected-unsupported`.
- Fallback `coverage.nested-clip-visual-parity-below-threshold` conserve.
- Fallback `image-filter.crop-input-nonnull-prepass-required` conserve.
- Aucun readback/fallback, support large clip-stack, Ganesh, Graphite ou
  compilateur SkSL ajoute.
- FOR-275 reste stable: saveLayer/direct `0`
  pixel different, pixels rouges perdus sous difference clip
  `1164`.

## Impact Scene M60

| Mesure scene complete | Valeur |
|---|---:|
| CPU/reference similarity | 97.31% |
| CPU matching pixels | 908439 |
| CPU max channel delta | 237 |
| GPU/reference similarity | 98.48% |
| GPU matching pixels | 919363 |
| GPU max channel delta | 57 |
| Seuil promotion | 99.95% |

La fixture bornee recupere le halo local, mais l'evidence scene regeneree
reste sous seuil: CPU/reference `97.31%` et GPU/reference
`98.48%`. La scene n'est donc pas promue.

Le bornage preserve aussi `BlurQuickRejectGM`: le comportement source-mask
non tronque n'est active que lorsqu'un AA clip non rectangulaire est lie au
device.

## Conclusion

Hypothese dominante:
`CPU_MASK_FILTER_SOURCE_CLIP_ORDER_WAS_TRUNCATING_BLUR_SOURCE`.

La correction est locale au chemin CPU `drawPathWithMaskFilter`. Elle ne suffit
pas a promouvoir M60: il faut regenerer les evidences scene et verifier
simultanement CPU/reference et GPU/reference au seuil `99.95`.

## Validation

```text
rtk ./gradlew --no-daemon :kanvas-skia:test --tests org.skia.core.For275CpuSrcInBlurLayerFixtureTest
rtk ./gradlew --no-daemon :skia-integration-tests:test --tests org.skia.tests.Round12Test
rtk python3 scripts/validate_for276_cpu_mask_filter_clip_order.py
rtk python3 scripts/validate_for275_cpu_srcin_blur_layer_fixture.py
rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.NestedClipSceneCaptureTest
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk git diff --check
```

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/cpu-mask-filter-clip-order-for276.json`
