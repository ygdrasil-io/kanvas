# W5g Composed and Procedural Materials Implementation Plan

## Current execution checkpoint — Task1 scalar accepted; Task2 gradients next

Task1 uniform-only ordered Blend is functionally accepted after its sole Sol task review and SAME-seat scoped fix round1 re-review: Spec Compliance COMPLIANT, Task quality APPROVED,0Critical/0Important/0new actionable Minor. I2's all29/all12 witness gap and ROOT-confirmed C1's historical invalid-gradient diagnostic masking are ADDRESSED. The unsupported alpha127-and255 matrix claim was withdrawn by the reviewer; alpha127/255 is a normalized rational.

Task1 production commits are `a9e167e3832bedf5e0453dfd3dbcfde23af83010` and bounded fix `bb0bffaa9906b20f1cb886de12c43908db19da89`, from recorded BASE `0b533d31e8f730c2837d585cf04660147de5e882`. ROOT FULL-read reports/review and independently verified all42committed code blobs equal the frozen tested source. The fix changes exactly2files182+/11−, no native/image/Picture/oracle change. Final amended-source covering has98PASS (Surface95+Picture3),0failure/error/skip, fresh XML17:46:01.074Z–17:54:11.598Z. The genuine initial scalar RED and genuine C1RED, all failed fixture/search epochs and historical six-class covering206registered/205PASS/1unchangedW5aAA4skip remain recorded, not relabeled final.

Command remains BUILD FAILED8m14s/Gradle1/native executor195exit133. Repeated exit133 is observed; cause and causal identity with earlier executors remain unknown. No native GREEN/Ready-to-merge/ISO claim. The native-cause investigation remains an explicit outside-slice gap, not silently closed by disclosure. Compressed ComposedMaterialPlanV5 formatting remains a nonblocking readability observation for an in-scope later edit/whole-branch triage.

Next is Task2's SAME V5 DAG extension to contextual gradients/shared stops, with one fresh adapted Astra implementer and one serialized Gradle writer. Full Blend closes only after Tasks2–3 acceptance; Noise255/archive/storage and wholeW5g gates remain OPEN. Parent W5f#2400 is unchanged, no W5g push/PR yet.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task with the session's adapted Astra implementers and Sol review-only allocation. Steps use checkbox (`- [ ]`) syntax for tracking. ROOT first reads this entire plan and the approved spec, performs the self-review, and obtains the requested fresh Astra plan review. This document does not authorize implementation before that gate.

**Goal:** Render ordered blend children and shared material subtrees, then Perlin/Fractal `NoiseV1`, through the existing common material authority with public Rect and Path-fill pixel evidence.

**Architecture:** Extend the existing metadata-first source construction, whole-frame layout, operation graph, proof and packing permit. A composed source adds explicit ordered child references to the existing material plan, with per-evaluation coordinate context and immutable resource owners; it does not introduce another material hierarchy or materializer. The renderer emits the exact sealed operation graph and consumes only its declared bindings.

**Tech Stack:** Kotlin/JVM; existing math geometry/matrix/color modules; render-ir, gpu-plan, gpu-renderer and kanvas; WebGPU/WGSL; JUnit5; independent outward-rounded public-input CPU oracles.

**Spec:** `refactor/specs/2026-09-09-w5-material-graph-design.md`, read completely, especially §§4–6,10–11,15–18. The current final checkpoint at the top of `refactor/plans/2026-09-14-w5f-color-filters-implementation-plan.md` is the inherited state; older chronological OPEN paragraphs are history.

## Global constraints and execution boundary

- Base: W5f final HEAD `55e4992d5aeb34412189d3bb52bcf2784468318c`. ROOT created `codex/w5g-composed-procedural-materials` at that unchanged commit in the existing app-owned worktree. One eventual Draft PR targets `codex/w5f-color-filters`, parent PR #2400. Do not mutate or merge the parent.
- W5f functional slices/reviews are CLOSED: 0 Critical, 0 Important, 2 inherited nonblocking Minor. Final covering was 14 classes, 690 methods, 688 PASS, 2 known AA4 skips, 0 assertion failures/errors; the command remained FAILED, Gradle exit1/native174 exit133. Five separate incremental compiles exited0. Preserve that distinction, all existing numerical restrictions, V3 pre-inventory-copy debt, owner/lifecycle gaps and warnings. No native/global ISO claim.
- `GraphLimits(maxDepth=64,maxNodes=4096)` remains exact. Preserve first-owner refusal priority and existing occurrence-based capture accounting; do not silently turn graph-node limits into unique-object limits while adding memoization.
- Only real public `Surface`, `Canvas`, `Picture`, render pixels, typed diagnostic boundaries, and native Render/Readback evidence are tests. No infrastructure, source-shape, private/internal, ABI, reflection, counter, cache-handle, injected-capability, fake-device, mock or structural-step assertions. Static architecture/ownership checks belong to review.
- Compute independent expected byte sets before creating a Surface or recorder. A set is a singleton or exactly two adjacent bytes justified by `WgslFloatEnvelopeV1`; wider/unbounded/nonadjacent sets are invalid fixtures. Never use similarity or an empirical tolerance.
- Every promoted family covers Rect and Path fill, including existing direct and stencil Path-fill routes where applicable. RRect, Path stroke, Point(s), Text, Vertices/Mesh and A8-image-origin composed/noise sources remain explicitly named H cells for W5h. RGBA image origins do not evaluate the paint shader. A composition containing an image shader on a Rect/Path is in scope; that is distinct from promoting A8 image origins.
- Retain all previous admitted W5a–f cases and their exact diagnostics. An H source cannot become an admitted unfiltered gradient by looking through Blend, and an ordinary previously admitted gradient cannot become refused merely because V5 exists.
- Internal order is material children/wrappers, paint alpha, origin composition, external paint color filter once, final draw blend, then geometry*clip coverage. A child blend is source computation and never requests a target snapshot by itself. Final destination-read still uses the existing versioned target ordering.
- Preserve wrapper-local coordinates and outermost working-interpolation precedence independently along each child edge. Do not multiply paint alpha once per child or move filters across a blend/opacity.
- Geometry and transformation values remain in `:math:geometry`/`:math:matrix` using I32/I64/F32/F64 names. `SizeI32` already exists in `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/SizeF32.kt`; reuse it.
- No runtime-effect admission (W5h), spatial filters/layers/backdrop (W6), external image decoding/encoding, font/glyph generation, GM, dashboard, renders, references, scores, baselines, `jpg-color-cube`, global suites or native harness/security changes. No reset/purge/teardown/budget workaround.
- Serialize all Gradle executions. Shell commands use `rtk` or `rtk proxy` and the explicit workdir `/Users/chaos/.codex/worktrees/cbf6/kanvas`. No implementation task may run an unrelated broad suite.
- Five sequential meaningful implementation tasks below; Tasks1–3 collectively deliver the COMPLETE Blend family, Task4 Noise and Task5 convergence. Setup/contracts/tests travel with each functional slice. This execution-size refinement retains the original three family deliveries and ALL requirements. Task reviews are bounded. End with ONE whole-branch Sol review, at most ONE complete Astra fix wave and ONE scoped Sol re-review, then ONE Draft PR. Do not repeat the historical review/test loop.
- Durable progress is limited to this plan, `refactor/waves/W05-material-graph/status.md` and `refactor/README.md`, owned by ROOT. This planning worker owns only this document. No packet/ledger/artifact proliferation in this planning turn.

## Concrete design decisions and one numerical decision gate

1. **Explicit V5 composition in the existing hierarchy.** Add `ComposedMaterialProgramV5 : MaterialProgramPlan`, `ComposedMaterialBindingV5 : MaterialBindingPlan`, and `PlanDrawMaterialAuthority.MaterialV5`. Preserve V1–V4 historical layouts. A V5 root owns an ordered DAG of references to existing leaf/wrapper execution recipes; child topology is explicit, never `root.indexI32 - 1`. This is an extension of the common material contract, not a second material system.
2. **Sharing separates values from evaluation context.** Snapshot identity sharing survives public Shader → immutable MaterialNode. The same immutable payload can share one physical range; two evaluations with different accumulated local matrices, clamps or working domains remain different evaluation nodes. Canonical equality alone cannot certify that two captured objects have the same immutable owner. Program identity contains topology/order, operators, domains and binding types; values, seed, frequency, octave count, table bytes, colors and resource IDs remain dynamic.
3. **Keep capture limit semantics.** Memoize completed captures but still charge the existing occurrence traversal and check active-path cycles/depth before memo lookup returns a node. Do not switch to unique-node graph limits. Resource tables and actual evaluation DAG accounting are separately deduplicated only where their owner/context identities permit it.
4. **Nullable historical constructors.** Use the new five-argument `SizeI32?` constructor, a deprecated five-argument `SizeF32?` constructor, and a third five-argument `Nothing?` constructor delegating literal null explicitly to `SizeI32?`. ROOT's local Kotlin probe verified positional/named null, typed nullable old/new sizes, and old nullable-null variables. A trailing default Unit overload was disproved and is not the design. Task4 still compiles the real four declarations (Shader and MaterialNode, both noise kinds). Preserve historical named arguments. Generated `copy(tileSize=SizeF32)` is a source migration to the new typed property, not silently promised binary compatibility.
5. **Archive versioning is intrinsic.** Picture11/schema5 writes noise tile axes as I32 and new noise canonical domains. Read supported old Picture8/9/10 and schema versions with their actual old F32 layout, validating before explicit conversion. Do not reinterpret old bits or drop Picture10 from the accepted-version branch. Other geometry `SizeF32` fields keep their wire type.
6. **Noise numerical gate, before Task4 production edits.** The approved recurrence is F32 `q=(P+.5)*frequency`, doubling q and halving amplitude, with integer stitching periods doubled each octave. A naive255-iteration F32/I32 implementation overflows for admitted finite inputs. This plan does not invent an unreviewed multiword ABI or claim a future certificate. Task4 must present a safe-phase/true-integral-zero-tail derivation preserving approved operations/rounding. Charge all requested octaves even with a proven zero tail. A new numerical policy requires ROOT's precise exposed decision; numeric-domain-unbounded refusal never closes that valid domain or family. Blend Tasks1–3 remain independently executable; W5g closure cannot bypass this gate.

