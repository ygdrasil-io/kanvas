# W7 — audit causal de `alphagradients`

29 septembre 2026. HEAD audité `f040c97532bc446e6de116c48ae9d9ce1a97c52e`,
renderer mesuré `ff628a94da2a9c38aa05c004dff354f61ceaafbf`, draft
[#2420](https://github.com/ygdrasil-io/kanvas/pull/2420).
Audit de code Terra, recoupement amont, analyse des PNG et contrôles natifs.
Relecture stratégique Astra : résultats de la sonde reproduits indépendamment.
Aucune modification de renderer, GM, adaptateur, référence, seuil ou exclusion.

## Conclusion

Le score historique **33,8822 % de pixels ±2/canal** mélange au moins deux
contrats différents, qu'un correctif local au gradient ne peut pas résoudre :

1. **Interpolation alpha non représentable dans l'API.** Skia demande un mode
   différent par colonne ; Kanvas répète le même shader straight-alpha.
2. **Domaine de composition différent.** Les intérieurs du PNG Skia suivent
   SrcOver en sRGB encodé ; le rendu Kanvas suit SrcOver en lumière linéaire.
3. **AA explicitement différent dans le port.** SkPaint laisse AA désactivé,
   Paint Kanvas l'active par défaut. Les contours ne sont donc pas un oracle
   à paramètres identiques. Leur attribution exacte nécessite un témoin causal.

La géométrie du gradient est conforme : `(left,top)` → `(right,bottom)` dans
les deux sources. Une première lecture erronée l'avait dit horizontal ; cette
hypothèse est retirée, pas retenue comme défaut.

## Sources et chaîne réellement sélectionnée

Amont consulté et figé : [gm/alphagradients.cpp à
8019e2e0629f3516b9d829737de2553b1d0ecb4a](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/alphagradients.cpp).
`doPreMul` devient `InPremul::kNo` à gauche et `kYes` à droite. Le
[constructeur SkPaint](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/src/core/SkPaint.cpp)
initialise largeur 0 et AA false. Cette révision amont n'est **pas** revendiquée
comme la révision génératrice du PNG archivé, dont la configuration exacte
n'a pas été retrouvée dans cet audit.

Dans le dépôt, chemins relatifs à la racine :

- `integration-tests/skia/src/test/kotlin/org/graphiks/kanvas/skia/gm/gradient/AlphaGradientsGm.kt:52` :
  `col` ne change que le placement ; mêmes stops et même shader aux deux colonnes.
  La ligne 69 garde bien la diagonale ; lignes 68–74, aucun AA explicite.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Shader.kt:11` : l'espace
  d'interpolation est exposé, pas la politique premul/unpremul ; SRGB par défaut.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/paint/Paint.kt:15` : hairline 0,
  AA true. Ce sont des défauts Kanvas, pas ceux de SkPaint.
- `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt:282` :
  capture des stops straight et de l'espace SRGB.
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt:567` :
  clamp sans wrapper de coordonnées ne prend pas W5d ; normalisation
  `LinearGradientClampSrgbV1` à la ligne 713.
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt:350` :
  interpolation straight, puis EOTF sRGB, puis prémultiplication linéaire
  (lignes 365–381).

Un autre chemin `GPUPreparedMaterialProgram` interpole des stops prémultipliés,
mais ce n'est pas la route sélectionnée par ce fill clamp sans wrapper.
L'existence de ce code ne prouve ni une capacité publique équivalente ni la
prise en charge de la seconde colonne du GM.

## Colorimétrie et empreintes

Référence : `integration-tests/skia/src/test/resources/reference/alphagradients.png`,
640×480 RGBA16, SHA-256
`8389c8ad0391c19dd30b2e2a698d997a73340813aff5a3932e96b6b6bbfd04db`.
Le nom du chunk iCCP est **Rec.2020** ; sa description ICC interne est
`Google/Skia/CAB9EFCEBFA516F3185A3C8B8072A056` (ne pas confondre les deux).
Le texte PNG indique `Author: DM unified Rec.2020`, sans config de rendu.
SHA-256 du profil ICC décompressé :
`8ebad59a830cdaef8af6c1c4be7f3c091b2afc089da131a6c0aed2d4bc8c4266`.

`ComparisonUtils.kt:322` reconnaît le nom Rec.2020, et `:259` convertit les
canaux vers sRGB avant comparaison. Le transfert utilisé correspond au profil
embarqué ; sa matrice diffère des constantes du dépôt d'au plus environ
0,000007 par coefficient. Ce n'est pas une référence simplement interprétée
comme sRGB. Cet audit ne valide pas tous les chemins du codec, hors périmètre.

Rendu existant :
`/private/tmp/kanvas-w7-next.0hEQlf/alphagradients-inspection/images/alphagradients/actual.png`,
RGBA8 sRGB, SHA-256 PNG
`026b086cbea9a918d2ac0e7216cc8af3540bbe8c219dd24f9b349a616e076b46`,
empreinte RGBA déjà identique au corpus
`2a6184c82cbd235b2f77ee7f4b58f37e2408ab492a57611ce58609251166ee3a`.
Pas de nouveau rendu du GM dans cet audit.

## Témoin numérique et modèles falsifiables

Analyse indépendante en lecture seule des octets PNG : reconstruction des
filtres PNG, conservation des valeurs 16 bits, transfert et matrice lus dans
l'ICC réel, conversion vers sRGB puis arrondi 8 bits. Ni ImageIO ni codec
Kanvas n'interviennent dans cette sonde. Cela ne remplace pas le comparateur
officiel et ne produit pas de nouveau score GM.

Au pixel `(160,25)`, à l'intérieur de la première ligne gauche,
`t = ((150,5×300)+(15,5×30))/90900 = 0,5018151815181519` et `a=1−t` :

- interpolation straight : `C_s=1−t` ;
- composition sRGB encodée : `255 × (C_s×a + 1−a)` → **191** ;
- composition linéaire : `255 × OETF(EOTF(C_s)×a + 1−a)` → **205**.

La référence convertie vaut `(191,191,191,255)`, Kanvas `(205,205,205,255)`.
À droite `(470,25)`, la référence est blanche 255, comme attendu avec la
prémultiplication avant interpolation ; Kanvas répète 205.

Vérification étendue : **184 704 pixels intérieurs**, 24 rectangles,
coordonnées locales entières x=2…297, y=2…27, centres à +0,5. Bordures exclues
uniquement de cette expérience de diagnostic, jamais du score du corpus.

| Modèle indépendant | Référence : RGB ±2 | Kanvas : RGB ±2 |
| --- | ---: | ---: |
| Straight des deux côtés, composition linéaire | 26 037 / 184 704 | **184 704 / 184 704** |
| Straight gauche / premul droite, composition sRGB encodée | **184 704 / 184 704** | 25 960 / 184 704 |
| Straight des deux côtés, composition sRGB encodée | 107 798 / 184 704 | 35 700 / 184 704 |

Cette statistique vérifie RGB, pas alpha ; les témoins ponctuels ci-dessus
comprennent bien alpha. Écart maximal RGB de **1 octet** pour chacun des deux
modèles qui correspondent à leur image. Les autres atteignent 64 à 84 octets
d'écart. Le défaut de composition reste donc observable dans la colonne gauche, sans dépendre du
mode premul manquant à droite ni des contours. Cette expérience établit le
comportement de **cette référence**, pas celui de toutes les configurations
Skia possibles, ni celui de tous les GMs.

Le contour supérieur `(160,10)` vaut 0 dans la référence et 149 dans Kanvas ;
le pixel voisin `(160,9)` vaut respectivement 255 et 187. Ces mesures attestent
l'écart, sans prouver seules la contribution respective de l'AA et du domaine
de composition au contour.

Sondes temporaires, non ajoutées à l'infrastructure de tests :
`/private/tmp/kanvas-w7-alpha-audit.S67HDe/png-metadata.mjs` et `png-samples.mjs`.
Commande : `rtk proxy node /private/tmp/kanvas-w7-alpha-audit.S67HDe/png-samples.mjs`
suivie des deux chemins PNG ci-dessus. Leur disponibilité reste temporaire ;
les formules, régions, empreintes et résultats sont conservés ici.
SHA-256 de `png-samples.mjs` :
`b4cfe65be11e3a4432ef0d3a276fa1e5134bab49748183c87327891c37aa7ad7`.

## Contrôles natifs frais

**15/15 tests publics, 3 classes, Gradle 0, 29 s**, XML et events complets :

- `W5fGradientInterpolationSurfacePixelTest.mixedHistoricalAndWorkingSrgb*` : 2 ;
- `W7StrokeRoutingSurfacePixelTest` : 6 ;
- `W7MixedRootAaRectSurfacePixelTest` : 7.

Commande : `rtk proxy ./gradlew :kanvas:test --offline --no-daemon --no-build-cache`
avec ces trois sélections `--tests`,
`-I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle`,
`-Pw7.validationDir=/private/tmp/kanvas-w7-alpha-audit.S67HDe/native-controls`,
`--console=plain`. Timeout test 240 s ; un seul runtime natif lancé.
Warnings JVM System::load/Unsafe et dépréciations Gradle observés.

Ils prouvent le respect du contrat Kanvas actuel, pas la fidélité de son
contrat de composition à la référence Skia. Aucun nouveau test d'infrastructure.
Pas de globale rejouée : la globale précédente reste rouge/incomplète,
W7 et les gates W6 restent ouverts, pas de merge readiness.

## Ordre retenu après relecture Astra

**1. Prochain lot : politique alpha explicite des gradients.** Première
capacité bornée au LinearGradient sRGB clamp nécessaire ici, sans changement
du domaine de composition, de la représentation des images ni de l'encodage
de sortie. Les combinaisons non couvertes doivent refuser explicitement,
pas changer de route ou approximer silencieusement. Les autres familles et
espaces polaires ne deviennent pas automatiquement pris en charge.

Témoins publics à obtenir avec les fixtures existantes : blanc opaque vers
noir transparent sur blanc sans AA (straight 205, premul 255 à la position
analysée), deux alphas distincts **non nuls**, et maintien du mode par défaut
dans un mélange des routes déjà couvertes. Capture immuable, preuves, budgets,
refus transactionnels, second rendu et récupération doivent rester cohérents.
Si cette capacité impose de modifier la composition, arrêter ce lot avec la
preuve du couplage ; ne pas étendre tacitement son périmètre. Une similarité GM
encore incomplète est attendue, pas un échec de cette capacité bornée.

**2. Ensuite : contrat de composition de Surface.** Distinguer quatre axes :
espace colorimétrique, domaine d'interpolation, domaine de composition et
représentation des pixels prémultipliés. `ColorSpace.SRGB` ne définit pas à
lui seul ces quatre choix. `ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL`,
déjà utilisé par les snapshots, interdit de changer seulement les cibles GPU
ou l'encodage terminal sans traiter les consommateurs de cette convention.

Témoin indépendant du gradient : noir alpha 0,5 sur blanc, environ 128 en
sRGB encodé contre 188 en lumière linéaire. La conception de ce lot doit
inclure un passage direct, un intermédiaire et un snapshot semi-transparent ;
un témoin opaque masquerait les erreurs transfert/prémultiplication. Pour les
filtres, définir le domaine d'entrée/sortie ou garder un refus explicite.
Ces obligations ne prescrivent pas une refonte complète préalable à l'étape 1.

**3. Port corrigé séparément.** Choix alpha par colonne et AA explicite ;
changement de scène de test annoncé, baseline gelée et attribution distincte
du gain renderer. Ne pas ajuster seuils/références ni présenter le modèle
analytique comme un renderer corrigé. Fonts, codecs et `jpg-color-cube`
restent hors périmètre.

### Périmètre de la relecture

Astra ne relève pas de défaut critique invalidant l'audit. Ses deux points
importants sont intégrés ci-dessus : borner l'ordre des lots et distinguer les
quatre axes colorimétriques. Le point mineur RGB/alpha est corrigé dans le
tableau. Hors jugement : comportement universel de Skia, provenance exacte
du PNG, correction des contours, gain futur de similarité, tous les chemins
image/filtre et clôture W7/W6. Ces sujets restent ouverts, pas implicitement
validés ; aucune merge readiness.
