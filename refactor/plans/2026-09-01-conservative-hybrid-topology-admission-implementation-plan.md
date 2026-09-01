# Conservative Hybrid Topology Admission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:subagent-driven-development` to implement this plan task by task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabiliser `PathOpsF32` rapidement en n'autorisant que les topologies
F64/F32 déjà entièrement prouvées et en rejetant de façon déterministe toute
projection ambiguë par `path-f32-projection-collapse`.

**Architecture:** Deux capability gates (portes de capacité) fail-closed sont
ajoutés au pipeline existant. Le premier détecte les primitives courbes
self-closed dupliquées avant le split et la compression proxy. Le second fige
une observation immutable après la découverte projetée, puis n'admet que les
événements F64 exacts et les relations endpoint-only locales dont les deux
bounds possèdent déjà une `PathVertexIdentityF64`. Le DCEL hybride existant ne
reçoit que le plan accepté; les collapsed incidences, deferred contacts,
physical strict cuts et règles `full-cover` restent dans le code historique si
nécessaire, mais deviennent inaccessibles depuis le chemin public.

**Tech Stack:** Kotlin 2.x Multiplatform, `commonMain`/`commonTest`, module
`:math:geometry`, types `Point2F32`/`Point2F64`, topologie F64 existante, Gradle
JVM/JS.

**Spec:**
[`refactor/specs/2026-09-01-conservative-hybrid-topology-admission-design.md`](../specs/2026-09-01-conservative-hybrid-topology-admission-design.md)

## Global Constraints

- Font et codec sont hors périmètre.
- Toute géométrie nouvelle reste dans `:math:geometry`.
- Les noms numériques nouveaux utilisent les suffixes `I32`, `I64`, `F32` ou
  `F64`; aucun type géométrique générique sans précision numérique n'est créé.
- Les API publiques `PathF32`, `PathOpsF32`, `PathAnalysisF32` et
  `PathMeasureF32` ne changent pas.
- Les tests nouveaux ou modifiés sont uniquement publics, comportementaux,
  géométriques ou numériques. Aucun test d'infrastructure, de source-shape,
  d'import, de package, de visibilité ou de collection privée n'est autorisé.
- Aucun nom de GM, raccourci d'égalité de `PathF32`, seuil de distance, AABB ou
  liste de fixtures ne participe à une décision d'admission.
- Les GMs déjà exclus restent exclus; cette stabilisation ne modifie ni le
  dashboard ni son dénominateur.
- Les erreurs gardent cette priorité publique : entrée/limite invalide,
  `path-candidate-limit`, `path-f32-projection-collapse`,
  `path-intersection-limit`, puis limites structurelles finales.
- Une topologie rejetée est entièrement observée sous le budget candidat avant
  de rendre `Unsupported`; aucune publication partielle d'alias, cut, vertex,
  half-edge, face ou sortie n'est permise.
- Le nombre `maxIntersections` ne contient que les événements canoniques du
  source topology. Une relation endpoint-only déjà portée par des identités
  exactes, un joint de flattening ou un membre proxy n'ajoute pas d'événement.
- Les commandes shell sont préfixées par `rtk`; les éditions manuelles utilisent
  `apply_patch`.
- Le code historique des strict cuts/full-cover peut rester physiquement en
  place pour limiter le risque. Il ne doit plus être appelé par le chemin
  public admis et ne doit pas recevoir de nouvelle autorité.

## Bounded agent and review protocol

Cette livraison contient **une seule task d'implémentation** : les Steps 1 à 8.
Le Step 9 appartient exclusivement au parent et aux reviewers Sol.

1. Dispatcher un agent Terra pour exécuter les Steps 1 à 8 en TDD et produire
   les commits et le rapport demandés.
2. Une fois la vérification complète verte, dispatcher un agent Sol en lecture
   seule pour la review de conformité à la spec.
3. Après `Spec: PASS`, dispatcher un autre agent Sol en lecture seule pour la
   review qualité.
4. Une review en échec autorise au maximum deux corrections. Par choix explicite
   de ce plan borné, et conformément à la demande utilisateur d'agents adaptés,
   chaque correction est confiée à un **nouvel** agent Terra plutôt que de
   réutiliser l'implementer initial, puis repasse les deux gates Sol.
5. Un troisième échec arrête la boucle. Le parent écrit le ruling dans
   `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/ruling.md`;
   aucune nouvelle règle d'autorité ou exception de fixture n'est ajoutée.

