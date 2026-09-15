# W5h Registered Runtime Effects and H-Lane Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the registered `child-opacity` runtime effect through the common W5 material authority, promote every applicable H lane, preserve Picture compatibility, and close W5 with public pixel evidence.

**Architecture:** Extend the existing backend-neutral descriptor, semantic plan catalogue, V5 material DAG, common source stage, frame inventory, and native ownership path into V6. Positive runtime effects are authenticated by `(RuntimeEffectId, semanticVersionI32, abiHash)` before `Ready`; the renderer only verifies its manifest and emits the numeric graph already sealed by `:gpu-plan`. Historical version-zero effects remain decodable but inert.

**Tech Stack:** Kotlin/JVM; `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; SHA-256; JUnit 5; public `Surface`, `Canvas`, `Picture`, `TextBlob`, `Vertices`, and `Mesh` APIs.

**Spec:** `refactor/specs/2026-09-15-w5h-registered-runtime-effects-design.md`

## Global Constraints

- Base branch: `codex/w5g-composed-procedural-materials` at `da9b367bd`; implementation branch: `codex/w5h-registered-runtime-effects`. The single Draft PR targets the W5g branch directly.
- Read the complete approved spec before each task. If code reality conflicts with it, stop that task and report the exact conflict; do not silently change the architecture.
- Use RED -> GREEN -> refactor in each behavior's owning task. A RED must fail for the intended public behavior on unchanged production, not for a fixture, build, permission, or harness problem. Task 7 accepts an already GREEN convergence baseline when every covered behavior has causal RED evidence in Tasks 1–6; never introduce an artificial defect to manufacture a convergence RED.
- Tests may observe only public API behavior, rendered pixels, public refusal diagnostics, public Picture bytes/playback, and recovery. Do not add tests for private/internal code, source shape, reflection, ABI layout through internals, counters, cache handles, call counts, mocks, fake devices, injected capabilities, or code infrastructure.
- Compute expected pixels independently before creating a `Surface` or `PictureRecorder`. Accept a singleton byte or exactly two adjacent bytes justified by the existing `WgslFloatEnvelopeV1` oracle. Do not introduce similarity thresholds.
- New geometry and transform value objects belong only in `:math`, with I32/I64/F32/F64 nomenclature. W5h should not need a new geometry type; reuse the existing math types.
- Font/shaping/glyph implementation changes, font discovery, dedicated font assertions/suites, external image codecs, Skia GMs, dashboards, renders, baselines, scores, `jpg-color-cube`, `:integration-tests:skia`, and global suites are outside every gate.
- Text coverage may reuse the existing in-memory synthetic `FontTypeface` fixture and pre-resolved glyph IDs/positions. Its existing rasterization can execute to supply A8 coverage; no font/shaping/glyph-generation implementation, assertion, discovery or dedicated gate is added or changed.
- W5h runtime entries and instances are **none-only** for their own logical resources: reject every nonempty runtime resource list before `Ready`. Typed resource facts remain representable in IR for deterministic validation/wire, but W5h adds no runtime resource API, binding or materialization path. Existing image/gradient/noise child resources keep their prior owners. This implements the review's explicit restriction of the resource shapes described in design §6; it does not claim runtime resource execution coverage.
- Serialize all Gradle runs. Run shell commands through `rtk` or `rtk proxy` with workdir `/Users/chaos/.codex/worktrees/cbf6/kanvas`.
- A native process exit 133 has cause `UNKNOWN` unless concrete evidence proves a cause. Report Gradle exit, XML method results, native exit, and source custody separately.
- Preserve W5g semantics: occurrence-based capture limits, first-owner refusal order, frame-wide immutable ownership, stop deduplication by content, independent image owners, pessimistic cache admission, one lease per consumer, and lease retention to frame completion.
- All new W5h admissions publish `MaterialProgramPlan`/`MaterialBindingPlan` V6 and use the existing table, common source stage, geometry coverage, final blend, frame inventory, and ownership path. V6 is not a second material compiler.
- Version-zero compatibility (`compile(wgsl)`, `register(effect)`, `registered(id)`, embedded WGSL and historical renderer registries) is deprecated and quarantined. It must never gain W5 admission. Delete only code made unreachable by promoted lanes; preserve remaining W8 transition hooks.
- Durable tracking is limited to this plan, `refactor/README.md`, `refactor/waves/W05-material-graph/status.md`, and the approved design. Task reports and review packages live only in the gitignored SDD workspace.
- Each task ends with one implementation commit, one Sol task review, implementer-owned corrections when required, and one scoped Sol re-review. After Task 7, run one whole-branch Sol review; permit one bounded implementation correction wave and one final scoped Sol re-review.

---

## Actual Code Map and Intended Ownership

Repository-relative path aliases used below:

```text
IR   = render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/
PLAN = gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/
GPU  = gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/
API  = kanvas/src/main/kotlin/org/graphiks/kanvas/
TEST = kanvas/src/test/kotlin/org/graphiks/kanvas/
RES  = kanvas/src/test/resources/picture/
```

| Existing owner | W5h responsibility |
| --- | --- |
| `IR/ResourceSnapshot.kt` | Evolve the current runtime descriptor v2 into descriptor v3 values: semantic version, logical ABI hash, uniform block/slots, child nullability and logical resources. Preserve an explicit legacy-v0 section. |
| `IR/SceneArchiveCodec.kt` | Write scene schema 6/Picture 12 and retain Picture versions 8–11 with their existing scene-schema mapping. Adapt all v2 runtime descriptors to inert v3 version zero through one path. |
| `API/pipeline/RuntimeEffect.kt`, `RuntimeEffectWgsl4kWiring.kt`, `ShaderModule.kt` | Keep legacy compilation isolated and expose exact positive lookup `registered(id, semanticVersionI32)`. Do not expose application registration of positive entries. |
| `API/render/ir/PaintSceneAdapter.kt` | Capture positive descriptors without WGSL; reconstruct positive built-ins by exact version; reconstruct old archives as detached/inert v0 only. Preserve post-capture immutability and child order. |
| `PLAN/NumericOperationGraphV1.kt`, `ColorOperationGraphV1.kt` | Carry the backend-neutral runtime operation `scaleAlpha(child, alpha)` in the existing numeric/color graph authority. |
| `PLAN/ComposedMaterialPlanV5.kt`, `MaterialPlan.kt` | Extend the existing composed DAG/layout/table to V6. Keep historical V1–V5 readable but never select them for a newly promoted W5h lane. |
| `PLAN/MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt` | Resolve the semantic catalogue, validate descriptor/children/uniforms/resources, seal owners and physical layout, and account before publication. |
| `GPU/planning/GpuRenderContext.kt` (`GpuPlanSurfaceExecutor.plan`), `PLAN/CapabilityCompilerChain.kt`, `W5eImagePlanCompiler.kt`, `W5aCompositePlanCompiler.kt`, `SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `RenderGraphConstruction.kt`, `PlanPasses.kt` | Freeze one plan-owned catalogue snapshot at frame entry, pass it through every nested compiler/source capture, and keep V6 publication transactional. |
| `GPU/materials/W5aMaterialSourceStage.kt`, `W5fColorOperationEmitterV1.kt`, `W5aPacketMaterialSourceV2.kt` | Generate runtime WGSL from the sealed numeric graph, assemble the common program, bind the sealed V6 layout, and keep dynamic values out of program identity. |
| `GPU/runtimeeffects/KanvasRuntimeEffectRegistry.kt`, `RuntimeEffectContracts.kt`, `KanvasPreparedRuntimeEffectResolver.kt` | Split the positive built-in renderer manifest from legacy registries; verify the sealed triplet/numeric identity before pipeline or resource ownership. |
| `GPU/planning/W5bAnalyticRRectGraphLowerer.kt`, `W4bAnalyticRRectGraphLowerer.kt`, `W4dPathStrokeGraphLowerer.kt`, `W5bPreparedPointBridgeV3.kt` | Retain geometry/coverage authority and consume `MaterialPlanRef` V6 instead of extracting or rejecting material locally. |
| `GPU/materials/GPUPreparedTextMaterialPlanProvenance.kt`, `GPUPreparedVerticesMaterialPlanProvenance.kt` | Replace W5a-only provenance with version-neutral references authenticated against the V6 table. |
| `API/surface/gpu/W5aPreparedTextMaterialBridge.kt`, `W5aPreparedVerticesMaterialBridge.kt`, text/vertices lowerers and preparers | Carry the common V6 material through pre-resolved Text, Vertices and Mesh without creating local material programs. |
| `API/surface/gpu/GPUPreparedDrawImageLowerer.kt`, `GPUOpMapper.kt`, `GPUMaterialMapper.kt` | For direct images, compose RGBA alpha or A8 mask with the common material and final `BlendPlan`; remove tint and `SRC_OVER` as local authorities. |
| `GPU/execution/GPUW5eImageNativeV1.kt`, frame-witness and resource-plan owners | Preserve owner-aware upload/accounting and device-generation-first cache keys while adding the final image-origin composition. |

New files have one narrow owner:

- `IR/CanonicalHashBytesV1.kt`: checked canonical byte encoder and positive/legacy runtime ABI hash recipes.
- `PLAN/RuntimeEffectSemanticCatalog.kt`: immutable exact-key catalogue, semantic entry and built-in semantic graph/evaluator identity.
- `PLAN/RuntimeEffectCpuEvaluatorV1.kt`: backend-neutral typed evaluation inputs, numerical rejection contract and executable child-opacity CPU evaluator.
- `PLAN/ComposedMaterialPlanV6.kt`: V6 program/binding expectations and runtime node data added to the existing composed table.
- `PLAN/W5hPlanDiagnostics.kt`: exact W5h refusal codes and classifiers.
- `GPU/runtimeeffects/W5hRuntimeEffectManifest.kt`: positive renderer manifest and exact triplet lookup.
- `GPU/materials/W5hRuntimeEffectWgslEmitter.kt`: deterministic emission from the sealed numeric graph only.
- `GPU/execution/W5hFrameSourcePreflightV1.kt`: frame-wide non-owning source validation and immutable validation witness before dispatcher materialization.
- `TEST/pipeline/W5hRuntimeEffectCatalogTest.kt`: public exact lookup and public construction behavior.
- `TEST/picture/W5hRuntimeEffectPictureTest.kt`: public v12 round trip and historical v11-v0 behavior.
- `TEST/surface/W5hRuntimeEffectCpuOracle.kt`: independent test-only pixel oracle.
- `TEST/surface/W5hRuntimeEffectSurfacePixelTest.kt`: Rect/Path fill and semantic refusal/recovery.
- `TEST/surface/W5hGeometryHLaneSurfacePixelTest.kt`: RRect, stroke/hairline and Point(s).
- `TEST/surface/W5hTextVerticesSurfacePixelTest.kt`: pre-resolved Text, Vertices and Mesh.
- `TEST/surface/W5hImageOriginSurfacePixelTest.kt`: A8/RGBA image origins and final blend.
- `TEST/surface/W5hConvergenceSurfacePixelTest.kt`: bounded cross-lane, ownership and prior-family covering.

