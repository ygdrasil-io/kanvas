# Fixture JPEG XL Modular lossless

Ces fixtures documentent le premier profil JPEG XL dont le décodeur Kotlin doit
restituer les pixels, sans dépendance native au runtime : un codestream brut
`FF 0A`, son enveloppe ISO-BMFF exacte `JXL `/`ftyp`/`jxlc`, ou des fragments
`jxlp` version 0 strictement ordonnés, image fixe grayscale ou RGB sRGB/D65
8-bit, lossless, Modular, sans alpha, palette, transform, animation ni
métadonnée de conteneur.
Le cas `jxlp` est une enveloppe synthétique créée dans le test à partir du petit
codestream raw ci-dessous : ce n’est pas une fixture distincte, ni une promesse
de prise en charge de `jxlp` version 1 ou hors ordre.

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
| `rgb-direct-4x3-8bit-source.png.base64` | `b98eaa8020820be4f64a6fd5f679aa48deabef384090c8aca3bb07c877f54879` (PNG décodé) | Source compacte RGB sRGB 4×3 dont les pixels ARGB sont explicitement vérifiés dans le test Kotlin |
| `rgb-direct-4x3-8bit-lossless.jxl.base64` | `49b779382b5bd1402aa1a1b928fe1d205fced3032176964e3d2bfbf52e8af033` (codestream décodé) | Encodeur libjxl v0.13.0 `-d 0 -m 1`, Modular RGB direct sans transform; même flux vérifié raw, `jxlc` et `jxlp` |
| `rgb-direct-4x3-all-default-color.jxl.base64` | `352a29dbb0718733fabdfb6251099c41049a3c38fad72b1850c51d280bd7b730` (codestream décodé) | Même image RGB avec `ColorEncoding.all_default`, donc profil RGB sRGB/D65 implicite |
| `rgb-direct-510x532-multigroup.jxl.base64` | `ccd4464ec90aa113fea4627bda792ebb3a4e870af2f26e7d6b21ef531fa9f3ae` (codestream décodé) | Même palette RGB sRGB/D65 répartie par libjxl sur six groupes TOC, avec échantillons aux frontières de groupe vérifiés contre l’oracle `djxl` |

Pour rejouer l’oracle explicitement :

```text
rtk ./gradlew :codec:jpegxl:test \
  --tests '*JpegXlModularDecodeTest.opt in djxl oracle retains pinned jxlc modular lossless pixels exactly' \
  -PjpegxlOracleDjxl=/absolute/path/to/djxl --no-daemon
```

Sans cette propriété, le test est ignoré : l’oracle ne fait jamais partie de
la CI portable ou des dépendances de production.
