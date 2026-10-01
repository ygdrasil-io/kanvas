# W7 — autorité native des scènes inverses

## Objectif et état

Rendre correctement un path inverse seul puis mélangé à des dessins ordinaires,
sans affaiblir les preuves du graphe ni les contrôles natifs. Cette conception
fait suite au [diagnostic mesuré](inverse-scene-inventory-plan.md), sur la base
publiée #2430. Elle ne décrit pas un correctif déjà implémenté.

L'utilisateur a délégué les décisions W7. Le contrôleur retient la stratégie
Astra ci-dessous ; pas de nouvelle boucle d'approbation. Fonts, décodage externe
et jpg-color-cube restent hors périmètre. Les interfaces publiques peuvent
évoluer si nécessaire, mais ce problème ne demande pas leur modification.

## Causes établies

Les six scènes Surface8×8 ont donné2SUCCESS/4FAILURE : les clips inverses
fonctionnent, les quatre dessins inverses directs refusent avant les pixels.
Une seconde exécution avec traces temporaires confirme les mêmes résultats ;
les traces ont été retirées et le diff produit est revenu vide.

Le cas mixte hard échoue parce que les phases ordinaires n'ont pas de consumer
InverseDomain. Le cas mixte AA contient aussi un PathMaskClear et un vrai
masque hard, sans chaîne Initialize/Fold de clip. La dichotomie actuelle
« tous inverses sans masque » / « inventaire complet de clip » ne décrit
aucun de ces deux inventaires légitimes. Un simple all→any serait insuffisant.

Pour l'inverse seul, les phases producer et cover portent chacune le consumer
InverseDomain.Geometry. L'encodeur leur donne à chacune une opération complète ;
le constructeur des clés continue à les classer par leur phase historique.
Les nombres observés sont10/5 puis10/7 en hard,10/5 puis11/8 en AA
(opérandes totaux/clés). Seul le premier désaccord lève l'exception ;
le second existe dans les recettes construites. La répétition de la couleur
est un risque à tester, pas un défaut pixel déjà observé.

## Décision : conserver la paire et rendre son contrat explicite

Conserver les deux passes ordonnées du graphe. Une autorité immutable émise
depuis le graphe authentifié scelle leur relation : commande, domaine fini,
intérieur/fill rule, pass IDs et ordre, atomic group, target/depth/sample,
slices du payload, load/store et propriétaire du resolve final.
Ni le champ consumer d'un packet ni son enum de phase ne suffisent à émettre
cette autorité. Ne pas activer artificiellement commonSource.

La recette ordonnée issue de cette autorité est consommée par les clés et
les commandes natives ; les deux ne doivent plus reconstruire séparément
la priorité entre inverse et phase historique.

1. Le producer remet le stencil à zéro et rasterise seulement l'intérieur
   fini scellé, sans écriture couleur. Triangle direct : replace-one ;
   fan déjà admis : winding/parity existant.
2. Le cover charge le même stencil et applique la couleur une seule fois
   là où il est zéro, dans le domaine inverse fini, avec le blend existant.
   Il ne remet pas le stencil à zéro et ne rejoue ni domaine ni intérieur.
3. En AA, même logique par sample dans le target/depth4× ; seul le cover
   désigné par le graphe porte le resolve final.

Les opérandes des commandes deviennent P/V/I pour le producer et P/BG
pour le cover, en plus des attachments exacts. Les nombres dérivés des
fixtures (hard5/4,AA5/5) ne deviennent pas des constantes d'admission.
Réutiliser les slices INVERSE_DOMAIN_INTERIOR et uniforms déjà scellées ;
conserver les capacités/bytes réservés existants dans ce lot. Pas de
réorganisation du packing ni de migration incidente W5b/W6.
Une paire Geometry dont l'autorité est incomplète refuse : aucun repli
silencieux vers l'ancienne opération complète.

## Ensuite : conserver l'inventaire authentifié jusqu'au natif

Transmettre dans l'autorité préparée un témoin immutable des ressources
du graphe et de leur mapping exact : IDs/refs, descripteurs, rôles, samples,
bytes, usages, lifetime, propriétaires et continuations ordonnés.
Valider les préparations et leurs owners contre ce témoin avant checkout.

Les allocations par ressource utilisent ce contrat, sans déduire un
inventaire global du nombre de consumers inverses. Un masque hard de scène
AA est alloué comme tel, sans inventer d'accumulateurs ou de resolve de clip.
Les chaînes de fold réellement déclarées conservent leurs contrôles de
pairage. V/I/U, readback, couleur/resolve, stencil scène et masques restent
distincts ; limites mémoire, aliasing, cleanup et refus sont préservés.

## Ordre d'exécution et preuves publiques

Premier lot borné : ajouter inverse-only alpha128 hard/AA, puis corriger
uniquement la paire. Même rectangle inverse (2,2)-(6,6), Surface8×8 transparente :
trou transparent, extérieur alpha128 après une application. Deux applications
source-over donneraient environ192, donc un oracle arithmétique indépendant
doit distinguer ces résultats sans valeur attendue dérivée du GPU ni epsilon
nouveau. Tous les tests gardent pixels, Render/Readback, zéro refus et répétition.
Les six cibles à rendre vertes sont inverse-only2 + alpha2 + clip2.
Les deux cas mixtes restent explicitement rouges à l'ancien garde à ce stade.

Second lot : autorité d'inventaire, puis les huit témoins doivent passer.
Ajouter les seuls cas publics nécessaires : sibling avant/après pour l'ordre
et les clears, Picture réel en mémoire et sérialisé, refus public existant
avec sentinel intact puis discard/recovery sur la même Surface.
Les domaines finis et transforms doivent être explicites ; les bounds du
Picture ne sont pas supposées former un clip implicite.

Les étapes intermédiaires restent locales tant que des nouveaux tests positifs
de ce chantier sont rouges. Aucun commit/publication « vert » ne masque
les deux refus mixtes. Le plan d'implémentation précis fixera la garde des
commits et des revues par étape avant les modifications produit.

## Contraintes et qualification

Aucun test d'infrastructure/mock/source-text/forwarding, skip GPU, fallback CPU,
Picture factice ou routage par GM. Géométrie et calcul numérique restent dans
math avec nomenclature I/F32/64. Pas de changement de CompositionEnvelope,
proofs, epsilon, caps/budgets, références/seuils,631/443, scopes ou exclusions.

Le contrôleur exécute seul Gradle/native, en groupes privés bornés240s,
une exécution à la fois jusqu'au terminal réel. Sous-agents d'implémentation
adaptés, Sol en revue, Astra en support architectural/final ciblé.
Après correction : couverture native des routes affectées, globale avec
attribution explicite de ses échecs/timeout, corpus631/443 au SHA final,
stabilité des invariants/anciens rendus, images nouvelles inspectées et
revue indépendante avant la prochaine draft empilée. Pas de merge automatique.

Le dernier corpus publié reste207rendus/184comparés à e9da0ebd6.
W7, globale rouge/incomplète, gates W6/W0 et autres dettes restent ouverts.

## Auto-relecture

Le problème, l'autorité d'émission, les responsabilités des deux phases,
la polarité du stencil et le resolve sont explicites. Les deux lots isolent
recettes de commande et inventaire, sans sauter leurs tests. Aucun gain GM
supplémentaire ni comportement Picture/alpha non exécuté n'est promis.
