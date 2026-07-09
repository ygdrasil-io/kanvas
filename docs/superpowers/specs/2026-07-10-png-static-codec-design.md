# Design: codec PNG statique professionnel

## Objectif

Faire evoluer le codec PNG portable Kotlin vers un encodeur/decodeur statique
de niveau outil graphique. Le resultat doit produire des pixels colores
correctement, encoder tous les types de pixels PNG statiques valides, proteger
les ressources de decode, et permettre a un outil graphique de reouvrir puis
reenregistrer un document sans perdre les metadonnees ou extensions qu'il ne
comprend pas.

Le perimetre cible la recommandation W3C PNG Third Edition pour les images
statiques. APNG est explicitement hors perimetre de cette livraison : sa
presence est detectee et refusee plutot que de decoder seulement son image par
defaut puis de risquer une reedition destructive.

## Contexte et contraintes

Le decodeur actuel couvre deja les cinq types couleur, les profondeurs 1 a 16
bits applicables, `tRNS`, Adam7 et une partie des chunks couleur. L'encodeur
est limite a RGBA 8 bits et le decodeur concatene IDAT et les lignes
decompressees en memoire.

Le correctif GM du 9 juillet 2026 contourne aussi une limite de codec : il
decode puis convertit manuellement les PNG vers sRGB dans
`ComparisonUtils`. Le contrat cible de `PngCodec.getPixels()` est que le
`SkImageInfo.colorSpace` de destination demande cette conversion directement.
Les GMs doivent seulement demander explicitement sRGB.

Contraintes non negociables :

- runtime de production portable, sans backend AWT, ImageIO ou JNI ;
- pas de conversion silencieuse d'un profil, HDR ou format de pixels non
  supporte ;
- pas de substitut temporaire pour le color-management professionnel ;
- les chunks inconnus suivent les regles W3C `safe-to-copy` ;
- les nouveaux claims sont accompagnes de tests et de mises a jour de
  `SUPPORTED_CODECS.md`.

## Choix retenu

L'architecture retenue separe trois responsabilites :

```text
PngDocument (fidelite de fichier et metadonnees)
        |
        +--> PngCodec (parse, decode/encode raster, conversion couleur)
        |           |
        |           +--> :color-management (ICC, CICP, HDR, tone mapping)
        |
        +--> PngEncoder (plan d'ecriture et emission streaming)
```

- `PngCodec` reste le chemin simple de conversion d'image. Il decode des
  pixels vers le format et l'espace couleur demandes ; il ne promet pas de
  conserver le flux d'origine.
- `PngDocument` est le chemin des outils graphiques. Il conserve la provenance
  des chunks, les metadonnees semantiques et les octets source quand l'appelant
  ouvre un document PNG.
- `:color-management` est une dependance fonctionnelle obligatoire pour les
  conversions ICC/HDR. Ce n'est pas une couche de convenance locale au codec.

Une abstraction de document encode generique pour JPEG, WebP et les autres
formats n'est pas creee dans cette livraison. Elle serait plus large que PNG
et masquerait les regles propres a chaque container.

## API et responsabilites

### `PngCodec`

`PngCodec.getPixels(info, dst)` accepte une destination dont le `colorSpace`
differe de celui de la source. Il prepare une transformation une seule fois
pour le decode en cours, puis transforme chaque pixel avant son ecriture dans
`dst`.

Le pipeline de pixel est :

```text
echantillons PNG non premultiplies
  -> precision source 1/2/4/8/16 bits
  -> unpremultiply si necessaire
  -> transfer function source vers lineaire
  -> gamut/PCS source vers destination
  -> tone mapping si HDR vers SDR
  -> transfer function destination
  -> premultiply selon alphaType destination
  -> format destination 8 bits, U16 ou F16
```

Une destination dans l'espace source est une voie sans transformation. Une
destination sRGB est le contrat du comparateur GM. La conversion ne doit pas
etre reimplementee dans les tests, `CodecImageDecoder`, ou un appelant.

L'API `Codec` conserve son retour `Codec.Result` pour les appels simples. Les
API `PngDocument` exposent les diagnostics detailles necessaires aux outils
graphiques.

### `PngDocument`

`PngDocument.open(bytes, options)` est le point d'entree d'un outil graphique.
Son option de conservation est activee par defaut. Un convertisseur qui veut
seulement des pixels continue a utiliser `Codec.MakeFromData()`.

Le document contient :

- les octets source et un etat `pristine` ;
- `PngColorMetadata` : `iCCP`, `sRGB`, `gAMA`, `cHRM`, `sBIT`, `cICP`, `mDCV`
  et `cLLI` parses et les payloads originaux associes ;