Before editing an existing owner, read its complete relevant function and direct callers. If a named file moved, record the one replacement path in the SDD ledger and use that owner; do not create a duplicate class under the old name.

---

## Task 1: Backend-Neutral Descriptor, Canonical Hash, and Semantic Catalogue

**Outcome:** A positive built-in can be looked up only by exact `(id, semanticVersionI32)`, and its descriptor/catalogue identity is stable and backend-neutral. No renderer execution is enabled yet.

**Files — Create:**

- `IR/CanonicalHashBytesV1.kt`
- `PLAN/RuntimeEffectSemanticCatalog.kt`
- `PLAN/RuntimeEffectCpuEvaluatorV1.kt`
- `PLAN/W5hPlanDiagnostics.kt`
- `TEST/pipeline/W5hRuntimeEffectCatalogTest.kt`

**Files — Modify:**

- `IR/ResourceSnapshot.kt`
- `PLAN/NumericOperationGraphV1.kt`
- `PLAN/ColorOperationGraphV1.kt`
- `API/pipeline/RuntimeEffect.kt`
- `API/pipeline/ShaderModule.kt`
- `API/pipeline/RuntimeEffectWgsl4kWiring.kt`

### Contract to implement

The public lookup is exact:

```kotlin
public fun RuntimeEffect.Companion.registered(
    id: String,
    semanticVersionI32: Int,
): RuntimeEffect?
```

The only positive built-in is:

```text
id                     kanvas.runtime.child-opacity
semanticVersionI32     1
kind                   SHADER
input/output           LINEAR_PREMUL
child                  child: SHADER, nullable=false
uniform                alpha: FLOAT, offset=0, size=4, alignment=4,
                       arrayCountI32=1, arrayStrideBytesI32=0
uniform block size     16
logical resources      none
numericContractId      kanvas.runtime.child-opacity.numeric-v1
CPU evaluator          kanvas.runtime.child-opacity.cpu-v1, version 1
operation              scaleAlpha(eval(child), alpha)
```

`CanonicalHashBytesV1` must expose checked `u8`, `u32`, `i32`, `i64`, `text`, `option`, and list-count encoding. Integers are little-endian; strings use raw UTF-8 length in U32; every size/count/cumulative addition is checked in I64 before narrowing. The positive hash uses domain `kanvas-runtime-effect-abi-v1`, and the legacy recipe uses `kanvas-runtime-effect-legacy-abi-v0`. Both return raw 64-character lowercase SHA-256.

Descriptor v3 values use I32/I64 names:

```kotlin
public data class RuntimeUniformSlotV2(
    val name: String,
    val type: RuntimeUniformType,
    val offsetBytesI32: Int,
    val sizeBytesI32: Int,
    val alignmentBytesI32: Int,
    val arrayCountI32: Int,
    val arrayStrideBytesI32: Int,
)

public class RuntimeUniformBlockV1 private constructor(
    public val slots: List<RuntimeUniformSlotV2>,
    public val sizeBytesI32: Int,
) {
    public companion object {
        public fun of(slots: Collection<RuntimeUniformSlotV2>, sizeBytesI32: Int): RuntimeUniformBlockV1
    }
}

public data class RuntimeChildSlotV2(
    val name: String,
    val type: RuntimeChildType,
    val nullable: Boolean,
)

public enum class RuntimeLogicalResourceKindV1 {
    STORAGE_BUFFER, SAMPLED_TEXTURE, SAMPLER
}

public sealed interface RuntimeLogicalResourceFactsV1 {
    public data class StorageRead(val minBindingSizeBytesI64: Long) : RuntimeLogicalResourceFactsV1
    public data class Texture2DFloatFilterable(val multisampled: Boolean = false) : RuntimeLogicalResourceFactsV1
    public data class Sampler(val type: RuntimeSamplerTypeV1) : RuntimeLogicalResourceFactsV1
}
public enum class RuntimeSamplerTypeV1 { FILTERING, NON_FILTERING }
public data class RuntimeLogicalResourceSlotV1(
    val name: String,
    val logicalSlotI32: Int,
    val kind: RuntimeLogicalResourceKindV1,
    val facts: RuntimeLogicalResourceFactsV1,
)
```

The declarations above specify signatures; implement `of` with an unmodifiable defensive list copy, distinct nonblank names, checked local align/size/offset computation and final size aligned to 16. Resource slots require distinct nonnegative logical slots and matching kind/facts; storage size is positive, texture multisampled is false. Arrays, comparison samplers, storage textures, hidden samplers, and non-2D/non-filterable texture facts cannot be admitted. `RuntimeEffectDescriptor` retains its public name with `versionI32=3`, `id`, `abi`, `semanticVersionI32`, `abiHash`, `uniformBlock`, `childSlots`, `logicalResources`, and nullable `legacyV0`; all collections are immutable defensive snapshots. `legacyV0` holds original uniform layout, child slots, vertex layout and module facts exactly as specified in design §9. Positive descriptors require `legacyV0=null`; zero requires a legacy section and its separate hash recipe. Descriptor facts can represent a nonempty resource list for decode validation, but positive catalogue entry construction and W5 admission reject it.

### Exact public RuntimeEffect representation and compatibility

Replace the mandatory module field with this private immutable union inside `API/pipeline/RuntimeEffect.kt`:

```kotlin
private sealed interface Definition {
    data class PositiveV3(val descriptor: RuntimeEffectDescriptor) : Definition
    data class LegacyV0(
        val id: String,
        val module: ShaderModule,
        val uniformLayout: UniformLayout,
        val children: List<ChildSlot>,
    ) : Definition
}
// RuntimeEffect's private primary constructor receives only Definition.
public val id: String
public val semanticVersionI32: Int
public val abiHash: String?
public val kind: RuntimeEffectAbi?
public val descriptor: RuntimeEffectDescriptor?
public val moduleOrNull: ShaderModule?
@Deprecated("Legacy WGSL only; use descriptor for registered effects")
public val module: ShaderModule
@Deprecated("Legacy physical layout only; use descriptor.uniformBlock")
public val uniformLayout: UniformLayout
@Deprecated("Use descriptor.childSlots for logical type and nullability")
public val children: List<ChildSlot>
```

`PositiveV3` is built only by private companion `fromBuiltin(descriptor: RuntimeEffectDescriptor): RuntimeEffect` after the immutable plan-owned built-in lookup and exact triplet/hash verification; no public positive constructor or registration exists. Its metadata getters are non-null and read the descriptor; `moduleOrNull=null`; legacy `module` and `uniformLayout` throw `UnsupportedOperationException` with messages `Registered runtime effects have no WGSL module` and `Registered runtime effects have no legacy uniform layout`. `children` is an immutable compatibility projection by name/type; the descriptor remains authoritative for nullability.

`LegacyV0` snapshots module/layout/children and reports version zero. Because one legacy effect may be wrapped as different ABI roles, its `descriptor`, `kind` and `abiHash` getters return `null`; `PaintSceneAdapter.toDescriptor(abi)` constructs the role-specific legacy descriptor and v0 hash at capture. Its module/layout getters preserve their old values. Keep the old internal constructor signature as a deprecated `LegacyV0` adapter, including `registerOnConstruction`; it can never create a positive value. Update all module/layout consumers to branch on `semanticVersionI32` before legacy access. This preserves source signatures without inventing an empty WGSL module or a false intrinsic ABI for legacy values.

Keep exact existing factory signatures `makeShader(uniforms: UniformBlock, children: Map<String, Shader> = emptyMap()): Shader.RuntimeEffect`, `makeColorFilter(uniforms: UniformBlock, children: Map<String, ColorFilter> = emptyMap()): ColorFilter`, and `makeBlender(uniforms: UniformBlock): Blender`. Positive factories require respectively SHADER, COLOR_FILTER, BLENDER and never call legacy hooks; the sole SHADER built-in therefore rejects the latter two with `IllegalArgumentException("Runtime effect kind mismatch")`. No positive COLOR_FILTER/BLENDER implementation is introduced until a matching built-in exists. Legacy factories retain historical wrappers/hooks but remain inadmissible to W5. `register`, one-argument `registered`, and `compile` are deprecated v0-only; `register(positive)` rejects. Do not add a resource argument to any factory.

Task 2 changes the existing `PaintSceneAdapter` private `RuntimeEffect.toDescriptor(abi: RuntimeEffectAbi, extraChildren: Collection<RuntimeChildSlot> = emptyList()): RuntimeEffectDescriptor`: for positive values require `abi==kind` and empty extraChildren, return the already immutable positive descriptor; for v0 preserve original facts and build the role-specific legacy adapter/hash. Capture copies instance uniform values/children after budget validation; factories do not permit modifying descriptor identity. Positive archive reconstruction requires exact `(id,version,hash)` and descriptor facts; it cannot fabricate a built-in with `fromBuiltin` from unchecked bytes.

`RuntimeEffectSemanticCatalogSnapshot` is immutable after construction. Exact lookup requires the complete triplet after descriptor hash verification; `(id, version)` lookup exists only to construct the public built-in object. Duplicate exact keys with unequal facts and duplicate `(id, version)` with different `abiHash` fail snapshot construction.

### Executable CPU authority and catalogue entry

In `PLAN/RuntimeEffectCpuEvaluatorV1.kt`, define immutable backend-neutral values and an evaluator interface (all list constructors make unmodifiable defensive copies):

```kotlin
public data class RuntimeEffectCpuColorF32(val rF32: Float, val gF32: Float, val bF32: Float, val aF32: Float)
public sealed interface RuntimeEffectCpuUniformV1 {
    public data class FloatValue(val name: String, val valueF32: Float) : RuntimeEffectCpuUniformV1
    public data class IntValue(val name: String, val valueI32: Int) : RuntimeEffectCpuUniformV1
    public class FloatComponents(
        public val name: String,
        public val type: RuntimeUniformType,
        componentsF32: List<Float>,
    ) : RuntimeEffectCpuUniformV1 {
        public val componentsF32: List<Float>
    }
}
public class RuntimeEffectCpuInputsV1(
    children: List<RuntimeEffectCpuColorF32?>,
    uniforms: List<RuntimeEffectCpuUniformV1>,
) {
    public val children: List<RuntimeEffectCpuColorF32?>
    public val uniforms: List<RuntimeEffectCpuUniformV1>
}
public interface RuntimeEffectCpuEvaluatorV1 {
    public val id: String
    public val semanticVersionI32: Int
    public fun evaluate(inputs: RuntimeEffectCpuInputsV1): RuntimeEffectCpuColorF32
}
```

