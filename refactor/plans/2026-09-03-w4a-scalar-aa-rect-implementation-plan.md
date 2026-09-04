# W4a ScalarAA Rect — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use Terra for implementation and Sol only for reviews.

**Goal:** Rendre par la voie `SceneSnapshot -> RenderGraph -> GPU` les frames de rectangles solides fractionnaires axis-aligned avec couverture analytique `ScalarAA`, scissor intégral et `SrcOver`, sans modifier les pixels ni l'admission W3.

**Architecture:** Une chaîne handle-free sélectionne W3 ou W4a avant toute acquisition du device et transmet un candidat opaque au compiler choisi. W4a scelle les bounds `RectF32`, le raster/scissor `RectI32`, cinq ressources logiques et le budget des capacités réellement réservées. Un lowering sibling matérialise exclusivement ces décisions dans la lane native analytic-shape Uniform80 existante, sans route générique, double allocation, reclassification ou fallback après `Ready`.

**Tech Stack:** Kotlin 2.x/JVM, Gradle Kotlin DSL, JUnit 5, `kotlin.test`, coroutines Kotlin, contrats WebGPU confinés à `:gpu-renderer`, géométrie `RectF32`/`RectI32`/`SizeI32`/`Matrix3x3F32` fournie par `:math`.

**Spec:** [`refactor/specs/2026-09-03-w4-geometry-coverage-stack-design.md`](../specs/2026-09-03-w4-geometry-coverage-stack-design.md)

## Global Constraints

- `font`, décodage et encodage `codec` sont hors périmètre.
- Ne jamais exécuter `jpg-color-cube` ni une commande de la suite GM Skia.
- Aucun fallback CPU silencieux ; une frame entière reste legacy avant promotion ou entièrement GPU après `Ready`.
- Toute géométrie backend-neutral reste dans `:math` et suit `I32`/`I64`/`F32`/`F64`. `:gpu-plan` ne crée aucune algèbre géométrique parallèle.
- W4a accepte uniquement `GeometryNode.Rect`, solid, fill, `CoverageRequest.ANTIALIASED`, `SrcOver`, transform identity/scale-translate, cible sRGB 1x et clip vide ou `DeviceRect` intégral non-AA.
- W4a accepte 1 à 512 draws ; W3 conserve sa limite historique de 512 commandes totales. Les limites générales de metadata/capture restent celles de `SceneCaptureLimits`.
- Le graphe W4a déclare target, staging, vertex, index et uniform. Les trois derniers sont les capacités exactes du pool, jamais des buffers ordinary supplémentaires.
- Useful bytes : vertex `32N`, index `24N`, uniform `N * alignUp(80, minUniformBufferOffsetAlignment)`.
- Floors actuels : vertex 16 KiB, index 4 KiB, uniform 4 KiB ; croissance power-of-two exposée par l'adapter, non recopiée comme vérité renderer dans le compiler.
- Lifetimes W4a : target `[0,2)`, staging `[1,2)`, vertex/index/uniform `[0,2)` ; peak égal à la somme checked des cinq ressources.
- Le slot natif W4a doit avoir exactement les capacités V/I/U du plan. Un slot plus grand ne satisfait pas cette réservation.
- Aucun nouveau WGSL : réutiliser la lane Rect `AnalyticShape`, l'ABI Uniform80 et sa formule overlap-area.
- Aucun test d'infrastructure, reflection, parsing du source, méthode privée, call-count, ou snapshot de forme interne/WGSL. Prouver des valeurs publiques, résultats typés, autorités immuables, payloads natifs et pixels.
- Tous les changements de comportement suivent RED–GREEN–REFACTOR.
- Toutes les commandes shell sont préfixées par `rtk`; toutes les éditions manuelles passent par `apply_patch`.
- Les agents d'implémentation Terra travaillent séquentiellement. Chaque task reçoit ensuite une review de conformité puis une review qualité Sol avant la task suivante.

---

## Progression

- [x] Task 1 — Étendre les contrats de plan et les capacités physiques
- [x] Task 2 — Sélectionner W3/W4a par candidat opaque
- [x] Task 3 — Compiler les rectangles fractionnaires en graphe W4a
- [x] Task 4 — Abaisser W4a en task list scellée Uniform80
- [x] Task 5 — Préflighter et matérialiser W4a avec le pool exact
- [x] Task 6 — Brancher la chaîne dans le context et la façade Surface
- [x] Task 7 — Prouver les pixels et les frontières 512/513
- [ ] Task 8 — Vérifier globalement, publier W04 et ouvrir la PR stackée

## Carte des dépendances

```text
Task 1 -> Task 2 -> Task 3 -> Task 4 -> Task 5 -> Task 6 -> Task 7 -> Task 8
```

Chaque task produit un commit vert et reviewable. Aucun agent d'implémentation ne commence la task suivante avant les deux reviews de la task courante.

## Carte des fichiers

| Unité | Responsabilité |
| --- | --- |
| `PlanBufferAllocationPolicy.kt` | Politique handle-free de floors/croissance et calcul checked des capacités réservées. |
| `AnalyticRectPlanBudget.kt` | Footprint W4a exact, row alignment, useful bytes, capacités pool et peak. |
| `GpuPlanCompiler.kt` | Sélection explicite, candidat opaque et compilation sans seconde reconnaissance. |
| `CapabilityCompilerChain.kt` | Ordre W3 puis W4a, agrégation déterministe des gaps et dispatch au même compiler. |
| `W4aAnalyticRectPlanCompiler.kt` | Admission, transform axis-aligned, bounds device/raster/scissor et `RenderGraph` W4a. |
| `GPUPlanW4aPreparedAuthority.kt` | Autorité renderer scellée de la task list W4a et de ses capacités physiques. |
| `GPUCorePrimitiveW4aPreparedFrameTaskListAssembler.kt` | Assemblage sibling W4a sans prepared builder générique. |
| `W4aAnalyticRectGraphLowerer.kt` | Validation fermée des cinq ressources et création des packets ScalarAA. |
| `W4aAnalyticRectCpuOracle.kt` | Oracle indépendant overlap-area, SrcOver et quantification sRGB. |

## Task 1 — Étendre les contrats de plan et les capacités physiques

**Files:**

- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanBufferAllocationPolicy.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRectPlanBudget.kt`
- Create: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRectPlanBudgetTest.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraphContractTest.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompilerTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapterTest.kt`
- Modify the `PlanCapabilitySnapshot.of` fixtures in:
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt`

**Interfaces:**

- Consumes: `PlanCapabilitySnapshot`, `PlanResource`, `PlanPass.RenderPass`, `SolidRectDraw`, `PlanMemoryBudget` W3.
- Produces: `PlanBufferAllocationPolicy`, `AnalyticRectMemoryFootprint`, `PlanDraw`, `AnalyticRectDraw`, `PlanDrawDataResources` et snapshot physique complet consommés par Tasks 2–5.

- [ ] **Step 1: Write the failing policy and budget tests**

Ajouter des tests publics couvrant les floors, le premier doublement, l'overflow et le footprint exact :

```kotlin
private fun supportedCapabilities(
    minUniformAlignment: Int = 256,
    maxBufferSizeBytes: Long = 1L shl 20,
): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
    deviceGeneration = 0,
    maxTextureDimension2D = 64,
    maxBufferSizeBytes = maxBufferSizeBytes,
    copyBytesPerRowAlignment = 256,
    supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
    minUniformBufferOffsetAlignment = minUniformAlignment,
    maxDynamicUniformBuffersPerPipelineLayout = 1,
    supportedOperations = PlanOperationCapability.entries.toSet(),
    bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
)

