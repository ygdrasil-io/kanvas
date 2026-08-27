# Bitmap formats GM evidence — 2026-08-27

## Périmètre

La préparation GPU normalise explicitement `RGB_565`, `ARGB_4444`,
`RGBA_F16` et `GRAY_8` en RGBA8 SDR borné. La conversion préserve les
dimensions, le `sourceRowBytes`, le budget d'upload, le hash de contenu et
la clé d'artefact. Elle ne passe ni par un codec implicite, ni par SkSL, et le
runtime reste WebGPU headless/offscreen.

`ARGB_4444` et `RGBA_F16` sont décodés depuis leur représentation prémultipliée
vers RGBA8 straight; `RGB_565` et `GRAY_8` restent opaques. Un format hors
contrat (`RGB_888X`) continue de produire le diagnostic stable
`unsupported.image.pixel.format`.

## Artefacts

Chaque ligne dispose de la référence CPU/Skia, de la sortie WebGPU native et
du diff généré avec `ComparisonUtils` :

| GM | Référence | GPU | Diff |
| --- | --- | --- | --- |
| `all_bitmap_configs` | `reference/all_bitmap_configs.png` | `generated/all_bitmap_configs.png` | `diff/all_bitmap_configs.png` |
| `copyTo4444` | `reference/copyTo4444.png` | `generated/copyTo4444.png` | `diff/copyTo4444.png` |
| `format4444` | `reference/format4444.png` | `generated/format4444.png` | `diff/format4444.png` |
| `mipmap_gray8_srgb` | `reference/mipmap_gray8_srgb.png` | `generated/mipmap_gray8_srgb.png` | `diff/mipmap_gray8_srgb.png` |

Les statistiques de comparaison des PNG sont dans `stats.tsv`. Les scores de
runner natif sont enregistrés dans
`integration-tests/skia/test-similarity-scores.properties`; ils ont été mis à
jour sans modifier les seuils.

## Exécution native et diagnostics

Les quatre GMs ont été exécutés avec `SkiaGmRunner` et `DebugLevel.PIXEL` :

| GM | Similarité runner | Dispatch | Refus |
| --- | ---: | ---: | ---: |
| `all_bitmap_configs` | 62.452189127604164% | 1662 | 0 |
| `copyTo4444` | 57.373456790123456% | 3 | 0 |
| `format4444` | 100.0% | 5 | 0 |
| `mipmap_gray8_srgb` | 88.63377926421406% | 10 | 0 |

`diagnostics.tsv` conserve le refus négatif hors contrat et les résultats
nativement rendus. Les similarités sont de l'évidence, pas une promotion de
seuil : les valeurs de seuil existantes restent inchangées.

## Reproduction

```sh
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=all_bitmap_configs
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=copyTo4444
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=format4444
./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name=mipmap_gray8_srgb
```