Children are evaluated in descriptor order by the common graph evaluator, and the input list retains that order/nullability; uniforms are decoded according to the logical block in descriptor order, without backend handles, WGSL, bindings or callbacks. Add `ChildOpacityCpuEvaluatorV1` with ID `kanvas.runtime.child-opacity.cpu-v1`, version 1. Its real `evaluate` body is:

```kotlin
require(inputs.children.size == 1 && inputs.children[0] != null) { "invalid.material.runtime_effect.cpu_children" }
require(inputs.uniforms.size == 1) { "invalid.material.runtime_effect.cpu_uniforms" }
val alpha = inputs.uniforms[0] as? RuntimeEffectCpuUniformV1.FloatValue
    ?: throw IllegalArgumentException("invalid.material.runtime_effect.cpu_uniforms")
require(alpha.name == "alpha" && alpha.valueF32.isFinite() && alpha.valueF32 in 0f..1f) {
    "invalid.material.runtime_effect.cpu_uniforms"
}
val child = requireNotNull(inputs.children[0])
require(listOf(child.rF32, child.gF32, child.bF32, child.aF32).all { it.isFinite() }) {
    "invalid.material.runtime_effect.cpu_numeric"
}
val out = RuntimeEffectCpuColorF32(child.rF32 * alpha.valueF32, child.gF32 * alpha.valueF32,
    child.bF32 * alpha.valueF32, child.aF32 * alpha.valueF32)
require(listOf(out.rF32, out.gF32, out.bF32, out.aF32).all { it.isFinite() }) {
    "invalid.material.runtime_effect.cpu_numeric"
}
return out
```

Use F32 multiplication per channel, with no clamp, implicit premultiplication or reorder. The constructor validates the finite `[0,1]` alpha domain and bounded `WgslFloatEnvelopeV1` graph contract before catalogue publication; dynamic validation completes before `Ready`. Invalid arity/type/nullability/nonfinite input or output is a terminal numeric/semantic diagnostic, never a fallback. `RuntimeEffectSemanticEntryV1` owns `descriptor: RuntimeEffectDescriptor`, `numericContractId: String`, `numericGraph: NumericOperationGraphV1`, `cpuEvaluator: RuntimeEffectCpuEvaluatorV1`, and the existing checked material graph/frame limits; it verifies evaluator ID/version and graph contract. V6 seals the evaluator's actual ID/version, not disconnected string metadata. The independent test oracle must not call this production evaluator.

`FloatComponents` accepts only `FLOAT2`, `FLOAT3`, `FLOAT4`, `MAT3X3` or `MAT4X4`; its required component counts are respectively 2, 3, 4, 9 and 16. It rejects blank names, wrong counts and non-finite components, then stores an unmodifiable defensive copy. `RuntimeEffectCpuColorF32` denotes linear-premultiplied RGBA and rejects non-finite components at construction.

In `PLAN/RuntimeEffectSemanticCatalog.kt`, expose `RuntimeEffectSemanticCatalog.builtinSnapshot(): RuntimeEffectSemanticCatalogSnapshot`, `snapshot.find(id: RuntimeEffectId, semanticVersionI32: Int, abiHash: String): RuntimeEffectSemanticEntryV1?`, and `snapshot.builtinDescriptor(id: String, semanticVersionI32: Int): RuntimeEffectDescriptor?`. The factory constructs only the immutable child-opacity entry and may safely return that immutable snapshot repeatedly. No registration API and no renderer registry lookup is permitted.

### TDD and implementation steps

- [ ] **Step 1 — Write the public RED.** In `W5hRuntimeEffectCatalogTest`, assert that `registered("kanvas.runtime.child-opacity", 1)` returns the same public descriptor facts across lookups (no object-identity assertion); unknown ID, version 0, version 2, and negative version return `null`; the one-argument `registered(id)` cannot select the positive effect; `compile(wgsl)` still yields version zero. Through public descriptor access, assert exact ordered child/uniform facts, absent resources, `moduleOrNull==null`, the specified exceptions from legacy module/layout getters and incompatible color-filter/blender factories, and the known ABI hash literal computed independently from the spec bytes. Assert a legacy ID collision cannot shadow the positive descriptor.
- [ ] **Step 2 — Prove the RED is causal.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest
  ```

  Record the failing methods and XML path in the SDD report. Existing production must fail because the versioned lookup/descriptor v3 is absent.
- [ ] **Step 3 — Add canonical values and hash recipes.** Implement `CanonicalHashBytesV1`, the V2 slot/block/resource values, descriptor-v3 validation, both hash domains, and exhaustive numeric tags from spec §§6 and 9. Keep old public type names as deprecated adapters when source compatibility requires them; positive construction must use only the new logical fields.
- [ ] **Step 4 — Add the immutable semantic catalogue.** Implement the factory, exact-key value, `RuntimeEffectSemanticEntryV1`, none-only resource validation, finite graph/capture limits and real `ChildOpacityCpuEvaluatorV1` defined above. Bind its actual identity/version and the sole `child-opacity` entry to `NumericOperationGraphV1`; add only the typed runtime operation needed for `scaleAlpha`.
- [ ] **Step 5 — Split positive lookup from legacy registration.** Update `RuntimeEffect` so exact versioned lookup returns a positive immutable built-in without module WGSL. Keep compile/register/one-argument lookup version-zero only and deprecate them. A legacy ID collision must not shadow the positive built-in.
- [ ] **Step 6 — Run the focused GREEN and compiles.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest
  rtk proxy ./gradlew :render-ir:compileKotlin
  rtk proxy ./gradlew :gpu-plan:compileKotlin
  rtk proxy ./gradlew :kanvas:compileKotlin
  ```

  Verify all new test methods pass in XML; report any native exit separately.
- [ ] **Step 7 — Refactor and commit.** Remove duplicate hash/tag logic introduced during GREEN, keep all public values immutable, run the focused test once more if source changed, and commit with `feat(runtime-effects): add versioned semantic catalog`.

### Task 1 acceptance

- Exact positive lookup works and latest-version lookup does not exist.
- Positive descriptors contain no WGSL or physical binding.
- Legacy v0 remains isolated and cannot satisfy positive lookup.
- Hash encoding and catalogue validation are backend-neutral and checked.
- No renderer, H-lane, Picture writer, external codec, font, or GM path changed.

---

## Task 2: Public Capture, Descriptor V3, and Picture 12 / Scene Schema 6

**Outcome:** Positive runtime effects survive immutable capture and Picture round trip; historical Picture 11 runtime descriptors become deterministic inert version zero and never execute.

**Files — Create:**

- `RES/format-11-runtime-effect-v0.base64`
- `TEST/picture/W5hRuntimeEffectPictureTest.kt`

**Files — Modify:**

- `API/render/ir/PaintSceneAdapter.kt`
- `IR/SceneArchiveCodec.kt`
- `API/picture/Picture.kt`
- `IR/ResourceSnapshot.kt` only for adapter helpers not completed in Task 1

### Wire contract

- Writer constants become Picture `12` and scene schema `6`.
- Readers accept historical Picture versions `8`, `9`, `10`, `11` and current `12` through explicit branches, retaining each old version's existing scene-schema number; only Picture 12 writes scene schema 6.
- A positive v3 descriptor writes `semanticVersionI32`, `abiHash`, logical uniform block facts, ordered child nullability, logical resource facts and no module.
- A v0 descriptor writes the legacy section only. It stores the v0 SHA-256 in `RuntimeEffectDescriptor.abiHash` but that hash grants no W5 capability.
- Every v2 reader feeds one v2-to-v3 adapter using the exact align/size table from spec §6. Historical children become `nullable=false`; resources are empty; version is zero.
- Positive-with-module, zero-pretending-positive, negative-version, malformed-hash, overflow, invalid count and truncated input are rejected before registration.
- Archive validation is transactional. A rejected archive installs no legacy descriptors. A valid old archive may reconstruct detached v0 objects, but no positive lookup or renderer lookup can observe them.

### TDD and implementation steps

- [ ] **Step 1 — Capture a genuine v11 fixture before writer edits.** Add a disposable public JUnit producer that records one `compile(wgsl)` shader with one uniform and one child, serializes it with the current Picture 11 writer and prints Base64. Run only that producer, copy the exact output into `format-11-runtime-effect-v0.base64` with `apply_patch`, then remove the producer before any production edit. Record base commit `983c4f1e6`, the public construction input and the decoded byte SHA-256 in the SDD report. The producer is not a committed test or gate.
- [ ] **Step 2 — Write Picture RED tests.** Through public `PictureRecorder`/`Picture.toByteArray`/`Picture.fromByteArray`/`Picture.playback`, cover positive `child-opacity` with alpha `0.5`, child order, mutation after capture, round-trip bytes, playback into a second recorder twice, and the stored v11 fixture. Task 2's GREEN asserts capture, decoded descriptor and bytes only. Task 3 adds replay-pixel, inert-v0 rendering refusal and same-surface positive recovery methods to this class; do not add disabled/skipped execution tests during Task 2.
- [ ] **Step 3 — Add malformed public byte cases.** In public methods `decodeRejectsUnknownRuntimeTriplet`, `decodeRejectsRuntimeAbiHashMismatch`, and `decodeRejectsMalformedRuntimeDescriptor`, start from public writer bytes and alter the ID, positive version, well-formed but unequal ABI hash, hash syntax, module or count fields with a small test-local wire parser. Cover unknown ID/version, negative version, non-lowercase/short hash, positive-with-module, positive nonempty resource declaration, truncated uniform list and overflowing count. `Picture.fromByteArray` returns `null` on rejection; subsequent valid decode and exact positive lookup remain unchanged. These bytes are the public path for invalid positive identity/ABI cases; never use internal constructors to force a Surface RED.
- [ ] **Step 4 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest
  ```

  The positive descriptor/wire round trip must fail on Picture 11/schema 5; record its causal byte/descriptor failure. Do not demand a positive render during this task. Historical v0 execution/refusal evidence belongs to Task 3.
- [ ] **Step 5 — Implement v12/schema6 and the common adapter.** Change writer/reader constants, encode v3 exhaustively, route Picture v8–v11 runtime descriptors through one checked v2-to-v3 adapter, and retain their existing scene-schema mappings and other historical field readers. Do not reinterpret unrelated Picture fields.
- [ ] **Step 6 — Make reconstruction exact and inert.** Update `PaintSceneAdapter` to capture positive built-ins by exact version/hash, preserve immutable uniform bytes and child snapshots, and reconstruct v0 detached values without any renderer hook. Register/install only after complete archive validation.
- [ ] **Step 7 — Run focused GREEN and compatibility gates.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.PictureTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest
  rtk proxy ./gradlew :render-ir:compileKotlin
  rtk proxy ./gradlew :kanvas:compileTestKotlin
  ```

  Inspect XML for method-level results and keep native exit reporting separate.