Les reviewers ne modifient aucun fichier. Le parent transcrit leurs conclusions
dans les documents de review. L'implementer ne review pas son propre code.

Avant tout dispatch, le parent commit ce plan avec
`docs(refactor): plan conservative topology admission` et vérifie que
`rtk git status --short` est vide. Le commit `790bb74dc` reste la baseline fixe
de design; le range `790bb74dc..HEAD` inclut donc le plan et l'implémentation.

## File Map

**Create**

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt`
  — source capability gate, projection observation et accepted plan.
- `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/implementation-report.md`
  — périmètre livré, commandes et couverture publique.
- `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/spec-review.md`
  — verdict du premier reviewer Sol, écrit par le parent.
- `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/quality-review.md`
  — verdict du second reviewer Sol, écrit par le parent.

**Modify**

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
  — orchestration du source gate avant flatten/split.
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
  — possibilité de différer le gate `maxIntersections` pour le pipeline public.
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
  — construction du source topology avec le comptage canonique différé.
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
  — observation immutable, admission, route accepted-only et déconnexion des
  branches strict-cut/full-cover/collapse.
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
  — matrices publiques de succès, rejet, limites, permutations et immutabilité.

**Do not modify**

- `PathArrangementF64F32.kt`, sauf si une production guard minimale est
  indispensable après preuve par un test public. Une telle modification doit
  uniquement rejeter un état interdit avec `path-f32-projection-collapse`.
- Les modules font, codec et intégration Skia.
- Les listes d'exclusion GM et les fichiers de scores/renders.

---

## Task 1 — Implement the conservative admission boundary

### Interfaces to deliver

`PathHybridAdmissionF64F32.kt` porte les types suivants. Les payloads privés de
clé peuvent être des sealed data classes distinctes pour quad, cubic et arc,
mais aucun `Any`, tableau non typé ou string sérialisée ne sert de clé.

```kotlin
internal data class PathOperandInputF32(
    val operand: PathOperand,
    val pathF32: PathF32,
)

internal sealed interface PathSourceAdmissionF64F32 {
    data object Accepted : PathSourceAdmissionF64F32
    data object Unsupported : PathSourceAdmissionF64F32
}

internal fun admitPathSourcePrimitivesF64F32(
    inputsF32: List<PathOperandInputF32>,
    normalizationF64: PathNormalizationF64,
    candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
): PathSourceAdmissionF64F32

internal data class PathHybridProjectionObservationF64F32(
    val exactContactWitnessesF64: List<PathContactWitnessF64>,
    val endpointOnlyProjectedRelationsF64F32:
        List<PathProjectedCoincidenceProposalF64F32>,
    val deferredEndpointContactsF64F32:
        List<PathDeferredProjectedEndpointObservationF64F32>,
    val strictInteriorCutRequirementCountI32: Int,
    val collapsedIncidencesF64F32: List<PathCollapsedIncidenceF64F32>,
    val operandLocalCollapsedSectionCountI32: Int,
    val unsupportedProjectedContactCountI32: Int,
    val canonicalSourceEventCountI32: Int,
)

internal data class PathDeferredProjectedEndpointObservationF64F32(
    val firstSourceSpanIdI64: Long,
    val firstSourceSectionIndexI32: Int,
    val firstParameterF64: Double,
    val secondSourceSpanIdI64: Long,
    val secondSourceSectionIndexI32: Int,
    val secondParameterF64: Double,
)

internal sealed interface PathHybridAdmissionF64F32 {
    data class Accepted(
        val exactPlanF64F32: PathAcceptedExactPlanF64F32,
    ) : PathHybridAdmissionF64F32

    data object Unsupported : PathHybridAdmissionF64F32
}