@Test
fun `analytic rect budget counts exact pooled capacities`() {
    val result = AnalyticRectPlanBudget.calculate(
        targetExtent = SizeI32(4, 3),
        drawCount = 2,
        capabilities = supportedCapabilities(minUniformAlignment = 256),
        budget = PlanBudget(25_392),
    )
    val ready = assertIs<AnalyticRectPlanBudgetResult.WithinBudget>(result)
    assertEquals(48, ready.footprint.targetBytes)
    assertEquals(768, ready.footprint.readbackBytes)
    assertEquals(16_384, ready.footprint.vertexCapacityBytes)
    assertEquals(4_096, ready.footprint.indexCapacityBytes)
    assertEquals(4_096, ready.footprint.uniformCapacityBytes)
    assertEquals(25_392, ready.footprint.peakBytes)
}

@Test
fun `pool policy doubles only after a floor is exceeded`() {
    val policy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096)
    assertEquals(16_384, policy.reserve(PlanScratchBufferKind.Vertex, 16_384))
    assertEquals(32_768, policy.reserve(PlanScratchBufferKind.Vertex, 16_385))
    assertNull(policy.reserve(PlanScratchBufferKind.Uniform, Long.MAX_VALUE))
}
```

Run:

```bash
rtk ./gradlew :gpu-plan:test --tests '*AnalyticRectPlanBudgetTest*'
```

Expected: FAIL at compilation because the W4a policy and budget types do not exist.

- [ ] **Step 2: Implement the immutable pool policy and checked footprint**

Créer les contracts suivants. `reserve` renvoie `null` pour input non positif ou overflow ; il n'effectue jamais de clamp.

```kotlin
public enum class PlanScratchBufferKind { Vertex, Index, Uniform }
public enum class PlanBufferGrowth { PowerOfTwo }

public data class PlanBufferAllocationPolicy private constructor(
    public val vertexFloorBytes: Long,
    public val indexFloorBytes: Long,
    public val uniformFloorBytes: Long,
    public val growth: PlanBufferGrowth,
) {
    public fun reserve(kind: PlanScratchBufferKind, usefulBytes: Long): Long?

    public companion object {
        public fun of(
            vertexFloorBytes: Long,
            indexFloorBytes: Long,
            uniformFloorBytes: Long,
            growth: PlanBufferGrowth = PlanBufferGrowth.PowerOfTwo,
        ): PlanBufferAllocationPolicy
    }
}

public data class AnalyticRectMemoryFootprint(
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
    public val peakBytes: Long,
)

public sealed interface AnalyticRectPlanBudgetResult {
    public data class WithinBudget(val footprint: AnalyticRectMemoryFootprint) : AnalyticRectPlanBudgetResult
    public data class Exceeded(val requiredBytes: Long, val limitBytes: Long) : AnalyticRectPlanBudgetResult
    public data class Invalid(val code: String) : AnalyticRectPlanBudgetResult
}
```

`AnalyticRectPlanBudget.calculate` utilise `Math.multiplyExact`, `Math.addExact` et `alignUp` checked. Les codes `invalid-input`, `size-overflow` et `pool-capacity-overflow` sont fermés.
`PlanBufferAllocationPolicy` implémente une égalité et un hash structurels sur
ses quatre champs afin que deux snapshots adaptés depuis les mêmes faits soient
égaux.

- [ ] **Step 3: Write failing graph contract tests for draw resources and defensive geometry**

```kotlin
@Test
fun `analytic rect draw owns exact defensive math geometry`() {
    val exact = RectF32(0.25f, 0.5f, 2.75f, 2.25f)
    val raster = RectI32(0, 0, 3, 3)
    val draw = AnalyticRectDraw.of(
        0, ColorF32.of(1f, 0f, 0f, 1f), exact, raster, RectI32(1, 0, 3, 3),
    )
    exact.left = 99f
    raster.left = 99
    assertEquals(RectF32(0.25f, 0.5f, 2.75f, 2.25f), draw.copyDeviceBounds())
    assertEquals(RectI32(0, 0, 3, 3), draw.copyRasterBounds())
    assertEquals(CoveragePlan.AnalyticScalarAA, draw.coverage)
}

@Test
fun `render pass draw resources participate in lifetime validation`() {
    assertFailsWith<IllegalArgumentException> {
        graphWithAnalyticDrawResources(uniformLifetime = 1 until 2)
    }
}

private fun graphWithAnalyticDrawResources(uniformLifetime: IntRange): RenderGraph {
    val target = targetResource()
    val staging = stagingResource()
    fun scratch(
        role: PlanResourceRole,
        usage: PlanResourceUsage,
        lifetime: IntRange = 0 until 2,
    ) = PlanResource.of(
        role, 0, PlanResourceKind.Buffer, null, null, 4_096,
        setOf(usage, PlanResourceUsage.CopyDestination),
        PlanResourceLifetime.FrameLocal, lifetime.first, lifetime.last + 1,
    )
    val vertex = scratch(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
    val index = scratch(PlanResourceRole.IndexData, PlanResourceUsage.Index)
    val uniform = scratch(
        PlanResourceRole.UniformData,
        PlanResourceUsage.Uniform,
        uniformLifetime,
    )
    val draw = AnalyticRectDraw.of(
        0, ColorF32.of(1f, 0f, 0f, 1f),
        RectF32(0.25f, 0f, 0.75f, 1f), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
    )
    val render = PlanPass.RenderPass(
        0, target.id, listOf(draw), AttachmentLoadPlan.ClearTransparent,
        AttachmentStorePlan.Store, PlanDrawDataResources(vertex.id, index.id, uniform.id),
    )
    val readback = PlanPass.ReadbackPass(0, target.id, staging.id, 256)
    return validGraph(
        resources = listOf(target, staging, vertex, index, uniform),
        passes = listOf(render, readback),
        dependencies = listOf(PlanPassDependency(render.id, readback.id)),
        peakFrameLocalBytes = 12_548,
    )
}
```

Run:

```bash
rtk ./gradlew :gpu-plan:test --tests '*RenderGraphContractTest*'
```

Expected: FAIL because the analytic draw and typed resource binding do not exist.

- [ ] **Step 4: Extend the graph algebra without changing W3 semantics**

Implémenter :

```kotlin
public sealed interface PlanDraw {
    public val commandIndex: Int
    public val color: ColorF32
    public val coverage: CoveragePlan
    public val sample: SamplePlan
    public val blend: BlendPlan
}

public class AnalyticRectDraw private constructor(/* defensive RectF32/RectI32 snapshots */) : PlanDraw {
    public fun copyDeviceBounds(): RectF32
    public fun copyRasterBounds(): RectI32
    public fun copyScissor(): RectI32
    public companion object { public fun of(/* exact arguments from the test */): AnalyticRectDraw }
}

public data class PlanDrawDataResources(
    public val vertex: PlanResourceId,
    public val index: PlanResourceId,
    public val uniform: PlanResourceId,
)
```

Faire implémenter `PlanDraw` par `SolidRectDraw`, ajouter
`AnalyticScalarAA` à `CoveragePlan`, `VertexData/IndexData/UniformData` à
`PlanResourceRole` et `Vertex/Index/Uniform` à `PlanResourceUsage`.

`PlanPass.RenderPass` accepte `List<PlanDraw>` et un
`drawDataResources: PlanDrawDataResources? = null`. `RenderGraph` ajoute ces
trois IDs à `referencedResources(pass)`. Dans le lowerer W3, extraire une liste
de `SolidRectDraw` seulement après avoir vérifié que tous les draws ont ce type ;
ne modifier aucune autre règle W3.

- [ ] **Step 5: Write the failing complete capability snapshot tests**

```kotlin
@Test
fun `adapter publishes exact W4 planning facts`() {
    val snapshot = assertIs<GpuPlanCapabilityAdapterResult.Supported>(
        capabilities(rendererFeatures = requiredPlanFeatures()).toPlanCapabilitySnapshot(GPUDeviceGenerationID(7)),
    ).snapshot
    assertEquals(256, snapshot.minUniformBufferOffsetAlignment)
    assertEquals(1, snapshot.maxDynamicUniformBuffersPerPipelineLayout)
    assertEquals(
        setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer, PlanOperationCapability.Readback),
        snapshot.supportedOperations(),
    )
    assertEquals(16_384, snapshot.bufferAllocationPolicy.vertexFloorBytes)
}
```

Ajouter aussi un cas où une opération est absente : l'adapter produit un
snapshot sans cette opération, il ne la fabrique pas et ne rejette pas
globalement W3 à cet endroit.

- [ ] **Step 6: Extend the snapshot and adapter**

Ajouter :

```kotlin
public enum class PlanOperationCapability { RenderPass, CopyUpload, UniformBuffer, Readback }

