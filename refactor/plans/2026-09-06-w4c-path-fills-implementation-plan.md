# W4c Path Fills Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer W4c : des frames de paths solides hard-edge 1× rendues par triangle direct strict ou par paire stencil/cover, avec géométrie préparée dans `:math`, ordre de paint atomique et preuve pixel byte-exact hors GM.

**Architecture:** `:math:geometry` produit les snapshots F32 et les preuves géométriques depuis un flux device-space F64 construit par `:math:matrix`. `:gpu-plan` sélectionne toute la frame, scelle la stratégie de chaque draw ainsi qu'un `RenderGraph` couleur/depth-stencil typé, puis `:gpu-renderer` traduit mécaniquement ce plan vers les pipelines path natifs existants sans rappeler la tessellation legacy. W4c est une lane sœur fermée de W3/W4a/W4b : après `Ready`, toute divergence est terminale et aucun fallback n'est possible.

**Tech Stack:** Kotlin multiplatform JVM/JS, `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGPU4K, Gradle, `rtk`, GitHub CLI.

**Spec:** `refactor/specs/2026-09-05-w4c-path-fills-design.md`

## Global Constraints

- Capability exacte : `solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1`.
- Branche : `codex/w4c-path-fills`, empilée sur `codex/w4b-analytic-rrect`; la PR W4c cible la branche W4b, pas `main`.
- SDD est obligatoire. Un agent Terra implémente une seule tâche à la fois selon TDD (`RED → GREEN → self-review → commit`). Aucun second agent d'implémentation ne travaille en parallèle dans le worktree partagé.
- Après chaque commit Terra, un agent Sol effectue successivement la conformité à la spec puis la quality review. Sol ne modifie et ne committe rien.
- Toute géométrie nouvelle vit dans `:math`; les noms portent leur représentation `I32`, `I64`, `F32` ou `F64`. `:gpu-plan` et `:gpu-renderer` ne créent aucun nouveau type de point, contour, triangle, fan, bounds ou tolérance.
- Le transform axis-aligned est appliqué en F64 dans `:math:matrix`; il est interdit de passer par `Matrix3x3F32.map(PathF32)` avant le flattening W4c.
- Tolérance de flèche device-space : `0.25` px. Limites par défaut : profondeur 32, 65 536 arêtes tentées par path, 262 144 par frame. Chaque tentative est débitée avant émission/dédoublonnage.
- `DirectTriangle` est réservé à un contour `WINDING`, line-only, trois sommets distincts non collinéaires, sans retrace ni auto-intersection. Tout autre path admis utilise `StencilCover`.
- Un `StencilCover` `WINDING` est admis jusqu'à 255 arêtes fermées non nulles émises; 256 est `ResourceLimitExceeded`. `EVEN_ODD` conserve la limite générale de 65 536.
- W4c accepte 1 à 512 draws, exclusivement `GeometryNode.Path`/`DrawOrigin.PATH`, solid, `FILL`, `HARD_EDGE`, `SrcOver`, sRGB 1×, transform identity/scale/translate et clip vide ou scissor entier non-AA.
- Les inverse fills, AA/MSAA, strokes/hairlines, rotation/skew/perspective, clips complexes, images, gradients, shaders, filtres et blends non-`SrcOver` restent hors scope.
- Après `Ready`, le lowerer authentifie mais ne normalise, n'aplatit, ne triangule et ne reclasse jamais. Il ne peut appeler ni `PathTessellator`, ni un mapper/prepared builder legacy.
- Les tests sont comportementaux, publics ou portent sur les invariants de données exposés. Aucun test source-shape, reflection, accès privé, call-count ou autre test d'infrastructure du code.
- Aucun test ou artefact `font`, `codec`, GM Skia, dashboard, baseline ou `jpg-color-cube` n'est exécuté ou modifié. Aucun seuil ou tolérance de comparaison n'est ajouté.
- Les commandes JS utilisent `jsNodeTest` sans `--tests`; les filtres `--tests` sont réservés aux tâches JVM.
- La dette SDF W4b reste inchangée et suivie pour W7; W4c ne la masque ni ne la rebaseline.

---

## Carte de fichiers

| Frontière | Fichiers créés | Fichiers modifiés |
|---|---|---|
| Flux F64 math | `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillInputF64.kt`; `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathFillTransformsF64.kt` | aucun fichier renderer |
| Préparation math | `PathFillLimitsI32.kt`; `PathFillFlatteningPolicyF64.kt`; `PathFillGeometryF32.kt`; `PathFillPreparationF64.kt` | aucun type géométrique hors `:math` |
| Graphe | aucun fichier générique nouveau | `PlanIdentity.kt`; `PlanCapabilities.kt`; `PlanResources.kt`; `PlanPasses.kt`; `RenderGraph.kt` |
| Planner W4c | `PathFillPlanBudget.kt`; `W4cPlanDiagnostics.kt`; `W4cPathFillPlanCompiler.kt` | `GpuPlanCompiler.kt`; `CapabilityCompilerChain.kt` |
| Lowering W4c | `W4cPathFillGraphLowerer.kt`; `GPUPlanW4cPreparedAuthority.kt`; `GPUCorePrimitiveW4cPreparedFrameTaskListAssembler.kt` | `GpuPlanCapabilityAdapter.kt`; `GpuPlanTaskListLowerer.kt`; `GPUCorePrimitivePreparedAuthority.kt`; `RenderPathFanLimits.kt` |
| Native | tests W4c dédiés | branches scellées dans `GPUFramePreflighter.kt`; `PreparedGPUFrame.kt`; `GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`; `GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`; `GPUFrameExecutor.kt` |
| Surface/pixels | `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4cPathFillCpuOracle.kt` | `GpuRenderContext.kt`; `GPUPlanSurfaceCandidateGate.kt`; `GPUPlanSurfacePixelTest.kt`; `GPUPlanSurfaceRouterTest.kt` |
| Suivi | ce plan | `refactor/README.md`; `refactor/waves/W04-geometry-coverage/status.md` |

`gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/PathTessellator.kt` reste inchangé : son plafond 1 024 est legacy. `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderPathFanLimits.kt` est modifié seulement pour qualifier explicitement `MAX_TRIANGLES` et `MAX_GEOMETRY_BYTES` comme limites de cette route legacy; il ne reçoit ni limite W4c ni objet géométrique W4c. Seul `GPUPathSourceAuthority.W4cPlannedPathFillV1`, authentifié par le scratch W4c, peut transporter jusqu'à 65 536 arêtes dans l'ABI natif existant; cette admission vient exclusivement de `PathFillLimitsI32` de `:math`.

## Interfaces partagées

Toutes les tâches conservent les noms et signatures ci-dessous.

```kotlin
public sealed interface PathFillSegmentF64 {
    public data class MoveTo(public val point: Point2F64) : PathFillSegmentF64
    public data class LineTo(public val point: Point2F64) : PathFillSegmentF64
    public data class QuadTo(
        public val control: Point2F64,
        public val point: Point2F64,
    ) : PathFillSegmentF64
    public data class CubicTo(
        public val control1: Point2F64,
        public val control2: Point2F64,
        public val point: Point2F64,
    ) : PathFillSegmentF64
    public data class ArcTo(
        public val radius: Vector2F64,
        public val xAxisRotationDegreesF64: Double,
        public val largeArc: Boolean,
        public val sweep: Boolean,
        public val point: Point2F64,
    ) : PathFillSegmentF64
    public data object Close : PathFillSegmentF64
}

