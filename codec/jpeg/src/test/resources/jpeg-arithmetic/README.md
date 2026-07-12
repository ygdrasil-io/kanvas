# Fixtures JPEG arithmetic DCT

Ces fixtures statiques testent exclusivement le décodage JPEG arithmetic DCT
en Kotlin : SOF9 (sequential) et SOF10 (progressive). Les tests ne lancent ni
`cjpeg` ni `djpeg`.

## Générateur et oracle

- `libjpeg-turbo` 3.2.0, tag `3.2.0`, commit
  `c85e6b905bf237038faa936dab160ebfc5da0344`.
- Configuration : `-DWITH_ARITH_ENC=1 -DWITH_ARITH_DEC=1 -DENABLE_SHARED=0`.
- Les fichiers ont été produits par `cjpeg-static`; `djpeg-static -grayscale`
  a servi d'oracle de pixels.

Commandes reproductibles :

```text
cjpeg-static -arithmetic -quality 100 -restart 1B \
  -outfile gradient-sequential-sof9.jpg source/gradient-4x4.pgm
cjpeg-static -arithmetic -quality 100 -progressive -restart 1B \
  -outfile gradient-progressive-sof10.jpg source/gradient-4x4.pgm
cjpeg-static -arithmetic -quality 100 -sample 1x1,1x1,1x1 \
  -outfile color-sequential-sof9.jpg source/color-444-8x8.ppm
cjpeg-static -arithmetic -quality 100 -progressive -sample 1x1,1x1,1x1 \
  -outfile color-progressive-sof10.jpg source/color-444-8x8.ppm
cjpeg-static -arithmetic -precision 12 -quality 100 \
  -outfile gray-12bit-sequential-sof9.jpg source/gray-12bit-4x4.pgm
cjpeg-static -arithmetic -quality 100 \
  -outfile two-mcu-sequential-sof9.jpg source/restart-two-mcu-16x8.pgm
cjpeg-static -arithmetic -quality 100 -progressive \
  -outfile two-mcu-progressive-sof10.jpg source/restart-two-mcu-16x8.pgm
cjpeg-static -arithmetic -quality 100 -restart 1B \
  -outfile restart-sequential-sof9.jpg source/restart-two-mcu-16x8.pgm
cjpeg-static -arithmetic -quality 100 -progressive -restart 1B \
  -outfile restart-progressive-sof10.jpg source/restart-two-mcu-16x8.pgm
cjpeg-static -arithmetic -quality 100 -restart 1B \
  -outfile restart-three-mcu-sequential-sof9.jpg source/restart-three-mcu-24x8.pgm
cjpeg-static -arithmetic -quality 100 -progressive -restart 1B \
  -outfile restart-three-mcu-progressive-sof10.jpg source/restart-three-mcu-24x8.pgm
```

## Intégrité et couverture

| Fichier | SHA-256 | Rôle |
| --- | --- | --- |
| `source/gradient-4x4.pgm` | `c694bcbddcefba83a929d2f706c8a1b165975fe44a2316beda07186fb4cb80d2` | Source et oracle 4×4 |
| `gradient-sequential-sof9.jpg` | `083facc4ba38d6cb25974f9bb24bd166244e60d4e23e03da88ec23fdf4f05928` | SOF9, DAC et DRI déclarée |
| `gradient-progressive-sof10.jpg` | `823ea5c6b644812a427b41c9f15c4ce775a671d77f94734774b3fc89d8bc85af` | SOF10, DAC entre scans |
| `source/color-444-8x8.ppm` | `0b16cde82d7692822243a53bfbfeb3c62930beafae739b866996ad22dac77535` | Source RGB 4:4:4 |
| `color-sequential-sof9.jpg` | `e2cdaf3828e8e3543e60e9bb498873eec2d21204ca5c6cb5456cdd00cf24ecd1` | SOF9, blocs RGB interleaved |
| `color-progressive-sof10.jpg` | `52428c9ccad3afcda76a6cad5b2f602c08dd492ab5f670662e6263515c2c7c72` | SOF10, blocs RGB interleaved |
| `source/gray-12bit-4x4.pgm` | `c34ab5108c0197a5eb0fe6abe2f8bef10ae5ba47ea6ec5228ded226a73a9bff0` | Source grayscale 12-bit |
| `gray-12bit-sequential-sof9.jpg` | `b9805f6d7f24e39ebd96651ea820d9a238a431fb63ddc72869259905a61ee95c` | SOF9 12-bit |
| `source/restart-two-mcu-16x8.pgm` | `4f96318ecd27c6193a2da3cebafc8264dc0340350beb9bd98784ee8055a807b1` | Source 16×8, deux MCU |
| `two-mcu-sequential-sof9.jpg` | `2d92f260592dfcf838b67a399df90c5c54a945231fd8f52414eb8370675cf43f` | SOF9, prédiction DC inter-MCU |
| `two-mcu-progressive-sof10.jpg` | `6053bc14338c343e0f007a23792e6b4651fb8848ffb04bd7d6522adb8ab3e138` | SOF10, progression inter-MCU |
| `restart-sequential-sof9.jpg` | `0514a9d1e553ff5c4a2d17f7f6da7f188750311afd8b13d8a76c383d3899cc4f` | SOF9 avec RST0 réel |
| `restart-progressive-sof10.jpg` | `7b51993501070ab4fe41f30f1bf64a8bdea1f87c65608054a5064596050f2051` | SOF10 avec RST0 réel par scan |
| `source/restart-three-mcu-24x8.pgm` | `ab4cd2cee70058865e73fecd928f64931f0eada42f628aae5a76aa9fc3eba3ac` | Source 24×8, trois MCU |
| `restart-three-mcu-sequential-sof9.jpg` | `13727a034b860085e1895226db44558124e99b4ec24b8c480f83004fe37b7746` | SOF9 avec séquence RST0/RST1 |
| `restart-three-mcu-progressive-sof10.jpg` | `67c04863d217b5dad2175ba5fc3a4d2a4a4f6b76d68b1a5ef2722702a37320ac` | SOF10 avec séquence RST0/RST1 par scan |

