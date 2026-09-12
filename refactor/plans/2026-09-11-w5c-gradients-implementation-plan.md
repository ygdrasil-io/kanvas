# W5c Gradients and Shared Stop Buffer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote Linear, Radial, Sweep and Conical gradients through the sealed W5 material authority on Rect, RRect, Path fill and Path stroke, with exact public pixels and one frame-local storage buffer supporting more than sixteen stops.

**Architecture:** `:kanvas` rejects unbounded stop snapshots before copying, `:gpu-plan` normalizes gradients and seals their dynamic values into one immutable frame-local stop slab, and `:gpu-renderer` evaluates four program variants from an authenticated local-coordinate plan. The W4 geometry/coverage authority and W5b final blend remain unchanged; deferred tile modes, interpolation domains and geometry families stay pre-admission gaps until their scheduled slices.

**Tech Stack:** Kotlin/JVM, `:math:matrix`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit 5.

**Spec:** `refactor/specs/2026-09-11-w5c-gradients-design.md`, governed by `refactor/specs/2026-09-09-w5-material-graph-design.md` sections 5.4–5.6, 7, 15–18.

## Global Constraints

- Branch `codex/w5c-gradients` is stacked on `codex/w5b-blends`; W5c gets its own PR targeting the W5b branch.
- W5c admits exactly Linear/Radial/Sweep/Conical, `TileMode.CLAMP`, sRGB interpolation, and identity material local matrix on Rect, RRect, Path fill and Path stroke.
- `REPEAT`, `MIRROR`, `DECAL`, `WithLocalMatrix` and `CoordClamp` stay pre-admission gaps until W5d. LINEAR/OKLAB/HSL/OKLCH interpolation stays pre-admission until W5f. Point(s), Text and Vertices/Mesh gradients stay pre-admission until W5h.
- W5c changes no W4 geometry, tessellation, stroke, clip, coverage, stencil or AA topology and does not reconstruct any geometry outside `:math:geometry`.
- Matrix composition/inversion remains in `:math:matrix`. No duplicate matrix or geometry type is created in `:kanvas`, `:gpu-plan` or `:gpu-renderer`.
- Every new Kotlin numeric property or parameter uses an exact I32/I64/U32/F32/F64 suffix. Existing unsuffixed API and fixed WGSL ABI names are not renamed opportunistically.
- `SceneCaptureLimits.maxGradientStopsI32` defaults to 65 536 cumulative input stops. This is a revisable Kanvas memory bound, not a Skia semantic constant.
- Zero stops refuse; one stop becomes Solid for Linear/Radial/Sweep and is duplicated for Conical. Stop normalization, hard-stop equality, degeneracy branches and root selection follow the spec exactly.
- All gradients use one `GradientStopBufferV1`: stop alignment 16, stride 32 bytes; header alignment/size 16; explicit little-endian; zero reserved fields; checked I64 arithmetic and checked U32 representability.
- There is no inline small-gradient path, stop-count-specialized program, LUT approximation, per-draw stop buffer or inter-frame stop cache.
- The source is straight sRGB at the stop boundary and linear-premul after interpolation/conversion. Opacity and W5b apply exactly once; final coverage/blend remains `destination + coverage * (blend(source, destination) - destination)`.
- A W5c-admitted draw is terminal after ownership. No invalid value, capability failure, budget failure, allocation/compiler/submit error or readback error may fall back to the legacy gradient provider.
- Public behavior gates use only `Surface`, `Canvas`, `Picture`, `render()`, public typed diagnostics/exceptions and pixels. They never assert internal source shape, reflection, private/internal state, call counts, scopes, counters, packet contents, bind groups, uniform bytes or resource counts.
- The oracle is independent of production gradient code and closes through `WgslFloatEnvelopeV1`. A fixture is a gate only when it yields one exact RGBA8 code or two adjacent codes analytically; no similarity threshold is allowed.
- Capability failure is tested only when an authentic adapter reports an insufficient physical fact. Otherwise it is an authentic conditional skip plus static human review, never a fabricated capability.
- AA4 remains an authentic skip/refusal when unavailable. Fonts, codecs, GM, dashboard, renders/baselines, `:integration-tests:skia` and `jpg-color-cube` are excluded.
- Implementers use Astra. Task reviews and final code reviews use Sol only, except the explicitly requested Astra review of this written plan before Task 1.
- Durable tracking stays in this plan, `refactor/waves/W05-material-graph/status.md` and `refactor/README.md`. Agent briefs, ledgers, reports and review packages remain under the ignored plan-specific `.superpowers/sdd` workspace.

---