public class PathFillInputF64 private constructor(
    public val fillRule: FillRule,
    segments: Collection<PathFillSegmentF64>,
) : Iterable<PathFillSegmentF64> {
    public val segmentCountI32: Int
    public fun segmentAtI32(indexI32: Int): PathFillSegmentF64
    public companion object {
        public fun fromPathF32(path: PathF32): PathFillInputF64
        public fun of(
            fillRule: FillRule,
            segments: Collection<PathFillSegmentF64>,
        ): PathFillInputF64
    }
}

public fun Matrix3x3F32.mapPathFillInputF64(path: PathF32): PathFillInputF64

public data class PathFillLimitsI32(
    public val maxSubdivisionDepthI32: Int = 32,
    public val maxAttemptedEdgesPerPathI32: Int = 65_536,
    public val maxAttemptedEdgesPerFrameI32: Int = 262_144,
)

public data class PathFillFlatteningPolicyF64(
    public val maximumSagittaErrorF64: Double = 0.25,
    public val limitsI32: PathFillLimitsI32 = PathFillLimitsI32(),
)

public enum class PathFillInvalidSceneReason {
    NonFiniteInput,
    NonFiniteProjection,
}

public enum class PathFillResourceLimitReason {
    FlatteningDidNotConverge,
    PathAttemptedEdgeLimit,
    FrameAttemptedEdgeLimit,
    WindingStencilEdgeLimit,
    RasterBoundsOverflow,
    HostSizeOverflow,
}

public sealed interface PathFillPreparationResult {
    public data class Ready(
        public val geometryF32: PathFillGeometryF32,
        public val attemptedEdgeCountI32: Int,
    ) : PathFillPreparationResult
    public data class Empty(
        public val attemptedEdgeCountI32: Int,
    ) : PathFillPreparationResult
    public data class InvalidScene(
        public val reason: PathFillInvalidSceneReason,
    ) : PathFillPreparationResult
    public data class ResourceLimitExceeded(
        public val reason: PathFillResourceLimitReason,
    ) : PathFillPreparationResult
}

public fun preparePathFillGeometryF32(
    inputF64: PathFillInputF64,
    policyF64: PathFillFlatteningPolicyF64 = PathFillFlatteningPolicyF64(),
    frameAttemptedEdgesBeforeI32: Int = 0,
): PathFillPreparationResult

public class PathFillDirectTriangleF32 internal constructor(
    verticesF32: FloatArray,
    indicesI32: IntArray,
) {
    public val vertexCountI32: Int
    public val indexCountI32: Int
    public fun copyVerticesF32(): FloatArray
    public fun copyIndicesI32(): IntArray
}

public class PathStencilEdgeFanF32 internal constructor(
    verticesF32: FloatArray,
    indicesI32: IntArray,
    contourStartsI32: IntArray,
) {
    public val edgeCountI32: Int
    public val vertexCountI32: Int
    public val indexCountI32: Int
    public fun copyVerticesF32(): FloatArray
    public fun copyIndicesI32(): IntArray
    public fun copyContourStartsI32(): IntArray
}

public class PathFillGeometryF32 internal constructor(/* snapshots */) {
    public val fillRule: FillRule
    public val attemptedEdgeCountI32: Int
    public val emittedNonZeroClosedEdgeCountI32: Int
    public val vertexCostI64: Long
    public val indexCostI64: Long
    public fun copyConservativeScissorI32(): RectI32
    public fun copyDirectTriangleF32OrNull(): PathFillDirectTriangleF32?
    public fun copyStencilEdgeFanF32OrNull(): PathStencilEdgeFanF32?
}
```

```kotlin
public enum class PlanDepthStencilFormat { Depth24PlusStencil8 }

public sealed interface PlanTextureFormat {
    public data class Color(public val value: PlanLogicalColorFormat) : PlanTextureFormat
    public data class DepthStencil(public val value: PlanDepthStencilFormat) : PlanTextureFormat
}

public enum class PlanDepthStencilLoadStore {
    ClearZeroStore,
    LoadStoreTestReset,
}

@JvmInline
public value class PlanAtomicGroupId(public val value: String)

public enum class PathFillStrategy { DirectTriangle, StencilCover }