internal class PathAcceptedExactPlanF64F32 private constructor(
    internal val endpointOnlyProjectedRelationsF64F32:
        List<PathProjectedCoincidenceProposalF64F32>,
    internal val canonicalSourceEventCountI32: Int,
) {
    internal companion object {
        fun fromValidatedRelationsF64F32(
            committedEndpointOnlyProjectedRelationsF64F32:
                List<PathProjectedCoincidenceProposalF64F32>,
            canonicalSourceEventCountI32: Int,
            candidateWorkBudgetI32: PathCandidateWorkBudgetI32,
        ): PathHybridAdmissionF64F32
    }
}
```

Pour que ces signatures compilent sans encapsuler une closure mutable,
promouvoir de `private` à `internal` uniquement :

- `PathProjectedSourceSpanF64F32`;
- `PathProjectedCoincidenceProposalF64F32`.

Ils restent internes au module; aucune API publique n'est ajoutée.

### Step 1 — Establish the public RED contract for duplicate self-closed curves

**Files:**

- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

- [ ] Renommer le helper actuel pour distinguer le contrôle supporté `count=1`
  de la nouvelle expectation de rejet `count>=2`.

- [ ] Ajouter ce helper public-only; ne pas inspecter le nombre de carriers ni
  l'activation du proxy :

```kotlin
private fun assertDuplicateSelfClosedCarrierRejectedF32(
    separateContours: Boolean,
    operation: PathBooleanOp,
    swapOperands: Boolean,
    limitsI32: PathOpsLimitsI32 = PathOpsLimitsI32(),
) {
    val carriersF32 = repeatedEqualSelfClosedCarrierPathF32(
        countI32 = 2,
        separateContours = separateContours,
    )
    val firstF32 = if (swapOperands) equalSelfClosedCarrierClipF32 else carriersF32
    val secondF32 = if (swapOperands) carriersF32 else equalSelfClosedCarrierClipF32
    val firstBeforeF32 = firstF32.toList()
    val secondBeforeF32 = secondF32.toList()

    val error = assertFailsWith<IllegalStateException> {
        PathOpsF32.op(firstF32, secondF32, operation, limitsI32)
    }

    assertEquals("path-f32-projection-collapse", error.message)
    assertEquals(firstBeforeF32, firstF32.toList())
    assertEquals(secondBeforeF32, secondF32.toList())
}
```

- [ ] Conserver les dix contrôles `n1 compact/separate × five ops` qui doivent
  réussir. Remplacer les cas `n2` et `n3`, ainsi que la frontière artificielle
  `216`, par la matrice `compact/separate × five ops × operand order` qui doit
  rejeter. `n=2` suffit à prouver la duplication; ne pas multiplier les mêmes
  cas pour `n=3`.

- [ ] Ajouter un test de priorité budget : la même duplication avec
  `maxCandidateProbes = 1` rend `path-candidate-limit`, tandis que les limites
  par défaut rendent `path-f32-projection-collapse`.

- [ ] Exécuter et confirmer le RED sur la réussite historique des duplications :

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

Expected RED: au moins un cas `count=2` ne lève pas
`path-f32-projection-collapse`. Ne pas accepter un échec de compilation ou une
autre erreur comme preuve RED.

### Step 2 — Implement the source capability gate before flattening and proxy planning

**Files:**

- Create:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt`
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`

- [ ] Déplacer `PathOperandInputF32` dans le nouveau fichier, le rendre
  `internal`, renommer `path` en `pathF32` et ajuster les usages dans
  `PathOpsF32.kt`.

- [ ] Scanner les commandes immutables de chaque `PathF32` en conservant
  `currentPointF32`, `contourStartPointF32`, `contourIndexI32` et
  `sourceSegmentIndexI32`. Une primitive candidate est une `QuadTo`,
  `CubicTo` ou `ArcTo` non dégénérée dont l'endpoint est exactement le point de
  départ topologique. `LineTo` et `Close` ne sont jamais des courbes candidates.

- [ ] Définir « non dégénérée » sans epsilon : pour une self-closed `QuadTo`,
  le control normalisé diffère du start; pour une self-closed `CubicTo`, au
  moins un control normalisé diffère du start; pour `ArcTo`,
  `arcCenterF64(ArcEndpointF64(...))` doit produire un centre fini et une
  paramétrisation non nulle. Une arc SVG self-closed que le convertisseur
  classe comme ligne/point dégénéré n'entre pas dans le gate.

- [ ] Construire une clé F64 par kind, endpoints, controls et paramètres d'arc
  après `normalizationF64`. Canonicaliser `-0.0` en `0.0` uniquement pour la
  comparaison topologique. Pour la direction inverse : garder le control de la
  quadratic, inverser `control1/control2` de la cubic et inverser `sweep` de
  l'arc. Retenir lexicographiquement la plus petite forme forward/reversed.

- [ ] Trier les observations par cette clé sous un coût de tri déterministe
  `sizeI64 * ceilLog2(sizeI64)` calculé avec
  `checkedPathWorkMultiplyI64`. Ce worst-case préflight est l'unique débit des
  comparaisons de tri; ne jamais débiter depuis le comparator JVM/JS. Débiter
  séparément les scans et copies avant l'action. Ne pas rendre dès le premier
  doublon : terminer le scan borné, puis rendre `Unsupported`.

- [ ] Revalider deux clés adjacentes component-by-component avec égalité F64
  topologique avant de déclarer le doublon. Operand, contour et segment restent
  dans le payload de provenance et sont exclus de l'égalité géométrique.
  Rejeter seulement si les deux membres sont eux-mêmes des primitives courbes
  non dégénérées et self-closed complètes. Les lignes, les courbes ouvertes et
  leurs exact overlaps inter-opérandes restent hors de ce gate; l'exception
  conservative concerne uniquement les self-closed curves dupliquées.

- [ ] Dans `buildHybridArrangementF64F32`, appeler le gate immédiatement avant
  `inputEdgesF64` :

```kotlin
when (
    admitPathSourcePrimitivesF64F32(
        inputsF32 = inputs,
        normalizationF64 = normalization,
        candidateWorkBudgetI32 = candidateWorkBudget,
    )
) {
    PathSourceAdmissionF64F32.Accepted -> Unit
    PathSourceAdmissionF64F32.Unsupported ->
        throw IllegalStateException("path-f32-projection-collapse")
}
val preparedEdgesF64 = inputEdgesF64(inputs, normalization, limits, candidateWorkBudget)
```

- [ ] Exécuter les tests JVM puis JS ciblés :

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

- [ ] Commit checkpoint :

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt
rtk git commit -m "fix(math): reject duplicate self closed topology"
```