- [ ] **Step 8 — Refactor and commit.** Remove the disposable producer, verify only the genuine Base64 fixture remains, rerun `W5hRuntimeEffectPictureTest` if source changed, and commit with `feat(picture): encode runtime effect descriptor v3`.

### Task 2 acceptance

- Public Picture 12 round trip preserves the exact positive triplet, values and child order.
- Public historical v11 fixture maps deterministically to inert v0.
- Malformed and overflow cases reject transactionally.
- No external image codec, renderer execution, font, GM, or infrastructure test changed.

---

## Task 3: V6 Runtime Authority on Rect and Path Fill

**Outcome:** The registered `child-opacity` effect renders through the common material path on Rect and direct/stencil Path fill; semantic mismatches refuse before `Ready`, renderer mismatches refuse before pipeline/lease, and recovery succeeds on the same surface.

**Files — Create:**

- `PLAN/ComposedMaterialPlanV6.kt`
- `GPU/runtimeeffects/W5hRuntimeEffectManifest.kt`
- `GPU/materials/W5hRuntimeEffectWgslEmitter.kt`
- `GPU/execution/W5hFrameSourcePreflightV1.kt`
- `TEST/surface/W5hRuntimeEffectCpuOracle.kt`
- `TEST/surface/W5hRuntimeEffectSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/NumericOperationGraphV1.kt`
- `PLAN/ColorOperationGraphV1.kt`
- `PLAN/ColorSourceProofV1.kt`
- `PLAN/ColorSourceProofCompilerV1.kt`
- `PLAN/MaterialPlan.kt`
- `PLAN/ComposedMaterialPlanV5.kt`
- `PLAN/MaterialSourceConstructionV4.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `PLAN/MaterialSourceFootprintV4.kt`
- `PLAN/RawMaterialRequirementsV2.kt`
- `PLAN/EffectiveMaterialPlanner.kt`
- `PLAN/CapabilityCompilerChain.kt`
- `PLAN/W5eImagePlanCompiler.kt`
- `PLAN/W5aCompositePlanCompiler.kt`
- `PLAN/SourceDeferredRenderConstructionV4.kt`
- `PLAN/RenderGraph.kt`
- `PLAN/RenderGraphConstruction.kt`
- `PLAN/PlanPasses.kt`
- `PLAN/W5aMaterialGraphContract.kt`
- `PLAN/W4dRenderGraphCanonicalSeal.kt`
- `PLAN/W4dGeneralRenderGraphCanonicalSeal.kt`
- `GPU/materials/W5aMaterialSourceStage.kt`
- `GPU/materials/W5fColorOperationEmitterV1.kt`
- `GPU/materials/W5aPacketMaterialSourceV2.kt`
- `GPU/runtimeeffects/KanvasRuntimeEffectRegistry.kt`
- `GPU/runtimeeffects/RuntimeEffectContracts.kt`
- `GPU/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
- `GPU/execution/GPUW5aSourceStageNativeV2.kt`
- `GPU/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- `GPU/planning/GpuRenderContext.kt`
- `GPU/materials/W5aFrameMaterialBudgetV2.kt`
- `GPU/planning/W4aAnalyticRectGraphLowerer.kt`
- `GPU/planning/W4cPathFillGraphLowerer.kt`
- `GPU/planning/W4dGeneralPathGraphLowerer.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/passes/W5aMaterialPlanAuthorityV2.kt`
- `GPU/passes/GPUPlanW4dGeneralPreparedAuthority.kt`
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- `API/surface/gpu/W5dGradientCandidateV2.kt`
- `TEST/picture/W5hRuntimeEffectPictureTest.kt` for the now-executable replay/refusal methods specified by Task 2

### V6 contract

### Frame composition root and snapshot transport

The composition root is `GpuPlanSurfaceExecutor.plan` in `GPU/planning/GpuRenderContext.kt`. Its first planning action is `val runtimeCatalog = RuntimeEffectSemanticCatalog.builtinSnapshot()`. Construct the chain and every nested image/composite compiler with this one snapshot reference, before selection/capture; no capture or fallback takes another snapshot. The public `plan(scene, target, frameLocalBudgetBytes, materialFrameLimits)` signature is unchanged.

Required changed signatures in `:gpu-plan` are:

```kotlin
CapabilityCompilerChain.of(compilers: List<GpuPlanCompiler>, runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): CapabilityCompilerChain
W5eImagePlanCompiler(runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot)
W5aCompositePlanCompiler(runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot)
// Existing imageEntries constructor is internal and retains image ownership:
W5aCompositePlanCompiler(imageEntries: Map<Int, ImageConstructionEntryV1>, runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot)
// MaterialSourceConstructionV4 companion entry points; captureComposed remains private:
fun capture(draw: DrawNode, coordinates: SourceCoordinatesV4, bounds: RectF32,
    blend: BlendPlan, imageMaskChild: Boolean = false,
    runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): SourceConstructionResultV4<MaterialSourceConstructionV4>
fun captureImage(metadata: ImageMetadata, bounds: RectF32, blend: BlendPlan,
    runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): MaterialSourceConstructionV4
private fun captureComposed(draw: DrawNode, bounds: RectF32, blend: BlendPlan,
    runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): MaterialSourceConstructionV4
```

Store `runtimeCatalog` on the chain and these compiler instances. Append a required `runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot` parameter to the existing `MaterialSourceConstructionV4.capture`, `captureImage`, and private `captureComposed` methods, retaining their existing parameters/return types; pass it through every recursive child capture. The private source-construction object retains this reference until V6 is sealed. Thread it through the existing deferred-construction callers instead of using a global, thread-local or renderer registry. In particular, `W5eImagePlanCompiler.plan` currently builds a nested `W5aCompositePlanCompiler(constructionEntries)` and nested compiler chain: both must receive the exact frame snapshot. Update existing constructor call sites explicitly; no default argument may silently acquire a new snapshot. Existing `GpuPlanCompiler.select/plan` interfaces remain unchanged since frame scope is carried by compiler instances. Sol traces all construction/capture paths statically; no identity/counter test is added.

### Sealed program and layout

`ComposedMaterialProgramV6` and `ComposedMaterialBindingV6` implement the existing interfaces and retain the existing V5 evaluation DAG, resource owners, source proofs and dynamic values. V6 adds immutable runtime expectations per runtime node:

```kotlin
public data class RuntimeEffectExpectationV1(
    val id: RuntimeEffectId,
    val semanticVersionI32: Int,
    val abiHash: String,
    val numericContractId: String,
    val cpuEvaluatorId: String,
    val cpuEvaluatorVersionI32: Int,
)
```

The planner order is fixed:

1. validate capture depth/node/byte counts;
2. exact catalogue lookup by triplet;
3. recompute and compare logical ABI;
4. validate ordered uniforms, child names/types/nullability, logical resources and color contracts;
5. inline child evaluation into the existing DAG;
6. assign owner ranges and physical bindings in prefix-owner order;
7. validate frame/device capabilities and pessimistic budgets;
8. publish V6 `Ready` only after all draws/siblings succeed.

The V6 layout extends `ComposedBindingLayoutV1` rather than replacing it. Uniform node bases are 16-byte aligned; local F32 fields keep 4-byte alignment. Existing child-family resources follow the uniform binding in owner-prefix order; runtime-owned resource lists must be empty. `composedBindingLayoutHash` keeps domain `kanvas-material-binding-layout-v1`, and includes the exact uniform binding/mappings/resource options from spec §6.

Define `ComposedMaterialProgramV6.programIdentity: String` as raw lowercase SHA-256 of `CanonicalHashBytesV1`, with this exact ordered preimage:

1. domain ASCII `kanvas-material-program-v6` followed by `00`, then I32 schema `6`;
2. root evaluation-node index I32;
3. U32-counted evaluation-node list in the existing child-before-parent DAG order. Each row is its index I32, ownerNodeIndexI32, U32-counted ordered child-index list (each I32), then `entry.program.structuralId.value` encoded as canonical text. This is the existing value-free per-operation structure identity, including operation kind, uniform types, color/interpolation/address/filter/noise choices;
4. U32-counted owner list in ascending prefix-assigned owner index, each owner index I32 followed by its U32-counted member-node indices (ascending I32). Shared DAG nodes retain their owner; independently captured equal values remain distinct owners;
5. the existing sealed `operationGraph.canonicalIdentity` as canonical text, preserving the full value-free numeric/color operation structure;
6. U32-counted runtime expectation list in evaluation-node order; each is node index I32, `id.value` text, semanticVersionI32, abiHash text, numericContractId text, cpuEvaluatorId text, cpuEvaluatorVersionI32;
7. `composedBindingLayoutHash` as canonical text.

Use the checked Task 1 encoder for every list/text/integer; no ad-hoc separators, enum ordinal, object hash or renderer-derived field. Per-runtime leaf structural IDs are `runtime-effect-v1` (their identity fields are already encoded in step 6); do not recursively embed the final V6 program identity in a leaf. Store DAG/owner facts, operation graph and expectations on V6 and set `structuralId = MaterialProgramPlanId(programIdentity)`, so one plan-owned recipe authenticates all callers.

Dynamic uniform values, colors/stops/pixels, transforms represented as uniforms, image contents/handles, mutable cache state and device generation are excluded from this preimage. Runtime ABI hash stays logical and never absorbs composed offsets/layout. The renderer's source program cache key begins `(deviceGeneration, programIdentity)`; geometry/target pipeline specialization follows these fields. Layout/sampler/texture cache keys also begin with device generation and retain their family-specific identities. Thus runtime numeric/CPU identity and physical composition both affect program identity even when generated WGSL happens to match.

### Non-owning renderer preflight before every native materialization

The renderer receives no semantic catalogue object. Add `preflightW5hFrameSourcesV1(framePlan: GPUFramePlan): W5hFrameSourcePreflightResultV1` in `GPU/execution/W5hFrameSourcePreflightV1.kt`; define a sealed result with `Validated(witness: W5hFrameSourceValidationWitnessV1)` and `Refused(diagnostics: List<RenderDiagnostic>)`. The witness has a private constructor, binds the immutable frame plan identity, and stores all V6 source structural IDs, generated assembled module text, validated layouts and sealed expectations as immutable host values. It owns no device objects or leases and is produced only when the complete frame passes.

Call it at entry to `GPUWgpu4kFramePayloadMaterializerDispatcher`, before dispatching geometry or any other materializer. First enumerate **all** frame sources, across render steps/partitions/lanes and all siblings. For each V6 source, recompute manifest ABI and compare its triplet, numeric contract and CPU evaluator ID/version against the sealed expectation (which Task 1 derives from the actual evaluator); verify runtime resources are empty; emit from the sealed numeric graph; assemble with the sealed composed layout; validate WGSL parse, reflection and full layout/hash equality. The renderer never receives or invokes the catalogue's evaluator. Cache lookup/population, pipeline creation, geometry buffers, stop/noise buffers, image upload, uniforms and lease acquisition are prohibited during this first pass. A late sibling mismatch returns terminal diagnostics for the whole frame, with no fallback or mutation of `Ready`.

Only after the witness exists may the dispatcher call its geometry/resource materializers and then `materializeW5aSourcePartitionV2`. Add required parameter `sourceWitness: W5hFrameSourceValidationWitnessV1` to that function's existing signature (after `framePlan`); authenticate the exact frame and source at its entry and consume the previously validated module/layout. No default/nullable witness and no per-packet validation followed immediately by allocation is allowed. Hoist the current materializer's non-owning stop/noise owner/layout checks into the same first pass, before its existing early buffer uploads. All sibling validation must finish before any buffer/upload/cache/pipeline/lease operation, including geometry. Hand-written W5h WGSL and manifest-driven semantic reconstruction remain forbidden. Static Sol review traces the dispatcher order and witness custody; unreachable malformed manifests remain an explicit integration gap, never a private/fake test.

### Public test matrix

Use a non-white, non-opaque child color so channel and premultiplication errors are visible. Expected values come from `W5hRuntimeEffectCpuOracle`, which re-implements only `out = child * alpha` in linear premultiplied space and final attachment conversion; it must not import plan or renderer graph classes.

| Behavior | Required public witness |
| --- | --- |
| execution | Rect, direct Path fill and stencil Path fill with alpha `1.0` and `0.5` |
| ordering | nested child opacity with distinct inner/outer alpha and a noncommutative W5g blend child |
| immutability | mutate caller uniform bytes and child map after capture; original render and Picture replay remain unchanged |
| identity | public v0 `compile(wgsl)` and decoded historical v0 refuse with `unsupported.material.runtime_effect.unregistered_semantics`; malformed positive triplet/hash uses Task 2 public byte decode only |
| ABI/values | missing/extra/wrong child, malformed/missing/extra/non-finite/out-of-range uniform refuse with their classified W5h diagnostics |
| budgets | public graph depth/node count and uniform byte limit refuse before render publication; immediately render a valid draw on the same surface |
| prior W5 | one solid, gradient, image shader, color filter, W5g blend and Noise control remain byte-exact |

The public exact lookup returns `null` for unknown ID/version and exposes no positive custom constructor. Surface REDs therefore cover only constructible compile-v0, invalid child/uniform/value and public budget cases. Descriptor/catalogue/manifest mismatch, evaluator identity disagreement, nonempty positive catalogue entries and unbounded positive graph construction are static Sol checks or explicit integration gaps; never fabricate an internal manifest/reflection/capability mismatch or count one as a public gate.

### TDD and implementation steps

- [ ] **Step 1 — Write independent oracle and execution RED.** Add Rect and both Path-fill route cases for alpha `1.0`/`0.5`, nested ordering, mutation after capture, repeat render and one Picture replay. Assert the exact bytes or two adjacent oracle bytes.
- [ ] **Step 2 — Write refusal/recovery RED.** Add public compile-v0/historical-v0, child, uniform/value and depth/node/byte-budget refusals with the exact diagnostic category from `W5hPlanDiagnostics`; after each refusal, render a positive built-in on the same `Surface`. Add the Picture replay/refusal methods specified by Task 2 here. Unknown positive triplet/hash refusal remains exclusively in the public decode tests.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest
  ```

  Record failing method names and confirm the first positive render is refused by the current W5g runtime slice, not by the fixture.
