# W7 — Rect hairline hard-edge et composition encodée

30 septembre 2026. Base `a170ea7be389240cc0b6efa581ec28ce33f1e910`,
draft [#2422](https://github.com/ygdrasil-io/kanvas/pull/2422).
Branche `codex/w7-encoded-hairline`. Pilotage délégué par l'utilisateur.

## Intention et preuve du besoin

Débloquer le vrai contour `drawRect`, `STROKE`, largeur zéro, non-AA de
`alphagradients`, sans changer son sens en largeur locale 1 et sans redessiner
les contours dans l'adaptateur GM. Ce lot livre une capacité moteur ; le port
GM et son choix de domaine resteront un changement de scène mesuré séparément.
Les deux prérequis précédents — politique alpha du gradient et composition
Surface — sont livrés dans #2421 et #2422. W7 n'est pas clos.

La baseline native fraîche de cette branche est **43/43**, trois classes
`W7StrokeRoutingSurfacePixelTest`, `W7SurfaceCompositionPixelTest` et
`W7GradientAlphaSurfacePixelTest`, processus/wrapper0. Archives :
`/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/baseline`.
La globale héritée reste rouge/incomplète ; cette baseline ne la remplace pas.

La source Skia épinglée par l'audit a été relue :
[GM](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/gm/alphagradients.cpp),
[HairRect](https://github.com/google/skia/blob/8019e2e0629f3516b9d829737de2553b1d0ecb4a/src/core/SkScan_Hairline.cpp).
Le contour rectangulaire non-AA couvre la bordure intérieure de
`[floor(left), floor(top), floor(right+1), floor(bottom+1))`, sans double
composition des coins. Pour des coordonnées entières admises ici, le pixel
au bord gauche/haut appartient au contour, ainsi que celui au bord droit/bas.
Ce constat porte sur ce chemin raster Skia, pas sur tous ses backends ni sur
la provenance exacte du PNG historique, toujours inconnue.

## Choix architectural

Trois voies examinées :

1. Remplacer le hairline par un stroke local de largeur 1 : rejeté, faux sous
   transformation et aux coordonnées entières.
2. Émettre quatre draws W3 par commande d'origine : la couleur encodée existe
   déjà, mais les occurrences de source, command indices, rebinding W6 et
   budgets supposent une occurrence géométrique authentifiée. Ne pas lever
   ces invariants pour faciliter l'extension.
3. **Retenir une occurrence géométrique unique dans le chemin W4d général**,
   couverture calculée par `math`, puis source/material et composition
   cibles dans les mécanismes existants. C'est la voie retenue. Le coût est
   de propager explicitement le domaine dans ce planner et ses jonctions.

La géométrie ne dépend pas de `CompositionDomain`. La même correction de
Rect hairline entier s'applique aux cas LINEAR admis : un changement de pixels
LINEAR causé par cette correction sera mesuré, pas masqué sous « compatibilité ».
AA, Path, stroke fini et demi-coordonnées existants gardent leurs chemins.

## Contrat géométrique

- Rect d'origine non vide, fini, `STROKE`, `strokeWidth == 0f`, non-AA,
  source solide sans shader/filtre/path effect, SrcOver, cap BUTT et join
  MITER avec miter fini au moins 2. La tranche ne généralise pas les joins.
- Projection axis-aligned vers des bords device entiers, sans perspective,
  avec échelles non nulles. Pour SRGB_ENCODED, conserver les transforms
  identité/translation entière du contrat Surface ; les scales LINEAR
  déjà admis servent de contrôle de largeur device.
- Géométrie dans `math/geometry`, API nommée `rectHairlineCoverageBandsI32` :
  entrée `RectI32` device et `RectI32` clip, sortie de rectangles disjoints
  neufs. Arithmétique I64 pour `right+1`, `bottom+1` et intersections avant
  conversion en I32. Pas d'allocation proportionnelle à la surface.
- Les bandes haut/bas possèdent les coins ; les bandes gauche/droite ne
  contiennent que les rangées intermédiaires. Les petits rectangles dont
  la bordure remplit toute l'étendue n'ont ni trou ni chevauchement.
- Former le contour d'origine avant intersection avec cible/clip. Ne jamais
  déplacer une bordure sur le bord du clip. Une couverture totalement coupée
  est un NoOp validé ; un doute numérique ou une limite de ressources reste
  un refus explicite, pas un élargissement de bounds.
- Adapter ces bandes en une seule géométrie préparée avec les outils math
  existants ; comptabiliser segments/vertices/indices/uniforms et travail
  avant publication, avec les plafonds actuels. Aucun nouveau type GPU de
  « faux stroke » et aucun epsilon de placement.

## Contrat de composition et de provenance

Le Rect/STROKE originel reste dans Picture/SceneSnapshot et dans l'autorité
de source. La projection de couverture n'est ni une réécriture du port GM ni
une mutation du paint capturé. Les identités de contenu des nouvelles scènes
doivent inclure leur géométrie ; les identités des scènes FILL inchangées ne
doivent pas bouger simplement à cause de l'extension.

Le domaine cible pilote normalisation de source, proof, préparation différée,
format logique/physique, construction, pipeline et readback. Aucun seal/check
physique ne devient optionnel. Les routes AA demeurent LINEAR dans cette
tranche ; ne pas admettre de MSAA encodé par effet d'une constante remplacée.

Support public requis : hairline seul et avec les sources encodées déjà
admises (solide, LinearGradient dans les deux modes alpha, DrawColor et image),
dans l'ordre, à la racine et dans l'unique plain layer existant. Restore opacity
une seule fois, translation et hard clip, bounds de layer non nuls. Les
snapshots SOURCE_SPACE full/subset et replay Picture mémoire/archive restent
cohérents. L'admission est whole-frame avant publication ; les exclusions
précèdent tout rendu partiel et permettent discard/récupération.

La réserve readback clear-only de #2422 devient pertinente pour le contour
entièrement coupé : corriger le format déclaré de son layout à partir du
format cible déjà authentifié. Pas de nouvelle famille de conversions.

## Validation indépendante

Les attentes sont figées avant GPU, sans appeler le helper géométrique produit.
Sur 8×8, Rect `(2,2,5,5)` : rangées 2 et 5 `BBRRRRBB`, rangées 3 et 4
`BBRBBRBB`, toutes les autres `BBBBBBBB`. Bleu opaque de fond, rouge de contour.
Vérifier tous les pixels, pas seulement les intérieurs. Répéter avec une
couleur semi-transparente : les quatre coins doivent avoir exactement la
même composition que les côtés, un double-hit doit être discriminé avant GPU.

Un Rect `(-1,2,5,5)` coupé par la cible conserve ses côtés haut/bas et droit,
sans nouveau côté gauche à x=0. Même règle avec un hard clip intérieur.
Un Rect local `(1,1,2,2)` sous scale LINEAR 3 couvre une bordure device d'un
pixel de `(3,3)` à `(6,6)`, pas trois pixels. Conserver la baseline demi-entière.
Inclure dimensions minces, invisible complet et coordonnées hors cible.

Pour les valeurs composées, réutiliser `W7CompositionCpuOracle` et son
`CompositionEnvelope` explicitement moins précis, sans modifier ses bornes.
Géométrie exacte, alpha/domaine/ordre/canaux discriminés par ensembles disjoints
avant GPU. Les réserves d'arrondi/store de #2422 demeurent, pas de parité
Skia à un code déduite d'une assertion d'appartenance.

Témoins publics Surface/Picture, native Render+Readback, RGBA/BGRA, répétition,
alternance des domaines, capture immuable, refus/sentinel/discard/récupération.
Un témoin de budget B/B−1 est dérivé statiquement et archivé avant son premier
run ; aucune recherche a posteriori de la limite ne devient une preuve a priori.
Tests math de calcul autorisés ; aucun test nouveau de l'infrastructure.

## Livraison et limites

Deux tâches successives : géométrie commune avec preuve publique LINEAR, puis
propagation encodée et intégration. Reviews Sol de tâche, review finale Astra
et unique vague de correction si nécessaire. Une globale bornée240s, puis
corpus631/443 inchangé, timeout30s conservé, comparaison nominative des198
empreintes initiales et diagnostics. Toute variation est attribuée au moteur.

Fonts, codecs/décodage externe, `jpg-color-cube`, GM/adaptateurs, PNG de
référence, seuils, exclusions et scores historiques ne changent pas dans ce lot.
PR draft empilée sur #2422, pas de merge. Port fidèle d'`alphagradients` ensuite,
avec changement de scène explicitement séparé ; objectif W7 toujours actif.
