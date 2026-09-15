# W5h Registered Runtime Effects and H-Lane Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the registered `child-opacity` runtime effect through the common W5 material authority, promote every applicable H lane, preserve Picture compatibility, and close W5 with public pixel evidence.

**Architecture:** Extend the existing backend-neutral descriptor, semantic plan catalogue, V5 material DAG, common source stage, frame inventory, and native ownership path into V6. Positive runtime effects are authenticated by `(RuntimeEffectId, semanticVersionI32, abiHash)` before `Ready`; the renderer only verifies its manifest and emits the numeric graph already sealed by `:gpu-plan`. Historical version-zero effects remain decodable but inert.

**Tech Stack:** Kotlin/JVM; `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`; WebGPU/WGSL; SHA-256; JUnit 5; public `Surface`, `Canvas`, `Picture`, `TextBlob`, `Vertices`, and `Mesh` APIs.

**Spec:** `refactor/specs/2026-09-15-w5h-registered-runtime-effects-design.md`

## Global Constraints

- Base branch: `codex/w5g-composed-procedural-materials` at `da9b367bd`; implementation branch: `codex/w5h-registered-runtime-effects`. The single Draft PR targets the W5g branch directly.
- Read the complete approved spec before each task. If code reality conflicts with it, stop that task and report the exact conflict; do not silently change the architecture.
- Use RED -> GREEN -> refactor for every behavior. A RED must fail for the intended public behavior on unchanged production, not for a fixture, build, permission, or harness problem.
- Tests may observe only public API behavior, rendered pixels, public refusal diagnostics, public Picture bytes/playback, and recovery. Do not add tests for private/internal code, source shape, reflection, ABI layout through internals, counters, cache handles, call counts, mocks, fake devices, injected capabilities, or code infrastructure.
- Compute expected pixels independently before creating a `Surface` or `PictureRecorder`. Accept a singleton byte or exactly two adjacent bytes justified by the existing `WgslFloatEnvelopeV1` oracle. Do not introduce similarity thresholds.
- New geometry and transform value objects belong only in `:math`, with I32/I64/F32/F64 nomenclature. W5h should not need a new geometry type; reuse the existing math types.
- Fonts, glyph generation/shaping, external image codecs, Skia GMs, dashboards, renders, baselines, scores, `jpg-color-cube`, `:integration-tests:skia`, and global suites are outside every gate.
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
| `IR/SceneArchiveCodec.kt` | Write scene schema 6/Picture 12 and read schemas 8–12. Adapt all v2 runtime descriptors to inert v3 version zero through one path. |
| `API/pipeline/RuntimeEffect.kt`, `RuntimeEffectWgsl4kWiring.kt`, `ShaderModule.kt` | Keep legacy compilation isolated and expose exact positive lookup `registered(id, semanticVersionI32)`. Do not expose application registration of positive entries. |
| `API/render/ir/PaintSceneAdapter.kt` | Capture positive descriptors without WGSL; reconstruct positive built-ins by exact version; reconstruct old archives as detached/inert v0 only. Preserve post-capture immutability and child order. |
| `PLAN/NumericOperationGraphV1.kt`, `ColorOperationGraphV1.kt` | Carry the backend-neutral runtime operation `scaleAlpha(child, alpha)` in the existing numeric/color graph authority. |
| `PLAN/ComposedMaterialPlanV5.kt`, `MaterialPlan.kt` | Extend the existing composed DAG/layout/table to V6. Keep historical V1–V5 readable but never select them for a newly promoted W5h lane. |
| `PLAN/MaterialSourceConstructionV4.kt`, `FrameSourceLayoutV4.kt`, `MaterialSourceFootprintV4.kt`, `RawMaterialRequirementsV2.kt` | Resolve the semantic catalogue, validate descriptor/children/uniforms/resources, seal owners and physical layout, and account before publication. |
| `PLAN/CapabilityCompilerChain.kt`, `SourceDeferredRenderConstructionV4.kt`, `RenderGraph.kt`, `RenderGraphConstruction.kt`, `PlanPasses.kt` | Transport the same V6 authority through every selected lane and keep frame publication transactional. |
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
- `PLAN/ComposedMaterialPlanV6.kt`: V6 program/binding expectations and runtime node data added to the existing composed table.
- `PLAN/W5hPlanDiagnostics.kt`: exact W5h refusal codes and classifiers.
- `GPU/runtimeeffects/W5hRuntimeEffectManifest.kt`: positive renderer manifest and exact triplet lookup.
- `GPU/materials/W5hRuntimeEffectWgslEmitter.kt`: deterministic emission from the sealed numeric graph only.
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
- `PLAN/W5hPlanDiagnostics.kt`
- `TEST/pipeline/W5hRuntimeEffectCatalogTest.kt`

