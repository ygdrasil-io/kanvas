# W5f Color Filters and Interpolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote all twelve existing non-runtime color filters and the five existing color-interpolation domains into the single sealed material authority, preserving their exact order, alpha/color contracts, immutable capture and public pixel evidence.

**Architecture:** Keep the approved plan-first vertical migration: `:render-ir` captures unchanged immutable public semantics; `:gpu-plan` issues the sole effective source/filter/interpolation program, bindings, numeric proof and typed refusal before `Ready`; `:gpu-renderer` emits and executes that exact operation graph through the existing source/geometry/blend bridges. Extend the closed material contract to V4 for filtered/interpolated sources, without a parallel material hierarchy or a semantic filter compiler in each geometry lane. Ordinary unfiltered V1/V2/V3 paths and ABI stay unchanged.

**Tech Stack:** Kotlin/JVM, `:math:color`, `:math:geometry`, `:math:matrix`, `:color-management`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit5, arbitrary-precision outward-rounded test intervals.

**Spec:** `refactor/specs/2026-09-09-w5-material-graph-design.md`, especially §§4–6,7.3–7.4,11.1,15,16–16.1. Existing W5a–e contracts remain binding. This is an execution refinement of that approved design, not a new public color model.

## Global Constraints

- Branch `codex/w5f-color-filters` starts at W5e commit `7a06459dde8fb3ba0a4c8287a8405bc1994ce883`. One stacked W5f PR targets `codex/w5e-decoded-images` and depends on W5e #2399. Do not mutate the parent PR or merge the stack.
- Deliver exactly the existing non-runtime `Matrix`, `Blend`, `Compose`, `Table`, `Lighting`, `SRGBToLinear`, `LinearToSRGB`, `HSLAMatrix`, `Lerp`, `HighContrast`, `Luma`, `Overdraw`. `ColorFilter.RuntimeEffect` and arbitrary source blend children/noise remain W5h/W5g respectively.
- Mandatory filter cells are Rect, Path fill, and image origins; filtered RRect/Path stroke/Point(s)/Text/Vertices/Mesh remain H unless separately evidenced. Unfiltered gradients retain their promoted Rect/RRect/Path-fill/Path-stroke lanes, and all five interpolation domains must cover those applicable lanes. Distinguish an interpolated V4 gradient from a filtered V4 source in H-lane admission; neither reject an ordinary new-domain gradient nor accidentally admit a filtered stroke. Image origins include decoded DrawImage/Nine/Lattice/Atlas and the existing Rect/Path-fill image-shader lanes.
- Interpolation is exactly `SRGB`, `LINEAR`, `OKLAB`, `HSL`, `OKLCH`. `WithWorkingColorSpace` contains `ColorInterpolation`, not a gamut or `ColorSpace`; no HDR target, arbitrary working gamut, interpolation-premul option or new hue method.
- All internal inputs/outputs of filters are RGBA linear-premultiplied. Color conversion functions/constant recipes belong to `:color-management`; do not copy different conversions into materializers. Reuse `:math:color` values. Geometry/transforms stay in `:math:geometry`/`:math:matrix`, with I32/I64/U32/F32/F64 suffixes; no private Rect/Point/Size/Matrix.
- The only effective source ordering is global §5.3: material/paint alpha and A8 masking, then origin/operation composition, then `PaintNode.colorFilter` exactly once, then final draw blend, then geometry*clip coverage. An internal `MaterialNode.WithColorFilter` remains at its original location in the DAG.
- `PaintNode.colorFilter` and its duplicated `EffectStack` snapshot must agree. Read the filter from PaintNode, authenticate the duplicate, consume it once. An absent/divergent mirror is a schema refusal, never a second evaluation. Spatial effects remain pre-admission.
- Preserve alpha0 safety and genuine unit-alpha branches. A zero-opacity/transparent source must not bypass a following filter that can restore RGB/alpha. No arbitrary clamp, division-as-exact assumption or transparent/child substitution to hide an unsupported source.
- Keep the single existing `GPUPlanSurfaceCandidateGate` and native ownership boundary. Extend its existing readers with the actual filter/interpolation promotions in each task; no second W5f predicate, classifier or fallback. Structurally recognized invalid filter payloads reach typed owned planner/capture refusal, not legacy.
- `GraphLimits(maxDepth=64,maxNodes=4096)` remains exact. Validate metadata before collection copies. Table has exactly256 entries; Matrix/HSLA exactly20 finite F32 coefficients; Lerp requires finite t in[0,1]. Checked I64 governs payload, index, stride, binding, stop-range, frame and staging arithmetic; no truncating casts.
- V4 programs are structure-only: source family, filter kinds/ordered children, blend mode, interpolation, operation/ABI versions and binding topology. Colors, coefficients, table bytes, t, stop values/count and pixels are dynamic bindings. Authenticate every value-dependent proof to those exact bindings and coordinate/source identities.
- W5f filter data is appended to the existing source uniform allocation, not a texture LUT. Matrix/HSLA80bytes, Table256 packed bytes, Lighting32bytes, Blend16bytes, Lerp16bytes; parameterless filters have no dynamic record. Every record is16-byte aligned and padding is zero. Table uses64 packed U32 words and integer extraction, not filtered texture sampling.
- Check actual uniform/buffer/binding capabilities and frame budgets before bulk packing/copy and native handles. Missing capabilities mean unavailable. Do not introduce per-filter buffers or host-evaluated color caches; the existing one frame-owned gradient storage slab and decoded-image cache/leases keep their owners.
- Gradient stops retain the32-byte stride, binding offset0, shared frame storage and checked U32 ranges from §7.4. V4 stop data carries its exact interpolation domain; signed OKLab/OKLCH components are not constrained by the old `[0,1]` RGB validation. Old V1/V2 source programs/slabs retain their historical interpretation.
- One finite shared typed operation graph is consumed by numeric proof and WGSL emission. Validate lazy versus eager branches, log2/exp2/pow/sqrt/division/atan2 and F32→I32/Table conversions honestly under the pinned `WgslFloatEnvelopeV1`. No hand-authored shader formula beside a nominal proof graph or WGSL string replacement.
- Independent CPU oracles duplicate published equations and outward-rounded operation bounds; never call production filter, color-conversion, plan, decomposition, shader, hashing or numeric-authority helpers. Mandatory expected RGBA8 sets are singletons or two adjacent codes, established BEFORE Surface execution. Wider/unbounded fixtures do not close a cell.
- Tests observe only public `Surface`/`Canvas`/`Picture`, render pixels, diagnostics/statistics and native evidence scopes. Require public Render/Readback before pixel comparison. No infrastructure/static-source/private-plan/ABI/handle/counter test, fake device, injected native failure, reset/GC/dispose workaround, native-access override or widened tolerance.
- Fonts, external codecs, `jpg-color-cube`, GM/dashboard/renders/references/scores/baseline regeneration are excluded. Image/mask filters, blur/crop/backdrop/layers remain W6. Do not fix W4 GM producer/AA geometry as an incidental W5f change.
- Known W5e limits remain explicit: Gradle1/native133 despite passing assertion XML, two unavailable AA4 cases, generic Picture clip, no public Surface.close or same-target numeric rollback proof, conservative Atlas numeric domains, legacy extra-tail refusal/prevalidation-copy debt. W5f neither masks these nor declares global ISO.
- Use adapted non-Sol implementers, Astra/high for the operation/numeric/interpolation tasks; Sol review-only for task spec+quality and final branch review. This plan receives the requested independent Astra review before implementation. Execute sequentially, serialize Gradle, and reuse supported completed seats if fresh dispatch is refused; never bypass the harness.
- Track durable state only in this plan, `refactor/waves/W05-material-graph/status.md`, `refactor/README.md`. Temporary task briefs/reports/review packets stay in the ignored plan-specific `.superpowers/sdd/` workspace. Do not re-create tracked intermediate status markdowns.

## Existing-code map and new contract

Existing capture is in `kanvas/.../canvas/DisplayOpSnapshot.kt` and `kanvas/.../render/ir/PaintSceneAdapter.kt`; filters/interpolation already round-trip through current Picture10/schema4. No version bump is required without an actual semantic schema change. Public `ColorMatrixF32` is mutable; snapshot its20 values, tables and HSLA arrays before retaining them. `EffectiveMaterialPlanner` currently rejects effects, `WithColorFilter`, non-SRGB interpolation and working-space wrappers. The Surface gate, W5d candidate reader and `PaintSceneAdapter` validity path also reject these; all actual readers must move with the vertical delivery.

The V1/V2/V3 table currently represents unary parents by child-at-ref-minus1. W5f keeps a unary material source chain; Compose/Lerp form a FILTER operation DAG over the same input, not arbitrary material child graphs. Extend table interning, sourceIdentity, rebasing, child ownership and all exhaustive consumers atomically. Do not implement generic W5g material DAG sharing here.

Task1 defines these exact cross-task interfaces in `org.graphiks.kanvas.gpu.plan`:

```kotlin
public sealed interface SourceCoordinatesV4 {
    public data object None : SourceCoordinatesV4
    public data class V1(val plan: MaterialCoordinatePlanV1) : SourceCoordinatesV4
    public data class V2(val plan: MaterialCoordinatePlanV2) : SourceCoordinatesV4
    public data class V3(val plan: ImageCoordinatePlanV1) : SourceCoordinatesV4
}
// Added member of the existing PlanDrawMaterialAuthority, not another hierarchy:
// MaterialV4(ref: MaterialPlanRef, coordinates: SourceCoordinatesV4)
public class ColorFilterExecutionPlanV1 private constructor(
    public val structuralIdentity: String,
    public val canonicalIdentity: String,
    capturedFilter: ColorFilterNode,
    graph: ColorOperationGraphV1,
) {
    public val dynamicByteCountI64: Long
    public fun copyDynamicBytes(): ByteArray
    public fun copyOperationGraph(): ColorOperationGraphV1
    // Only ColorFilterPlanCompilerV1 can issue this validated immutable value.
}
public class ColorNumericAuthorityV1 private constructor(
    public val canonicalIdentity: String,
    public val outputSourceProof: ColorSourceProofV1,
) {
    public fun authenticates(execution: ColorFilterExecutionPlanV1,
        source: ColorSourceProofV1): Boolean
    // Issued only after proof of the exact source component ranges and graph.
}
public class ColorSourceProofV1 private constructor(
    public val canonicalIdentity: String,
    public val sourceIdentity: String,
    public val coordinates: SourceCoordinatesV4,
) {
    public fun copyOperationGraph(): ColorOperationGraphV1
    public fun authenticates(table:MaterialPlanTable, root:MaterialPlanRef,
        coordinates:SourceCoordinatesV4): Boolean
}
public sealed interface ColorSourceProofResultV1 {
    public data class Ready(val source:ColorSourceProofV1) : ColorSourceProofResultV1
    public data class Refused(val diagnosticCode:String) : ColorSourceProofResultV1
}
public object ColorFilterPlanCompilerV1 {
    public fun compile(filter: ColorFilterNode): ColorFilterCompileResultV1
}
public sealed interface ColorFilterCompileResultV1 {
    public data class Ready(val execution: ColorFilterExecutionPlanV1) : ColorFilterCompileResultV1
    public data class Refused(val diagnosticCode: String) : ColorFilterCompileResultV1
}
```

`ColorFilterExecutionPlanV1` exposes `structuralIdentity:String`, `canonicalIdentity:String`, `dynamicByteCountI64:Long`, `copyDynamicBytes():ByteArray`, `copyOperationGraph():ColorOperationGraphV1`. Add `ColorFilteredProgramV4(child:MaterialProgramPlan,filterStructureIdentity:String)` to the existing MaterialProgramPlan and an immutable `ColorFilterBindingV4` to MaterialBindingPlan, issued from the execution plan plus source proof. Table root references and coordinate owners remain explicit; filtered roots cannot lose a gradient/image child during interning.

`ColorOperationGraphV1` is immutable, ordered and handle-free. Its typed nodes include InputLinearPremul, finite F32/U32 constants/dynamic slots, Add/Subtract/Multiply/Divide, comparisons, true lazy guarded branches, Min/Max/Clamp, Floor/Round, checked integer selection, conversion recipes, premultiply/unpremultiply and calls to the shared `BlendFormulaProgramV1`. Every built-in and executed branch is decoded by the proof and emitted from the same node. The renderer may choose syntax, never semantics.

The declarations above are contract signatures, not unchecked scaffolding to commit. Task1 implements their bodies together with Matrix. The compiler retains the existing immutable captured IR payload, computes checked offsets/byte counts and validates metadata without bulk packing or array copies. `copyDynamicBytes()` is invoked only by raw materialization after the actual source-plus-filter frame/device budget checks; it packs one declared allocation, not a speculative array later checked against budget. `SourceCoordinatesV4` belongs in `MaterialPlan.kt`; its identity is the canonical identity of its existing coordinate owner, or the literal `none-v4`.

The source proof issuer is internal `ColorSourceProofCompilerV1.seal(table:MaterialPlanTable, root:MaterialPlanRef, coordinates:SourceCoordinatesV4, deviceBoundsF32:RectF32):ColorSourceProofResultV1`. It derives a typed source-prefix graph and predicate-conditioned relational facts from authenticated material bindings and the actual existing source/coordinate/degeneracy/sampling authorities. Callers supply no arbitrary ranges. Task1 implements Solid/Opacity; Task2 propagates ordered filter outputs; Task5/6 extend the same issuer to gradient coordinate/tile/selection/interpolation/transfer/premultiply nodes; Task7 extends it to decoded sampling, A8 child/mask and Atlas source composition. Each bridge binds exact old authority identity, source graph/version, uniform/upload/stop bits and coordinate/device bounds. Existing `ProvenFinite` or coarse signed image bounds alone cannot issue this relational certificate.

The certificate retains shared expression provenance and path predicates, not four independent component boxes: alpha-zero and alpha-one cases are separate from the normal-positive-alpha division domain; RGB/alpha factors and the denominator's actual bounds remain linked; HSL max/min/delta equalities and its denominator are proved on their selected branch. Signed Display-P3/cubic outputs do not acquire an invented `0<=RGB<=alpha` relation. Preserve all permitted F32 rounding/fusion/reassociation alternatives; do not algebraically cancel rounded premultiply/unpremultiply. A branch that cannot close is a precise source-domain refusal, not nominal completion of a required filter. The filter issuer is internal `ColorNumericAuthorityV1.seal(execution:ColorFilterExecutionPlanV1, source:ColorSourceProofV1):ColorNumericAuthorityV1?`; null produces `unsupported.material.filter.numeric-domain-unbounded`. Its `outputSourceProof` contains the composed prefix/filter graph and conditioned output facts, reusable by the next internal filter, external paint filter and final blend. Rebase/authenticate the complete certificate after table/slab interning; never attach a certificate from a merely textually equal source.

`ColorFilterBindingV4` owns that authority plus immutable execution, with `canonicalIdentity:String`; its constructor is private and its internal `seal` requires `authenticates` to succeed. V4 MaterialProgramPlan members implement `copyNumericOperationGraphV1()` via `NumericOperationGraphV1.colorSourceV4()`, while every V4 consumer also requires the full bound source-prefix proof; the generic source input is not itself a filter/interpolation proof. The emitter entry is internal `W5fColorOperationEmitterV1.emit(graph:ColorOperationGraphV1,inputRgbaExpression:String,uniformWordOffsetU32:Long):String`; it emits graph nodes only, validates its offset, and creates genuine `if` blocks for lazy branches. A WGSL expression string is syntax input, not a second semantic recipe.

Raw V4 uses measurement before packing, since existing `RawMaterialRequirementsV2.of` allocates bytes before later budget checks. Add internal `measureV4(table:MaterialPlanTable,root:MaterialPlanRef):MaterialSourceFootprintV4`, containing exact checked uniform/storage/binding counts, immutable source/filter/coordinate identities and packing recipe, but no packed arrays. `requireFrameBudgetV4(sources:List<MaterialSourceFootprintV4>,nonUniformBytesI64:Long,budget:PlanBudget,capabilities:PlanCapabilitySnapshot,legacyCode:String):MaterialSourcePackingPermitV4` validates all unique physical allocations and returns an opaque permit bound to those exact footprints. Only `packV4(footprint:MaterialSourceFootprintV4,permit:MaterialSourcePackingPermitV4):RawMaterialRequirementsV2` may allocate/copy. Preserve earlier nonuniform/unextended-source refusal owners; W5f is causal only for its actual added uniform requirement. All admitted V4 frame builders use this seam before `Ready`; renderer consumes the same packed identity. Historical V1/V2/V3 byte layout/semantics remain unchanged, but must not route V4 through eager `of` or invent a permit at native execution.

Preserve earlier diagnostic owners: nonfinite public coefficients/t already rejected by capture retain their existing capture code; planner codes apply only to payloads that actually reach planning. Do not relabel an earlier capture or archive failure to manufacture a W5f diagnostic. Verify the owning path in each public refusal test.

Task1 also defines test-only `W5fColorCpuOracle` and `W5fSurfacePixelFixtures`, with these exact entry points reused later:

```kotlin
fun expectedPaintSource(color: ColorARGB, filter: ColorFilter,
    destination:ColorARGB = ColorARGB.Transparent, finalBlend:BlendMode = BlendMode.SRC_OVER,
    coverageF32:Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult
fun expectedShaderSource(color: ColorARGB, shaderAlphaF32: Float,
    paintAlphaF32: Float, internal: ColorFilter?, external: ColorFilter?,
    destination:ColorARGB = ColorARGB.Transparent, finalBlend:BlendMode = BlendMode.SRC_OVER,
    coverageF32:Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult
fun expectedGradientPixel(domain: ColorSpaceInterpolation, left: ColorARGB,
    right: ColorARGB, tF32: Float, external:ColorFilter? = null,
    destination:ColorARGB = ColorARGB.Transparent, finalBlend:BlendMode = BlendMode.SRC_OVER,
    coverageF32:Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult
fun expectedImagePixel(image:Image,sampling:SamplingOptions,sourcePointF32:Point2F32,
    paint:Paint,atlasEntryColor:ColorARGB? = null,atlasEntryBlend:BlendMode? = null,
    destination:ColorARGB = ColorARGB.Transparent,finalBlend:BlendMode = BlendMode.SRC_OVER,
    coverageF32:Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult
fun requireBounded(expected: WgslFloatEnvelopeV1Oracle.DrawResult)
fun assertNativePixels(result: RenderResult, expected: List<WgslFloatEnvelopeV1Oracle.DrawResult>)
```

These are CPU/test interfaces, not production source evaluators. `assertNativePixels` first requires `result.nativeEvidenceScopeKinds` to contain Render and Readback, then admits each four-byte public pixel through the expected channel sets. Every test invokes `requireBounded` before creating/rendering its Surface. Do not pass a production MaterialPlanTable into the oracle.

## Sequential delivery