### Step 3 — Establish the public RED contract for conservative projection

**Files:**

- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

- [ ] Transformer les expectations historiques suivantes en rejets exacts,
  avec vérification que les inputs ne changent pas :

  - `even odd repeated tiny lobes simplify to an empty path`;
  - `identical collapsed loops XOR to an empty public path`;
  - toutes les branches de
    `under threshold collapsed loop drops both alone and inside a retained face`;
  - le `distantIntersectF32` de
    `public intersect rejects jointly selected collapsed sibling...`;
  - `C DIFFERENCE C` pour le même collapsed loop, direct et reversed.

- [ ] Garder verts les contrôles `representable self closed cubic keeps a normal
  public boundary`, exact overlaps, exact n-way contacts, signed-zero et
  rectangle operations.

- [ ] Ajouter deux appels publics `PathOpsF32.asWinding` : un polygon ordinaire
  doit réussir, tandis qu'un single collapsed loop et un path contenant deux
  self-closed curves identiques doivent chacun rendre exactement
  `path-f32-projection-collapse`. Vérifier l'immutabilité du path dans les trois
  cas.

- [ ] Ajouter une fixture publique `thinLensWithDistantSelfClosedPrimitiveF32`
  composée des deux contours quadratic suivants, puis d'un cubic self-closed
  distant. Elle doit rejeter pour `simplify`, `C UNION C` et `C INTERSECT C` :

```kotlin
private fun thinLensWithDistantSelfClosedPrimitiveF32(): PathF32 {
    val eF32 = 2.0.pow(-23).toFloat()
    return PathBuilder()
        .moveTo(0f, 1f - eF32)
        .quadTo(.5f, 4f, 1f, 1f - eF32)
        .lineTo(2f, -1f)
        .lineTo(0f, -1f)
        .close()
        .moveTo(0f, 1f + eF32)
        .quadTo(.5f, 4f, 1f, 1f + eF32)
        .lineTo(2f, 3f)
        .lineTo(0f, 3f)
        .close()
        .moveTo(10f, 10f)
        .cubicTo(11f, 10f, 10f, 11f, 10f, 10f)
        .close()
        .build()
}
```

- [ ] Ajouter aussi un rectangle tiers
  `RectF32.ofLTRB(20f, 20f, 21f, 21f)` et vérifier que
  `UNION(C, rectangle)` et `UNION(rectangle, C)` rejettent. Ces deux variantes
  atteignent le projection gate sans dupliquer le cubic self-closed entre les
  operands; elles constituent la preuve publique contre une autorité
  distante/transitive.

- [ ] Modifier le test de limite du projected endpoint-only local : conserver
  son succès, mais tester `maxIntersections = 8` ->
  `path-intersection-limit` et `maxIntersections = 9` -> succès. Les neuf
  événements sont source-authoritative; la relation projetée sans cut ne crée
  plus le dixième événement public.

