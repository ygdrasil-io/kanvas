# W7 — couverture AA et composition différée

## Intention et autorité

Rapprocher réellement le renderer de Skia, sans multiplier les exceptions par
GM. L'utilisateur délègue les changements W7 (« carte blanche »), conserve
Subagent-Driven Development et des PR draft empilées. Ce design fait suite à
la draft #2425, base `07e3c05674b8c2e225249bda7bd7583d6bff2198`.
Il ne clôt ni W7 ni les gates W6 encore ouvertes.

Le diagnostic frais confirme 31/31 témoins AA existants, mais `PlusMergesAA`
refuse toujours : le prédicat de source couleur exclut PLUS avant sélection
W4d, puis W6 enveloppe le refus ordinaire. Le port correspond à la source
[Skia plus.cpp](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/plus.cpp).
Les 38 refus `unsupported_child` ne représentent pas 38 gains démontrés.

## Décision architecturale

Retenir **couverture résolue C + matériau prémultiplié S + blend final**.
La source couleur résolue C*S suffit pour PLUS, mais perd C lorsque l'alpha
de S est nul ; elle ne peut donc devenir le contrat de CLEAR/SRC.
L'ancienne source couleur SrcOver reste disponible et inchangée.

Réutiliser le producteur blanc opaque `PlanW4dAaCoverageSourceBindingV1`, les
matériaux solides et les formules existantes. Un consommateur typé et un
émetteur d'occurrence partagé raccordent root, layer puis Picture. Aucun
rasterizer, interpréteur de matériaux ou moteur de blend parallèle.
Astra a relu la stratégie et recommande quatre unités verticales : correction
W5 PLUS couvert ; AA PLUS/SRC_OVER root/layer ; famille Porter-Duff ; Picture.

## Loi et identité

S et D sont prémultipliés dans le domaine de composition admis. C est une
couverture géométrique scalaire indépendante de l'alpha du matériau.

- PLUS : `sat(C*S + D)`, pas `D + C*(sat(S+D)-D)`.
- Autres modes admis : `D + C*(B(S,D)-D)`. Une multiplication de source ne
  remplace cette expression que pour une équivalence sélectionnée et prouvée.
- CLEAR : `(1-C)*D` ; SRC : `C*S + (1-C)*D`, y compris S.a=0.
- C=0 conserve D ; C=1 conserve le blend plein existant ; DST ne produit
  aucune mutation ni incrément de version de destination.
- Producteur AA : blanc opaque, SrcOver sur transparent, 4 samples,
  direct-triangle ou paire atomique stencil/cover. Consommateur : 1 sample.
- Lire l'**alpha** du resolve comme C. Son stockage RGBA8 sRGB n'applique
  aucune fonction de transfert à l'alpha. Ne pas réutiliser implicitement
  le canal rouge du masque W5 ABI4. Inclure la quantification UNORM.

La loi est un fait sélectionné canonique, transporté jusqu'à la recette,
preuve préparée et clé native ; le backend ne reclasse pas le mode public.
Ajouter `BlendCoverageLawV1` (`DestinationInterpolation`, `SourcePreScale`)
au contrat de composition destination-read. PLUS scalaire sélectionne
`SourcePreScale`, les contrats existants conservent leur loi. Le kernel plein
`plus_exact@v1` ne change pas. La nouvelle identité composée encode la loi
versionnée ; ABI3/4 gardent leur topologie/bindings, sans optimisation mémoire
concomitante. Un paquet incohérent doit refuser avant allocation native.

La loi Skia est documentée dans
[SkBlendMode.cpp](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/src/core/SkBlendMode.cpp).
Le post-lerp PLUS de l'oracle W5 V1 décrit l'ancien comportement, pas cette
sémantique. Une entrée indépendante V2 le remplace explicitement pour PLUS
couvert ; le calcul V1 peut seulement servir de contre-exemple. Les arrondis,
preuves, bornes et codes acceptables ne sont pas élargis.

## Matrice fermée en fin de série