The last item is an actual unresolved implementation-design obligation, not a scope waiver. Mandatory 255-octave and positive-tile public witnesses below remain required. This document is complete as the Blend-first execution plan and as the explicit contract/gate for the remaining Noise delivery; it is not evidence that the unresolved representation already exists.

## Actual code map and ownership

Paths below are repository-relative exact paths; prefix abbreviations in later tables expand to these directories only:

```text
IR = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
```

| Existing file | Actual responsibility and required join |
| --- | --- |
| `API/canvas/DisplayOpSnapshot.kt` | Its Shader snapshot already uses `IdentityHashMap` and preserves shared captured children. Noise leaves are already immutable and reused; do not add artificial leaf copies. Its first-owner preflight must acquire complete noise scalar validation. |
| `API/render/ir/PaintSceneAdapter.kt` | Current recursive `Shader.toMaterial` loses sharing; Blend calls both children with `preserveW5dMatrices=false`. Replace this capture traversal with bounded ordered memoization, carrying the real capture context and preserving matrix semantics across Blend. |
| `API/render/ir/ColorFilterCapturePreflight.kt` | Current occurrence traversal has active-path detection but no completed memo. Preserve charging/refusal priority while avoiding duplicate retained snapshots. |
| `IR/MaterialNode.kt`, `IR/SceneArchiveCodec.kt` | Existing Blend and noise semantic nodes; current noise `SizeF32?`, current schema4. Change the noise value and canonical/wire domains only in Task4. |
| `PLAN/MaterialSourceConstructionV4.kt` | Pending original metadata has no table/proof/tuple; current gradient wrappers and image child are unary. Extend this owner to composed metadata instead of preparing child tables early. |
| `PLAN/FrameSourceLayoutV4.kt` | `standalone`, `ordinaryComposite`, `nativeComposite`, checked inventory and prepare/bind are the actual full-frame gate. Add child DAG inventory before any new stop/noise preparation or packing. |
| `PLAN/MaterialPlan.kt` | Current opacity/filter/image child access and V4 interner use adjacent refs. Add explicit V5 traversal/authentication/remapping without changing historical adjacency contracts. |
| `PLAN/ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt` | Existing graph is shared by color proof/emission; proofs retain child/filter/binding identity but assume one chain/image. Compose actual child graphs and complete contextual provenance, not component bounding boxes. |
| `PLAN/BlendFormulaProgramV1.kt`, `BlendFormulaOperationGraphV1.kt` | Existing shared 29-mode formula authority. Reuse it for child blends; do not copy a second blend implementation. |
| `PLAN/MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt` | Exact measured payload/physical requirements and packing permit. Extend existing permit to the final DAG layout and unique resources. |
| `PLAN/CapabilityCompilerChain.kt`, `SourceDeferredRenderConstructionV4.kt` | Composite selection and deferred construction converge here. No new lane-specific material compiler. |
| `GPU/materials/W5aMaterialSourceStage.kt`, `W5fColorOperationEmitterV1.kt` | Current source stage has one image execution and optional stop slab. Emit V5 through this stage with explicit physical resource mapping; multiple image children cannot alias the single `w5eTexture`. |
| `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt` | Actual native source owner and combined memory inventory currently retain one image lease/request and select a single storage/texture binding. Extend these same owners with the final ordered typed V5 resource mapping; no second materializer or lifecycle redesign. |
| `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt` | Existing single admission gate and unary candidate reader. Extend bounded recognition with explicit family/geometry facts, including recognized-invalid input ownership. |

Before editing a reader, inspect its whole function and callers. A sealed-interface compiler error is not H promotion authority. The shared Blend reader list below is finite; each current task's subset is classified before edits, never a wildcard future permission.

## Shared Blend authority and complete-family acceptance contract

**Outcome across Tasks1–3:** A complete public Blend delivery for all29 child modes, noncommutative children, shared immutable payloads, local coordinates, existing gradient/image/filter combinations, capture/replay and checked resources. The original monolithic implementation handoff obtained genuine RED but no production; ROOT split its50+-owner unit into three functional slices (R11). This shared contract is not a claim that Task1 alone implements every resource family. Blend is CLOSED only after all three slices and their reviews; the numerical Noise gate remains independent.

**Files — Create:**

- `PLAN/ComposedMaterialPlanV5.kt`: explicit immutable evaluation DAG, binding layout and V5 program/binding members.
- `PLAN/W5gPlanDiagnostics.kt`: exact new classification/schema/budget diagnostics.
- `TEST/surface/W5gComposedMaterialSurfacePixelTest.kt`: public Blend/child/coordinate/mutation/limits witnesses.
- `TEST/picture/W5gComposedMaterialPictureTest.kt`: public retained and decoded Picture playback.

**Files — Modify:**

- `API/render/ir/PaintSceneAdapter.kt`, `API/render/ir/ColorFilterCapturePreflight.kt`: preserve ordered shared capture and context without relaxing first-owner limits.
- `PLAN/MaterialPlan.kt`, `MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt`, `EffectiveMaterialPlanner.kt`, `ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`: exact shared source inventory, graph, proof, physical layout and V5 publication.
- `PLAN/ImageNumericOperationGraphV1.kt`: ONLY Task3's TexelRead constructor/identity/rebase, SampledRegion bind/rebase and narrowly necessary sampledTexelGraph logical-resource transport; preserve exact legacy upload-bound paths and numerical sampler/decoder/kernel equations (R10).
- `PLAN/CapabilityCompilerChain.kt`, `SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `RenderGraphConstruction.kt`, `PlanPasses.kt`, `W5aMaterialGraphContract.kt`, `W5bDestinationGraph.kt`, `W5bGeometryLanePlanV3.kt`, `W4dRenderGraphCanonicalSeal.kt`, `W4dGeneralRenderGraphCanonicalSeal.kt`: transport/authenticate new authority through existing frames/seals.
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt`: exact Rect/Path-fill admission and owned refusal.
- `GPU/materials/W5aMaterialSourceStage.kt`, `W5aPacketMaterialSourceV2.kt`, `W5fColorOperationEmitterV1.kt`, `GPU/planning/W5aMaterialPlanLowerer.kt`, `W4aAnalyticRectGraphLowerer.kt`, `W4cPathFillGraphLowerer.kt`, `W4dGeneralPathGraphLowerer.kt`, `W5bAnalyticRectGraphLowerer.kt`, `W5bNativeGeometryGraphLowerer.kt`, `GpuPlanTaskListLowerer.kt`, `GPU/passes/W5aMaterialPlanAuthorityV2.kt`, `GPUPlanW4dGeneralPreparedAuthority.kt`: common V5 lowering, actual bindings and witness custody on existing eligible geometry.
- `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt`: complete declared-resource inventory, per-slot native binding/reflection and retained image/slab ownership through the existing source materializer.
- `TEST/surface/W5fColorCpuOracle.kt`: extend the existing independent public-Shader interpreter with ordered Blend and public image/coordinate child recipes; do not import production plans/formulas/proofs.

**Files — Audit/reuse, no write unless a concrete missed V5 transport is identified to ROOT:**

- `API/canvas/DisplayOpSnapshot.kt`, `API/render/ir/SceneDisplayOpAdapter.kt`, `API/picture/Picture.kt`, `IR/MaterialNode.kt`, `IR/SceneArchiveCodec.kt`: existing immutable Blend round-trip and snapshots. No version bump solely for Blend.
- `PLAN/BlendFormulaProgramV1.kt`, `BlendFormulaOperationGraphV1.kt`, `ColorFilterExecutionPlanV1.kt`, `ColorNumericAuthorityV1.kt`, `GradientInterpolationPlanV4.kt`, `ImageNumericAuthorityV1.kt`, `ImageAtlasBlendNumericAuthorityV1.kt`: reuse actual equations/leaves; no duplicated numerical authority. ImageNumericOperationGraphV1 numerical regions remain read-only; its explicitly classified Task3 transport regions are the sole exception.
- `PLAN/W4bAnalyticRRectPlanCompiler.kt`, `W4dPathStrokePlanCompiler.kt`, `W4eNativePayloadPlan.kt`, `W5eImagePlanCompiler.kt`; `GPU/planning/W4bAnalyticRRectGraphLowerer.kt`, `W4dPathStrokeGraphLowerer.kt`, `W5bAnalyticRRectGraphLowerer.kt`; `GPU/execution/GPUW5eImageNativeV1.kt`, `GPU/passes/W5ePreparedFrameWitnessV1.kt`: exhaustive recognition must preserve historical support and explicit H refusal; no automatic composed-source promotion.
- `TEST/surface/W5fSurfacePixelFixtures.kt`, `WgslFloatEnvelopeV1Oracle.kt`, `W5dGradientAddressingSurfacePixelTest.kt`, `W5eImageShaderSurfacePixelTest.kt`: public native assertion/oracle/fixture patterns, not production-derived expected values.

**Interfaces:** Existing consumed entry points include `MaterialSourceConstructionV4.capture(draw: DrawNode, coordinates: SourceCoordinatesV4, bounds: RectF32, blend: BlendPlan, imageMaskChild: Boolean=false)`, `FrameSourceLayoutV4.nativeComposite(lanes: List<SourceDeferredRenderConstructionV4>)`, `MaterialPlanTable.colorSourceProofV4(root: MaterialPlanRef)`, and `ColorSourceProofV1.authenticates(table, root, coordinates)`. Preserve their old callers. Introduce these V5 contracts in the existing plan domain:

```kotlin
public data class MaterialEvaluationRefV5(public val indexI32: Int)
public class ComposedMaterialProgramV5 internal constructor(
    public val structuralId: MaterialProgramPlanId,
    internal val operationGraph: ColorOperationGraphV1,
) : MaterialProgramPlan
public class ComposedMaterialBindingV5 internal constructor(
    internal val definition: PreparedComposedSourceV5,
    public val sourceProof: ColorSourceProofV1,
) : MaterialBindingPlan
// Added to existing PlanDrawMaterialAuthority:
public data class MaterialV5(public val ref: MaterialPlanRef) : PlanDrawMaterialAuthority
// Added to existing MaterialPlanTable:
public fun colorSourceProofV5(root: MaterialPlanRef): ColorSourceProofV1

internal class MaterialEvaluationDagV5 private constructor(
    val entries: List<Entry>, val root: MaterialEvaluationRefV5,
) {
    class Entry internal constructor(
        val ownerNodeIndexI32: Int,
        val children: List<MaterialEvaluationRefV5>,
        val coordinates: SourceCoordinatesV4,
        val program: MaterialProgramPlan,
    )
}
internal class PreparedComposedSourceV5 private constructor(
    val capturedIdentity: String,
    val frameOwner: FrameSourceLayoutV4,
    val evaluation: MaterialEvaluationDagV5,
    val layout: ComposedBindingLayoutV1,
    val operationGraph: ColorOperationGraphV1,
)
```

`ComposedBindingLayoutV1` is added under existing `MaterialBindingPlan`, as specified in global §12's common layout contract; only built-in slots are delivered here, no runtime schema. It records ordered owner/local→physical offsets, total aligned uniform bytes and typed resources. Immutable constructors validate references and ownership before publication. Definition/proof issuance remains module-controlled. These signatures describe the new contract; implementations must supply all existing `MaterialProgramPlan` members, including version5 and the actual shared numeric graph bridge.

The program retains code-shaped topology/operators, coordinate-operation shape and binding types only. The contextual `MaterialEvaluationDagV5` and all coordinate/value/resource owners stay in `PreparedComposedSourceV5`/bindings and are authenticated by the value-dependent proof; never cache this dynamic DAG in a shared program. Fixed logical resource minima and dynamic complete physical slab sizes are distinct.

### Task1 built-in composed layout and diagnostic contract

The §12 common layout below is binding for built-in V5 sources, not runtime-effect admission. Define the layout under the EXISTING `MaterialBindingPlan`; keep all native handles/leases outside it and authenticate dynamic resource ownership separately. The logical schema remains the versioned existing leaf/wrapper `MaterialProgramPlan`.

For the complete ordered material DAG, prefix traversal assigns `ownerNodeIndexI32` on first visit; a later reference to the SAME captured shared node reuses that owner and logical ranges. Evaluation-context refs remain separate. The material group is1, uniform binding0. For each logical node block, `baseOffsetBytesI32=alignUp(cursor,16)`; physical field offset is base+local. Uniform mapping rows contain EXACTLY `ownerNodeIndexI32: Int`, `localOffsetBytesI32: Int`, `physicalOffsetBytesI32: Int`, `sizeBytesI32: Int`, `alignmentBytesI32: Int`. The total uniform size is aligned16 and stored/checked in I64 before narrowing. Never retain an unvalidated mutable list.

Resource rows retain `ownerNodeIndexI32: Int`, `logicalSlotI32: Int`, `groupI32: Int`, `bindingI32: Int`, `visibilityFlagsU32: UInt`, `kindTagU32: UInt`. Binding assignment starts at1 in declared prefix order. Each row has exactly ONE present typed layout option:
- buffer: `bufferTypeTagU32: UInt`, `minBindingSizeBytesI64: Long`, `hasDynamicOffset: Boolean=false`;
- texture: `textureViewDimensionTagU32: UInt`, `textureSampleTypeTagU32: UInt`, `multisampled: Boolean=false`;
- sampler option is ABSENT for all W5g built-ins; no hardware sampler/runtime slot/array is admitted.

The two other layout options are absent, not sentinel values. Fixed published tags are resource kind STORAGE_BUFFER=1/SAMPLED_TEXTURE=2, visibility FRAGMENT=0x2, buffer UNIFORM=1/STORAGE_READ_ONLY=2, dimension D2=1 and sample type FLOAT_FILTERABLE=1. Stop/noise storage is read-only; decoded image sampling uses explicit textureLoad taps. Slot logical minimum and complete dynamic slab/allocation byte size are separate authenticated facts; check the COMPLETE latter against actual capabilities/budgets before preparation. No default physical capability is invented.

The `composedBindingLayoutHash` preimage uses domain `kanvas-material-binding-layout-v1` then00, in EXACT order:
1. groupI32=1,bindingI32=0,visibilityFlagsU32=0x2,bufferTypeTagU32=1,minBindingSizeBytesI64=aligned uniform size,hasDynamicOffset=false,then that total sizeI64;
2. the ordered uniform mappings, each owner's index/local offset/physical offset/size/alignment;
3. the ordered physical resources, each owner/logicalSlot/group/binding/visibility/kind then exactly one present buffer/texture layout option above, others absent.

Use approved `CanonicalHashBytesV1` encoding: integers little-endian I/U32/64, bool/option tags00/01, lists countU32, domain ASCII terminated00, UTF8 strings lengthU32. Every count/length/sum is checkedI64 and representableU32 before hash/allocation; enums use published tags, never ordinal/name/native values. SHA256 lowercase enters assembled program identity only for the actual structural layout. Never include colors/stops/pixels/seed/ranges/value ownership or other dynamic data when binding ABI is identical. WGSL reflection/stage/native mapping must agree with the same sealed rows; immutable resource-owner/range/frame proof remains separate from this hash. No runtime ABI catalog/hash registration is introduced.

Task1's new diagnostic names/strings are EXACTLY:

```kotlin
public object W5gPlanDiagnostics {
    public const val Schema: String = "invalid.material.composed.schema"
    public const val NumericDomainUnbounded: String = "unsupported.material.composed.numeric-domain-unbounded"
    public const val Binding: String = "resource-limit.w5g.composed-binding"
    public const val Uniform: String = "budget.w5g.composed-uniform"
    public const val Storage: String = "budget.material.composed.storage"
    public const val Unpromoted: String = "unsupported.material.composed.slice"
}
```

Schema owns invalid composed topology/child refs/owner/layout authentication; NumericDomainUnbounded owns a useful unprovable COMPOSED output; Binding owns unavailable/exceeded physical resource/binding facts; Uniform and Storage distinguish composed uniform versus shared storage footprint overruns; Unpromoted owns composed H geometry or an explicitly pending child family. Preserve FIRST preexisting filter/gradient/image/opacity/capture diagnostics for invalid leaves and old admitted cases. Forward codes through the EXISTING typed boundary, no new exception/fallback. No new numeric byte budget/default/ceiling in Tasks1–3: use snapshotted PlanBudget.maxFrameLocalBytes, real physical limits, GraphLimits and checkedI64. Noise diagnostics/work limit are Task4-only.


- [ ] **Step 1 — Write RED public children/order/capture witnesses.** Join the existing test-only independent equations into ONE point-aware public-Shader interpreter. Preserve existing `expectedShaderTree` callers by appending defaulted arguments; replace its disconnected local solid-only `evaluate` with delegation to `shaderSource`. The concrete callable contract is:

```kotlin
fun expectedShaderTree(
    shader: Shader, paintAlphaF32: Float = 1f, external: ColorFilter? = null,
    destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC,
    devicePointF32: Point2F32 = Point2F32(.5f,.5f),
    canvasMatrixF32: Matrix3x3F32 = Matrix3x3F32(),
): WgslFloatEnvelopeV1Oracle.DrawResult
```

`devicePointF32` is the device-space sample BEFORE inverse canvas CTM and shader-local matrices, not a secretly pretransformed local point. Start with two `Interval.input` coordinate scalars; independently invert/project the canvas CTM once as the existing coordinate contract specifies, then evaluate its normalized F32 map with outward-rounded arithmetic. Carry a branch-local pending list of uninterrupted local matrices: `WithLocalMatrix` appends frozen coefficients, rather than independently inverting/mapping each edge. Compose the pending segment in declared order in F64, invert and project its coefficients ONCE, then apply the normalized F32 map at the same semantic flush boundary as the existing authority: immediately before `CoordClamp` or at the leaf/end. `CoordClamp` then applies at its original position. Blend clones pending segment/context into each child without introducing a new flush boundary; value/filter/opacity/working wrappers do not arbitrarily flush either. Independently duplicate these published composition/inversion/projection equations, including F32 normalized coefficients and allowed WGSL rounding; never call production `MaterialCoordinatePlanV2`, matrix decomposition, coordinate-plan or proof helpers. Keep branch-local outermost interpolation precedence. Do not collapse uncertain coordinates into a Point2F32 singleton. Ambiguous validity/predicate branches remain alternatives or an unbounded fixture. No new geometric value type outside math is needed: coordinate uncertainty is two numeric Interval scalars.

The private common `shaderSource` handles Solid, Opacity, WithColorFilter, WithWorkingColorSpace, WithLocalMatrix, CoordClamp, ordered Blend, existing gradient-family equations/stops/domains, and Shader.Image. Blend is `blend(shaderSource(src,context),shaderSource(dst,context),mode)` using the existing independent published formula oracle. For image SHADER children use the existing independent `sampledImage` equations with branch-local coordinates, tile modes and sampling, not image-ORIGIN paint/mask composition. Extend sampler/gradient coordinate inputs to carry the same two Interval scalars; keep old Point2F32 entrypoints as delegates. General stop sequences use independently bounded parameter/tile/segment decisions and the published interpolation equations, including >16-stop fixtures; no two-stop-only shortcut or production parameter graph import.

After this common source evaluation, apply paint alpha once, external filter once, final draw blend and attachment encoding through the existing independent `finish`. This sequence is shared by the Task1/Task3 fixtures. Production `BlendFormulaProgramV1` and plans/formulas/proofs remain forbidden in tests; expose no production test hook.