public class PathFillDraw private constructor(
    override public val commandIndex: Int,
    override public val color: ColorF32,
    geometryF32: PathFillGeometryF32,
    public val strategy: PathFillStrategy,
    scissorI32: RectI32,
) : PlanDraw {
    override public val coverage: CoveragePlan = CoveragePlan.FullOrScissor
    override public val sample: SamplePlan = SamplePlan.SingleSample
    override public val blend: BlendPlan = BlendPlan.SrcOver
    public fun copyGeometryF32(): PathFillGeometryF32
    public fun copyScissorI32(): RectI32
    public companion object {
        public fun of(
            commandIndex: Int,
            color: ColorF32,
            geometryF32: PathFillGeometryF32,
            strategy: PathFillStrategy,
            scissorI32: RectI32,
        ): PathFillDraw
    }
}
```

`PlanPass.StencilProducer` et `PlanPass.StencilCover` transportent chacun `target`, `depthStencil`, le même `PathFillDraw`, `PlanDrawDataResources`, `PlanAtomicGroupId`, le load/store couleur et le contrat depth/stencil exact. Le producer fixe `ClearZeroStore`; le cover fixe `LoadStoreTestReset`. Leur accès depth/stencil est respectivement write et read-write.

```kotlin
public class W4cPathFillPlanCompiler : GpuPlanCompiler {
    override fun select(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): GpuPlanSelection

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>

    public companion object {
        public const val CAPABILITY_ID: String =
            "solid-path-fill-tessellation-stencil-hard-1x-simple-scissor-src-over-srgb-v1"
    }
}
```

---

### Task 1: Flux de path device-space F64 et transform axis-aligned

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillInputF64.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillInputF64Test.kt`
- Create: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathFillTransformsF64.kt`
- Create: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathFillTransformsF64Test.kt`
- Do not modify: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathF32.kt`
- Do not use: `Matrix3x3F32.map(PathF32)` dans la voie W4c

**Interfaces:**

- Consumes: `PathF32`, `PathSegmentF32`, `Matrix3x3F32`, `Point2F64`, `Vector2F64`.
- Produces: `PathFillSegmentF64`, `PathFillInputF64`, `Matrix3x3F32.mapPathFillInputF64(PathF32)` définis dans les interfaces partagées.

- [ ] **Step 1: Écrire les tests RED JVM/JS du snapshot F64**

Créer les tests nommés exactement ci-dessous, avec ces observations publiques :

- `path fill input snapshots every segment in F64` : muter la collection source après `of`, puis vérifier que `segmentAtI32` reste inchangé;
- `drawing verb before MoveTo starts at device origin` : transformer `LineTo(2,1)` et vérifier que le flux conserve l'origine implicite;
- `negative axis scale maps points and flips SVG arc sweep` : appliquer `scale(-2,3)` et vérifier endpoint, rayons, rotation et inversion de `sweep`;
- `F64 transform does not round an intermediate coordinate to F32` : comparer le `Double` brut attendu au résultat qui aurait été arrondi via `map(PathF32)`;
- `non finite matrix or mapped coordinate is rejected` : vérifier `IllegalArgumentException` pour chaque entrée non finie.

- [ ] **Step 2: Vérifier l'échec avant implémentation**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathFillInputF64Test*' :math:matrix:jvmTest --tests '*PathFillTransformsF64Test*'
```

Expected: FAIL à la compilation car `PathFillInputF64` et `mapPathFillInputF64` n'existent pas.

- [ ] **Step 3: Implémenter le snapshot et le mapping F64 minimal**

Implémenter les signatures partagées. Toutes les coordonnées sont reconstruites depuis leurs bits F32 puis calculées en `Double`. Pour `ArcTo`, reprendre l'algèbre de `transformArcMetadata` mais conserver les résultats F64; une déterminant négatif inverse `sweep`. Une source `start == end` reste représentée et sera classée no-op par Task 2.

- [ ] **Step 4: Exécuter les preuves JVM et JS**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathFillInputF64Test*' :math:matrix:jvmTest --tests '*PathFillTransformsF64Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest :math:matrix:jsNodeTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL, mêmes doubles et mêmes payloads F32 d'entrée sur JVM/JS.

- [ ] **Step 5: Self-review et commit Terra**

Vérifier absence de dépendance `:math:geometry → :math:matrix`, puis :

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillInputF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillInputF64Test.kt math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/PathFillTransformsF64.kt math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/PathFillTransformsF64Test.kt
rtk git commit -m "feat(math): add F64 path fill transforms"
```

Le controller soumet ensuite le commit aux deux gates Sol avant Task 2.

---

### Task 2: Préparation géométrique W4c dans `:math`

**Files:**

- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillLimitsI32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillFlatteningPolicyF64.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillGeometryF32.kt`
- Create: `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillPreparationF64.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillGeometryF32Test.kt`
- Create: `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillLimitsI32Test.kt`
- Do not modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/PathTessellator.kt`

**Interfaces:**

- Consumes: `PathFillInputF64` de Task 1.
- Produces: tous les types `PathFill*I32/F32/F64`, le résultat typé et `preparePathFillGeometryF32(...)` définis plus haut.

- [ ] **Step 1: Écrire les tests RED de normalisation et classification**

Les tests couvrent explicitement : origine implicite, `MoveTo` qui clôt le contour précédent, fermeture implicite, `Close` répété, `ArcTo start==end` no-op, quad/cubic fermées non nulles, dédoublonnage F32, suppression des contours `<3` sommets/colinéaires, conservation retrace/auto-intersection, triangle strict et triangle `EVEN_ODD` envoyé au stencil.

Assertions centrales :

```kotlin
val ready = assertIs<PathFillPreparationResult.Ready>(preparePathFillGeometryF32(input))
assertNotNull(ready.geometryF32.copyDirectTriangleF32OrNull())
assertNull(ready.geometryF32.copyStencilEdgeFanF32OrNull())
assertEquals(3, ready.geometryF32.emittedNonZeroClosedEdgeCountI32)
```

et pour un concave :

```kotlin
val fan = assertIs<PathFillPreparationResult.Ready>(result)
    .geometryF32.copyStencilEdgeFanF32OrNull()
