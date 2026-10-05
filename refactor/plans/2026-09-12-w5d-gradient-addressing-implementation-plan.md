# W5d Gradient Addressing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ordered local-coordinate wrappers and the four Skia gradient tile modes to the sealed W5 material path for Linear, Radial, Sweep and Conical gradients on Rect, RRect, Path fill and Path stroke/hairline, with exact public pixel evidence and no legacy fallback after ownership.

**Architecture:** `:kanvas` captures and admits only the bounded W5d wrapper grammar. `:gpu-plan` builds an immutable `MaterialCoordinatePlanV2`, composes and inverts adjacent matrices through `:math:matrix` in F64, preserves every `CoordClamp` boundary, seals a shared tile graph and authenticates every value before `Ready`. `:gpu-renderer` consumes the sealed topology and values, applies projective transforms with a validity mask, tiles the family parameter and reuses the W5c stop slab plus the W5b final blend. Geometry remains owned by W4 lanes and all `RectF32`/matrix values come from `:math:geometry`/`:math:matrix`.

**Tech Stack:** Kotlin/JVM and Kotlin/JS, `:math:geometry`, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit 5.

**Spec:** `refactor/specs/2026-09-12-w5d-gradient-addressing-design.md`, governed by `refactor/specs/2026-09-09-w5-material-graph-design.md` sections 7–8 and the sealed W5a/W5b/W5c contracts.

## Global Constraints

- Branch `codex/w5d-gradient-addressing` is stacked on `codex/w5c-gradients`; its PR targets `codex/w5c-gradients`.
- The W5d grammar is a W5c Linear/Radial/Sweep/Conical leaf surrounded by bounded `Opacity`, `WithLocalMatrix` and `CoordClamp` nodes in any order. Interpolation remains sRGB.
- Tile modes are exactly `CLAMP`, `REPEAT`, `MIRROR` and `DECAL`. Sweep full coverage forces effective CLAMP. An invalid Conical root is transparent before tile evaluation.
- W5d changes no W4 geometry, tessellation, stroke, clip, stencil, coverage or AA topology and creates no geometry type outside `:math:geometry`. Matrix composition/inversion lives only in `:math:matrix`.
- Every new public numeric Kotlin property or parameter uses an exact I32/I64/U32/F32/F64 suffix. Existing unsuffixed APIs and fixed WGSL ABI identifiers are not renamed opportunistically.
- `MaterialCoordinatePlanV1` remains the historical W5c authority. W5d adds `MaterialCoordinatePlanV2`; it must not silently reinterpret or rewrite V1 instances.
- Coordinate evaluation starts with inverse CTM, then applies inverse local matrices from the outermost wrapper toward the leaf. Adjacent matrices may be composed in F64, but never across `CoordClamp`; adjacent clamps are never merged.
- A matrix is copied before validation. Non-finite matrices, F64-singular segments, non-F32-representable inverses, non-finite subsets and unsorted subsets refuse with the exact public diagnostics in the spec.
- A projective point with zero/non-finite `w` or a non-finite quotient becomes safe point `(0,0)` plus invalid. Invalid values never reach `floor`, `atan2`, stop search or blend; final source is transparent.
- The program key contains gradient family, requested/effective tile graph version and coordinate operation tags, never matrix coefficients, clamp bounds, stops, opacity or family scalar values.
- The binding stores immutable coordinate values and layout. It remains within checked I64/Int/device limits. W5d adds no native handle, stop buffer, LUT, per-draw stop buffer or inter-frame cache.
- The W5c stop buffer ABI stays byte-for-byte unchanged: one frame-local storage buffer, 32-byte little-endian stop stride, zero reserved fields, group 1 binding 1.
- Degenerate CLAMP returns the normative last/special family result; DECAL returns transparent; REPEAT/MIRROR use the exact normalized straight-sRGB trapezoid average computed once on CPU from exact F32 binary rationals and projected once roundTiesToEven.
- A W5d-admitted draw is terminal after ownership. Validation, budget, allocation, compilation, submission and readback failures do not consult a legacy material provider.
- Public behavior tests use only `Surface`, `Canvas`, `Picture`, `render()`, public pixels, public typed diagnostics/exceptions and public math values. They do not assert reflection, private state, source shape, counters, call counts, packets, allocator state, bind groups, resource lifetimes or uniform bytes.
- The independent test oracle does not call production graph factories, production tile helpers or production average helpers. Exact pixel fixtures must close to a singleton or two adjacent RGBA8 codes through `WgslFloatEnvelopeV1`; similarity thresholds are forbidden.
- AA4/capability evidence is authentic: exact pixels when available, otherwise the existing precise public skip/refusal. No fake adapter/device or failpoint is introduced.
- Fonts, codecs, GM, dashboard, render regeneration, Skia integration, baselines and `jpg-color-cube` remain excluded. `gpu-plan`/`gpu-renderer` infrastructure tests are not added or used as rendering proof.
- Implementers use Astra. Task and final code reviewers use Sol only. The written plan itself receives the explicitly requested Astra review before Task 1.
- Durable tracking stays in this plan, `refactor/waves/W05-material-graph/status.md` and `refactor/README.md`. Temporary agent briefs, ledgers and review packets stay below the ignored `.superpowers/sdd/2026-09-12-w5d-gradient-addressing-implementation-plan/` directory.

---

### Task 1: Capture the W5d grammar and deliver affine Linear Rect

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5dPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingCaptureV2.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialCoordinatePlanV2.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientTileOperationGraphV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5aMaterialPlanAuthorityV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: immutable `MaterialNode`/public `Shader` nodes, existing `GraphLimits(maxDepth=64, maxNodes=4096)`, W5c gradient normalization and W5a/W5b/W5c candidate ownership.
- Produces: one ordered immutable wrapper capture, exact W5d diagnostic constants, the first V2 coordinate/program/value seal, and a public Linear + affine local-matrix + Rect + CLAMP vertical slice with recovery.

- [ ] **Step 1: Write public RED tests for the grammar and first terminal slice.** Add `linearClampWithAffineLocalMatrixIsPlanOwned`, `unsupportedWrapperRemainsPreAdmission`, and `singularLocalMatrixRefusesAndRecoversOnTheSameRuntime`. The positive fixture uses seventeen stops so the historical small-gradient mapper cannot satisfy it, a non-identity affine local matrix, nested opacity and `TileMode.CLAMP`. The negative fixture surrounds a valid leaf with `Shader.WithColorFilter` and requires its existing pre-admission diagnostic. The recovery fixture renders a finite singular local matrix, asserts `unsupported.material.gradient.local-matrix-singular`, then renders a valid W5c CLAMP gradient on the same runtime.