- [ ] **Step 4 — Extend V5 into V6 in the existing plan.** Add runtime nodes/expectations to the current evaluation DAG and table, resolve the immutable catalogue during source construction, authenticate the descriptor, inline the child, compute checked layout/footprint and issue V6 only after whole-frame validation. Keep V1–V5 compatibility readers but select V6 for every new W5h-ready plan.
- [ ] **Step 5 — Extend numeric proof and emission.** Add one typed `scaleAlpha` operation with the same input/output color domain as the built-in. Prove finite alpha `[0,1]`, preserve the existing `WgslFloatEnvelopeV1` contract, and emit it through `W5fColorOperationEmitterV1`/the common source stage.
- [ ] **Step 6 — Add renderer manifest verification.** Implement the frame-wide non-owning preflight and required witness contract above at dispatcher entry, before geometry materialization. Validate every sibling's manifest/ABI/numeric/CPU identity, emitted WGSL and reflection/layout before all cache/pipeline/buffer/upload/lease operations, then require its witness in `materializeW5aSourcePartitionV2`. Keep legacy registry/resolver paths v0-only and unreachable from V6.
- [ ] **Step 7 — Complete ownership and keying.** Put `deviceGeneration` first in all newly touched pipeline/layout/sampler/texture keys; charge a complete miss without cache lookup; retain leases until completion; only evict zero-lease entries; invalidate prior generation entries on existing device-loss flow. Reuse a shared immutable owner once per frame while retaining a lease per consumer.
- [ ] **Step 8 — Run focused GREEN.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest
  rtk proxy ./gradlew :render-ir:compileKotlin
  rtk proxy ./gradlew :gpu-plan:compileKotlin
  rtk proxy ./gradlew :gpu-renderer:compileKotlin
  rtk proxy ./gradlew :kanvas:compileTestKotlin
  ```

- [ ] **Step 9 — Refactor and commit.** Search touched renderer code for runtime WGSL literals and V6 semantic re-resolution; remove duplicates, rerun the focused runtime surface class after source changes, and commit with `feat(gpu): render registered runtime effects through material v6`.

### Task 3 acceptance

- Rect and both Path-fill routes render the positive effect via common V6.
- All semantic validation and budgets complete before `Ready`.
- Renderer verification completes before native ownership and uses only sealed expectations/graph.
- Program structure excludes dynamic uniform values; program/layout keys start with device generation.
- Version zero never executes; refusal recovery is public and same-surface.

---

## Exhaustive W5 §16.1 H-Cell Ledger (Tasks 4–6)

Every row below is one H cell, with a named public JUnit method in its owning task. The ledger contains **33 H cells: Task 4 = 14, Task 5 = 12, Task 6 = 7**. `G` means `org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest`; `TV` means `org.graphiks.kanvas.surface.W5hTextVerticesSurfacePixelTest`; `I` means `org.graphiks.kanvas.surface.W5hImageOriginSurfacePixelTest`. Method names below are exact and must appear in their class, with parameterized invocations where specified. The Blend-final image cell covers both A8 and RGBA and counts once; Solid/Opacity's A8 and RGBA-alpha promotions are distinct obligations. Rect/Path-fill runtime T cells stay in Task 3 and do not increase this H count.

| H ID | W5 family | Lane/origin | Task | Exact public test method |
| --- | --- | --- | ---: | --- |
| H01 | Solid + Opacity | A8 | 6 | `I.a8SolidOpacityCaptureBlend` |
| H02 | Solid + Opacity | RGBA alpha | 6 | `I.rgbaPaintAlphaCaptureBlend` |
| H03 | Blend final | Image origin A8 + RGBA | 6 | `I.imageOriginsFinalBlendCapture` |
| H04 | 4 gradients | Point(s) | 4 | `G.pointsFourGradientsCaptureBlend` |
| H05 | 4 gradients | Text | 5 | `TV.textFourGradientsCaptureBlend` |
| H06 | 4 gradients | Vertices/Mesh | 5 | `TV.verticesMeshFourGradientsCaptureBlend` |
| H07 | 4 gradients | A8 | 6 | `I.a8FourGradientsCaptureBlend` |
| H08 | LocalMatrix + tile + CoordClamp | Point(s) | 4 | `G.pointsAddressingCaptureBlend` |
| H09 | LocalMatrix + tile + CoordClamp | Text | 5 | `TV.textAddressingCaptureBlend` |
| H10 | LocalMatrix + tile + CoordClamp | Vertices/Mesh | 5 | `TV.verticesMeshAddressingCaptureBlend` |
| H11 | LocalMatrix + tile + CoordClamp | A8 | 6 | `I.a8AddressingCaptureBlend` |
| H12 | ImageSample | RRect | 4 | `G.rrectImageSampleCaptureBlend` |
| H13 | ImageSample | Path stroke/hairline | 4 | `G.strokeImageSampleCaptureBlend` |
| H14 | ImageSample | Point(s) | 4 | `G.pointsImageSampleCaptureBlend` |
| H15 | ImageSample | Text | 5 | `TV.textImageSampleCaptureBlend` |
| H16 | ImageSample | Vertices/Mesh | 5 | `TV.verticesMeshImageSampleCaptureBlend` |
| H17 | Color filters | RRect | 4 | `G.rrectColorFiltersCaptureBlend` |
| H18 | Color filters | Path stroke/hairline | 4 | `G.strokeColorFiltersCaptureBlend` |
| H19 | Color filters | Point(s) | 4 | `G.pointsColorFiltersCaptureBlend` |
| H20 | Color filters | Text | 5 | `TV.textColorFiltersCaptureBlend` |
| H21 | Color filters | Vertices/Mesh | 5 | `TV.verticesMeshColorFiltersCaptureBlend` |
| H22 | Blend children + Noise | RRect | 4 | `G.rrectBlendNoiseCaptureBlend` |
| H23 | Blend children + Noise | Path stroke/hairline | 4 | `G.strokeBlendNoiseCaptureBlend` |
| H24 | Blend children + Noise | Point(s) | 4 | `G.pointsBlendNoiseCaptureBlend` |
| H25 | Blend children + Noise | Text | 5 | `TV.textBlendNoiseCaptureBlend` |
| H26 | Blend children + Noise | Vertices/Mesh | 5 | `TV.verticesMeshBlendNoiseCaptureBlend` |
| H27 | Blend children + Noise | A8 | 6 | `I.a8BlendNoiseCaptureBlend` |
| H28 | Runtime effect catalogued | RRect | 4 | `G.rrectRuntimeCaptureBlend` |
| H29 | Runtime effect catalogued | Path stroke/hairline | 4 | `G.strokeRuntimeCaptureBlend` |
| H30 | Runtime effect catalogued | Point(s) | 4 | `G.pointsRuntimeCaptureBlend` |
| H31 | Runtime effect catalogued | Text | 5 | `TV.textRuntimeCaptureBlend` |
| H32 | Runtime effect catalogued | Vertices/Mesh | 5 | `TV.verticesMeshRuntimeCaptureBlend` |
| H33 | Runtime effect catalogued | A8 | 6 | `I.a8RuntimeCaptureBlend` |

Every invocation in **every row** must use nontrivial alpha (paint alpha `149/255`; runtime rows additionally `alpha=0.5`), mutate caller-owned material inputs after capture, and exercise nontrivial final blend `SRC_IN` and `DIFFERENCE` over a nonopaque colored destination. Render the captured draw twice and a public Picture replay when that operation is supported; assert independently computed pixels remain the pre-mutation result. Mutate gradient stop colors/positions, image bytes, filter matrix or runtime uniform bytes/child map according to the family. For immutable Noise/Blend wrappers, wrap that actual family material in registered child-opacity using a caller-owned mutable child map and uniform bytes, capture, then replace the map's child with a distinct Noise seed/Blend and change alpha bytes; assert the original family pixels still render. For addressing, mutate its gradient child's stop arrays after capture while retaining the transform/tile/clamp. A8/RGBA solid/alpha/blend cells mutate caller image bytes. `Paint` itself is immutable; reassigning a local Paint variable is not mutation evidence. Parameterized invocations within a cell inherit these three requirements; wrapping a family with runtime does not replace that family's independent oracle or invocation set.

Family invocation sets are explicit:

- Four gradients: linear, radial, sweep and two-point conical; use each family with the existing W5c 1/2/16/>16-stop and duplicate-stop controls where applicable.
- Addressing: existing local-matrix transform classes, each CLAMP/REPEAT/MIRROR/DECAL tile mode, and CoordClamp with asymmetric bounds/coordinates outside the clamp; choose interior coverage pixels whose color changes if the transform/clamp/tile is ignored. Reuse the W5d independent oracle, not renderer code.
- ImageSample: in-memory RGBA and A8 image shader inputs with nearest, linear and cubic sampling using the W5e oracle.
- Color filters: the public admitted W5f filter constructors and ordered composed-filter cases already enumerated in `W5fColorFilterSurfacePixelTest`, `W5fFilterOrderingSurfacePixelTest` and `W5fImageFilterSurfacePixelTest`; apply those material cases to each listed H lane and retain their independent expected-color formulas.
- Blend children + Noise: ordered noncommutative Blend children with shared DAG child, Perlin and Fractal Noise; evaluate both Noise variants and the Blend case in each row, using the W5g CPU oracle.
- Runtime: exact child-opacity built-in with colored, nonopaque child; include nested child-opacity and immutable capture. Runtime-owned resources stay empty, even when its child owns image/stop/noise resources.
- Point(s) rows include drawPoint plus every already admitted public drawPoints mode. Stroke rows include finite stroke and hairline; Vertices/Mesh rows include untextured and in-memory textured paint-material routes and operationBlendMode, without admitting a legacy MeshProgram. Text uses the synthetic fixture contract in Task 5.
- H03 additionally covers fixed-function, `DST/NoOp`, and destination-read on both origins, preserving destination for the no-op case; the other invocations still prove nontrivial final blend. RGBA shader-family cells marked `—` in §16.1 remain inapplicable, not missing coverage.

Task 7 records H IDs, exact class/method/invocation names, causal RED owner/source revision, GREEN source revision, alpha/mutation/final-blend evidence and JUnit result. It counts cells separately from invocations: 33 required, 33 closed, zero unexplained H gaps; historical T results and the 45 DrawPoint cases have separate counts.

## Task 4: Promote RRect, Path Stroke/Hairline, and Point(s)

**Outcome:** Geometry lanes keep their W4 coverage/topology authority and consume the same V6 material reference. Point(s) no longer extracts a solid payload. AA4 remains unchanged.

**Files — Create:**

- `TEST/surface/W5hGeometryHLaneSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/W4bAnalyticRRectPlanCompiler.kt`
- `PLAN/W4dPathStrokePlanCompiler.kt`
- `PLAN/W5bGeometryLanePlanV3.kt`
- `PLAN/W5bDestinationGraph.kt`
- `PLAN/W4eNativePayloadPlan.kt`
- `GPU/planning/W5bAnalyticRRectGraphLowerer.kt`
- `GPU/planning/W4bAnalyticRRectGraphLowerer.kt`
- `GPU/planning/W4dPathStrokeGraphLowerer.kt`
- `GPU/planning/W5bPreparedPointBridgeV3.kt`
- `GPU/recording/GPURecordedPointAuthorityV3.kt`
- `GPU/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- `GPU/planning/W5bNativeGeometryGraphLowerer.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/passes/GPUPlanW4dGeneralPreparedAuthority.kt`
- `API/surface/gpu/GPUDispatchRRect.kt`
- `API/surface/gpu/GPUOpMapper.kt`

