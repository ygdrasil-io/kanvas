# W7 — root Path AA et composition encodée

2 octobre 2026. Base produit publiée #2435 / 6f059f0dcf364cc351f984573f1fa9b7610ba337.
Diagnostic documenté a1a59388e4aedc651ce6f7e2e56151ab040607d8.
Pilotage et changements W7 délégués par carte blanche ; exécution Subagent-Driven
Development, Sol pour les reviews de tâche, Astra ciblée pour la stratégie.

## Intention et limites

Livrer une capacité publique Surface/Picture SRGB_ENCODED pour les Paths AA
solides et strokes finis à la racine. Garder le domaine LINEAR, son modèle de
samples et ses preuves historiques. Ce n'est pas une correction spéculative
ICC ou une conversion finale du rendu. Fonts, codecs externes et jpg-color-cube
restent exclus ; aucune référence, tolérance2, threshold, scope, budget ou cap changé.
Les GMs et leurs compositionDomain restent inchangés dans ce lot.

Le [diagnostic couleur](color-authority-diagnostic.md) écarte les paint literals
erronés fondés sur les triplets ICC bruts. Il localise 188/128 sur des côtés
verticaux et 0/32 sur des diagonales. Changer seulement le domaine ne promet
donc pas la parité raster Skia. Le corpus reste631/443,217rendus/194comparés ;
W7 et la globale sont ouverts.

## Décision relue par Astra

Retenir MSAA4 conservé jusqu'au dernier pass couleur, pas un resolve par draw.
Une Rect AA laisserait la frontière Path fermée ; une couverture analytique
modifierait simultanément sampling et géométrie. La tranche Path est la plus
petite capacité utile pour ces consommateurs. La stratégie Astra est statique,
pas une qualification runtime.

Admission fermée de la frame entière contenant au moins un Path AA : root seulement, source solide sans
shader/effet, SrcOver, clip absent ou hard Rect entier, Path non inverse.
Fills composés de segments linéaires et strokes de largeur positive,
BUTT/MITER avec miter fini selon la policy existante. Identité/translation,
puis affine axis-aligned finie non singulière déjà portée par math.
Le fond et les contrôles utilisent des Paths FILL de cette même famille ;
aucune admission implicite de Rect/image/gradient/layer par mélange.
Les anciennes frames encodées restent admises selon leur contrat antérieur.
Une frame de seuls Paths hard ne constitue pas une nouvelle entrée implicite.

## Autorité des samples et qualification numérique

Source indépendante épinglée : [WebGPU rasterization](https://github.com/gpuweb/gpuweb/blob/454d33cfdf6b8c8a1efafe490623cf0905e6c245/spec/index.bs#L16574).
Les positions MSAA4 imposées sont (0.375,0.125), (0.875,0.375),
(0.125,0.625), (0.625,0.875). Deux positions de chaque côté des lignes x=.5
ou y=.5, aucune sur leur bord. Ce n'est pas une couverture déduite du GPU.
Source archivée SHA2566408fbf862e748c2bbe1366aa7fb89455c25c7c200a0542e7175601bcc9458f5.

Le [resolve Metal documenté](https://developer.apple.com/documentation/metal/improving-edge-rendering-quality-with-multisample-antialiasing-msaa?language=objc)
moyenne les samples. L'oracle de test conserve les quatre états, compose et
quantifie chaque sample avant le resolve, décode les stores LINEAR et calcule
les code sets avec les primitives existantes. Aucune borne ou CompositionEnvelope
élargie. La précision native du resolve n'est pas une primitive WGSL : aucune
borne générale applicable n'est démontrée et cette dette reste OPEN. Le contrat
de ce lot est la qualification empirique de ces témoins sur la configuration
native observée, pas une enveloppe conservatrice prouvée de tous les résultats
natifs conformes, même sur cette plateforme. L'oracle moyenne exactement les
intervalles mais ne contient pas d'erreur propre au resolve natif. Aucun gating
automatique des autres backends n'est revendiqué. Une sortie hors des ensembles
figés arrête la qualification avant source ; déterminer sa cause, sans fitting,
tolérance empirique, élargissement des primitives ou attendu tiré du GPU.

Témoin 12×12 : segment vertical (4,2)→(4,10), width5 ; x1/x6 à y5 ont masque2/4,
x2..5 intérieur, x0/x7 extérieur. Miroir horizontal (2,4)→(10,4), mêmes
cellules transposées. Noir/rouge sur transparent et blanc distinguent alpha,
RGB et domaine. Noir répété sous le même masque doit garder le résultat2/4 ;
deux fills noirs aux masques complémentaires doivent rendre noir plein.
Ces cas séparent les masques corrélés d'alphas scalaires résolus par draw.

Le témoin non saturé R128 G64 B32 A128, transparent et blanc, est figé et vérifié
en LINEAR avant source, puis conservé identique pour encoded. Il qualifie des
sorties composées observées, pas la précision générale du resolve. L'identité
réelle de l'adapter/backend, les données logicielles disponibles, les formats,
les samples4 et les domaines sont archivés ; une identité non exposée reste
CannotVerify, sans déduction depuis le Mac. Opacité de paint, F32 tiny-scale
teeny et anisotropie nécessitent des attentes indépendantes avant GPU. Si une faute
math est découverte, corriger séparément à cette frontière, jamais
prétransformer les GMs, changer width ou ajouter un epsilon.

## Jonctions de la capacité

CompositionAdmissionV1 contextualise les transforms à la nouvelle frame
Path, sans élargir ses helpers Rect/image/layer. W4dGeneralPathPlanCompiler
conserve la géométrie math et l'origine, normalise la source dans le domaine
authentifié et dérive formats MSAA/resolve/target depuis celui-ci.
Les routes W4e/W6 différées restent LINEAR et fermées dans leur contrat.

Graph lowerer, autorité préparée, clés physiques MSAA et continuation doivent
porter le même format/interprétation/domaine, generation, roles, views et
samples4/1. Aucun seal/check facultatif, source opaque/noire interchangeable
ou resolve par draw. Le contrat public RenderConfig/Router reste inchangé.
Le preflight W4d.2 compare également format et interprétation aux facts scellés,
sans conserver un format LINEAR en dur ni accepter une valeur libre du caller.
Budget, durée de vie, floors et overflow I64 préflightés avant publication.

## Livraison séquentielle et arrêts

1. Témoins Surface réels et oracle indépendant, source inchangée : qualifier
   les contrôles LINEAR et observer le RED encoded par refus de capacité.
2. Une seule implémentation end-to-end de la capacité et ses guards,
   mêmes témoins GREEN, Picture/snapshot/cache/budget, contrôle des anciennes
   frames et corpus. Migration GM séparée seulement après qualification.

Pas de test infrastructure, mock, source-text, forwarding, skip GPU, CPU
renderer/fallback, faux Picture ou routage GM. Contrôleur seul lance runtime/
Gradle : original terminal, puis audit séparé complet avant le prochain.
Review de tâche Sol, fix même worker, revue finale ciblée à ce lot et une seule
fixwave. PR draft empilée sur #2435 ; ni merge, ni W7 clos, ni globale verte
sur la seule foi de ces témoins. Les pertes fraîches sont attribuées, pas masquées.
