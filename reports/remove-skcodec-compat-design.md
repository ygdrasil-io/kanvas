# Retirer `SkCodecCompat` au profit des types Kanvas — Design

**Statut :** design validé pour préparation du plan d’implémentation.

## Objectif

Supprimer `kanvas/src/main/kotlin/org/skia/foundation/SkCodecCompat.kt` et
toute dépendance de production au package `org.skia.foundation` dans les
codecs. Les API codec doivent exposer uniquement des types Kanvas, sans alias
ni façade de compatibilité `Sk*`.

## Constat et contraintes

`SkCodecCompat.kt` mélange aujourd’hui six responsabilités : description de
pixels, bitmap possédé, vue de pixels avec stride, image snapshot, conteneur
d’octets et noms de formats. Une substitution textuelle par les classes
Kanvas existantes n’est pas sûre : `SkImageInfo` et `SkBitmap` préservent un
`ImageColorSpace` avec provenance ICC, alors que les classes image actuelles
ne portent que le `ColorSpace` nommé du renderer. `SkPixmap` est également une
vue `ByteBuffer` avec `rowBytes`, ce que `Bitmap` ne représente pas.

Le changement est une rupture d’API intentionnelle. Il ne crée aucune
dépréciation, typealias ou overload `Sk*`. Les références des répertoires
`codec/android`, `codec/animated`, `codec/image-generator` et
`codec/extended` sont aussi migrées, même lorsqu’elles ne sont pas incluses
par le build Gradle courant.

Le chantier ne crée pas de nouvelle suite de tests de régression : il adapte
les tests codec existants et conserve leurs vecteurs, conformément à la
décision de périmètre.

## Architecture cible

### Types canoniques

Les types restent dans `org.graphiks.kanvas.image` et deviennent la seule
source de vérité pour les codecs.

```kotlin
data class ImageInfo(
    val width: Int,
    val height: Int,
    val colorType: ColorType,
    val alphaType: AlphaType,
    val colorSpace: ImageColorSpace,
)

class Bitmap(val info: ImageInfo)

class Pixmap(
    val info: ImageInfo,
    data: ByteBuffer,
    val rowBytes: Int,
)
```

`Bitmap` garde des constructeurs de convenance en termes de largeur, hauteur,
format, alpha et espace couleur, mais les délègue à `ImageInfo`. Il stocke les
octets dans le layout de `ColorType`, ne comporte plus de tableau ARGB public
spécifique à Skia et expose des primitives explicites (`getArgb`, `setArgb`,
accès F16 lorsque le format le permet). Les codecs et leurs tests passent par
ces primitives ou par des helpers de fixtures, jamais par une hypothèse de
layout `IntArray`.

`Pixmap` est une vue non possédée : il duplique le `ByteBuffer`, vérifie
`rowBytes >= info.minRowBytes()` pour une image non vide, et ne possède pas
d’opération mutable `reset`. Il remplace les overloads PNG et JPEG basés sur
`SkPixmap`.

`ByteArray` remplace `SkData`. Toute entrée qui doit survivre à son appelant
fait une copie défensive à sa frontière d’ownership ; les factories ne
promettent donc pas une fausse immutabilité.

### Profils couleur et renderer

`ImageInfo` et `Bitmap` portent `ImageColorSpace` afin de garder le profil
ICC, l’état supporté/non supporté et le code de refus. La projection vers
`ColorSpace`, qui est le sous-ensemble nommé consommé par le renderer, est une
frontière explicite :

* profil classifiable : conversion vers le `ColorSpace` nommé correspondant ;
* profil non classifiable : refus déterministe, sans conversion silencieuse
  vers sRGB.

`Image` reste la représentation rendable. Une conversion `Bitmap` vers
`Image` ne s’effectue que par cette frontière, ce qui évite de faire croire au
renderer qu’un profil ICC inconnu est sRGB.

### Catalogue `ColorType`

`ColorType` contient les 28 entrées du catalogue compatible Skia. `UNKNOWN`
est un sentinelle de métadonnées vide, jamais un format décodable ou
encodable. Les 27 autres entrées sont toutes des objectifs de support à terme.

| Horizon | Entrées |
|---|---|
| P0 — migration et support codec existant | `ALPHA_8`, `RGB_565`, `ARGB_4444`, `RGBA_8888`, `BGRA_8888`, `GRAY_8`, `RGBA_F16`, `RGBA_F16_NORM` |
| P1 — 8 bits et composants | `RGB_888X`, `SRGBA_8888`, `R8_UNORM`, `R8G8_UNORM` |
| P2 — packed 10 bits | `RGBA_1010102`, `BGRA_1010102`, `RGB_101010X`, `BGR_101010X`, `BGR_101010X_XR`, `BGRA_10101010_XR`, `RGBA_10X6` |
| P3 — float et 16 bits | `RGB_F16F16F16X`, `RGBA_F32`, `A16_FLOAT`, `R16G16_FLOAT`, `A16_UNORM`, `R16_UNORM`, `R16G16_UNORM`, `R16G16B16A16_UNORM` |
| Sentinelle | `UNKNOWN` |