assertNotNull(fan)
assertEquals(fan.edgeCountI32 * 3, fan.vertexCountI32)
assertContentEquals(IntArray(fan.indexCountI32) { it }, fan.copyIndicesI32())
```

- [ ] **Step 2: Écrire les tests RED de limites, erreurs et immutabilité**

Utiliser des limites réduites pour prouver `N` accepté / `N+1` refusé sans test lent, puis vérifier les valeurs par défaut séparément :

```kotlin
assertEquals(65_536, PathFillLimitsI32().maxAttemptedEdgesPerPathI32)
assertEquals(262_144, PathFillLimitsI32().maxAttemptedEdgesPerFrameI32)
assertIs<PathFillPreparationResult.ResourceLimitExceeded>(prepareWithPathLimit(3, attemptedEdges = 4))
assertIs<PathFillPreparationResult.ResourceLimitExceeded>(prepareWithFrameTotal(before = 7, limit = 8, attemptedEdges = 2))
```

Ajouter les frontières Winding 255/256, non-fini, non-convergence à profondeur bornée, overflow de raster bounds, mutation des tableaux retournés et parité JVM/JS. La frontière Winding doit vérifier le reason exact, pas seulement le sous-type :

```kotlin
assertEquals(
    PathFillResourceLimitReason.WindingStencilEdgeLimit,
    assertIs<PathFillPreparationResult.ResourceLimitExceeded>(
        prepareWindingStencilPath(edgeCountI32 = 256),
    ).reason,
)
```

Le même helper à 255 arêtes doit retourner `Ready`; il ne doit pas être routé par le proof `DirectTriangle`.

- [ ] **Step 3: Vérifier RED**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathFillGeometryF32Test*' --tests '*PathFillLimitsI32Test*'
```

Expected: FAIL à la compilation sur les nouveaux contrats.

- [ ] **Step 4: Implémenter la préparation transactionnelle**

Implémenter une subdivision adaptative F64 dédiée à W4c. `debitAttempt()` incrémente d'abord les compteurs checked path/frame, puis seulement l'émission est envisagée. Aucun résultat partiel n'est publié en cas d'erreur.

Chaque contour retenu stocke ses sommets F32 sans doublon consécutif. Le fan émet pour chaque arête `(anchor, p[i], p[i+1])`, avec un unique `anchor=(0f,0f)`, trois vertices et trois indices séquentiels par arête; `contourStartsI32` adresse les arêtes sources, pas les floats émis. Les tableaux publics sont toujours des copies.

Le proof direct est créé seulement si le flux source était line-only et si le contour normalisé satisfait exactement les règles de la spec. Sinon, produire le fan stencil. Appliquer la borne Winding 255 après émission; elle ne s'applique ni à `EVEN_ODD`, ni au triangle direct.

- [ ] **Step 5: Exécuter JVM/JS et la suite math existante**

Run:

```bash
rtk ./gradlew :math:geometry:jvmTest --tests '*PathFillGeometryF32Test*' --tests '*PathFillLimitsI32Test*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL; aucun test topology existant ne régresse.

- [ ] **Step 6: Self-review et commit Terra**

```bash
rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillLimitsI32.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillFlatteningPolicyF64.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillGeometryF32.kt math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFillPreparationF64.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillGeometryF32Test.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFillLimitsI32Test.kt
rtk git commit -m "feat(math): prepare bounded path fill geometry"
```

Le controller soumet le commit aux deux gates Sol avant Task 3.

---

### Task 3: Modèle `RenderGraph` couleur/depth-stencil et groupes atomiques

**Files:**

- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4aAnalyticRectGraphLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4aTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4bTest.kt`

**Interfaces:**

- Consumes: `PathFillGeometryF32` de Task 2.
- Produces: `PlanTextureFormat`, `PlanDepthStencilFormat`, `PlanDepthStencilLoadStore`, `PlanAtomicGroupId`, `PathFillDraw`, `PathFillStrategy`, `PlanPass.StencilProducer`, `PlanPass.StencilCover`.

- [ ] **Step 1: Écrire les tests RED du format et de la ressource D24S8**

Vérifier qu'une texture couleur exige `PlanTextureFormat.Color`, que D24S8 exige rôle `DepthStencil` + usage `DepthStencilAttachment`, qu'un buffer refuse tout format, et que le support D24S8 est distinct de `supportedFormats()` couleur.

```kotlin
assertFailsWith<IllegalArgumentException> {
    PlanResource.of(
        PlanResourceRole.DepthStencil, 0, PlanResourceKind.Texture2D,
        PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
        SizeI32(4, 4), 64, setOf(PlanResourceUsage.RenderAttachment),
        PlanResourceLifetime.FrameLocal, 0, 3,
    )
}
```

- [ ] **Step 2: Écrire les tests RED des passes et de l'atomicité**

Construire publiquement un graph `producer → cover → readback` valide, puis des graphes invalides : cover non adjacent, groupe différent, commande différente, depth texture absente, load/store inversé, cover sans accès read-write, dépendance manquante, lifetime D24S8 expirant avant readback, ordre de commandes décroissant.

- [ ] **Step 3: Vérifier RED**

Run:

```bash
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*'
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererTest*' --tests '*GpuPlanTaskListLowererW4aTest*' --tests '*GpuPlanTaskListLowererW4bTest*'
```

Expected: FAIL à la compilation sur les nouveaux formats/passes.

- [ ] **Step 4: Implémenter les types en préservant W3/W4a/W4b**

Remplacer `PlanResource.format: PlanLogicalColorFormat?` par `PlanTextureFormat?`; envelopper tous les call sites W3/W4a/W4b et ceux de `GpuPlanTaskListLowerer` avec `PlanTextureFormat.Color(...)`. Adapter dans cette même tâche les builders et assertions publics W4a/W4b qui construisent ou authentifient des `PlanResource`, afin que Task 3 compile seule sans attendre W4c. Dans `W4aAnalyticRectGraphLowerer.kt` et `W4bAnalyticRRectGraphLowerer.kt`, remplacer l'égalité à `PlanOperationCapability.entries` par l'ensemble historique explicite `{ RenderPass, CopyUpload, UniformBuffer, Readback }`; mettre les fixtures publiques W4a/W4b et le test W3 direct `GpuPlanTaskListLowererTest.kt` au même contrat. Ainsi l'ajout des opérations stencil ne change pas rétroactivement les lanes W3/W4a/W4b. Ajouter à `PlanCapabilitySnapshot.of(...)` un paramètre final `supportedDepthStencilFormats: Set<PlanDepthStencilFormat> = emptySet()` et une copie immuable exposée par `supportedDepthStencilFormats()`.

Ajouter `DepthStencilAttachment` et les opérations `DepthStencilAttachment`/`StencilCover` sans modifier les opérations W3 existantes. Ajouter `AttachmentLoadPlan.Load`.