**Files — Modify:**

- `IR/ResourceSnapshot.kt`
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

public class RuntimeUniformBlockV1 /* ordered slots, sizeBytesI32 aligned to 16 */

public data class RuntimeChildSlotV2(
    val name: String,
    val type: RuntimeChildType,
    val nullable: Boolean,
)

public enum class RuntimeLogicalResourceKindV1 {
    STORAGE_BUFFER, SAMPLED_TEXTURE, SAMPLER
}

public class RuntimeEffectDescriptor /* canonical descriptor versionI32 == 3 */
```

Preserve the old slot/module facts under an explicit legacy-v0 value rather than mixing `binding` into the positive logical ABI. Arrays, comparison samplers, storage textures, hidden samplers, and non-2D/non-filterable texture facts are rejected by construction.

`RuntimeEffectSemanticCatalogSnapshot` is immutable after construction. Exact lookup requires the complete triplet after descriptor hash verification; `(id, version)` lookup exists only to construct the public built-in object. Duplicate exact keys with unequal facts and duplicate `(id, version)` with different `abiHash` fail snapshot construction.

### TDD and implementation steps

- [ ] **Step 1 — Write the public RED.** In `W5hRuntimeEffectCatalogTest`, assert that `registered("kanvas.runtime.child-opacity", 1)` returns a stable positive effect; unknown ID, version 0, version 2, and negative version return `null`; the one-argument `registered(id)` cannot select the positive effect; `compile(wgsl)` still yields version zero. Through public descriptor access, assert the exact ordered child/uniform facts, absence of WGSL/resources, and exact known ABI hash literal computed independently from the spec bytes.
- [ ] **Step 2 — Prove the RED is causal.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.pipeline.W5hRuntimeEffectCatalogTest
  ```

  Record the failing methods and XML path in the SDD report. Existing production must fail because the versioned lookup/descriptor v3 is absent.
- [ ] **Step 3 — Add canonical values and hash recipes.** Implement `CanonicalHashBytesV1`, the V2 slot/block/resource values, descriptor-v3 validation, both hash domains, and exhaustive numeric tags from spec §§6 and 9. Keep old public type names as deprecated adapters when source compatibility requires them; positive construction must use only the new logical fields.
- [ ] **Step 4 — Add the immutable semantic catalogue.** Implement the exact-key value, `RuntimeEffectSemanticEntryV1`, immutable snapshot validation, finite graph/capture/resource limits, numeric contract ID, CPU evaluator ID/version and the sole `child-opacity` entry. Reuse `NumericOperationGraphV1`; add only the typed runtime operation needed for `scaleAlpha`.
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
- Readers accept historical Picture/scene versions `8`, `9`, `10`, `11` and current `12` through explicit branches.
- A positive v3 descriptor writes `semanticVersionI32`, `abiHash`, logical uniform block facts, ordered child nullability, logical resource facts and no module.
- A v0 descriptor writes the legacy section only. It stores the v0 SHA-256 in `RuntimeEffectDescriptor.abiHash` but that hash grants no W5 capability.
- Every v2 reader feeds one v2-to-v3 adapter using the exact align/size table from spec §6. Historical children become `nullable=false`; resources are empty; version is zero.
- Positive-with-module, zero-pretending-positive, negative-version, malformed-hash, overflow, invalid count and truncated input are rejected before registration.
- Archive validation is transactional. A rejected archive installs no legacy descriptors. A valid old archive may reconstruct detached v0 objects, but no positive lookup or renderer lookup can observe them.

### TDD and implementation steps