`RGBA_F16` et `RGBA_F16_NORM` restent distincts. Leur plage et leur contrat
de normalisation ne sont pas masqués par une conversion vers un format F16
unique.

Un registre central associe chaque format à ses capacités : allocation,
lecture/écriture CPU, décodage, encodage et route GPU. L’enum peut donc
énumérer tout le catalogue dès P0, tout en gardant les routes P1–P3 non
livrées explicitement indisponibles. Pour toute capacité absente :

* `Codec.getPixels` retourne `Codec.Result.kInvalidConversion` ;
* un encodeur retourne son refus documenté sans produire d’octets ;
* les opérations bitmap/pixmap qui exigent une interprétation de pixels
  refusent avec le diagnostic Kanvas de format non supporté ;
* aucune route ne convertit implicitement vers `RGBA_8888`.

Chaque format passe de P1/P2/P3 à « supporté » seulement lorsque ses cinq
capacités sont renseignées pour les routes réellement promises. Le support
codec, CPU et GPU reste donc mesurable séparément.

## Migration des API et du flux de données

Le flux cible est :

```text
ByteArray encodé → Codec → ImageInfo + Bitmap/Pixmap → Image rendable
```

1. `Codec.getInfo()` retourne `ImageInfo` et `Codec.getPixels()` prend un
   `ImageInfo` et un `Bitmap` Kanvas. `Codec.getImage()` retourne le même
   bitmap possédé en cas de succès.
2. Les codecs BMP, GIF, JPEG, JPEG-LS, JPEG 2000, JPEG XL, PNG, WBMP et WebP
   créent et copient des `Bitmap` Kanvas. Les comparaisons de destination
   vérifient géométrie, format, alpha et identité de `ImageColorSpace`.
3. Les encodeurs JPEG et PNG remplacent leurs overloads `SkPixmap` par
   `Pixmap`. Les encodeurs qui reçoivent actuellement `SkImage` acceptent un
   `Bitmap` ou un `Image` Kanvas selon leur besoin, avec la même frontière de
   profil explicite.
4. `org.skia.utils.PixmapUtils` est déplacé sous un package Kanvas codec ou
   image et ses signatures utilisent `Bitmap`, `ImageInfo` et
   `EncodedOrigin`.
5. `KanvasCodec.kt` est supprimé : ses conversions Skia-vers-Kanvas ne sont
   plus nécessaires. `CodecImageDecoder` reçoit directement le bitmap et son
   espace couleur Kanvas.

## Découpage d’implémentation

1. Introduire le catalogue complet, le registre de capacités, `ImageInfo`
   enrichi, `Bitmap` fondé sur `ImageInfo` et `Pixmap`. Livrer P0 uniquement.
2. Changer les signatures publiques de `Codec` et migrer les codecs de
   décodage P0.
3. Migrer encodeurs, orientation/pixmap utilities, WebP et les conversions
   vers l’image rendable.
4. Nettoyer les modules non inclus, fixtures et intégrations Skia afin qu’ils
   ne dépendent plus du package supprimé.
5. Supprimer `SkCodecCompat.kt`, `KanvasCodec.kt` et tous les imports
   `org.skia.foundation` liés au codec.

Chaque étape est committée séparément. Les ajouts P1, P2 et P3 sont des
travaux ultérieurs : ils activent une capacité existante plutôt que de changer
à nouveau l’API.

## Validation et critères d’acceptation

* Les tâches Gradle codec déclarées passent : api, bmp, common, core,
  extended, gif, ico, jpeg, jpeg-ls, jpeg2000, jpegxl, png, test-fixtures,
  wbmp et webp.
* Les tests existants de formats, ICC, F16, orientation et encodeurs sont
  adaptés aux primitives Kanvas et passent sans changer leurs fixtures.
* Une recherche source ne trouve plus `SkCodecCompat`, `SkBitmap`,
  `SkImageInfo`, `SkPixmap`, `SkData` ni `SkColorType` dans la production
  codec et ses intégrations.
* `git diff --check` est propre.
* Les échecs GPU/package-boundary déjà observés dans `./gradlew test` restent
  rapportés séparément et ne sont pas attribués à cette migration codec sans
  preuve causale.

## Hors périmètre

* Ajouter les implémentations P1, P2 ou P3 dans ce changement initial.
* Introduire un compilateur SkSL, Ganesh ou Graphite.
* Ajouter une compatibilité source ou binaire pour les anciens types `Sk*`.
* Ajouter une nouvelle suite de tests de régression distincte.