- les metadonnees standard : `tRNS`, `tEXt`, `zTXt`, `iTXt`, `bKGD`, `hIST`,
  `pHYs`, `sPLT`, `eXIf` et `tIME` ;
- les chunks auxiliaires non reconnus, representes par type, payload, ordre,
  ancre entre chunks critiques, et bit `safe-to-copy` ;
- un journal d'impact : aucun changement, changement auxiliaire, ou changement
  de pixels/chunks critiques.

`PngDocument.save(options)` retourne les octets source a l'identique quand le
document est `pristine`. Des qu'une modification existe, il construit un
`PngWritePlan` et delegue l'emission a `PngEncoder`.

Les regles de conservation sont :

- changement auxiliaire seulement : tous les chunks inconnus sont conserves ;
- pixels, palette, profondeur, dimensions ou espace couleur modifies : les
  chunks inconnus `safe-to-copy` sont conserves et les autres sont retires ;
- chunk connu devenu incompatible : il est regenere depuis les metadonnees
  mises a jour ou supprime avec diagnostic ;
- chunk critique inconnu : ouverture de document refusee, car une reedition
  valide ne peut pas etre garantie.

`PngSaveReport` recense les chunks preserves, regeneres et supprimes. La
suppression n'est jamais cachee a l'appelant.

### `PngEncoder`

Les surcharges actuelles `SkBitmap` et `SkPixmap` restent disponibles. Elles
utilisent par defaut `PngPixelFormat.Auto`, donc ne projettent pas
silencieusement une source F16/U16 vers RGBA 8 bits.

Une source raster typee complete les surcharges bitmap pour representer
exactement les echantillons palette et U16. Les politiques de sortie sont :

- `Auto` : choisit le type PNG et la profondeur les plus compacts qui
  representent tous les pixels sans perte ;
- `PreserveSource` : conserve type, profondeur et palette du document quand
  les nouveaux pixels y sont representables ;
- `Explicit` : impose type couleur, profondeur et palette fournis par l'outil.

Une demande qui reduirait ou quantifierait les pixels echoue avec un diagnostic.
La quantification est un choix d'appelant distinct, jamais un effet de bord de
l'encodeur.

L'encodeur couvre les cinq types couleur PNG, toutes les profondeurs valides,
`PLTE`, `tRNS`, `sBIT`, les metadonnees couleur/HDR coherentes, les cinq
filtres et Adam7. Il valide les exclusivites et priorites entre `iCCP`, `sRGB`,
`gAMA`/`cHRM` et `cICP` avant toute ecriture.

## Color management et HDR

### Resolution de metadata

Le parseur valide taille, ordre et contraintes entre chunks avant de produire
`PngColorMetadata`. Le resolveur applique la priorite de la specification :
`cICP` compris est prioritaire ; sinon le profil ICC, `sRGB`, puis les
informations `gAMA`/`cHRM` definissent l'espace source. Une image sans
information couleur explicite utilise sRGB comme convention documentee.

`mDCV` et `cLLI` sont exposes comme metadata HDR. Ils n'autorisent pas une
modification implicite des pixels.

### Moteur reel obligatoire

Le `SkcmsCompat` actuel ne reconnait qu'un format ICC reduit genere par Kanvas
et ne fournit ni LUT ICC, ni transform bulk, ni PQ/HLG. Il ne peut pas servir
de base au support professionnel annonce.

Le module `:color-management` doit fournir un moteur portable Kotlin ou une
dependance multiplateforme maintenue qui couvre :

- profils ICC RGB/grayscale matriciels et a courbes ;
- profils ICC LUT applicables aux pixels PNG ;
- CICP RGB et les transfer functions PQ/HLG ;
- precision U16/F16, alpha, conversions lineaires et gamut mapping ;
- transformations bulk preparees et reutilisables.

Le codec retourne un refus explicite et un code stable lorsqu'un profil valide
ne peut pas etre transforme par ce moteur. `PngDocument` peut tout de meme
preserver ses payloads. Aucun fallback sRGB ne doit etre declare comme support.
La livraison couleur/HDR reste dependency-gated tant que ce moteur reel n'est
pas disponible.

### Tone mapping

La conversion HDR vers une destination SDR sRGB utilise par defaut un
`ToneMappingPolicy.BT2390(targetPeakNits = 100)`. La policy est explicite dans
les options de decode et configurable par l'appelant. Les GMs utilisent cette
policy par defaut pour produire des goldens deterministes. Une destination HDR
compatible conserve la plage HDR et ne passe pas par ce tone mapper.

## Parsing streaming et limites

