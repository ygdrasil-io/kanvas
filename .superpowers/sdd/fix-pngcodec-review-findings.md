# Correctifs PngCodec - findings 1, 2, 3, 4, 5, 6, 7, 12 et 13

## Périmètre

- `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngCodec.kt`
- `codec/png/src/test/kotlin/org/graphiks/kanvas/codec/png/PngCodecTest.kt`
- `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/Codec.kt` (KDoc de provenance et contrat `getPixels`)
- `SUPPORTED_CODECS.md`

Les modifications concurrentes dans `PngEncoder.kt`, `PngEncoderTest.kt` et
`KanvasCodecColorSpaceTest.kt` ont été laissées intactes.

## Cycles TDD

Les régressions ont été écrites avant les modifications de production. La
première exécution de `PngCodecTest` a échoué sur les cas ajoutés ou inversés:

- dimensions `100000 x 30000` et allocation de bitmap;
- fin zlib absente après les scanlines utiles;
- priorité `cICP` sur `iCCP`;
- `cICP` narrow-range;
- provenance `getICCProfile()` pour `cICP`, `sRGB` et `gAMA`;
- `gAMA` seul et résolution `cHRM+gAMA`;
- métadonnées `Refused` (`iCCP`, `gAMA`, `cHRM`, `sRGB`);
- requêtes `getPixels` de scale, color space, destination divergente et alpha;
- index palette hors `PLTE`;
- conversions de stockage 8-bit/F16 qui impliqueraient une conversion alpha.

La régression F16 supplémentaire a aussi été exécutée seule en rouge, puis en
vert, avant de retirer cette conversion de `canDecodeTo`.

## Comportement livré

1. `decodeHeader` calcule les pixels et les allocations de bitmap en `Long`
   avant d'ouvrir le codec. Les sorties 8-bit/F16 dépassant le budget de
   tableau JVM sont refusées.
2. `inflate` exige `Inflater.finished()`, distingue l'entrée zlib incomplète
   (`kIncompleteInput`) des datastreams invalides, et refuse les octets après
   la fin zlib.
3. La résolution couleur consomme seulement `PngMetadataValue.Resolved`, dans
   l'ordre `cICP > iCCP > sRGB > cHRM+gAMA`. Les valeurs `Refused` restent des
   diagnostics et ne sont jamais appliquées.
4. Le `cICP` RGB narrow-range devient
   `png.cicp.narrow-range.unsupported`; les samples sont préservés sans range
   transform. Les codes H.273 SDR 1, 14 et 15 gardent la résolution existante,
   sans nouvelle distinction.
5. `gAMA` seul ne fabrique plus un profil sRGB. Un couple `cHRM+gAMA` valide
   dérive une matrice D50 adaptée et une TRC gamma; une paire non représentable
   devient explicitement unsupported.
6. Le profil utilisé par `getInfo()` est séparé du profil ICC embarqué.
   `getICCProfile()` ne retourne qu'un `iCCP` valide effectivement présent.
7. `getPixels` refuse les demandes de scale, d'alpha ou de color-space
   transform non implémentées et les divergences de color space entre `info`
   et destination.
8. Un index indexed hors palette est écrit localement comme opaque black.

## Vérification

Commande finale exécutée avec succès:

```text
rtk ./gradlew :codec:png:test :color-management:test :kanvas:test \
  checkPureKotlinCodecNoAwt \
  checkProductionCodecImageClasspathNoJavaDesktop \
  checkPureKotlinPngEncoderNoAwt \
  checkSupportedCodecsDoc --no-daemon
```

Résultat: `BUILD SUCCESSFUL` (59 tâches actionnables; 8 exécutées, 1 depuis le
cache, 50 à jour).

`rtk git diff --check` est également vert.