- [ ] **Step 1 — Capture a genuine v11 fixture before writer edits.** Add a disposable public JUnit producer that records one `compile(wgsl)` shader with one uniform and one child, serializes it with the current Picture 11 writer and prints Base64. Run only that producer, copy the exact output into `format-11-runtime-effect-v0.base64` with `apply_patch`, then remove the producer before any production edit. Record base commit `983c4f1e6`, the public construction input and the decoded byte SHA-256 in the SDD report. The producer is not a committed test or gate.
- [ ] **Step 2 — Write Picture RED tests.** Through public `PictureRecorder`/`Picture.encode`/`Picture.decode`/`Picture.playback`, cover positive `child-opacity` with alpha `0.5`, child order, mutation after capture, round-trip bytes, replay twice, and the stored v11 fixture. Assert the v11 effect decodes but rendering refuses with `unsupported.material.runtime_effect.unregistered_semantics`, then a valid positive picture renders on the same surface.
- [ ] **Step 3 — Add malformed public byte cases.** Starting from bytes produced by the public writer, mutate only the version/hash/module/count fields identified by a small test-local parser. Cover negative semantic version, non-lowercase/short hash, positive descriptor with module, truncated uniform list and overflowing count. Assert `Picture.decode` rejects and no later positive lookup or rendering behavior changes.
- [ ] **Step 4 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.picture.W5hRuntimeEffectPictureTest
  ```

  The positive round trip must fail on Picture 11/schema 5 and the historical-v0 refusal must fail because current reconstruction still registers by ID.
- [ ] **Step 5 — Implement v12/schema6 and the common adapter.** Change writer/reader constants, encode v3 exhaustively, route v8–v11 runtime descriptors through one checked v2-to-v3 adapter, and keep all other historical scene fields on their existing readers. Do not reinterpret unrelated Picture fields.
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
- `GPU/materials/W5aFrameMaterialBudgetV2.kt`
- `GPU/planning/W4aAnalyticRectGraphLowerer.kt`
- `GPU/planning/W4cPathFillGraphLowerer.kt`
- `GPU/planning/W4dGeneralPathGraphLowerer.kt`
- `GPU/planning/GpuPlanTaskListLowerer.kt`
- `GPU/passes/W5aMaterialPlanAuthorityV2.kt`
- `GPU/passes/GPUPlanW4dGeneralPreparedAuthority.kt`
- `API/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- `API/surface/gpu/W5dGradientCandidateV2.kt`

If current compilation names a direct V5 transport/seal owner omitted above, classify it in the SDD conflict table and modify that existing owner; do not add another route.

### V6 contract

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

The V6 layout extends `ComposedBindingLayoutV1` rather than replacing it. Uniform node bases are 16-byte aligned; local F32 fields keep 4-byte alignment. Resources follow the uniform binding in owner-prefix order. `composedBindingLayoutHash` keeps domain `kanvas-material-binding-layout-v1`, includes the exact uniform binding/mappings/resource options from spec §6, and enters the program key after `deviceGeneration`.

The renderer receives no semantic catalogue object. It matches the positive manifest against the sealed expectation, emits `scaleAlpha` from the sealed numeric graph, assembles the common source stage, parses/reflects the assembled module against the sealed layout, then creates pipeline/resources. Hand-written W5h WGSL and manifest-driven semantic reconstruction are forbidden.

### Public test matrix

Use a non-white, non-opaque child color so channel and premultiplication errors are visible. Expected values come from `W5hRuntimeEffectCpuOracle`, which re-implements only `out = child * alpha` in linear premultiplied space and final attachment conversion; it must not import plan or renderer graph classes.

| Behavior | Required public witness |
| --- | --- |
| execution | Rect, direct Path fill and stencil Path fill with alpha `1.0` and `0.5` |
| ordering | nested child opacity with distinct inner/outer alpha and a noncommutative W5g blend child |
| immutability | mutate caller uniform bytes and child map after capture; original render and Picture replay remain unchanged |
| identity | unknown triplet, wrong version and v0 `compile(wgsl)` refuse with `unsupported.material.runtime_effect.unregistered_semantics` |
| ABI/values | missing/extra/wrong child, malformed/missing/extra/non-finite/out-of-range uniform refuse with their classified W5h diagnostics |
| budgets | public graph depth/node count and uniform byte limit refuse before render publication; immediately render a valid draw on the same surface |
| prior W5 | one solid, gradient, image shader, color filter, W5g blend and Noise control remain byte-exact |

No test fabricates an internal manifest/reflection/capability mismatch. Those boundaries are verified statically by Sol review; any naturally reachable public mismatch is added as public evidence.

### TDD and implementation steps