```kotlin
@Test fun orderedBlendChildrenRenderOnRectAndPath() {
    val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue), .5f)
    val src = Shader.Opacity(Shader.SolidColor(ColorARGB.Red), .25f)
    val shader = Shader.Blend(BlendMode.SRC_OVER, dst, src)
    val expected = W5fColorCpuOracle.expectedShaderTree(shader,
        paintAlphaF32 = 1f, destination = ColorARGB.Transparent,
        finalBlend = BlendMode.SRC)
    W5fSurfacePixelFixtures.requireBounded(expected)
    for (path in listOf(false, true)) {
        val surface = Surface(1, 1)
        surface.canvas {
            val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
            if (path) drawPath(Path().apply {
                moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close()
            }, paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f), paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected)) }
    }
}
```

The exact expected source before final encoding is `(0.25,0,0.375,0.625)` linear-premul; reversed children give `(0.125,0,0.5,0.625)`. Require disjoint bounded red/blue counterfactual sets before Surface. Complete the matrix with all `BlendMode.entries` (29), alpha0/.25/.5/1, and final SRC_OVER/SRC_IN/DIFFERENCE over Blue. A mode with a wider fixture is unresolved until a different discriminating bounded input is derived; do not lower the mode count.

- [ ] **Step 2 — Run only the new public RED class.**

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest
```

Expected before production: existing owned material refusal for nontrivial Blend; preserve the actual diagnostic/log. Oracle-unbounded is not RED evidence. A build error from a misspelled oracle parameter is not behavioral RED; use the existing `expectedShaderTree` signature exactly.

- [ ] **Step 3 — Implement shared capture and full-frame composed construction together.** Preserve `dst` then `src` prefix owner assignment and postorder evaluation. The active-path check precedes completed memo reuse; keep occurrence-based GraphLimits while memoizing captured payloads. Use a capture-context key that includes `preserveW5dMatrices`; do not share a differently interpreted snapshot. Branch coordinate stacks are copied by immutable metadata references, not mutated while processing siblings.

```text
capture metadata in declared dst/src order
  -> assign first-visit owner index; retain ordered child edges
  -> collect every child leaf's stops/images/filter records and contextual evaluation
  -> join ALL lanes' metadata and checked physical requirements
  -> issue the existing frame preparation permit for this exact inventory
  -> prepare unique leaf resources and rebase every child reference/range
  -> compose shared operation graphs + BlendFormulaProgramV1
  -> seal proof to original capture, final frame owner, exact layout and coordinates
  -> issue packing permit -> pack -> publish Ready -> native lowering
```

Check `base+count`, alignment and total bytes in checked I64 and prove U32 representability before casts. Shared stop values are frame-local; image cache requests retain existing device-generation/lease contracts and pessimistic byte costs. For two image children, declare separate typed physical texture slots unless immutable identity and physical facts permit one slot. Physical binding assignment follows prefix order from binding1; the uniform block stays group1/binding0. No child preparation is permitted while a later sibling/frame lane can still exceed a budget. Do not convert preexisting V3 copy debt into permission for new copies.

Transport the SAME ordered typed physical-resource rows from `ComposedBindingLayoutV1` to stage manifest, parser reflection/layout validation, native bind entries and source-partition ownership validation in `GPUW5aSourceStageNativeV2`. Each row identifies its kind/physical binding, immutable owner and final complete resource: image rows authenticate cache request→lease and generation; storage rows identify the actual shared slab, complete byte size and declared base/count range. Remove singular `.single` resource selection only for V5; historical layouts remain exact. Coordinate consumption follows the V5 graph's actual inputs, including any coordinate-dependent child, not the old stops-or-single-image heuristic. Extend `W5aFrameMaterialBudgetV2` to inventory every unique declared V5 request/slab with existing pessimistic texture/upload costs before materialization. Retain all handles through the SAME existing completion/rollback owner, in acquisition order; do not change dispatcher, pool, harness or lifecycle policy.

Proofs retain the exact child graph/condition facts, immutable dynamic owner, coordinate context, final physical mapping and frame owner. Never concatenate child uniform arrays and attach a proof that still references their old offsets. Rebased graphs and proofs must use the same final table; a change to values invalidates the old value-dependent proof without changing structure-only program identity.

- [ ] **Step 4 — Complete meaningful positive and negative public cells.** Use these fixed fixture families, expected from public inputs before capture:

| Fixture | Values / counterfactual | Required observations |
| --- | --- | --- |
| Shared diamond | `shared=Opacity(Solid(Red),.5)`; `Blend(SRC_OVER, WithColorFilter(shared,Matrix(scaleR=.5)), shared)` | Rect/direct Path/stencil Path; distinguish swapped children and accidental first-child output reuse. |
| Shared mutable gradient | 2 stops Black/White alpha128, x range `.5..1.5`; shared leaf in two children with local translations `-.25` and `+.25` | Independent .25/.75 samples, outer rotation/translation order, mutation of stops after Surface and Picture capture. Add >16-stop child from existing W5c public pattern. |
| Filter placement | Matrix with RGB translation `.125,0,0`, alpha translation `.25`; child Opacity0/.5 and external filter | Distinguish filter-before-blend, paint-alpha-per-child, filter-twice and opacity erasing a later restoring filter. |
| Two image children | `Image.fromPixels(2,1,[255,0,0,255, 0,0,255,128])` and reversed bytes, nearest then linear/cubic existing sampler fixtures | Rect and Path; mutate original arrays after capture, repeated render; no external codec. |
| Working domains | existing five W5f bounded two-stop domain fixtures inside opposite blend children | Outer domain precedence remains local to each branch; no scalar-only domain substitution. |
| Graph limits | 65 nested wrappers and 4097 counted occurrences, with shared leaf variants | Exact first capture diagnostic, valid append to the same recording after refusal, bounded native recovery. Do not change limits or assert internal node identity. |
| H ownership | RRect, Path stroke, Point(s), Text and Mesh family classification of an actual Blend source | Preserve exact typed H refusal where publicly reachable without font generation; static review for unavailable public pre-resolved text route. No claim of H promotion. |

Picture test code follows the existing public sequence `PictureRecorder.beginRecording(rect)` → draw → `finishRecordingAsPicture()` → mutate → `Picture.fromByteArray(picture.toByteArray())` → `replay.playback(surfaceCanvas)` → repeated native pixel assertion. Use a single shared mutable Matrix/filter in both branches and prove expected differs from mutated/reversed outputs before recording. No serialization shape/identity assertions.

- [ ] **Step 5 — At Task3 acceptance, verify the complete composed class pair and affected historical public classes, transport and ownership.**

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fGradientInterpolationSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest
rtk git diff --check
rtk git commit -m 'feat(material): execute ordered shared blend sources'
```

For every slice commit, stage only its actual classified paths after ROOT confirms. Never stage workspace/ROOTdocs. Preserve command exit/XML separately. Each Sol review covers that slice's capture/proof/physical/H joins; Critical/Important blocks its acceptance. This full-family checkpoint is reached only after Task3.

## Task 1: Uniform-only ordered Blend, capture and replay

**Outcome:** Independently complete public Rect/direct-Path/stencil-Path Blend for SolidColor, Opacity and the twelve existing non-runtime WithColorFilter kinds, all29 child modes, immutable shared subtrees, external paint filter/alpha/final blend, limits and capture/replay. No gradient/image/coordinate-dependent child is claimed promoted by this slice. Existing ordinary W5a–f cases remain unchanged; pending composed gradient/image and H geometry receive owned composed.slice refusal, never fallback. Full Blend-family closure waits for Tasks2–3.

**Create:** `PLAN/ComposedMaterialPlanV5.kt`, `PLAN/W5gPlanDiagnostics.kt`, `TEST/surface/W5gComposedMaterialSurfacePixelTest.kt`, `TEST/picture/W5gComposedMaterialPictureTest.kt`.

**Modify:** Shared contract's API capture/preflight/gate, PLAN material/source/layout/footprint/requirements/operation/proof and exact existing sealed transport readers, GPU common source stage/emitter/packet/lowerers/authority, and `TEST/surface/W5fColorCpuOracle.kt`. Only the uniform-only V5 path is implemented here. Native multi-image/storage owners and ImageNumericOperationGraphV1 remain read-only until Tasks2–3; no new materializer/texture/slab is needed for this slice. A sealed-reader hunk preserves H and historical branches; it is not promotion authority. Report exact actual changed readers before staging.

**Interfaces/implementation boundary:** Use the V5 contracts and exact built-in layout/diagnostics above. The root V5 entry owns the explicit ordered scalar evaluation DAG and its prepared immutable bindings; do not append unary children and infer root-1. The program owns only DynamicF32/code-shaped operators/refs, no source value owners. Its binding/proof owns original capture, final frame definition/layout/uniform mappings and contextual DAG (SourceCoordinatesV4.None for these coordinate-independent leaves). Extend FrameSourceLayoutV4 metadata inventory/prepare/bind and ColorSourceProofV1 authentication together for THIS scalar DAG. Issue full-frame checked preparation/packing permits before new uniform preparation; preserve historical linear owners. Existing native uniform binding0/source completion owner can transport the resulting stage unchanged. Gradient/image proof constructors/leases/resources are neither reused inside this program nor migrated in this slice.

- [x] **Step1 — Preserve verified genuine RED and complete independent scalar witnesses.** The retained initial public test and W5f oracle delegate already produce bounded/disjoint child-order expectations before Surface; actual RED at2026-09-14T16:10:26.396Z is1failure0error/skip/Gradle1, owned unsupported.material.w5a.kind, no production/native pixels. Do not rerun solely to recreate this evidence. Extend the ONE existing independent shaderSource for scalar ordered Blend/unary filter equations; preserve old callers. Device/canvas defaulted parameters may be declared now, but coordinate-dependent interpretation is Task2, never an unverified pretransformed sample.

