# W7 — source AA racine dans une frame W6

## Intention et périmètre

Après #2417 (`54781716e`), réemployer la source couleur AA W4d pour un Path
racine d'une frame déjà possédée par W6, sans inventer une layer racine.
Le pilotage et les arbitrages sont délégués par l'utilisateur ; le présent
contrat borne le prochain lot, avec avis stratégique Astra avant exécution.
Ce prérequis ne promet pas un GM gagné : `PlusMergesAA` nécessite aussi `PLUS`,
et `blur2rects` une couverture filtrée, deux travaux distincts.

## Choix d'architecture

Conserver une source MSAA 4× isolée par occurrence, son resolve couleur 1×,
puis le composite `SrcOver` immédiat vers `LogicalTarget`. Le root emprunté
reste 1×. Ses pixels précédents sont chargés, jamais effacés par l'isolat.
Les autres options — root MSAA global ou layer synthétique — changeraient
l'entrelacement et les sémantiques de restore ; elles ne sont pas retenues.

La source et son exécution native existent : `PlanW4dAaSourceBindingV1` scelle
extent/origin/resources sans exiger de scope, et `W6aLayerGraphValidation`
accepte déjà `LogicalTarget` comme destination du composite adjacent.
L'extension concerne trois sites : sélection de l'occurrence racine,
construction/remapping de ses passes, puis origine de son binding natif.

Admission nouvelle : Path `ANTIALIASED`, solid fill `SrcOver`, sans filtre,
effet ou path effect, accepté par `acceptsW6AaColorSourceScope`, scope nul,
et frame `!ownsW6b`. Ce dernier garde ne s'applique qu'à la nouvelle route
racine, jamais aux sources de layer déjà admises. C'est une limite provisoire
de couverture d'interactions, pas une impossibilité de mélanger les siblings.
L'ownership W6 ne change pas. Les autres routes et diagnostics restent tels quels.

À la racine, le rebind conserve les coordonnées device déjà préparées
(`mapping=null`, domaine=null), sans réappliquer la CTM. La source résolue
et le composite couvrent le viewport entier, origine destination `(0,0)`.
Chaque occurrence garde ses IDs et allocations, ses loads scellés, son
resolve consommé une fois et `versions[root]` incrémenté. Aucun
`RenderChildren` ni scope fictif n'est ajouté pour root. La branche layer
conserve son mapping, son domaine et ses métadonnées.

Le binding natif d'une source AA root reçoit explicitement origine `(0,0)` :
son target est `MultisampleColorTarget`, dont l'ordinal ne désigne pas une layer.
Ne pas passer ce target au fallback `targetOriginDevice` réservé aux cibles
root/layer. Les inventaires, seals, formats, resolve et contrôles d'autorité
existants restent exacts, sans exception ad hoc.

## Preuve publique et coût

Oracles indépendants, fixés avant Surface : ordre root/layer dans les deux
sens ; source alpha 128 composée une fois sur noir (188/188/188/255) ;
translation/clip hard et layer AA à origine non nulle ; deux sources stencil
avec trous/exterieur ; budget exact B/B−1 avec sentinel et récupération.
Chaque succès exige Render/Readback, sans convertir un refus capability en PASS.
Le second `Surface.render()` prouve un second rendu des opérations retenues,
pas le replay Picture avec clip. Le cas alpha sans clip inclut aussi Picture.

Fixture budget : Surface 7×7, triangle root `(1,1)-(5,1)-(1,5)`, et
`saveLayer()/restore()` vide pour l'ownership W6. Cette layer est réellement
allouée : root 196 + readback 1792 + layer 196 + AA4 784 + resolve 196 +
V/I/U 16384/4096/4096 + uniform W6 16 + matériau solid 16 = **B 27 772**.
À B−1 : refus `w6a.layer.frame_budget_exceeded`, buffer intact, puis rendu
hard sur la même Surface après discard. Ne pas apprendre B du planner.

Risques : une origine incorrecte peut déplacer les pixels silencieusement ;
un resolve partagé peut effacer un sibling ; un mauvais ordre peut perdre le
parent ; une sous-charge mémoire invaliderait B. Le viewport entier par source
est conservateur et coûteux ; aucun alias/reuse ni optimisation de bounds ici.

## Contraintes globales

- Fonts, codecs, external decoding et `jpg-color-cube` restent hors périmètre.
- Ne modifier ni fixture GM, adaptateur GM, référence, seuil, exclusion, budget cap, enveloppe numérique ou contrôle d'autorité pour faire passer un cas.
- Aucun nouveau test d'infrastructure : pixels publics Surface, Render/Readback, second rendu, refus/sentinel et récupération uniquement.
- AA filtré, PLUS/destination-read, root dans une frame W6b, clips complexes et retrait du legacy restent hors de cette admission.
- Les types géométriques appartiennent à math avec nomenclature I/F32/64 ; aucun nouveau type géométrique n'est nécessaire ici.
- Un seul processus Gradle/GPU à la fois, terminal réel avant handoff ; tous les échecs et warnings restent rapportés.
- Mesurer les mêmes 631 identités avec timeout 30 s ; ne pas présenter un diagnostic déplacé comme un gain de rendu.
- Suite globale connue rouge/incomplète : publication draft empilée sur #2417 seulement, sans merge ni clôture W7.

## Avis Astra intégré

Le diagnostic initial surestimait le risque hard : W4d exclut déjà HARD_EDGE
dans ce contrat AA. Il omettait les 196 octets de layer vide et le fallback
d'origine du binding natif. Ces trois points sont corrigés dans ce design.
L'admission root sans W6b est retenue pour livrer un prérequis vérifiable,
avant toute extension aux filtres ou à `PLUS`.