### Task 1: Bound gradient snapshots before copying

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayListBuffer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/PictureRecorder.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/SceneRecordingLimitException.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`

**Interfaces:**
- Consumes: public shader stop lists, `DisplayListBuffer.append(DisplayOp)`, `SceneCaptureLimits`, `SceneCaptureResult` and the existing defensive snapshot contract.
- Produces: `SceneCaptureLimits.maxGradientStopsI32`, public `SceneRecordingLimitException`, a single bounded internal snapshot owner, and the same capture limits flowing from `Surface` into recording and GPU routing.

- [ ] **Step 1: Write public RED tests for pre-copy refusal and recovery.** Add methods named `recordingStopLimitRefusesTransactionallyBeforeCopy`, `recordingStopLimitCountsSiblingGradientChildren`, `recordingStopLimitCountsPriorAppends`, and `captureStopLimitReturnsTypedDiagnostic` to `W5cGradientSurfacePixelTest`. The first constructs `Surface(..., captureLimits = SceneCaptureLimits(maxGradientStopsI32 = 16))`, attempts a seventeen-stop Linear draw, catches only `SceneRecordingLimitException`, verifies its public diagnostic code and then renders a valid Solid draw on the same surface. The sibling test places two nine-stop gradients under one public `Shader.Blend` and proves the pending reservation is included. The prior-append test records nine stops successfully, then proves an eight-stop second append is refused by the cumulative committed count. The capture test records under the default limit and calls `snapshotScene(SceneCaptureLimits(maxGradientStopsI32 = 16))`, requiring `SceneCaptureResult.Invalid` with `scene-capture-gradient-stops-exceeded`.

```kotlin
val failure = assertThrows<SceneRecordingLimitException> {
    surface.canvas { drawRect(rect, Paint(shader = linearStops(17))) }
}
assertEquals("scene-recording-gradient-stops-exceeded", failure.diagnostic.code.value)
surface.canvas { drawRect(rect, Paint(shader = Shader.SolidColor(ColorARGB.Red), antiAlias = false)) }
assertContentEquals(ubyteArrayOf(0xFFu, 0x00u, 0x00u, 0xFFu), surface.render().pixels)
```

- [ ] **Step 2: Run the two tests and verify RED for the missing public limit contract.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.recordingStopLimit*' --tests '*W5cGradientSurfacePixelTest.captureStopLimitReturnsTypedDiagnostic' --no-parallel --rerun-tasks
```

Expected: compilation or assertion failure because `maxGradientStopsI32`, `captureLimits` and `SceneRecordingLimitException` do not exist; a legacy renderer failure is not an acceptable RED for this task.

- [ ] **Step 3: Add the exact public limits and exception contract.** Append the field to `SceneCaptureLimits` and create the exception without backend handles.

```kotlin
public data class SceneCaptureLimits(
    public val graphLimits: GraphLimits = GraphLimits(),
    public val maxDepth: Int = 64,
    public val maxNodes: Int = 4_096,
    public val maxResources: Int = 1_024,
    public val maxGradientStopsI32: Int = 65_536,
)

public class SceneRecordingLimitException(
    public val diagnostic: RenderDiagnostic,
    public val limitI32: Int,
    public val requestedI64: Long,
) : IllegalArgumentException(diagnostic.message)
```

Validate `maxGradientStopsI32 > 0`. Use the exact code `scene-recording-gradient-stops-exceeded`; never expose the offending list or shader instance.

- [ ] **Step 4: Move the metadata preflight ahead of every controlled list copy.** Add a buffer-owned cumulative reservation object. `GeometrySnapshotContext` must call a helper equivalent to the following before `toList()` for all four gradient families and roll back the current append reservation if the full operation snapshot fails.

```kotlin
internal fun reserveGradientStops(stopsCountI32: Int) {
    val requestedI64 = Math.addExact(
        Math.addExact(committedStopsI64, pendingStopsI64),
        stopsCountI32.toLong(),
    )
    if (requestedI64 > maxGradientStopsI32.toLong()) throw limitFailure(requestedI64)
    pendingStopsI64 = Math.addExact(pendingStopsI64, stopsCountI32.toLong())
}
```

Count each copied occurrence once; identity sharing within one shader graph must not double-count the same copied list. Commit the reservation only after `delegate.append(snapshot)` succeeds.

- [ ] **Step 5: Remove the double internal snapshot without weakening public snapshots.** Mark `SnapshotDisplayListBuffer` as the internal snapshot owner so `Canvas` does not wrap it in `GeometrySnapshotDisplayListBuffer`. Add an internal immutable `sealedOps()` view for `Surface`/GPU capture; keep `ops()` and `Surface.snapshotOps()` defensive for public callers. `PaintSceneAdapter` must preflight cumulative immutable stop metadata before mapping it into Render IR.