```kotlin
val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f)
val src = Shader.Opacity(Shader.SolidColor(ColorARGB.Red),.25f)
val shader = Shader.Blend(BlendMode.SRC_OVER,dst,src)
val wanted = W5fColorCpuOracle.expectedShaderTree(shader,finalBlend=BlendMode.SRC)
W5fSurfacePixelFixtures.requireBounded(wanted)
```

Complete all BlendMode.entries, alpha0/.25/.5/1, noncommutative order, shared diamond and restoring-filter placement from the shared fixture table. Each useful expected set is singleton/two-adjacent and counterfactuals disjoint BEFORE Surface/recorder. Use all twelve existing filter fixture recipes (not runtime), paint alpha127/255 and final SRC_OVER/SRC_IN/DIFFERENCE on Blue. For each promoted family use actual direct AND stencil fill fixtures from existing public W5f patterns, not two identical triangle routes mislabeled.

- [x] **Step2 — Implement the metadata-only scalar DAG→final uniform layout→same graph/proof→Ready/native slice.** Preserve prefix dst/src owner assignment, occurrence GraphLimits, completed capture memo/context facts, immutable sibling metadata and original capture identity. Exact uniform/schema/binding/numeric diagnostics come from the contract above. No value-dependent program key, retained color/upload owner, second source compiler or per-child paint alpha. Only bounded scalar leaf/filter recipes are admitted; structurally recognized pending resources/H are terminally owned.
- [x] **Step3 — Complete public retained replay/mutation and refusal recovery.** Same shared mutable Matrix/table/filter in both branches, derive original/mutated/reversed bounded outputs before capture, then Surface/Picture original+decoded replay twice. Keep existing Picture10/schema4; Blend needs no wire change. Deep/shared occurrence graphs must reject at exact existing first-capture typed boundary, then valid append/render on SAME recorder/Surface. Name pending gradient/image and H geometry controls separately; don't remove any later positive requirement.
- [x] **Step4 — Run only the two new classes plus affected scalar/filter history on final source.**

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest --tests org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest
rtk git diff --check
rtk git commit -m 'feat(material): execute uniform-only ordered blend DAGs'
```

Stage only actual classified task paths after ROOT confirms; preserve assertion XML/native command distinction and full report/source epoch. ONE Sol spec+quality review closes this scalar slice only. No full Blend/W5g closure.

## Task 2: Contextual gradient children and shared stop resources

**Outcome:** Extend Task1's SAME V5 DAG to all four existing gradient families, stop sequences including>16, five working domains, local matrices/CoordClamp and shared leaf payloads evaluated in different branch contexts, on Rect/direct/stencil Path fill. Existing ordinary gradient RRect/stroke lanes stay admitted; composed H lanes remain refused.

**Modify:** Task1 common scalar owners/transport/oracle/classes plus existing gradient-source metadata/layout/footprint/proof/stage readers in the shared contract. `GPU/materials/W5aFrameMaterialBudgetV2.kt` and `GPU/execution/GPUW5aSourceStageNativeV2.kt` may change ONLY for exact declared shared-stop inventory/binding and actual coordinate-input consumption; no image/logical-texel migration or Noise. Reuse existing gradient numeric/interpolation authorities. No extra production file without exact ROOT classification.

**Interfaces:** Task1's program/binding/proof/layout remain. Complete common expectedShaderTree(devicePointF32,canvasMatrixF32) and private interval coordinate/gradient-stop interpreter specified above. Pending local segments clone per child; independently compose/invert/project once in F64 at clamp/end, preserving actual normalized F32 map rounding and outermost branch working precedence. Shared physical stops do not imply shared evaluated samples.

- [ ] **Step1 — Write genuine RED gradient/context expectations before production.** Use shared two-stop Black/White alpha128,x=.5..1.5,local translations±.25, plus>16-stop existing W5c recipe; all four family fixtures and five domains use independently bounded/disjoint outputs. Force different branch sample/context/order, mutation/Picture original+decoded retention and old uncomposed controls. Wider fixtures stay unresolved, not empirical tolerance.
- [ ] **Step2 — Inventory ALL child stops/context uniform rows before preparation; bind final shared slab ranges and issue the SAME contextual graph/proof.** Physical range sharing needs immutable owner/value proof; evaluation refs include normalized coordinate operation/domain context. Neither old offsets nor adjacent-ref inference may survive rebasing into V5. Keep program topology/code-shaped and actual coordinate inputs.
- [ ] **Step3 — Complete shared-contract gradient/domain/clamp/mutation/limits public cells and run both W5g classes plus W5fGradientInterpolationSurfacePixelTest,W5dGradientAddressingSurfacePixelTest,W5cGradientSurfacePixelTest.** Retain every Task1 scalar case; freeze final source, full command/XML/native report, provisional coherent commit and ONE Sol review. This does not close image-child Blend.

## Task 3: Image children and complete mixed Blend resources

**Outcome:** Two decoded image children and mixed image/gradient/filter/shared DAGs on Rect/direct/stencil Path fill, nearest/linear/cubic/manual taps, immutable pixels/matrices and Picture retention. After this review Tasks1–3 collectively satisfy the full shared Blend acceptance contract. No external codec/A8-origin promotion.

**Modify:** Task1–2 common owners/oracle/classes, exact native source+combined memory owners, and ONLY the classified ImageNumericOperationGraphV1 transport regions above. Reuse image sampler/decoder/authority/cache/lease equations and existing device-generation/completion owners.

**Interfaces:** TexelRead V5 logical resource refs retain ownerNodeIndex/logicalSlot/code-shaped type facts, never ImageUploadPlanV1/pixel/value owner in shared program. Legacy upload-bound paths stay exact. Binding-owned PreparedComposedSource/frame mapping resolves original immutable request/upload,dynamic dimensions,final physical slot and frame/generation for BOTH proof and WGSL emission of the SAME logical operation graph. Omitting contentIdentity alone is not payload removal. Same ordered typed rows pass layout→manifest→reflection→native bind entries→retained request/lease/slab custody and complete aggregate inventory.

- [ ] **Step1 — Write independently bounded RED two-image/mixed expectations.** Use the shared two2×1 image fixture bytes, reversed pixels/order, nearest then existing bounded linear/cubic/tile/domain samples, sibling local contexts and mixed gradient child. Common shaderSource calls independent sampledImage with interval coordinates, not image-origin paint/mask equations. Expected/mutated/swapped outputs precede Surface/Picture.
- [ ] **Step2 — Implement logical image-read transport/proof resolver plus final typed resource inventory/native mapping together.** Validate every image slot against exact immutable cache request/lease/generation and storage slot against complete slab/range; no single texture/storage selector or hidden sampler. Admit/copy/pack only after all later siblings/frame lanes pass the existing checked permit. Preserve V3 pre-inventory debt without new unauthorized copy.
- [ ] **Step3 — Complete original shared-contract image/filter/working/capture/limits/H cells; run both W5g classes plus W5eImageShaderSurfacePixelTest,W5fImageFilterSurfacePixelTest,W5fGradientInterpolationSurfacePixelTest.** Retain all scalar/gradient cases; full epoch/native report, provisional coherent commit and ONE Sol spec+quality review. ROOT may then close the Blend family, not Noise or allW5g.

## Task 4: NoiseV1, integral tile API and public archive compatibility

**Outcome:** Both noise families on Rect and Path fill through Tasks1–3's existing composed source authority, with seeded tables, stitching,0..255 octave contract, constructor/wire compatibility, finite proof and work-budget refusal/recovery. Begins after Task3 acceptance and the numerical decision gate is resolved to a concrete reviewed derivation.

**Files — Create:**

- `PLAN/NoiseTableV1.kt`: deterministic immutable 4352-byte table recipe/result, normalized seed key, checked frame ownership.
- `PLAN/NoiseOperationGraphV1.kt`: typed noise loop/phase operations consumed by existing color graph proof/emission; no independent material evaluator authority.
- `TEST/surface/W5gNoiseCpuOracle.kt`: independent PRNG/table construction and outward-rounded published noise equations from public inputs.
- `TEST/surface/W5gNoiseSurfacePixelTest.kt`: both families, coordinates/stitching/limits/mutation/public native pixels.
- `TEST/picture/W5gNoisePictureCompatibilityTest.kt`: constructor surface and old/new public Picture replay.

**Files — Modify:**

- `API/paint/Shader.kt`, `IR/MaterialNode.kt`, `API/render/ir/PaintSceneAdapter.kt`, `SceneDisplayOpAdapter.kt`, `API/render/ir/ColorFilterCapturePreflight.kt`, `API/canvas/DisplayOpSnapshot.kt`: SizeI32 semantic type/bridges, earliest complete scalar validation, homonymous round-trip. DisplayOpSnapshot changes only actual validation traversal, not immutable leaf copying.
- `API/picture/Picture.kt`, `IR/SceneArchiveCodec.kt`: explicit Picture11/schema5 write and old-version F32 readers.
- Task1's `PLAN/ComposedMaterialPlanV5.kt`, `MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt`, `ColorOperationGraphV1.kt`, `ColorSourceProofCompilerV1.kt`, `ColorSourceProofV1.kt`, `W5gPlanDiagnostics.kt`; `GPU/materials/W5aMaterialSourceStage.kt`, `W5fColorOperationEmitterV1.kt`: noise metadata/resource inventory, exact graph/proof/emission/packing through the same owners.
- `GPU/execution/GPUW5aSourceStageNativeV2.kt`, `GPU/materials/W5aFrameMaterialBudgetV2.kt`: extend Task1's SAME typed mapping/native ownership/inventory with the complete frame-local noise slab alongside any gradient slab and image slots; no single-storage-resource alias.
- `TEST/surface/W5fColorCpuOracle.kt`: join the independent Noise source intervals into Task1's common Shader interpreter, preserving its wrapper/coordinate/paint/external-filter/final-blend sequence.
- `PLAN/PlanBudget.kt`: add snapshotted software material work limits with a default preserving existing callers.
- `API/surface/RenderConfig.kt`, `API/surface/gpu/GPUPlanSurfaceRouter.kt`, `GPUPlanRenderContextOwner.kt`, `GPU/planning/GpuRenderContext.kt`, `GpuRenderBackend.kt`: transport public software noise-work limit to the existing PlanBudget equality/admission checks.
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`, `W5dGradientCandidateV2.kt`: owned Rect/Path noise recognition using the same gate.