public class PlanCapabilitySnapshot private constructor(
    /* existing fields */
    public val minUniformBufferOffsetAlignment: Int,
    public val maxDynamicUniformBuffersPerPipelineLayout: Int,
    supportedOperations: Set<PlanOperationCapability>,
    public val bufferAllocationPolicy: PlanBufferAllocationPolicy,
) {
    public fun supportedOperations(): Set<PlanOperationCapability>
}
```

`GPUCapabilities.toPlanCapabilitySnapshot` convertit uniquement les features
réellement présentes dans `rendererFeatures`, convertit les deux limites UBO
avec contrôle `Int` (`maxDynamicUniformBuffersPerPipelineLayout == null`
devient la preuve conservative `0`), et construit la politique depuis
`CORE_PRIMITIVE_FRAME_POOL_VERTEX_FLOOR_BYTES`,
`CORE_PRIMITIVE_FRAME_POOL_INDEX_FLOOR_BYTES` et
`CORE_PRIMITIVE_FRAME_POOL_UNIFORM_FLOOR_BYTES`.
Étendre aussi `equals`/`hashCode` du snapshot et la preimage de `PlanId` W3 avec
les nouveaux champs ; le `capabilityId` et la structure de graphe W3 restent
inchangés.

- [ ] **Step 7: Run focused compatibility tests and commit**

```bash
rtk ./gradlew :gpu-plan:test --tests '*AnalyticRectPlanBudgetTest*' --tests '*RenderGraphContractTest*' --tests '*W3SolidRectPlanCompilerTest*'
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanCapabilityAdapterTest*' --tests '*GpuPlanTaskListLowererTest*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*'
rtk git add gpu-plan/src gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt
rtk git commit -m "feat: model W4 analytic plan resources"
```

Expected: PASS, including all existing W3 plan/lowering tests.

## Task 2 — Sélectionner W3/W4a par candidat opaque

**Files:**

- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GpuPlanCompiler.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Create: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompilerTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt`

**Interfaces:**

- Consumes: immutable `SceneSnapshot`, `RenderTargetDescriptor`, snapshot/budget de Task 1.
- Produces: `GpuPlanSelection`, `GpuPlanCandidate`, `CapabilityCompilerChain` et un W3 compiler sans seconde reconnaissance, consommés par Tasks 3 et 6.

- [ ] **Step 1: Write failing selection-chain tests**

Utiliser le vrai compiler W3 derrière un compiler de gap minimal, puis prouver
l'ordre, l'agrégation des gaps et l'arrêt sur invalid. Les fixtures du fichier
sont explicites et ne dépendent d'aucun mock :

```kotlin
private class NotCandidateCompiler(private val code: String) : GpuPlanCompiler {
    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection =
        GpuPlanSelection.NotCandidate(listOf(RenderDiagnostic(
            RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE,
            RenderDiagnosticSeverity.INFO, "Fixture gap $code",
        )))

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> = error("A gap compiler must never receive plan()")
}

private fun scene(): SceneSnapshot = SceneSnapshot.of(
    SceneExtent(1, 1), ColorSpace.SRGB,
    listOf(SceneCommand.DrawColor(ColorARGB.White, BlendMode.SRC_OVER)),
)

private fun target(): RenderTargetDescriptor =
    RenderTargetDescriptor(SceneExtent(1, 1), ColorSpace.SRGB)

private fun capabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
    deviceGeneration = 0,
    maxTextureDimension2D = 64,
    maxBufferSizeBytes = 1L shl 20,
    copyBytesPerRowAlignment = 256,
    supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
    minUniformBufferOffsetAlignment = 256,
    maxDynamicUniformBuffersPerPipelineLayout = 1,
    supportedOperations = PlanOperationCapability.entries.toSet(),
    bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
)

@Test
fun `chain chooses first candidate and plans only that opaque candidate`() {
    val chain = CapabilityCompilerChain.of(
        listOf(NotCandidateCompiler("first.gap"), W3SolidRectPlanCompiler()),
    )
    val selected = assertIs<GpuPlanSelection.Candidate>(chain.select(scene(), target()))
    val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
        chain.plan(selected.candidate, capabilities(), PlanBudget(1L shl 20)),
    ).plan
    assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
}

@Test
fun `all gaps remain ordered and require no physical snapshot`() {
    val result = CapabilityCompilerChain.of(
        listOf(NotCandidateCompiler("a"), NotCandidateCompiler("b")),
    )
        .select(scene(), target())
    assertEquals(listOf("a", "b"), assertIs<GpuPlanSelection.NotCandidate>(result).diagnostics().map { it.code.value })
}
```

Run `rtk ./gradlew :gpu-plan:test --tests '*CapabilityCompilerChainTest*'`.
Expected: compilation FAIL.

- [ ] **Step 2: Replace classify/re-plan with explicit selection**

Implémenter les contrats :

```kotlin
public interface GpuPlanCandidate {
    public val capabilityId: String
    public val sceneCanonicalId: CanonicalId
    public val target: RenderTargetDescriptor
}

public sealed interface GpuPlanSelection {
    public data class Candidate(public val candidate: GpuPlanCandidate) : GpuPlanSelection
    public class NotCandidate(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
        public fun diagnostics(): List<RenderDiagnostic>
    }
    public class InvalidScene(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
        public fun diagnostics(): List<RenderDiagnostic>
    }
}

public interface GpuPlanCompiler {
    public fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection
    public fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}
```

`CapabilityCompilerChain` valide d'abord extent/color space scène-cible, agrège
les `NotCandidate` dans l'ordre, enveloppe le candidat avec l'index et
l'identité du compiler, puis exige ce wrapper exact dans `plan`. Un candidat
étranger ou forgé produit `InvalidScene` avec code stable
`gpu-plan.selection.invalid-candidate`.

- [ ] **Step 3: Convert W3 to a sealed candidate**

La classe privée W3 candidate conserve la liste immuable de `SolidRectDraw`,
le canonical ID et la cible. `select` effectue `recognize` une seule fois.
`plan` caste vers cette classe, vérifie les empreintes, puis ne rappelle jamais
`recognize(scene)`.

Ajouter avant reconnaissance :

```kotlin
if (scene.count() > MAX_W3_COMMANDS) {
    return notCandidate("W3 accepts at most 512 total commands")
}
```

