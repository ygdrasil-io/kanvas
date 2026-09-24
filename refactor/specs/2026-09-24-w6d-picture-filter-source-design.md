# W6d — source Picture possédée par un filtre

## Intention et portée

`ImageFilter.Picture` doit produire les pixels d'une `SceneSnapshot` capturée,
immuable et bornée, dans le **même** `RenderGraph` W6 que les autres filtres.
Le renderer reçoit un graphe entièrement ordonné et figé ; il ne rejoue pas une
`Picture`, ne découvre pas de commandes et ne crée pas de passes après le plan.
Cette note amende uniquement la frontière physique de la famille Picture dans
le plan W6d. Elle ne change ni le contrat du draw `GeometryNode.Picture`, ni les
onze familles promises, ni les exclusions fonts, codecs et GMs.

La contrainte initiale « nouveaux arms `FilterPassOperationV1` et
`FilterTarget` seulement » est insuffisante : le nœud filtre détient une scène
de commandes qui doit être rendue et scellée avant le `FilterPass.Picture`.
Le contrat amendé autorise **pour cette seule famille** la réutilisation de
`PictureAggregateSource` et des passes Picture déjà existantes dans le graphe
W6b. Il n'autorise aucun nouveau type de `PlanPass`, rôle de ressource, graphe,
allocateur, voie de submit ou planner dans le renderer. La sortie du filtre
reste un `FilterTarget`.

## Choix et alternatives

Le choix retenu est un agrégat Picture isolé, possédé par une évaluation de
filtre, suivi d'un `FilterPass.Picture` qui échantillonne sa source scellée.
Une copie intermédiaire vers `FilterSource` ajouterait une texture, une passe,
un budget et une seconde génération sans résoudre le besoin d'un propriétaire
et d'un ordre de commandes. Un replay de `SceneSnapshot` dans le renderer
déplacerait la planification après freeze. Un refus terminal de la famille
reste sûr pendant la construction, mais ne clôt pas W6d Task 4.

## Propriétaire et identité

`PictureStreamAggregateV1` porte un propriétaire typé :

- `DrawPicture` conserve exactement la provenance `GeometryNode.Picture`, le
  command index, l'occurrence et le composite terminal W6b actuels ;
- `FilterPicture` porte le `CapturedFilterNodeIdI32`, l'identité de la table,
  l'occurrence de filtre, le chemin de scène, le contexte source et l'identité
  canonique de la `SceneSnapshot` capturée. Il ne fabrique aucun `DrawNode`,
  command index public ou `GeometryNode.Picture` fictif.

La découverte d'un `FilterPicture` part du vrai nœud
`CapturedFilterNodeV1.Picture`, pas d'un draw parent. Elle attribue des IDs
uniques dans les allocateurs du frame W6, dans l'ordre de visite figé. Deux
filtres de même valeur conservent deux identités de capture distinctes ; un
nœud explicitement partagé garde sa référence capturée stable. Aucune
déduplication physique par égalité ou canonical ID n'est requise. Les
identités et l'ordre doivent rester reproductibles en replay mémoire et wire.

La découverte traverse aussi les scènes possédées par les filtres Picture,
leurs draws, layers, effets et Picture imbriquées. Elle utilise les mêmes
autorités W4/W5/W6c que les draws Picture existants. Les références absentes,
cycles sur le chemin actif, profondeurs/tailles hors borne et incohérences de
table sont refusés avant publication ; deux sous-arbres égaux indépendants ne
sont pas pris à tort pour un cycle. `CapturedFilterInputV1.Picture(id)` reste
refusé : ce contrat d'entrée réservé n'est pas nécessaire à la valeur
`CapturedFilterNodeV1.Picture`.

## Ordre et validation du graphe

Pour un propriétaire `FilterPicture` non vide, le graphe contient dans cet
ordre :

1. `PictureAggregateBeginPass` ouvre un `PictureAggregateSource` isolé ;
2. les commandes capturées et leurs descendants produisent les passes W4/W5/W6
   normales, en respectant siblings, scopes, clips, transforms et layers ;
3. `PictureAggregateSealPass` fixe la génération RGBA ;
4. un seul `FilterPass.Picture` lit cette génération scellée et écrit son
   `FilterTarget`, avant tout consommateur du résultat du filtre.