**Audit/reuse:** Existing `SizeI32` declaration (no new geometry file); existing native buffer allocation/submission/completion owners and frame memory categories; existing Scene archive public validation and Picture malformed-input boundary; previous image external-codec exclusions. Any necessary additional resource-role/exhaustive transport file is identified by exact caller before edits; no native lifecycle patch is implied.

**Interfaces:**

```kotlin
// Both Shader and MaterialNode noise classes retain their existing names/field names.
data class PerlinNoise(
    val baseX: Float, val baseY: Float, val numOctaves: Int,
    val seed: Int, val tileSize: SizeI32?,
) : Shader {
    @Deprecated("Use SizeI32 for an integral noise tile")
    constructor(baseX: Float, baseY: Float, numOctaves: Int, seed: Int, tileSize: SizeF32?) :
        this(baseX, baseY, numOctaves, seed, checkedNoiseTileI32(tileSize))
    constructor(baseX: Float, baseY: Float, numOctaves: Int, seed: Int, tileSize: Nothing?) :
        this(baseX, baseY, numOctaves, seed, null as SizeI32?)
}
// FractalNoise has the same three signatures. MaterialNode uses the same scheme.
public data class MaterialFrameLimits(
    public val maxNoiseOctaveEvaluationsI64: Long = 1L shl 30,
)
public data class PlanBudget(
    public val maxFrameLocalBytes: Long,
    public val materialFrameLimits: MaterialFrameLimits = MaterialFrameLimits(),
)
// Added RenderConfig field; public software budget, not an injected device capability:
val maxNoiseOctaveEvaluationsI64: Long = 1L shl 30

internal class NoiseTableV1 private constructor(
    val normalizedSeedI32: Int, val bytes: ImmutableUBytes,
) {
    companion object {
        fun prepare(normalizedSeedI32: Int, owner: FrameSourceLayoutV4): NoiseTableV1
    }
}
```

Require noise-work limit >=0, so zero legitimately permits zero-octave noise and rejects requested nonzero work. Default `2^30` is the explicit software policy of this plan and must be acknowledged in ROOT/Astra plan review; no preexisting device fact is claimed. `checkedNoiseTileI32` is a shared render-ir semantic conversion function reused by public adapters/readers; it accepts null, otherwise validates each F32 with `isFinite`, `>=0`, `toDouble()<=Int.MAX_VALUE.toDouble()`, and `value.toDouble()==floor(value.toDouble())` BEFORE `toInt()`. In particular `2147483648f`, NaN, infinity, negative or fractional axes are invalid. Reuse `SizeI32` after validation. Do not validate via `Int.MAX_VALUE.toFloat()` (it rounds out of range).

- [ ] **Step 1 — Close the numeric representation decision before code.** Write the exact operation sequence and proof that it preserves the approved q/floor/fract/stitch recurrence, including its branch decisions, overflow/FTZ possibilities and loop bounds. An integral-zero tail may be skipped only if BOTH coordinate fractions are exactly zero for every permitted rounding branch and all subsequent contributions are identically zero, not merely small. One integral axis alone is insufficient. No epsilon tail truncation, lower semantic octave ceiling or successful fallback. If no such representation is derived, report that precise blocker and leave Noise unimplemented; do not run speculative native experiments to select a numerical rule.

- [ ] **Step 2 — Write RED public zero-octave and seeded nonzero fixtures, including constructor calls.**

```kotlin
@Test fun zeroOctavesAreDifferentMaterialsOnBothRequiredGeometries() {
    val shaders = listOf(
        Shader.PerlinNoise(.125f, .25f, 0, 7, null),
        Shader.FractalNoise(.125f, .25f, 0, 7, null),
    )
    val expected = shaders.map { W5gNoiseCpuOracle.expected(it, Point2F32(.5f,.5f)) }
    expected.forEach(W5fSurfacePixelFixtures::requireBounded)
    for (path in listOf(false,true)) shaders.forEachIndexed { index, shader ->
        val surface = Surface(1,1)
        surface.canvas {
            val paint = Paint(shader=shader, blendMode=BlendMode.SRC, antiAlias=false)
            if (path) drawPath(Path().apply {
                moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close()
            },paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected[index])) }
    }
}
```

`W5gNoiseCpuOracle.source(shader: Shader, localPoint: Array<WgslFloatEnvelopeV1Oracle.Interval>): Array<WgslFloatEnvelopeV1Oracle.Interval>` is the independent raw linear-premul source recipe for the two Noise leaf kinds; it consumes the two outward-rounded coordinate scalars without narrowing them. Task1's common `shaderSource` adds both Noise leaf cases delegating to this test-only function. `W5gNoiseCpuOracle.expected(shader: Shader, pointF32: Point2F32, paintAlphaF32: Float=1f, destination: ColorARGB=ColorARGB.Transparent, finalBlend: BlendMode=BlendMode.SRC, external: ColorFilter?=null, canvasMatrixF32: Matrix3x3F32=Matrix3x3F32()): WgslFloatEnvelopeV1Oracle.DrawResult` delegates the full public shader tree to `W5fColorCpuOracle.expectedShaderTree`, passing `pointF32` as `devicePointF32` and every named paint/filter/blend/CTM argument. It is callable for mixed Blend/Noise wrappers, not a second wrapper evaluator. All independent PRNG/table/noise equations remain in W5gNoiseCpuOracle; neither oracle imports production plans/formulas/proofs. At zero octaves the raw source is exact linear-premul Perlin `(0,0,0,0)` and Fractal `(.25,.25,.25,.5)`, before common paint/filter/final attachment equations. Do not turn Fractal into an sRGB `SolidColor(.5)` before linearization.

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest
```

- [ ] **Step 3 — Implement deterministic tables only after full-frame metadata admission.** The pinned references were actually read at Skia commit `70977ebbdbc111776199920c8c25243ba5dc71db`: [noise header](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.h) blob `51772235080948e81580f4f9d3feeeed80753de8`, [noise implementation](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/shaders/SkPerlinNoiseShaderImpl.cpp) blob `beee8413ae7afb66b8efa2edfa9bebe99cb61a26`, and [SkPoint normalization](https://github.com/google/skia/blob/70977ebbdbc111776199920c8c25243ba5dc71db/src/core/SkPoint.cpp) blob `d4fa426112a83ed6615d6dd0c85346e89df86505`.

```text
m=2147483647, q=127773, r=2836, multiplier=16807
s = seed<=0 ? -(seed % 2147483646)+1 : min(seed,2147483646)
next: t=16807*(s%127773)-2836*(s/127773); s=t<=0 ? t+m : t
perm[i]=i
channel0..3, i0..255: raw[channel][i]=(next()%512,next()%512)
i255 downTo1: swap(perm[i],perm[next()%256])
gradient[channel][i] comes from raw[channel][perm[i]]
x=(rawX-256)/256; y=(rawY-256)/256
normalize as pinned SkPoint: Double sqrt(x*x+y*y), Double reciprocal,
  project x*scale,y*scale toF32; zero vector -> (0,0)
quantize each component using pinned F32 add/multiply then positive half-up round
  (zero normalized component becomes U16 32768)
