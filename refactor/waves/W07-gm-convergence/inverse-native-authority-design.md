# W7 — autorité native des scènes inverses

## Objectif et état

Rendre correctement un path inverse seul puis mélangé à des dessins ordinaires,
sans affaiblir les preuves du graphe ni les contrôles natifs. Cette conception
fait suite au [diagnostic mesuré](inverse-scene-inventory-plan.md), sur la base
publiée #2430. La conception initiale et ses extensions ont été implémentées
au SHA source `c61786ab5b1aff0717d6f9f0de7727a1cbd438cd` ; la correction finale
de provenance d'origine décrite plus bas est committée en `38c75ab12` et
acceptée par la contre-revue ciblée Sol. Le status distingue
les résultats exécutés des étapes encore non qualifiées.

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

## Extension mesurée après le premier gate d'inventaire

Le gate initial9/9 puis la composition14/16 prouvent le progrès root, ordre,
alpha, Picture hard et refus/recovery. Les deux Picture AA rencontrent une
source occurrence W4e actuellement hard-only ; le compositeur AA différé
existant n'admet que la source W4d non inverse. Le contrat source→W6 manque,
pas seulement un booléen d'admission. La suite doit former une capacité
verticale distincte : source de couverture inverse AA W4e graph-issued,
binding W6 spécifique et somme fermée avec la variante W4d inchangée,
recette native producer/cover4×, resolve de couverture puis matériau une
seule fois via le compositeur différé existant. Pas de migration W5b AA,
de réutilisation d'autorité root dans un autre graphe ni de Picture aplati
artificiellement. Admission initiale : InverseDomain.Geometry, solide SrcOver,
scissor rectangulaire hard et mapping affine déjà prouvé ; autres familles
non implicitement admises. Des témoins fractional-edge/alpha128 et deux
occurrences, mémoire/sérialisées, devront distinguer AA et hard.

Qualification numérique précisée avec Astra : le test alpha128 utilise un
clip hard sélectionnant C0/C128 sur clear exact ; l'oracle SrcOver existant
borne alors RGBA sans changer sa précision. Les cas opaques imbriqués
portent séparément C1, les trous et deux destinations/rejeux distincts.
L'alpha128 fractionnel sur destination opaque reste une conjonction non
qualifiée par ces fixtures, dont l'oracle initial était non borné. Elle est
suivie comme limite de preuve, sans prétendre le rendu impossible ou acquis.

Le covering découvre séparément un abort134 : AA inverse avec clip path
hard emploie un pipeline maskedPath sans D24S8 alors que la passe l'attache.
La recette ne prouve pas non plus le complément de géométrie source ; ajouter
seulement un format stencil ne suffit pas. Avant l'extension Picture, refuser
de façon typée pré-submit la seule recette InverseMask.Geometry AA directe
sans support natif authentifié. Le témoin public4097 existant autorisait déjà
un refus ; le préciser avec sentinel/discard/recovery, sans changer le budget
scan-span ni rejeter l'inverse-domain AA fonctionnel. Son rendu positif reste
un chantier séparé, pas un gain de parité de ce correctif. Provenance du crash
historique non établie. L'avis Astra détaillé reste archivé avec les preuves.

Décision de pilotage sous carte blanche : stage d'inventaire revu séparément,
puis sécurisation du crash, puis extension Picture par plan dédié avant son
implémentation. Les deux nouveaux tests Picture AA restent rouges et locaux
jusqu'à cette extension ; aucun commit/publication produit-test avant leur
passage. Ni seuils ni tests ne sont assouplis pour fermer le lot.

## Correction finale d'origine — revue Astra du 2 octobre 2026

La revue de branche identifie une contradiction sur une Picture inverse-AA
dans un plain saveLayer borné à origine non nulle : la source raster impose
une origine locale zéro, le binding l'assimile à l'origine device du target.
Ce cas appartient à la composition ordinaire, non aux filtres exclus.
Sous la délégation W7, le contrôleur choisit de le corriger plutôt que de
réduire l'admission à root-only. Un nouveau témoin mémoire/sérialisé opaque,
edges entiers et tous pixels doit établir le RED avant les changements.

Distinguer le repère raster target-local et le repère device du propriétaire
W6. Émettre la correspondance depuis le mapping/domaine/extent/origine
authentifiés ; conserver son propriétaire/target exact dans le binding,
la recette, le compositeur et la capture prepared. Revalider cette même
preuve aux frontières natives. La géométrie, le domaine et le scissor locaux
ainsi que tous bytes/slices source-final restent canoniquement identiques :
ni double translation ni suppression simple du require. Les helpers de
mapping dans math prouvent la relation, aucun calcul numérique nouveau dans
gpu-plan. Les six ressources, budgets, blanc canonique et ordre restent scellés.

Une seule vague de corrections regroupe aussi l'immutabilité des listes
d'opérations/opérandes, l'ordre producer→cover, la recette val après validation
et l'index root partagé sans fusionner les admissions pair/safety distinctes.
Qualification fraîche au SHA corrigé avant la draft ; les gates locaux et
la contre-revue ci-dessous ne remplacent pas ces dernières exécutions.

Le gate de correction distingue aussi trois domaines : le raster-source local,
le clip inverse local (sous-ensemble), et le target/composite complet. Exiger
l'égalité du clip avec le target provoquait dix refus sur les anciens témoins ;
ces refus intermédiaires sont archivés, sans changer leurs oracles. Le témoin
math prouve exactement le rebase device→raster et l'inclusion dans le target ;
le binding conserve la géométrie/clip canonique et vérifie l'inclusion du clip.
L'owner, son origine et son mapping restent exacts jusqu'au prepared/native.
Au candidat figé a66c1d8a4, les40 témoins natifs passent ; les303 cas voisins
donnent302PASS/1NoOpW5b déjà connu ;788 événements math geometry/matrix passent
et le commonMain matrix compile en JavaScript. Ces gates locaux ne remplacent
ni la qualification globale/corpus du SHA corrigé. Sol a vérifié I1/M1/M2/M3
et le delta complet : tous ADDRESSED, aucune rupture nouvelle établie. Le
gate affecté frais au SHA committé38c75ab12 conserve342PASS/1NoOp connu sur343,
sans skip/abort/timeout. Qualification finale38c75ab12 :788 événements math PASS,
globale725cas/687PASS37FAIL1SKIP bornée et incomplète, corpus631/443 inchangé
à207rendus184comparés et zéro gain/perte. La draft reste distincte du merge/W7.

## Auto-relecture

Le problème, l'autorité d'émission, les responsabilités des deux phases,
la polarité du stencil et le resolve sont explicites. Les deux lots isolent
recettes de commande et inventaire, sans sauter leurs tests. Aucun gain GM
supplémentaire ni comportement Picture/alpha non exécuté n'est promis.