- [ ] **Step 6: Thread one limit snapshot through Surface, Picture and rendering.** Add trailing defaulted constructor parameters `captureLimits: SceneCaptureLimits = SceneCaptureLimits.DEFAULT` to `Surface` and `PictureRecorder`; feed the same object to the recording buffer and `GPUPlanSurfaceRouter`. `snapshotScene(limits)` may apply a stricter call-specific limit to sealed operations without making another stop copy first.

- [ ] **Step 7: Verify GREEN, existing public snapshot behavior and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.recordingStopLimit*' --tests '*W5cGradientSurfacePixelTest.captureStopLimitReturnsTypedDiagnostic' --tests '*SurfaceSceneSnapshotTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt
rtk git commit -m "feat(kanvas): bound W5c gradient capture"
```

### Task 2: Deliver a Linear-gradient Rect vertical slice

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialCoordinatePlanV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5cPlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/NumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCapabilities.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W3SolidRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/capabilities/CapabilityContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanCapabilityAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveSessionCache.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5cGradientStopNativeV1.kt`
- Modify: `math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F64.kt`
- Modify: `math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F64Test.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/WgslFloatEnvelopeV1Oracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5aMaterialSurfacePixelTest.kt`

**Interfaces:**
- Consumes: immutable `MaterialNode.LinearGradient`, W5a table/interner, W4a Rect facts, W5b blend plan and authentic GPU limits.
- Produces: normalized stop/slab plans, `LinearGradientClampSrgbV1`, a sealed `GradientNumericOperationGraphV1`, `MaterialCoordinatePlanV1`, `GradientStopData` resource, `NumericOperationGraphV1.INPUT_GRADIENT_SRGBA_STRAIGHT`, storage capability mapping, native group-1 uniform+storage ABI and exact Linear Rect pixels.

- [ ] **Step 1: Write a mutation-sensitive public RED Linear Rect test.** Add `linearRectUsesLocalCoordinatesAndMoreThanSixteenStops`. Use seventeen stops with a duplicated position at `0.5f`, a non-identity scale/translate CTM, nontrivial Paint alpha and a destination-read W5b blend. After recording, mutate the original stop list, reassign the immutable CTM variable and issue later Canvas transforms; none may alter the recorded draw. Observe pixels mapped just before, exactly at and just after the hard stop, plus an independent local-coordinate pixel, so the test distinguishes device coordinates, `(0,0)`, wrong equality, stale stop ranges and double opacity.

```kotlin
val capturedStops = MutableList(17) { indexI32 ->
    GradientStop(
        position = when (indexI32) {
            8, 9 -> 0.5f
            else -> indexI32 / 16f
        },
        color = if (indexI32 <= 8) ColorARGB.Red else ColorARGB.Blue,
    )
}
listOf(7.999f, 8f, 8.001f).forEachIndexed { sampleIndexI32, localXF32 ->
    val expected = W5cGradientCpuOracle.linearClampSrgb(
        localPointF32 = Point2F32(localXF32, 4.5f),
        startF32 = Point2F32(0f, 0f),
        endF32 = Point2F32(16f, 0f),
        stops = capturedStops,
    )
    assertPixelInEnvelope(result, sampleIndexI32, 0, expected.thenBlend(destination, blend, 1f))
}
```