### Task 1: Deliver Matrix on Solid Rect end-to-end with sealed V4 authority

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterExecutionPlanV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterPlanCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5fPlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialSourceFootprintV4.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/NumericOperationGraphV1.kt`: add `colorSourceV4():NumericOperationGraphV1`, the existing source-to-attachment wrapper with `INPUT_MATERIAL_LINEAR_PREMUL`; its V4 input is bound to the complete authenticated ColorSourceProofV1 graph, never an unproved opaque input
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aMaterialGraphContract.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bMixedFramePlanV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bDestinationGraph.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dRenderGraphCanonicalSeal.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralRenderGraphCanonicalSeal.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4eNativePayloadPlan.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`: explicit V4 handling/rejection only, no premature promotion of H lanes
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5aMaterialPlanAuthorityV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4aAnalyticRectGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bAnalyticRectGraphLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bAnalyticRRectGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4cPathFillGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dPathStrokeGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`: explicit unpromoted-V4 refusal in Task1; actual Path support follows Task2, unfiltered RRect/stroke interpolation Task5
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4aPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUCorePrimitivePreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4cPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dGeneralPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4bPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4ePreparedAuthority.kt`: preserve/reject the exact incoming authority, never reconstruct V1/V2 from optional coordinate maps
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5dGradientCandidateV2.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ColorFilterCapturePreflight.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/SceneRecordingValidationException.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayListBuffer.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fSurfacePixelFixtures.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorFilterSurfacePixelTest.kt`

**Interfaces:** Consumes existing DrawNode/Solid/Opacity/table/geometry/blend. Produces the exact V4/compiler/oracle interfaces above, a Matrix-only vertical admission initially restricted to Rect, and unchanged ordinary V1/V2/V3 behavior. Task1 V4 frames remain single-family Rect, including ordered multi-draw/destination-read; mixed-geometry V4 composites are rejected before lane invocation/packing until Task2 construction-first issuance. Required diagnostics: `invalid.material.filter.schema`, `invalid.material.filter.matrix`, `unsupported.material.filter.numeric-domain-unbounded`, `resource-limit.w5f.filter-binding`, `budget.w5f.filter-uniform`.

- [x] **Step 1: Add public Matrix RED witnesses and independent intervals first.** Literal row-major matrices exercise normalized translation, channel mixing, alpha0, nonunit alpha and clipping. Include an endpoint translation that must change the output and a nonendpoint case bounded to one/two adjacent codes.

```kotlin
@Test fun matrixTranslationUsesNormalizedUnitsAndIsPlanOwned() {
    val matrix = ColorMatrixF32.of(floatArrayOf(
        1f,0f,0f,0f,1f, 0f,1f,0f,0f,0f,
        0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f))
    val filter = ColorFilter.Matrix(matrix)
    val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black, filter)
    W5fSurfacePixelFixtures.requireBounded(expected)
    val surface = Surface(1,1)
    surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),
        Paint(color=ColorARGB.Black,colorFilter=filter,antiAlias=false)) }
    W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected))
}
```

- [x] **Step 2: Run only `*W5fColorFilterSurfacePixelTest.matrix*` before production.** Record actual refusal or authority/pixel RED, timestamps, Gradle status and process failure separately. A compiling/PASS fixture is not fabricated RED; correct an oracle/fixture mistake before changing production.
- [x] **Step 3: Issue the Matrix recipe and V4 source once, then emit it through the existing physical source stage.** Implement the typed operation semantics below, immutable20-float record, exact source+binding proof and genuine guarded division. Validate the duplicated paint filter before removing its already-consumed EffectStack entry from the construction projection; preserve the original semantic DrawNode for authentication. Update all exhaustive V4 consumers and interning/sourceIdentity in this same task.

First wire handle-free metadata preflight before the first recording/snapshot copies: internal `ColorFilterCapturePreflight.validatePaint(paint:Paint,limits:SceneCaptureLimits):RenderDiagnostic?` walks the whole public paint graph with the configured depth/node/cycle policy, without copying payloads. Task1 checks Matrix's20 indexed coefficients through `matrix[indexI32]` before `toFloatArray`. `GeometrySnapshotContext` receives the recording limits from its buffer and checks all paint roots before any snapshot payload; direct Scene/Paint capture calls the same metadata validator before recursive IR conversion. A recording rejection throws public `SceneRecordingValidationException(diagnostic:RenderDiagnostic):IllegalArgumentException`; capture translates the same diagnostic to existing CaptureFailure/SceneCaptureResult. The new exception exposes the diagnostic, not private IR. Retain `non-finite-value` for nonfinite coefficients; no relabelling of archive/capture codes. Failed append commits neither payload nor pending recording budgets; validate recovery through the same public Canvas/Surface. Tasks2/3/4 add Lerp/Table/HSLA metadata to this same validator before admitting those filters.

```text
u.rgb = if input.a == 0 then 0 else if input.a == 1 then input.rgb else input.rgb / input.a
u.a = input.a
v[row] = (((m[row,0]*u.r + m[row,1]*u.g) + m[row,2]*u.b) + m[row,3]*u.a) + m[row,4]
straight = clamp(v,0,1)
output = (straight.rgb * straight.a, straight.a)
```

All five products/additions and permitted reassociations belong to the operation proof; `let` is not a rounding barrier. Dynamic offsets are checked before allocation, little-endian packing,16-byte alignment and zero padding. Preserve source coverage/blend proofs, not an inherited provenOpaque flag invalidated by the filter.

Static reader inventory is mandatory, including casts/`else` branches that compilation will not expose. PlanPasses/materialPlanRef and RenderGraph authentication retain V4 root+coordinate owner. Rect and destination-read lowerers consume V4. W5aMaterialPlanAuthorityV2 takes the exact per-command `PlanDrawMaterialAuthority` from the planner, not a V1/V2 reconstruction based on nullable maps. Canonical seals incorporate V4 version, complete source/filter/binding proof and coordinates. Path/General readers first explicitly reject unpromoted V4, then Task2 extends them; RRect/stroke accept only new-domain unfiltered gradients in Task5, never filtered H sources. Existing gate must refuse an unpromoted whole frame before admission; a later owned failure is terminal, not a legacy continuation. No reader substitutes a child, a bbox or a transparent source.
- [x] **Step 4: Run the new public class plus exact W5a Solid/Opacity cases, then standalone five-module compile including color-management.** Inspect fresh public XML; native133 remains command failure. Independently review Matrix/order/binding/proof/ownership; no private tests.
- [x] **Step 5: Commit the Matrix vertical slice after the clean task review.** `rtk git diff --check`; explicit-path add; commit `feat(gpu): seal W5f matrix color filters`. Execution R2 permits provisional reviewable commits before review; closure follows clean scoped review and separate fix commit.

### Task 2: Close source/filter ordering, Compose/Lerp and Path-fill transport

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterPlanCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5dGradientCandidateV2.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fFilterOrderingSurfacePixelTest.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aMaterialGraphContract.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RenderGraph.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dRenderGraphCanonicalSeal.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralRenderGraphCanonicalSeal.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4cPathFillGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dGeneralPreparedAuthority.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ColorFilterCapturePreflight.kt`
- Audit/reuse unchanged: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`
- Audit/reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aCompositeGraphLowerer.kt`
- Audit/reuse unchanged: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Audit/reuse unchanged: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorFilterSurfacePixelTest.kt`
- Audit/reuse unchanged: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4eNativePayloadPlan.kt`
- Audit/reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4cPreparedAuthority.kt`
- Audit/reuse unchanged: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4ePreparedAuthority.kt`
- Audit/reuse unchanged: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`
- Audit/reuse unchanged: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`

R14 explicitly reconciles these nine audit/reuse paths after the first Task2 Sol review: they are NOT required code hunks. Their functional obligations remain binding: exact source/execution seal, final-graph/lowerer allocation correspondence, existing-reader delegation without a second compiler, retained six Task1 public witnesses, closed W4e V4 reader, producer/source separation, authentic legacy payload reader, immutable snapshot and first-preflight ordered IR capture. Actual Task2 consumers provide the required new functionality; no requirement is dropped and no unrelated historical route is declared fully verified. Sol's nine targeted checks and limitations remain in the review record; full prepared/W4e topologies and shared oracle arithmetic were not comprehensively reaudited. Any real missing contract still requires a functional fix. The original formal Missing finding stays recorded; sole scoped R14 confirmation marks it ADDRESSED/spec Compliant/quality Approved, with no new defect. Task2 is closed within its actual Matrix/Compose/Lerp Rect/Path/General/composite slice.

**Interfaces:** Consumes Task1 V4 unary source wrapper and immutable filter graph. Produces `Compose(outer,inner)` and `Lerp(t,dst,src)` recipes, full ordered wrappers and filtered Path fill/General source transport. Source blend children remain excluded; filter Lerp evaluates two filter functions on the SAME original input. Required additional code: `invalid.material.filter.lerp`.

- [x] **Step 1: Add genuine order witnesses before widening production.** Use translations plus scaling so `outer(inner(c)) != inner(outer(c))`; compare Shader.WithColorFilter versus external Paint.colorFilter across a nonunit paint alpha. Add a Matrix with alpha translation1 after Shader.Opacity(...,0): it must restore opaque output, not canonicalize away the external filter. Include Compose/Lerp0/1/.5, nested internal filters and two filtered Path fills, one General/destination-read with established W4 geometry.

```kotlin
val inner = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.25f,0f,0f,0f) })
val outer = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) })
val ordered = ColorFilter.Compose(outer,inner)
val reversed = ColorFilter.Compose(inner,outer)
val lerped = ColorFilter.Lerp(.5f,ordered,reversed)
```

Derive both expected expressions before rendering; do not use merely identity matrices. Test invalid/nonfinite t via public constructors/Canvas/render and precise typed refusal. A destination blend DST NoOp is not a source Blend-filter DST NoOp.
- [x] **Step 2: Run `*W5fFilterOrderingSurfacePixelTest*` before production and record real RED/PASS separately.** Existing geometry refusals must be corrected in the fixture or named, not silently implemented as filter fixes.
- [x] **Step 3: Compile the ordered filter DAG with one source owner and update true nested wrapper order.** Iterative depth64/nodes4096 traversal, distinct ordered children, exact canonical keys; no reassociation across filter clamps/conversions. Extend the source-coordinate reader through filter/opacity wrappers without moving CoordClamp/local matrices across filters. Apply the external filter AFTER paint alpha/origin composition and BEFORE final blend; remove only its authenticated mirror from physical construction.

