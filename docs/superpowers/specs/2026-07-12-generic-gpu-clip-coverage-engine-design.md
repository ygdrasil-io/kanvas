# Moteur générique de couverture de clip GPU

## Statut

Conception approuvée en discussion le 2026-07-12. Cette spécification attend
la revue du document avant la planification et ne revendique aucune prise en
charge tant que les preuves de validation ne sont pas produites.

Elle complète la conception du routage de mask blur du 2026-07-11 et en
remplace les règles de clip, de clé de pipeline du blur et de diagnostics.

## Objectif

Créer un moteur WebGPU de clips réutilisable qui conserve et exécute exactement
les piles ClipStack, au lieu de les réduire à leurs bornes. Le moteur est
activé immédiatement pour toutes les routes de draw déjà prises en charge par
le renderer GPU. Le mask blur est son premier consommateur de filtre ; il ne
possède plus de chemin de clip particulier.

Les invariants sont les suivants :

- ClipStack.WideOpen ne crée aucune ressource de clip.
- Un DeviceRect non-AA, à bornes entières, conserve le scissor matériel, qui
  est le chemin rapide. Un DeviceRect AA ou fractionnaire devient une
  AlphaMask analytique afin de préserver ses bords fractionnaires.
- Une ClipStack.Complex conserve chaque élément dans son ordre : rect, rrect
  ou path, INTERSECT ou DIFFERENCE, anti-aliasing, règle de remplissage et
  remplissage inverse.
- Une pile finie et géométriquement valide est exécutée sans limite arbitraire
  de profondeur. Les seules refus autorisés sont les entrées invalides, les
  limites de l'adaptateur ou les budgets explicitement configurés, dont le
  budget de vertex existant.
- Aucun clip complexe ne peut devenir silencieusement une boîte rectangulaire,
  un scissor, un SrcOver par défaut, ou un rendu CPU/readback caché.

Le moteur respecte le modèle Geometry -> Coverage -> Paint : le clip produit
une couverture ; le paint et le blend la consomment sans réinterpréter la pile
de clips brute.

## Périmètre

Le moteur couvre toutes les routes GPU qui peuvent déjà encoder un draw :
rect, rrect, path, image, texte et vertices. Les opérations publiques qui sont
déjà refusées pour une raison indépendante restent refusées avec leur code
existant ; ce changement ne prétend pas élargir ces familles.

Les clips complexes couvrent les éléments actuellement représentables par
ClipStack : RectOp, RRectOp et PathOp, les opérations INTERSECT et DIFFERENCE,
ainsi que les variantes AA et non-AA. Les paths conservent contours, trous,
règle non-zero/even-odd et inverse fill.

Ne font pas partie de ce travail : ports Ganesh/Graphite, SkSL dynamique,
clip shader arbitraire, atlas de couverture persistant, compute tessellation,
ou compatibilité CPU implicite. WebGPU reste le seul backend GPU.

## Contrats et planification

Le contrat Geometry/Coverage porte une requête de clip immuable, distincte des
bornes conservatrices. L'adaptateur Kanvas construit cette requête à partir de
ClipStack avant la normalisation du draw. GPUClipFacts garde sa classification
rapide et référence la requête complète ; il ne remplace jamais cette requête
par ses bounds.

Canvas fige la transform active au moment de chaque appel clipRect, clipRRect
ou clipPath. Un rect reste un rect seulement si la transform le conserve
axis-aligned ; sinon il est capturé comme un path en espace device. Un rrect
non axis-aligned est également capturé comme un path device. Un path conserve
son fill type puis est transformé avant son insertion dans ClipStack. Le moteur
ne relit donc jamais une CTM ambiante pour reconstruire un clip historique.

Une requête de clip contient :

- l'identité de contenu canonique de la pile ;
- les dimensions et le format du target, ainsi que la génération du device ;
- les éléments ordonnés, leurs géométries transformées en espace device, leur
  opération, leur mode AA et leurs faits de fill ;
- les bornes conservatrices, utilisées seulement pour l'allocation et le
  scissor, jamais comme approximation de couverture.

Le plan sélectionné est l'un des trois suivants :

- NoClip pour WideOpen ;
- Scissor pour un DeviceRect non-AA à bornes entières ;
- AlphaMask pour ComplexStack et pour un DeviceRect AA ou fractionnaire.

AlphaMask expose le hash de pile, le format, les dimensions, les octets, le
nombre d'éléments, la stratégie par forme, le nombre de passes, l'état du
cache frame-local et les diagnostics. Le plan est déterministe : deux piles de
contenu identique sur le même target et la même génération de device ont la
même clé, sans inclure une identité objet ou une adresse mémoire.

## Construction de la couverture