- [ ] **Step 2: Run the method and verify RED on the current sixteen-stop/legacy boundary.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.linearRectUsesLocalCoordinatesAndMoreThanSixteenStops' --no-parallel --rerun-tasks
```

Expected: typed legacy refusal or wrong public pixel. A RED caused only by an invalid oracle fixture must be corrected before production work.

- [ ] **Step 3: Implement exact stop normalization and the immutable slab types.** Define these contracts in `GradientPlanV1.kt`; all numeric fields use suffixes.

```kotlin
public data class GradientStopPlanV1(
    public val positionF32: Float,
    public val straightSrgbF32: ColorF32,
)
public data class GradientStopRangeV1(public val baseIndexU32: UInt, public val countU32: UInt)
public class GradientStopSlabPlanV1 private constructor(stops: List<GradientStopPlanV1>) {
    public fun copyStops(): List<GradientStopPlanV1>
    public val byteSizeI64: Long
}
```

The normalizer returns either a typed diagnostic, Solid collapse, or canonical gradient stops. Clamp/monotonize positions, add implicit endpoints, retain first/last of runs longer than two, preserve two-stop hard discontinuities and validate every arithmetic/count before allocation.

- [ ] **Step 4: Define and seal the complete typed gradient numeric sub-graph.** Create `GradientNumericOperationGraphV1` with typed inputs `LocalPointF32`, `UniformScalarF32`, `UniformFlag`, `StopRangeU32`, typed intermediates `ScalarF32`, `IndexU32`, `ValidityFlag`, and output `SrgbaStraightF32`. Its exhaustive operation vocabulary is `INPUT_LOCAL_POINT_F32`, `INPUT_UNIFORM_F32`, `INPUT_UNIFORM_FLAG`, `INPUT_STOP_RANGE_U32`, `LOAD_STOP_POSITION_F32`, `LOAD_STOP_COLOR_SRGBA_F32`, `ADD_F32`, `SUB_F32`, `MUL_F32`, `DIV_F32`, `SQRT_F32`, `ATAN2_F32`, `FLOOR_F32`, `ABS_F32`, `MAX_F32`, `COMPARE_F32`, `SELECT`, `UPPER_BOUND_STOPS_V1`, and `INTERPOLATE_SRGBA_STRAIGHT_F32`. `UPPER_BOUND_STOPS_V1` owns a bounded loop body over `countU32`; its comparison/load/body graph is explicit and the capture/plan bounds prove termination.

```kotlin
public sealed interface GradientNumericOperationGraphV1 {
    public val root: Node
    public val contractId: String get() = "WgslFloatEnvelopeV1"
    public val domainProof: GradientNumericDomainProofV1
}
public sealed interface GradientNumericDomainProofV1 {
    public data object ProvenFinite : GradientNumericDomainProofV1
    public data class Unbounded(public val diagnosticCode: String) : GradientNumericDomainProofV1
}
```

The Linear sub-graph starts from the authenticated local point and sealed axis parameters, constructs dot/length/division in the normative F32 order, selects the sealed degenerate branch, applies CLAMP, performs `upper_bound - 1`, loads the two stops and interpolates straight sRGB. Every allowed reassociation/fused-or-unfused schedule and operation-domain bound is part of its graph metadata; a missing finite-domain proof returns `unsupported.material.gradient.numeric-domain-unbounded` before `Ready`.

- [ ] **Step 5: Extend material programs, bindings, the outer numeric graph and interning.** Add `MaterialProgramPlan.LinearGradientClampSrgbV1`, implementing `copyGradientNumericOperationGraphV1()`, and a `MaterialBindingPlan.LinearGradientV1` carrying `startF32`, `endF32`, `GradientStopRangeV1` and `LinearGradientDegeneracyV1`. Add `NumericOperationGraphV1.Operation.INPUT_GRADIENT_SRGBA_STRAIGHT` with output `SrgbaStraightF32`; the outer Linear graph consumes the proven sub-graph output, then applies the existing `SRGB_TO_LINEAR` and `PREMULTIPLY` chain before Opacity/coverage/blend. `W5aMaterialSourceStage` must require both graphs when that input occurs. Extend `MaterialPlanTable` so `of` seals a slab and `intern` merges immutable slabs in first-occurrence order while rewriting every range. Program structural IDs include family/ABI/numeric version and exclude values, positions and concrete stop count.

- [ ] **Step 6: Write RED JVM/JS semantic tests for F64 matrix inversion.** Add exact identity, affine, perspective, singular and non-finite cases to `Matrix3x3F64Test`. These are public math-value tests, not renderer infrastructure assertions.

```bash
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jsNodeTest --no-parallel --rerun-tasks
```

Expected: compilation failure because `Matrix3x3F64.invertToMatrix3x3F32OrNull()` does not exist.

- [ ] **Step 7: Implement F64 inversion in `:math:matrix` and seal the Rect coordinate plan before W3/W4 lose the CTM.** Add `invertToMatrix3x3F32OrNull()` beside `Matrix3x3F64`; compute the cofactor inverse in Double, reject zero/non-finite determinant and any non-finite F32 projection, and canonicalize signed zero. `MaterialCoordinatePlanV1.fromCtm(ctmF32)` uses only that public math function, stores an immutable inverse projected to F32 and exposes a fixed 48-byte uniform representation. Add it to material-bearing `SolidRectDraw` and `AnalyticRectDraw`, their `withMaterialRef` paths, canonical identity and frame-local uniform budget.

```kotlin
public class MaterialCoordinatePlanV1 private constructor(
    inverseCtmF32: Matrix3x3F32,
) {
    public fun copyInverseCtmF32(): Matrix3x3F32
    public val uniformByteSizeI64: Long get() = 48L
}
```

- [ ] **Step 8: Add storage vocabulary and authentic capability mapping.** Add `PlanResourceRole.GradientStopData`; require `{StorageRead, CopyDestination}` plus `PlanOperationCapability.StorageBuffer` and `CopyUpload`. Add `GPURendererFeature.StorageBuffer`, derive it from the real WebGPU device/runtime facts in `GPUBackendRuntimeNative`, and map it in `GpuPlanCapabilityAdapter`. Missing storage limits stay null and refuse before `Ready`.

- [ ] **Step 9: Plan one frame-local stop resource.** The Rect plan adds one `GradientStopData` buffer covering every consumer pass. Check `maxStorageBufferBindingSizeBytesI64`, `maxStorageBuffersPerShaderStageI32`, `maxBindingsPerBindGroupI32`, `maxBindGroupsI32`, physical max buffer size, U32 ranges and combined frame-local budget before emitting the resource or ready token.

- [ ] **Step 10: Extend the common material source-stage ABI.** Replace the one-binding assumption with a sealed manifest. Solid/Opacity retain group 1 binding 0 only; gradients add group 1 binding 1 read-only storage. Compose the authenticated Rect local point from `MaterialCoordinatePlanV1`, not from device coordinates or public paint. Serialize uniforms and stops little-endian; allocate one native `Storage | CopyDst` buffer per frame and share it across gradient bind groups.

- [ ] **Step 11: Implement Linear WGSL and extend the independent numerical oracle from the sealed sub-graph.** WGSL and `W5cGradientCpuOracle` independently interpret the same allowed `GradientNumericOperationGraphV1` operations; the oracle does not receive an already-calculated gradient color and does not import production normalizers, shaders, plan bindings or formula helpers. It covers `upper_bound - 1`, right color at hard-stop equality, exact sRGB interpolation, transfer and premultiplication once. Extend the oracle result with distinct `DomainUnbounded` and `FixtureUnbounded` outcomes: `DomainUnbounded` means the production preflight cannot prove all inputs/reassociations finite and blocks cell closure; `FixtureUnbounded` means the program domain is proven but that chosen public pixel spans more than two adjacent codes and only that fixture must be replaced.

- [ ] **Step 12: Retarget the historical W5a gradient refusal to the normative empty-stop error.** In `W5aMaterialSurfacePixelTest`, change `public W5b gradient refusal leaves the runtime able to render a later W5a frame` to pass `emptyList()` to a CLAMP/SRGB Linear gradient, rename it to `public empty gradient refusal leaves the runtime able to render a later W5a frame`, and require `unsupported.material.gradient.empty_stops`. Keep the later Solid recovery pixels unchanged. Do not use a deferred mode as the refusal witness because the legacy route may render it.

- [ ] **Step 13: Verify the vertical slice and W5a/W5b regressions, then commit.**

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jsNodeTest --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.linearRectUsesLocalCoordinatesAndMoreThanSixteenStops' --tests '*W5aMaterialSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add math/matrix/src/commonMain/kotlin/org/graphiks/math/matrix/Matrix3x3F64.kt math/matrix/src/commonTest/kotlin/org/graphiks/math/matrix/Matrix3x3F64Test.kt gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): render W5c linear gradients on rects"
```