Conserver `CAPABILITY_ID`, les deux ressources, `FullOrScissor`, les diagnostics
et les IDs W3. Adapter tous les tests W3 avec un helper `plan(scene, target,
capabilities, budget)` qui appelle `select` puis `plan(candidate, ...)`.

- [ ] **Step 4: Prove W3 boundary and no reclassification**

```kotlin
@Test
fun `W3 keeps its historical total command limit`() {
    assertIs<GpuPlanSelection.Candidate>(compiler.select(sceneWithTotalCommands(512), target()))
    assertIs<GpuPlanSelection.NotCandidate>(compiler.select(sceneWithTotalCommands(513), target()))
}

@Test
fun `candidate remains bound to its exact scene and target`() {
    val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(sceneA, target())).candidate
    val foreign = object : GpuPlanCandidate {
        override val capabilityId = candidate.capabilityId
        override val sceneCanonicalId = candidate.sceneCanonicalId
        override val target = candidate.target
    }
    assertIs<RenderPlanResult.InvalidScene>(compiler.plan(foreign, capabilities(), budget()))
}
```

Le deuxième test utilise une autre implémentation publique de
`GpuPlanCandidate`, pas reflection ni mutation privée.

- [ ] **Step 5: Move backend device acquisition after Candidate**

Dans `GpuRenderBackend.plan`, appliquer exactement :

```kotlin
return when (val selected = compiler.select(scene, target)) {
    is GpuPlanSelection.NotCandidate -> RenderPlanResult.GapNotMigrated(selected.diagnostics())
    is GpuPlanSelection.InvalidScene -> RenderPlanResult.InvalidScene(selected.diagnostics())
    is GpuPlanSelection.Candidate -> {
        val capabilities = acquirePlanningCapabilitiesOrPromotedGap()
        compiler.plan(selected.candidate, capabilities, PlanBudget(targetConfig.frameLocalBudgetBytes))
            .also(::rememberIssuedPlanWhenReady)
    }
}
```

Extraire les deux helpers privés sans changer le weak identity registry. Un
backend dont le runtime est indisponible doit tout de même retourner
`GapNotMigrated` pour une scène non candidate ; ce résultat public prouve que
la sélection ne dépend pas du device, sans call-count.

- [ ] **Step 6: Run tests and commit**

```bash
rtk ./gradlew :gpu-plan:test --tests '*CapabilityCompilerChainTest*' --tests '*W3SolidRectPlanCompilerTest*'
rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderBackendTest*' --tests '*GpuPlanTaskListLowererTest*'
rtk git add gpu-plan/src gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackend.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderBackendTest.kt
rtk git commit -m "refactor: select gpu plan capabilities explicitly"
```

Expected: PASS; les preuves W3 existantes restent vertes.

## Task 3 — Compiler les rectangles fractionnaires en graphe W4a

**Files:**

- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Create: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompilerTest.kt`
- Modify: `gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt`

**Interfaces:**

- Consumes: Task 1 plan algebra/budget and Task 2 opaque selection.
- Produces: capability `solid-rect-scalar-aa-simple-scissor-src-over-srgb-v1` avec cinq ressources, consommée par Task 4.

- [ ] **Step 1: Write the failing exact-geometry test**

```kotlin
private val compiler = W4aAnalyticRectPlanCompiler()

private fun solidRect(
    left: Float = 0.25f,
    top: Float = 0.5f,
    right: Float = 3.75f,
    bottom: Float = 2.25f,
    clip: ClipStackNode = ClipStackNode.Empty,
    transform: Matrix3x3F32 = Matrix3x3F32.Identity,
    coverage: CoverageRequest = CoverageRequest.ANTIALIASED,
): SceneCommand.Draw {
    val color = ColorARGB.fromPackedUInt(0x80FF0000u)
    return SceneCommand.Draw(DrawNode(
        geometry = GeometryNode.Rect.of(RectF32(left, top, right, bottom)),
        material = MaterialNode.Solid(color),
        coverage = coverage,
        clip = clip,
        blend = BlendNode.SrcOver,
        effects = EffectStack.Empty,
        transform = transform,
        origin = DrawOrigin.RECT,
        paint = PaintNode(
            color, null, BlendMode.SRC_OVER, null, null, null, null, null,
            PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
        ),
    ))
}

private fun deviceClip(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    antiAlias: Boolean,
): ClipStackNode = ClipStackNode.DeviceRect.of(RectF32(left, top, right, bottom), antiAlias)

private fun ready(
    command: SceneCommand,
    width: Int = 4,
    height: Int = 3,
    metadata: List<SceneCommand> = emptyList(),
): RenderGraph = ready(metadata + command, width, height)

private fun ready(
    commands: List<SceneCommand>,
    width: Int = 4,
    height: Int = 3,
): RenderGraph {
    val scene = SceneSnapshot.of(SceneExtent(width, height), ColorSpace.SRGB, commands)
    val selected = assertIs<GpuPlanSelection.Candidate>(
        compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
    )
    return assertIs<RenderPlanResult.Ready<RenderGraph>>(
        compiler.plan(selected.candidate, supportedCapabilities(), PlanBudget(1L shl 20)),
    ).plan
}

private fun renderPass(graph: RenderGraph): PlanPass.RenderPass =
    assertIs<PlanPass.RenderPass>(graph.passes().single { it is PlanPass.RenderPass })