- [ ] **Step 5: Implémenter la validation atomique dans `RenderGraph.of`**

`referencedResources` doit inclure target, D24S8 et V/I/U pour les deux passes stencil. Une validation unique impose : D24S8 unique et absent des frames direct-only, producer immédiatement suivi du cover de même groupe/draw, `ClearZeroStore` puis `LoadStoreTestReset`, dépendance directe, accès write puis read-write, loads couleur corrects et dépendances linéaires jusqu'au readback.

- [ ] **Step 6: Exécuter les tests gpu-plan complets**

Run:

```bash
rtk ./gradlew :gpu-plan:test --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererTest*' --tests '*GpuPlanTaskListLowererW4aTest*' --tests '*GpuPlanTaskListLowererW4bTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL, contrats W3/W4a/W4b inchangés.

- [ ] **Step 7: Self-review et commit Terra**

```bash
rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanIdentity.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4aAnalyticRectGraphLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4aTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4bTest.kt
rtk git commit -m "feat(gpu-plan): model atomic stencil cover passes"
```

Le controller soumet le commit aux deux gates Sol avant Task 4.

---

### Task 4: Sélection, budget et compilation W4c

**Files:**

- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PathFillPlanBudget.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4cPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4cPathFillPlanCompiler.kt`
- Create: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/PathFillPlanBudgetTest.kt`
- Create: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4cPathFillPlanCompilerTest.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GpuPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt`

**Interfaces:**

- Consumes: Tasks 1–3.
- Produces: `W4cPathFillPlanCompiler`, `PathFillMemoryFootprint`, `PathFillPlanBudgetResult`, diagnostics W4c et `GpuPlanSelection.ResourceLimitExceeded`.

- [ ] **Step 1: Écrire les tests RED du résultat de sélection terminal pré-device**

Étendre `GpuPlanSelection` ainsi :

```kotlin
public class ResourceLimitExceeded(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
    public fun diagnostics(): List<RenderDiagnostic>
}
```

Tester que `CapabilityCompilerChain` et `GpuRenderBackend.plan` propagent ce résultat terminal. Dans `GpuRenderBackendTest.kt`, construire une scène W4c qui dépasse une limite de préparation avant device avec un `GpuRenderContext(ThrowingOwner())`, puis observer publiquement `RenderPlanResult.ResourceLimitExceeded` et son diagnostic W4c. Le `ThrowingOwner` rend le test comportemental : toute acquisition device ferait échouer le test, sans reflection ni call-count. Utiliser des compilateurs suivants dont le résultat public serait un sentinel distinct s'ils devenaient autorité; vérifier que la réponse reste le `ResourceLimitExceeded` W4c.

- [ ] **Step 2: Écrire les tests RED d'admission W4c**

Cas admis : triangle Winding direct; concave Winding stencil; trou EvenOdd; transform négatif; scissor I32; 1 et 512 paths. Cas `NotCandidate` : empty, 513, `TEXT_EXPANDED_PATH`, AA, inverse, stroke, transform non-axis, clip complexe, material/blend hors scope, frame mixte Rect/Path. Cas `InvalidScene` : path/transform/clip non-fini. Cas `ResourceLimitExceeded` : limites math, Winding 256, bounds/host overflow.

Vérifier que la chaîne reste ordonnée `W3 → W4a → W4b → W4c` et que les scènes historiques conservent leur capability.

- [ ] **Step 3: Écrire les tests RED du budget exact**

Définir :

```kotlin
public data class PathFillMemoryFootprint(
    public val targetBytes: Long,
    public val readbackBytesPerRow: Long,
    public val readbackBytes: Long,
    public val vertexUsefulBytes: Long,
    public val indexUsefulBytes: Long,
    public val uniformStrideBytes: Long,
    public val uniformUsefulBytes: Long,
    public val vertexCapacityBytes: Long,
    public val indexCapacityBytes: Long,
    public val uniformCapacityBytes: Long,
    public val depthStencilBytes: Long,
    public val peakBytes: Long,
)
```

Tester : direct `3×8` bytes vertices et `3×4` indices; stencil `edgeCount×3×8 + 4×8` vertices et `edgeCount×3×4 + 6×4` indices; Uniform32 aligné; D24S8 `4×w×h` exactement une fois; direct-only sans D24S8; pool capacities arrondies; overflow et budget ±1.

- [ ] **Step 4: Vérifier RED**

Run:

```bash
rtk ./gradlew :gpu-plan:test --tests '*W4cPathFillPlanCompilerTest*' --tests '*PathFillPlanBudgetTest*' --tests '*CapabilityCompilerChainTest*'
rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderBackendTest*'
```

Expected: FAIL à la compilation.

- [ ] **Step 5: Implémenter le selector atomique**

Le selector copie le `PathF32`, appelle `matrix.mapPathFillInputF64`, puis `preparePathFillGeometryF32` en passant le cumul frame. Le premier draw hors scope rend toute la frame `NotCandidate`; `Empty` rend toute la frame `NotCandidate`; les erreurs typées sont propagées sans candidat partiel. Le candidat privé snapshotte géométrie, couleur, scissor, ordre et fingerprints scène/cible.

- [ ] **Step 6: Implémenter budget et graph**

Le compiler choisit `DirectTriangle` uniquement à partir de `copyDirectTriangleF32OrNull()`. Pour chaque direct, émettre un `RenderPass` coloré à un draw. Pour chaque stencil, émettre deux passes adjacentes avec le même `PlanAtomicGroupId("w4c:$commandIndex")`. La première passe qui touche la couleur clear; toutes les suivantes load/store. Ajouter un readback final et des dépendances entre chaque passe consécutive.

Target/V/I/U vivent jusqu'après readback; staging commence au readback; D24S8 commence au premier producer mais vit physiquement jusqu'après readback. `peakFrameLocalBytes` doit être la somme des capacités physiques réellement déclarées.

- [ ] **Step 7: Exécuter gpu-plan complet**

Run:

```bash
rtk ./gradlew :render-ir:test :gpu-plan:test --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderBackendTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Self-review et commit Terra**

```bash
rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt
rtk git commit -m "feat(gpu-plan): compile bounded W4c path fills"
```

Le controller soumet le commit aux deux gates Sol avant Task 5.

---

### Task 5: Capability adapter, scratch et lowerer W4c fermé

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4cPathFillGraphLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4cPreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4cPreparedFrameTaskListAssembler.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4cTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GPUPathEdgeFanPayloadContract.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/PathTessellatorTest.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderPathFanLimits.kt`
- Modify: `render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/RenderPathFanLimitsTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt`

**Interfaces:**

- Consumes: le graph W4c authentifié de Task 4 et les ABI existantes `Uniform32`, `TriangulatedPath`, path stencil structural keys.
- Produces: `W4cSessionScratchV1`, `W4cSessionScratchDrawV1`, `W4cPathFillGraphLowerer.lower`, `GPUCorePrimitivePreparedPacketAuthority.plannedW4c` et une task list linéaire.

- [ ] **Step 1: Écrire les tests RED du capability adapter**

Quand `GPUTextureFormat.Depth24PlusStencil8` supporte 1 sample, le snapshot expose `PlanDepthStencilFormat.Depth24PlusStencil8` et les opérations depth/stencil + stencil/cover. Si le format ou 1× manque, W3/W4a/W4b restent planifiables mais W4c retourne `UnsupportedCapability` après sélection.

- [ ] **Step 2: Écrire les tests RED du lowerer**

Pour une séquence `direct, stencil, direct`, vérifier les rôles et loads :

```kotlin
assertEquals(
    listOf("direct", "producer", "cover", "direct"),
    lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().map(::w4cSemanticRole),
)
assertEquals(
    listOf("clear", "load", "load", "load"),
    renders.map { it.loadStore.loadOperation },
)
```

Le producer utilise `WritableStencil(Clear, Store, 0u)`; le cover `WritableStencil(Load, Store, null)` et son `GPUFrameResourceUse(PathDepthStencil)` porte `write=true`. Tester Uniform32, offsets V/I/U exacts, scissor, fill rule, source authority W4c et ordre de paint.

Ne pas reconstruire au lowerer des graphes impossibles à fabriquer publiquement : non-adjacence, groupe divergent, lifetime/ressource/budget incohérents sont déjà des refus de `RenderGraph.of` dans Task 3. Garder ici seulement des contrefaçons constructibles par API publique : capability snapshot périmé face au renderer courant (`UnsupportedCapability`) et graph valide d'une autre lane transmis directement au lowerer W4c (`InvalidPlan`, jamais une autre lane). Tous les tests restent sans reflection, accès privé, inspection source ou call-count.

- [ ] **Step 3: Vérifier RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanCapabilityAdapterTest*' --tests '*GpuPlanTaskListLowererW4cTest*'
```

Expected: FAIL à la compilation.

- [ ] **Step 4: Implémenter le scratch sans géométrie renderer nouvelle**

`W4cSessionScratchDrawV1` conserve une copie de `PathFillGeometryF32`, la stratégie, le scissor, le groupe, le slot uniform et les ranges V/I. `W4cSessionScratchV1` conserve l'ordre complet, les capacités pool exactes, les IDs target/staging/D24S8 et les hashes de graph/capability. Ajouter `ScratchLane.W4c` et `plannedW4c(...)`.

Ajouter `GPUPathSourceAuthority.W4cPlannedPathFillV1`. La production et sa KDoc qualifient `RenderPathFanLimits.MAX_TRIANGLES = 1_024` et `MAX_GEOMETRY_BYTES = 36_864` comme limites des routes legacy/`Unknown`; conserver leurs valeurs exactes. Qualifier le même ABI dans `GPUPathEdgeFanPayloadContract.kt`, et qualifier `RenderConfig.MAX_PATH_FAN_TRIANGLES`, `MAX_PATH_GEOMETRY_BYTES` ainsi que leurs valeurs par défaut dans `RenderConfig.kt` comme configuration de la seule route legacy edge-fan, jamais comme plafond W4c. `PathTessellator` continue de consommer cet adapter ABI legacy, sans nouveau type géométrique renderer. Les tests publics ne lisent aucun texte ou KDoc : `RenderPathFanLimitsTest.kt` vérifie seulement les valeurs publiques 1 024/36 864, `PathTessellatorTest.kt` le refus runtime legacy à 1 025, et `GPUFramePathApiInventoryTest.kt` les defaults publics legacy.

Dans la validation `TriangulatedPath`, conserver ce plafond legacy pour toute autre authority; pour cette seule authority, autoriser au plus `PathFillLimitsI32().maxAttemptedEdgesPerPathI32`, sachant que le scratch W4c vérifie séparément 255 pour Winding. Un test public du lowerer vérifie le comportement d'authority runtime : un fan W4c à 1 025 arêtes est représentable seulement avec l'authority W4c, alors que l'authority `Unknown` est refusée. `PathFillLimitsI32` reste l'unique autorité de la limite W4c (65 536) : aucune limite W4c n'est dupliquée dans `:render-ir`, `:gpu-renderer` ou `:kanvas`, et aucun type géométrique W4c ne sort de `:math`.

- [ ] **Step 5: Implémenter le lowerer et l'assembler**

Le lowerer copie les tableaux math dans `GPUCorePrimitiveGeometryInput.TriangulatedPath` avec `GPUCorePrimitiveSourceFamily.Path`. Le direct utilise `DirectTriangles`; le producer/cover partagent un `StencilEdgeFan`, `Stencil1x`, `Winding` ou `EvenOdd`, non-inverse.

L'assembler émet `PrepareResources`, un `GPUTask.Render` par passe planifiée et un `Readback`, avec les dépendances exactes du graph. Il prépare uniquement target/staging; V/I/U/D24S8 appartiennent au lease pool scellé. Il ne passe jamais par `GPUCorePrimitivePreparedFrameTaskListBuilder` pour classifier la géométrie.

- [ ] **Step 6: Exécuter les tests ciblés et renderer planning**

Run:

```bash
rtk ./gradlew :render-ir:test --tests '*RenderPathFanLimitsTest*' --rerun-tasks
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanCapabilityAdapterTest*' --tests '*GpuPlanTaskListLowererW4cTest*' --tests '*GpuPlanTaskListLowererW4aTest*' --tests '*GpuPlanTaskListLowererW4bTest*' --tests '*PathTessellatorTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*GPUFramePathApiInventoryTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Self-review et commit Terra**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4cPreparedAuthority.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4cPreparedFrameTaskListAssembler.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/state/StateContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GPUPathEdgeFanPayloadContract.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/PathTessellatorTest.kt render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/RenderPathFanLimits.kt render-ir/src/test/kotlin/org/graphiks/kanvas/render/ir/RenderPathFanLimitsTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/RenderConfig.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt
rtk git commit -m "feat(gpu-renderer): lower sealed W4c path plans"
```

Le controller soumet le commit aux deux gates Sol avant Task 6.

---

### Task 6: Preflight et matérialisation native du lease W4c

**Files:**

- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveW4cFramePayloadMaterializerTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveW4cFrameSmokeTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFrameExecutor.kt`
- Test existing pool behavior in: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt`

**Interfaces:**

- Consumes: W4c scratch et task list de Task 5; `GPUWgpu4kCorePrimitivePathDepthStencilRequirement`; pipelines natifs Winding/EvenOdd/CoverRegular existants.
- Produces: une frame préparée avec un lease unique V/I/U/D24S8 et un readback ordonné.

- [ ] **Step 1: Écrire les tests RED de preflight/materialization**

Prouver par sorties de données observables :

- une seule exigence `Depth24PlusStencil8`, extent target, sample 1, usage RenderAttachment;
- capacités V/I/U égales aux capacités du graph;
- upload byte-exact des arrays math et Uniform32;
- producer Winding/EvenOdd puis cover Regular, jamais inverse;
- cover natif `NotEqual` + `Zero` + write mask `0xff`;
- direct adjacent à un path stencil conserve son ordre et le bon état depth/stencil;
- refus terminal en cas de range, packet, uniform, pass, group ou attachment contradictoire.

- [ ] **Step 2: Écrire le test RED du lifetime pool jusqu'au readback**

Acquérir le slot W4c, le marquer submitted, vérifier qu'un checkout concurrent n'obtient pas le même slot avant `completeSuccessfully`, puis qu'il devient réutilisable après completion. Le test observe uniquement les handles de lease rendus par l'API normale du pool, sans introspection ni call-count de factory.

- [ ] **Step 3: Vérifier RED**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GPUWgpu4kCorePrimitiveW4cFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveW4cFrameSmokeTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
```