```kotlin
val shader = Shader.Opacity(
    alphaF32 = 0.5f,
    shader = Shader.WithLocalMatrix(
        matrix = Matrix3x3F32.translation(3f, 0f),
        shader = linearGradient(stopsCountI32 = 17, tileMode = TileMode.CLAMP),
    ),
)
assertPixelInEnvelope(actual, W5dGradientAddressingCpuOracle.evaluate(fixture))
```

- [ ] **Step 2: Run the positive/recovery methods and verify RED at the current W5c boundary.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearClampWithAffineLocalMatrixIsPlanOwned' --tests '*W5dGradientAddressingSurfacePixelTest.singularLocalMatrixRefusesAndRecoversOnTheSameRuntime' --no-parallel --rerun-tasks
```

Expected: the positive draw refuses with `unsupported.material.mapping.local_matrix`; the singular-matrix method cannot yet expose `unsupported.material.gradient.local-matrix-singular`. A failure caused only by a malformed public fixture or oracle is corrected before production work.

- [ ] **Step 3: Define the exhaustive diagnostic and capture contracts.** Keep these exact strings in `W5dPlanDiagnostics`:

```kotlin
public object W5dPlanDiagnostics {
    public const val LocalMatrixNonFinite: String = "unsupported.material.gradient.local-matrix-non-finite"
    public const val LocalMatrixSingular: String = "unsupported.material.gradient.local-matrix-singular"
    public const val LocalMatrixUnrepresentable: String = "unsupported.material.gradient.local-matrix-unrepresentable"
    public const val CoordClampNonFinite: String = "unsupported.material.gradient.coord-clamp-non-finite"
    public const val CoordClampUnsorted: String = "unsupported.material.gradient.coord-clamp-unsorted"
    public const val CoordinateUniformBudget: String = "resource.material.gradient.coordinate-uniform-budget"
    public const val CoordinatePlanSchema: String = "schema.material.gradient.coordinate-plan"
}

internal sealed interface GradientAddressingCaptureV2 {
    data class Ready(
        val leaf: MaterialNode,
        val opacityF32: Float,
        val coordinateNodes: List<CoordinateNodeV2>,
    ) : GradientAddressingCaptureV2
    data class PreAdmission(val diagnosticCode: String) : GradientAddressingCaptureV2
    data class Refused(val diagnosticCode: String) : GradientAddressingCaptureV2
}
```

`CoordinateNodeV2` is an internal capture-only tagged value containing copied `Matrix3x3F32` or `RectF32` values from the math modules. It is not a second public geometry or matrix type.

- [ ] **Step 4: Implement bounded traversals over the two existing representations.** `GradientAddressingCaptureV2` traverses immutable `MaterialNode` outer-to-inner, multiplies Opacity values in the existing order, appends coordinate wrappers without reordering and stops only at one of the four W5c leaves. In `:kanvas`, create one internal `Shader.isW5dGradientCandidateV2()` helper used by both the candidate gate and prepared-frame registry; do not retain two separate Shader `when` lists. Both traversals consume the graph limits already established by capture, add no second public limit and use the same exhaustive wrapper/leaf enums. In this first slice they claim only Linear + CLAMP + Rect with Opacity/local-matrix wrappers; later tasks expand that readiness predicate without changing the captured grammar.

- [ ] **Step 5: Build and lower the complete V2 authority contract.** Create `MaterialCoordinatePlanV2` with copied `InverseMatrixF32` operations, `uniformByteSizeI64`, `canonicalIdentity` and tag-only `topologyIdentity`. For this slice prepend inverse CTM and append the inverse of one affine local matrix, using F64 conversion/inversion from `:math:matrix`, then project finite coefficients to F32. Add these variants before changing the lowerer:

```kotlin
public enum class GradientFamilyV2 { LINEAR, RADIAL, SWEEP, CONICAL }
public enum class GradientTileModeV2 { CLAMP, REPEAT, MIRROR, DECAL }

public sealed interface GradientTileOperationNodeV2
public sealed interface GradientTileOperationGraphV2 {
    public val requestedMode: GradientTileModeV2
    public val effectiveMode: GradientTileModeV2
    public val outputTF32: GradientTileOperationNodeV2
    public val validity: GradientTileOperationNodeV2
    public val contractId: String get() = "gradient-tile-v2"
    public companion object {
        public fun clamp(): GradientTileOperationGraphV2
    }
}

public data class GradientAddressingProgramV2(
    public val family: GradientFamilyV2,
    public val requestedTileMode: GradientTileModeV2,
    public val effectiveTileMode: GradientTileModeV2,
    public val tileGraphId: String,
    public val coordinateTopologyId: String,
) : MaterialProgramPlan {
    override val versionI32: Int = 2
    override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId(
        "w5d-gradient-v2:${family.name}:${requestedTileMode.name}:${effectiveTileMode.name}:$tileGraphId:$coordinateTopologyId",
    )
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
}

public sealed interface GradientV2 : MaterialBindingPlan {
    override val versionI32: Int get() = 2
    public val stopRange: GradientStopRangeV1
    public val numericAuthority: GradientNumericAuthorityV2
    public val gradientDegenerate: Boolean
    public fun copyUniformValuesF32(): List<Float>
    public fun rebind(
        range: GradientStopRangeV1,
        authority: GradientNumericAuthorityV2 = numericAuthority,
    ): GradientV2
}