Propagate `outputSourceProof` between Compose children; both Lerp branches share the authenticated original input and merge their actual conditioned outputs under the rounded premultiplied Lerp nodes. Add Lerp finite/range metadata to the first preflight: nonfinite retains `non-finite-value`, finite out-of-range yields `invalid.material.filter.lerp`. Extend Path-fill direct/stencil/General/destination readers and canonical seals from the Task1 inventory, preserving the incoming V4 authority intact.

Task2 also closes the execution-discovered composite publication boundary: `W5aCompositePlanCompiler.kt` (which owns `W5aCompositePlanV1`) calls lane compilers returning `Ready` before its shared-target/geometry/stops/scratch peak is known; `W5bGeometryLanePlanV3.kt` native composites likewise receive issued graphs. Separate compiler-owned immutable, handle-free lane construction metadata from final `Ready`, retain exact source/coordinate authorities and interned proofs, derive actual unique whole-frame allocation inventory, obtain the frame/device packing permit, then pack V4 and issue final graphs/lanes. No packed/discarded V4 bytes, placeholder source, mutable issuance, shadow semantic planner or late aggregate permit. Update related existing lane/compiler/RenderGraph/footprint/raw/canonical/prepared handoffs required for this seam, without geometry repair. Before widening admission, add real public mixed Rect/Path/General RED proof and legitimate configured composite-budget control/refusal/recovery where needed; no infrastructure observations. Remove Task1's temporary mixed-V4 restriction only with this actual construction-first issuance. Ordinary V1/V2/V3 ABI and earlier refusal owners remain unchanged.

```text
Compose(outer,inner)(x) = outer(inner(x))
Lerp(t,dst,src)(x) = (1-t)*dst(x) + t*src(x)  // all four premultiplied components
Shader.Opacity(WithColorFilter(child,F),a) = a * F(child)
WithColorFilter(Shader.Opacity(child,a),F) = F(a * child)
```

- [x] **Step 4: Fresh ordered/filter Path public gates + preceding W5a/b/c/d affected cases; static audit all V4 identity/geometry bridges.** A field-only transport or bbox is not genuine General support. Recompute effective opacity/coverage/final-blend eligibility from the filtered source proof.
- [x] **Step 5: Local provisional `feat(gpu): preserve W5f ordered filter composition`231eae980 precedes exact Sol review under R2; separate R14 documentation reconciliation02c63c14d receives sole scoped ADDRESSED/spec Compliant/quality Approved confirmation. Task2 closes only after that review.**

### Task 3: Deliver Table, Lighting, transfer and Blend filters

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterPlanCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5fPlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/BlendFormulaProgramV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/BlendFormulaOperationGraphV1.kt` only for necessary typed access to the EXISTING exact formulas
- Audit/reuse existing under R9: `color-management/src/main/kotlin/org/graphiks/kanvas/color/ColorInterpolationProgramV1.kt`, already delivered/reviewed in Task1 for EOTF/OETF. Reuse those actual recipes; extend this same provider in place only for an actually missing required conversion, never recreate or duplicate it. This inherited-file audit does not require an artificial hunk.
- Audit/reuse existing under R9: `gpu-plan/build.gradle.kts`, whose `implementation(project(":color-management"))` was already delivered/reviewed in Task1. Preserve the one-way dependency; do not expose color-management recipe types in public gpu-plan signatures. `color-management` already depends on `:math:color` and needs no build change or reverse dependency. No new dependency hunk is required for this retained contract.
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ColorFilterCapturePreflight.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorFilterSurfacePixelTest.kt`

**Interfaces:** Consumes shared color graph/recipes and `BlendFormulaProgramV1`. Produces filter opcodes Table/Lighting/SRGBToLinear/LinearToSRGB/Blend. `ColorInterpolationProgramV1` in color-management publishes immutable handle-free conversion recipes and constants keyed by semantic conversion identity; gpu-plan instantiates them into ColorOperationGraphV1 without reverse dependencies or duplicated shader conversion formulas. Its `RecipeKind` values are `EOTF`, `OETF`, `RGB_TO_HSL`, `HSL_TO_RGB`, `LINEAR_RGB_TO_OKLAB`, `OKLAB_TO_LINEAR_RGB`, `OKLAB_TO_OKLCH`, `OKLCH_TO_OKLAB`; `recipe(kind:RecipeKind):Recipe` returns an immutable ordered scalar-operation recipe with `identity:String` and exact F32 constant bits. This enum deliberately does not import render-ir's `ColorInterpolation`; the gpu-plan adapter maps that enum to recipes. Recipes are reusable semantic definitions, not a second bound/native material graph. Required additional code: `invalid.material.filter.table`.

- [ ] **Step 1: Add public tables0/255/interior, nonidentity/inverse table, transfer breakpoint neighborhoods, Lighting nontrivial mul/add with ignored parameter alpha, and Blend all29 public modes.** For each kind include alpha0/nonunit, clamp-needed input and noncommutative composition with Matrix. Table255/0 alone is insufficient for index rounding. Derive expected Table indices under intervals before rendering; ambiguous multiple nonadjacent results do not close the mandatory case.

```kotlin
val inverted = UByteArray(256) { indexI32 -> (255-indexI32).toUByte() }
val table = ColorFilter.Table(inverted)
val filter = ColorFilter.Compose(table,ColorFilter.LinearToSRGB)
// Public payloads of255/257 entries must refuse precisely, not truncate or normalize.
```

- [ ] **Step 2: Run the new exact Table/Lighting/transfer/Blend methods on unchanged production, inspect genuine RED.** Blend formulas themselves already exist; a new mode fixture that PASSes is not an invented arithmetic defect.
- [ ] **Step 3: Emit the following exact graph and seal every dynamic payload/index/domain.** Table record is64 little-endian U32 words; index∈[0,255] checked before fetch; extract `(word >> ((index&3)*8))&255`. Do not bind a LUT texture. Transfer recipes are EOTF/OETF global §11.1, operating on numerical straight RGB without target retagging. Lighting ignores mul/add alpha. Blend calls the exact shared29 formula authority with `dst=input,src=toLinearPremul(color)`; no new mode formulas/native semantic choice.

Before any recording/IR copy, `ColorFilterCapturePreflight` checks `table.size==256`, otherwise `invalid.material.filter.table`; do not call `copyOf` or `ImmutableUBytes.copyOf` first. The raw V4 measurement/permit validates Table's actual appended allocation before64-word packing. Table's proof keeps the discrete admissible integer set from rounded scalar evaluation, checks every reachable index, fetches the corresponding bound byte and propagates the union. It must not claim `round`, F32→U32 or a table selection is continuous/exact around a half-integer.

```text
Table(x) = P(table(round(clamp(U(x),0,1)*255))/255)
Lighting(x) = P(clamp(U(x).rgb*toLinear(mul.rgb)+toLinear(add.rgb),0,1),x.a)
Blend(x) = BlendMathV1(dst=x,src=toLinearPremul(color),mode)
SRGBToLinear(x) = P(EOTF(U(x).rgb),x.a)
LinearToSRGB(x) = P(OETF(U(x).rgb),x.a)
```

Use genuine alpha guards and operation-specific domains, including signed intermediate data where the requested conversion supports it. The pinned pow/log2/exp2/division bounds are not exact identities. Entirely unbounded mandatory domains require reformulation, not nominal unsupported-mode closure.
- [ ] **Step 4: Fresh full filter/order gates plus affected W5b blend and W5e source/format gates.** Public mutations of Table after capture cannot change retained frames. Review packed Table bounds and shader/proof correspondence statically, never assert private bytes.
- [ ] **Step 5: Commit `feat(gpu): add W5f table transfer and blend filters` after Sol review.**

### Task 4: Deliver HSLAMatrix, HighContrast, Luma and Overdraw

**Files:**
- Modify: `color-management/src/main/kotlin/org/graphiks/kanvas/color/ColorInterpolationProgramV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorFilterPlanCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5fPlanDiagnostics.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorFilterSurfacePixelTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fFilterOrderingSurfacePixelTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ColorFilterCapturePreflight.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`

**Interfaces:** Produces all remaining existing non-runtime filters, shared normative RGB↔HSL recipes and precise `invalid.material.filter.hsla`. HighContrast stays the public preset grayscale=false/invert=NONE/contrast=.5, not a new configurable API.

- [ ] **Step 1: Public REDs for nonidentity HSLA hue/saturation/alpha transform, grayscale hue-undefined branches, preset HighContrast clipping, Luma alpha and each Overdraw bucket.** Include nonendpoint alpha, alpha0 and ordered Compose with Matrix for each remaining filter. Check Overdraw depends on alpha, not input RGB. HSLA19/21/nonfinite and post-capture mutation use only public APIs.

```kotlin
val hsla = ColorFilter.HSLAMatrix(floatArrayOf(
    1f,0f,0f,0f,.25f, 0f,.5f,0f,0f,0f,
    0f,0f,1f,0f,0f, 0f,0f,0f,.5f,0f))
val composed = ColorFilter.Compose(ColorFilter.Luma,hsla)
```