- [ ] **Step 1 — Write independent oracle and execution RED.** Add Rect and both Path-fill route cases for alpha `1.0`/`0.5`, nested ordering, mutation after capture, repeat render and one Picture replay. Assert the exact bytes or two adjacent oracle bytes.
- [ ] **Step 2 — Write refusal/recovery RED.** Add public unknown/version-zero, child, uniform, depth/node/byte-budget refusals with the exact diagnostic category from `W5hPlanDiagnostics`; after each refusal, render a positive built-in on the same `Surface`.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hRuntimeEffectSurfacePixelTest
  ```

  Record failing method names and confirm the first positive render is refused by the current W5g runtime slice, not by the fixture.
- [ ] **Step 4 — Extend V5 into V6 in the existing plan.** Add runtime nodes/expectations to the current evaluation DAG and table, resolve the immutable catalogue during source construction, authenticate the descriptor, inline the child, compute checked layout/footprint and issue V6 only after whole-frame validation. Keep V1–V5 compatibility readers but select V6 for every new W5h-ready plan.
- [ ] **Step 5 — Extend numeric proof and emission.** Add one typed `scaleAlpha` operation with the same input/output color domain as the built-in. Prove finite alpha `[0,1]`, preserve the existing `WgslFloatEnvelopeV1` contract, and emit it through `W5fColorOperationEmitterV1`/the common source stage.
- [ ] **Step 6 — Add renderer manifest verification.** Define the sole positive manifest for the exact triplet. Recompute manifest ABI, compare all sealed expectation fields, generate WGSL from the graph, validate reflection/layout, and ensure all checks precede pipeline lookup, lease acquisition, upload or buffer materialization. Keep legacy registry/resolver paths v0-only and unreachable from V6.
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

Every lane also covers post-capture uniform/child mutation and one refusal followed by a valid same-surface recovery. Reuse the Task 3 oracle.

### TDD and implementation steps

- [ ] **Step 1 — Write geometry RED cases.** Add RRect, stroke, hairline and all public point modes using the exact positive built-in, alpha `0.5`, a nontrivial child, mutation after capture and a final blend that exposes source/destination order.
- [ ] **Step 2 — Add historical preservation calls.** Invoke the existing public 45-case `GPUAllApiBlendSurfaceTest` class as a separate gate; do not copy its assertions or weaken its two historical AA4 skips. Add one public W4 RRect and stroke coverage control to the new class only where the current public tests do not already cover it.
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
- No font lookup, shaping, glyph generation, font suite or text-local runtime descriptor enters W5h.
- Vertices/Mesh retains vertex color and `operationBlendMode` inside source evaluation, then applies paint alpha/filter and final draw blend in the existing global order.
- A `MeshProgram` runtime effect is executable only if it carries the exact positive triplet and its semantic kind is catalogued for that role. The current W5h catalogue contains only the SHADER `child-opacity`; legacy mesh resolver hits therefore remain refused rather than being promoted accidentally.
- Text/vertices provenance stores the V6 `MaterialPlanRef`, table/program identity, binding/layout hash and existing resource owner. It does not reconstruct a material program or copy dynamic values into code identity.

### Public test matrix

- Pre-resolved `TextBlob` with deterministic glyph IDs/positions from existing public fixtures, alpha `0.5`, post-capture mutation, repeat render and nontrivial final blend.
- Public `Vertices` without texture plus runtime material; with vertex colors and noncommutative `operationBlendMode`; mutation and recovery.
- Public textured `Vertices`/Mesh case already backed by in-memory pixels, with runtime material and final blend. No external decoder.
- Public legacy `MeshProgram` runtime attempt refuses as unregistered semantics, followed by a valid V6 vertices draw on the same surface.
- Existing W5a/W5b text and vertices public pixel controls remain green.

### TDD and implementation steps

- [ ] **Step 1 — Write pre-resolved Text RED.** Reuse an existing deterministic public `TextBlob` fixture; apply `child-opacity` with alpha `0.5`, mutate inputs after capture, render twice and compare exact oracle bytes. Do not call font discovery, shaping or glyph generation.
- [ ] **Step 2 — Write Vertices/Mesh RED.** Cover no-texture vertices, vertex colors with a noncommutative operation blend, in-memory textured vertices and final blend. Add legacy `MeshProgram` refusal/recovery without asserting internal resolver calls.
- [ ] **Step 3 — Prove RED causality.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hTextVerticesSurfacePixelTest
  ```

  Confirm text/vertices fail at the W5a-only provenance boundary and not in fixture construction.
- [ ] **Step 4 — Generalize provenance and bridges.** Make existing provenance version-neutral and authenticate V6 against the same table/layout owner. Replace W5a-only material bridges with common-plan references while leaving text atlas and vertices resource inventories unchanged except for authenticated runtime uniforms/resources.
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
- Text atlas remains coverage-only and no font work enters the change.
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