L'oracle 4×4 est, en lecture ligne par ligne :
`0, 32, 96, 160, 16, 64, 128, 193, 48, 112, 176, 224, 80, 144, 208, 255`.
L'oracle 16×8 vaut `32` dans les huit premières colonnes et `192` dans les
huit suivantes.
L'oracle 24×8 vaut respectivement `32`, `128` et `192` dans ses trois blocs de
huit colonnes.

## Hors périmètre : SOF11

Il n'y a intentionnellement aucune fixture SOF11 (lossless arithmetic) :
`cjpeg-static -arithmetic -lossless 7,0` de libjpeg-turbo 3.2.0 échoue avec
`Sorry, arithmetic coding is not implemented`. Kanvas refuse donc SOF11 par
le diagnostic stable `jpeg.arithmetic.lossless.unsupported`; cela ne constitue
pas une revendication de support lossless arithmetic.

## Encodeurs SOF9/SOF10 Kotlin

L'encodeur statique Kanvas écrit SOF9 (DCT sequential arithmetic) et SOF10
(DCT progressive arithmetic) pour les images grayscale et YCbCr en précision
8 ou 12-bit. Il n'émet ni DHT ni fallback Huffman : les tables DAC par défaut
sont écrites explicitement (`L=0`, `U=1`, `Kx=5`) et les scans avec DRI
terminent proprement chaque intervalle avant le marqueur RST suivant.

Les tests couvrent les catégories DC `|v| = 2, 3, 4`, des décisions AC,
le byte stuffing `FF 00`, la terminaison QM et des RST réels. SOF10 accepte
un script explicite de scans initiaux seulement (`Ah = Al = 0`) : DC avant AC,
et un seul composant par scan AC. Le coder QM suit alors la grammaire
progressive Annex D (décision EOB et zero-run par coefficient), distincte du
scan AC sequential. Les scans de refinement et SOF11 restent des refus
explicites. Les variantes differential et hiérarchiques restent refusées,
sauf les intersections DHP/EXP documentées ci-dessous.

L'oracle externe reste opt-in et n'est jamais une dépendance runtime ou CI :

```text
rtk ./gradlew :codec:jpeg:test \
  --tests '*JpegAdvancedEncodeTest.opt in djpeg oracle matches generated SOF9 grayscale pixels' \
  --tests '*JpegAdvancedEncodeTest.opt in djpeg oracle matches generated SOF10 grayscale pixels' \
  --tests '*JpegAdvancedEncodeTest.opt in djpeg oracle matches generated SOF10 color pixels' \
  --tests '*JpegAdvancedEncodeTest.opt in djpeg oracle matches generated SOF10 12-bit grayscale pixels' \
  -PjpegOracleDjpeg=/absolute/path/to/djpeg --no-daemon
```

Il analyse le PNM binaire renvoyé par `djpeg`, puis vérifie chaque échantillon
contre la source et, pour les sorties 8-bit, contre le décodeur Kanvas. Les
flux gris 8-bit (SOF9/SOF10) tolèrent au plus 2 niveaux face à la source et 1
niveau face à Kanvas : la compression à `quality = 100` garde une DCT
quantifiée à 1, mais les IDCT indépendantes peuvent arrondir d'un niveau. Le
flux couleur SOF10 est volontairement en 4:4:4 et tolère respectivement 3 et
2 niveaux, ce qui couvre les arrondis YCbCr↔RGB supplémentaires sans masquer
une erreur de composant. Le flux gris SOF10 12-bit demande `djpeg -precision
12`, lit le PGM 16-bit big-endian (`maxval = 4095`) et tolère 32 niveaux : deux
niveaux 8-bit transposés sur l'échelle 0..4095. Sans la propriété
`jpegOracleDjpeg`, ces tests sont ignorés ; l'oracle ne devient donc jamais
une dépendance runtime ou CI.

## Hiérarchies arithmetic SOF13/SOF14

Kanvas encode aussi deux intersections hiérarchiques étroites : grayscale
8-bit S444, deux niveaux, référence demi-résolution puis `EXP=0x11`, avec
base SOF9 et résidu arithmetic SOF13 sequential, ou base SOF10 et résidu
SOF14 progressive (scans DC puis AC initiaux). Les deux flux conservent DAC,
DRI/RST et les métadonnées. Les couleurs, plusieurs niveaux, l'expansion autre
que ×2, le refinement, SOF15 et toute autre variante differential/hierarchy
restent refusés.

La référence ISO/IEC 10918-7 `thorfdbg/libjpeg`, compilée séparément, peut
vérifier ces sorties sans être une dépendance runtime ou CI :

```text
rtk ./gradlew :codec:jpeg:test \
  --tests '*JpegAdvancedEncodeTest.opt in hierarchy reference decodes generated SOF13 pixels' \
  --tests '*JpegAdvancedEncodeTest.opt in hierarchy reference decodes generated SOF14 pixels' \
  -PjpegOracleHierarchy=/absolute/path/to/jpeg --no-daemon
```

Les tests comparent les pixels PNM de la référence à la source avec une erreur
maximale d'un niveau; sans `jpegOracleHierarchy`, ils sont ignorés.