- [ ] **Step 2: Run the exact new methods before production and classify real failures.** HSLA numeric branch ambiguity must be addressed by a discriminating bounded fixture and honest operation proof, not broadening output tolerance.
- [ ] **Step 3: Add the exact §11.1 recipes, preserving all branch/clamp positions.** HSL max/min/delta tie rule, delta0 guard, hue modulo1, saturation division, sector selection; no S/L clamp before hslToRgb. HighContrast scale3 and final clamp only. Luma gives black RGB with `a * dot(straightRGB,(.2126,.7152,.0722))`. Overdraw palette is exactly ARGB `[80FF0000,8000FF00,800000FF,80FFFF00,8000FFFF,80FF00FF]` and index=min(round(clamp(alpha)*255),5).

First preflight checks `values.size==20` before scanning/copying HSLA; then every value is finite. Wrong length yields `invalid.material.filter.hsla`; nonfinite retains `non-finite-value`. The conditioned source proof—not a box crossing delta0/denominator0—justifies the true HSL branches. Preserve that output certificate for later filters and final blend.
- [ ] **Step 4: Run all twelve filter kinds and ordering cases, compile all production modules and request Sol numeric/spec/quality review.** No runtime filter admission. Preserve old ordinary source ABI and rounded blend functions.
- [ ] **Step 5: Commit `feat(gpu): complete W5f nonspatial color filters`.**

### Task 5: Deliver LINEAR and OKLAB interpolation with domain-tagged shared stops

**Files:**
- Modify: `color-management/src/main/kotlin/org/graphiks/kanvas/color/ColorInterpolationProgramV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientInterpolationPlanV4.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialSourceFootprintV4.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dRenderGraphCanonicalSeal.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralRenderGraphCanonicalSeal.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5bGeometryLanePlanV3.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientPlanV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingCaptureV2.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientNumericOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4bAnalyticRRectGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5bAnalyticRRectGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dPathStrokeGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W4dGeneralPathGraphLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4bPreparedAuthority.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/GPUPlanW4dPreparedAuthority.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5dGradientCandidateV2.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fGradientInterpolationSurfacePixelTest.kt`

**Interfaces:** Consumes original W5d geometry/coordinates/tile/degeneracy and stop capture; produces `GradientInterpolationPlanV4` issued with `domain:ColorInterpolation`, exact F32 converted stop tuples, authenticated coordinate/range/slab/degeneracy and dynamic data. One tagged frame-owned32-byte stop ABI remains; each range identifies its domain. No legacy source reads signed Lab as RGB, and no untagged dedup aliases equal numerical tuples from distinct domains.

Add `GradientInterpolationProgramV4(addressing:GradientAddressingProgramV2,domain:ColorInterpolation):MaterialProgramPlan` and issued `GradientInterpolationBindingV4:MaterialBindingPlan`. The addressing field is the existing family/tile/coordinate program, not an evaluated SRGB child source: V4 evaluates its coordinate/parameter/stop selection once, then the requested interpolation and inverse conversion. A V4 gradient leaf is one table entry; ColorFilteredProgramV4/Opacity are its unary parents. Binding owns original stop identities, prepared domain range, family uniforms/degeneracy, coordinate certificate and ColorSourceProofV1. New stop slab entries carry domain in their semantic range identity while their physical reserved fields remain zero. Nonexecuted old-domain shadow ranges are not allocated merely to retain a child pointer. Extend the same source proof issuer through the actual rounded interpolation/premultiply graph; the final blend consumes its conditioned output proof.

- [ ] **Step 1: Add LINEAR/OKLAB public REDs for all four gradients on Rect/RRect/Path fill/Path stroke and existing coordinate wrappers/tile modes.** Red→green midpoint distinguishes OKLab from SRGB/LINEAR; black→white distinguishes LINEAR from SRGB. Include transparent stops, duplicates,1/2/16/>16stops, >1 domain in one frame and changed values between renders. Filter the interpolated result with Matrix/Table on Rect/Path fill to prove the pipeline is not disconnected; filtered RRect/stroke remain H.

```kotlin
val shader = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(3f,0f),
    listOf(GradientStop(0f,ColorARGB.Black),GradientStop(1f,ColorARGB.White)),
    interpolation=ColorSpaceInterpolation.LINEAR)
val expected = (0..2).map { W5fColorCpuOracle.expectedGradientPixel(
    ColorSpaceInterpolation.LINEAR,ColorARGB.Black,ColorARGB.White,(it+.5f)/3f) }
expected.forEach(W5fSurfacePixelFixtures::requireBounded)
```