### Task 3: Promote Linear gradients across all four W5c geometry lanes

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4bAnalyticRRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4cPathFillPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dPathStrokePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dRenderGraphCanonicalSeal.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralRenderGraphCanonicalSeal.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aCompositeGraphLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bW4eNativeLaneV3.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveSessionCache.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`

**Interfaces:**
- Consumes: Linear plan/source/slab and Rect `MaterialCoordinatePlanV1` from Task 2.
- Produces: the same sealed coordinate/material/resource contract on RRect, direct/stencil Path fill and Path stroke/hairline, without modifying W4 geometry.

- [ ] **Step 1: Add RED public Linear tests for RRect, Path fill and Path stroke.** Add `linearGradientCoversRRectPathFillAndStroke`, `linearGradientPreservesHardStopsAndImplicitEndpoints`, and `linearGradientPictureSnapshotIsMutationSafe`. Include 1/2/16/17-stop cases across the matrix, triple duplicate positions, equality samples, nontrivial CTMs and at least one direct fill, stencil fill, finite stroke and hairline. Every middle draw retains an observable pixel not overwritten by a later opaque draw.

- [ ] **Step 2: Verify RED for missing lane ownership or wrong local coordinates.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.linearGradient*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Carry `MaterialCoordinatePlanV1` through every sealed draw.** Compute it while each W4 compiler still owns the command CTM. Add it to `AnalyticRRectDraw`, `PathFillDraw`, `PathStrokeDraw` and general-path successors, their `ofMaterial`/`withMaterialRef` methods, canonical seals and uniform budgets. Producer-only stencil/mask passes carry no material coordinate; only the final color consumer does.

- [ ] **Step 4: Expose an authenticated coordinate slot for both native template providers.** `GPUW5aGeometryPipelineTemplate` receives `MaterialCoordinateSlotV1`. Both `GPUWgpu4kCorePrimitiveSessionCache` and W4e's provider in `GPUWgpu4kCorePrimitiveFramePayloadMaterializer` must supply a slot that maps fragment device position through the sealed inverse CTM. Version group-0 ABI only where the matrix transport actually changes it.

- [ ] **Step 5: Extend candidate admission without stealing deferred families.** `W5aPreparedFrameMaterialRegistry` admits Linear+Opacity only for Rect/RRect/Path fill/stroke and only CLAMP/SRGB/no local matrix. Point(s), text, vertices, non-CLAMP and non-SRGB remain outside W5c ownership. Once a lane carries the gradient material ref, every downstream error is terminal.

- [ ] **Step 6: Verify the Linear matrix and W4/W5 regressions.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.linear*' --tests '*W5aMaterialSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --no-parallel --rerun-tasks
```