@Test
fun `fractional rect seals exact device raster and scissor bounds`() {
    val graph = ready(
        solidRect(0.25f, 0.5f, 3.75f, 2.25f,
            clip = deviceClip(1f, 0f, 3f, 3f, antiAlias = false)),
        width = 4, height = 3,
    )
    val draw = assertIs<AnalyticRectDraw>(renderPass(graph).draws().single())
    assertEquals(RectF32(0.25f, 0.5f, 3.75f, 2.25f), draw.copyDeviceBounds())
    assertEquals(RectI32(0, 0, 4, 3), draw.copyRasterBounds())
    assertEquals(RectI32(1, 0, 3, 3), draw.copyScissor())
    assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
}
```

Run `rtk ./gradlew :gpu-plan:test --tests '*W4aAnalyticRectPlanCompilerTest*'`.
Expected: FAIL because the compiler does not exist.

- [ ] **Step 2: Implement semantic selection**

Créer une candidate privée qui conserve les `AnalyticRectDraw` défensifs.
Pour chaque draw, vérifier dans cet ordre : provenance/geometry/material,
paint/effects/resource/blend, coverage AA, nombres finis, rect source ordonné,
transform identity ou scale-translate, clip vide ou `DeviceRect` non-AA
intégral, bounds visibles non vides.

Transformer les quatre coins et normaliser les min/max, y compris pour un scale
négatif :

```kotlin
private fun deviceBounds(source: RectF32, matrix: Matrix3x3F32): RectF32? {
    if (!(matrix.isIdentity || matrix.isScaleTranslate()) || !matrixIsFinite(matrix)) return null
    val corners = listOf(
        matrix.transform(Point2F32(source.left, source.top)),
        matrix.transform(Point2F32(source.right, source.top)),
        matrix.transform(Point2F32(source.right, source.bottom)),
        matrix.transform(Point2F32(source.left, source.bottom)),
    )
    if (corners.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    return RectF32(corners.minOf { it.x }, corners.minOf { it.y },
        corners.maxOf { it.x }, corners.maxOf { it.y })
}

private fun matrixIsFinite(matrix: Matrix3x3F32): Boolean = listOf(
    matrix.sx, matrix.kx, matrix.tx,
    matrix.ky, matrix.sy, matrix.ty,
    matrix.persp0, matrix.persp1, matrix.persp2,
).all(Float::isFinite)
```

La sélection conserve les bounds device `RectF32` finies sans les convertir.
Le `plan` applique ensuite `floor/ceil` checked vers `I32` ; une valeur finie
non représentable ou un overflow produit `ResourceLimitExceeded`, jamais un
gap ni un clamp. Exiger au moins une arête fractionnaire dans la frame. Les
commandes `SetTransform`, `SetClip` et `Annotation` finies sont des metadata ;
`DrawColor` reste hors W4a.

- [ ] **Step 3: Prove the complete admission matrix**

Ajouter une matrice table-driven. Chaque mutation porte une seule différence
par rapport au draw W4a valide ; les helpers construisent de vrais `DrawNode`
publics et n'inspectent aucune structure privée :

```kotlin
@Test
fun `unsupported semantic axes are gaps while non finite input is invalid`() {
    val base = solidRect().node
    val gaps = listOf(
        "hard-edge" to base.copy(coverage = CoverageRequest.HARD_EDGE),
        "stroke" to base.copy(paint = requireNotNull(base.paint).copy(
            style = PaintStyleNode.STROKE, strokeWidth = 1f,
        )),
        "non-solid" to base.copy(material = MaterialNode.Transparent),
        "aa-clip" to base.copy(clip = deviceClip(0f, 0f, 3f, 3f, antiAlias = true)),
        "fractional-clip" to base.copy(clip = deviceClip(0.5f, 0f, 3f, 3f, antiAlias = false)),
        "complex-clip" to base.copy(clip = ClipStackNode.Operations.of(emptyList())),
        "rotation" to base.copy(transform = Matrix3x3F32.rotation(0.25f)),
    )
    gaps.forEach { (label, node) ->
        assertIs<GpuPlanSelection.NotCandidate>(select(SceneCommand.Draw(node)), label)
    }
    assertIs<GpuPlanSelection.NotCandidate>(
        select(SceneCommand.DrawColor(ColorARGB.White, BlendMode.SRC_OVER)),
    )
    assertIs<GpuPlanSelection.InvalidScene>(select(SceneCommand.Draw(base.copy(
        geometry = GeometryNode.Rect.of(RectF32(Float.NaN, 0f, 1f, 1f)),
    ))))
}

@Test
fun `mixed coordinates negative scale and metadata remain candidates`() {
    val metadata = listOf(
        SceneCommand.SetTransform(Matrix3x3F32.Identity),
        SceneCommand.SetClip(ClipStackNode.Empty),
        SceneCommand.Annotation.of(RectF32(0f, 0f, 4f, 3f), "fixture", "w4a"),
    )
    val mixed = ready(metadata + listOf(
        solidRect(left = 0f, top = 0f, right = 1f, bottom = 1f),
        solidRect(left = 1.25f, top = 0.5f, right = 3.75f, bottom = 2.25f),
    ))
    assertEquals(2, renderPass(mixed).draws().size)
    assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, mixed.capabilityId)
    assertIs<GpuPlanSelection.Candidate>(select(
        solidRect(transform = Matrix3x3F32(sx = -1f, sy = 1f, tx = 4f)),
    ))
}

private fun select(
    command: SceneCommand,
    metadata: List<SceneCommand> = emptyList(),
): GpuPlanSelection {
    val scene = SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, metadata + command)
    return compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace))
}
```

Dans cette même table, ajouter les mutations exactes `GeometryNode.RRect`,
`GeometryNode.Path` et oval avec leur `DrawOrigin`, rect vide/inversé,
skew/perspective et fully clipped ; chacune attend `NotCandidate`. Ajouter un
test de cardinalité qui construit `List(512) { solidRect() } + metadata` puis
`List(513) { solidRect() }`, et attend respectivement `Candidate` et
`NotCandidate`.

- [ ] **Step 4: Write the failing resource and physical-limit tests**

```kotlin
@Test
fun `ready W4a graph declares exact pool-backed resources`() {
    val graph = ready(twoFractionalRects())
    assertEquals(
        listOf(LogicalTarget, ReadbackStaging, VertexData, IndexData, UniformData),
        graph.resources().map { it.role },
    )
    assertEquals(25_392, graph.peakFrameLocalBytes)
    assertEquals(
        PlanDrawDataResources(
            planResourceId(PlanResourceRole.VertexData, 0),
            planResourceId(PlanResourceRole.IndexData, 0),
            planResourceId(PlanResourceRole.UniformData, 0),
        ),
        renderPass(graph).drawDataResources,
    )
}
```

Ajouter des cas ±1 pour budget, max buffer, missing operation, dynamic uniform
0, mauvais alignement/policy et target dimension. Les opérations absentes ou
limites device insuffisantes donnent `GapOnPromotedScope`; footprint overflow ou
budget insuffisant donne `ResourceLimitExceeded`.

```kotlin
@Test
fun `physical limits fail closed at their exact boundary`() {
    val candidate = selected(twoFractionalRects())
    assertIs<RenderPlanResult.Ready<RenderGraph>>(
        compiler.plan(candidate, supportedCapabilities(), PlanBudget(25_392)),
    )
    assertIs<RenderPlanResult.ResourceLimitExceeded>(
        compiler.plan(candidate, supportedCapabilities(), PlanBudget(25_391)),
    )
    assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(
        candidate,
        supportedCapabilities(maxBufferSizeBytes = 16_383),
        PlanBudget(25_392),
    ))
    assertIs<RenderPlanResult.GapOnPromotedScope>(compiler.plan(
        candidate,
        supportedCapabilities(
            supportedOperations = PlanOperationCapability.entries.toSet() -
                PlanOperationCapability.UniformBuffer,
        ),
        PlanBudget(25_392),
    ))
}

@Test
fun `finite bounds outside I32 are selected then fail as a resource limit`() {
    val scene = SceneSnapshot.of(
        SceneExtent(4, 3), ColorSpace.SRGB,
        listOf(solidRect(left = -2_147_483_648f, right = 2_147_483_648f)),
    )
    val target = RenderTargetDescriptor(scene.extent, scene.colorSpace)
    val candidate = assertIs<GpuPlanSelection.Candidate>(compiler.select(scene, target)).candidate
    assertIs<RenderPlanResult.ResourceLimitExceeded>(
        compiler.plan(candidate, supportedCapabilities(), PlanBudget(1L shl 20)),
    )
}
```

- [ ] **Step 5: Build the exact five-resource graph**

Utiliser `AnalyticRectPlanBudget.calculate`, puis créer :

```kotlin
target  = PlanResource.of(LogicalTarget, 0, Texture2D, format, extent,
    footprint.targetBytes, setOf(RenderAttachment, CopySource), FrameLocal, 0, 2)
staging = PlanResource.of(ReadbackStaging, 0, Buffer, null, null,
    footprint.readbackBytes, setOf(CopyDestination, MapRead), FrameLocal, 1, 2)
vertex  = PlanResource.of(VertexData, 0, Buffer, null, null,
    footprint.vertexCapacityBytes, setOf(Vertex, CopyDestination), FrameLocal, 0, 2)
index   = PlanResource.of(IndexData, 0, Buffer, null, null,
    footprint.indexCapacityBytes, setOf(Index, CopyDestination), FrameLocal, 0, 2)
uniform = PlanResource.of(UniformData, 0, Buffer, null, null,
    footprint.uniformCapacityBytes, setOf(Uniform, CopyDestination), FrameLocal, 0, 2)