- [ ] Ajouter deux tests de priorité :

  - thin lens avec `maxCandidateProbes = 1` -> `path-candidate-limit`, avec
    limites par défaut -> `path-f32-projection-collapse`;
  - thin lens avec `maxIntersections = 1` et budget candidat suffisant ->
    `path-f32-projection-collapse`, donc le gate de projection précède le gate
    d'intersections.

- [ ] Exécuter le JVM ciblé et confirmer le RED sur les anciennes réussites
  collapsed et/ou sur la frontière projetée `10` :

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

Expected RED: sortie vide historique, résultat distant ou frontière `10`; une
erreur privée telle que `path-arrangement-inconsistent` n'est pas la bonne
preuve.

### Step 4 — Discover an immutable projection observation and admit fail-closed

**Files:**

- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt`
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`

- [ ] Promouvoir uniquement les deux types privés listés dans « Interfaces to
  deliver », puis implémenter `PathHybridProjectionObservationF64F32` et
  `PathAcceptedExactPlanF64F32`.

- [ ] Dans `processProjectedSpanContactF64F32`, remplacer chaque rejet immédiat
  lié à la capability par une observation locale :

  - point projected sans exact witness et non endpoint -> incrémenter
    `unsupportedProjectedContactCountI32`;
  - overlap sans exact overlap complet et sans local point witness valide ->
    incrémenter le même compteur;
  - claim impossible -> incrémenter le compteur, sans publier de proposal;
  - endpoint non résolu -> conserver une
    `PathDeferredProjectedEndpointObservationF64F32` structurelle; ne pas
    construire le legacy `PathDeferredProjectedEndpointContactF64F32` et ne
    pas calculer son ancien booléen `hasCompleteExactOppositeComponentF64`;
  - claim valide mais endpoint identity `null` -> compter une exigence de
    strict-interior cut.

  Continuer le candidate scan après chaque finding afin que le budget ait la
  priorité. Les erreurs d'invariant exact déjà corrompu restent des erreurs;
  elles ne sont pas converties en admission.

- [ ] Après le projected broad phase et **avant toute copie/allocation de
  l'observation**, calculer avec checked I64 puis débiter le coût des copies de
  witnesses, proposals, deferred observations et collapsed incidences. Créer
  seulement ensuite les copies immutables et le compteur :

```kotlin
val observationF64F32 = PathHybridProjectionObservationF64F32(
    exactContactWitnessesF64 = sourceTopologyF64.contactWitnessesF64.toList(),
    endpointOnlyProjectedRelationsF64F32 = proposalsF64F32.toList(),
    deferredEndpointContactsF64F32 = deferredEndpointContactsF64F32.toList(),
    strictInteriorCutRequirementCountI32 = strictInteriorCutRequirementCountI32,
    collapsedIncidencesF64F32 = collapsedIncidencesF64F32.toList(),
    operandLocalCollapsedSectionCountI32 =
        sourceTopologyF64.operandLocalCollapsedSectionsF64F32.size,
    unsupportedProjectedContactCountI32 = unsupportedProjectedContactCountI32,
    canonicalSourceEventCountI32 = sourceTopologyF64.intersectionEventCountI32,
)
```

- [ ] Avant de lire le premier flag d'échec, calculer avec checked I64 et
  débiter le coût fixe de la validation complète : witnesses, proposals,
  deferred contacts, collapsed incidences et quatre endpoints par proposal.
  Parcourir ensuite toute l'observation en accumulant un booléen
  `unsupportedF64F32`; ne jamais retourner `Unsupported` depuis le milieu de
  la boucle. Ainsi un budget insuffisant reste prioritaire sur le rejet.

- [ ] Pour chaque relation endpoint-only, valider pendant cette passe : witness
  ID identique sur les deux claims, deux spans distincts, quatre endpoint
  identities non null, intervalles finis et ordonnés, witness présent dans
  `exactContactWitnessesF64`. Si la passe trouve un élément interdit, construire
  `PathHybridAdmissionF64F32.Unsupported` uniquement après la fin du scan.

- [ ] Si et seulement si le scan est supporté, appeler
  `validateAndOrderProjectedCoincidenceTransactionsF64F32` **avant** de
  construire `PathHybridAdmissionF64F32.Accepted`. Cette validation reçoit les
  indexes exacts et vertices déjà découverts et produit les committed
  relations. Toute erreur de claim/transaction rend donc
  `path-f32-projection-collapse` avant `maxIntersections`.

