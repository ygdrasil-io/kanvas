# W6b — amendement `PictureStreamAggregateV1`

Date : 2026-09-22

Autorités parentes :

- [`2026-09-16-w6-layers-effects-design.md`](2026-09-16-w6-layers-effects-design.md)
- [`2026-09-22-w6b-w6e-stacked-delivery-design.md`](2026-09-22-w6b-w6e-stacked-delivery-design.md)

Branche : `codex/w6b-blur-masks-shadows`

## 1. Problème découvert

Le planning W6b sait représenter un draw filtré et une `Picture` contenant des
draws filtrés. Il ne peut pas encore représenter fidèlement une `Picture`
filtrée qui contient elle-même une `Picture` filtrée.

Traiter le parent comme une feuille laisse les occurrences descendantes sans
consommateur. Aplatir seulement les descendants filtrés supprime les siblings
non filtrés et perd l'ordre. Rejouer tout le `SceneSnapshot` pour chaque
descendant duplique les siblings. Refuser ce cas serait sûr, mais laisserait
W6b incomplet et reporterait une frontière structurante à Task 3.

La décision retenue est donc d'ajouter un agrégat de command stream typé,
backend-neutral et gelé par `:gpu-plan` avant toute allocation native.

## 2. Décision

`PictureStreamAggregateV1` représente une occurrence de draw `Picture` comme
une source immuable produite par l'exécution ordonnée de tous ses enfants.

L'agrégat :

- possède une identité d'occurrence distincte de l'identité canonique de la
  `Picture` capturée ;
- référence le `SceneSnapshot` capturé sans le recopier ni le modifier ;
- conserve le paint, le transform, le clip, le cull et la chaîne de Pictures
  extérieures de l'occurrence ;
- contient toutes les commandes enfants, filtrées ou non, exactement une fois
  et dans l'ordre source ;
- produit une seule ressource `PictureAggregateSource` scellée ;
- applique ensuite le mask filter, le matériau W5, l'image filter, le clip et
  le blend du parent exactement une fois ;
- peut être imbriqué dans un autre agrégat sans aplatir ni inverser l'ordre.

Ce contrat concerne le draw public d'une `Picture`. Il est distinct de la
famille `ImageFilter.Picture`, qui reste réservée à W6d.

## 3. Contrat capturé et contrat planifié

Aucune nouvelle donnée wire n'est nécessaire. Picture 14/schema 8 continue de
capturer le `SceneSnapshot`, ses commandes, ses paints et sa table de filtres.
`PictureStreamAggregateV1` est une vue planifiée d'une occurrence capturée ; il
n'est ni sérialisé dans Picture, ni exposé par l'API publique.

Le plan introduit des IDs entiers typés, sans géométrie privée :

```kotlin
@JvmInline
public value class PictureStreamAggregateIdI32(public val valueI32: Int)

@JvmInline
public value class PictureStreamEntryIdI32(public val valueI32: Int)
```

Un agrégat possède au minimum :

```text
PictureStreamAggregateV1
  idI32
  sourceSceneCanonicalId
  sourcePictureOccurrenceIdI32
  outerPicturePathI32[]
  mappingF64
  clip/cull capturés
  parentTargetId
  aggregateTargetId
  entries[]
  bounds: knownContent / desiredOutput / requiredInput / producedOutput
```

Les `entries` forment une séquence immuable et monotone :

```text
DrawEntry       -> coverage brute, lane matériau W5, filtre éventuel, composite
PictureEntry    -> référence vers un PictureStreamAggregateV1 enfant
LayerEntry      -> scope W6a enfant et son composite terminal
```

Une entry ne contient jamais un objet public `Picture`, `Paint`, `ImageFilter`
ou `MaskFilter`. Elle référence uniquement les snapshots `:render-ir`, les
mappings `:math`, les source lanes W5 et les ressources/pass IDs `:gpu-plan`.

## 4. Ordre d'exécution gelé

Pour chaque agrégat, le `RenderGraph` encode :

1. création logique de `PictureAggregateSource` en transparent black ;
2. exécution des entries dans l'ordre source ;
3. pour une entry filtrée : coverage → mask → matériau W5 → image filter →
   composite dans `PictureAggregateSource` ;
4. pour une entry non filtrée : source W5 puis composite direct dans
   `PictureAggregateSource` ;
5. pour une `PictureEntry` : exécution complète et scellement de l'agrégat
   enfant, puis composite unique de sa source dans l'agrégat parent ;
6. scellement de `PictureAggregateSource` ;
7. application du paint du draw Picture parent, dont ses propres mask/image
   filters, puis composite unique dans la cible de son scope.

Le filtre du parent commence uniquement après le scellement de tous ses
enfants. Un enfant filtré composite avant son sibling suivant. Aucun résultat
descendant ne peut écrire directement dans la cible du grand-parent.

Les passes planifiées sont typées :

- `PictureAggregateBegin` initialise la cible logique ;
- `PictureAggregateChildComposite` consomme exactement le terminal d'une entry ;
- `PictureAggregateSeal` rend la source immuable pour le filtre/paint parent ;
- le `FilterComposite` existant composite le résultat final du parent.

Le renderer ne reconstruit ni la séquence, ni la hiérarchie, ni les bounds.
Task 3 matérialise exactement ces passes gelées.

## 5. Transforms, clips et matériaux

La chaîne `outerPicturePathI32[]` n'est pas une provenance décorative. Le plan
compose, dans `:math`, chaque transform et clip extérieur avec le mapping de
l'entry avant de construire :

- sa coverage brute ;
- ses bounds F64 puis leur projection outward I32 ;
- ses coordonnées de matériau W5 ;
- son clip final dans la cible d'agrégat.