```

Le `RenderPass` référence `PlanDrawDataResources(vertex.id, index.id,
uniform.id)`. L'identité SHA-256 commence par `w4a-plan-v1` et inclut la scène,
la cible, chaque champ du snapshot physique et le budget. Elle n'inclut aucun
label diagnostic.

- [ ] **Step 6: Prove chain priority and commit**

```kotlin
@Test
fun `W3 wins aligned frames and W4a wins fractional frames`() {
    val chain = CapabilityCompilerChain.of(listOf(W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler()))
    assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, ready(chain, alignedScene()).capabilityId)
    assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, ready(chain, fractionalScene()).capabilityId)
}
```

```bash
rtk ./gradlew :gpu-plan:test
rtk git add gpu-plan/src
rtk git commit -m "feat: compile W4 scalar aa rectangles"
```

## Task 4 — Abaisser W4a en task list scellée Uniform80

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4aAnalyticRectGraphLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4aPreparedAuthority.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4aPreparedFrameTaskListAssembler.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4aTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`

**Interfaces:**

- Consumes: exact `RenderGraph` W4a from Task 3 and existing analytic Uniform80 builders.
- Produces: `W4aSessionScratchV1` attached to ordered ScalarAA packets and une task list preplanned, consommés par Task 5.

- [ ] **Step 1: Write a failing valid-lowering test**

```kotlin
@Test
fun `valid W4a graph lowers to sealed analytic Uniform80 packets`() {
    val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(request(readyW4aGraph())))
    val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
    assertEquals(2, render.drawPackets.size)
    render.drawPackets.forEach { packet ->
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        assertEquals(GPUCorePrimitiveCoverageMode.ScalarAA, semantic.coverageMode)
        assertEquals(80L, packet.corePrimitivePreparedAuthority
            ?.analyticShapeUniformSeal?.payloadBytes)
        assertNotNull(packet.corePrimitivePreparedAuthority?.w4aSessionScratch)
    }
}
```

Run `rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4aTest*'`.
Expected: FAIL because W4a dispatch/authority do not exist.

- [ ] **Step 2: Add capability dispatch and exact graph validation**

`GpuPlanTaskListLowerer.lower` conserve les vérifications communes snapshot et
budget, puis dispatch :

```kotlin
return when (request.graph.capabilityId) {
    W3SolidRectPlanCompiler.CAPABILITY_ID -> lowerW3(request)
    W4aAnalyticRectPlanCompiler.CAPABILITY_ID -> W4aAnalyticRectGraphLowerer().lower(request)
    else -> invalid("Unknown gpu-plan capability id.")
}
```

Le validateur W4a exige exactement cinq ressources, usages/lifetimes/IDs,
deux passes `Render -> Readback`, `PlanDrawDataResources` exact, 1..512
`AnalyticRectDraw`, `AnalyticScalarAA`, `SingleSample`, `SrcOver`, clear/store,
footprint recalculé égal aux useful/capacity/peak du plan. Il ne lit jamais la
`SceneSnapshot`.

- [ ] **Step 3: Create packets only from sealed plan values**

Pour chaque draw, construire le semantic payload avec :

```kotlin
GPUCorePrimitivePayloadInput(
    commandIdValue = draw.commandIndex,
    sourceFamily = GPUCorePrimitiveSourceFamily.Rect,
    geometry = GPUCorePrimitiveGeometryInput.Rect(left, top, right, bottom),
    premultipliedRgba = listOf(draw.color.red, draw.color.green, draw.color.blue, draw.color.alpha),
    targetBounds = targetBounds,
    scissorBounds = plannedScissor,
    clipCoveragePlan = plannedClip,
    clipExecutionPlanIdentity = plannedExecution.canonicalIdentity(),
    blendPlanIdentity = blend.canonicalIdentity(),
    frameProvenance = GPUFrameProvenance.None,
    coverageMode = GPUCorePrimitiveCoverageMode.ScalarAA,
    analysisRecordId = "analysis.fill_rect.${draw.commandIndex}",
    analysisCommandFamily = "FillRect",
    rectRouteAuthority = GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
    rectGeometryAuthority = corePrimitiveRectGeometryAuthority(deviceRect, GPUTransformFacts.identity()),
)
```

Le lowerer convertit les `RectF32` sans normalisation et les `RectI32` sans
`coerceIn`. Seul le quad d'émission peut être élargi ; le scissor packet reste
exactement celui du plan.

- [ ] **Step 4: Seal the W4a scratch and Uniform80 slab**

Créer `W4aSessionScratchV1` avec : plan/capability hash/génération,
target/staging, IDs V/I/U, liste ordonnée packet/command, useful bytes,
capacités réservées, uniform stride/plan et un snapshot défensif
device/raster/scissor par draw.

L'assembler sibling appelle `buildCorePrimitiveAnalyticShapeUniform`, crée un
unique `GPUUniformSlabPlan` de source
`core-primitive-analytic-shape-uniform-pass`, un
`GPUCorePrimitiveAnalyticShapeUniformSeal` de 80 bytes par packet et attache :

```kotlin
GPUCorePrimitivePreparedPacketAuthority(
    structuralPipelineKey = analyticKey,
    renderPipelineKey = publicKey,
    uniformSlabSeal = null,
    analyticShapeUniformSeal = perPacketSeal,
    w4aSessionScratch = scratch,
)
```

Il assemble target preparation, render, readback et leurs dépendances comme le
sibling W3, mais il n'appelle jamais `GPUCorePrimitivePreparedFrameTaskListBuilder`
ni la route analytique générique.

- [ ] **Step 5: Write rejection tests**

Créer des graphes via les factories publiques avec une seule contradiction à
la fois : mauvais capability ID, rôle/usage/lifetime, binding V/I/U, capacité,
stride, peak, draw type, coverage, scissor ou snapshot. Vérifier
`InvalidPlan` ou `UnsupportedCapability` selon le domaine, et aucune task list.
Ne pas forger par reflection.

- [ ] **Step 6: Run lowerer regressions and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4aTest*' --tests '*GpuPlanTaskListLowererTest*'
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4aAnalyticRectGraphLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4aPreparedAuthority.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4aPreparedFrameTaskListAssembler.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4aTest.kt
rtk git commit -m "feat: lower W4 analytic rect plans"
```

## Task 5 — Préflighter et matérialiser W4a avec le pool exact

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt`

**Interfaces:**

- Consumes: task list et `W4aSessionScratchV1` de Task 4.
- Produces: payload natif AnalyticShape utilisant exactement le pool budgété, avec lease jusqu'à completion/readback.

- [ ] **Step 1: Write failing preflight tests for exact authority**

Ajouter un cas W4a vert, puis modifier séparément exact bounds, scissor, slot
Uniform80, resource ID et capacité pool. Chaque contradiction doit produire un
diagnostic terminal ; aucun cas ne doit tomber dans le classifier générique.

```kotlin
@Test
fun `W4a scratch authenticates planned analytic packets`() {
    val result = preflight(loweredW4aTaskList())
    assertIs<GPUFramePreflightResult.Prepared>(result)
}
```

Run `rtk ./gradlew :gpu-renderer:test --tests '*GPUFramePreflighterTest*'`.
Expected: FAIL sur l'enveloppe W4a non reconnue.

- [ ] **Step 2: Add the sealed W4a preflight branch**

Détecter un unique `w4aSessionScratch` avant le routing générique. Vérifier
identité d'objet commune à tous les packets, Uniform80, shader
`AnalyticShape`, zero radii Rect, target/scissor/bounds scellés, ordre des
packets, cinq allocations mémoire et sommes exactes. Produire les operand keys
W4a nécessaires au materializer sans reconstruire une route.

