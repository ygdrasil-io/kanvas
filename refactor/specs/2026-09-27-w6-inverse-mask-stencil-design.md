# W6 — couverture inverse sous clip mask

## Intention et périmètre

La route W6 admise doit dessiner un `drawPath` à fill inverse dans une layer
avec un `clipPath` public non rectangulaire. Le résultat est le complément de
l'intérieur du path **dans le domaine fini de la cible**, multiplié par la
couverture normale du clip. Les pixels hors du clip ou hors du domaine restent
inchangés. La source de vérité géométrique demeure dans `:math` et garde la
nomenclature I/F32/F64 ; `:gpu-plan` choisit et publie les passes, tandis que
`:gpu-renderer` traduit uniquement cette publication en commandes natives.

Ce sous-lot porte sur la route W4e single-sample hard-edge réellement publiée
par W6, puis sur ses conséquences pour le gel 2A0c, le budget 2A1 et
l'authentification 2B. Il n'ouvre ni fonts, ni codecs, ni GMs, ni tests
d'infrastructure, ni les phases AA/multisample non publiées par W6. Il ne
prétend pas résoudre le crash macOS de fermeture GLFW sur un thread non-main.

Le témoin public 6×6 déjà écrit dans
`W6aLayerW4W5SurfacePixelTest.kt` est rouge avant comparaison des pixels :
`ClipMaskProducer` reçoit un `GPUPixelBounds.left` négatif après translation
dans la layer. Le domaine physique de la layer suit actuellement les bounds
du triangle intérieur, alors que le résultat inverse existe hors de ce
triangle. Corriger seulement ces bounds laisserait une erreur de rendu : la
phase directe n'émet aucun fragment hors du triangle et son flag `inverse`
inverse la valeur échantillonnée du **clip**, non la couverture du path.

## Décision

`InverseMask.Geometry` quitte 2A0c.IIIa4b, qui supposait un unique consumer
direct, et rejoint 2A0c.IIIb/c. Cette décision remplace le passage IIIa4b
du plan `2026-09-26-w6-final-gates-implementation-plan.md` ; le reste du
séquençage W6 demeure inchangé jusqu'à sa révision après approbation de
cette spec. En plus du préfixe `ClipMask*` déjà nécessaire, W4e publie deux
passes ordonnées et deux bundles logiques sous le même draw :

1. Un producer dessine l'intérieur fini du path sans couleur dans un D24S8
   initialisé à zéro. Pour un triangle direct, il utilise les indices
   existants et `Replace(1)`. Pour un fan, il accumule le winding signé ou
   la parité `EVEN_ODD` des triangles ; écrire systématiquement 1 serait
   incorrect pour les trous et les contours opposés.
2. Un cover parcourt le domaine fini de sortie. Il n'écrit la couleur que là
   où le stencil vaut zéro et échantillonne le **clip normal**. La sortie est
   donc `(1 - pathInterior) × clipCoverage` dans ce domaine. Son binding de
   mask reste la texture au slot 0 et l'uniform au slot 1 ; aucun nouveau mask
   ou ABI de texture n'est ajouté.

`PathFillStrategy` continue de décrire la **forme** sélectionnée par la
géométrie. Les `PathRenderPhase.SingleSampleStencilProducer` et
`SingleSampleStencilColorCover` décrivent la **topologie d'exécution** de
ces deux passes. Le triangle conserve `DirectTriangle` ; il n'est pas
artificiellement déclaré `StencilCover` pour franchir la validation de
géométrie. Les consommateurs W5/W6 et l'inventaire natif découvrent le
besoin D24S8 et les deux sites par les phases finales, pas par la seule
`PathFillStrategy`.

Les alternatives écartées sont une texture supplémentaire de couverture
du path, qui ajouterait producer, ressource, ABI et budget, et la tessellation
du complément borné, qui impose une géométrie booléenne robuste pour trous,
contours et deux fill rules. Le chemin stencil réutilise le matériel natif
existant avec le plus petit changement de contrat W4e.

## Domaine et coordonnées

Pour `InverseMask`, `W5bW4ePathDraw.copyScissorI32()` expose le domaine fini
de `InversePathGeometryF32`, et non les seuls bounds de l'intérieur. W6
calcule ensuite son domaine physique par intersection avec la demande de
sortie, les bornes/hints de layer et la cible suivant ses règles existantes.
Le clip peut restreindre le résultat visible, mais les bounds du triangle
intérieur ne peuvent jamais borner le complément. L'usage initial du domaine
fini complet est conservateur ; une réduction future exige une preuve
indépendante de couverture et ne fait pas partie de cette correction.