Les source lanes et `MaterialPlanRef` restent publiés par l'unique autorité
`MaterialSourceConstructionV4` / `FrameSourceLayoutV4`. Un mask shader de
Picture ou de descendant reçoit un vrai material row et sa vraie ressource
uniforme. Aucun canonical ID, `MaterialPlanRef(0)` ou offset zéro ne remplace
une publication W5.

Le paint du draw Picture extérieur est consommé après la source agrégée. Il ne
peut pas être propagé dans chaque enfant, ce qui doublerait alpha, color filter,
mask filter, image filter ou blend.

## 6. Bounds, ressources et budget

Chaque entry conserve les quatre régions W6 : `knownContent`,
`desiredOutput`, `requiredInput` et `producedOutput`. L'agrégat calcule :

- `knownContent` par union ordonnée des outputs enfants réellement initialisés ;
- `desiredOutput` depuis le clip/cull de l'occurrence parent ;
- `requiredInput` par propagation inverse à travers le filtre du parent ;
- `producedOutput` depuis l'union enfant puis le paint/filter parent.

Les bounds physiques de `PictureAggregateSource` sont scellées en F64 puis
projetées outward en I32 par `:math`. Elles ne sont pas remplacées par l'étendue
du parent et ne tronquent pas un blur ou une shadow.

Le budget I64 pessimiste inclut :

- la cible de chaque agrégat actif ;
- les sources coverage/W5 et FilterTargets de ses entries ;
- les agrégats enfants jusqu'à leur composite terminal ;
- les uniforms, matériaux, LUTs, samplers et leases associés.

Un agrégat enfant peut libérer ses ressources après son composite si les
lifetimes le prouvent, mais un cache hit ne réduit jamais le coût d'admission.

## 7. Validation et diagnostics

La publication du graph refuse avant allocation :

- ID d'agrégat ou d'entry absent/dupliqué ;
- cycle de Pictures ou profondeur/nombre de commandes hors limites ;
- ordre non monotone ou entry non représentée ;
- terminal d'enfant absent, consommé deux fois ou composite après le sibling
  suivant ;
- filtre parent démarré avant `PictureAggregateSeal` ;
- mapping/clip non fini ou projection I32 impossible ;
- source lane W5, material row ou uniforme manquant ;
- ressource/pass/binding physique ciblant une autre source que le pass
  sémantique correspondant ;
- budget I64 overflow ou supérieur à la limite.

Après ownership W6b, ces refus sont terminaux et ne tombent jamais en legacy.
Les exits natifs 133/134 restent `UNKNOWN` sans preuve indépendante.

## 8. Répartition des tâches

- **Task 2** gèle `PictureStreamAggregateV1`, les entries ordonnées, les
  mappings/bounds, les source/material lanes, les ressources/passes, le witness
  et le budget. Elle conserve `w6b.filter.native_execution_unimplemented` à la
  frontière native.
- **Task 3** matérialise les passes d'agrégat et les blur image déjà gelés. Elle
  ne redécouvre ni enfants, ni ordre, ni transforms/clips, ni bounds.
- **Tasks 4–5** matérialisent les opérations mask gelées sur les coverage lanes
  des entries ; elles ne changent pas la hiérarchie Picture.
- **Task 6** matérialise les shadows et vérifie budget/atomicité sur la même
  structure.
- **W6d `ImageFilter.Picture`** réutilise une source Picture immuable, mais ne
  remplace pas le contrat de draw Picture défini ici.

## 9. Preuves requises

Les tests publics utilisent uniquement `Surface`, `Canvas`, `Picture`, Render
et Readback :

1. parent Picture filtré contenant un enfant Picture filtré ;
2. enfants filtrés et non filtrés alternés dans une même Picture ;
3. deux Pictures imbriquées siblings dont l'ordre produit des pixels distincts ;
4. transforms et clips extérieurs discriminants ;
5. paint/filter du parent appliqué exactement une fois ;
6. replay Picture mémoire et wire ;
7. refus terminal, sentinelle intacte et recovery sur la même `Surface` tant
   que la matérialisation positive n'est pas livrée.

Les tests de contrat vérifient aussi cycle, ordre, terminal unique, seal avant
filtre parent, source/material manquante et égalité target sémantique/native.
Ils n'utilisent ni reflection, ni mock/fake device, ni compteur, ni inspection
statique de source, ni test d'infrastructure.

## 10. Alternatives rejetées

### Exclusion transitoire

Un diagnostic terminal serait sûr mais reporterait une frontière indispensable
à Task 3 et rendrait W6b incomplet. Cette option n'est pas retenue.

### Aplatissement global

Aplatir toutes les commandes dans le parent complique les scopes, duplique les
paints extérieurs et rend les lifetimes Picture implicites. Cette option est
rejetée.

### Replay opaque dans le renderer

Transmettre seulement un `SceneSnapshot` et demander au renderer de le rejouer
recréerait un planner tardif sans bounds ni budget authentifiés. Cette option
est interdite par l'autorité W6.

## 11. Critères d'acceptation de l'amendement

L'amendement est fermé lorsque :

1. toute commande enfant apparaît exactement une fois et dans l'ordre source ;
2. chaque agrégat enfant est scellé puis composité avant le sibling suivant ;
3. le filtre/paint parent consomme uniquement la source agrégée scellée ;
4. transforms, clips et matériaux incluent toute la chaîne extérieure ;
5. les quatre régions restent distinctes jusqu'au native ;
6. graph, layout physique et budget sont gelés avant allocation ;
7. Task 3 peut matérialiser sans parcourir de `SceneSnapshot` ni recalculer une
   décision ;
8. les preuves publiques et contractuelles de la section 9 sont présentes ;
9. aucune route legacy ou autorité matériau concurrente n'est introduite.