Le parseur enregistre des vues/ranges sur les IDAT de la source au lieu de les
concatener. Le decodeur alimente l'inflateur par segment IDAT, conserve deux
lignes de travail, et ecrit les pixels transformes directement dans la
destination. L'entree `ByteArray` reste retenue par le codec actuel, mais les
copies supplementaires du flux compresse et de l'image decompressee complete
disparaissent.

L'encodeur ecrit les scanlines vers le deflater et emet des chunks IDAT bornes
a 64 KiB de payload. Adam7 reutilise ce meme chemin pour chaque passe.

`PngResourceLimits.Default` fixe les bornes suivantes :

- largeur et hauteur maximales : 100 000 chacune ;
- pixels : 67 108 864 ;
- octets de destination estimes : 512 MiB ;
- total IDAT compresse : 512 MiB ;
- chunks auxiliaires individuels : 64 MiB ;
- donnees auxiliaires decompressees cumulees : 64 MiB ;
- nombre total de chunks : 100 000.

Un appelant de bureau peut fournir des limites plus larges. Depasser une limite
retourne `kOutOfMemory` ou un diagnostic `resource-limit`, jamais une allocation
non bornee.

## Validite, erreurs et non-objectifs

Le parseur rejette CRC incorrect, chunk critique inconnu, doublon ou ordre
invalide, valeurs reservees, flux apres IEND, IDAT non contigus, valeur de
metadata malformee, profile compresse invalide et APNG. Les chunks auxiliaires
inconnus ne sont pas une erreur de decode d'image statique ; ils sont ignores
par `PngCodec` et conserves par `PngDocument` lorsque demande.

Les erreurs de donnees externes reviennent sous forme de `Codec.Result` ou de
`PngOpenResult.Failure(PngDiagnostic)`. Un diagnostic contient au minimum un
code stable, le type de chunk, son offset, et la gravite. Les options
d'appelant invalides restent des erreurs de programmation explicites.

Cette livraison ne donne aucune semantique aux extensions privees/enregistrees
non standard. Elles sont conservees selon la policy de copie. APNG, animation,
gainmaps et les formats autres que PNG restent hors perimetre.

## Strategie de validation

### Tests de codec

- tables de tests pour tous les types/depths PNG, filtres, Adam7, `PLTE` et
  `tRNS` ;
- un test de parse et d'ordre pour chaque chunk standard statique ;
- tests de conflits et priorites des metadata couleur ;
- tests U16/F16 et conversion vers les destinations sRGB, Display P3 et
  Rec.2020 ;
- tests PQ/HLG, HDR vers SDR avec BT.2390, et sorties HDR sans tone mapping ;
- tests de streaming avec chaque frontiere IDAT possible ;
- tests de limites, flux tronques, CRC, deflate invalide et bombes de
  decompression.

### Tests de document et d'encodage

- ouverture/sauvegarde `pristine` binaire identique ;
- conservation/suppression selon `safe-to-copy` et journal `PngSaveReport` ;
- reemission de texte Latin-1/UTF-8, Exif, resolution et profiles ICC ;
- encodage/decode de chaque type/depth et echecs sans perte silencieuse ;
- comparaison pixel et metadata apres round-trip.

### Interoperabilite et GMs

Le corpus reel est etendu avec fixtures versionnees et licences tracees de
libpng/pngsuite, ImageMagick, GIMP et Photoshop. Les fixtures couvrent types,
profondeurs, profiles, HDR, metadata et inputs malformes. Des sorties Kanvas
sont decodees par les implementations de reference dans une tache de
verification non-production ; aucune dependance native n'entre dans le runtime
portable.

Les GMs couleur demandent sRGB a `PngCodec`. `ComparisonUtils` perd sa logique
PNG/ICC manuelle et devient un consommateur du contrat codec. Les goldens HDR
vers SDR utilisent BT.2390 et les artifacts de comparaison restent encodes en
sRGB canonique.

Le fuzzing ajoute generation de chunks valides/invalides, fragmentation IDAT,
mutations de CRC/longueur/ordre et assertions de limite de ressources.

## Criteres d'acceptation

- toutes les variantes statiques PNG Third Edition sont decodees ou refusent
  explicitement avec une raison stable ;
- aucun chemin d'encodage ne reduit precision, gamut ou alpha sans choix
  explicite de l'appelant ;
- les conversions de couleur appartiennent au codec et les GMs ne dupliquent
  pas de logique PNG ;
- `PngDocument` reemet les octets source sans modification et applique les
  regles `safe-to-copy` apres edition ;
- les fixtures et tests interop/fuzz prouvent les claims ;
- `checkCodecImageComplete` et les GMs concernes passent sans AWT/ImageIO/JNI
  dans le runtime portable.