- [ ] **Step 3: Write the failing exact-pool test**

```kotlin
@Test
fun `W4a exact reservation does not reuse a larger slot`() {
    val larger = pool.acquire(requirements(
        vertexBytes = 16_385, indexBytes = 4_097, uniformBytes = 4_097,
        expectedCapacities = GPUWgpu4kCorePrimitiveFramePoolCapacities(32_768, 8_192, 8_192),
    )).acquiredLease()
    larger.rollbackBeforeSubmit()
    val exact = pool.acquire(requirements(
        vertexBytes = 16_384, indexBytes = 4_096, uniformBytes = 4_096,
        expectedCapacities = GPUWgpu4kCorePrimitiveFramePoolCapacities(16_384, 4_096, 4_096),
    )).acquiredLease()
    assertEquals(
        GPUWgpu4kCorePrimitiveFramePoolCapacities(16_384, 4_096, 4_096),
        exact.capacities,
    )
    exact.rollbackBeforeSubmit()
}
```

Ajouter à la request pool un champ optionnel
`expectedCapacities: GPUWgpu4kCorePrimitiveFramePoolCapacities?` et le même
paramètre au helper `requirements` du test. Le chemin historique
conserve `contains`; W4a renseigne ce champ et exige l'égalité.

- [ ] **Step 4: Materialize W4a before generic and W3 paths**

Dans `GPUWgpu4kCorePrimitiveFramePayloadMaterializer`, détecter W4a avant les
branches W3/génériques et appeler :

```kotlin
private fun materializeW4aSessionScratch(
    framePlan: GPUFramePlan,
    encoderPlan: GPUCommandEncoderPlan,
    resources: GPUPreparedResourceSet,
    generationSeal: GPUPreparedGenerationSeal,
    renderStep: GPUFrameStep.RenderPassStep,
    scratch: W4aSessionScratchV1,
): GPUPreparedNativeFramePayloadMaterialization
```

La méthode réutilise le shader/pipeline AnalyticShape, les
`GPUCorePrimitiveAnalyticShapeUniformSeal`, les uploads packed et
`GPUWgpu4kCorePrimitivePayloadLeaseLifecycle`. Elle demande au pool les
capacités exactes du scratch. Elle ne prépare aucun buffer ordinary et ne
réappelle pas `GPUCorePrimitiveDirectNativeRoute`.

- [ ] **Step 5: Prove ABI, scissor, no double allocation and lifecycle**

Dans le test materializer, vérifier le payload natif public/interne autorisé :
80 bytes par slot, offsets alignés, vertex/index useful exacts, une seule lease
pool, aucune resource preparation V/I/U ordinary, scissor égal au plan et
capacités exactes. Exercer success, refusal et cancellation pour montrer que la
lease n'est réutilisable qu'après completion/readback, sans compteur artificiel.

- [ ] **Step 6: Run native tests and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt
rtk git commit -m "feat: materialize W4 analytic rect payloads"
```

## Task 6 — Brancher la chaîne dans le context et la façade Surface

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt`
- Rename/modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceShallowGate.kt` -> `GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanRenderContextOwner.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`
- Modify W3 façade names in `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt` and `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt`.

**Interfaces:**

- Consumes: chain/compiler W3+W4a and lowering natif Tasks 2–5.
- Produces: voie produit générique `GpuPlanSurfaceExecutor` utilisée par `Surface.render()`.

- [ ] **Step 1: Write failing context behavior tests**

Prouver qu'un context produit W3 pour une scène alignée, W4a pour une scène
fractionnaire et `GapNotMigrated` pour une scène path, puis qu'un token W4a est
one-shot et lié au context émetteur. Utiliser résultats typés/outputs, jamais un
call-count.

- [ ] **Step 2: Install the compiler chain**

Dans la façade context :

```kotlin
val compiler = CapabilityCompilerChain.of(
    listOf(W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler()),
)
val backend = GpuRenderBackend(compiler, context, targetConfig)
```

Renommer sans alias W3 :

```text
GpuW3SurfaceReadyToken   -> GpuPlanSurfaceReadyToken
GpuW3SurfacePlanResult   -> GpuPlanSurfacePlanResult
GpuW3SurfaceSubmitResult -> GpuPlanSurfaceSubmitResult
GpuW3SurfaceExecutor     -> GpuPlanSurfaceExecutor
w3SurfaceExecutor()      -> planSurfaceExecutor()
```

Conserver weak graph authentication, réservation LRU, one-shot token et absence
de fallback après `Ready`.

- [ ] **Step 3: Make the Surface candidate gate composition-only**

`GPUPlanSurfaceCandidateGate.accepts` vérifie le format sRGB et l'union
`DrawRect`, `DrawColor`, `SetTransform`, `SetClip`, `Annotation`. Supprimer
`MAX_W3_COMMANDS` et tout test sur `operations.size`. W3/W4a font leurs comptes
dans leurs selectors ; `DisplayOpSceneAdapter` conserve `SceneCaptureLimits`.

Renommer `W3SurfacePlanSubmitPort` en `GPUPlanSurfacePort` et le champ `w3Port`
en `planPort`. Les résultats de capture limit continuent vers le legacy ; les
corruptions et tout échec après `Ready` restent terminaux.

- [ ] **Step 4: Migrate existing router tests without adding infrastructure assertions**

Adapter seulement les noms du port et des résultats dans les tests existants.
Supprimer l'ancien test interne « 512 annotations », devenu faux. Ne pas ajouter
de test direct de la candidate gate, du port, ni de call-count. Les nouvelles
preuves de comportement et les frontières 512/513 passent exclusivement par
`Surface` dans Task 7.

- [ ] **Step 5: Run context/Surface regressions and commit**

```bash
rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderContextTest*' --tests '*GpuRenderBackendTest*'
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfaceRouterTest*' --tests '*GPUPlanSurfacePixelTest*'
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanRenderContextOwner.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt
rtk git add -A -- kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceShallowGate.kt kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt
rtk git commit -m "feat: route Surface through W4 plan chain"
```

## Task 7 — Prouver les pixels et les frontières 512/513

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4aAnalyticRectCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt`

**Interfaces:**

- Consumes: voie publique `Surface.render()` de Task 6.
- Produces: preuve indépendante overlap-area/quantification et preuve de migration W4a.

- [ ] **Step 1: Implement the independent oracle tests first**

L'oracle dépend uniquement de `ColorARGB`, `PixelFormat` et des valeurs math.
Pour chaque pixel/scissor :

```kotlin
internal object W4aAnalyticRectCpuOracle {
    internal data class Draw(
        val color: ColorARGB,
        val bounds: RectF32,
        val scissor: RectI32,
    )

    internal fun render(
        width: Int,
        height: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray
}

val coverageX = (minOf(x + 1f, draw.right) - maxOf(x.toFloat(), draw.left)).coerceIn(0f, 1f)
val coverageY = (minOf(y + 1f, draw.bottom) - maxOf(y.toFloat(), draw.top)).coerceIn(0f, 1f)
val coverage = coverageX * coverageY
val coveredSource = LinearPremul.from(draw.color) * coverage
pixels[offset] = srcOver(coveredSource, pixels[offset]).quantizedForAttachment()
```

