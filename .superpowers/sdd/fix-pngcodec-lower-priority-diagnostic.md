# Correctif P1 PNG codec: diagnostic de priorité inférieure

## Résultat

`PngCodec.open()` détermine maintenant le signal couleur actif après le parsing
typé, selon la precedence `cICP > iCCP > sRGB > cHRM+gAMA`. Un diagnostic
structurel n'est fatal que s'il concerne ce signal actif ou `tRNS`, dont la
validité est nécessaire au décodage raster.

Les diagnostics structurels des signaux couleur de priorité inférieure restent
attachés à `PngCodec.diagnostics`. Un `iCCP` structurellement refusé n'est plus
exposé comme provenance ICC, même lorsqu'un `cICP` valide fournit le profil
résolu.

## Preuve TDD

Régression ajoutée dans `PngCodecTest` pour deux formes de refus structurel du
`iCCP` inférieur: `png.metadata.iCCP.duplicate` et
`png.metadata.iCCP.order`. Chaque cas exige une ouverture réussie, le profil HDR
du `cICP` actif, aucune provenance ICC et la conservation du diagnostic.

RED, avant modification de `PngCodec.kt`:

```text
PngCodecTest > cICP remains active when lower priority iCCP is structurally refused() FAILED
org.opentest4j.AssertionFailedError at PngCodecTest.kt:1032
1 test completed, 1 failed
```

GREEN ciblé, après implémentation:

```text
PngCodecTest > cICP remains active when lower priority iCCP is structurally refused() PASSED
BUILD SUCCESSFUL
```

## Vérification

Commande de suite:

```text
./gradlew :codec:png:test
```

Résultat: 146 tests, 0 failure, 0 error, `BUILD SUCCESSFUL`.

`git diff --check` termine avec le code de sortie 0 et sans sortie. Le correctif
est limité à `PngCodec.kt`, `PngCodecTest.kt` et au présent rapport; les
modifications préexistantes sous `codec/api` ne font pas partie du commit.
