# Portage fidèle des GMs Skia liés aux polices

**Date :** 2026-07-13  
**Statut :** approuvé pour planification

## Objectif

Remplacer cinq GMs enregistrés mais encore stub par des ports aussi proches que
possible de leurs sources Skia, sans porter Ganesh, Graphite, ni les formats
binaires internes de Skia :

- `lattice2` ;
- `not_native32_bitmap_config` ;
- `stroketext_native` ;
- `typefacerendering` ;
- `user_typeface`.

Les providers sont déjà déclarés dans
`META-INF/services/org.graphiks.kanvas.skia.SkiaGm`; aucun doublon ne doit être
ajouté au `ServiceLoader`.

## Décision

Le port reproduira la structure et les scénarios des sources Skia. Lorsqu'une
API Skia dépend d'une implémentation interne, Kanvas ajoute une API propre avec
le même contrat fonctionnel, jamais une copie de l'ABI ou du format binaire
Skia.

La police de référence portable est la fixture Liberation déterministe de
Kanvas. Les fixtures importées depuis Skia conservent leur provenance, hash et
licence. Aucun font de substitution dépendant de la machine hôte n'est admis.

## Comportement des GMs

### `lattice2`

Reproduire la surface source de 80 px, les divisions `4,5` et `1,2`, les neuf
rectangles fixed-color/transparents, la bande de fond `Src`, puis les deux
passages de blend (`SrcOver` et `SrcATop`). Utiliser `Lattice`,
`drawImageLattice`, `Paint` et la surface Kanvas existants.

### `not_native32_bitmap_config`

Reproduire le checkerboard, la color wheel et les lettres centrées. La roue est
rasterisée dans un `Bitmap(ColorType.BGRA_8888)`, convertie en `Image`, puis
dessinée sur le canvas. Cela exerce réellement le stockage non natif et non
simplement une image RGBA équivalente.

### `stroketext_native`

Importer les fixtures d'essai Skia nécessaires, avec provenance vérifiée :
`Stroking.ttf`, `Stroking.otf` et la variable font demandée par la source.
Le GM conserve les trois groupes originaux : contours TTF dégénérés, contours
OTF dégénérés et paire avec le flag `overlap`, avec le même paint rouge,
stroke, cap et join.

L'API de variation doit transmettre les coordonnées (`wght=721`) au scaler
CPU et à la route WebGPU. Si une fixture n'est pas interprétable par une route,
la raison stable est visible dans les diagnostics; elle n'est ni masquée ni
remplacée par une autre police.

### `typefacerendering`

Importer `hintgasp.ttf` avec provenance et reproduire la matrice de la source :
tailles 9–16, edging, subpixel offsets, rotations, hinting, passages dans
layer, styles fill/stroke/fill-and-stroke, embolden et blur mask filters. Le
glyph invalide `0xFFFF` reste un no-op défini.

La représentation Kanvas introduit des enums explicites pour edging et
hinting, le drapeau `embeddedBitmaps` et une position de variation. Toute
valeur qui ne peut pas être reproduite par la route WebGPU possède une
diagnostic de dégradation stable. Les modes LCD/subpixel ne sont pas déclarés
comme supportés tant qu'une vraie sortie LCD n'est pas prouvée : le GM demeure
un test de comportement et ne transforme pas une approximation en support.

### `user_typeface`

Ajouter un `KanvasCustomTypeface` immuable et son builder. Il stocke les
métriques, le mapping codepoint → glyph, l'avance, le path vectoriel et, pour
les glyphes construits depuis un drawable, le paint intrinsèque. Une
sérialisation Kanvas déterministe permet le même round-trip fonctionnel que la
source, sans revendiquer la compatibilité binaire `SkTypeface`.

Le GM construit les 128 premiers glyphes depuis Liberation à l'UPM, alterne
glyphes de path et glyphes de drawable vert, sérialise/désérialise la police,
puis rend les deux waterfalls avec bounds et lignes de base. La route de texte
CPU/WebGPU devient capable de consommer une `Typeface` générique à contours;
elle n'est plus limitée au cast `FontTypeface`.

## API et architecture

1. Étendre `Font` avec `edging`, `hinting`, `embeddedBitmaps` et une position
   de variation immutable.
2. Rendre `FontTypeface` sensible aux coordonnées de variation via le scaler
   existant, plutôt que d'ajouter un moteur de police parallèle.
3. Ajouter `KanvasCustomTypeface` et son builder, avec codec Kanvas versionné
   et tests de round-trip déterministe.
4. Refactorer le lowering texte pour employer une interface de contour commune
   aux `FontTypeface` et `KanvasCustomTypeface`, côté CPU et côté WebGPU.
   Les glyphes à paint intrinsèque conservent cette couleur sans modifier les
   autres glyphes du `TextBlob`.
5. Conserver les formats Type 1 PFA/PFB hors périmètre : ils ne sont requis ni
   par les cinq GMs ciblés ni par la capacité textuelle sélectionnée.

## Validation et non-claims

La validation précède toute suppression du rapport d'audit :

- tests unitaires TDD pour les nouvelles APIs, les fixtures et la structure
  des GMs ;
- rendu CPU et WebGPU de chaque GM, avec `dispatchedCount`, diagnostics et
  refus contrôlés ;
- PNG généré, référence, diff et score par GM ;
- aucune baisse globale de seuil de similarité ;
- mise à jour des artefacts générés et des scores ;
- retrait du rapport seulement des cinq lignes effectivement non-stub.

Le résultat ne revendique pas de compatibilité Skia binaire, de port
Ganesh/Graphite, ni de support LCD/subpixel tant que les artefacts ne le
prouvent pas.

## Références d'architecture

- `.upstream/target/skia-like-realtime-renderer-target.md`
- `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`
- `.upstream/specs/font/README.md`