Une AlphaMask est une texture rgba8unorm frame-local. Le cas non-AA est à un
échantillon. Le cas AA est rendu dans un attachment quatre échantillons avec
un depth-stencil de même sample count, puis résolu dans la texture échantillonnée.
Le budget compte le multisample et sa texture résolue. Le masque résolu commence
avec une couverture égale à 1. Pour chaque élément, le moteur produit sa
couverture E dans l'espace device et compose directement dans le masque :

- INTERSECT : C devient C multiplié par E ;
- DIFFERENCE : C devient C multiplié par (1 moins E).

Les facteurs de blend fixes nécessaires à ces deux opérations sont employés
uniquement pour construire le masque. Ils ne modifient jamais la scène.

Les rects et rrects utilisent une couverture analytique, y compris les rayons
non uniformes. Les rrects qui ne peuvent pas utiliser la variante analytique
sont abaissés vers le même chemin path que les autres formes. Les paths sont
aplatis à la tolérance device-space configurée, conservent leurs contours et
sont résolus par un stencil-cover de production, avec règle non-zero/even-odd,
inverse fill et résolution de bord AA. Cette précision configurée, validée par
les seuils de comparaison, définit l'exactitude du renderer ; aucune
approximation par bounds ou scissor n'est autorisée. Le triangle fan existant
ne constitue pas une stratégie valide pour un path concave ou à trous.

Le masque est mis en cache uniquement pendant la frame. Un pré-scan des
commandes compte les consommateurs de chaque clé ; une entrée est créée au
premier usage et libérée après le dernier. Cette politique permet le partage
entre draws sans introduire un atlas persistant.

## Intégration de toutes les routes GPU

Pour AlphaMask, chaque route encode d'abord son source non clippé dans le
target source temporaire partagé. Le composite générique lit ce source et le
masque de clip, applique source multiplié par couverture, puis applique le
blend final dans la scène. La logique est commune aux formes, images, texte
et vertices ; une route ne peut pas contourner le moteur avec les bounds du
clip.

Pour NoClip et Scissor, les routes directes existantes restent possibles afin
de ne pas ajouter de passe aux cas simples. Le scissor limite le raster et le
composite, mais il ne modifie pas la couverture géométrique.

Le mask blur continue de construire son masque de forme, ses deux passes de
blur et sa couverture de style dans un domaine local. Le composite final lit
le masque de clip générique en coordonnées device. Le clip est donc appliqué
après le filtre : le halo n'est plus tronqué à tort par les bounds du clip,
tout en restant invisible hors de la couverture exacte du clip.

## Blends avec lecture de destination

Les blends fixes composent le source déjà modulé par le clip directement dans
la scène avec l'état de blend demandé.

Pour MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN, DIFFERENCE et EXCLUSION, le
plan impose une lecture de destination :

1. la scène courante est copiée dans le snapshot de destination ;
2. le source est produit, puis modulé par le masque de clip ;
3. le composite WGSL lit le source et le snapshot ;
4. le résultat remplace la région de scène concernée.

Cette séquence empêche de lire et écrire le même attachment dans une passe et
empêche tout retour implicite à SrcOver. Les modes qui ne possèdent ni état de
blend fixe validé ni formule validée restent des refus explicites ; ils ne
produisent pas de pixels approximatifs.

Le snapshot est une copie GPU-à-GPU : le target source possède l'usage CopySrc,
la texture de snapshot possède CopyDst et TextureBinding, et le command encoder
encode copyTextureToTexture avant le pass de composite. Aucun readback CPU ni
upload de pixels ne participe au chemin de destination read. La télémétrie
enregistre la copie et doit rester à zéro pour destinationReadbackSnapshot sur
ce chemin.

## Budgets et durée de vie

RenderConfig reçoit maxClipIntermediateBytes, avec la même valeur par défaut
de 64 MiB que le budget actuel du mask blur, et la propriété d'environnement
kanvas.render.maxClipIntermediateBytes. Le budget couvre simultanément les
masques de clip frame-local, leurs ressources AA/résolution et les ressources
temporaires nécessaires à leur construction. Les ressources de destination
read restent comptées par leur politique de budget dédiée.

Avant toute allocation, le planner vérifie :

- maxTextureDimension2D exposé par l'adaptateur ;
- dimensions, format et octets de chaque ressource ;
- total actif de la frame par rapport au budget configuré ;
- vertex count d'un path par rapport à maxPathVertices ;
- validité et finitude de la géométrie.

Les refus utilisent des codes stables, notamment
unsupported.clip.nonfinite_input, unsupported.clip.texture_limit et
unsupported.clip.intermediate_budget, ainsi que unsupported.clip.vertex_budget.
Ils sont terminaux, n'encodent aucune passe de draw et ne modifient pas la
scène. Augmenter le budget dans
RenderConfig permet de traiter une pile qui ne tenait pas dans le réglage
précédent, dans les limites du matériel.