### TDD and implementation steps

- [ ] **Step 1 — Write RGBA/A8 RED.** Add the full public matrix above with small in-memory images, exact expected bytes, post-capture mutation and repeat render. Include an RGBA case whose paint runtime child would visibly alter color if incorrectly evaluated.
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

- [ ] **Step 1 — Write convergence RED.** Add only the five cross-lane behaviors above. Run the class on Task 6 production and retain at least one causal failure showing a remaining split owner/publication/material route.
- [ ] **Step 2 — Close convergence in existing owners.** Fix only the common plan, transport, inventory or ownership owner responsible for the RED. Do not add a convergence-only route or widen public diagnostics/tolerances.
- [ ] **Step 3 — Run convergence GREEN.** Run:

  ```bash
  rtk proxy ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.W5hConvergenceSurfacePixelTest
  ```

- [ ] **Step 4 — Perform static architecture cleanup.** Trace callers of `GPUMaterialMapper`, W5a-only text/vertices provenance, legacy runtime child authority/resolvers, direct-image tint/`SRC_OVER`, and text/vertices/mesh local material program builders. Remove only branches/files unreachable from all remaining legitimate routes. Record remaining legacy files and their W8 consumer in the task report. This review is static; do not create code-shape tests.
- [ ] **Step 5 — Run the frozen-source covering.** Record HEAD and the hashes of every production source file before the command. Run the exact covering command once, inspect all JUnit XML, and record registered/pass/failure/error/skip counts. If Gradle ends with native exit 133 after XML is complete, report cause `UNKNOWN`; do not label the native run green.
- [ ] **Step 6 — Run the five compiles.** Execute each listed compile separately and record its exit code. No global suite, GM, dashboard, render regeneration, font suite, codec suite or `jpg-color-cube` run is permitted.
- [ ] **Step 7 — Update durable tracking.** In `refactor/waves/W05-material-graph/status.md`, record exact H-cell closure, public counts, compile results, review verdicts, commit IDs, native status, deleted static files and remaining W8/device-loss gaps. Update `refactor/README.md` with the W5h branch/PR state. Add a concise final checkpoint at the top of this plan and check completed boxes; do not create another status markdown.
- [ ] **Step 8 — Commit Task 7.** Commit production/test convergence with `feat(gpu): close w5 registered runtime effect lanes`, then commit tracking-only changes with `docs(refactor): record w5h closure` if the task review requires a stable production commit before docs.
- [ ] **Step 9 — Obtain whole-branch Sol review.** Review the complete diff from `da9b367bd` to W5h HEAD against the approved design and this plan. Required verdict: no Critical or Important findings, exact module authority, no H-lane parallel material route, no forbidden tests/scope, and explicit Minor/integration gaps.
- [ ] **Step 10 — Apply one bounded correction wave if required.** A single non-Sol implementer fixes all accepted Critical/Important findings with RED/GREEN evidence where behavior changes. The same Sol reviewer performs one scoped re-review. Do not restart the review loop for nonblocking stylistic observations.
- [ ] **Step 11 — Push and publish one stacked Draft PR.** Push `codex/w5h-registered-runtime-effects`, create or update one Draft PR targeting `codex/w5g-composed-procedural-materials`, and include: architecture summary, seven-lot commit map, public test counts, five compile results, exact native-exit disclosure, excluded suites, transition-v0/W8 note, device-loss integration gap, final Sol verdict, and parent PR link. Do not merge either PR.

### Task 7 and W5h acceptance

- All applicable H cells render the registered built-in through V6 and the common source/blend path.
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
- [x] Every task has a causal public RED, focused GREEN, exact command, bounded compile set, commit, Sol review and acceptance criteria.
- [x] No test asserts private/internal structure, reflection, counters, cache identity, fake capability/device behavior or infrastructure shape.
- [x] Picture v11 fixture capture precedes the v12 writer edit and leaves no committed producer.
- [x] V6 extends the existing material table/DAG/source stage and does not create a parallel compiler/materializer.
- [x] H-lane promotion order is Rect/Path fill; RRect/stroke/points; Text/Vertices/Mesh; image origins; convergence.
- [x] External codecs, fonts, GMs, dashboards, renders, baselines, scores, `jpg-color-cube`, Skia integration and global suites are excluded.
- [x] Version-zero compatibility is inert and W8 removal is tracked.
- [x] One stacked Draft PR targets W5g and no merge is included.
