# W4b Analytic RRect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Livrer W4b : Rect et RRect solides AA dans une frame préparée, avec
normalisation F32 commune, Uniform80 existant et preuves pixels non-GM.

**Architecture:** W4b est un sibling vertical de W4a, pas une abstraction
générique. :math normalise les RRect, :gpu-plan scelle un AnalyticRRectDraw
par primitive, et :gpu-renderer consomme seulement ces faits scellés dans une
enveloppe W4b qui ne renormalise, ne reclassifie et ne fallback jamais après
Ready.

**Tech Stack:** Kotlin multiplatform JVM/JS, :math, :render-ir, :gpu-plan,
:gpu-renderer, :kanvas, WebGPU AnalyticShape, Gradle et rtk.

**Spec:** refactor/specs/2026-09-04-w4b-analytic-rrect-design.md

## Global Constraints

> géométrie seulement :math, nomenclature I32/I64/F32/F64; capability exacte; chaîne W3→W4a→W4b; solid/SrcOver/sRGB1x/simple integral scissor; Uniform80 et cinq PlanResource exactes; pas de reclassification/fallback après Ready; pas de font/codec/GM/Skia/jpg-color-cube; aucun test infrastructure/source-shape/reflection/private/call-count; dette SDF suivie mais aucun seuil/tolérance modifié.

- La capability exacte est solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1.
- SDD est obligatoire : toute décision est reliée à la spec. TDD impose RED
  observé avant le code GREEN pour chaque tâche. Le workflow Terra est
  strictement RED→GREEN→self-review→commit.
- Terra implémente une tâche à la fois. Après le commit Terra, le controller
  génère le package de review et sollicite Sol; Sol fait uniquement une review
  externe et ne committe ni code ni tests. Aucun agent d’implémentation ne
  travaille en parallèle.
- Les exécutions Node ciblées utilisent jsNodeTest. Les filtres --tests sont
  employés seulement pour les tâches JVM; jsNodeTest exécute la suite de
  source-set concernée sans filtre.
- Les nouveaux types géométriques restent dans :math et portent F32/I32/I64/F64.
- W4b admet uniquement SolidColor, SrcOver, sRGB 1x, AA scalaire, transform
  scale-plus-translate fini et scissor DeviceRect entier non-AA.
- Les cinq PlanResource sont exactement LogicalTarget, ReadbackStaging,
  VertexData, IndexData, UniformData. Pipeline/bind group sont des faits de
  soumission, jamais des PlanResource ni des remplaçants de staging.
- Uniform80 est inchangé : bounds 32..47, TL/TR 48..63, BR/BL 64..79 et
  stride alignUp(80, minUniformBufferOffsetAlignment).
- Avant `Ready`, `uniformUsefulBytes` reste représentable par l’hôte
  (`<= Int.MAX_VALUE`) et le dernier dynamic offset
  `(drawCount - 1) × uniformStride` reste `<= UInt.MAX_VALUE`, avec
  arithmétique checked.
- Après Ready, toute divergence est terminale : jamais W4b→W4a/W3, legacy ou
  direct native. GPURRectNormalizer et GPUCorePrimitiveDirectNativeRoute ne
  participent pas à W4b.
- Les tests sont comportementaux : ni infrastructure, source-shape,
  reflection, privé ou call-count. Aucun font, codec, GM, Skia ou
  jpg-color-cube n’est modifié ou exécuté.
- La SDF native RRect reste une dette suivie. Aucun seuil/tolérance/baseline
  ne change; W7 est le point de réévaluation Skia.

---

## Carte de fichiers

| Frontière | Existants | Créés W4b |
|---|---|---|
| :math | math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RRectF32.kt; math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/AxisAlignedGeometryF32.kt | RRectNormalizationF32.kt |
| :gpu-plan | PlanPasses.kt; AnalyticRectPlanBudget.kt; W4aAnalyticRectPlanCompiler.kt; CapabilityCompilerChain.kt | AnalyticRRectPlanBudget.kt; W4bPlanDiagnostics.kt; W4bAnalyticRRectPlanCompiler.kt |
| lowering | GpuPlanTaskListLowerer.kt; W4aAnalyticRectGraphLowerer.kt | W4bAnalyticRRectGraphLowerer.kt |
| scratch/recording | passes/GPUPlanW4aPreparedAuthority.kt; recording/GPUCorePrimitiveW4aPreparedFrameTaskListAssembler.kt | GPUPlanW4bPreparedAuthority.kt; GPUCorePrimitiveW4bPreparedFrameTaskListAssembler.kt |
| exécution | execution/GPUFramePreflighter.kt; GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt; GPUWgpu4kCorePrimitiveFramePool.kt | branches W4b scellées, pool réutilisé |
| Surface | GPUPlanSurfaceCandidateGate.kt; GPUPlanSurfaceRouter.kt; DisplayOpSceneAdapter.kt; GpuRenderContext.kt | aucun token nouveau |
| pixels | GPUPlanSurfacePixelTest.kt; W4aAnalyticRectCpuOracle.kt | W4bAnalyticRRectCpuOracle.kt |

## Interfaces partagées

Les tâches suivantes conservent ces noms.

~~~kotlin
public enum class RRectNormalizationF32Rejection {
    NonFiniteBounds, EmptyBounds, NonFiniteRadius, NegativeRadius,
}

public sealed interface RRectNormalizationF32Result {
    public class Accepted internal constructor(shape: RRectF32) :
        RRectNormalizationF32Result {
        public fun copyShape(): RRectF32
    }
    public data class Rejected(
        public val reason: RRectNormalizationF32Rejection,
    ) : RRectNormalizationF32Result
}

public fun RRectF32.normalizeForAnalyticFillF32(): RRectNormalizationF32Result