bytes0..255 = permutation; remaining4096 = channel-major/i-major/x,y U16 LE
total4352 bytes = 1088 packed U32 words
```

The application seed is I32 per approved spec; do not cast it through upstream's historical SkScalar seed. CPU and GPU consume the same prepared permutation/U16 bytes. The test oracle independently constructs its own bytes from public seed inputs, with no production table/helper import. U16 extraction is integer little-endian. Decode a component by the approved inverse normalization `(u16/32767.5)-1` with its real WGSL division/subtraction envelope; 32768 is not silently changed into exact zero.

- [ ] **Step 4 — Connect the reviewed loop and exact resource/work checks.** Keep all q/smooth/dot/bilerp/abs/sum/clamp/premultiply operations in the typed graph consumed by `ColorSourceProofV1` and `W5fColorOperationEmitterV1`. The finite loop uses dynamic requested octaves; seed/frequency/tile/octave values do not create shader variants. Allocate ONE read-only frame-local noise slab, with one 4352-byte, 16-byte-aligned range per unique normalized seed. Every node carries a dynamic base range; the same/different seed count changes slab bytes and numeric proof identity, never the program key or binding topology. Check each range and the complete physical slab, storage limits/stage count, bind-group count and total frame bytes before table preparation. Tables live through last consumer completion using existing ownership; no new cache or per-node hidden native allocation.

For each contextual noise evaluation, charge checked `ceil(conservativeWidth)*ceil(conservativeHeight)*requestedOctaves*4`, with bounds conservatively intersected with the real target as in existing geometry admission. Sum across actual draws/lane consumers; shared bytes do not imply shared evaluations across draws or different coordinates. Reused same-context DAG evaluation may count once only when the emitted evaluation actually occurs once. Compare to the snapshotted `MaterialFrameLimits` before `Ready`, before bulk tables/packing/native resources; overflow is a typed budget refusal.

Use diagnostics `invalid.material.noise.parameters`, `invalid.material.noise.tile`, `unsupported.material.composed.slice`, `unsupported.material.noise.slice`, `unsupported.material.noise.numeric-domain-unbounded`, `budget.material.noise.octave-evaluations`, and `budget.material.noise.storage` at their actual existing typed boundaries. Archive malformed conversion returns the existing invalid-archive public boundary with the precise conversion reason, rather than a raw cast or accepted saturated tile. Do not use string matching as a replacement for available typed recording diagnostics.

For positive axes adjust frequency exactly as global §11.2: zero base stays zero; choose floor/tile versus ceil/tile by the strict ratio comparison, tie/low0 choose high. Freeze the uniform decision; do not recompute it differently in WGSL. Wrap all four lattice corners before permutation lookup; double the integer period at each octave by the reviewed safe representation. An absent or zero-axis tile disables stitching.

- [ ] **Step 5 — Complete mandatory public fixture matrix and archive compatibility.**

| Coverage | Concrete inputs and required discriminants |
| --- | --- |
| Seed normalization | `Int.MIN_VALUE,-7,0,1,7,Int.MAX_VALUE`; compare equal-normalized pairs and distinct-seed nonzero noise outputs using independently constructed tables. |
| Octaves | `0,1,2,8,255` for BOTH families and BOTH required geometries; `.125,.25`, seed7, points `.5,.5` and `1.5,.5`; 255 case must be actual nonzero initial work, not merely base0. |
| Stitch | `SizeI32(8,4)`, `.2,.3`, seed7, octaves2/8/255; compare local P and P+(8,0)/(0,4), plus independent absolute expected pixels; low0 `.03125` on tile8, exact frequency `.25`, tie boundary derived from the strict ratios. |
| Disabled stitch | null, `(0,4)`, `(8,0)`, `(0,0)`; same public pixels as absent tile. |
| Local coordinates | inherited W5d transform order `C*T*R`, sibling-local clamps, negative local P and lattice boundaries. Shared noise leaf under two different matrices cannot reuse the same sampled result. |
| Composition | Perlin as dst/Fractal as src, and the reverse, inside opacity `.5`; external Matrix restoring alpha, paint alpha127/255, final SRC_IN/DIFFERENCE on Blue. Require bounded order and alpha counterfactuals before Surface. |
| Invalid parameters | negative/NaN/infinite base, octaves-1/256, I32 negative tile, historical F32 axis `.5`, `-1`, NaN, infinity, `2147483648f`; reject at capture/constructor boundary before snapshot allocations, then valid capture/render recovery. |
| Constructors | named/positional literal null, `SizeF32?=null`, `SizeF32?=SizeF32(8f,4f)`, `SizeI32?`, both families, public render and Picture playback; source compilation itself checks overload resolution without an ABI/private test. |
| Archives | frozen genuine old Picture8/9/10 noise payloads with absent/zero/integral tile; each new11 round-trip; malformed old fractional/nonfinite/out-of-range F32 tile rejected. Retain existing old8/9 image fixtures unchanged. |

Historical noise fixtures must come from the actual parent public Picture writer before Task4 changes, with fixed bytes/provenance. Use kanvas/src/test/resources/picture/format-10-noise-integral.base64 and format-10-noise-absent.base64; older8/9 require actual old writer/documented public wire fixture, never v11 version-byte substitution. Missing genuine payloads remain an exact compatibility gap, not coverage from unrelated image fixtures.

- [ ] **Step 6 — Run the two complete public noise classes and Task1's two classes, then commit.** Preserve source epoch, actual XML names/byte bounds and command exit separately. Compile modified math modules on JVM/JS only if new public math functions were actually necessary; merely reusing SizeI32 does not justify new math infrastructure tests. Use a coherent commit message `feat(material): add NoiseV1 and integral tile compatibility` after the reviewed source passes its targeted functional assertions.

## Task 5: Mixed-frame work/storage refusal and retained recovery

**Outcome:** Publicly validate the whole-frame sharing/work/ownership contract after both families exist, preserving old sources in the same frame. This is a behavioral integration delivery, not a scaffolding or bookkeeping task.

**Files — Create:** `TEST/surface/W5gConvergenceSurfacePixelTest.kt`.

**Files — Audit/reuse:** Task1–4 production owners, existing public W5a–f classes and native readback helpers. A causal production defect is fixed only in its already-classified owner; identify an extra owner to ROOT before expanding the file set. No pool/budget/native workaround.

**Interfaces consumed:** Public `RenderConfig(maxNoiseOctaveEvaluationsI64=...)`, `Surface(width,height,config)`, `canvas { ... }`, `render()`, Shader Blend/Noise constructors, Picture capture/replay; the common Blend Shader interpreter completed by Tasks1–3 and Task4's independent Noise oracle. No new production API.

- [ ] **Step 1 — Write public work-budget boundary and same-runtime recovery.** The calculation is exact: 1×1, one same-context noise evaluation, 2 octaves ×4 channels =8. A second distinct-coordinate evaluation costs another8; same bytes do not waive it. An alternating Rect/Path frame has two draws and costs16 even when their seed/table is shared.

```kotlin
@Test fun workBudgetChargesBothGeometryConsumersAndRecovers() {
    val shader = Shader.FractalNoise(.125f,.25f,2,7,null)
    val expected = W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f))
    W5fSurfacePixelFixtures.requireBounded(expected)
    val healthy = Surface(1,1,config=RenderConfig(maxNoiseOctaveEvaluationsI64=16))
    healthy.canvas {
        drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
        drawPath(Path().apply { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() },
            Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
    }
    W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
    val refused = Surface(1,1,config=RenderConfig(maxNoiseOctaveEvaluationsI64=15))
    refused.canvas {
        drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
        drawPath(Path().apply { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() },
            Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false))
    }
    repeat(2) {
        val failure = assertFailsWith<IllegalStateException> { refused.render() }
        assertEquals("budget.material.noise.octave-evaluations",failure.message.orEmpty().substringBefore(':'))
        W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
    }
}
```

The healthy Surface uses the same existing runtime, not an invented configurable failure injector. Also prove recovery on the same recording owner after invalid capture followed by a valid append. Do not claim same-target numeric rollback or native device-loss/quarantine coverage from the separate healthy Surface.

- [ ] **Step 2 — Add causal byte sharing and mixed ordering cases.** On a 29×1 target use64 alternating Rect/Path draws. In BOTH one-seed and64-distinct-normalized-seed controls retain a non-seed per-draw discriminator: the existing constant-output Matrix pattern uses RGB coefficient rows0, red translation `2f+indexI32/128f`, green/blue translations0, alpha coefficient row0/translation1. Clamp gives Red, but each of the64 source values remains distinct in BOTH controls. Use this external filter at the same position and with the same80-byte record in both, preserving graph topology, source-uniform allocation count/size, coordinates, geometry, work and all other resources. Derive the bounded Red output independently through the common Shader oracle before recording; do not assert internal identity/counts in tests.

Compare seed7 in all64 draws versus64 seeds with independently verified distinct normalized keys. First document both COMPLETE declared physical inventories, including64 distinct source uniforms, equal geometry/filter/coordinate records and pessimistic residency costs. Their only difference must be63 additional16-byte-aligned4352-byte table ranges (`63*4352=274176`); changing seed/source canonical identity alone without fixing the other inventory is not a causal test. Then analytically derive a reachable public frame budget between those inventories, before native execution. Do not reuse1,585,000 blindly or tune a budget until a desired error appears. Require shared-table positive control, precise distinct-table storage refusal and valid repeated native recovery. If no safe public causal window exists, the mandatory public storage witness remains OPEN; static ownership review may disclose the gap but cannot pass or close it. Do not fabricate a capability.

Mix ordered Blend, both Noise kinds, >16-stop gradient and two decoded image children across Rect/direct Path/stencil Path, interleaving ordinary old W5a–f draws and final destination-read. Compute the entire paint-order oracle independently; ensure later child preparation cannot evade earlier or later frame-wide budgets. Mutate the original stops/matrices/pixels after capture, replay original and decoded Picture, and render twice. Keep unsupported H, AA4 and spatial/runtime refusal controls distinct from positive cells.

- [ ] **Step 3 — Run the five new W5g public classes as one targeted covering, then the exact inherited fourteen-class covering plus these five classes once on the frozen final source.** ROOT read the actual final06 argv in `/private/tmp/w5f-task8-fix1-06-final-fourteen-forced-command.txt`; the explicit final19 command below preserves those fourteen names/order and adds the five new classes. `W5ePictureImageSamplingTest` exists but was not in that recorded fourteen-class covering; do not silently replace the historical set with a wildcard. No global `:kanvas:test` without `--tests` and no GM suite. Every previous public name is retained; compare actual fresh XML names, failures/errors and skips. Report inherited native exit133 separately from assertions; no rerun solely to relabel the native command green.

```text
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest --tests org.graphiks.kanvas.surface.W5gConvergenceSurfacePixelTest
rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fGradientInterpolationSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fImageFilterSurfacePixelTest --tests org.graphiks.kanvas.surface.W5fConvergenceSurfacePixelTest --tests org.graphiks.kanvas.picture.W5fPictureFilterInterpolationTest --tests org.graphiks.kanvas.surface.W5eDecodedImageSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageFamiliesSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest --tests org.graphiks.kanvas.surface.W5eImageConvergenceSurfaceTest --tests org.graphiks.kanvas.surface.W5dGradientAddressingSurfacePixelTest --tests org.graphiks.kanvas.surface.W5cGradientSurfacePixelTest --tests org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest --tests org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest --tests org.graphiks.kanvas.surface.W5gConvergenceSurfacePixelTest --rerun-tasks --no-parallel --console=plain
rtk proxy ./gradlew :render-ir:compileKotlin
rtk proxy ./gradlew :gpu-plan:compileKotlin
rtk proxy ./gradlew :gpu-renderer:compileKotlin
rtk proxy ./gradlew :kanvas:compileKotlin
rtk proxy ./gradlew :kanvas:compileTestKotlin
rtk git diff --check
```

Five compile commands are separate invocations, not a combined claim. Fresh final public tests must cover the same source that is committed; later source changes invalidate that final-source claim and require the necessary affected covering, not endless arbitrary reruns. ROOT verifies staged/committed source against tested source using normal repository custody checks, without adding source/hash tests to the suite.

- [ ] **Step 4 — Finish the reviewed branch.** Commit the coherent public integration slice, then ROOT performs ONE whole-branch Sol review against the W5f base. Give every Critical/Important finding verbatim to one adapted Astra fix wave and return only those findings plus introduced breakage to ONE scoped Sol review. If the plan's numerical or public evidence gate remains unresolved, report it precisely; no W5g CLOSED claim. After acceptance ROOT updates only the three durable docs and opens the single stacked Draft PR. No merge and no parent update.

## Self-review checklist for ROOT and fresh Astra plan review

The SDD preflight checks each self-row and every pair that shares a file or interface:

| Review relation | Required coupling check |
| --- | --- |
| Task1 → Task1 | Scalar capture/DAG/final uniform graph+proof →29modes/public Rect/direct+stencil/replay; no claimed resource promotion. |
| Task2 → Task2 | Independent normalized segment/gradient oracle →context DAG/final shared stops→same proof/emitter/public families/domains. |
| Task3 → Task3 | Logical image reads→binding-owned original upload/frame map→typed inventory/native custody→two-image/mixed public pixels. |
| Task4 → Task4 | Tile/archive→pinned table→reviewed255loop→software work policy→both Noise public families. |
| Task5 → Task5 | Independent whole-frame oracle→causal complete-inventory budgets/refusal→native recovery, no reset. |
| Task1 → Task2 | Same capture/material/source/layout/proof/stage/oracle/classes; gradients extend scalar DAG, no replacement/historical regression. |
| Task1 → Task3 | Same root program/binding/graph/typed layout/oracle/classes; image resources preserve scalar operator/alpha/filter order. |
| Task1 → Task4 | Same V5 shape/proof/permit; Noise adds leaf graph/work facts, no new material system. |
| Task1 → Task5 | Scalar cases/owner contracts preserved in final mixed covering. |
| Task2 → Task3 | Same branch coordinates/shared stops/proof/native resource rows/oracle; image sampling uses same interval contexts, not image-origin equations. |
| Task2 → Task4 | Same normalized coordinates/stop storage/stage mapping; Noise slab coexists, no single-storage alias. |
| Task2 → Task5 | Gradient/domain/mutation/native cases retained in mixed frames. |
| Task3 → Task4 | Same logical physical-resource/authenticity/native inventory; Noise extends typed mapping without payload-retaining program. |
| Task3 → Task5 | Image/gradient mixed bindings/leases/native custody and public classes retained. |
| Task4 → Task5 | Dynamic seed/slab/work config agree; sharing bytes differs from contextual/draw work; full255/stitch/archive gates retained. |

Each relation is an independent review obligation. ROOT's preflight records the actual file/interface joins before dispatch; it does not require another tracked document.

- [ ] §§5.2–5.3: dst/src and effective alpha/filter/coordinate order are carried by the actual graph, including shared sibling contexts; child blend never becomes draw blend.
- [ ] §§5.4–5.5: metadata capture, full-frame checked inventory and exact owners precede every new bulk copy/preparation/packing/native action; existing V3 debt is identified, not expanded.
- [ ] §5.6: independent oracle and actual graph share only the published contract, not production implementation. 255-octave representation and rounding/tail derivation are resolved before Noise edits; valid refused domains stay explicit.
- [ ] §11.2: PRNG, seed range, table generation order, pinned zero normalization, U16 little-endian storage, q+.5, turbulence versus signed fractal, alpha premultiplication, zero octaves, stitching choice/wrap, full work charging and table ownership have concrete obligations.
- [ ] Compatibility: nullable bridge is real; old F32 malformed values cannot saturate or be bit-reinterpreted; Picture10 support is retained alongside8/9; SizeI32 is reused.
- [ ] Matrix: Blend, Perlin, Fractal each have Rect AND Path fill plus alpha/mutation/nontrivial final blend. H lanes are named; old promoted source lanes are not unintentionally rejected/promoted.
- [ ] Exact file map: every new symbol is defined in this plan; any further consumer identified by implementation is classified before edit. Source review, never an infrastructure test, verifies architectural uniqueness.
- [ ] Closure: W5f historical native failure/warnings/numerical limits remain accurately inherited; five implementation tasks grouped into three family deliveries and one bounded final review/fix cycle do not promise native/global ISO or W5h/W6 work.

## ROOT preflight and plan-review decisions

Fresh Astra review/scoped confirmation closes the original3Important and coordinate correctionR8. The monolithic worker subsequently returned genuine scalar RED plus a FULL131-line size escalation, no production. R9–11 settle missing exact layout/diagnostics, payload-free logical image transport and functional execution decomposition. Same-seat refinement confirmation reads the complete585-line refined plan and permits scalar1 resumption:0Critical/0Important/1nonblocking numbering Minor, corrected in the two live references above. The15self/pair rows are recorded in the own ignored ledger; no old/sibling scope is current progress. R1–8 use the original numbering: oldBlend1 is nowTasks1–3,oldNoise2 now4,oldConvergence3 now5; their requirements/costs remain, not waived.

| Ruling | Decision and reason | Cost if wrong / required safeguard |
| --- | --- | --- |
| R1 | New W5g branch at unchanged W5f final55e4992d5 in existing isolation; future Draft PR targets W5f#2400 only. User authorized next stacked lot, not parent mutation/merge. | Stack contamination; exact branch/status and individually classified paths before commits, no parent writes. |
| R2 | Blend Task1 independently proceeds after plan acceptance; Noise production waits for a reviewed safe255 phase/stitch derivation. Full valid255 semantics are binding. | Overflow/false closure or unnecessary Blend delay; no epsilon/lower ceiling/refusal-as-success, mandatory public255/stitch witnesses. |
| R3 | Accept software work default2^30 and verified Nothing? nullable bridge, reusing math SizeI32. Explicit implementation refinements, never physical capability facts. | Old call-site break or poor budget default; real four declarations/public render/Picture constructor cases, exact budget/equality transport review; no binary/generated-copy promise. |
| R4 | Genuine old8/9/10 Noise archive and causal public storage witnesses remain required; unavailable evidence stays an open gate, never static/unrelated-fixture closure. | Wire regression or hidden memory/ownership defect; authentic provenance and analytically derived public controls before corresponding acceptance. |
| R5 | One independent point-aware public-Shader interpreter with defaulted device point/CTM, interval coordinates/wrappers/images/stops, and Task2 raw Noise interval join/common external-filter sequence. Actual disconnected oracle could not produce mandated fixtures. | Oracle-only errors masquerading as RED or missed sampled/coordinate behavior; published equations only, bounded/disjoint expectations before capture and actual Native Render/Readback. |
| R6 | Add exact existing native source binding and aggregate-memory owners to Task1/2; same typed resource rows and dynamic DAG/binding owners through native reflection/bindings/lease/slab custody. Actual code has singular resource assumptions. | Missing/aliased/unaccounted resource or unsafe lifetime; SAME materializer/completion/rollback owner, historical layouts/H retained, no pool/dispatcher/harness/lifecycle redesign. |
| R7 | Both Task3 controls retain64 distinct source uniforms through the same per-draw Matrix discriminator; freeze complete non-table inventory before table-only274176-byte delta/budget. Actual canonical dedup confounds seed-only controls. | False causal refusal or hidden residency costs; both complete inventories, independent output and positive/refusal/recovery, no tuned budget or static-only pass. |
| R8 | Preserve existing local-coordinate normalization boundaries in the independent oracle: compose each uninterrupted branch-local segment in F64, invert/project once before clamp/end; clone pending segments across Blend. Actual MaterialCoordinatePlanV2 does not map a separate F32 inverse per edge. | Wrong rounding/transform oracle could mask or falsely report a coordinate defect; independent equations and normalized F32 map envelopes, no production helper imports or new flush at Blend. |
| R9 | Copy exact built-in §12 layout rows/tags/hash encoding and six composed diagnostic strings into the brief; reuse current budgets, no runtime admission. Worker identified missing supplied contracts before production. | Guessed ABI/diagnostic owner or key fork; same sealed rows through proof/emission/reflection/native, old first-leaf diagnostics retained. |
| R10 | Classify only ImageNumericOperationGraphV1 logical-read transport regions for Task3; code-shaped V5 refs resolve through binding/frame-owned exact image proof/emission. Actual TexelRead retains upload payload after rebase. | Value-retaining program, sampled wrong owner/slot or decoder divergence; no key-only workaround, exact legacy sampler paths and same graph/owner mapping. |
| R11 | Split too-large monolithic Blend unit into scalar1,contextual-gradient2,image3; Noise4 and convergence5 follow. Retain all29/full-family/native/shared-budget obligations and ONE final stacked PR. | Partial slice mislabeled family closure or extra integration rework; owned pending-family refusal, each complete public slice+Sol review, full Blend closure only after3, wholeW5g only after5/gates. |
| R12 | Classify ONLY W4eNativePayloadPlan.collectPathPayload.materialColor's exhaustive MaterialV5→W5g Unpromoted branch, before edit, after ROOT full local-function/caller inspection and concrete compiler error. | Unintended W4e admission or old diagnostic loss; rejection-only hunk, preserve all historical branches, public/H controls and Sol review, no flattened material/native payload policy change. |
| R13 | Classify five exact exhaustive H readers: GPUPreparedMaterialProgram.hasNonFiniteW5aBindings, prepared Text/Vertices tableSnapshotIdentity, W4b RRect validate/resolve and W4d stroke resolve; V5 rejection only, after focused ROOT function/caller checks. | H leak/common-stage refusal or old gradient/token regression; retain all old branches especially strokeV4/V2, no flattening/resource/lifecycle change, functional/history/H gates and Sol review. |

Execution status: scalar Task1 functionally accepted after sole Sol review and SAME-seat scoped fix1 verification,0Critical/Important. Its final98public assertions pass on exact committed source; command FAILED/native195exit133 of unknown cause remains distinct. Task2 contextual gradients is next, Tasks3–5/fullBlend/fullW5g and Noise255/archive/storage gates remain open. ROOT owns durable progress; no parent mutation, nativeGREEN or ISO claim.