- [ ] Avant cet appel, calculer sans parcourir les collections une borne haute
  du coût transactionnel restant. Pour `P` proposals, `S` source spans et `W`
  exact witnesses, utiliser la formule conservative suivante, uniquement avec
  les helpers checked I64 :

```kotlin
private fun projectedTransactionValidationUpperBoundI64F32(
    proposalCountI32: Int,
    sourceSpanCountI32: Int,
    witnessCountI32: Int,
): Long {
    val proposalCountI64 = proposalCountI32.toLong()
    val sourceSpanCountI64 = sourceSpanCountI32.toLong()
    val witnessCountI64 = witnessCountI32.toLong()
    val sideCountI64 = checkedPathWorkMultiplyI64(proposalCountI64, 2L)
    return checkedPathWorkAddI64(
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(proposalCountI64, 128L),
            checkedPathWorkMultiplyI64(sideCountI64, sourceSpanCountI64),
        ),
        checkedPathWorkAddI64(
            checkedPathWorkMultiplyI64(
                checkedPathWorkMultiplyI64(proposalCountI64, 4L),
                witnessCountI64,
            ),
            checkedPathWorkAddI64(
                deterministicSortCostI64F32(proposalCountI32),
                checkedPathWorkAddI64(
                    checkedPathWorkMultiplyI64(
                        deterministicSortCostI64F32(
                            checkedPathCapacityI32(sideCountI64, "path-candidate-limit"),
                        ),
                        2L,
                    ),
                    4L,
                ),
            ),
        ),
    )
}
```

  Cette borne couvre les trois sorts, toutes les validations par transaction,
  les deux sides par proposal, le maximum `S` des memberships d'un witness et
  les deux listes de witnesses de taille maximale `W` pour chaque joint. Avant
  la validation, appeler :

```kotlin
candidateWorkBudgetI32.requireRemainingAtLeast(
    projectedTransactionValidationUpperBoundI64F32(
        proposalCountI32 = observationF64F32.endpointOnlyProjectedRelationsF64F32.size,
        sourceSpanCountI32 = sourceTopologyF64.sourceSpansF64.size,
        witnessCountI32 = sourceTopologyF64.contactWitnessesF64.size,
    ),
)
```

  `requireRemainingAtLeast` est un check sans débit. Les helpers existants
  continuent de débiter chaque opération réelle. Si le travail complet aurait
  épuisé le ledger, `path-candidate-limit` précède donc tout finding
  transactionnel; sinon une transaction invalide peut rejeter sans masquer une
  future exhaustion. Ne pas ajouter de débit depuis un comparator.

- [ ] Appeler ensuite `fromValidatedRelationsF64F32` avec les committed
  relations. Cette factory préflight la copie finale puis crée le plan privé;
  elle n'accepte jamais les proposals brutes et retourne directement
  `PathHybridAdmissionF64F32.Accepted(plan)`. Le builder ne construit jamais
  `Accepted` lui-même.

- [ ] Convertir `Unsupported` en
  `IllegalStateException("path-f32-projection-collapse")` avant tout appel à
  `materializeProjectedClaimPlanF64F32`,
  `validateDeferredEndpointContactsF64F32`,
  `propagateProjectedCutPartitionsF64F32` ou construction d'alias.

- [ ] Ajouter une production guard immédiatement après `Accepted` et avant
  toute construction d'alias : si un accepted plan contient encore un endpoint
  identity null, rendre uniquement
  `path-f32-projection-collapse`. Les collapsed incidences et deferred
  relations ne sont pas transportées par le plan et doivent déjà avoir bloqué
  sa construction.

### Step 5 — Defer `maxIntersections` until after admission

**Files:**

- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`

- [ ] Étendre le splitter avec un défaut compatible pour ses autres callers :

```kotlin
internal fun splitPathTopologyF64(
    edges: List<PathInputEdgeF64>,
    limits: PathOpsLimitsI32,
    candidateWorkBudget: PathCandidateWorkBudgetI32,
    allowSelfClosedNWayCarrierGroupingF64: Boolean = false,
    enforceIntersectionLimit: Boolean = true,
): PathSplitTopologyF64
```

- [ ] Protéger le gate existant :

```kotlin
if (enforceIntersectionLimit && components.size > limits.maxIntersections) {
    throw IllegalStateException("path-intersection-limit")
}
```

- [ ] Dans `splitPathSourceTopologyF64`, passer
  `enforceIntersectionLimit = false`. Le candidate budget reste la borne de la
  construction transitoire; ne supprimer aucun checked capacity.

- [ ] Après `Accepted` et avant aliases/DCEL, débiter une comparaison puis
  appliquer uniquement le compteur source :

```kotlin
candidateWorkBudgetI32.consume()
if (exactPlanF64F32.canonicalSourceEventCountI32 > limitsI32.maxIntersections) {
    throw IllegalStateException("path-intersection-limit")
}
```

- [ ] Retirer du chemin admis l'addition
  `newCanonicalCutGroupCountI64` au compteur public. Les endpoint-only
  relations admises ne possèdent aucun strict cut et ne consomment donc pas
  `maxIntersections`.

### Step 6 — Route only the accepted exact plan to the existing arrangement

**Files:**

- Modify:
  `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`

- [ ] Ne plus construire
  `completeExactOppositeContourComponentsF64F32` dans le chemin public. Un
  deferred contact est toujours `Unsupported`; `full-cover` ne peut plus
  l'absoudre. Le nouveau deferred observation n'a pas de champ full-cover; le
  legacy `PathDeferredProjectedEndpointContactF64F32` et ses validateurs restent
  uniquement dans le code déconnecté.

- [ ] Pour `Accepted`, passer seulement les relations déjà committed de
  `exactPlanF64F32.endpointOnlyProjectedRelationsF64F32` à
  `assignProjectedCoincidencesF64F32`. Ne pas relancer la validation après le
  gate `maxIntersections`.

- [ ] Ne pas appeler les fonctions suivantes sur le chemin admis :

  - `materializeProjectedClaimPlanF64F32`;
  - `validateDeferredEndpointContactsF64F32`;
  - `propagateProjectedCutPartitionsF64F32`;
  - `materializeProjectedSourceSpansF64F32`;
  - `buildMaterializedHybridCarrierTopologyF64F32`.

- [ ] Construire le `PathHybridTopologyF64F32` à partir des spans, vertices et
  carrier sections découverts avant admission, avec
  `collapsedIncidencesF64F32 = emptyList()` et les aliases issus uniquement des
  `projectedCoincidencesF32` endpoint-only. Le plan accepté garantit que la
  liste operand-local collapsed est vide; publier également
  `operandLocalCollapsedSectionsF64F32 = emptyList()`.

- [ ] Laisser les fonctions complexes déconnectées en place. Ne pas lancer un
  nettoyage massif de plusieurs milliers de lignes dans cette stabilisation.

- [ ] Exécuter les tests ciblés sur les deux backends :

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

- [ ] Commit checkpoint :

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt
rtk git commit -m "fix(math): admit only proved projected topology"
```

### Step 7 — Complete the public supported/rejected matrix

**Files:**

- Modify:
  `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

- [ ] Vérifier ou compléter les assertions publiques suivantes sans dupliquer
  les tests déjà présents dans `PathOpsF32Test` :

  - five boolean ops sur rectangles ordinaires;
  - exact point crossings, exact overlaps, exact n-way junctions et événements
    disjoints;
  - endpoint-only local projected relation sous operand swap;
  - cyclic contour order, reversal, translation et scale;
  - signed-zero raw payload et égalité JVM/JS;
  - limites adjacentes source `maxIntersections`, `maxVertices` et
    `maxHalfEdges`;
  - immutabilité des deux operands sur succès et rejet.

- [ ] Exécuter explicitement les success/rejection tests `asWinding` du Step 3;
  l'entrée unary traverse les deux mêmes gates que `simplify`.

- [ ] Pour les rejected families, utiliser un helper commun qui exige
  exactement `path-f32-projection-collapse`; ne jamais accepter « any
  exception ». Couvrir thin lens, duplicate self-closed, collapsed
  XOR/Difference, significant/partial collapsed sibling et distant collapsed
  operation.

- [ ] Nommer et commenter explicitement les deux preuves d'autorité négative :

  - `public quadratic coincidence without the adjacent source witness rejects`
    couvre la relation unowned et le witness distant/transitif;
  - `thin lens overlapping projected claims reject atomically` utilise la
    fixture du Step 3, teste `simplify`, puis `UNION` avec un troisième rectangle
    distant dans les deux operand orders; il couvre les rails projetés qui se
    recouvrent sans autorité exacte sans dépendre du source gate `C op C`.

- [ ] Ne pas créer de test interne synthétique pour physical strict-interior
  cuts. Si aucune fixture `PathF32` publique ne l'atteint, inscrire explicitement
  cette seule lacune dans le rapport; elle n'empêche pas la stabilisation
  puisque la production guard rejette cet état. Les overlapping/unowned claims
  ne bénéficient pas de cette exception et restent couverts par les deux tests
  publics précédents.

- [ ] Exécuter toute la classe JVM/JS une nouvelle fois, puis les tests publics
  de base :

```bash
rtk ./gradlew :math:geometry:jvmTest \
  --tests '*PathOpsF32Test*' \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest \
  --tests '*PathOpsF32Test*' \
  --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