L'agrégat possédé par le filtre termine au seal : il ne reçoit ni consommateur
W5 de draw Picture, ni composite synthétique vers le parent, ni paint du draw
porteur. Les validateurs conservent sans relâchement la branche `DrawPicture`
actuelle. La branche `FilterPicture` prouve le propriétaire, l'ordre
Begin/children/Seal/FilterPass, l'ID source, la génération exacte, l'unique
liaison à cette évaluation de filtre et la durée de vie jusqu'au dernier
lecteur. Hors des passes internes de l'agrégat, seul
`FilterPassOperationV1.Picture` peut lire directement ce
`PictureAggregateSource`; les autres arms gardent leurs rôles admis actuels.
Le witness de graphe et la validation de production contrôlent cette relation,
pas seulement une liste de rôles. Les dépendances restent dans l'unique queue
ordonnée du `RenderGraph`.

L'operand figé de `FilterPassOperationV1.Picture` contient l'aggregate ID, le
resource ID, la génération scellée, le domaine/offset d'échantillonnage
target-local et les bounds de sortie. La `SceneSnapshot` reste une provenance
de capture/plan ; elle n'est jamais une recette d'exécution native. Le
materializer lie les passes et ressources prévues, sans replay, replan, resize
ou choix de fallback. `Picture` est une feuille du DAG de filtres : sa seule
entrée physique est la source Picture scellée, jamais l'`ImplicitSource` du
draw ou du layer portant le filtre.

## Sémantique géométrique et contenu vide

`src`, lorsqu'il est présent, est le rectangle cible dans les coordonnées de
la Picture : il découpe la sortie, sans étirement, translation à l'origine ni
resampling. En son absence, le cull de la Picture est le rectangle cible.
Le contenu visible est l'intersection de ce rectangle et du cull. La
transformation du contexte de filtre est appliquée une seule fois en F64 ;
seul `:math` possède les objets géométriques et les projections checked I32,
avec noms précisant I/F et 32/64. La demande du consommateur est propagée à
rebours avant le clip terminal. Le cull borne le contenu Picture, pas le halo
ou les pixels qu'un filtre extérieur peut produire à partir du noir
transparent. Hors du contenu, l'échantillonnage est transparent (decal).

Une scène vide, un cull vide ou une intersection `src ∩ cull` vide produit le
noir transparent, sans texture de dimension zéro ni `Picture` operation aux
bounds illégales. Les wrappers `ColorFilter`, `Compose`, `Blend` et les modes
de composite restent évalués normalement : un filtre extérieur peut créer de
l'alpha et `SRC` peut effacer la destination. Le chemin transparent W6 existant
est utilisé avec les mêmes règles de demande et de budget.

Cette interprétation de `src` suit le `targetRect` de `SkImageFilters::Picture` :
Skia le découpe avec le cull interne et ne définit pas de paire src/dst qui
impliquerait une mise à l'échelle
([contrat public Skia](https://skia.googlesource.com/skia/+/0d16a70c7cbb/include/effects/SkImageFilters.h#370)).

## Ressources, refus et preuves

L'agrégat source et tous ses descendants partagent les allocateurs, IDs,
slots, lifetimes, générations et dépendances du frame W6. Le plan charge en
I64 checked les cibles RGBA8, images, uniforms, samplers, staging, alignements
et leases avant freeze/publication ; un cache hit ne diminue pas le budget
pessimiste. Les leases persistent jusqu'à completion ou quarantine. Toute
erreur de mapping, de cycle, d'ID, de borne, de capacité ou de matérialisation
est terminale pour le frame et ne publie aucun readback partiel. Le même
`Surface` récupère après `discardRecordedOperations()`.

Les preuves sont des tests publics `Surface`/`Picture` avec oracle de pixels
calculé avant la capture et scopes `Render` + `Readback` pour chaque positif :
source indépendante du draw porteur ; ordre `Clear`/`DrawColor`/siblings,
`DST_OUT`, nested Picture et saveLayer ; crop translaté/transformé/vide ; halo
hors cull ; transparent sous `ColorFilter` et `SRC` ; deux valeurs égales mais
distinctes et une référence partagée ; Compose ; replay mémoire/wire et
mutation post-capture ; accounting des descendants, refus tardif atomique et
récupération. La preuve chiffrée B/B−1 reste la gate W6d Task 7. Les
anciens lecteurs Picture non-lighting restent lisibles et les anciens records
2D-lighting restent refusés. Aucun test d'infrastructure, mock, réflexion,
inspection statique ni GM n'est ajouté.

## Handoff

Le plan W6d Task 4 sera découpé en tranches séquentielles : (a) propriétaire,
découverte et validation de source seule, (b) binding et exécution figée,
(c) pixels/replay/pression ressource. Chaque tranche est implémentée par Terra
et relue par Sol avant la suivante. Tasks 5–7, la revue finale et la PR Draft
stackée restent inchangés. Cette note prévaut uniquement sur la restriction
physique W6d « `FilterPass`/`FilterTarget` seulement » pour
`ImageFilter.Picture` ; elle ne modifie pas la sémantique draw Picture W6b.