Expected: FAIL car le marker W4c et le cover writable-load ne sont pas encore admis.

- [ ] **Step 4: Brancher le preflight scellé W4c**

Reconnaître `W4cSessionScratchV1` avant les voies génériques. Authentifier l'ordre, les packets, les groupes, les offsets et la ressource D24S8. Construire `GPUCorePrimitivePathStencilNativeRouteSeal` à partir des copies math déjà présentes; ne jamais appeler `PathTessellator`.

- [ ] **Step 5: Brancher le materializer et le cover reset**

Construire `GPUWgpu4kCorePrimitiveFramePoolRequirements` avec `expectedCapacities` et `pathDepthStencil`. Dans le materializer et `GPUFrameExecutor`, accepter pour le cover W4c uniquement `GPUDepthStencilLoadStorePlan.WritableStencil(Load, Store, null)` avec resource use write. Laisser les covers destination-read legacy en `ReadOnlyKeep`.

Réutiliser les programmes `PathStencilProducerWinding`, `PathStencilProducerEvenOdd`, `PathStencilCoverRegular` et la texture poolée existante. Aucun nouveau shader ni pipeline family.

- [ ] **Step 6: Exécuter les tests ciblés puis les régressions path natives**

Run:

```bash
rtk ./gradlew :gpu-renderer:test --tests '*W4c*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --tests '*GPUFramePreflighterTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Self-review et commit Terra**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "feat(gpu-renderer): materialize W4c stencil cover frames"
```

Le controller soumet le commit aux deux gates Sol avant Task 7.

---

### Task 7: Activation Surface et oracle pixel indépendant

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4cPathFillCpuOracle.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt`

**Interfaces:**

- Consumes: capability W4c entièrement exécutable des Tasks 1–6.
- Produces: activation `Surface → Scene IR → W4c → readback`, oracle CPU indépendant et preuve RGBA/BGRA.

- [ ] **Step 1: Écrire l'oracle line-only indépendant**

Créer :

```kotlin
internal object W4cPathFillCpuOracle {
    data class Draw(
        val path: PathF32,
        val transform: Matrix3x3F32,
        val color: ColorARGB,
        val scissorI32: RectI32,
    )