```

### Step 8 — Document and verify the stabilization

**Files:**

- Create:
  `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/implementation-report.md`

- [ ] Écrire le rapport avec exactement ces sections : `Baseline`, `Delivered
  gates`, `Supported public families`, `Stable rejected families`, `Limits and
  precedence`, `Public coverage gap`, `Verification`, `GM accounting`.

- [ ] Dans `Public coverage gap`, noter que le strict-interior projected cut
  n'a pas de fixture publique si cela reste vrai; ne pas annoncer cette preuve
  comme couverte.

- [ ] Dans `GM accounting`, rappeler : rendered = opération admise et sortie
  produite; excluded = GM hors dénominateur convenu; topology rejected = rejet
  volontaire du capability domain. Confirmer qu'aucun GM n'a été ajouté ou
  retiré par cette task.

- [ ] Exécuter la vérification complète fraîche :

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
rtk git diff --check
rtk git status --short
```

- [ ] Lire les sorties, pas uniquement les exit codes. Le rapport doit donner
  le nombre de tasks Gradle exécutées et les deux résultats JVM/JS. Le status
  avant commit ne doit contenir que les fichiers de cette task.

- [ ] Commit final de l'implementer :

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridAdmissionF64F32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt \
  math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt \
  math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt \
  refactor/progress/2026-09-01-conservative-hybrid-topology-admission/implementation-report.md
rtk git commit -m "docs(refactor): record conservative topology admission"
```

### Step 9 — Parent-only: run the two independent Sol gates

- [ ] Spec reviewer Sol, read-only, compare `790bb74dc..HEAD` against
  `refactor/specs/2026-09-01-conservative-hybrid-topology-admission-design.md`.
  Il vérifie notamment que source rejection précède proxy planning, projection
  rejection précède aliases/cuts/DCEL, collapsed incidences rejettent toujours,
  et `maxIntersections` reste source-only.

- [ ] Le parent écrit le verdict dans
  `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/spec-review.md`.
  Le fichier doit contenir `Verdict: PASS` ou `Verdict: FAIL`, le range reviewé,
  les commandes vérifiées et les findings classés Critical/Important/Minor.

- [ ] Seulement après `Spec: PASS`, quality reviewer Sol, read-only, inspecte le
  même range et cherche : early exit non débité, mutation avant admission,
  erreur privée exposée, ordre dépendant JVM/JS, faux positif de clé primitive,
  route historique encore callable et test d'infrastructure.

- [ ] Le parent écrit le verdict dans
  `refactor/progress/2026-09-01-conservative-hybrid-topology-admission/quality-review.md`.

- [ ] Si les deux verdicts sont `PASS`, exécuter une dernière fois :

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
rtk git diff --check
rtk git status --short
```

- [ ] Commit des seuls documents de review :

```bash
rtk git add refactor/progress/2026-09-01-conservative-hybrid-topology-admission/spec-review.md \
  refactor/progress/2026-09-01-conservative-hybrid-topology-admission/quality-review.md
rtk git commit -m "docs(refactor): review conservative topology admission"
```

## Completion Criteria

La task est terminée uniquement si :

- les deux capability gates sont actifs sur `op`, `simplify` et `asWinding`;
- les topologies admises ne passent par aucune branche deferred/full-cover,
  strict-cut ou collapsed;
- les erreurs et leur priorité correspondent à la spec;
- les tests publics ciblés et les suites complètes JVM/JS sont verts;
- aucun module font/codec, GM, render, dashboard ou exclusion n'a changé;
- `git diff --check` est vert et le worktree est propre;
- les reviews Sol spec et quality sont toutes deux `PASS` dans la limite de
  deux corrections.

Cette livraison doit être décrite comme une stabilisation fonctionnelle à
domaine réduit, jamais comme la parité Skia ISO finale.