public data class MaterialV2(
    public val ref: MaterialPlanRef,
    public val coordinates: MaterialCoordinatePlanV2,
) : PlanDrawMaterialAuthority
```

Task 1 creates `GradientTileOperationGraphV2` with its typed input/output/validity contract and the CLAMP graph only; Task 4 adds REPEAT/MIRROR/DECAL without changing this API. Task 1 also adds `LinearGradientV2`; Task 5 adds the other three family bindings with the same interface. `GradientNumericAuthorityV2.sealLinear(program, coordinates, startF32, endF32, degeneracy, slab, localMagnitudeF64, uniformMagnitudeF64): GradientNumericAuthorityV2?` seals the V1 family raw-parameter graph, V2 CLAMP tile graph, V2 coordinate plan, Linear uniform tuple, degeneracy, stop range/slab and domain proof. Its public verification shape is `authenticates(program: GradientAddressingProgramV2, binding: GradientV2, slab: GradientStopSlabPlanV1, coordinates: MaterialCoordinatePlanV2): Boolean`; its internal `rebase(binding: GradientV2, sourceSlab: GradientStopSlabPlanV1, newRange: GradientStopRangeV1, newSlab: GradientStopSlabPlanV1): GradientNumericAuthorityV2` first authenticates, proves identical selected stop sequences and returns a new V2 authority. `MaterialPlanTable.of`, stop-slab rewrite, interning and `copyForInterning` handle `GradientV1` and `GradientV2` exhaustively without converting between them.

`W5aMaterialPlanLowerer` keeps its existing `MaterialV1` branch byte-for-byte and adds an explicit `MaterialV2` branch. `W5aCorePrimitiveMaterialAuthorityV2.issue` receives a separate `coordinatesV2ByCommandIdI32` map; `W5aMaterialSourceStage` has distinct V1/V2 overloads and never attempts a V2 cast through `GradientNumericAuthorityV1`. The raw source key includes `tileGraphId` and `coordinateTopologyId`; the 48-byte-per-matrix values remain in the uniform. This preserves `MaterialCoordinatePlanV1`, all four `GradientV1` bindings and W5c CLAMP behavior unchanged.

- [ ] **Step 6: Preserve the pre-admission boundary and terminal ownership.** Unknown source nodes, image sources, color filters, source blends, non-sRGB interpolation, `CoordClamp`, non-CLAMP modes and non-Rect lanes remain outside this first executable slice. Once the positive Linear draw is claimed and sealed, it cannot consult the legacy mapper. A finite singular matrix becomes a typed planner refusal; it is not downgraded to pre-admission or identity.

- [ ] **Step 7: Verify GREEN, W5c continuity and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearClampWithAffineLocalMatrixIsPlanOwned' --tests '*W5dGradientAddressingSurfacePixelTest.unsupportedWrapperRemainsPreAdmission' --tests '*W5dGradientAddressingSurfacePixelTest.singularLocalMatrixRefusesAndRecoversOnTheSameRuntime' --tests '*W5cGradientSurfacePixelTest.linearRectUsesLocalCoordinatesAndMoreThanSixteenStops' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): deliver W5d affine linear slice"
```

### Task 2: Seal ordered matrix segments and Picture value preservation

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialCoordinatePlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F64.kt`
- Modify: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F64Test.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: ordered `CoordinateNodeV2`, draw CTM, `Matrix3x3F32`/`Matrix3x3F64`, `RectF32`, W5c Linear bindings and the W4a Rect lane.
- Produces: checked F64 segment composition/inversion, non-commutative ordered matrix chains, complete matrix validation and preservation of distinct immutable matrix values through `Picture` on the existing affine Linear Rect slice.

- [ ] **Step 1: Write public RED tests for order and Picture value preservation.** Add `linearLocalMatricesPreserveNonCommutativeOrder`, `linearDistinctLocalMatrixValuesSurvivePictureCapture`, `nonFiniteAndSingularLocalMatricesRefusePrecisely`, and `unrepresentableInverseRefusesPrecisely`. Compare translate-then-rotate with rotate-then-translate using the same seventeen-stop Linear shader and assert different exact public pixels. Record two Pictures from two distinct immutable `Matrix3x3F32` values and prove each preserves its own expected pixels; do not claim mutation of this immutable data class. For each invalid case, assert only the public diagnostic prefix and then render a valid draw on the same eligible runtime.

```kotlin
val outerThenInner = Shader.WithLocalMatrix(
    shader = Shader.WithLocalMatrix(shader = linear17, matrix = rotationF32),
    matrix = translationF32,
)
val reversed = Shader.WithLocalMatrix(
    shader = Shader.WithLocalMatrix(shader = linear17, matrix = translationF32),
    matrix = rotationF32,
)
assertNotEquals(renderPixel(outerThenInner), renderPixel(reversed))
```

- [ ] **Step 2: Run the four methods and verify RED on missing V2 coordinate consumption.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearLocalMatricesPreserveNonCommutativeOrder' --tests '*W5dGradientAddressingSurfacePixelTest.linearDistinctLocalMatrixValuesSurvivePictureCapture' --tests '*W5dGradientAddressingSurfacePixelTest.nonFiniteAndSingularLocalMatricesRefusePrecisely' --tests '*W5dGradientAddressingSurfacePixelTest.unrepresentableInverseRefusesPrecisely' --no-parallel --rerun-tasks
```

Expected: the non-commutative chain and at least one distinct Picture fixture refuse or render wrong pixels; non-finite/unrepresentable cases lack their exact W5d codes. The single affine slice and singular diagnostic delivered by Task 1 are GREEN controls, not RED evidence for Task 2.

- [ ] **Step 3: Add only the reusable matrix operations to `:math:matrix`.** Implement checked F64 composition of an ordered segment and projection to `Matrix3x3F32`. Do not introduce a renderer-specific matrix class.

```kotlin
public fun composeInOrderF64(matrices: List<Matrix3x3F32>): Matrix3x3F64
public fun Matrix3x3F64.invertFiniteOrNull(): Matrix3x3F64?
public fun Matrix3x3F64.toFiniteMatrix3x3F32OrNull(): Matrix3x3F32?
```

The common tests cover identity, `translation * rotation != rotation * translation`, singularity, non-finite input and a finite F64 inverse whose F32 projection overflows. Function parameters are collections or math values; every new numeric property/parameter remains suffixed.

- [ ] **Step 4: Extend the immutable V2 coordinate plan to ordered segments.** Retain the exact public operation names from the design and expose copied values only.

```kotlin
public sealed interface MaterialCoordinateOperationV2 {
    public data class InverseMatrixF32(
        public val inverseF32: Matrix3x3F32,
    ) : MaterialCoordinateOperationV2
    public data class ClampRectF32(
        public val subsetF32: RectF32,
    ) : MaterialCoordinateOperationV2
}

public class MaterialCoordinatePlanV2 private constructor(
    operations: List<MaterialCoordinateOperationV2>,
) {
    public fun copyOperations(): List<MaterialCoordinateOperationV2>
    public val uniformByteSizeI64: Long
    public val canonicalIdentity: String
    public val topologyIdentity: String
}
```