### Public test matrix

| Lane | Positive witness | Preservation witness |
| --- | --- | --- |
| RRect fill | alpha `0.5`, nested child and nontrivial final blend | historical geometry edge/coverage pixels and existing AA4 refusal/skip unchanged |
| Path stroke | finite width, alpha `0.5`, direct route | cap/join and destination-read control |
| Path hairline | public hairline width, alpha `0.5` | historical hairline coverage control |
| DrawPoint | one point, alpha `0.5` | all existing 15 blend modes × 3 contexts = 45 cases unchanged |
| DrawPoints | lines/polygon public modes with runtime material | ordering and coverage control |

Every lane also covers post-capture uniform/child mutation and one refusal followed by a valid same-surface recovery. Reuse the Task 3 oracle for runtime and the independent W5c–W5g family oracles for the ledger methods. Task 4 owns all 14 cells assigned to it in the exhaustive ledger; runtime-only lane tests do not close its gradient/addressing/ImageSample/filter/Blend/Noise cells.

### TDD and implementation steps

- [ ] **Step 1 — Write geometry RED cases.** Implement the 14 named Task 4 ledger methods and all their specified family/lane invocations. Each has nontrivial alpha, post-capture mutation and the final-blend witnesses; record a causal RED for each newly promoted family/lane, including points gradients/addressing and RRect/stroke/points ImageSample, filters, Blend and both Noise variants.
- [ ] **Step 2 — Add historical preservation calls.** Invoke `GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix` as a separate gate for its 45 public cases; do not copy or weaken its assertions. Add one public W4 RRect and stroke coverage control to the new class only where the current public tests do not already cover it.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest
  ```

  Confirm each lane reaches its present material refusal/solid extraction boundary.
- [ ] **Step 4 — Replace lane barriers with V6 references.** Keep the existing geometry plan, bounds, tessellation, coverage, clip, AA and final blend. Replace only the material rejection/extraction with authenticated `MaterialPlanRef` V6 transport into the common source stage.
- [ ] **Step 5 — Remove point-local solid authority.** `W5bPreparedPointBridgeV3` transports the plan reference and binding owner; it does not decode `SolidColor`, multiply alpha, or choose blend. Preserve all existing point modes and draw ordering.
- [ ] **Step 6 — Keep publication and ownership transactional.** Ensure every geometry sibling completes preflight before any runtime pipeline/resource lease. Reuse the Task 3 layout/program/cache identities and frame owners.
- [ ] **Step 7 — Run focused GREEN and historical point gate.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix
  rtk proxy ./gradlew :gpu-plan:compileKotlin
  rtk proxy ./gradlew :gpu-renderer:compileKotlin
  rtk proxy ./gradlew :kanvas:compileTestKotlin
  ```

- [ ] **Step 8 — Refactor and commit.** Delete material-only branch code made unreachable in these lanes, retain W8 legacy routes still used elsewhere, rerun the new class if source changed, and commit with `feat(gpu): promote geometry h lanes to material v6`.

### Task 4 acceptance

- RRect, stroke/hairline and Point(s) render the exact same V6 runtime source as Rect.
- All 14 Task 4 H cells have named public alpha/mutation/blend witnesses and complete family invocation coverage.
- Geometry/coverage semantics and AA4 boundaries are unchanged.
- All 45 historical DrawPoint cases retain their result.
- No lane-local material descriptor, solid extraction, alpha or blend authority remains on promoted paths.

---

## Task 5: Promote Pre-Resolved Text, Vertices, and Mesh

**Outcome:** Public pre-resolved text and Vertices/Mesh use V6 paint material while retaining their atlas/vertex/mesh geometry and internal vertex-color composition semantics.

**Files — Create:**

- `TEST/surface/W5hTextVerticesSurfacePixelTest.kt`

**Files — Modify:**

