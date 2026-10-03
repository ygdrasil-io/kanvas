# W7 — source couleur PATH AA ordinaire sous W6

3 octobre 2026. Suite du diagnostic diagonal approuvé Sol/Astra, pas une
activation d'AA analytique. Autorisation W7 carte blanche ; exécution Subagent.

## Intention et décision

Réparer une frontière partagée de scènes complètes plutôt que raffiner
TeenyStrokes, déjà99.5228% dans le corpus. Parmi36 refus de child W6, deux
sous-groupes de9 ont PATH/AA/WINDING/ownsW6b=true ; ils se distinguent par le
payload filtre. Le sous-groupe **sans filtre sur le draw fautif** est visé.
Ces9 sont une opportunité, pas9 gains promis ; les9 draws filtrés sont exclus.

La revue Astra a identifié deux gardes distinctes : rootAaSource exclut
ownsW6b et la source W6 historique exige FILL. Les séparateurs drawLine de
rrect_blurs et blurcircles2 étaient des suspects concrets PATH/STROKE.
Les captures natives RED confirment rrect draw10 STROKEwidth1 mais blurcircles
draw22 FILLwidth0 sur un path ouvert, CTMtranslation65,65. Les deux sont
solides AA SrcOver sans filtre/effet. GmCanvas.drawLine et Canvas.drawLine
omettent la sémantique implicite STROKE de Skia : une admission FILL vide
ne livrerait pas les séparateurs. Ce défaut API devient un préalable borné.

Approches considérées : supprimer la seule garde de propriété est insuffisant
pour STROKE ; refaire l'AA géométrique ajouterait un algorithme sans ROI corpus
établi ; **une source couleur ordinaire nommée**, consommant les contours et
l'isolation AA4 existants, cible les deux frontières sans nouveau sampling.

## Préalable drawLine — décision après capture et avis Astra