These W5a/W5b public pixel classes already exercise the promoted W3/W4 geometry lanes. Do not add `GPUPlanSurfacePixelTest` to the gate: that class contains `nativeEvidenceScopeKinds` infrastructure assertions. Do not substitute a broad GM/Skia suite.

- [ ] **Step 7: Commit the reviewed vertical expansion.**

```bash
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): promote W5c linear gradient lanes"
```

### Task 4: Add Radial gradients on the shared authority

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`

**Interfaces:**
- Consumes: shared normalization, stop range/slab, coordinate slot and four promoted lanes.
- Produces: `RadialGradientClampSrgbV1`, `RadialGradientV1` binding and `RadialGradientDegeneracyV1` with identical resource/ownership behavior.

- [ ] **Step 1: Add RED public Radial tests.** Add `radialGradientCoversFourGeometryLanes` and `radialGradientHandlesDegenerateAndSingletonStops`. Cover a finite radius, radius exactly/near epsilon, negative radius refusal, one-stop Solid collapse, 17 stops, an off-center CTM and a hard stop sampled before/at/after equality.

- [ ] **Step 2: Verify RED for unsupported Radial ownership.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.radialGradient*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Add the Radial plan, exact degeneracy seal and numeric sub-graph.** `RadialGradientV1` carries `centerF32`, `radiusF32`, stop range and `RadialGradientDegeneracyV1(radialRadiusF32, radialDegenerate)`. Compute the F32 comparison to exact `2^-15` in `:gpu-plan`; reject negative/non-finite radius before `Ready`. Its `GradientNumericOperationGraphV1` constructs subtract/length/divide or the sealed degenerate selection, then shares CLAMP/search/interpolation nodes.

- [ ] **Step 4: Add Radial WGSL without duplicating shared stop logic.** Compute `t = length(P-center)/radius`; when sealed degenerate under CLAMP, return the last normalized stop. Reuse the same binary search, interpolation, sRGB conversion, stop buffer and opacity/blend tail as Linear.

- [ ] **Step 5: Verify Radial plus Linear and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.radialGradient*' --tests '*W5cGradientSurfacePixelTest.linear*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): add W5c radial gradients"
```

### Task 5: Add Sweep gradients with exact screen-angle semantics

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`

**Interfaces:**
- Consumes: shared stop/coordinate/four-lane authority.
- Produces: `SweepGradientClampSrgbV1`, `SweepGradientV1` and a fully serialized `SweepGradientDegeneracyV1`.

- [ ] **Step 1: Add RED public Sweep tests.** Add `sweepGradientUsesClockwiseScreenAnglesOnFourLanes` and `sweepGradientHandlesSpanBoundariesAndDegeneracy`. Sample +X, +Y, -X and -Y around a translated center; cover partial span, full coverage, start greater than end refusal, epsilon span, CLAMP leading segment and exact hard-stop angle.

- [ ] **Step 2: Verify RED for unsupported Sweep ownership.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.sweepGradient*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Seal every Sweep branch decision and its numeric sub-graph.** Store `startAngleDegreesF32`, `endAngleDegreesF32`, `sweepSpanDegreesF32`, `sweepOrderingInvalid`, `sweepDegenerate`, `sweepClampLeadingSegment` and `sweepFullCoverage`; compute them with immediate F32 rounding in the normative order. Reject invalid ordering before `Ready`. The Sweep sub-graph explicitly connects subtract, `ATAN2_F32`, division by `2*pi`, `FLOOR_F32`, span division and sealed CLAMP/degenerate selects before shared stop lookup.

- [ ] **Step 4: Add Sweep WGSL and oracle closure.** Use clockwise screen coordinates, zero on +X, `floorMod(atan2(...)/(2*pi),1)`, then the sealed span. Do not recompute uniform branch booleans. Full coverage forces CLAMP semantics; degenerate leading segment chooses first strictly before the boundary and right/last color at equality and after it.

- [ ] **Step 5: Verify all three implemented families and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.sweepGradient*' --tests '*W5cGradientSurfacePixelTest.radialGradient*' --tests '*W5cGradientSurfacePixelTest.linear*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): add W5c sweep gradients"
```

