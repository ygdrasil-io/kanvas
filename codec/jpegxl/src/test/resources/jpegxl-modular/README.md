# Fixture JPEG XL Modular lossless

Ces fixtures documentent le premier profil JPEG XL dont le décodeur Kotlin doit
restituer les pixels, sans dépendance native au runtime : un codestream brut
`FF 0A` ou son enveloppe ISO-BMFF exacte `JXL `/`ftyp`/`jxlc`, image fixe
grayscale 8-bit, lossless, Modular, sans alpha, palette, transform, animation
ni métadonnée de conteneur.

## Provenance reproductible

- Source : `libjxl/testdata/jxl/flower/flower_small.g.depth8.pgm`, révision
  testdata `73695d303670c90e4d506ea89d9901b081385089`, licence CC BY 4.0.
- Outil : libjxl `196a43d996aa6ed33ebf98812a7c6d43b2b6d01b` (v0.13.0).
- Générateur et oracle local : `cjxl` / `djxl`, employés uniquement dans le
  test opt-in, jamais par le runtime Kotlin.

```text
cjxl flower_small.g.depth8.pgm flower-510x532-8bit-lossless.jxl \
  -d 0 -e 2 -m 1 --modular_colorspace=0 --iterations=100 \
  --modular_palette_colors=0 --pre-compact=0 --post-compact=0 \
  --container=0 --num_threads=0 -v
cjxl flower_small.g.depth8.pgm flower-510x532-8bit-lossless-jxlc.jxl \
  -d 0 -e 2 -m 1 --modular_colorspace=0 --iterations=100 \
  --modular_palette_colors=0 --pre-compact=0 --post-compact=0 \
  --container=1 --num_threads=0 -v
djxl flower-510x532-8bit-lossless-jxlc.jxl oracle.pgm --num_threads=0 -v
```

`djxl` produit le même PGM binaire `P5`, 510×532, maxval 255, que la source.

| Fichier | SHA-256 | Rôle |
| --- | --- | --- |
| `flower-510x532-8bit-lossless.jxl` | `c68282d6f7644cdf3485010a566c18b5ded40c3c25dcce59fe3672eeade06aa9` | Codestream brut Modular lossless 120691 octets |
| `flower-510x532-8bit-lossless-jxlc.jxl.base64` | `ee9348318a009ffbae25ba279db37c64bc4a2c729b9a276ad0743c23a8f30218` (codestream décodé) | Enveloppe `JXL `/`ftyp`/`jxlc` Modular lossless 120731 octets |
| `flower-510x532-8bit-lossless.pgm` | `4580f75490c0bc38159a381615571e2a341fc0adde99b4b3b0ed5bbea97da1fc` | Source et oracle pixels P5 510×532 |
| `single-group-4x3-8bit-lossless.jxl.base64` | `b01d8f59c10376d91f06d2df8c20e04e34f8684282a7a2f8659f1f6fcc6e97c7` (codestream décodé) | Régression raw Modular 4×3, mono-groupe et section TOC unique; pixels `67 65 64 63 / 62 63 64 65 / 61 63 65 65` |

Pour rejouer l’oracle explicitement :

```text
rtk ./gradlew :codec:jpegxl:test \
  --tests '*JpegXlModularDecodeTest.opt in djxl oracle retains pinned jxlc modular lossless pixels exactly' \
  -PjpegxlOracleDjxl=/absolute/path/to/djxl --no-daemon
```

Sans cette propriété, le test est ignoré : l’oracle ne fait jamais partie de
la CI portable ou des dépendances de production.