- `GPU/materials/GPUPreparedTextMaterialPlanProvenance.kt`
- `GPU/materials/GPUPreparedVerticesMaterialPlanProvenance.kt`
- `GPU/wgsl/GPUPreparedTextShaderComposer.kt`
- `GPU/wgsl/PreparedVerticesShader.kt`
- `GPU/payloads/GPUPreparedVerticesPayload.kt`
- `GPU/payloads/PayloadContracts.kt`
- `GPU/commands/NormalizedDrawCommand.kt`
- `API/surface/gpu/W5aPreparedTextMaterialBridge.kt`
- `API/surface/gpu/W5aPreparedVerticesMaterialBridge.kt`
- `API/surface/gpu/GPUPreparedTextSemanticBuilder.kt`
- `API/surface/gpu/GPUPreparedTextFramePreparer.kt`
- `API/surface/gpu/GPUPreparedTextLowerer.kt`
- `API/surface/gpu/PreparedTextFrameInventory.kt`
- `API/surface/gpu/GPUPreparedVerticesSemanticBuilder.kt`
- `API/surface/gpu/GPUPreparedVerticesFramePreparer.kt`
- `API/surface/gpu/GPUPreparedVerticesLowerer.kt`
- `API/surface/gpu/PreparedVerticesFrameInventory.kt`
- `API/surface/gpu/GPUOpMapper.kt`
- `API/surface/gpu/GPUMaterialMapper.kt`
- `API/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- `GPU/execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`
- `GPU/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt`

`MeshProgram` legacy runtime resolver files are modified only to prevent them granting W5 capability; positive mesh paint material still comes from the common V6 plan.

### Semantic contract

- `TextBlob` supplies already resolved glyph IDs/positions. Existing A8 atlas output is coverage only: `source = materialV6 * atlasCoverage`.
- Use the existing `GPUPreparedTextTestFixtures.colrFontBytesWithForegroundLayer()` in-memory synthetic font with `A8_GLYPH_ID`, `KanvasGlyphRun` positions and `FontTypeface`, as in `W5aMaterialSurfacePixelTest`'s `public prepared A8 text applies nested shader opacity and Paint alpha once`. The existing rasterization may execute to produce A8 coverage. No font lookup/discovery, shaping, generation implementation, glyph/font assertion or dedicated font gate is added or modified; only material pixels at established interior-coverage coordinates are asserted.
- Vertices/Mesh retains vertex color and `operationBlendMode` inside source evaluation, then applies paint alpha/filter and final draw blend in the existing global order.
- A `MeshProgram` runtime effect is executable only if it carries the exact positive triplet and its semantic kind is catalogued for that role. The current W5h catalogue contains only the SHADER `child-opacity`; legacy mesh resolver hits therefore remain refused rather than being promoted accidentally.
- Text/vertices provenance stores the V6 `MaterialPlanRef`, table/program identity, binding/layout hash and existing resource owner. It does not reconstruct a material program or copy dynamic values into code identity.

### Public test matrix

- Pre-resolved `TextBlob` with deterministic glyph IDs/positions from existing public fixtures, alpha `0.5`, post-capture mutation, repeat render and nontrivial final blend.
- Public `Vertices` without texture plus runtime material; with vertex colors and noncommutative `operationBlendMode`; mutation and recovery.
- Public textured `Vertices`/Mesh case already backed by in-memory pixels, with runtime material and final blend. No external decoder.
- Public legacy `MeshProgram` runtime attempt refuses as unregistered semantics, followed by a valid V6 vertices draw on the same surface.
- Existing W5a/W5b text and vertices public pixel controls remain green.
- Implement all 12 Task 5 ledger methods for gradients, addressing, ImageSample, filters, Blend/Noise and runtime on both Text and Vertices/Mesh, with the per-invocation alpha/mutation/blend contract and independent family oracles.

### TDD and implementation steps

- [ ] **Step 1 — Write pre-resolved Text RED.** Build `TextBlob` with the existing synthetic `FontTypeface` fixture above. Implement the six `TV.text*CaptureBlend` ledger methods for all six families, each with nontrivial alpha, caller-input mutation after capture and nontrivial final blend. Rasterization is a coverage prerequisite, not a font gate or assertion target.
- [ ] **Step 2 — Write Vertices/Mesh RED.** Implement the six `TV.verticesMesh*CaptureBlend` ledger methods with all family invocations, no-texture vertices, vertex colors with noncommutative operation blend and in-memory textured routes. Add public legacy `MeshProgram` refusal/recovery without internal resolver assertions.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hTextVerticesSurfacePixelTest
  ```

  Confirm text/vertices fail at the W5a-only provenance boundary and not in fixture construction.
- [ ] **Step 4 — Generalize provenance and bridges.** Make existing provenance version-neutral and authenticate V6 against the same table/layout owner. Replace W5a-only material bridges with common-plan references; carry authenticated runtime uniforms and existing child-family resource owners, with no runtime-owned resource path.
- [ ] **Step 5 — Compose source in the existing shader stages.** Text multiplies A8 coverage after material evaluation. Vertices/Mesh composes vertex colors/operation blend in its existing source position, then uses the common final blend. Remove text/vertices local material source generation from promoted routes.
- [ ] **Step 6 — Quarantine MeshProgram legacy resolution.** Require a positive catalogued triplet for W5 admission. Since no W5h mesh semantic exists, existing legacy programs remain on their historical non-W5 path or refuse; they cannot borrow the shader built-in's capability.
- [ ] **Step 7 — Run focused GREEN and bounded prior controls.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hTextVerticesSurfacePixelTest
  rtk proxy ./gradlew :gpu-renderer:compileKotlin
  rtk proxy ./gradlew :kanvas:compileTestKotlin
  ```
- [ ] **Step 8 — Refactor and commit.** Remove W5a-only provenance/bridge branches made unreachable for promoted routes, preserve other legacy consumers, rerun the new public class after source edits, and commit with `feat(gpu): promote text vertices and mesh material lanes`.

### Task 5 acceptance

- Pre-resolved Text and Vertices/Mesh consume V6 without local material authority.
- All 12 Task 5 H cells have named public alpha/mutation/blend witnesses and complete family invocation coverage.
- Text atlas remains coverage-only; no font/shaping/glyph implementation or dedicated font assertion changes.
- Vertex color/operation blend remains distinct from final draw blend.
- Legacy MeshProgram resolution cannot grant positive runtime semantics.

---

## Task 6: Promote A8/RGBA Image Origins and Common Final Blend

**Outcome:** Direct in-memory image origins follow the common material/blend order: RGBA uses paint alpha only, A8 uses mask × V6 material, and both use the existing final `BlendPlan` including no-op and destination-read modes.

**Files — Create:**

- `TEST/surface/W5hImageOriginSurfacePixelTest.kt`

**Files — Modify:**

- `PLAN/EffectiveMaterialPlanner.kt`
- `PLAN/W5eImagePlanCompiler.kt`
- `PLAN/MaterialSourceConstructionV4.kt`
- `PLAN/FrameSourceLayoutV4.kt`
- `PLAN/ColorOperationGraphV1.kt`
- `PLAN/W5bDestinationGraph.kt`
- `GPU/materials/W5aMaterialSourceStage.kt`
- `GPU/materials/W5fColorOperationEmitterV1.kt`
- `GPU/passes/W5ePreparedFrameWitnessV1.kt`
- `GPU/execution/GPUW5eImageNativeV1.kt`
- `API/surface/gpu/GPUPreparedDrawImageLowerer.kt`
- `API/surface/gpu/GPUOpMapper.kt`
- `API/surface/gpu/GPUMaterialMapper.kt`

### Image-origin contract

The exact source order is:

```text
RGBA: decode/sample image -> paint alpha -> external color filter once -> final BlendPlan -> coverage/clip
A8:   evaluate V6 paint material -> multiply image mask -> paint alpha if not already represented
      -> external color filter once -> final BlendPlan -> coverage/clip
```

- RGBA never evaluates the paint shader; a runtime shader on an RGBA-origin paint is not a hidden colorizer.
- A8 evaluates the runtime paint material once and multiplies only the mask channel.
- Remove direct-lowerer tint and `SRC_OVER` restriction as material/blend authorities; use the same plan/table/source stage/final blend as other lanes.
- `NoOp` and destination-read modes retain the existing ordering and target-snapshot authority.
- Pixels come only from public in-memory image construction. No external codec changes or tests.
- A shared exact `ImageResourceSnapshot.Pixels` owner reuses one upload/request/accounting row across ordinary image shader, composed material and direct origin. Two independently captured images remain two admission reservations even if content-equal. A device cache may share canonical storage but retains one lease per consumer.

### Public test matrix

| Origin | Cases |
| --- | --- |
| RGBA | non-opaque pixels with paint alpha `0.5`; shader present but demonstrably not evaluated; `SRC_OVER`, `SRC_IN`, `DIFFERENCE`, no-op/destination-preserving mode; mutation and repeat render |
| A8 | solid child and `child-opacity` shader child at alpha `0.5`; mask values 0/partial/255; external filter once; same final blend modes; mutation and recovery |
| ownership | same owner used in direct image + image shader + V6 child in one frame; two independent equal-content owners; bounded frame budget refusal followed by recovery |

Expected pixels are computed independently from decoded in-memory byte values, premultiplication, mask multiplication, filter and blend formulas.

Task 6 additionally owns the seven exact ledger methods H01/H02/H03/H07/H11/H27/H33. A8 must run the four-gradient, addressing, Blend and both Noise sets as V6 paint families in addition to the runtime case. RGBA closes alpha and final blend only; it does not masquerade as shader-family coverage.

### TDD and implementation steps

- [ ] **Step 1 — Write RGBA/A8 RED.** Implement the seven named Task 6 ledger methods and every family invocation, with nontrivial alpha, post-capture mutation and nontrivial final blend. Add small in-memory image controls from the origin matrix and an RGBA case whose runtime child would visibly alter color if incorrectly evaluated.
- [ ] **Step 2 — Write ownership/budget RED.** Build one public frame sharing the same immutable image owner across direct/source uses and another with two independently constructed equal-content images. Use an already exposed public budget boundary; assert the bounded failure diagnostic and same-surface recovery without observing cache identity or counters.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hImageOriginSurfacePixelTest
  ```

  Confirm failures arise from current tint/`SRC_OVER`/A8 source barriers.
- [ ] **Step 4 — Route origins through common composition.** Extend the existing image plan to select RGBA alpha-only or A8 mask-material composition, use V6 for the A8 material, preserve source/filter/final-blend/coverage order, and publish only after frame-wide validation.
- [ ] **Step 5 — Remove local image material authority.** Delete promoted-path tint computation and local `SRC_OVER` gate from `GPUPreparedDrawImageLowerer`; retain only geometry/sample facts. Resolve final blend through the common destination graph and source stage.
- [ ] **Step 6 — Preserve owner-aware accounting.** Reuse the W5g owner inventory for an identical captured owner across routes, keep independent owners separate for admission, start cache keys with device generation, and retain a lease for every consumer until completion.
- [ ] **Step 7 — Run focused GREEN and W5e public controls.** Run serially:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hImageOriginSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5eDecodedImageSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageFamiliesSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5eImageConvergenceSurfaceTest
  rtk proxy ./gradlew :gpu-plan:compileKotlin
  rtk proxy ./gradlew :gpu-renderer:compileKotlin
  rtk proxy ./gradlew :kanvas:compileTestKotlin
  ```

- [ ] **Step 8 — Refactor and commit.** Remove newly dead direct-image material branches only, preserve external codec boundaries untouched, rerun the new public class after source edits, and commit with `feat(gpu): unify image origins with material v6`.

### Task 6 acceptance

- RGBA and A8 obey the exact source/filter/blend/coverage order.
- All seven Task 6 H cells have named public alpha/mutation/blend witnesses and complete family invocation coverage.
- RGBA does not evaluate the paint shader; A8 evaluates V6 exactly once.
- Direct image tint and `SRC_OVER` are no longer parallel authorities.
- Public ownership/budget behavior preserves shared-owner and independent-owner contracts.

---

## Task 7: W5h Convergence, Transition Cleanup, Tracking, and Stacked Draft PR

**Outcome:** All H cells and prior W5 families pass one frozen-source public covering; promoted routes have no parallel material authority; durable docs state remaining integration gaps precisely; a single stacked Draft PR is published.

**Files — Create:**

- `TEST/surface/W5hConvergenceSurfacePixelTest.kt`

**Files — Modify:**

- only production files proven by static call-graph review to contain dead promoted-path branches
- `refactor/waves/W05-material-graph/status.md`
- `refactor/README.md`
- this plan's checkbox and final-checkpoint sections

**Files — Delete:** none planned. Remove dead branches inside listed owners; retain compatibility files for W8 unless a Sol review identifies an exact file with no remaining caller or transition role.

Do not delete historical fixtures, approved design/plan docs, active W8 transition hooks, external codec code, font code, GM data or prior-wave status evidence.

### Frozen-source covering

The final command is one serial Gradle invocation over these public classes, using one immutable source commit recorded before execution:

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest \
  --tests org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest \
  --tests org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5hGeometryHLaneSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5hTextVerticesSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5hImageOriginSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5hConvergenceSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest.drawPointHistoricalW5bMatrix \
  --tests org.graphiks.kanvas.surface.W5aMaterialSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5bBlendSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5cGradientSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5dGradientAddressingSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5eDecodedImageSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5eImageFamiliesSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5eImageShaderSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5eImageConvergenceSurfaceTest \
  --tests org.graphiks.kanvas.surface.W5fColorFilterSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5fFilterOrderingSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5fGradientInterpolationSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5fImageFilterSurfacePixelTest \
  --tests org.graphiks.kanvas.surface.W5fConvergenceSurfacePixelTest \
  --tests org.graphiks.kanvas.picture.W5fPictureFilterInterpolationTest \
  --tests org.graphiks.kanvas.surface.W5gComposedMaterialSurfacePixelTest \
  --tests org.graphiks.kanvas.picture.W5gComposedMaterialPictureTest \
  --tests org.graphiks.kanvas.surface.W5gNoiseSurfacePixelTest \
  --tests org.graphiks.kanvas.picture.W5gNoisePictureCompatibilityTest \
  --tests org.graphiks.kanvas.surface.W5gConvergenceSurfacePixelTest \
  --rerun-tasks --no-parallel --console=plain
```