Tester l'oracle avec des valeurs manuelles simples : un rect `[0.25,0.5,
1.75,1.5]` produit les coverages `0.375, 0.375, 0.375, 0.375` sur les quatre
pixels touchés avant blend. Vérifier aussi le swizzle BGRA.

- [ ] **Step 2: Add the opaque fractional Surface proof**

Créer un `Surface(4, 3)`, dessiner un rect fractionnaire opaque AA et comparer
les bytes exacts de `render()` à l'oracle. Exiger les scopes publics exactement
`{Render, Readback}`.

- [ ] **Step 3: Add translucent and scissor proofs**

Ajouter deux rectangles fractionnaires translucides superposés dont le second
observe la quantification du premier. Ajouter un clip intégral non-AA qui coupe
la fringe ; tous les pixels hors scissor sont strictement transparents. Tester
RGBA et BGRA contre le même oracle.

Ajouter dans le même fichier une preuve explicite de frame mixte :

```kotlin
@Test
fun `integral and fractional AA rectangles share the W4a frame in paint order`() {
    val integral = ColorARGB.of(255, 231, 37, 19)
    val fractional = ColorARGB.of(173, 17, 83, 219)
    val draws = listOf(
        W4aAnalyticRectCpuOracle.Draw(
            integral, RectF32(0f, 0f, 2f, 2f), RectI32(0, 0, 4, 3),
        ),
        W4aAnalyticRectCpuOracle.Draw(
            fractional, RectF32(1.25f, 0.5f, 3.75f, 2.25f), RectI32(0, 0, 4, 3),
        ),
    )
    val surface = Surface(4, 3)
    surface.canvas {
        drawRect(draws[0].bounds, Paint.fill(integral).copy(antiAlias = true))
        drawRect(draws[1].bounds, Paint.fill(fractional).copy(antiAlias = true))
    }

    val result = surface.render()
    assertEquals(setOf("Render", "Readback"), result.nativeEvidenceScopeKinds.toSet())
    assertContentEquals(W4aAnalyticRectCpuOracle.render(4, 3, draws), result.pixels)
}
```

- [ ] **Step 4: Add unsupported-scene legacy proof**

Utiliser un rect fractionnaire stroked ou un clip AA, calculer/figer son résultat
legacy connu, et vérifier seulement les pixels publics. Ne pas imposer de forme
de scopes au legacy.

- [ ] **Step 5: Prove 512 draws plus metadata and 513 draws through Surface**

Sur une target 1x1, enregistrer 512 rectangles fractionnaires AA et une
annotation : le rendu doit égaler l'oracle et exposer exactement
`{Render, Readback}`. Pour 513 rectangles, choisir un
`RenderConfig.frameLocalBudgetBytes` qui autorise le footprint 512 mais pas un
footprint W4a 513 ; le comportement correct reste le pixel legacy. Si 513 est
promu par erreur, le budget terminal fait échouer le test au lieu de masquer la
faute par des pixels identiques.

- [ ] **Step 6: Run public proof suite and commit**

```bash
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'
rtk git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4aAnalyticRectCpuOracle.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt
rtk git commit -m "test: prove W4 scalar aa Surface pixels"
```

Expected: les preuves W3 et W4a passent ; les échecs legacy connus des sélections
larges restent inchangés.

## Task 8 — Vérifier globalement, publier W04 et ouvrir la PR stackée

**Files:**

- Create: `refactor/waves/W04-geometry-coverage/status.md`
- Modify: `refactor/README.md`
- Modify: `refactor/plans/2026-09-03-w4a-scalar-aa-rect-implementation-plan.md` uniquement pour refléter les checkboxes réellement terminées.

**Interfaces:**

- Consumes: tous les commits W4a et leurs résultats frais.
- Produces: preuves autoritaires W04, review Sol globale et PR stackée sur `codex/w3-gpu-plan-first-slice`.

- [x] **Step 1: Run module gates**

```bash
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsTest :math:matrix:jvmTest :math:matrix:jsTest :math:color:jvmTest :math:color:jsTest
rtk ./gradlew :render-ir:test :gpu-plan:test
rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*'
rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'
```

Expected: les gates ciblées W4a passent. La commande `:kanvas:test` ciblée peut
encore rencontrer les 45 échecs legacy `GPUAllApiBlendSurfaceTest` uniquement si
le pattern les inclut ; leurs noms doivent rester ceux de la baseline.

- [x] **Step 2: Run the fresh global non-GM baseline**

```bash
rtk ./gradlew :kanvas:test --rerun-tasks
rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
```

Expected: Gradle reste rouge uniquement sur les 51 échecs connus, 0 erreur. La
liste doit être exactement :

- `ImageTest :: ColorType enum values` ;
- les 45 `GPUAllApiBlendSurfaceTest :: DrawPoint` des 15 blends avancés sous
  `UNCLIPPED`, `SCISSOR`, `ALPHA_MASK` ;
- `GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase` ;
- les deux refus `GPUPreparedSurfaceFrameBuilderTest` ;
- `GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry and verb count seals every contour` ;
- `GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non identity matrix facts before dispatch`.

Tout nouveau nom rouge bloque W4a. Ne pas exécuter `:integration-tests:skia` ni
aucune tâche GM.

- [x] **Step 3: Write the authoritative W04 status**

Documenter : capability W4a, cinq ressources/capacités exactes, sélection
W3/W4a, pixel proofs, commandes et résultats frais, exact 51-name baseline,
`jpg-color-cube` non exécutée, exclusions font/codec, et limites ouvertes
RRect/path/stroke/clip. Dans `refactor/README.md`, passer W4 de « Non démarrée »
à « W4a ScalarAA Rect atteinte ; gate W4 ouverte » et lier le status.

- [x] **Step 4: Run document and diff checks**

```bash
rtk rg -n 'T[B]D|T[O]DO|F[I]XME|place[h]older' refactor/specs/2026-09-03-w4-geometry-coverage-stack-design.md refactor/plans/2026-09-03-w4a-scalar-aa-rect-implementation-plan.md refactor/waves/W04-geometry-coverage/status.md
rtk git diff --check
rtk git status --short
```

Expected: aucun marqueur incomplet, aucune erreur whitespace, uniquement les changements
W4a attendus.

- [ ] **Step 5: Commit status and request final Sol review**

```bash
rtk git add refactor/README.md refactor/waves/W04-geometry-coverage/status.md refactor/plans/2026-09-03-w4a-scalar-aa-rect-implementation-plan.md
rtk git commit -m "docs: publish W4 scalar aa evidence"
```

La review Sol globale relit tous les commits depuis
`codex/w3-gpu-plan-first-slice`, vérifie spec/plan, architecture, pixels,
budgets, absence de fallback/double allocation et exact failure-name baseline.
Tout finding bloquant ou important est corrigé par un nouvel agent Terra puis
re-reviewé par Sol avant publication.

- [ ] **Step 6: Push and open the stacked PR**

Après review Sol `APPROVE` et vérifications fraîches :

```bash
rtk git push -u origin codex/w4a-scalar-aa-rect
rtk gh pr create --base codex/w3-gpu-plan-first-slice --head codex/w4a-scalar-aa-rect --title "feat: add W4 scalar AA rectangle rendering" --body-file refactor/.w4a-pr-body.tmp.md
```

Le body décrit la stack `#2385 -> #2386 -> W4a`, le scope fermé, les preuves,
les 51 échecs connus, la quarantaine `jpg-color-cube` et les limites W4b–W4e.
Créer `refactor/.w4a-pr-body.tmp.md` via `apply_patch`, exécuter la commande,
puis supprimer ce seul fichier via `apply_patch` immédiatement après création
de la PR ; il ne doit jamais être commité.
