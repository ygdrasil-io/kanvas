# Correctif P1 - perte d'alpha PNG vers formats opaques

## Finding traite

`PngCodec.getPixels()` acceptait une requete construite avec
`codec.getInfo().makeColorType(kRGB_565)` ou `kGray_8`. `makeColorType()`
conserve `kUnpremul`, donc le controle d'`alphaType` passait. L'ecriture dans
`SkBitmap` forcait ensuite l'alpha a `0xFF` et le codec retournait
`kSuccess`, avec une perte silencieuse de l'alpha source.

## Cycle TDD

Deux regressions ont ete ajoutees avec une source RGBA 8-bit dont l'alpha vaut
`0x80`, une pour `kRGB_565` et une pour `kGray_8`. Les deux requetes utilisent
explicitement `codec.getInfo().makeColorType(...)` et exigent le refus type
`Codec.Result.kInvalidConversion`.

Execution RED:

```text
rtk ./gradlew :codec:png:test --tests '*PngCodecTest*makeColorType*'

PngCodecTest > refuses RGBA alpha loss when makeColorType requests Gray8() FAILED
PngCodecTest > refuses RGBA alpha loss when makeColorType requests RGB565() FAILED
PngCodecTest > keeps opaque RGB conversions requested with makeColorType() PASSED
3 tests completed, 2 failed
```

Le controle positif RGB sans `tRNS` a ete ajoute avant le correctif pour
garantir que les conversions opaques valides restent supportees.

## Correctif minimal

Avant le decodage, `getPixels()` refuse `kRGB_565` et `kGray_8` lorsque le PNG
peut fournir de l'alpha:

- canal alpha explicite (`grayscale+alpha` ou `RGBA`);
- `tRNS` sur une source grayscale ou RGB;
- palette contenant au moins une entree non opaque.

Les sources RGB/grayscale sans `tRNS` et les palettes entierement opaques
conservent leurs conversions existantes.

## Verification

Execution GREEN ciblee:

```text
rtk ./gradlew :codec:png:test --tests '*PngCodecTest*makeColorType*'
BUILD SUCCESSFUL
3 tests passed
```

Suite PNG complete:

```text
rtk ./gradlew :codec:png:test
BUILD SUCCESSFUL
```

Controle du diff:

```text
rtk git diff --check
```

Resultat: code 0, aucune erreur de whitespace.

Des changements concurrents sont presents dans `SUPPORTED_CODECS.md`,
`codec/api/*` et `PngEncoder*`. Ils ont ete laisses intacts et sont exclus du
commit de ce correctif.