Le contrat [SkCanvas.drawLine](https://api.skia.org/classSkCanvas.html) ignore
le style de paint comme STROKE ; [SkCanvas.cpp](https://github.com/google/skia/blob/main/src/core/SkCanvas.cpp)
passe par drawPoints puis impose strokePaint.setStyle(kStroke_Style).
Sources primaires vérifiées le3octobre2026. Le planner ne doit jamais
reclasser arbitrairement un drawPath FILL pour compenser un bug API.

Ajouter Task0 avant source W6 : Canvas.drawLine copie seulement style=STROKE,
préserve largeur/cap/couleur/AA/effets ; GmCanvas.drawLine délègue au helper
partagé avec l'enveloppe clip/CTM de drawPath exactement conservée. Aucun
changement de GM ni de largeur0 en1. La préparation math Hairline existante
reste l'autorité. Deux témoins natifs indépendants, publicCanvas et GmCanvas,
fixent pixels visibles/extérieurs avant run et comparaison au drawPath
explicitement STROKE (égalité seule insuffisante), width0/width2,
FILL/STROKE/STROKE_AND_FILL et translation adapter. DrawPath FILL ouvert reste
vide. RED API→correctiflocal→GREEN→Sol, puis recapture blurcircles inchangé
attend STROKEwidth0/mêmescoordonnées/CTM/refusW6 et REDhairlineW6 dédié.

Approches écartées : poursuivre source seule risque une frame admise mais
incomplète ; attendre le rendu complet pour découvrir ce défaut ajoute du
travail sans information. Extension bornée à ces helpers/témoins ; tout
autre filtre/clip/famille substantielle reste un falsifier et impose
re-priorisation. Pas d'élargissement à drawPoints/arc/autres APIs ici.

## Contrat sélectionné

- Un draw root PATH/ANTIALIASED, FILL ou STROKE, solide SrcOver, réellement
  sans filtre/effet, dans une frame déjà possédée par W6.
- Original et entrée child représentent le même draw ordinaire. Ne pas enlever
  un vrai filtre puis appeler sa source nue « ordinaire » ; la provenance,
  l'ordre et les données paint/CTM/clip restent authentiques.
- Les transforms/clips, fill rules, préparations et budgets déjà supportés
  bornent la famille. Aucun inverse, nouveau matériau/blend/clip/filtre.
- FILL consomme le PathFillGeometryF32 existant ; STROKE conserve la préparation
  matrixF64.preparePathStrokeGeometryF32 et son snapshot PathStrokeGeometryF32.
  Pas de nouvel objet géométrique hors math, ni reconstruction renderer.
- Une source couleur isolée AA4 stencil/direct→resolve1x, consommée une fois
  par W6. Pas de readback interne, mélange scalar/sample ou shader analytique.
  Les root AA4 corrélés existants restent inchangés. La corrélation perdue lors
  d'une composition par sources isolées est une frontière existante de W6,
  pas une promesse d'aire exacte d'une scène composée.
- Domaines/format, transparence prémultipliée, bornes/localisation, lifetimes,
  clés/seals, coûts/pass counts sont conservés et authentifiés jusqu'au natif.
  Tester encoded seulement si le frère filtré relève déjà de ce domaine.

## Responsabilités et interfaces

`W6aLayerPlanCompiler` sélectionne la source à partir de l'original non filtré.
`W4dGeneralPathPlanCompiler` expose un factory/predicate W6 nommé pour cette
famille, distinct du mode Rect STROKE et du mode couverture de filtre.
`W6aLayerGraphConstruction` assemble AaResolvedColor et la consommation ;
`W6aLayerGraphValidation` et les consommateurs lowerer/prepared authority
gardent les invariants complets. Aucune vérification affaiblie globalement.
Le worker peut ajouter un fichier dédié de prédicat si cela évite de copier
les gardes ; pas de refonte générale du layer engine.

## Preuves attendues

Avant patch : capturer les draws fautifs de rrect_blurs et blurcircles2 via le
chemin public existant ; consigner style/coverage/origin/material/blend,
filtre/effets, CTM/clip et premier refus. Leur concordance est le gate causal.

Un témoin Surface public réduit contient un vrai frère filtré déjà admis et
un PATH AA FILL/STROKE non filtré. Fixer avant runtime des ancres pleines,
vides, demi-couverture, couleur prémultipliée et ordre à intersection. Une
source colorée sur transparent empêche de cacher alpha perdu/double couleur.
Un doublon demi-couvert sépare composition par source isolée et samples root.
Les négatifs préservent vrais filtres, matériaux/blends/clip/transform hors
famille et récupération de la prochaine frame native.

Livraison sur **rrect_blurs300×400 et blurcircles2730×1350 complets, inchangés** :
zéro refus, natif prouvé, formes filtrées ET séparateurs présents, replay,
comparaison/crops inspectés avec références/domaines/dimensions fixes.
Le threshold0 de rrect_blurs n'est jamais une preuve de fidélité. La mesure
entière et les régions de contenu doivent exposer les erreurs, pas seulement
le fond. Ensuite mesurer les9 cas et les contrôles touchés, puis le corpus
si le produit est retenu ; aucun gain déduit d'une capability seule.

## Arrêt et limites

Falsifier avant extension si capture hors famille, nouveau vrai filtre/clip/
matériau nécessaire, consumptionReady non prouvée, coût hors budget, l'un
des deux GM bloqué par une autre famille substantielle, contenu manquant ou
forte erreur de composition. Consigner la frontière exacte et re-prioriser ;
ne pas élargir automatiquement à toutes les layers ni revenir à l'AA convexe.

Fonts/codecs externes/jpg-color-cube exclus. Pas de CPU renderer/fallback,
mock, skip GPU, tests d'infrastructure/source-text/forwarding. Références,
scores/seuils, registre631/scope443, sample count et budgets inchangés.
Math suit I/F32/64. Contrôleur seul pour runtime/Git, un runtime à la fois,
watchdog240s inchangé et audit séparé complet après chaque terminal.
Workspaces/raw receipts et inverse-filter untracked SHA96cd8349 préservés.
Suites globales RED/incomplètes explicites, pas de merge ni W7 clos.