- [ ] **Step 2: Run the exact new LINEAR/OKLAB methods before production; record real RED and oracle boundedness before execution.** Avoid inherited Conical cross-zero domains for mandatory positive witnesses; preserve separate exact refusal tests.
- [ ] **Step 3: Prepare stops in their requested straight domain, interpolate four components, return to linear sRGB then premultiply by interpolated alpha.** Color-management owns the fixed conversion recipes below. There is no existing OKLab conversion in the inspected production modules. Pin the [author's updated 2021-01-25 OKLab matrices](https://bottosson.github.io/posts/oklab/) as F32 constants under `oklab-srgb-2021-v1`; recipe/source identities include that version. Do not clamp signed Lab intermediate channels or bake dynamic stops into a pipeline key.

```text
straightDomainStop = ConvertSrgbStraightToDomain(stopColor)
v = (1-t)*leftDomainStop + t*rightDomainStop
linearRGB = ConvertDomainToLinearSrgb(v.rgb)
source = (linearRGB * v.a,v.a)
```

```text
// Linear sRGB -> OKLab; each decimal constant is rounded to F32 once.
l = .4122214708*r + .5363325363*g + .0514459929*b
m = .2119034982*r + .6806995451*g + .1073969566*b
s = .0883024619*r + .2817188376*g + .6299787005*b
lp = cbrt(l); mp = cbrt(m); sp = cbrt(s)
L = .2104542553*lp + .7936177850*mp - .0040720468*sp
a = 1.9779984951*lp - 2.4285922050*mp + .4505937099*sp
b = .0259040371*lp + .7827717662*mp - .8086757660*sp
// OKLab -> linear sRGB; cube is two multiplies, including negative values.
lp = L + .3963377774*a + .2158037573*b
mp = L - .1055613458*a - .0638541728*b
sp = L - .0894841775*a - 1.2914855480*b
l = (lp*lp)*lp; m = (mp*mp)*mp; s = (sp*sp)*sp
r = 4.0767416621*l - 3.3077115913*m + .2309699292*s
g = -1.2684380046*l + 2.6097574011*m - .3413193965*s
b = -.0041960863*l - .7034186147*m + 1.7076147010*s
```

Stop preparation is an explicit handle-free preflight stage, not a host-rendered source/cache: the color-management recipe is interpreted once to produce the exact F32 tuples bound in the shared slab, only after stop-count/stride/frame/device limits are checked. Basic host operations use ordered F32 products/additions (no FMA); transcendental host operations use [StrictMath's specified JVM semantics](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/StrictMath.html) on promoted F64 operands followed by F32 rounding. Signed cbrt uses `StrictMath.cbrt`, never pow on a negative input. The independent oracle encloses these preparation roundings and the documented StrictMath accuracy, then the actual WGSL interpolation/inverse schedule. The numeric authority authenticates original stop bits, recipe identity, prepared tuple bits and range/domain together. Forward cbrt/atan2 for stop preparation is not emitted into the fragment shader; inverse OKLab uses multiply/cube nodes, inverse OKLCH uses bounded sin/cos nodes. This proposed schedule must pass the Astra plan review before execution; if it cannot meet the approved envelope, stop for a revised design rather than silently switching evaluators.

Extend the stop slab/range interning and proof rebasing once; preserve the32-byte record and one shared native buffer. Distinct conversion domains must participate in semantic range/source identities, not create one storage buffer per domain. The changed stop interpretation has a V4 contract; V1/V2 exact endpoint ABI stays valid.

Preserve §7.2 degeneracy priority and its exact average in ORIGINAL straight sRGB before requested interpolation, converted once to linear-premul. A REPEAT/MIRROR degenerate average is not computed in OKLab/HSL/OKLCH. RRect/stroke lowerers and witnesses accept the exact unfiltered interpolation authority from the planner but still refuse a filtered H-lane source before ownership; no V1/V2 reconstruction or coordinate loss.
- [ ] **Step 4: Fresh interpolation/filter/order and affected W5c/d mixed-domain >16stop regressions; static audit all transport/rebase/domain identities.** Numeric validity covers the actual converted signed stop tuples and final blend domain, not original unit sRGB alone.
- [ ] **Step 5: Commit `feat(gpu): add W5f linear and Oklab interpolation` after Sol review.**

### Task 6: Deliver HSL/OKLCH hue semantics and WithWorkingColorSpace

**Files:**
- Modify: `color-management/src/main/kotlin/org/graphiks/kanvas/color/ColorInterpolationProgramV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientInterpolationPlanV4.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/GradientAddressingCaptureV2.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialSourceFootprintV4.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5dGradientCandidateV2.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fGradientInterpolationSurfacePixelTest.kt`

**Interfaces:** Produces all five domains plus semantic selection by `Shader.WithWorkingColorSpace(shader,interpolation)`. Proposed explicit precedence: the outermost applicable working-space wrapper wins for descendant gradient leaves; a leaf's own domain applies only when no wrapper selects one. Traverse the existing unary filter/opacity/local-matrix/CoordClamp chain without relocating any of those operations. `kanvas/.../surface/gpu/GPUMaterialMapper.kt` currently maps inner-to-outer, then overwrites Linear/Radial/Sweep facts with the outer wrapper; preserve that observable precedence and extend the formerly incomplete Conical/filter-wrapped cases consistently, rather than preserving passthrough bugs. No arbitrary W5g blend-child traversal. Non-gradient sources remain linear-premul; the wrapper does not apply a transfer twice, change image ColorSpace, or invent a working gamut. This clarification is explicitly submitted to Astra against the approved spec; any conflicting public contract requires user adjudication before implementation.

- [ ] **Step 1: Add HSL/OKLCH public positives for each Linear/Radial/Sweep/Conical family on Rect/RRect/Path fill/Path stroke, with real hue wrap/180°tie/achromatic/signed-domain witnesses and wrapper-versus-leaf selection.** Include HSL350→10°, explicit180°tie using the specified positive tie, one/two achromatic endpoints, nontrivial alpha, OKLCH white/gray→chromatic endpoints and two distinct grays, two ordered working-space wrappers and a surrounding color filter/local matrix on Rect/Path fill only. Compare direct domain requests with their semantically equivalent wrapper; expected pixels are computed before render.

```kotlin
val wrapped = Shader.WithWorkingColorSpace(
    Shader.WithLocalMatrix(shader,Matrix3x3F32.translation(1f,0f)),
    ColorSpaceInterpolation.OKLCH)
val filtered = Shader.WithColorFilter(wrapped,ColorFilter.HighContrast)
```

- [ ] **Step 2: Run new HSL/OKLCH/wrapper methods before production; retain true RED/PASS distinctions.** A nearest-hue test at identical RGB endpoints is not discriminating; use explicit different hues and boundary midpoint coordinates.
- [ ] **Step 3: Implement the §7.3 shared hue rule and true numeric topology.** Domain HSL hue may be stored in turns internally; convert consistently to the semantic degree rule. OKLCH uses L/sqrt(a²+b²)/atan2(b,a) and inverse sin/cos; guard chroma0 before undefined atan2, and prove the actual lazy branch. Hue delta uses shortest path with -180→+180 tie; one achromatic endpoint borrows hue, two use0; no intermediate gamut clamp.

Version the semantic stop-preparation branch as `polar-achromatic-original-srgb-v1`. The classification is exact equality of ORIGINAL encoded R8/G8/B8, not rounded Lab/C equality or an epsilon. It belongs in the interpreted recipe and independent oracle before preparation. For original gray, HSL prepares H=0/S=0/L=encodedGrayF32; OKLCH prepares L from the ordinary OKLab recipe, C=0/H=0. Ordinary OKLAB is unchanged. The authority authenticates original bits, classification, recipe/domain and prepared tuples together; WGSL borrows hue using exact S==0/C==0 on the bound tuples. This transmits classification through the existing32-byte tuples with all reserved fields zero, no extra slab or stop-count-dependent pipeline. A pinned F32 conversion of white otherwise produces C=5.960464477539063e-8/hue90°, so computed-C==0 alone is not lawful achromatism.

```text
achromatic = originalR8 == originalG8 && originalG8 == originalB8
HSL: if achromatic then (0,0,encodedGrayF32,alphaF32) else RGB_TO_HSL(srgbStraightF32)
OKLCH:
    lab = LINEAR_RGB_TO_OKLAB(EOTF(srgbStraightF32))
    if achromatic then (lab.L,0,0,alphaF32) else OKLAB_TO_OKLCH(lab)
```

```text
d = ((h1-h0+540) mod360)-180
if d == -180 then d = +180
h = (h0+t*d) mod360
```

Preserve wrapper order and coordinate owner, domain-tagged ranges and conversion structural IDs. Numeric ambiguous hue branches need a lawful reformulation/exact semantic branch, not broad output tolerance or nominal mode refusal.
- [ ] **Step 4: Run the entire interpolation class and all filter/order gates plus W5c/d affected cases; independent Sol audit of wrapper precedence and signed transcendental envelopes.**
- [ ] **Step 5: Commit `feat(gpu): complete W5f hue interpolation domains`.**

### Task 7: Integrate filters with decoded RGBA/A8, Nine/Lattice/Atlas and mixed frames

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageNumericOperationGraphV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageAtlasBlendNumericAuthorityV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorNumericAuthorityV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ColorSourceProofCompilerV1.kt`, `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialSourceFootprintV4.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5eImageTexelEvaluatorV1.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5fColorOperationEmitterV1.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5eImagePlanLowerer.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5ePreparedFrameWitnessV1.kt`, `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fColorCpuOracle.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fSurfacePixelFixtures.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fImageFilterSurfacePixelTest.kt`

**Interfaces:** Consumes Task1–6 filtered/interpolated source authority and original W5e upload/coordinate/cell/child/Atlas plans. Produces V4 image roots with exact underlying V3 ownership, all twelve external filters and internal ImageShader filters, filtered A8 child material including new interpolation, and frame-wide material/stops/payload accounting. No second sampler/format/alpha/cell classifier or upload identity change.

- [ ] **Step 1: Public RED witnesses for every decoded image family plus Rect/Path image shader.** Include RGBA/BGRA/SRGBA, PREMUL/UNPREMUL/OPAQUE, typed translucent attachment snapshots, padded rows, Nearest/Linear/Cubic and A8 mask with solid/gradient/filtered paint shader. Distinguish filter-after-mask from filter-before-mask by alpha translation and nonunit mask. For Atlas distinguish external-filter-after-entryBlend from before-entryBlend and after-finalDrawBlend; retain original entry order/count. For each of12filters one image positive includes nontrivial alpha and an independent bounded expectation.

```text
RGBA source = paintAlpha * sampleDecodedImage
A8 source = sampledMask * (paintAlpha * evalPaintShaderOrPaintColor)
Atlas source = entryBlend(source,entryColor)  // exact existing operation authority
filteredSource = externalPaintFilter(source)
output = destination + coverage*(finalBlend(destination,filteredSource)-destination)
```

- [ ] **Step 2: Run new image-family/filter methods before gate/compiler changes; inspect actual RED.** An excluded GM producer/stroke/AA4 failure is not an image-filter arithmetic RED. Do not run or modify integration-tests/skia/provider fixtures.
- [ ] **Step 3: Move the existing gate and image compiler readers atomically with V4 image source transport.** Authenticate the original DrawNode plus consumed mirror, preserve original image child source identity/range after interning, and carry V4 source through Rect/Path/General and ordered family entries. A8 mask is scalar, color-converted child once; external filter runs only after masking and Atlas composition. Retain V3 decoder, raw UNORM texture key, typed attachment premultiplication and completion-held leases; append only declared V4 dynamic bindings/proof.

The source certificate derives joint sampled RGB/alpha expressions from actual decoded texel nodes, tap indices/weights and their existing numeric authority; it does not replace that sampler or invent an alpha floor for a weighted result. A8 composes the child's authenticated source proof with the scalar mask, then Atlas composes the exact source blend and paint-alpha ordering. Filter output proof feeds final blend/coverage. All image/mixed-frame builders measure source+filter footprints and actual shared stop inventory, obtain the packing permit, then pack; an old eager Raw.of on V4 is a terminal contract refusal, not a shadow planner.
- [ ] **Step 4: Run all new image filters plus complete W5e public classes and affected W5a/d mixed-frame cases.** The mixed frame contains filtered Rect, Path fill, decoded image, A8 and Atlas with different program values; repeat render and a subsequent Surface on the same runtime. Require Render/Readback before pixels/counts. Native failure terminality/allocation ordering remain static-review evidence, not injected tests.
- [ ] **Step 5: Commit `feat(gpu): share W5f filters across decoded image lanes` after Sol review.**

### Task 8: Close mutation, budgets, compatibility, ownership and full W5f reviews

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`, `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/PaintSceneAdapter.kt` only where a real capture defect is demonstrated
- Modify: relevant V4 table/compiler/numeric/binding/source/witness code above only for actionable closure findings
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5fConvergenceSurfacePixelTest.kt`, `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W5fPictureFilterInterpolationTest.kt`
- Modify: `refactor/plans/2026-09-14-w5f-color-filters-implementation-plan.md`, `refactor/waves/W05-material-graph/status.md`, `refactor/README.md`

**Interfaces:** Consumes completed twelve-filter/five-domain/V4 image lanes. Produces honest public mutation/Picture/budget/recovery evidence, one authority after admission and durable functional/command/integration closure; no unproved native failure/close/cache-hit claims.

- [ ] **Step 1: Add public convergence witnesses before cleanup.** Mutate ColorMatrixF32 via setRowMajor, Table/HSLA arrays, source pixels, stops and alpha after Surface/Picture recording; retained output is independent of mutation. Picture10/schema4 serialize/deserialize/playback must preserve the same ordered filtered graph and interpolation without private wire assertions or version bump. Build a legitimate filter chain whose actual dynamic allocation alone crosses the configured frame budget, plus a matching control and same-runtime recovery. Capture graph-limit/invalid t/table/coefficients refuse precisely and do not poison a later valid capture on the same Surface.

```kotlin
val matrix = ColorMatrixF32.ofIdentity()
val filter = ColorFilter.Matrix(matrix)
val surface = Surface(1,1)
surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),
    Paint(color=ColorARGB.Red,colorFilter=filter,antiAlias=false)) }