The builder prepends inverse CTM, accumulates adjacent local matrices outer-to-inner and emits one inverse per adjacent segment. `Matrix3x3F32` is immutable; the planner still snapshots its coefficients into the sealed F64/F32 authority before lowering. `PaintSceneAdapter` preserves an admitted W5d matrix long enough for the planner to classify non-finite/singular/unrepresentable cases with W5d codes while retaining existing checked behavior for out-of-grammar materials. Identity segments may be omitted only if doing so leaves the same topology version and value seal; the Task 3 clamp boundaries always flush a segment.

- [ ] **Step 5: Extend the exact V2 contracts from Task 1 to matrix chains.** `GradientAddressingProgramV2`, `LinearGradientV2`, `GradientNumericAuthorityV2` and `MaterialV2` keep the signatures fixed in Task 1. Recompute `coordinateTopologyId`, `coordinateSeal` and the V2 domain proof from the full segment list; `authenticates` and `rebase` remain the only paths to a lowered binding. No V2 value is routed through `GradientV1.numericAuthority` or `MaterialCoordinatePlanV1`.

- [ ] **Step 6: Lower an arbitrary affine segment sequence.** Pack each inverse as three padded `vec4<f32>` values, 48 bytes, in operation order. Extend the Task 1 local-point function to consume all segments in order and retain `{ pointF32, valid }`. For Task 2 only, valid affine matrices are exercised; projective validity is completed in Task 3. Include the complete topology identity in the raw program/source key and values only in the immutable uniform payload.

- [ ] **Step 7: Verify GREEN across JVM and JS math plus W5c regression, then commit.**

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' :math:matrix:jsNodeTest --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearLocalMatricesPreserveNonCommutativeOrder' --tests '*W5dGradientAddressingSurfacePixelTest.linearDistinctLocalMatrixValuesSurvivePictureCapture' --tests '*W5dGradientAddressingSurfacePixelTest.nonFiniteAndSingularLocalMatricesRefusePrecisely' --tests '*W5dGradientAddressingSurfacePixelTest.unrepresentableInverseRefusesPrecisely' --tests '*W5cGradientSurfacePixelTest.linearRectUsesLocalCoordinatesAndMoreThanSixteenStops' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add math/matrix gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): seal W5d coordinate plan"
```

### Task 3: Preserve `CoordClamp` order and seal projective validity

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialCoordinatePlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: V2 ordered operations, checked `RectF32`, projective `Matrix3x3F32`, W5c numeric-domain proof and the versioned material uniform.
- Produces: 16-byte clamp records, exact no-cross-clamp ordering, safe homogeneous division, propagated validity and public typed recovery evidence.

- [ ] **Step 1: Write public RED tests for clamp placement, projective invalidity and invalid subsets.** Add `localClampLocalPreservesExactOrder`, `adjacentDisjointClampsAreNotMerged`, `projectiveLocalMatrixRendersBoundedPixelsAndMasksWZero`, `coordClampRejectsNonFiniteAndUnsortedSubsets`, and `coordinateGraphDepthUsesExistingCaptureLimit`. Use two arrangements with the same values but different wrapper order. Include one pixel whose homogeneous `w == 0` and two finite neighboring pixels; only the singular pixel is transparent. Equal clamp edges are a separate valid fixture. Every refusal is followed by a valid public render.

```kotlin
val ordered = withLocalMatrix(translateF32,
    coordClamp(subsetF32,
        withLocalMatrix(scaleF32, linear17)))
val movedClamp = coordClamp(subsetF32,
    withLocalMatrix(translateF32,
        withLocalMatrix(scaleF32, linear17)))
assertNotEquals(renderPixel(ordered), renderPixel(movedClamp))
```

- [ ] **Step 2: Run the methods and verify RED on absent clamp/projective semantics.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.localClampLocalPreservesExactOrder' --tests '*W5dGradientAddressingSurfacePixelTest.adjacentDisjointClampsAreNotMerged' --tests '*W5dGradientAddressingSurfacePixelTest.projectiveLocalMatrixRendersBoundedPixelsAndMasksWZero' --tests '*W5dGradientAddressingSurfacePixelTest.coordClampRejectsNonFiniteAndUnsortedSubsets' --tests '*W5dGradientAddressingSurfacePixelTest.coordinateGraphDepthUsesExistingCaptureLimit' --no-parallel --rerun-tasks
```

Expected: valid fixtures refuse or disagree with the independent oracle; invalid subsets lack the W5d diagnostic contract.

- [ ] **Step 3: Seal clamp validation, layout and projective domains in the planner.** Validate `isFinite`, `left <= right`, `top <= bottom`; equal edges are valid. Serialize one `ClampRectF32` as `(left, top, right, bottom)` in 16 bytes. Use checked I64 addition for all operations, reject before allocation when the plan exceeds `RawMaterialRequirementsV2`, device `maxUniformBufferBindingSize`, `Int.MAX_VALUE` or frame-local budget. Start from the owner lane's bounded device-coordinate envelope, prove each allowed F32 multiply/add schedule for the first homogeneous segment, then propagate its quotient envelope into the next operation; a clamp replaces that propagated X/Y envelope with the clamped subset before the following segment. If a projective quotient is unbounded only because its `w` interval crosses zero, admit it only when the next coordinate operation is `ClampRectF32`, which closes the envelope before another matrix or family operation; otherwise refuse with `unsupported.material.gradient.numeric-domain-unbounded`. The public `w == 0` fixture therefore uses projective-matrix then clamp in evaluation order. Any other segment whose homogeneous rows or propagated output cannot close also refuses before `Ready`.

- [ ] **Step 4: Emit ordered coordinate WGSL with a validity carrier.** The generated function uses safe values after every matrix:

```wgsl
struct W5dBinaryPartsF32 {
    fractionF32: f32, // normal, signed, magnitude in [0.5, 1)
    exponentI32: i32,
    valid: bool,
}

fn w5dBinaryPartsF32(valueF32: f32) -> W5dBinaryPartsF32 {
    let bitsU32 = bitcast<u32>(valueF32);
    let absBitsU32 = bitsU32 & 0x7fffffffu;
    let exponentBitsU32 = (absBitsU32 >> 23u) & 0xffu;
    let mantissaBitsU32 = absBitsU32 & 0x007fffffu;
    if (exponentBitsU32 == 0xffu || absBitsU32 == 0u) {
        return W5dBinaryPartsF32(0.0, 0, exponentBitsU32 != 0xffu);
    }
    var fractionBitsU32 = (bitsU32 & 0x80000000u) | (126u << 23u) | mantissaBitsU32;
    var exponentI32 = i32(exponentBitsU32) - 126;
    if (exponentBitsU32 == 0u) {
        let leadingI32 = 31 - i32(countLeadingZeros(mantissaBitsU32));
        let normalizedU32 = mantissaBitsU32 << u32(23 - leadingI32);
        fractionBitsU32 = (bitsU32 & 0x80000000u) | (126u << 23u) |
            (normalizedU32 & 0x007fffffu);
        exponentI32 = leadingI32 - 148;
    }
    return W5dBinaryPartsF32(bitcast<f32>(fractionBitsU32), exponentI32, true);
}

fn w5dSafeDivideF32(numeratorF32: f32, denominatorF32: f32) -> W5dSafeDivideResultF32 {
    let numerator = w5dBinaryPartsF32(numeratorF32);
    let denominator = w5dBinaryPartsF32(denominatorF32);
    if (!numerator.valid || !denominator.valid || denominator.fractionF32 == 0.0) {
        return W5dSafeDivideResultF32(0.0, false);
    }
    if (numerator.fractionF32 == 0.0) {
        return W5dSafeDivideResultF32(0.0, true);
    }
    let fractionQuotientF32 = numerator.fractionF32 / denominator.fractionF32;
    let normalized = frexp(fractionQuotientF32);
    let resultExponentI32 = numerator.exponentI32 - denominator.exponentI32 + normalized.exp;
    if (resultExponentI32 > 128) {
        return W5dSafeDivideResultF32(0.0, false);
    }
    return W5dSafeDivideResultF32(ldexp(normalized.fract, resultExponentI32), true);
}

let homogeneous = inverseMatrix * vec3<f32>(state.pointF32, 1.0);
let projectedX = w5dSafeDivideF32(homogeneous.x, homogeneous.z);
let projectedY = w5dSafeDivideF32(homogeneous.y, homogeneous.z);
state.valid = state.valid && projectedX.valid && projectedY.valid;
state.pointF32 = select(vec2<f32>(0.0), vec2<f32>(projectedX.valueF32, projectedY.valueF32), state.valid);
```

Define `W5dSafeDivideResultF32(valueF32: f32, valid: bool)` next to the decomposition result. `bitcast`, shifts and `countLeadingZeros` normalize subnormal operands that survive WGSL's permitted input flush-to-zero without floating-point overflow. A flushed operand is classified as zero by its derived `fractionF32`; every zero decision uses that classified fraction, never a second comparison of the original float. The sole fraction division therefore has a normal denominator of magnitude at least `0.5` and a result below `2`; `frexp` receives a finite normal. `ldexp` is called only with exponent `<= 128`; lower exponents may flush a subnormal result to zero as WGSL permits, which the oracle envelopes. This path preserves the review counterexample with numerator near `2^127`, denominator `2^126` and quotient near `2`; it returns invalid only for a non-finite operand, a denominator classified as zero or a genuinely overflowing F32 quotient. Each clamp runs exactly at its list position. Invalid coordinates return transparent before family, tile, stop-search or blend evaluation.

- [ ] **Step 5: Extend the independent oracle with the same mathematical contract, not production helpers.** Enumerate the allowed F32 multiply/add schedules for each homogeneous row and propagate their output envelope operation-by-operation. Independently decode normal/subnormal F32 bits, evaluate the bounded fraction division plus `frexp`/`ldexp` exponent rule, carry `valid`, clamp only safe points and require singleton/two-code closure for chosen pixels. Include both WGSL-permitted classifications of each subnormal input (surviving subnormal or flushed zero), as well as flush-to-zero alternatives for subnormal final quotients; the oracle must make its zero decision from the selected classified parts exactly once. Add the finite large-numerator/large-denominator review counterexample as a public pixel control, and prove that only its neighboring exact `w == 0` sample is transparent.

- [ ] **Step 6: Verify GREEN, mutation safety and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.localClampLocalPreservesExactOrder' --tests '*W5dGradientAddressingSurfacePixelTest.adjacentDisjointClampsAreNotMerged' --tests '*W5dGradientAddressingSurfacePixelTest.projectiveLocalMatrixRendersBoundedPixelsAndMasksWZero' --tests '*W5dGradientAddressingSurfacePixelTest.coordClampRejectsNonFiniteAndUnsortedSubsets' --tests '*W5dGradientAddressingSurfacePixelTest.coordinateGraphDepthUsesExistingCaptureLimit' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): preserve W5d coordinate ordering"
```

### Task 4: Add the common tile graph for Linear on all W5c lanes

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientTileOperationGraphV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: Linear raw `t`, V2 coordinate validity, W5c upper-bound stop evaluation and the four W5c geometry lanes.
- Produces: one sealed tile operation graph shared by all families, exact endpoint rules, DECAL validity and Linear pixels on Rect/RRect/Path fill/Path stroke.

- [ ] **Step 1: Write a public table-driven RED for every Linear tile boundary and lane.** Add `linearTileModesCoverSignedBoundariesOnEveryLane`, `linearHardStopsPreserveTileBoundaries`, and `nonClampDropsOnlyTheOuterEndpointDuplicate`. For each lane and mode sample negative non-integer, negative integer, zero, interior hard stop, one, positive integer and greater-than-one non-integer values. Give endpoint duplicates deliberately asymmetric colors: at `p == 0`, non-CLAMP must keep the right/interior color; at `p == 1`, it must keep the left/interior color. Use no AA where supported so coverage does not obscure the tile boundary. Include nontrivial Opacity and one W5b destination-read blend case.

```kotlin
data class TileSample(
    val rawTF32: Float,
    val expectedTF32: Float,
    val expectedValid: Boolean,
)
val repeatSamples = listOf(
    TileSample(-2f, 0f, true),
    TileSample(-1.25f, 0.75f, true),
    TileSample(0f, 0f, true),
    TileSample(1f, 0f, true),
    TileSample(2.25f, 0.25f, true),
)
```

- [ ] **Step 2: Run the methods and verify RED for REPEAT/MIRROR/DECAL.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearTileModesCoverSignedBoundariesOnEveryLane' --tests '*W5dGradientAddressingSurfacePixelTest.linearHardStopsPreserveTileBoundaries' --tests '*W5dGradientAddressingSurfacePixelTest.nonClampDropsOnlyTheOuterEndpointDuplicate' --no-parallel --rerun-tasks
```

Expected: CLAMP controls may pass; the other three modes refuse or disagree at signed/boundary samples.