| Axe | Nouvelle admission | Hors nouvelle admission |
| --- | --- | --- |
| Matériau | Solide existant, alpha 0/fractionnaire/1, opacité existante | Gradients, images, runtime shaders, filtres |
| Géométrie | Path AA FILL winding/even-odd, direct/stencil ; Rect AA FILL via lane analytique existante ou projection prouvée | Strokes/hairlines, inverse, path effects, nouveaux oval/RRect |
| CTM/clip | Identité et affine finie non singulière déjà préparée ; aucun clip ou scissor device rect entier non-AA | Perspective nouvelle, clip AA/complexe |
| Blend | CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, PLUS | Modes avancés, extension SCREEN/MODULATE, custom/LCD |
| Cible | LINEAR prémultiplié, stockage sRGB déjà accepté par le producteur AA | Nouvelle admission SRGB_ENCODED, float/HDR, clamp implicite |
| Contexte | Root, plain layer transparente déjà admise, Picture non filtrée root/dans layer | Nouvelles initialisations/restores/filtres de layer/Picture |

Les cellules ne sont activées qu'avec leur exécution et leurs pixels. Les
capacités historiques hors matrice sont préservées. Rect garde sa provenance
et ses lanes analytiques déjà réussies. Aucune saveLayer artificielle pour
forcer l'ownership racine ; le prédicat reconnaît la capacité sémantique.

## Assemblage et ressources

L'occurrence conserve identité de commande, couverture/scissor/mapping,
matériau solide scellé, BlendPlan/loi, cible réelle, versions avant/après,
snapshot éventuel et dépendances. L'émetteur extrait de
`W6aLayerGraphConstruction` reçoit ces faits sélectionnés ; il ne choisit pas
de compiler. Il rebind le producteur, émet ses passes, snapshot la destination
si nécessaire puis compose une fois. Root/layer/Picture utilisent ce seam.
Picture exige sélection **et** assemblage, pas seulement un compiler ajouté.

Valider propriétaire unique du resolve, samples/format/extent/origine exacts,
initialisation, absence de feedback alias, atomicité stencil et version de
destination immédiatement précédente. Le blend enfant affecte sa layer ;
le restore de celle-ci est une opération distincte.

Budgéter MSAA, resolve, stencil, buffers, snapshots, alignement des lignes,
passes et travail avant publication. Pas de hausse de cap, cropping implicite
ou nouvelle politique de cache. Les refus restent transactionnels ; sentinelle
inchangée et récupération sur la même Surface sont des preuves publiques.

## Vérification et limites

RED causal puis GREEN sur GPU réel. Chaque cellule annoncée des 13 modes ×
Path/Rect × root/layer/Picture-root/Picture-layer a un témoin positif ; ne pas
réaliser tout le produit cartésien des sous-cas numériques. Couvrir globalement
alpha nul/fractionnaire/opaque, C extérieur/intérieur/partiel, deux stratégies
Path, deux fill rules, transform/scissor, ordre, réemploi Picture et budget B/B−1.
Les oracles sont fixés avant GPU, avec contre-exemples disjoints pour saturation
PLUS, CLEAR/SRC transparents, version périmée et double couverture.

Les nouveaux positifs exigent Render/Readback, dispatch réel, zéro refus,
diagnostic ou skip et répétition byte-identique. Pas de nouveau test
d'infrastructure, mock, forwarding, structure ou inspection du code source.
Géométrie nouvelle dans math avec suffixes I/F32/64 ; aucune prévue ici.
Fonts, codecs/décodage externe et jpg-color-cube restent exclus.

Rejouer les 31 témoins AA, les suites W5 affectées puis le corpus constant
631/443 avec les mêmes références, seuils, exclusions et timeout de 30 s/GM.
Distinguer capacité rendue et similarité. `aarectmodes` peut rester bloqué par
son oval ; aucun gain présumé. Les tests globaux historiquement rouges ou
incomplets restent déclarés, jamais transformés en baseline verte.

## Arbitrages délégués

1. Couverture séparée plutôt que resolvedColor PLUS : coût d'intégration plus
   élevé, mais évite un second contrat incompatible pour CLEAR/SRC.
2. Corriger W5 PLUS avant la nouvelle source : risque de migration de témoins
   historiques ; nécessaire pour une loi publique cohérente, sans tolérance nouvelle.
3. Limiter la série à 13 modes solides LINEAR : les autres matériaux/domaines
   restent ouverts ; la réussite de cette série n'est pas la clôture W7.