### Task 6: Add Conical gradients and validity masks

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`

**Interfaces:**
- Consumes: shared stop/slab/coordinate/four-lane authority.
- Produces: `ConicalGradientClampSrgbV1`, `ConicalGradientV1`, exclusive conical branch tags and transparent invalid-fragment masking.

- [ ] **Step 1: Add RED public Conical tests.** Add `conicalGradientCoversFourLanesAndSelectsLargestValidRoot`, `conicalGradientMasksFragmentsWithoutValidRoot`, and `conicalGradientCoversAllDegeneracyBranches`. Cover quadratic two-root selection, linear equation, concentric circles, fully degenerate shared radius, negative radius refusal, one-stop duplication, 17 stops, and public pixels inside/outside the validity domain.

- [ ] **Step 2: Verify RED for unsupported Conical ownership.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.conicalGradient*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Implement the exact conical preflight and numeric sub-graphs.** Serialize every scalar/boolean named in spec section 7.2, including `conicalAF32`, `conicalScaleF32`, and an exclusive `conicalBranchTagU32` chosen in order FULLY_DEGENERATE, CONCENTRIC, LINEAR_EQUATION, QUADRATIC. The Kotlin field spelling must preserve the required F32/U32 suffix even if the fixed WGSL ABI label differs. The Conical sub-graph explicitly encodes B/C/discriminant arithmetic, `SQRT_F32`, linear/quadratic root candidates, finiteness/radius comparisons, largest-valid-root selection and validity mask before CLAMP/search/interpolation.

- [ ] **Step 4: Implement fragment root selection and masks.** Evaluate B, C, discriminant and roots in F32; retain only finite roots where `startRadiusF32 + tF32 * drF32 > 0`, choose the largest, and return transparent before tile when none remains. Implement the concentric and fully-degenerate CLAMP cases exactly; never substitute the Linear/Radial rule where the conical validity mask differs.

- [ ] **Step 5: Extend the independent oracle with the same allowed operation graph, not production helpers.** If root/discriminant uncertainty produces more than two non-adjacent output codes, redesign the fixture rather than widening tolerance.

- [ ] **Step 6: Verify all four families and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): add W5c conical gradients"
```

### Task 7: Close mixed-frame, resource and ownership gates

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5cGradientStopNativeV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5cGradientCpuOracle.kt`
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

**Interfaces:**
- Consumes: all four family programs, four geometry lanes, W5b final blend and frame-owned stop slab.
- Produces: one ordered mixed-frame material graph, deterministic capture/software-budget refusals, authentic conditional capability evidence, terminal W5c ownership and durable W5c status.

- [ ] **Step 1: Add RED mixed-frame and resource tests.** Add `mixedGradientFramePreservesOrderRangesOpacityAndBlend`, `gradientFrameBudgetRefusesThenRuntimeRecovers`, and `authenticStorageCapabilityEitherRendersOrRefusesTyped`. The mixed frame interleaves all four families and four geometry lanes, reuses one exact stop sequence, uses a distinct 17-stop sequence, wraps at least one gradient in Opacity and uses one destination-read blend. Observe a unique pixel for every middle draw plus an order counterfactual.

- [ ] **Step 2: Verify RED on aggregate slab/range/budget behavior.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest.mixedGradientFrame*' --tests '*W5cGradientSurfacePixelTest.gradientFrameBudget*' --tests '*W5cGradientSurfacePixelTest.authenticStorageCapability*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Seal slab identity, ranges and lifetime across mixed lanes.** First occurrence determines slab order. Exact repeated normalized sequences share a range; distinct sequences never alias. Slab content, ABI version, provenance and rewritten ranges enter the frame-local seal, while program keys still exclude stop data/count. One native buffer survives through the last consuming pass and is closed/quarantined by existing ownership journals.

- [ ] **Step 4: Complete overflow, limit and budget refusals before `Ready`.** Check raw and normalized count accumulation, `baseIndexU32 + countU32`, byte multiplication/addition, binding size, physical buffer size, binding counts and peak frame-local bytes. The public budget test uses a real low `RenderConfig.frameLocalBudgetBytes`, not an internal injected capability. Run a valid render immediately afterward on the same uninterrupted runtime/backend; use the same Surface only where its append-only public state permits it and document any distinct-Surface necessity exactly.

- [ ] **Step 5: Make the physical capability gate conditional and authentic.** Query only the production adapter snapshot. If StorageBuffer and all limits suffice, assert exact pixels. If a real fact is absent/insufficient, assert the typed public capability diagnostic. Do not replace this with a fake adapter, mock device or reflection test.

- [ ] **Step 6: Audit and remove only promoted legacy continuations.** W5c CLAMP/SRGB gradients on the four admitted lanes cannot call the legacy mapper/provider after ownership. Non-CLAMP, non-SRGB, local-matrix and H geometry families remain pre-admission and keep their scheduled legacy continuation. Keep historical gradient classes only for those remaining gaps; do not delete adjacent image/font/codec code.

- [ ] **Step 7: Update durable W5 status.** Mark W5c closed only with fresh counts. Record promoted matrix, test commands, exact pass/fail/skip counts, conditional capability result, any AA4 skips, remaining W5d/W5f/W5h gaps and W5d as the next stack. Do not copy transient agent reports into `refactor/`.

- [ ] **Step 8: Run the full W5c gate and bounded W3–W5b regressions, then commit.**

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface refactor/waves/W05-material-graph/status.md refactor/README.md
rtk git commit -m "refactor(material): close W5c gradient ownership"
```