matrix.setScale(0f,0f,1f,1f)
// Expected retained red is declared/bounded before mutation and render.
```

- [ ] **Step 2: Run convergence/Picture methods before semantic cleanup and identify actual shadows/refusals.** If already PASS, record proof honestly; do not invent a fake RED or test private counters.
- [ ] **Step 3: Audit the actual last semantic/ownership boundary.** All admitted filter/interpolation sources consume one V4 sealed authority through draw/source/witness/numeric/native stages. No child/transparent/old materializer continuation after capture/plan/budget/ABI/allocation/submit/readback failure. Preserve legacy semantics only before admission for W5g/h/W6/excluded content; do not rename it physical-only or delete unpromoted behavior. Check old V1/V2/V3 source correctness, one domain-aware stop slab, Table/matrix binding budget before copies/handles, source/coordinate/frame identities, actual SceneTarget session keys, journal ownership, completion leases, rollback/quarantine and generation invalidation. No pipeline-cache measurement is inferred from pixels.
- [ ] **Step 4: Run one forced complete planned public covering and standalone compile, then inspect fresh XML and process status.**

```bash
rtk ./gradlew :render-ir:compileKotlin :color-management:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5f*' --tests '*W5e*' --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
```

Report actual method/PASS/skip/assertion failure/error counts and UTC timestamps; keep synthetic Gradle worker failure separate. Native133/Gradle1 is BUILD FAILED, not GREEN. Two AA4 skips keep their exact `w4d.general.texture-sample-support-unavailable` code; no additional unavailable cell becomes silently skipped.
- [ ] **Step 5: Close task Sol review, then one independent whole-W5f Sol review against this plan and global spec.** Supply exact branch merge-base/HEAD review package, all parked/minor rulings and full scope. If wholebranch findings exist, one adapted Astra fix worker receives the COMPLETE findings list, one complete fix wave, exactly one scoped Sol re-review. No per-finding workers or second final loop. Adjudicate residual load-bearing findings explicitly and stop for the user rather than quietly closing an unbounded mandatory cell.
- [ ] **Step 6: Update only the three durable docs with actual delivered cells, independent verdicts and execution/integration limits; commit closure.** Ordinary current Picture10/schema4 and historical8/9 remain valid; no infrastructure archive-count/source assertions. W5g/h, W6–8, native133/AA4/Pictureclip/close/target-recovery and any newly evidenced gap remain named with precise diagnostics.
- [ ] **Step 7: Normal push and create/update the one stacked W5f PR targeting W5e.** Describe all eight deliveries, public evidence and limits, not only final fixes. If commands remain Gradle1/native133 or integration limits persist, publish Draft only, never ready-to-merge/globalISO and do not merge. Preserve app-owned worktree. Collect ALL chronological rulings+costs before recoverable cleanup of ONLY this ignored plan workspace; preserve siblings and durable Git documents.

## Completion gate and review checkpoints

### Assigned public witness matrix

Each cell below is a parameterized public pixel witness, not a private-plan test. Fixtures establish bounded expected channel sets before Surface creation/render, then require Render/Readback and compare pixels. Each filter cell includes alpha0/alpha1/nonunit, a clamp-needed nontrivial source and noncommutative Compose, plus a colored translucent destination with nontrivial final draw `DIFFERENCE` or another independently bounded non-SRC_OVER mode. Mutation changes the applicable caller-owned Matrix/Table/HSLA payload. For immutable/parameterless Rect/Path filters in Tasks2–4, mutate a ColorMatrixF32 in an ordered Compose already admitted in Task2, before or after the tested filter according to a discriminating expression; the oracle proves this mutation would change output without snapshot (use an alpha mutation before Overdraw). No early task depends on future gradient/image certificates. Gradient-stop/image-pixel mutations belong to Tasks5–7 respectively. Repeated renders verify retained output. Lerp additionally covers t0/t1/.5; source/filter `DST` is never substituted for final draw `DST`.

| Filter | Rect public class/task | Path-fill public class/task | Image public class/task |
| --- | --- | --- | --- |
| Matrix | W5fColorFilterSurfacePixelTest /1 | W5fFilterOrderingSurfacePixelTest /2 | W5fImageFilterSurfacePixelTest /7 |
| Compose,Lerp | W5fFilterOrderingSurfacePixelTest /2 | W5fFilterOrderingSurfacePixelTest /2 | W5fImageFilterSurfacePixelTest /7 |
| Table,Lighting,SRGBToLinear,LinearToSRGB | W5fColorFilterSurfacePixelTest /3 | W5fColorFilterSurfacePixelTest /3 | W5fImageFilterSurfacePixelTest /7 |
| Blend: all29 existing modes | W5fColorFilterSurfacePixelTest /3 | W5fColorFilterSurfacePixelTest /3 | W5fImageFilterSurfacePixelTest /7 |
| HSLAMatrix,HighContrast,Luma,Overdraw | W5fColorFilterSurfacePixelTest /4 | W5fColorFilterSurfacePixelTest /4 | W5fImageFilterSurfacePixelTest /7 |

`W5fColorFilterSurfacePixelTest` has separately selectable methods `matrixRectAlphaMutationAndFinalBlend`, `tableRectPathAlphaMutationAndFinalBlend`, `lightingRectPathAlphaMutationAndFinalBlend`, `transferRectPathAlphaMutationAndFinalBlend`, `blendModesRectPathAlphaMutationAndFinalBlend`, `presetsRectPathAlphaMutationAndFinalBlend`. Path variants include established direct and stencil fill fixtures, with General/destination-read represented in Task2. `W5fImageFilterSurfacePixelTest.allKindsImageAlphaMutationAndFinalBlend` covers every kind and all29 Blend modes on DrawImage; its other methods cover each Nine/Lattice/Atlas/A8/ImageShader origin and order. Parameterization may share setup; no required kind/mode is closed by another parameter or only a refusal.

Table selection witnesses prepare an input channel with Matrix translation around `(128+.5)/255`: the predecessor F32, nearest F32 and successor F32, with adjacent mapped bytes128/129 so the independently derived union still quantifies to one/two adjacent output codes. Other channels/alpha use stable endpoint records. Also include interior indices away from half-integers with a nonidentity Table. The oracle enumerates every admissible rounded index and actual table byte; it neither assumes a fixed tie direction nor drops a reachable integer. Genuine alpha0 and alpha1 branches use actual source states before filter, alongside nonunit alpha; do not obtain them by merely clamping the final attachment.

| Interpolation domain | Families and applicable lanes | Assigned public witnesses |
| --- | --- | --- |
| SRGB | Linear/Radial/Sweep/Conical × Rect/RRect/Path-fill/Path-stroke | Existing W5c/d positives/regressions; Task5/6 new V4 filtered Rect/Path/mixed-domain cases |
| LINEAR,OKLAB | Linear/Radial/Sweep/Conical × Rect/RRect/Path-fill/Path-stroke | W5fGradientInterpolationSurfacePixelTest `linearFamiliesLanesAlphaMutationAndFinalBlend`, `oklabFamiliesLanesAlphaMutationAndFinalBlend` /5 |
| HSL,OKLCH | Linear/Radial/Sweep/Conical × Rect/RRect/Path-fill/Path-stroke | W5fGradientInterpolationSurfacePixelTest `hslFamiliesLanesAlphaMutationAndFinalBlend`, `oklchFamiliesLanesAlphaMutationAndFinalBlend` /6 |
| All five domains in A8 paint child | Existing DrawImage/Nine/Lattice/Atlas/Rect/Path-fill mask-source paths | W5fImageFilterSurfacePixelTest `maskChildDomainsAndFilterOrder` /7 |

Every new-domain family/lane cell includes nontrivial alpha, post-record mutable stop mutation, colored destination/nontrivial final blend and an independent bounded discriminator from SRGB. Task5 adds mixed32-byte ranges and1/2/16/>16stops; Task6 adds wrapper equivalence/outer precedence, exact gray classification, hue seam/180°tie/one/two achromatic endpoints. Existing W4 geometry is reused, not repaired to rescue an invalid fixture. Task8 aggregates mutation/Picture/budget/recovery evidence; it does not postpone missing mandatory filter/gradient positives from their delivery task.

W5f closes functionally only if all twelve existing non-runtime filters and all five interpolation domains have mandatory bounded public positive evidence, with Rect/Path-fill/image cells covered, exact ordering/alpha/conversion/compose/working-space semantics, immutable capture/Picture replay, checked bindings/resources and one authority after admission. Wider/unbounded mandatory oracle results, nominal unsupported-filter/domain refusals, dropped mirrors/child identities, legacy continuation, or private tests do not close the gate. Known precise physical capability refusals remain explicit and cannot be presented as positive AA4 evidence.

Each task includes public RED or honestly recorded existing proof, production, fresh affected assertion pass, independent Sol spec+quality verdict, then commit. Progress records command failures honestly. The final wholebranch review follows all eight tasks; one complete final fix wave and one scoped re-review are the cap. W5f does not claim W5 complete, Skia GM convergence, green native teardown or a globally retired legacy.

## Plan self-review coverage matrix

| Normative requirement | Delivery |
| --- | --- |
| §§4–5 authority, versioned witness, structure/dynamic separation | 1,2,7,8 |
| §5.3 paint/internal filters, mirror, alpha/origin/filter/blend/coverage order | 1,2,7 |
| §5.4 graph limits, metadata before copies, checked binding/device/frame budgets | 1,3,5,7,8 |
| §5.5 shared stops, image leases, bounded generation caches, lifetime | 5,7,8 |
| §5.6 true rounded operation graph, independent bounded public oracle | 1–8 |
| §6 color/alpha, working interpolation not gamut | 1,3–7 |
| §§7.3–7.4 five domains, hue rules, domain-tagged32-byte frame slab | 5,6,7 |
| §11.1 all12non-runtime filters, validation, composition, presets | 1–4,7,8 |
| §15 public pixels/mutation/transactional failure and no infrastructure | 1–8 |
| §16 stacked PR and §16.1 mandatory Rect/Path-fill/image matrix | 1,2,7,8 |
| W5g/h, W6, fonts/codecs/GM exclusions and known non-green native debt | Global constraints,8 |

## Independent Astra plan review and rulings

One independent full Astra plan review returned `With fixes`: zero Critical, five Important, no Minor. All five are adjudicated as requirements already approved by W5, not new product choices:

1. Relational/authenticated source input: replace caller-supplied four ranges with issued ColorSourceProofV1 and composed output certificates; shared issuer extensions assigned to Solid/Opacity, ordered filters, gradient domains and decoded/A8/Atlas source graphs. Actual V4 footprint/packing permit is explicit, preserving existing source arithmetic/owners.
2. Exact polar achromatism: retain original integer-channel equality; versioned HSL/OKLCH preparation creates semantic zero S/C and hue0 before interpolation, keeping L/alpha. Astra's bounded clarification confirms this respects §7.3 and unchanged32-byte reserved-zero ABI, no epsilon/new decision.
3. First-snapshot metadata: assigned shared preflight plus public typed recording diagnostic to Tasks1/2/3/4 before payload copies; Task8 verifies already-delivered failure recovery.
4. Complete V4 reader transport: named PlanPasses/RenderGraph, nonexhaustive casts, canonical seals, source/witness reconstruction and all Rect/Path/General/RRect/stroke consumers. Unpromoted H readers explicitly reject, promoted readers retain exact incoming root/coordinate ownership.
5. Mandatory coverage: exact filter×Rect/Path/image and interpolation×family×applicable-lane table; destination/blend/coverage oracle signatures, all29 Blend modes, discrete Table boundaries and real alpha0/1 branches are assigned, not left as global aspirations.

Scoped Astra confirmation marked findings1–4 ADDRESSED and finding5 PARTIAL only because the first coverage wording depended on future stop/image certificates. Its sole recommended local correction is applied above: Tasks2–4 mutate an already-admitted Compose Matrix, with an output discriminator and Overdraw alpha witness; stop/image mutations stay in Tasks5–7. Astra explicitly requires no additional whole review or product adjudication once that wording is fixed. This closes the review's five plan findings without another review loop; it is not a production verdict.

Planning verdict: ready after full Astra review, targeted confirmation and its verified local sequencing correction. Planning records: one independent full review, one bounded achromatic clarification, one scoped confirmation; no helper agents or Gradle/native tests during planning. The approved W5 scope and sequential SDD workflow remain unchanged.

## Execution tracking

| Task | Delivery | State |
| --- | --- | --- |
| 1 | Matrix/Solid/Opacity Rect, sealed V4 source/filter/bindings and exact packed handoff | CLOSED within this slice; Sol spec compliant / quality Approved, scoped numeric-documentation fix ADDRESSED |
| 2 | Compose/Lerp/order, Path/General and construction-first composite publication | CLOSED231eae9809f7c535eaf75ec84153215a4f0f24c9; Sol Approved/no Critical/Important; sole scoped R14 reconciliation02c63c14d ADDRESSED/spec Compliant/quality Approved |
| 3 | Table/Lighting/transfers/all29 Blend filters | Next sequential Astra task; actual Task2 source/permit contracts retained |
| 4 | HSLA/HighContrast/Luma/Overdraw | Pending |
| 5 | LINEAR/OKLAB gradient interpolation | Pending |
| 6 | HSL/OKLCH/working-space precedence | Pending |
| 7 | Filters across decoded/A8/Atlas image lanes | Pending |
| 8 | Public convergence/Picture/budgets and full bounded branch reviews/stacked Draft PR | Pending |

Task1 real public RED precedes implementation efe36e95eb73a5cd2e43fabb35375b01d31b4629; independent Sol approves spec and quality, no Critical/Important. Numerical-documentation correction b535d3f554163ed9f013070b17d446ebc5e9122e has its sole scoped Sol confirmation ADDRESSED/Approved. Forced public gate: six Matrix plus two selected W5a, all pass/no skip/failure/error; Gradle1/worker8native133 BUILD FAILED. Forced five-module compile including color-management: exit0; controller's fresh post-documentation check also exit0 BUILD SUCCESSFUL1s,6executed/32up-to-date. Details are in [W05 status](../waves/W05-material-graph/status.md).

Task2 actual implementation231eae9809f7c535eaf75ec84153215a4f0f24c9 consumes unpublished immutable construction, final interning/proofs, real whole-frame inventory/permit, then V4 packing and Ready for ordinary/native composites; the temporary Task1 mixed-V4 restriction is removed with its readers. Sol verifies the R11/R13 architecture and R12 nonunit Paint-alpha/restoring-filter witness, quality Approved/no Critical/Important. Its initial spec verdict Not compliant solely for nine formal Modify-without-hunk paths remains recorded; separate R14 audit/reuse reconciliation02c63c14d preserves all obligations, and sole scoped Sol confirmation marks it ADDRESSED/spec Compliant/quality Approved/no new defect. Task2 is CLOSED within its slice. Fresh affected gate27assertionsPASS/no skip/failure/error, Gradle1/executor27native133 BUILDFAILED37s; separate5module compileexit0/914ms and rootfresh compileexit0/1s, not forcedclean. Historical prepared/W4e topologies and shared oracle arithmetic are not comprehensively reaudited. Preexisting warnings remain visible, not pristine. Task3 is next; R9's Task1-delivered recipe provider/dependency are classified as actual reuse before dispatch, not new/fake creation. No full W5f integration, command GREEN or W5f PR is claimed. Full operational evidence currently lives in the ignored plan-specific SDD scope; chronological rulings/costs below must be completed before its eventual recoverable cleanup. No cleanup or global ISO claim yet.

### Chronological controller rulings and costs

These are binding execution adjudications, not additional product scope. The table is current through R14; later rulings must be appended, never silently replace earlier decisions. Outstanding Task2 requirements are not closed by recording them here.

| Ruling | Decision / disposition | Cost if wrong |
| --- | --- | --- |
| R1 | Implement only exercised shared contracts: paint/shader oracle and Solid/Opacity/Matrix in1; gradient/image oracle with real consumers in5/7. Preserve final signatures, no fake future certificate. | A later consumer may need a small interface adjustment; no permission to weaken the final mandatory contract. |
| R2 | Local provisional implementation commit precedes exact BASE→HEAD task review; fixes are separate commits and task closes only after clean review. No publication of unreviewed work. | Unreviewed provisional history temporarily exists locally; no parent/shared branch mutation. |
| R3 | Fresh affected baseline and command status separately from assertions/compile. Native133 remains FAILED, without reset/dispose/teardown workaround. | Native execution can remain unsuitable for merge; PR stays Draft and command evidence non-green. |
| R4 | Task1 issues typed V4 only for implemented Solid/Opacity/Matrix Rect. Other lanes explicitly refuse before admission; no child/transparent/bbox substitution. | Later readers require extensions; missing required Rect positives cannot be parked. |
| R5 | Actual rounded source/filter graph, conditioned joint provenance and genuine lazy guards; measured frame/device permit before V4 payload copies/packing/native allocations. | Invalid portable numeric or budget claims require correction before task closure. |
| R6 | Reuse test-only interval/attachment arithmetic through independent color/blend input entry; never use legacy MaterialPlanTable source evaluation or production conversions/filters. | Hidden production-semantic dependency invalidates independent evidence and must be reviewed/fixed. |
| R7 | Task1 initially may use bounded SRC over colored translucent destination with an alpha discriminator; preserve actual alpha0/1/nonunit and Task2 General/destination-read. Task1 later also delivers genuine DIFFERENCE/fractional Rect. | Initial interaction witness is narrower than DIFFERENCE; stronger Rect evidence does not replace mandatory Task2 General coverage. |
| R8 | Immutable packed V4 lookup belongs to RenderGraph, keyed by complete source/proof/coordinate footprint; all issuers/copies/seals authenticate final interned ownership, Raw.of refuses V4. | Missing propagation breaks readers; stale/forged identities violate budgets. Related plumbing is mandatory, not a mutable-cache/late-permit workaround. |
| R9 | Bring planned ColorInterpolationProgramV1 and one-way gpu-plan→color-management dependency from3 into1 for true EOTF/OETF; future tasks extend it in place. No duplicate recipe provider or fake future proof. | Early API shape constrains later conversions; later tasks must preserve the reviewed immutable interface and constant bits. |
| R10 | Add only readonly sizeI32/indexed get to ImmutableFloats for metadata validation before copies. Preserve private storage/immutability/canonical identity/schema; public Matrix witnesses only. | Additive public API must remain stable; no backing-array exposure or unrelated payload APIs. |
| R11 | Temporarily restrict Task1 V4 to single-family Rect. Task2 MUST implement unpublished lane construction→final interning/proofs→actual shared inventory→frame/device permit→V4 pack→Ready for both composites, then remove restriction atomically. | Temporary pre-admission compatibility persists until2; broader Task2 is necessary. This gap cannot be parked at W5f closure. |
| R12 | Scoped Task1 Sol Approved/no Critical/Important. Fix numeric-certificate documentation now, with sole scoped confirmation (ADDRESSED/Approved); assign nonunit Paint-alpha restoration witness to2; retain old warnings explicitly. | Misleading proof documentation hides future errors; deferred order evidence must close in2; warnings keep output non-pristine. Task1 does not validate later source/composite proofs or unchanged oracle arithmetic. |
| R13 | Authorize necessary internal immutable graph-construction extraction and construct/compiler/sealer/validator/witness signatures for R11. Preserve public wrappers, one genuine validator, old rules/refusals/ABI and final-table reissued certificates; no RenderGraph flag or partial Ready. | Larger mechanical extraction can regress old validation/ownership. Per-file reasons, focused old public gates and Sol review must verify compatibility; new geometry/ABI/ownership strategy needs explicit ruling. |
| R14 | Reconcile only nine Task2 Modify-without-hunk paths as audit/reuse after Sol's targeted existing-contract/new-consumer checks. Retain every functional obligation, original formal finding and verification limits; no production/test changes or artificial hunks. Sole scoped confirmation is required before Task2 closure. | A hidden historical contract deficiency can remain; exact join checks/public27PASS do not comprehensively validate all prepared/W4e topologies or shared oracle arithmetic. Any actual missing functionality still requires a fix, with no new promotion/API/lifecycle scope. |
