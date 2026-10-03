# W7 — isoler la couverture AA diagonale

3 octobre 2026. Parent #2437, d12749b64ebd5bb33e659dc35e8e41af3f678c84.

## Intention et preuve attendue

Approcher la parité Skia, pas simplement ouvrir des capacités sans gain visuel.
Le vrai teenyStrokes garde 191 encoded contre223 sur une diagonale, malgré
la correction188→128 du bord vertical. Il faut départager contour/CTM,
couverture et composition avant de choisir un nouvel algorithme.

Ce lot est un diagnostic, sans modification produit ni promesse de parité.
Les témoins passent par les APIs math et Surface natives existantes. Ils ne
constituent ni CPU renderer ni nouvelle infrastructure de tests.

## Choix

1. Hausser le MSAA : ne garantit ni l'aire ni la parité et n'est pas retenu.
2. Corriger la couleur uniquement : déjà réfuté par le témoin diagonal.
3. Isoler le contour et la couverture, puis choisir une architecture AA : retenu.

Le contour attendu d'une ligne BUTT de largeur5 allant de(70,20) à(150,100)
a les quatre sommets indépendants(68.232233,21.767767),
(71.767767,18.232233),(148.232233,101.767767),(151.767767,98.232233).
La préparation tiny reciprocal doit retrouver ces sommets à3e-5 pixel près,
sans imposer l'égalité bit-exact F32/F64.

À y60, les cellules x106 et107 ont des aires théoriques respectives
0.143398282201787 et0.892135623730951 : demi-triangles de côtés
2.5√2−3 et4−2.5√2. Ce calcul de deux pixels ne reproduit pas le renderer.
Les valeurs Skia223 et31/32 ne sont pas ces aires exactes : ne pas les
identifier à un modèle universel ni présumer le backend de la référence.

Surface compare ligne écran, ligne source sous tiny CTM et contour littéral
FILL à y60. Les pixels intérieurs/extérieurs ont des attendus indépendants.
Les rampes natives transparentes et opaques sont archivées : alpha sépare
couverture et couleur ; sur blanc encoded, gris+alpha vaut255 à±1.
La couverture n'est pas figée par une assertion exigeant quatre niveaux :
les observations diagnostiques devront permettre une future correction AA.

## Contraintes

- Fonts, codecs externes et jpg-color-cube hors périmètre.
- Aucune modification renderer/plan/math produit, référence, seuil, score,
  registre631/scope443, GM, budget, sample count ou précision oracle.
- Pas de mock, skip GPU, test source-text/forwarding/infrastructure ou fallback CPU.
- Objets et calculs géométriques produit dans math, nomenclature I/F32/64.
- Un seul runtime contrôleur, watchdog240s inchangé, audit séparé avant suivant.
- Préserver le diagnostic inverse-filter untracked SHA96cd8349, les raw receipts
  et les workspaces existants. Aucun cleanup ni merge.
- Suites globales héritées RED/incomplètes : pas de GREEN global déduit.

## Sortie et décision suivante

Deux fichiers de témoins, reçus natifs/math, diagnostic causal durable et
avis stratégique Astra unique. L'avis doit proposer la plus petite étape
produit utile ; ce lot ne ferme pas W7 et n'autorise pas une migration GM.
La carte blanche du pilote couvre l'écriture/relecture et l'exécution du plan
sans demander une nouvelle approbation ; le suivi explicite conserve les choix.