### Task 8: Independent reviews, final verification and stacked PR

**Files:**
- Modify only production, public-test or durable refactor files required by verified findings.

**Interfaces:**
- Consumes: full `codex/w5b-blends..codex/w5c-gradients` range, both specs, this plan, the SDD ledger and fresh public verification evidence.
- Produces: independent READY verdicts, a clean verified branch and a W5c PR targeting `codex/w5b-blends`.

- [ ] **Step 1: Generate one full review package from the exact W5b base commit.** Include commit list, diff stat, full contextual diff, design/plan paths, SDD ledger and test reports. Do not use `HEAD~1`.

- [ ] **Step 2: Ask a fresh Sol agent for global specification compliance.** It checks the four families × four lanes, stop normalization, numeric/dependency contracts, storage ABI, coordinate seal, ownership boundary, exclusions and test discriminance. Every Critical/Important finding gets one Astra fix dispatch and a scoped Sol re-review.

- [ ] **Step 3: Ask a different fresh Sol agent for code quality and lifecycle review.** It examines overflow handling, immutable copies, program/binding separation, native rollback/close, pipeline/bind-group compatibility, public API behavior and legacy boundary. Run at most one broad final fix wave; subsequent work is scoped to concrete findings.

- [ ] **Step 4: Run fresh controller verification after all review fixes.**

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jsNodeTest --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git status --short --branch
```

- [ ] **Step 5: Confirm the real target inventory.** Run JS verification only if `:kanvas` exposes an authentic JS target. Do not create a substitute target or an infrastructure test. Do not run font, codec, GM/dashboard/render/baseline, Skia integration or `jpg-color-cube`.

- [ ] **Step 6: Reconcile status and evidence.** The test counts in W05 status and PR text must match the fresh XML/results exactly. Record authentic skips and remaining W5d/W5f/W5h work without claiming full W5 closure.

- [ ] **Step 7: Push the W5c branch and create/update the stacked PR.** Base must be `codex/w5b-blends`; include the W5b base SHA, design/plan links, promoted matrix, exact commands/counts, review verdicts, resource/capability behavior and deferred scopes.

```bash
rtk git push -u origin codex/w5c-gradients
rtk gh pr create --base codex/w5b-blends --head codex/w5c-gradients --title "W5c: promote gradients with a shared stop buffer" --body-file .superpowers/sdd/2026-09-11-w5c-gradients-implementation-plan/pr-body.md
```

- [ ] **Step 8: Record PR publication and leave the branch clean.** Add only the final PR number/link and publication SHA to W05 status if the existing convention does so, commit that doc update, push it, then verify no tracked/untracked files remain outside ignored SDD artifacts.

## Verification Commands

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jvmTest --tests '*Matrix3x3F64Test*' --no-parallel --rerun-tasks
rtk ./gradlew :math:matrix:jsNodeTest --no-parallel --rerun-tasks
rtk ./gradlew :kanvas:test --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git status --short --branch
```

The final test selectors may be narrowed to concrete public Surface classes or methods when diagnosing a failure. They may not be replaced by infrastructure assertions, GM/Skia similarity tests, font or codec suites.