L'origine de layer est ensuite appliquée une fois, de façon cohérente, à la
cible couleur, au D24S8, aux attachments de clip, aux scissors producer et
cover, et aux uniforms. Un scissor natif négatif est un échec de contrat de
publication/localisation, non une valeur à clamper silencieusement.

## Publication, inventaire et refus

W4e choisit la topologie **avant** la publication finale du graphe et avant
le calcul des ressources, lifetimes et du budget. Le producer et le cover
portent chacun leur `PlanPass.id`/ordinal final, cible, domaine, géométrie,
slice V/I/U éventuelle, load/store, état stencil, format/sample, blend et
binding. Le producer clear le stencil à zéro et le stocke ; le cover le
charge en lecture et le teste à zéro. Une ressource D24S8 existante n'est
réutilisée que si son clear, son intervalle de vie et ses usages sont
compatibles, sinon une ressource distincte est publiée avant le budget.
L'admission W4e vérifie dès la sélection que la cible accepte D24S8 1× en
`DepthStencilAttachment` pour tout `InverseMask.Geometry`, y compris quand
le clip mask provient d'un Rect ou RRect : la vérification actuelle du seul
clip `Path` ne suffit pas. Une capacité absente produit un refus explicite
avant publication et avant allocation native.

Le catalogue 2A0c, le seal et le préflight comparent ces deux sites, leurs
faits physiques et leurs usages enregistrés avant toute allocation native.
Ils comptent les créations logiquement requises même si un cache hit ou
`commonSource` omet une commande. Le décompte W4e du préfixe, des passes de
base et de leurs resource spans est révisé avant insertion des clips ; ajouter
une passe seulement à `insertClips` serait trop tard. Une incohérence de
topologie, de domaine, de D24S8 ou de binding refuse la frame de manière
diagnostiquée plutôt que de retomber sur le consumer direct erroné.

`InverseMask.Zero` fait l'objet d'un audit de reachability séparé : si cette
variante est publiée, son intérieur est vide et sa sortie est simplement le
clip normal sur le domaine. Elle ne reçoit pas de producer fictif. Aucune
lease positive n'est revendiquée pour une variante non publiée. `InverseDomain`
sans texture mask conserve sa route IV propre ; ses trois bundles historiques
ne sont pas copiés sur `InverseMask.Geometry`.

Le nouveau producer et le cover entrent dans les recettes gelées 2A0c.IIIb/c
et dans leurs leases 2A1 avant calcul de B/B−1. W5a ne spécialise que la
couleur finale du cover, pas le producer. Le gate 2B authentifie la séquence
effective des deux passes et leurs ressources ; aucun sous-lot IIIa4b isolé
ne ferme 2A0c, 2A1 ou 2B.

## Preuves publiques et critères de sortie

Le témoin 6×6 reste la première preuve : son oracle littéral est calculé
avant `Surface`, il distingue l'extérieur du triangle dans le L du clip,
le triangle non inverse et le domaine non clippé, et il exige `Render` plus
`Readback`. Après correction des bounds, son échec éventuel au niveau des
pixels reste un RED causal, non une raison de modifier l'oracle.

Les témoins ciblés suivants couvrent un `INVERSE_EVEN_ODD` à plusieurs
contours avec trou, une layer à origine non nulle, et deux draws inverses
successifs pour détecter un stencil non réinitialisé. Un témoin Zero n'est
ajouté que si la variante est effectivement publiée ; sinon l'audit note
explicitement son absence. Les témoins voisins `Mask` ordinaire et
`InverseDomain` Zero/Geometry restent verts. Chaque sélecteur est exécuté
séparément ; on consigne JUnit XML (tests/failures/errors/skipped), code
Gradle et sortie native séparément. L'exit 133/134 après JUnit ne vaut pas
validation native globale. Aucun GM, dashboard ou test d'infrastructure.

La correction est prête à rejoindre le reste de W6 seulement lorsque les
pixels publics, le catalogue/seal/préflight, les lifetimes et le budget
B/B−1 attestent la même topologie, puis que la revue indépendante ne relève
aucun finding Critical/Important. La PR reste empilée et Draft jusqu'aux
gates W6 globaux déjà définis dans la spec principale.