    fun render(
        widthI32: Int,
        heightI32: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray
}
```

L'oracle transforme directement les segments source en F64, calcule crossings et winding/even-odd au centre de pixel, applique le scissor, `SrcOver` linear-premultiplied, encode sRGB et quantifie après chaque draw. Il n'importe aucun package `gpu.plan` ou `gpu.renderer`.

- [ ] **Step 2: Écrire le certificat indépendant des courbes**

Le même fichier contient une subdivision d'intervalles F64 test-only depuis le `PathF32` original. Pour chaque pixel center, elle calcule une enclosure à arrondi sortant, une borne de round-trip F32 dépendant des magnitudes device-space et refuse la fixture tant que distance/crossing/tie ne sont pas uniques au-delà de `0.25 + f32Bound + intervalBound`.

Le rendu d'une fixture courbe commence par :

```kotlin
val certificate = W4cPathFillCpuOracle.certifyCurveFixture(width, height, draw)
assertIs<W4cPathFillCpuOracle.CurveFixtureCertificate.Certified>(certificate)
```

Aucun chemin `Uncertified` ne peut produire des pixels attendus ni recevoir une tolérance.

- [ ] **Step 3: Écrire les tests Surface RED**

Ajouter des cas byte-exact : triangle direct; concave; trou Winding; trou EvenOdd; retrace/auto-intersection; quad, cubic et arc certifiés; scissor; transform négatif; deux paths translucides montrant la quantification entre draws; RGBA/BGRA. Vérifier l'evidence native Render/Readback. Pour le router, fournir au callback legacy un résultat sentinel distinct et vérifier que le résultat W4c observé n'est pas ce sentinel; ne compter aucun appel.

Ajouter les refus : frame mixte, AA, inverse, stroke et `TEXT_EXPANDED_PATH` restent legacy avant promotion; une erreur post-`Ready` reste terminale.

- [ ] **Step 4: Vérifier RED**

Run:

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*' --tests '*GPUPlanSurfaceRouterTest*'
```

Expected: les paths hard-edge passent encore par legacy et les nouvelles preuves W4c échouent.

- [ ] **Step 5: Activer W4c dans la chaîne et la gate**

Ajouter `W4cPathFillPlanCompiler()` après W4b dans `GpuRenderContext`. Autoriser `DisplayOp.DrawPath` dans `GPUPlanSurfaceCandidateGate`; l'admission sémantique complète reste exclusivement dans le compiler W4c. Ne pas ajouter `DrawText` ni les paths `TEXT_EXPANDED` à la gate.

- [ ] **Step 6: Exécuter les tests Surface ciblés et les lanes précédentes**

Run:

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*' --tests '*GPUPlanSurfaceRouterTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL ou uniquement les failures baseline déjà documentées hors W4c; aucune failure W4c.

- [ ] **Step 7: Self-review et commit Terra**

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4cPathFillCpuOracle.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt
rtk git commit -m "feat(kanvas): route W4c path fills through Surface"
```

Le controller soumet le commit aux deux gates Sol avant Task 8.

---

### Task 8: Suivi final, vérification et PR stackée

**Files:**

- Modify: `refactor/README.md`
- Modify: `refactor/waves/W04-geometry-coverage/status.md`
- Modify: `refactor/plans/2026-09-06-w4c-path-fills-implementation-plan.md` pour cocher les tâches réellement terminées
- Do not create: document de status intermédiaire supplémentaire

**Interfaces:**

- Consumes: commits approuvés des Tasks 1–7.
- Produces: preuve fraîche, status W04 consolidé et PR W4c empilée sur W4b.

- [ ] **Step 1: Exécuter la vérification math/planner**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Exécuter la vérification renderer ciblée**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlan*' --tests '*W4c*' --tests '*GPUCorePrimitivePathStencil*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
```

Expected: BUILD SUCCESSFUL, aucun XML failure/error W4c.

- [ ] **Step 3: Exécuter la vérification Kanvas ciblée puis globale**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
rtk ./gradlew :kanvas:test --rerun-tasks
```

Expected: aucun nouveau nom de failure par rapport au ledger W4b; zéro error. Les failures baseline existantes sont listées, jamais masquées.

- [ ] **Step 4: Vérifier explicitement le périmètre et les fichiers**

```bash
rtk git diff codex/w4b-analytic-rrect...HEAD --name-only
rtk git diff --check codex/w4b-analytic-rrect...HEAD
rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
```

Confirmer qu'aucun chemin `font`, `codec`, `integration-tests/skia`, GM, render/dashboard/baseline ou `jpg-color-cube` n'apparaît dans le diff ou les commandes exécutées.

- [ ] **Step 5: Mettre à jour uniquement les documents autoritaires**

Ajouter les liens spec/plan W4c dans `refactor/README.md`. Mettre à jour le status W04 avec capability, architecture, limites, ressources, commandes fraîches et ledger exact. Conserver W4 ouverte pour W4d/W4e et la dette SDF W4b pour W7.

- [ ] **Step 6: Committer le suivi final**

```bash
rtk git add refactor/README.md refactor/waves/W04-geometry-coverage/status.md refactor/plans/2026-09-06-w4c-path-fills-implementation-plan.md
rtk git commit -m "docs(refactor): publish W4c path fill evidence"
```

- [ ] **Step 7: Sol final review et corrections**

Utiliser `superpowers:requesting-code-review`. Sol revoit le diff complet `codex/w4b-analytic-rrect...HEAD`, les résultats frais et la conformité à la spec. Tout finding valide repart vers l'agent Terra de la tâche concernée; refaire les tests proportionnés puis la revue jusqu'à `APPROVED`.

- [ ] **Step 8: Pousser et créer/mettre à jour la PR stackée**

Créer avec `apply_patch` `/tmp/w4c-pr-body.md` contenant exactement les sections suivantes, renseignées à partir des preuves fraîches de cette tâche :

```markdown
## Summary

- W4c Tasks 1–8 et capability livrée
- architecture `:math` → `:gpu-plan` → `:gpu-renderer` → `Surface`

## Verification

- commandes exactes exécutées et résultats
- ledger des éventuelles failures baseline hors W4c

## Scope and follow-ups

- font, codec, GM Skia, dashboard, baseline et `jpg-color-cube` inchangés
- W4d/W4e restent ouvertes
- dette SDF W4b toujours suivie pour W7
```

```bash
rtk git push -u origin codex/w4c-path-fills
rtk gh pr list --head codex/w4c-path-fills --state all
rtk gh pr create --base codex/w4b-analytic-rrect --head codex/w4c-path-fills --title "feat: add W4c hard-edge path fills" --body-file /tmp/w4c-pr-body.md
```

Si la PR existe, utiliser `rtk gh pr edit <number> --base codex/w4b-analytic-rrect --body-file /tmp/w4c-pr-body.md`. Le corps décrit les Tasks 1–8, les exclusions, les preuves fraîches et le fait que W4d/W4e restent ouvertes.

---

## Ordre d'exécution et gates

```text
Task 1 F64 transform
  → Sol spec + quality
Task 2 math preparation
  → Sol spec + quality
Task 3 RenderGraph
  → Sol spec + quality
Task 4 planner/budget
  → Sol spec + quality
Task 5 lowerer/scratch
  → Sol spec + quality
Task 6 native/pool
  → Sol spec + quality
Task 7 Surface/oracle
  → Sol spec + quality
Task 8 verification/docs/PR
  → Sol final review
```

Le controller n'avance à la tâche suivante qu'après correction de tous les findings Important/Critical. Les suggestions non bloquantes sont consignées dans la review mais ne doivent pas élargir W4c.