- [ ] **Step 3: Normalize non-CLAMP endpoint duplicates before defining the tile graph.** Add `normalizeGradientStopsV2(input, effectiveTileMode, preserveValidityMask)`. It delegates all W5c position normalization/hard-stop compaction first. When `effectiveTileMode != CLAMP`, it removes the first stop only when the first two normalized positions are both zero, and removes the last stop only when the last two positions are both one; implicit endpoints keep the remaining list at two or more entries. A Sweep whose requested mode is non-CLAMP but whose full-coverage rule produces effective CLAMP uses CLAMP normalization. The stop slab ABI and ordinary interior hard stops remain unchanged.

- [ ] **Step 4: Extend the typed tile graph from Task 1 instead of adding handwritten shader branches.** Keep `GradientTileOperationGraphV2` and `GradientTileOperationNodeV2` unchanged; add the three missing factories to its companion:

```kotlin
public companion object {
    public fun clamp(): GradientTileOperationGraphV2
    public fun repeat(): GradientTileOperationGraphV2
    public fun mirror(): GradientTileOperationGraphV2
    public fun decal(): GradientTileOperationGraphV2
}
```

Its finite operation vocabulary is `INPUT_T_F32`, `CONSTANT_F32`, `MUL_F32`, `SUB_F32`, `FLOOR_F32`, `ABS_F32`, `COMPARE_F32`, `SELECT_F32` and `AND_VALIDITY`. Seal exact graph shape per requested/effective mode. The graph contains no family geometry, stop values or uniform coefficients.

- [ ] **Step 5: Implement exact tile semantics in plan and WGSL.** CLAMP uses strict outside comparisons and preserves `t == 0/1`. REPEAT uses `t - floor(t)`. MIRROR uses `q = t - 2*floor(t*0.5)` then `1 - abs(q - 1)`. DECAL computes inclusive validity, clamps only the safe lookup parameter and masks strict outsiders. Combine tile validity with coordinate/family validity before source conversion.

- [ ] **Step 6: Route Linear V2 through all four existing lanes.** Reuse the same W4 lane facts and W5b blend/coverage closure; do not add a Linear-specific geometry lowerer. Update candidate/registry parity so a valid non-CLAMP Linear draw cannot partially fall back by lane.

- [ ] **Step 7: Verify GREEN, W5b blend continuity and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.linearTileModesCoverSignedBoundariesOnEveryLane' --tests '*W5dGradientAddressingSurfacePixelTest.linearHardStopsPreserveTileBoundaries' --tests '*W5dGradientAddressingSurfacePixelTest.nonClampDropsOnlyTheOuterEndpointDuplicate' --tests '*W5bBlendSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): tile W5d linear gradients"
```

### Task 5: Extend all tile/coordinate semantics to every gradient family and lane

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: common coordinate plan, common tile graph, W5c Radial/Sweep/Conical family parameter graphs and all four W5c lanes.
- Produces: the complete 4 families × 4 modes × 4 lanes W5d matrix with local matrix/CoordClamp coverage and no family/lane legacy gap.

- [ ] **Step 1: Write one public combinatorial RED plus focused family boundaries.** Add `allGradientFamiliesTileModesAndLanesMatchOracle`, `radialNonNegativePeriodsMatchOracle`, `sweepEndpointsAndFullCoverageMatchOracle`, and `conicalRootValidityPrecedesTile`. The combinatorial test samples at least one interior and one out-of-domain pixel for every family/mode/lane tuple; its fixtures alternate a non-identity local matrix and an ordered local-matrix/CoordClamp chain so every family and lane consumes the shared coordinate plan. Every fixture has seventeen stops or a wrapper so it cannot be satisfied by an unrelated legacy small-gradient path. Focused methods add the signed tile periods each family can authentically produce, endpoints, hard stops and Conical root/no-root points. Radial samples zero, interior, one, positive integers and greater-than-one values because `length(P-center)/radius` is non-negative for admitted radii.

```kotlin
for (family in GradientFixtureFamily.entries) {
    for (tileMode in TileMode.entries) {
        for (lane in W5dPublicLane.entries) {
            val actual = renderPublicFixture(family, tileMode, lane)
            assertPixelsInEnvelope(actual, W5dGradientAddressingCpuOracle.evaluate(family, tileMode, lane))
        }
    }
}
```

- [ ] **Step 2: Run the four methods and verify RED outside the completed Linear slice.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.allGradientFamiliesTileModesAndLanesMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.radialNonNegativePeriodsMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.sweepEndpointsAndFullCoverageMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.conicalRootValidityPrecedesTile' --no-parallel --rerun-tasks
```

Expected: Linear entries pass after Task 4; Radial/Sweep/Conical non-CLAMP or wrapped entries refuse or disagree.

- [ ] **Step 3: Add the three remaining V2 binding/factory variants.** `RadialGradientV2`, `SweepGradientV2` and `ConicalGradientV2` implement `GradientV2` with the same `stopRange`, `numericAuthority`, `gradientDegenerate`, `copyUniformValuesF32` and `rebind` contract as Linear. Add typed `GradientNumericAuthorityV2.sealRadial`, `sealSweep` and `sealConical` factories with the W5c family values/degeneracy tuples plus V2 program, coordinates, tile graph, slab and domain bounds. Extend `MaterialPlanTable.of`, rewrite/interning/copy and renderer exhaustiveness for all four V2 variants.

- [ ] **Step 4: Attach the common coordinate/tile authorities to Radial, Sweep and Conical.** Preserve each W5c family raw-parameter graph and sealed degeneracy tuple. Feed only raw `t` and family validity into `GradientTileOperationGraphV2`. Do not copy tile formulas into family branches and do not recompute a sealed W5c tuple in the renderer. Sweep full coverage uses effective CLAMP before stop normalization and key creation. Conical chooses its sealed valid root before tile; no-root remains transparent.

- [ ] **Step 5: Promote every family through all existing lanes.** Candidate gate, prepared registry, planner and lowerer accept the same tuple set. Reuse Rect/RRect/Path fill/Path stroke geometry and W5b final blend. Hairline stroke remains the existing Path stroke lane. Point(s), Text, Vertices/Mesh and W5h cells remain pre-admission. Coordinate invalidity dominates family and tile evaluation in every lane.