public class AnalyticRRectDraw private constructor(
    override val commandIndex: Int,
    override val color: ColorF32,
    origin: DrawOrigin,
    deviceShape: RRectF32,
    rasterBounds: RectI32,
    scissor: RectI32,
) : PlanDraw {
    public val origin: DrawOrigin
    public fun copyDeviceShape(): RRectF32
    public fun copyRasterBounds(): RectI32
    public fun copyScissor(): RectI32
    public companion object {
        public fun of(
            commandIndex: Int, color: ColorF32, origin: DrawOrigin,
            deviceShape: RRectF32, rasterBounds: RectI32, scissor: RectI32,
        ): AnalyticRRectDraw
    }
}

public object AnalyticRRectPlanBudget {
    public fun calculate(
        targetExtent: SizeI32, drawCount: Int,
        capabilities: PlanCapabilitySnapshot, budget: PlanBudget,
    ): AnalyticRRectPlanBudgetResult
}

public data class AnalyticRRectMemoryFootprint(
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

public sealed interface AnalyticRRectPlanBudgetResult {
    public data class WithinBudget(
        public val footprint: AnalyticRRectMemoryFootprint,
    ) : AnalyticRRectPlanBudgetResult
    public data class Exceeded(
        public val requiredBytes: Long, public val limitBytes: Long,
    ) : AnalyticRRectPlanBudgetResult
    public data class Invalid(public val code: String) : AnalyticRRectPlanBudgetResult
}

public class W4bAnalyticRRectPlanCompiler : GpuPlanCompiler {
    public companion object {
        public const val CAPABILITY_ID: String =
            "solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1"
    }
}

internal class W4bAnalyticRRectGraphLowerer {
    fun lower(request: GpuPlanLoweringRequest): GpuPlanLoweringResult
}

internal class W4bSessionScratchDrawV1(
    val packetId: GPUDrawPacketID, val commandId: Int, val origin: DrawOrigin,
    deviceShape: RRectF32, rasterBounds: RectI32, scissorBounds: GPUPixelBounds,
) {
    fun copyDeviceShape(): RRectF32
    fun copyRasterBounds(): RectI32
    fun copyScissorBounds(): GPUPixelBounds
}

internal class W4bSessionScratchV1
internal class GPUCorePrimitiveW4bPlannedRRectAuthority private constructor(
    private val sealedFacts: GPUCorePrimitiveRRectRawFacts,
    private val opaqueAuthority: GPUCorePrimitiveRRectGeometryAuthority,
) {
    internal fun geometryInput(): GPUCorePrimitiveGeometryInput.RRect
    internal fun authority(): GPUCorePrimitiveRRectGeometryAuthority
    internal fun matches(geometry: GPUCorePrimitiveGeometry.RRect): Boolean
    internal companion object {
        internal fun issue(
            draw: W4bSessionScratchDrawV1,
        ): GPUCorePrimitiveW4bPlannedRRectAuthority?
    }
}
~~~

GPUCorePrimitiveW4bPlannedRRectAuthority.issue copie uniquement les bits
device de draw.copyDeviceShape() dans GPUCorePrimitiveRRectRawFacts. Elle
construit GPUCorePrimitiveRRectTransformRawFacts Identity avec translate/skew
à 0f et scale à 1f, puis appelle directement la factory internal existante :

~~~kotlin
val shape = draw.copyDeviceShape()
val sealedFacts = GPUCorePrimitiveRRectRawFacts(
    leftBits = shape.rect.left.toRawBits(),
    topBits = shape.rect.top.toRawBits(),
    rightBits = shape.rect.right.toRawBits(),
    bottomBits = shape.rect.bottom.toRawBits(),
    topLeftXBits = shape.topLeft.x.toRawBits(),
    topLeftYBits = shape.topLeft.y.toRawBits(),
    topRightXBits = shape.topRight.x.toRawBits(),
    topRightYBits = shape.topRight.y.toRawBits(),
    bottomRightXBits = shape.bottomRight.x.toRawBits(),
    bottomRightYBits = shape.bottomRight.y.toRawBits(),
    bottomLeftXBits = shape.bottomLeft.x.toRawBits(),
    bottomLeftYBits = shape.bottomLeft.y.toRawBits(),
)
val identityTransformFacts = GPUCorePrimitiveRRectTransformRawFacts(
    type = GPUCorePrimitiveRectTransformType.Identity,
    translateXBits = 0f.toRawBits(), translateYBits = 0f.toRawBits(),
    scaleXBits = 1f.toRawBits(), scaleYBits = 1f.toRawBits(),
    skewXBits = 0f.toRawBits(), skewYBits = 0f.toRawBits(),
)
val opaque = GPUCorePrimitiveRRectGeometryAuthority.issue(
    source = sealedFacts,
    normalized = sealedFacts,
    transform = identityTransformFacts,
    device = sealedFacts,
) ?: return null
~~~

Ce wrapper ne normalise ni ne transforme. Il ne reçoit ni GPURRect ni
GPUTransformFacts et n’appelle ni GPURRectNormalizer ni
corePrimitiveRRectGeometryAuthority(...). Le lowerer construit pour chaque
AnalyticRRectDraw, y compris un RECT à huit +0, un semantic
GPUCorePrimitiveSourceFamily.RRect avec geometryInput() et
rrectGeometryAuthority = authority(). Ainsi le builder actuel
buildCorePrimitiveAnalyticShapeUniform reçoit l’autorité opaque qu’il exige,
sans modifier GPUCorePrimitiveAnalyticShapeUniformAbi.kt ni
PayloadContracts.kt.

Lors de la création de GPUCorePrimitivePayloadInput, le lowerer renseigne
sourceFamily=GPUCorePrimitiveSourceFamily.RRect,
geometry=plannedAuthority.geometryInput() et
rrectGeometryAuthority=plannedAuthority.authority(),
analysisRecordId formé par la chaîne analysis.fill_rrect suivie du
commandIndex décimal, et analysisCommandFamily=FillRRect, en plus des faits
de couleur, target, scissor, ScalarAA, SrcOver et identity déjà présents dans
le lowerer W4b. Le test d’ABI appelle le builder existant avec ce semantic et son
GPUCorePrimitivePreparedSemanticAuthority; il attend Accepted et compare les
80 bytes. Il ne réclame aucune nouvelle surcharge ou branche du builder.

### Task 1: Normalisation RRectF32 backend-neutral et parité JVM/JS

**Files:**

- Create: math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RRectNormalizationF32.kt
- Create: math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RRectNormalizationF32Test.kt
- Modify: math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/AxisAlignedGeometryF32Test.kt
- Do not modify: math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathBuilder.kt

**Interfaces:**

- Consumes: RRectF32, CornerRadiiF32, RectF32 et RRectF32.mapAxisAligned.
- Produces: les trois types/functions :math listés dans Interfaces partagées.
- Used later by: W4bAnalyticRRectPlanCompiler; jamais par le renderer.

- [ ] **Step 1: Écrire les tests RED avec fixtures dérivées**

  Dans RRectNormalizationF32Test, ajouter :

  ~~~kotlin
  @Test
  fun normalizationScalesEveryCornerByOneF64Factor() {
      val source = RRectF32.of(
          RectF32(0f, 0f, 10f, 10f),
          CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(8f, 8f),
          CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(8f, 8f),
      )
      val shape = accepted(source.normalizeForAnalyticFillF32())
      assertEquals(CornerRadiiF32.of(5f, 5f), shape.topLeft)
      assertEquals(CornerRadiiF32.of(5f, 5f), shape.topRight)
      assertEquals(CornerRadiiF32.of(5f, 5f), shape.bottomRight)
      assertEquals(CornerRadiiF32.of(5f, 5f), shape.bottomLeft)
  }

  @Test
  fun normalizationCanonicalizesEitherZeroPairToPositiveZero() {
      val source = RRectF32.of(
          RectF32(0f, 0f, 4f, 4f),
          CornerRadiiF32.of(-0f, 3f), CornerRadiiF32.of(2f, 0f),
      )
      val shape = accepted(source.normalizeForAnalyticFillF32())
      assertEquals(0f.toRawBits(), shape.topLeft.x.toRawBits())
      assertEquals(0f.toRawBits(), shape.topLeft.y.toRawBits())
      assertEquals(0f.toRawBits(), shape.topRight.x.toRawBits())
      assertEquals(0f.toRawBits(), shape.topRight.y.toRawBits())
  }
  ~~~

  Ajouter des cas isolés pour bounds NaN, bounds inversées, rayon infini et
  rayon négatif, qui observent RRectNormalizationF32Rejection. Ajouter le
  fixture ULP fixe sur RectF32(0f,0f,1f,1f) : TL=(Float.fromBits(0x3b09a031),
  0.001f), TR=(Float.fromBits(0x3faf3eb7),0.001f), BR=(0.001f,0.001f),
  BL=(0.001f,0.001f). Avant correction, les deux X top arrondis sont
  Float.fromBits(0x3ac8bccb) et Float.fromBits(0x3f7f9ba2), dont la somme
  dépasse 1f. Après normalisation, TR.x vaut exactement
  Float.fromBits(0x3f7f9ba1) et les quatre sommes Double sont ≤ leurs côtés.

  Dans AxisAlignedGeometryF32Test, mapper TL(1,2), TR(3,4), BR(5,6), BL(7,8)
  par scale(-2,3). Attendre device TL=(6,12), TR=(2,6), BR=(14,24),
  BL=(10,18), puis vérifier les contraintes après normalisation.

- [ ] **Step 2: Lancer RED sur les deux plateformes**

  ~~~bash
  rtk ./gradlew :math:geometry:jvmTest --tests '*RRectNormalizationF32Test*' --rerun-tasks
  rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
  ~~~

  Expected: RED de compilation, symbole normalizeForAnalyticFillF32 absent.

- [ ] **Step 3: Écrire l’implémentation minimale :math**

  RRectNormalizationF32.kt ne référence aucun API GPU. Il vérifie finitude,
  non-vacuité et rayons ≥0, canonise toute paire avec une composante zéro en
  +0f,+0f, puis calcule en Double le facteur commun minimum des contraintes :

  ~~~text
  TL.x + TR.x <= width; BL.x + BR.x <= width
  TL.y + BL.y <= height; TR.y + BR.y <= height
  ~~~

  Convertir les résultats en Float. Après conversion, réparer seulement une
  violation de rounding par un ULP positif suivant l’ordre déterministe
  TR.x, BR.x, BL.y, BR.y pour top, bottom, left, right. La primitive ULP
  privée utilise toRawBits/fromBits, ne crée jamais -0f et ne change pas la
  forme avant la conversion.

- [ ] **Step 4: Lancer GREEN et parité**

  ~~~bash
  rtk ./gradlew :math:geometry:jvmTest --tests '*RRectNormalizationF32Test*' --rerun-tasks
  rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
  rtk ./gradlew :math:matrix:jvmTest --tests '*AxisAlignedGeometryF32Test*' --rerun-tasks
  rtk ./gradlew :math:matrix:jsNodeTest --rerun-tasks
  ~~~

  Expected: GREEN; mêmes bits F32 acceptés sous JVM/JS et rejets identiques.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra contrôle facteur F64 commun, +0, parité et absence de backend avant
  de committer. Sol n’est pas encore sollicité.

  ~~~bash
  rtk git add math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RRectNormalizationF32.kt math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RRectNormalizationF32Test.kt math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/AxisAlignedGeometryF32Test.kt
  rtk git commit -m "feat: normalize analytic rrect geometry"
  ~~~

  Le controller génère ensuite le package de review pour Sol. Sol contrôle
  les mêmes invariants sur le commit; Terra résout ses findings dans un commit
  correctif avant Task 2.

### Task 2: PlanDraw par primitive, capability facts et budget W4b

**Files:**

- Modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt
- Create: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectPlanBudget.kt
- Create: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bPlanDiagnostics.kt
- Create: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectDrawTest.kt
- Create: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectPlanBudgetTest.kt
- Modify: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRectPlanBudgetTest.kt

**Interfaces:**

- Consumes: Task 1 types, DrawOrigin, PlanResource and PlanCapabilitySnapshot.
- Produces: AnalyticRRectDraw par primitive, budget sibling et diagnostics W4b.
- Used later by: compiler W4b et lowerer W4b.

- [ ] **Step 1: Écrire RED du draw et du footprint**

  Dans AnalyticRRectDrawTest, créer deux objets distincts : command 0,
  origin RECT, huit +0; command 1, origin RRECT, TL(1,2), TR(2,1), BR(1,3),
  BL(3,1). Vérifier commandIndex, copie shape, origin, ScalarAA,
  SingleSample et SrcOver. Le cas origin PATH doit lever
  IllegalArgumentException. Ce test prouve une primitive par PlanDraw, pas un
  batch.

  Dans AnalyticRRectPlanBudgetTest, avec le snapshot W4a alignment 256,
  floors 16384/4096/4096, target 4×3, N=2 et budget 25392, attendre :

  ~~~kotlin
  assertEquals(48L, footprint.targetBytes)
  assertEquals(768L, footprint.readbackBytes)
  assertEquals(64L, footprint.vertexUsefulBytes)
  assertEquals(48L, footprint.indexUsefulBytes)
  assertEquals(256L, footprint.uniformStrideBytes)
  assertEquals(512L, footprint.uniformUsefulBytes)
  assertEquals(16_384L, footprint.vertexCapacityBytes)
  assertEquals(4_096L, footprint.indexCapacityBytes)
  assertEquals(4_096L, footprint.uniformCapacityBytes)
  assertEquals(25_392L, footprint.peakBytes)
  ~~~

  Ajouter N=0 Invalid et budget 25391 Exceeded. Le test W4a existant reste
  vert avec ses nombres publiés.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :gpu-plan:test --tests '*AnalyticRRectDrawTest*' --tests '*AnalyticRRectPlanBudgetTest*' --rerun-tasks
  ~~~

  Expected: RED de compilation, types W4b absents.

- [ ] **Step 3: Implémenter sans généraliser W4a**

  Ajouter AnalyticRRectDraw à PlanPasses.kt, sibling de AnalyticRectDraw :
  origin seulement RECT/RRECT, RRectF32 déjà normalisé/validé, raster/scissor
  non vides, copies défensives; aucune liste de primitives ou compteur batch.

  AnalyticRRectPlanBudget utilise Math.multiplyExact/Math.addExact, 4 bytes
  pixel, 32 vertex/draw, 24 index/draw et Uniform80. Le pic est :

  ~~~text
  target + readback + vertexCapacity + indexCapacity + uniformCapacity
  ~~~

  LogicalTarget, VertexData, IndexData, UniformData vivent [0,2);
  ReadbackStaging vit [1,2). Pipeline/bind group ne comptent pas.
  W4bPlanDiagnostics expose les mêmes classes de diagnostic stables que W4a,
  sous préfixe w4b.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :gpu-plan:test --tests '*AnalyticRRectDrawTest*' --tests '*AnalyticRRectPlanBudgetTest*' --tests '*AnalyticRectPlanBudgetTest*' --rerun-tasks
  ~~~

  Expected: GREEN et footprint exact à cinq ressources.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra vérifie granularité par primitive, cinq PlanResource et pic checked
  avant le commit.

  ~~~bash
  rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectPlanBudget.kt gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bPlanDiagnostics.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectDrawTest.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRRectPlanBudgetTest.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/AnalyticRectPlanBudgetTest.kt
  rtk git commit -m "feat: model W4b analytic rrect plan"
  ~~~

  Le controller envoie le package post-commit à Sol; Sol review sans modifier
  le code, puis Terra traite tout finding avant Task 3.

### Task 3: Selector/compiler W4b et graphe fermé

**Files:**

- Create: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt
- Create: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompilerTest.kt
- Modify: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt
- Do not modify: gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt

**Interfaces:**

- Consumes: Tasks 1–2, GeometryNode.Rect/RRect, DrawOrigin, RenderGraph.
- Produces: select/plan W4b et exactement cinq PlanResource.
- Used later by: GpuRenderContext et lowerer W4b.

- [ ] **Step 1: Écrire RED d’admission**

  Créer fixtures SceneSnapshot 4×4 sRGB, SolidColor/SrcOver/ANTIALIASED :

  1. RRect RRECT TL(1,1), TR(2,1), BR(1,2), BL(0.5,1), attendu Ready W4b;
  2. Rect RECT puis ce RRect, attendu deux AnalyticRRectDraw ordonnés,
     origins RECT/RRECT et huit +0 pour le Rect;
  3. Rect seuls, attendu NotCandidate W4b;
  4. RRect geometry avec origin RECT, attendu NotCandidate;
  5. clip AA/path/RRect, rotation, shader paint, SRC, MSAA, non-sRGB,
     attendu NotCandidate;
  6. rayon négatif ou NaN, attendu InvalidScene;
  7. 512 draws dont un RRECT, attendu Ready; 513, attendu NotCandidate.

  Vérifier exactement les rôles LogicalTarget, ReadbackStaging, VertexData,
  IndexData, UniformData et lifetimes 0,1,0,0,0. Le pic doit être la somme
  checked des cinq byteSize.

  Dans CapabilityCompilerChainTest, la chaîne
  W3SolidRectPlanCompiler, W4aAnalyticRectPlanCompiler,
  W4bAnalyticRRectPlanCompiler sélectionne W4b pour le RRect et conserve W4a
  pour un Rect fractionnaire. Assertion sur capabilityId public seulement.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :gpu-plan:test --tests '*W4bAnalyticRRectPlanCompilerTest*' --tests '*CapabilityCompilerChainTest*' --rerun-tasks
  ~~~

  Expected: RED, compiler W4b absent.

- [ ] **Step 3: Implémenter reconnaissance et graph**

  Créer W4bAnalyticRRectPlanCompiler sans toucher W4a. Il accepte uniquement
  Rect+origin RECT et RRect+origin RRECT, requiert au moins un RRECT dans la
  frame, normalise source dans :math, mapAxisAligned, renormalise les faits
  device dans :math, calcule raster floor/ceil et scissor entier.

  Rect devient un RRectF32 à huit +0 mais conserve origin RECT. Chaque
  commande devient un AnalyticRRectDraw. Le compiler crée RenderPass puis
  ReadbackPass, ressources exactes W4a et budget Task 2. Les scènes hors
  enveloppe sont refusées avant Ready; toute divergence post-Ready est
  terminale, sans promotion ni route directe.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :gpu-plan:test --tests '*W4bAnalyticRRectPlanCompilerTest*' --tests '*CapabilityCompilerChainTest*' --tests '*W4aAnalyticRectPlanCompilerTest*' --rerun-tasks
  ~~~

  Expected: GREEN; chaîne W3→W4a→W4b et frontière 512/513 stables.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra relit admission, provenance RRECT, normalisation :math et refus
  terminal avant le commit.

  ~~~bash
  rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompilerTest.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChainTest.kt
  rtk git commit -m "feat: compile W4b analytic rrect graphs"
  ~~~

  Le controller construit le package puis sollicite Sol; tout finding est
  corrigé par Terra dans un commit avant Task 4.

### Task 4: Lowerer, scratch et authority W4b scellés

**Files:**

- Create: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt
- Create: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4bPreparedAuthority.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt
- Verify only: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveAnalyticShapeUniformAbi.kt
- Verify only: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt
- Create: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4bTest.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt

**Interfaces:**

- Consumes: Ready graph W4b et AnalyticRRectDraw.
- Produces: W4bSessionScratchDrawV1, W4bSessionScratchV1 et
  GPUCorePrimitiveW4bPlannedRRectAuthority.
- Used later by: assembler/preflight/materializer W4b.

- [ ] **Step 1: Écrire RED terminal**

  Compiler la fixture mixte Task 3, lower via GpuPlanTaskListLowerer et
  vérifier Lowered, packets paint order 0/1 et markers w4b publics. Construire
  trois graphes avec factories publiques, chacun avec une contradiction :
  AnalyticRectDraw injecté, UniformData lifetime [1,2), ou RRECT changé en
  RECT. Tous retournent InvalidPlan; aucun ne retourne Lowered,
  UnsupportedCapability, W3 ou W4a. Snapshot device divergent retourne
  UnsupportedCapability.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4bTest*' --tests '*GpuPlanTaskListLowererTest*' --rerun-tasks
  ~~~

  Expected: RED, lowerer/scratch W4b absents.

- [ ] **Step 3: Implémenter le sibling sealed**

  Ajouter le case capability W4b dans GpuPlanTaskListLowerer.lower vers
  W4bAnalyticRRectGraphLowerer.lower. Le lowerer exige capability, format,
  passes Render/Readback, cinq rôles/usages/lifetimes, pic checked, ScalarAA,
  sRGB 1x, SrcOver, une liste AnalyticRRectDraw et au moins un RRECT.

  W4bSessionScratchDrawV1 copie RRectF32 device, origin, raster/scissor,
  packet/command IDs. W4bSessionScratchV1 conserve ordre, capacities V/I/U,
  Uniform80 stride/seals/target/staging/limites. La planned authority copie
  les douze bits device du scratch, émet l’autorité opaque existante avec
  source=normalized=device et transform Identity, puis fournit
  GPUCorePrimitiveGeometryInput.RRect et cette autorité au semantic RRect.
  Même les RECT à rayons +0 utilisent ce semantic RRect tout en conservant
  leur provenance RECT dans le scratch et le PlanDraw. Ajouter
  w4bSessionScratch à GPUCorePrimitivePreparedPacketAuthority avec invariant :
  exactement une scratch W3, W4a ou W4b.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :gpu-renderer:test --tests '*GpuPlanTaskListLowererW4bTest*' --tests '*GpuPlanTaskListLowererW4aTest*' --tests '*GpuPlanTaskListLowererTest*' --rerun-tasks
  ~~~

  Expected: GREEN; les contradictions restent terminales.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra relit les invariants de la tâche, le diff et les sorties GREEN avant
  de committer. Sol n’est pas encore sollicité.

  ~~~bash
  rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4bPreparedAuthority.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererW4bTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowererTest.kt
  rtk git commit -m "feat: lower sealed W4b rrect graphs"
  ~~~

  Après ce commit, le controller prépare le package de review puis sollicite
  Sol. Sol vérifie l’absence W4b de GPURRectNormalizer,
  corePrimitiveRRectGeometryAuthority et GPUCorePrimitiveDirectNativeRoute;
  Terra traite tout finding dans un commit correctif avant Task 5.

### Task 5: Assembler, preflight, materializer et pool

**Files:**

- Create: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4bPreparedFrameTaskListAssembler.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt
- Verify only: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePool.kt
- Verify only: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitiveAnalyticShapeUniformAbi.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveAnalyticShapeUniformAbiTest.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt

**Interfaces:**

- Consumes: Task 4 scratch/authority et ABI AnalyticShape actuel.
- Produces: tâche w4b prepare→render→readback, preflight et payload Uniform80.
- Does not produce: shader, pipeline, bind group ou ressource additionnelle.

- [ ] **Step 1: Écrire RED ABI/lifecycle**

  Fixture : Rect bleu opaque (0,0,2,2), puis RRect rouge alpha 128
  (1,1,4,4), TL(1,1), TR(2,1), BR(1,2), BL(0.5,1). Après lower/preflight/
  materialize, vérifier deux slots 80, stride 256, offsets 0/256, premier
  huit +0, second bounds/rayons planifiés, target/staging et unique lease V/I/U.

  Ajouter refus pour slot 79 bytes, offset non aligné et scissor scratch
  différent du PlanDraw. Les fixtures utilisent lowerer/factories publiques,
  jamais reflection ou méthode privée. Dans AnalyticShapeUniformAbiTest,
  passer le semantic RRect émis par la planned authority au builder existant,
  vérifier Accepted et les offsets 32/48/64. Ne modifier ni WGSL ni
  GPUCorePrimitiveAnalyticShapeUniformAbi.kt.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :gpu-renderer:test --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUCorePrimitiveAnalyticShapeUniformAbiTest*' --rerun-tasks
  ~~~

  Expected: RED, marker/assembler/materializer W4b absents.

- [ ] **Step 3: Implémenter les siblings**

  Assembler W4b prend plan ID, resource/pass IDs, target/staging, readback,
  memory budget, sizes V/I/U/stride et snapshots W4b. Il produit
  PrepareResources, Render, Readback et dépendances w4b.

  Preflighter ajoute un marker/validateur W4b distinct : un render envelope,
  scratch commun, cinq ressources/lifetimes, Uniform80, AnalyticShape/
  DirectTriangleList, ScalarAA, 1x, SrcOver, sRGB, origin RRECT présent et
  scissor exact. Materializer traite ce marker avant générique, écrit les
  bytes depuis planned authority/scratch, réserve une seule lease aux
  capacities graph et conserve resources jusqu’à completion/readback.

  Le pool est vérifié comme agnostique à la géométrie : la fixture W4b doit
  conserver les mêmes règles de lease/reuse que W4a sans modifier
  GPUWgpu4kCorePrimitiveFramePool.kt. Ajouter dans son test existant le
  scénario W4b completion/readback qui observe qu’un lease n’est réutilisable
  qu’après la dernière passe. Ne jamais créer de préparation ordinary V/I/U.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :gpu-renderer:test --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUCorePrimitiveAnalyticShapeUniformAbiTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --rerun-tasks
  ~~~

  Expected: GREEN; target/V/I/U [0,2), staging [1,2), refus terminal.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra vérifie ABI inchangé, authority opaque existante, lease unique et
  refus terminal avant le commit.

  ~~~bash
  rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitiveW4bPreparedFrameTaskListAssembler.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUCorePrimitiveAnalyticShapeUniformAbiTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePoolTest.kt
  rtk git commit -m "feat: materialize W4b analytic rrect frames"
  ~~~

  Le controller produit le package de review après le commit; Sol vérifie
  scellement/resource/lifecycle et Terra corrige tout finding avant Task 6.

### Task 6: Gate Surface, context et token public

**Files:**

- Modify: kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt
- Modify: gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt
- Modify: gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt
- Verify only: kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt
- Verify only: kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt

**Interfaces:**

- Consumes: DrawRRect déjà capturé en GeometryNode.RRect/origin RRECT, compiler W4b.
- Produces: gate compositionnelle DrawRRect et chaîne W3→W4a→W4b.
- Public: mêmes router/token Ready existants, sans token W4b nouveau.

- [ ] **Step 1: Écrire RED du parcours public**

  GPUPreparedSurfaceFrameGateTest : DrawRRect devient Candidate pour
  RGBA8_UNORM_SRGB; DrawDRRect/DrawPath restent exclus. GPUPlanSurfaceRouterTest :
  Surface 4×4 Rect bleu + RRect rouge asymétrique AA/SrcOver/scissor total
  retourne résultat préparé W4b; clip RRect et gradient deviennent legacy
  avant Ready. GpuRenderContextTest attend capability W4b pour cette frame,
  W4a pour Rect fractionnaire seul.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :kanvas:test --tests '*GPUPreparedSurfaceFrameGateTest*' --tests '*GPUPlanSurfaceRouterTest*' --rerun-tasks
  rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderContextTest*' --rerun-tasks
  ~~~

  Expected: RED, gate refuse DrawRRect et context ne connaît pas W4b.

- [ ] **Step 3: Ouvrir deux sélecteurs seulement**

  Ajouter DisplayOp.DrawRRect à GPUPlanSurfaceCandidateGate.accepts. Dans
  GpuRenderContext.plan, configurer exactement :

  ~~~kotlin
  CapabilityCompilerChain.of(
      listOf(
          W3SolidRectPlanCompiler(),
          W4aAnalyticRectPlanCompiler(),
          W4bAnalyticRRectPlanCompiler(),
      ),
  )
  ~~~

  Ne modifier DisplayOpSceneAdapter que si la provenance RRECT manque, ni
  router sauf exclusion explicite du capabilityId W4b. Aucun chemin parallèle.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :kanvas:test --tests '*GPUPreparedSurfaceFrameGateTest*' --tests '*GPUPlanSurfaceRouterTest*' --tests '*DisplayOpSceneAdapterTest*' --rerun-tasks
  rtk ./gradlew :gpu-renderer:test --tests '*GpuRenderContextTest*' --rerun-tasks
  ~~~

  Expected: GREEN; DrawDRRect/path/état hors capability ne deviennent pas W4b.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra vérifie que la gate reste compositionnelle et que la chaîne est
  W3→W4a→W4b avant le commit.

  ~~~bash
  rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContextTest.kt
  rtk git commit -m "feat: route W4b rrect surface frames"
  ~~~

  Le controller sollicite Sol après commit; Terra corrige tout finding avant
  Task 7.

### Task 7: Oracle indépendant, pixels, bornes et dette SDF

**Files:**

- Create: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4bAnalyticRRectCpuOracle.kt
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt
- Modify: kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt
- Modify: gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompilerTest.kt

**Interfaces:**

- Consumes: ColorARGB, RRectF32, RectI32 et Surface publics.
- Produces: W4bAnalyticRRectCpuOracle.render(width,height,draws,format).
- Does not consume: shader executable, materializer, private helper, GM/Skia.

- [ ] **Step 1: Écrire RED oracle/pixels**

  Créer le contrat test-only :

  ~~~kotlin
  internal object W4bAnalyticRRectCpuOracle {
      internal data class Draw(
          val color: ColorARGB, val shape: RRectF32, val scissor: RectI32,
      )
      fun render(
          width: Int, height: Int, draws: List<Draw>,
          format: PixelFormat = PixelFormat.RGBA8,
      ): UByteArray
  }
  ~~~

  Ajouter ces scènes littérales dans GPUPlanSurfacePixelTest :

  1. RRect blanc 2×2, huit +0, pixels 255/255/255/255;
  2. RRect rouge opaque 3×3, quatre rayons 1, neuf pixels oracle;
  3. Rect bleu opaque puis RRect rouge alpha 128, et ordre inverse : buffers
     oracle différents;
  4. RRect asymétrique TL(1,1),TR(2,1),BR(1,2),BL(0.5,1), scale(-1,1),
     scissor (1,0,4,4), transparence exacte hors scissor;
  5. 512 draws mixtes avec RRECT Ready, 513 non-W4b.

- [ ] **Step 2: Lancer RED**

  ~~~bash
  rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*' --tests '*GPUPlanSurfaceRouterTest*' --rerun-tasks
  rtk ./gradlew :gpu-plan:test --tests '*W4bAnalyticRRectPlanCompilerTest*' --rerun-tasks
  ~~~

  Expected: RED, oracle W4b absent et assertions pixels non satisfaites.

- [ ] **Step 3: Écrire l’oracle indépendant**

  Dans le support de test, reproduire indépendamment le WGSL actuel :
  edge distances, corner_distance TL/TR/BR/BL, overlap rectaire exact quand
  huit +0, sinon scale=min(width,height) clamp [0,1],
  bias=1-0.5*scale et clamp(scale*(distance+bias),0,1). Composer SrcOver
  linear prémultiplié, quantifier sRGB entre draws et encoder RGBA/BGRA.

  Aucun import renderer. Aucun seuil : un écart est un failure. Les tests
  documentent la SDF native actuelle, ne comparent pas Skia et ne modifient
  ni shader ni tolérance.

- [ ] **Step 4: Lancer GREEN**

  ~~~bash
  rtk ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest*' --tests '*GPUPlanSurfaceRouterTest*' --rerun-tasks
  rtk ./gradlew :gpu-plan:test --tests '*W4bAnalyticRRectPlanCompilerTest*' --rerun-tasks
  ~~~

  Expected: GREEN, bytes expliqués par oracle et ordre SrcOver conservé.

- [ ] **Step 5: Self-review Terra, commit, puis review Sol controller**

  Terra vérifie l’indépendance de l’oracle, l’absence de seuil et les
  exclusions GM/Skia avant le commit.

  ~~~bash
  rtk git add kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W4bAnalyticRRectCpuOracle.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/GPUPlanSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouterTest.kt gpu-plan/src/test/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompilerTest.kt
  rtk git commit -m "test: prove W4b analytic rrect pixels"
  ~~~

  Le controller transmet le package post-commit à Sol; Terra résout les
  findings éventuels avant Task 8.

### Task 8: Documentation, verification et PR stackée

**Files:**

- Modify: refactor/waves/W04-geometry-coverage/status.md
- Modify: refactor/README.md
- Verify: refactor/specs/2026-09-04-w4b-analytic-rrect-design.md
- Verify: refactor/plans/2026-09-04-w4b-analytic-rrect-implementation-plan.md
- Do not modify: integration-tests/skia, font, codec, jpg-color-cube, dashboard GM.

**Interfaces:**

- Consumes: sorties GREEN Tasks 1–7.
- Produces: statut W04, ledger XML nominatif et package de publication.

- [ ] **Step 1: Préparer le relevé evidence/docs**

  Relire les critères de sortie de la spec et préparer les sections W4b de
  status.md : normalisation JVM/JS, PlanDraw par primitive/origin RRECT, cinq
  resources/lifetimes/pic, Uniform80, refus terminal, oracle non-GM et dette
  SDF. Cette étape est une checklist documentaire, pas un test RED ni un test
  d’infrastructure; elle ne consigne aucun résultat avant les exécutions
  fraîches.

- [ ] **Step 2: Exécuter les vérifications fraîches ciblées**

  ~~~bash
  rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
  rtk ./gradlew :render-ir:test :gpu-plan:test
  rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --tests '*GPUCorePrimitiveAnalyticShapeUniformAbiTest*'
  rtk ./gradlew :kanvas:test --tests '*GPUPlanSurface*' --tests '*SurfaceTest*' --tests '*DisplayOpSceneAdapterTest*'
  ~~~

  Expected: toutes les commandes ciblées passent. Une failure bloque
  l’evidence/docs, revient à la tâche Terra responsable et repasse par
  RED→GREEN→self-review→commit avant une nouvelle exécution fraîche.

- [ ] **Step 3: Exécuter le run global et inventorier le ledger XML**

  ~~~bash
  rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest :render-ir:test :gpu-plan:test
  rtk ./gradlew :gpu-renderer:test --tests '*Gpu*Plan*' --tests '*GpuRender*' --tests '*GPUFramePreflighterTest*' --tests '*GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest*' --tests '*GPUWgpu4kCorePrimitiveFramePoolTest*' --tests '*GPUCorePrimitiveAnalyticShapeUniformAbiTest*'
  rtk ./gradlew :kanvas:test --rerun-tasks
  rtk rg -n '<failure|<error' kanvas/build/test-results/test/TEST-*.xml
  rtk git diff --check
  ~~~

  Exclure explicitement GM, :integration-tests:skia et jpg-color-cube. Le
  status doit inventorier les seuls failures XML connus par noms :

  - ImageTest :: ColorType enum values();
  - GPUAllApiBlendSurfaceTest :: DrawPoint pour les 45 combinaisons
    PLUS, MULTIPLY, OVERLAY, DARKEN, LIGHTEN, COLOR_DODGE, COLOR_BURN,
    HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION, HUE, SATURATION, COLOR,
    LUMINOSITY × UNCLIPPED, SCISSOR, ALPHA_MASK;
  - GPUMaskBlurDispatchTest :: local path mask scales dash intervals and phase();
  - GPUPreparedSurfaceFrameBuilderTest :: public non finite singular and
    perspective transforms refuse before frame task assembly();
  - GPUPreparedSurfaceFrameBuilderTest :: prepared atlas expands to ordered
    sampled packets sharing one artifact with distinct uniforms();
  - GPUPreparedTextStrokeTest :: prepared stroke path key seals exact geometry
    and verb count seals every contour();
  - GPURefusalGuardsTest :: direct fill guard refuses radial and sweep non
    identity matrix facts before dispatch().

  Attendre aucun XML error ni nouveau failure W4b. Tout nom différent bloque
  la publication et doit être attribué/corrigé.

- [ ] **Step 4: Mettre à jour les documents à partir des sorties fraîches**

  status.md consigne capability, cinq resources/lifetimes, commandes réelles,
  ledger, exclusions et dette : SDF native non exacte Skia, nouveau shader
  seulement après divergence matérielle intégration Skia, aucune tolérance
  masquante, réévaluation W7. README ajoute spec/plan W4b et état W4a+W4b,
  W4c+ ouvertes.

- [ ] **Step 5: Self-review Terra et commit evidence/docs**

  ~~~bash
  rtk git add refactor/waves/W04-geometry-coverage/status.md refactor/README.md
  rtk git commit -m "docs: verify W4b analytic rrect slice"
  rtk git diff --check
  rtk git status --short
  rtk git log --oneline codex/w4a-scalar-aa-rect..HEAD
  ~~~

  Terra vérifie les résultats, les noms XML et le diff avant ce commit. Après
  commit, le controller génère le package complet et Sol fait la review
  externe. Après review Sol sans finding bloquant et vérification finale, le
  controller déjà autorisé pousse et crée la PR :

  ~~~bash
  rtk git push -u origin codex/w4b-analytic-rrect
  rtk gh pr create --base codex/w4a-scalar-aa-rect --head codex/w4b-analytic-rrect --title "W4b analytic RRect" --body "Implements solid-rect-rrect-scalar-aa-simple-scissor-src-over-srgb-v1 with math normalization, sealed Uniform80 execution, and non-GM pixel evidence."
  ~~~

  Expected: arbre propre, diff check sans sortie, PR base
  codex/w4a-scalar-aa-rect et head codex/w4b-analytic-rrect.

## Self-review Writing Plans

- Couverture de spec : Task 1 couvre normalisation/math et JVM/JS; Task 2
  modèle/budget; Task 3 admission/capability/chaîne; Task 4 scellement;
  Task 5 ressources/ABI/preflight/materialization; Task 6 Surface/token;
  Task 7 oracle/pixels/dette; Task 8 sorties, baseline, docs et PR.
- Placeholders : aucun; Tasks 1–7 ont fichiers, interfaces, fixture,
  commande RED, attente RED, implémentation, commande GREEN, self-review et
  commit. Task 8 est intentionnellement evidence/docs, avec exécutions
  fraîches, ledger XML, self-review, commit, review Sol puis publication
  controller.
- Cohérence de types : RRectNormalizationF32Result est produit Task 1 et
  consommé Task 3; AnalyticRRectDraw/AnalyticRRectPlanBudget Task 2 sont
  consommés Tasks 3–5; W4bSessionScratch Task 4 est consommé Task 5;
  GPUCorePrimitiveW4bPlannedRRectAuthority Task 4 émet l’autorité opaque
  GPUCorePrimitiveRRectGeometryAuthority existante, consommée sans
  modification par le builder Uniform80 Task 5; l’oracle Task 7 est
  strictement test-only.
- Workflow : chaque Task 1–7 ordonne RED→GREEN→self-review Terra→commit,
  puis package controller→review Sol. Task 8 est evidence/docs normale,
  suivie de commit Terra, package/review Sol et publication controller.
- Périmètre : aucun shader nouveau, aucune géométrie hors :math, aucune
  ressource additionnelle, aucun GM/Skia/font/codec/jpg-color-cube et aucun
  changement de seuil.

## Handoff

Exécuter strictement Task 1 à Task 8. Pour Tasks 1–7, Terra suit
RED→GREEN→self-review→commit; le controller génère ensuite le package et
Sol review le commit sans coder. Terra résout un finding Sol par un nouveau
commit avant la tâche suivante. Task 8 suit evidence fraîche→docs→self-review
Terra→commit→package controller→review Sol→push/PR controller autorisée.
W7, pas W4b, décide un éventuel nouveau shader si l’intégration Skia révèle
une divergence matérielle.