## Blur, WGSL et clés de pipeline

Les passes de mask blur utilisent exactement deux modules WGSL statiques :
horizontal et vertical. Leur uniforme contient un noyau gaussien de capacité
fixe, le nombre de taps actif et ses poids. La capacité couvre la sigma
effective maximale de 12 px du plan réduit ; elle est de 25 taps pour la
qualité normale actuelle. La boucle WGSL a donc une borne de 25 et choisit les
taps actifs par uniforme ; sa structure compilée ne dépend pas de sigma.

Sigma égale à zéro est le seul cas Identity. Toute sigma strictement positive,
y compris 0,1 et 0,5, utilise un noyau d'au moins trois taps. La sigma effective
est au minimum 0,5 px pour ce noyau, puis limitée par la stratégie de réduction
existante. Les valeurs demandée, normalisée, effective et l'éventuelle
réduction restent visibles dans le plan.

Les sigma, poids, taps, couleurs, bounds et données de clip sont des
uniformes ou des ressources. Ils ne sont jamais des axes de PipelineKey. Les
clés ne comprennent que les axes de topologie, de layout, de format, d'axe
horizontal/vertical et d'état de blend. Par conséquent, une série de sigmas ne
peut créer que les modules et pipelines correspondant à ces deux topologies de
blur, au lieu de créer un shader par sigma.

Les modules WGSL de clip, de composite clip, de blur et de blend sont parsés,
abaissés et réfléchis. Le packer Kotlin est vérifié contre les offsets reflétés
avant qu'un module soit exécuté. Les sources générées restent déterministes.

## Diagnostics structurés

Diagnostic reçoit facts: List<DiagnosticFact>, en plus du code et du message
humain. Chaque fait contient une clé et une valeur canoniques ; la liste est
triée lexicalement par clé avant son stockage. Les faits sont conservés jusqu'à
RenderResult ; une route ne les réduit pas à une simple chaîne de raison.

Les diagnostics de clip contiennent au minimum la clé de pile, le nombre et
le type d'éléments, les opérations, la stratégie, AA, dimensions, octets,
budget, limite d'adaptateur, cache hit/miss, passes et résultat. Les diagnostics
de blur contiennent sigma demandée/normalisée/effective, halo, échelle,
dimensions, octets, taps, modules/pipelines et passes. Les diagnostics de
blend signalent l'état de blend, la présence du snapshot, les ressources lues
et l'action choisie.

Les clés et les faits sont stables, triés et ne comportent ni adresse, ni
identité d'objet, ni détail dépendant du backend hôte.

## Validation

Les tests de contrat vérifient la capture complète des piles et les plans
NoClip, Scissor et AlphaMask. Ils couvrent rect, rrect à rayons non uniformes,
paths convexes ou concaves, multi-contours, trous, non-zero/even-odd, inverse
fill, INTERSECT, DIFFERENCE et les variantes AA/non-AA.

Les tests WebGPU réels vérifient que chaque route GPU prise en charge applique
la même couverture exacte et qu'aucun pixel hors clip n'est modifié. Ils
couvrent les blends avec destination non opaque, les snapshots, les mask blurs
à sigma 0, 0,1, 0,5, normale et grande, ainsi que les budgets par défaut,
augmentés et matériels. Les tests de blend affirment aussi que le snapshot
utilise une copie GPU-à-GPU et que le compteur de readback de destination est
nul.

Les tests WGSL parsèrent et abaissent tous les modules touchés, puis comparent
la réflexion au packer. Les tests de cache rendent plusieurs sigmas et piles
identiques après warmup, puis attestent zéro création de module ou pipeline
pendant 120 frames stables.

La preuve finale comporte tests unitaires ciblés, captures GPU, diff/stat,
compteurs de passes, de cache et de mémoire, diagnostics de route, et
régénération du dashboard Skia. Les GMs blur concernés conservent ou améliorent
leurs seuils existants ; aucun score n'est abaissé pour masquer une différence.

## Critères d'acceptation

Le moteur est prêt lorsque :

- ComplexStack n'est plus convertie en bounds dans aucune route GPU activée ;
- les clips complets sont appliqués à tous les draws GPU supportés, au blur et
  aux blends avec lecture de destination ;
- les chemins concaves, à trous et inverses ont une preuve WebGPU et visuelle ;
- les refus de budget ou de limite matérielle sont stables, sans dispatch et
  sans pixels partiels ;
- les sigma positives n'utilisent plus un shader de copie ;
- la cardinalité des modules/pipelines de blur ne dépend plus de sigma ;
- tous les WGSL concernés sont parser-validés et les diagnostics conservent
  leurs faits structurés ;
- la comparaison référence/CPU/GPU, les diff/stat et les artefacts dashboard
  sont disponibles pour les GMs promus.
