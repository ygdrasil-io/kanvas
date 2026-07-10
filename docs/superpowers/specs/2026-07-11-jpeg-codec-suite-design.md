# Suite de codecs JPEG statiques — conception

## Décision

Kanvas fournira une suite portable, pure Kotlin, d'encodeurs et décodeurs pour
les quatre standards d'image fixe suivants : JPEG classique (ISO/IEC 10918-1),
JPEG-LS, JPEG 2000 et JPEG XL. Aucun chemin de production ne dépendra de JNI,
AWT, ImageIO, `java.desktop` ou d'un codec système.

Les bibliothèques natives de référence (libjpeg-turbo, MozJPEG, Jpegli,
CharLS, OpenJPEG et libjxl) peuvent servir d'oracles de développement
optionnels ; elles ne font jamais partie du runtime Kanvas ni de la CI portable.

L'objectif est un support d'outils graphiques pour les images fixes. Les
capacités d'animation, vidéo et séquences sont explicitement hors périmètre,
même lorsqu'un format les définit.

## Architecture

Le dispatcher `:codec` détecte le format et choisit un unique provider. Il ne
peut ni essayer un autre codec après un refus ni déléguer à un backend externe.

```text
bytes / stream -> registry -> codec owner -> immutable document
                                            |- decode pixels
                                            |- lossless transcode
                                            `- explicit encode
```

Les modules cibles sont :

```text
codec:api / codec:core
├── codec:jpeg        JPEG classique
├── codec:jpeg-ls     JPEG-LS
├── codec:jpeg2000    J2K et JP2
├── codec:jpegxl      JPEG XL statique
├── codec:common      streams bornés, budgets, diagnostics et métadonnées
└── codec:conformance corpus, générateurs, fuzzing et rapports
```

`codec:common` ne contient aucun algorithme de compression ou de décompression.
Chaque module possède son analyse de conteneur/codestream, son modèle de
document, ses limites sémantiques, son decodeur et son encodeur.

### JPEG classique

`JpegCodec` demeure la façade compatible avec l'API `Codec`. Son implémentation
est séparée en sous-composants :

```text
container/  marqueurs, segments APP/COM, streams et limites
metadata/   JFIF, Adobe, EXIF, XMP et ICC segmenté
frame/      SOF, précision, sampling, modèles couleur et scripts de scans
entropy/    Huffman, arithmetic coding et restart intervals
dct/        sequential et progressive
lossless/   prédicteurs et point transform
pixel/      grayscale, YCbCr, RGB, CMYK et YCCK
transform/  crop, flip et rotation MCU sans ré-encodage
encode/     stratégies et sérialisation des modes pris en charge
```

La cible couvre les modes fixes non réservés du JPEG classique : sequential,
progressive, lossless, differential et hierarchical ; les variantes Huffman et
arithmetic ; les précisions et sampling factors valides ; les modèles grayscale,
YCbCr, RGB, CMYK et YCCK ; ainsi que les métadonnées ICC, EXIF, XMP, JFIF,
Adobe et COM.

## Contrat d'API

L'API usuelle reste disponible : `Codec.MakeFromData()` sélectionne le provider
et `JpegEncoder` conserve ses surcharges `SkBitmap`, `SkPixmap`,
`OutputStream` et `SkWStream`.

Une API experte, commune dans ses principes à chaque format, sépare l'ouverture
du decode :

```kotlin
JpegDocument.open(source, limits)
document.decode(request)
document.transcode(transform, metadataPolicy)
JpegEncoder.encode(source, options)
```

Le document est immutable. Il expose les segments et métadonnées reconnues,
ainsi que des diagnostics typés. Les segments inconnus sont conservés
byte-for-byte lors d'un transcode qui ne les affecte pas. Les métadonnées ne
sont réécrites que par une demande explicite.

Les options d'encodage sont déclaratives : mode de codage, précision, modèle
couleur, sampling, tables, restart interval, script progressif, prédicteur
lossless, point transform et métadonnées. Aucune option ne choisit un backend
ni ne réduit la précision silencieusement.

Les modes d'export sont :

- `Compatibility` : JPEG baseline largement interopérable ;
- `Quality` : stratégies psychovisuelles et progressive scan scripts ;
- `Archival` : chemin lossless ou haute précision conforme au format choisi.

## Contraintes de sûreté et de comportement

Chaque parseur applique les limites sur les dimensions, échantillons, segments,
tables, scans, tuiles, mémoire et travail CPU avant l'allocation ou la
décompression. Les données invalides et les fonctionnalités impossibles à
représenter produisent un refus déterministe et diagnostiqué ; elles ne sont
jamais "devinées".

Les chemins de decode normalisent les pixels selon la demande API sans inventer
une provenance couleur. Les profils ICC sont traités comme des données
provenancées ; les transformations couleur non implémentées restent explicites.

Les opérations de transcode sans perte sont structurales. Elles préservent les
pixels et les segments non affectés ; une opération qui imposerait un
ré-encodage est signalée comme telle avant l'écriture.

## Frontières par format

### JPEG-LS

Module autonome pour les modes lossless et near-lossless, les prédicteurs et
les profondeurs pertinentes. Son bitstream ne partage pas le moteur entropy ou
le parseur du JPEG classique.

### JPEG 2000

Prend en charge le codestream J2K et le conteneur JP2, y compris les tuiles,
résolutions, progressions et profils couleur nécessaires au travail sur grands
documents. Les APIs de decode doivent permettre le choix de région et de
résolution avant la matérialisation des pixels.

### JPEG XL

Prend en charge les images fixes lossless et lossy, alpha, HDR, profils ICC et
extra channels pertinents pour un outil graphique. La reconstruction sans perte
d'un JPEG encapsulé est prise en charge lorsqu'elle est présente. Les images
animées et fonctionnalités vidéo sont refusées de façon stable.

## Validation

La définition de fini d'un module exige :

1. decode, encode et métadonnées du scope déclaré ;
2. corpus sous provenance, cas générés et fichiers malformés ;
3. tests de structure et de pixels, avec identité exacte pour les chemins
   lossless et des métriques explicites pour les chemins lossy ;
4. fuzzing du parseur et du decodeur ;
5. comparaisons différentielles optionnelles avec les oracles de référence ;
6. mesures de performance reproductibles sur photos, graphismes, CMYK,
   haute précision et grandes images ;
7. CI portable confirmant l'absence de dépendance AWT, ImageIO, JNI et
   `java.desktop`.

Les rapports de conformance ne revendiquent jamais un support sans les
fixtures, les diagnostics de route et les preuves de test correspondantes.

## Ordre de livraison

1. Fonder les primitives communes, le registry, les limites et le harness de
   conformance.
2. Achever JPEG classique sur l'ensemble du scope défini, y compris l'encodeur
   et les transforms structuraux.
3. Ajouter JPEG-LS comme module indépendant.
4. Ajouter JPEG 2000, y compris les APIs de région et résolution.
5. Ajouter JPEG XL statique, y compris HDR et la reconstruction JPEG sans
   perte.

Un format ne peut être promu dans la matrice de support qu'une fois tous ses
critères de validation remplis. Cet ordre est une stratégie de réduction du
risque, pas une réduction de la cible finale.