Before running, verify every class name exists with `rtk rg --files kanvas/src/test/kotlin`. Do not add an infrastructure wrapper suite.

Then run five separate incremental compiles, still serially:

```bash
rtk proxy ./gradlew :render-ir:compileKotlin
rtk proxy ./gradlew :gpu-plan:compileKotlin
rtk proxy ./gradlew :gpu-renderer:compileKotlin
rtk proxy ./gradlew :kanvas:compileKotlin
rtk proxy ./gradlew :kanvas:compileTestKotlin
```

### Convergence test contract

`W5hConvergenceSurfacePixelTest` is not a suite runner. It adds only cross-lane public behaviors not owned by a prior class:

- one frame containing Rect, RRect, Path fill, stroke, hairline, Point(s), pre-resolved Text, Vertices, A8 and RGBA origins with the same captured runtime owner;
- a second frame with independent equal-valued owners proving admission does not collapse their budgets;
- one mixed prior-family frame containing gradient, image shader, color filter, W5g Blend and Noise under `child-opacity`;
- one late sibling refusal proving no partial frame publication, followed by valid same-surface recovery;
- Picture capture/replay of a mixed promoted-lane sequence where the public Picture model supports that operation.

### TDD, review, and publication steps

- [ ] **Step 1 — Run the convergence baseline.** Add only the five cross-lane behaviors above and run on unchanged Task 6 production. If GREEN, retain the result and map every behavior to its causal RED/GREEN evidence in its owning Tasks 1–6; no new defect or redundant RED is required. If a new observable split owner/publication/material failure appears, retain its causal RED. Missing earlier causal evidence is a coverage gap to document and resolve against that behavior's owning-task source revision, not a reason to alter correct production artificially.
- [ ] **Step 2 — Close demonstrated convergence gaps in existing owners.** Fix only a common plan, transport, inventory or ownership owner responsible for an observed RED. When the baseline is already GREEN and prior causal evidence is complete, no production correction is required. Do not add a convergence-only route or widen diagnostics/tolerances.
- [ ] **Step 3 — Run convergence GREEN.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hConvergenceSurfacePixelTest
  ```

- [ ] **Step 4 — Perform static architecture cleanup.** Trace callers of `GPUMaterialMapper`, W5a-only text/vertices provenance, legacy runtime child authority/resolvers, direct-image tint/`SRC_OVER`, and text/vertices/mesh local material program builders. Remove only branches/files unreachable from all remaining legitimate routes. Record remaining legacy files and their W8 consumer in the task report. This review is static; do not create code-shape tests.
- [ ] **Step 5 — Run the frozen-source covering.** Record HEAD and the hashes of every production source file before the command. Run the exact covering command once, inspect all JUnit XML, and record registered/pass/failure/error/skip counts. If Gradle ends with native exit 133 after XML is complete, report cause `UNKNOWN`; do not label the native run green.
- [ ] **Step 6 — Run the five compiles.** Execute each listed compile separately and record its exit code. No global suite, GM, dashboard, render regeneration, font suite, codec suite or `jpg-color-cube` run is permitted.
- [ ] **Step 7 — Update durable tracking.** In `refactor/waves/W05-material-graph/status.md`, transcribe all H01–H33 ledger results with exact class/method/invocations and alpha/mutation/final-blend evidence, count Task 4's 14 + Task 5's 12 + Task 6's 7 cells separately from public test invocation counts, and record causal RED/GREEN source custody. Report required/closed/missing cells, compile results, review verdicts, commit IDs, native status, deleted static files and remaining W8/device-loss or non-public mismatch gaps. Update `refactor/README.md` with the W5h branch/PR state. Add a concise final checkpoint at the top of this plan and check completed boxes; do not create another status markdown.
- [ ] **Step 8 — Commit Task 7.** Commit production/test convergence with `feat(gpu): close w5 registered runtime effect lanes`, then commit tracking-only changes with `docs(refactor): record w5h closure` if the task review requires a stable production commit before docs.
- [ ] **Step 9 — Obtain whole-branch Sol review.** Review the complete diff from `da9b367bd` to W5h HEAD against the approved design and this plan. Required verdict: no Critical or Important findings, exact module authority, no H-lane parallel material route, no forbidden tests/scope, and explicit Minor/integration gaps.
- [ ] **Step 10 — Apply one bounded correction wave if required.** A single non-Sol implementer fixes all accepted Critical/Important findings with RED/GREEN evidence where behavior changes. The same Sol reviewer performs one scoped re-review. Do not restart the review loop for nonblocking stylistic observations.
- [ ] **Step 11 — Push and publish one stacked Draft PR.** Push `codex/w5h-registered-runtime-effects`, create or update one Draft PR targeting `codex/w5g-composed-procedural-materials`, and include: architecture summary, seven-lot commit map, public test counts, five compile results, exact native-exit disclosure, excluded suites, transition-v0/W8 note, device-loss integration gap, final Sol verdict, and parent PR link. Do not merge either PR.

### Task 7 and W5h acceptance

- All 33 H cells in the exhaustive ledger render their actual W5 family through V6 and the common source/blend path, with alpha, post-capture mutation and final-blend evidence. The six runtime H cells render the exact built-in; previous-family cells retain their independent family semantics and full invocation sets.
- `compile(wgsl)` and all historical v0 descriptors refuse before `Ready` with `unsupported.material.runtime_effect.unregistered_semantics`.
- Descriptor, catalogue, CPU evaluator, renderer manifest, numeric graph, `abiHash` and `composedBindingLayoutHash` agree at their specified authority boundaries.
- Dynamic values do not enter program identity; physical layout does not enter logical ABI identity.
- Uniform, binding, resource and frame budgets are checked before copy/publication/native ownership.
- Cache keys begin with device generation; pessimistic admission, lease lifetime, zero-lease LRU eviction and generation invalidation remain enforced by production design and public reachable behavior.
- The common owner is preserved across lanes; stop/image sharing policies remain family-correct.
- Public focused/frozen-source gates have no new failure/error. Historical AA4 skips remain explicitly counted rather than relabeled.
- No font, external codec, GM, dashboard, baseline, score, `jpg-color-cube`, global suite, fake device or infrastructure test is used as evidence.
- Remaining non-injectable device-loss/native-limit proof is tracked as an integration gap, not closed by mocks.
- Final Sol review has zero Critical and zero Important findings, and the Draft PR is stacked directly on W5g.

---

## Plan Self-Review Checklist

| Design authority | Plan coverage |
| --- | --- |
| §§1–4 objective, scope and selected architecture | Goal, Global Constraints, seven sequential tasks |
| §5 module boundaries | Actual Code Map; Tasks 1 and 3 |
| §§6–7 ABI, two hashes and initial built-in | Tasks 1 and 3 |
| §8 public API and v0 transition | Tasks 1, 2 and 7 |
| §9 Picture/SceneArchive | Task 2 |
| §10 V6 data flow | Task 3 and all later lane tasks |
| §11 H-lane matrix | Tasks 3–6 and convergence Task 7 |
| §12 diagnostics, budgets, recovery and ownership | Tasks 3, 4, 6 and 7 |
| §13 public-only test strategy | Global Constraints and every task's TDD section |
| §§14–15 sequencing and W8 cleanup | Tasks 1–7 and Task 7 static review |
| §16 closure criteria | Task 7 acceptance and frozen-source covering |

- [x] Every approved design section (§§1–16) maps to at least one task or global constraint.
- [x] Every new type has one named module owner and uses I32/I64/F32/F64 suffixes where numeric width is part of the contract.
- [x] Every new behavior has a causal public RED in its owning task, focused GREEN, exact command, bounded compiles, commit, Sol review and acceptance criteria; Task 7 may retain a GREEN baseline with complete earlier causal evidence.
- [x] No test asserts private/internal structure, reflection, counters, cache identity, fake capability/device behavior or infrastructure shape.
- [x] Picture v11 fixture capture precedes the v12 writer edit and leaves no committed producer.
- [x] V6 extends the existing material table/DAG/source stage and does not create a parallel compiler/materializer.
- [x] H-lane promotion order is Rect/Path fill; RRect/stroke/points; Text/Vertices/Mesh; image origins; convergence.
- [x] H01–H33 cover every §16.1 H obligation with exact Tasks 4–6 method ownership and mandatory alpha/mutation/final-blend witnesses; Task 7 counts cells independently of methods/invocations.
- [x] External codecs, fonts, GMs, dashboards, renders, baselines, scores, `jpg-color-cube`, Skia integration and global suites are excluded.
- [x] Version-zero compatibility is inert and W8 removal is tracked.
- [x] One stacked Draft PR targets W5g and no merge is included.