- [ ] **Step 6: Verify GREEN, W5c regression and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.allGradientFamiliesTileModesAndLanesMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.radialNonNegativePeriodsMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.sweepEndpointsAndFullCoverageMatchOracle' --tests '*W5dGradientAddressingSurfacePixelTest.conicalRootValidityPrecedesTile' --tests '*W5cGradientSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): complete W5d gradient families"
```

### Task 6: Seal exact degenerate averages and family precedence

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAverageSrgbaF32.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingPlanV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt`

**Interfaces:**
- Consumes: the four admitted V2 gradient bindings, normalized immutable stop slab/range, family degeneracy seals and requested/effective tile mode.
- Produces: exact binary-rational trapezoid average, a 16-byte straight-sRGB average field only for reachable REPEAT/MIRROR degeneracy and correct CLAMP/DECAL/Sweep/Conical precedence.

- [ ] **Step 1: Write public RED tests for all degenerate rules.** Add `degenerateLinearAndRadialUseExactAverageForRepeatMirror`, `degenerateSweepAndConicalUseExactAverageForRepeatMirror`, `degenerateDecalIsTransparent`, `degenerateClampKeepsW5cLastColorRules`, `singleStopCollapsePreservesFamilyRulesUnderAddressing`, `sweepFullCoverageForcesClamp`, `conicalInvalidRootStaysTransparentBeforeTile`, and `conicalFullyDegenerateClampKeepsCircularHardStop`. Use irregular positions, a zero-width hard stop and nontrivial alpha. Pick channel values whose exact integral differs by at least one RGBA8 code from sequential Float accumulation. The single-stop method proves Linear/Radial/Sweep collapse to Solid despite tile/wrappers while Conical retains two identical stops and its root mask.

```kotlin
val irregularStops = listOf(
    GradientStop(0f, ColorARGB.of(191, 17, 253, 5)),
    GradientStop(0.1f, ColorARGB.of(113, 241, 7, 199)),
    GradientStop(0.1f, ColorARGB.of(67, 3, 149, 251)),
    GradientStop(1f, ColorARGB.of(229, 101, 37, 11)),
)
```

- [ ] **Step 2: Run the eight methods and verify RED only on missing average/precedence.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.degenerate*' --tests '*W5dGradientAddressingSurfacePixelTest.singleStopCollapsePreservesFamilyRulesUnderAddressing' --tests '*W5dGradientAddressingSurfacePixelTest.sweepFullCoverageForcesClamp' --tests '*W5dGradientAddressingSurfacePixelTest.conicalInvalidRootStaysTransparentBeforeTile' --tests '*W5dGradientAddressingSurfacePixelTest.conicalFullyDegenerateClampKeepsCircularHardStop' --no-parallel --rerun-tasks
```

Expected: all four families are admitted after Task 5; REPEAT/MIRROR degenerate pixels disagree because no average is sealed yet, while the Task 5 non-degenerate controls stay GREEN.

- [ ] **Step 3: Implement a local exact binary-rational accumulator in JVM `:gpu-plan`.** Decode each finite F32 channel/position into sign, integer significand and power-of-two exponent. Compute every trapezoid term exactly with `java.math.BigInteger`; normalize only at the final conversion. The public result type is:

```kotlin
public data class GradientAverageSrgbaF32(
    public val redF32: Float,
    public val greenF32: Float,
    public val blueF32: Float,
    public val alphaF32: Float,
)
```

The final conversion implements IEEE-754 F32 roundTiesToEven explicitly. A zero-width interval contributes exactly zero. Do not expose the accumulator type publicly or move color integration into `:math`.

- [ ] **Step 4: Compute/store the average only when the uniform degeneracy branch can consume it.** Add `degenerateAverageSrgbaF32: GradientAverageSrgbaF32?` to `GradientV2` and all four binding variants; `rebind` preserves it. The `sealLinear`, `sealRadial`, `sealSweep` and `sealConical` factories receive that value, require it non-null exactly for a family-degenerate binding whose effective mode is REPEAT/MIRROR, and authenticate it with normalized stop range/slab identity, family degeneracy and tile graph. Serialize four F32 channels in 16 bytes only for structural programs that consume the field; the W5c stop buffer remains unchanged.

- [ ] **Step 5: Apply branch precedence before tile evaluation.** Family invalidity wins first. Degenerate CLAMP selects the W5c normative result, DECAL selects transparent, REPEAT/MIRROR select the sealed average. Sweep full coverage already has effective CLAMP from Task 5. Conical invalid root never reaches a tile node; fully-degenerate Conical CLAMP retains its circular hard stop.

- [ ] **Step 6: Verify GREEN, deterministic repeated rendering and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.degenerate*' --tests '*W5dGradientAddressingSurfacePixelTest.singleStopCollapsePreservesFamilyRulesUnderAddressing' --tests '*W5dGradientAddressingSurfacePixelTest.sweepFullCoverageForcesClamp' --tests '*W5dGradientAddressingSurfacePixelTest.conicalInvalidRootStaysTransparentBeforeTile' --tests '*W5dGradientAddressingSurfacePixelTest.conicalFullyDegenerateClampKeepsCircularHardStop' --tests '*W5cGradientSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingCpuOracle.kt
rtk git commit -m "feat(gpu): seal W5d degenerate averages"
```

### Task 7: Close mixed frames, budgets, capability and durable tracking

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveSessionCache.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt`
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

**Interfaces:**
- Consumes: complete W5d source plans, authentic adapter/device limits, W5c frame-local resources, W4 lanes, W5b blends and existing rollback/completion/quarantine owners.
- Produces: checked aggregate budgets, distinct structural layouts, public mixed-frame/ownership evidence, authentic capability/AA4 reporting and durable W5d status.

- [ ] **Step 1: Write public RED aggregate tests.** Add `mixedFramePreservesOrderAcrossFamiliesTilesWrappersLanesAndBlends`, `coordinateUniformBudgetRefusesPreciselyAndRecovers`, `mixedCoordinateTopologiesRemainSemanticallyDistinct`, `capturedSubsetsAndStopsIgnorePostRecordMutation`, and `authenticAa4OrPreciseSkip`. The mixed frame draws overlapping Rect/RRect/Path fill/Path stroke entries with all four families, multiple tile modes, Opacity, local matrices, clamps and destination-read W5b blends; draw order must be observable in final pixels. The topology method renders two different operation orders in one frame and distinguishes them only through public pixels; it does not inspect a program key. The mutation method records a `Picture`, mutates the caller-owned mutable subset and stop list, then renders the original snapshot. Distinct immutable matrix values are already covered by Task 2.

- [ ] **Step 2: Run the aggregate methods and verify RED on at least the budget/aggregate closure.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest.mixedFramePreservesOrderAcrossFamiliesTilesWrappersLanesAndBlends' --tests '*W5dGradientAddressingSurfacePixelTest.coordinateUniformBudgetRefusesPreciselyAndRecovers' --tests '*W5dGradientAddressingSurfacePixelTest.mixedCoordinateTopologiesRemainSemanticallyDistinct' --tests '*W5dGradientAddressingSurfacePixelTest.capturedSubsetsAndStopsIgnorePostRecordMutation' --tests '*W5dGradientAddressingSurfacePixelTest.authenticAa4OrPreciseSkip' --no-parallel --rerun-tasks
```

