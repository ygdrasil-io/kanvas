# État consolidé — topologie de paths F64/F32

Date de consolidation : 2026-09-01
Périmètre : `:math:geometry`, `PathF32` et `PathOpsF32`

## État du jalon

La vague W1 est **partiellement livrée**. Elle comprend l'immuabilité de
`PathF32`, la topologie source F64, l'arrangement hybride F64/F32 et une
admission conservative. Cette livraison stabilise un sous-ensemble prouvé des
opérations de paths ; elle ne clôt ni toute la vague W1 ni l'objectif de parité
Skia quasi isopixel.

La baseline W00 reste la référence de vérité pour le renderer. Sa gate stricte
n'est pas atteinte à cause de la quarantaine temporaire de `jpg-color-cube` ;
la présente livraison ne modifie ni les GMs, ni les scores, ni les exclusions,
ni les domaines `font` et `codec`.

## Décisions et faits porteurs

- `PathF32` est une valeur immuable ; les opérations publiques préservent les
  entrées, qu'elles réussissent ou rejettent.
- La provenance, les paramètres, les intersections et les prédicats restent
  en F64 dans la topologie source. L'embedding et l'écriture publique utilisent
  les représentants F32 déjà prouvés ; aucune compaction finale ni corde de
  remplacement n'est autorisée.
- L'arrangement hybride unique conserve les directions source F64 pour l'ordre
  angulaire, les witnesses exacts pour la provenance et les représentants F32
  pour les sommets et la sortie. Les overlaps exacts, contacts n-way exacts et
  les relations locales endpoint-only explicitement prouvées restent admis.
- Les routes larges étudiées pendant Task 3 — `full-cover`, équivalence de
  contacts deferred, algèbre des incidences collapsed et physical strict-interior
  cuts — sont supersédées par l'admission conservative. Elles ne constituent
  plus une autorité sur le chemin public.

## Graphe d'appel public et frontière fail-closed

Les trois entrées publiques `PathOpsF32.op`, `PathOpsF32.simplify` et
`PathOpsF32.asWinding` suivent la même route :

```text
PathF32
  -> validation des entrées et limites
  -> budget candidat, puis source capability gate
  -> topologie source exacte F64
  -> observation projetée immutable
  -> projection capability gate
       Unsupported -> IllegalStateException("path-f32-projection-collapse")
       Accepted    -> plan exact accepté
  -> arrangement hybride F64/F32
  -> trace de frontière et writer
  -> PathF32
```

Le source gate s'exécute avant le flattening et la planification proxy. Il
rejette les primitives courbes self-closed non dégénérées dupliquées. Le
projection gate s'exécute avant aliases, cuts physiques, DCEL ou sortie ; il
n'admet que les événements source exacts et les contacts projetés locaux,
endpoint-only, directement soutenus par un witness exact et des identités de
sommets source existantes.

Une relation deferred ou non prouvée, un strict-interior cut, une incidence
collapsed, une autorité `full-cover`/cross-operand, un overlap projeté non
couvert par un overlap F64 exact, ou une ambiguïté d'ownership, d'orientation
ou de comptage est rejeté après le scan d'admission. Aucun état partiel n'est
publié et aucun fallback legacy n'est appelé.

La priorité observable est : entrée ou limite invalide, `path-candidate-limit`,
`path-f32-projection-collapse`, limite d'intersections source, puis limites
structurelles finales. Les coûts d'observation et d'admission sont préflightés
en I64 ; `maxIntersections` ne compte que les événements canoniques source.

## Vérification disponible

- Les tests ciblés `PathOpsHybridTopologyF32Test` sont verts sur JVM (20
  tâches Gradle) et JS (53 tâches Gradle).
- La vérification complète
  `rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks`
  est verte avec 61 tâches Gradle actionnables.
- Les contrôles publics couvrent notamment opérations rectangulaires, crossings
  et overlaps exacts, contacts n-way, signed zero, permutations d'opérandes,
  immuabilité, contacts endpoint-only admis, limites et rejets conservatifs.
- Les revues de spécification et de qualité indépendantes ont le verdict PASS ;
  leurs commandes de vérification et leurs constats sont conservés dans les
  rapports finaux.
- `git diff --check` était propre pendant la livraison conservative.

## Risques et suite

Le domaine accepté est délibérément incomplet : une opération hors preuve est
prévisiblement rejetée, ce qui limite le taux de succès mais protège la
topologie et le déterminisme JVM/JS. Il n'existe pas encore de fixture publique
qui atteigne physiquement un strict-interior projected cut ; la branche est
néanmoins rejetée fail-closed sans test interne synthétique.

Les classes de preuve futures doivent être réintroduites séparément, avec leur
autorité source exacte, obligation de preuve immutable, comptage canonique,
disposition de collapse sélection-aware et tests publics. Les premières pistes
sont les strict-interior projected cuts ; `full-cover`, l'équivalence
cross-operand et l'algèbre collapsed restent des classes distinctes. Le
remplacement du writer de Task 4 et l'oracle global de budget indépendant de
Task 5 ne sont pas livrés par cette stabilisation.