Expected: the mixed aggregate/topology fixtures disagree until every path is sealed. The budget method uses only public `RenderConfig.frameLocalBudgetBytes` with a valid multi-draw workload; it never fabricates device limits. If an existing, more specific public resource diagnostic rejects the aggregate earlier, reformulate the workload rather than accepting the wrong failure.

- [ ] **Step 3: Centralize checked requirements and authentic capability mapping.** `RawMaterialRequirementsV2` is the single source for family fields, stop header, optional 16-byte average and ordered coordinate bytes. Use checked I64 arithmetic, validate U32/Int conversions, `maxUniformBufferBindingSize`, bind count/alignment and `frameLocalBudgetBytes` before `Ready`. The capability adapter maps only physical facts reported by the real backend; no test adapter or synthetic limit is added.

- [ ] **Step 4: Seal structural/value separation.** Structural IDs distinguish family, requested/effective tile, tile graph version, average-layout presence and coordinate tag sequence. Value seals include stops/range, family tuple, opacity, matrix bits, subset bits and average when present. A topology/value/schema mismatch returns `schema.material.gradient.coordinate-plan` before native allocation.

- [ ] **Step 5: Audit ownership without adding infrastructure tests.** Trace the existing frame-local material uniform, stop buffer and bind-group owners through allocate/upload/submit/completion/rollback/close/quarantine. Reuse them unchanged; remove any W5d-only native owner or redundant stop resource introduced earlier. Confirm by code review and public repeated-render/recovery pixels, not counters or resource-lifetime assertions.

- [ ] **Step 6: Update durable documents.** Mark W5d scope and public gates complete only after the test evidence exists. Record the authentic AA4 availability/skip, capability facts, Gradle exit and native post-assertion status. Keep the same-runtime equal-extent target-ID collision and conservative Conical B-cross-zero oracle interval as explicit deferred gaps. Do not create intermediate status Markdown files.

- [ ] **Step 7: Verify GREEN against the bounded W5 regression set and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5dGradientAddressingSurfacePixelTest.kt refactor/waves/W05-material-graph/status.md refactor/README.md
rtk git commit -m "test(gpu): close W5d aggregate behavior"
```

### Task 8: Independent reviews, controller verification and stacked PR

**Files:**
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`
- Create only in ignored workspace: `.superpowers/sdd/2026-09-12-w5d-gradient-addressing-implementation-plan/progress.md`
- Create only in ignored workspace: `.superpowers/sdd/2026-09-12-w5d-gradient-addressing-implementation-plan/pr-body.md`

**Interfaces:**
- Consumes: all seven task commits, their public RED/GREEN evidence and the W5d design/plan.
- Produces: bounded independent review verdicts, fresh controller verification, an in-sync pushed branch and a stacked PR targeting `codex/w5c-gradients`.

- [ ] **Step 1: Run one Sol spec review over the complete branch.** Give the reviewer the W5d spec, this plan, base/head SHAs and global exclusions. Require findings with file/line/evidence and a final `READY` or `NOT READY`. The reviewer must not edit files or run excluded suites.

- [ ] **Step 2: Run one independent Sol code-quality/lifecycle review.** Focus on public behavior, coordinate order, F64 math ownership, value/topology seals, tile boundary formulas, invalid-value containment, stop-buffer ABI, native ownership and legacy terminality. Do not ask it to duplicate the first review.

- [ ] **Step 3: Correct substantive findings with Astra and re-review only the corrected scope with Sol.** Every correction starts from a public RED when behavior changes, then runs the narrow GREEN and the affected W5 regressions. Commit each coherent correction. Reviewer agents never implement their own findings.

- [ ] **Step 4: Run fresh controller compilation and public verification.** Do not reuse agent output as final evidence.

```bash
rtk ./gradlew :math:matrix:compileKotlinJvm :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' :math:matrix:jsNodeTest --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git status --short --branch
```

If the last Gradle process exits 133 only after JUnit XML records all requested assertions, record both the XML pass/fail/skip totals and exit 133; do not relabel the process exit as success. Any earlier crash, missing XML, assertion failure or unexpected skip keeps W5d open.

- [ ] **Step 5: Confirm exclusions and deferred gaps.** Review the executed command ledger and prove it contains no GM/dashboard/render-regeneration/Skia integration/font/codec/`jpg-color-cube` command and no `gpu-plan`/`gpu-renderer` infrastructure-test claim. Confirm the equal-extent target-ID and conservative Conical B-cross-zero gaps remain documented rather than silently closed.

- [ ] **Step 6: Push and open/update the stacked PR.** Verify the exact base/head before mutation, push `codex/w5d-gradient-addressing`, then create a PR with base `codex/w5c-gradients`. The PR body contains scope, architecture, task commits, exact tests/counts/skips/process exits, exclusions, deferred gaps, review verdicts and the W5c dependency.

```bash
rtk git fetch origin codex/w5c-gradients
rtk git rev-parse HEAD
rtk git rev-parse origin/codex/w5c-gradients
rtk git log --oneline origin/codex/w5c-gradients..HEAD
rtk git push -u origin codex/w5d-gradient-addressing
rtk gh pr create --base codex/w5c-gradients --head codex/w5d-gradient-addressing --title "feat(gpu): add W5d gradient addressing" --body-file .superpowers/sdd/2026-09-12-w5d-gradient-addressing-implementation-plan/pr-body.md
```

- [ ] **Step 7: Verify remote parity and record the final PR URL.**

```bash
rtk git fetch origin codex/w5d-gradient-addressing
rtk git diff --quiet HEAD origin/codex/w5d-gradient-addressing
rtk gh pr view --json url,baseRefName,headRefName,state
```

Expected: clean worktree; local and remote heads equal; open PR base `codex/w5c-gradients`, head `codex/w5d-gradient-addressing`.
