# FP-05 Prepared Text Composite Shader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the isolated solid-only text shader prototype with one
parser-validated, premultiplied-linear WGSL program that composes the common
Task 3 material with exact TextA8 geometry, atlas coverage, frame planning,
preflight and native ownership.

**Architecture:** Task 3 remains the only material compiler and gains a
canonical composable fragment result. A small TextA8 composer combines that
fragment with a 64-byte instanced vertex ABI and A8 coverage. Surface snapshots
the exact device-to-local transform; Tasks 8 and 9 plan and validate the final
program plus draw-uniform slices before Task 10 creates any native resource.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, Java toolchain 25, WGSL, wgsl4k
parser/lowering/reflection, wgpu4k/WebGPU, `kotlin.test`, JUnit Jupiter.

## Global Constraints

- Use
  `docs/superpowers/specs/2026-07-29-fp-05-prepared-text-composite-shader-design.md`
  as the acceptance authority.
- Keep `GPUPreparedMaterialProgramCompiler` as the only material-family
  authority; no text-specific `when (sourceKind)`.
- Every composable material returns linear premultiplied RGBA.
- Apply final `paintAlpha` and A8 coverage to RGBA exactly once.
- Keep the existing 64-byte instance bytes unchanged and bind them as a vertex
  instance buffer, never as storage.
- Preserve exact four-corner affine geometry, UV LTRB and continuous material
  coordinates.
- Build, parse, lower and reflect the final composed WGSL before native
  creation.
- Pipeline keys exclude uniform values, `paintAlpha`, texture contents and
  value-level `materialKey`.
- Keep WebGPU as the only backend; do not port Graphite, Ganesh, SkSL, shader
  dictionaries or multi-backend abstractions.
- Keep ColorGlyph native integration for FP-05 Task 11.
- Keep product router, gate, legacy allowlist and animation unchanged.
- Keep one encoder, one submit and at most one readback.
- Do not freeze the exact number of compatible render passes.
- If wgsl4k or wgpu4k behavior is ambiguous, produce a minimal reproducer and
  report it; do not add a hidden workaround.
- Run every shell command through `rtk`; run Gradle through
  `rtk proxy ./gradlew`.
- Do not stage `.superpowers/sdd/`.

---

## File and Interface Map

### Common material composition

- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/GPUPreparedMaterialFragment.kt`
  - immutable composable WGSL, fixed binding schema, color/coordinate
    contracts and hashes.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/PreparedMaterialContracts.kt`
  - attach the fragment to `GPUPreparedMaterialProgram`.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
  - compile standalone and composable forms from the same prepared source.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt`
  - expose canonical callable gradient source without a stage wrapper.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt`
  - expose canonical callable premultiplied blend source.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/MaterialContracts.kt`
  - make registered runtime-effect source color semantics explicit.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
  - register the current effect with its exact source color contract.

### Text program composition

- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8Shader.kt`
  - fixed vertex/coverage templates only; remove the 32-byte storage ABI and
    fixed material color.
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GPUPreparedTextShaderComposer.kt`
  - final source, binding allocation, reflection, ABI and pipeline key.

### Surface and frame planning

- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
  - immutable six-float `deviceToLocal` snapshot.
- Modify:
  `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt`
  - invert the admitted Surface transform once.
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedTextDrawUniformPlan.kt`
  - frame-global buffer and aligned subrun slices.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
  - compose programs and plan exact draw uniforms.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
  - seal/hash/dump composite and draw-uniform facts.

### Pure preflight

- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextCompositePreflight.kt`
  - focused composite/vertex/draw-uniform validator and refusal constants.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
  - delegate prepared-text composite validation.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
  - retain the Task 10 materialization guard after all pure validation.

### Native materialization

- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextSessionCache.kt`
  - pipeline/layout/sampler cache only.
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`
  - TextA8 uploads, bindings, commands, rollback and completion ownership.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
  - merge TextA8 operands by exact source scope.
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
  - install the text run materializer without opening ColorGlyph.

---

### Task 1: Canonical Premultiplied Material Fragments

**Files:**
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/GPUPreparedMaterialFragment.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/PreparedMaterialContracts.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GradientWgslShaderProvider.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/BlendWgslBuilder.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/MaterialContracts.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
- Create:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialFragmentTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgramTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolverTest.kt`

**Interfaces:**
- Consumes: existing `PreparedSource`, standalone material WGSL, exact uniform
  bytes, sampled resources and registered runtime-effect program.
- Produces:

```kotlin
enum class GPUPreparedMaterialColorContract {
    LinearPremultipliedRgba,
}

enum class GPUPreparedMaterialCoordinateContract {
    LocalPosition2D,
}

data class GPUPreparedMaterialUniformBinding(
    val group: Int = 1,
    val binding: Int = 0,
    val minBindingSizeBytes: Int,
)

data class GPUPreparedMaterialSampledBinding(
    val resourceIndex: Int,
    val textureGroup: Int = 1,
    val textureBinding: Int,
    val samplerGroup: Int = 1,
    val samplerBinding: Int,
)

class GPUPreparedMaterialFragment internal constructor(
    val declarationsWgsl: String,
    val evaluationFunctionWgsl: String,
    val evaluationFunction: String,
    val uniformBinding: GPUPreparedMaterialUniformBinding?,
    sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    val colorContract: GPUPreparedMaterialColorContract,
    val coordinateContract: GPUPreparedMaterialCoordinateContract,
    val fragmentHash: String,
    val abiHash: String,
)
```

- `GPUPreparedMaterialProgram` gains:

```kotlin
val composableFragment: GPUPreparedMaterialFragment
```

- `GPUPreparedRuntimeEffectProgram` gains:

```kotlin
val sourceColorContract: GPUPreparedRuntimeEffectSourceColorContract
```

where the source contract is explicitly `LinearStraightRgba` or
`LinearPremultipliedRgba`; the compiler normalizes it to the one final
premultiplied fragment contract.

- [ ] **Step 1: Write the failing accepted-fragment matrix**

Create `GPUPreparedMaterialFragmentTest` with one row for solid, four gradient
families, image, supported blend and registered runtime effect:

```kotlin
@Test
fun `every accepted prepared material exposes one premultiplied composable fragment`() {
    acceptedDescriptors().forEach { descriptor ->
        val ready = assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(descriptor, 0.5f, context),
        )
        assertEquals(
            GPUPreparedMaterialColorContract.LinearPremultipliedRgba,
            ready.program.composableFragment.colorContract,
        )
        assertEquals(
            GPUPreparedMaterialCoordinateContract.LocalPosition2D,
            ready.program.composableFragment.coordinateContract,
        )
        assertEquals(
            "kanvas_evaluate_material",
            ready.program.composableFragment.evaluationFunction,
        )
    }
}
```

- [ ] **Step 2: Write failing identity and paint-alpha tests**

```kotlin
@Test
fun `uniform values and paint alpha do not alter fragment ABI`() {
    val first = compile(solid(r = 0.25f), paintAlpha = 0.5f)
    val second = compile(solid(r = 0.75f), paintAlpha = 0.25f)
    assertNotEquals(first.materialKey, second.materialKey)
    assertEquals(
        first.composableFragment.fragmentHash,
        second.composableFragment.fragmentHash,
    )
    assertEquals(
        first.composableFragment.abiHash,
        second.composableFragment.abiHash,
    )
}
```

Add tests proving sampled-resource count changes the fragment ABI and texture
content does not.

- [ ] **Step 3: Run the tests and observe the missing contract**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialFragmentTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolverTest"
```

Expected: compilation fails because `composableFragment`,
`GPUPreparedMaterialColorContract` and the runtime-effect source color contract
do not exist.

- [ ] **Step 4: Add the immutable fragment contracts**

Implement the types above. Require:

```kotlin
require(evaluationFunction == "kanvas_evaluate_material")
require(fragmentHash.matches(Regex("[0-9a-f]{64}")))
require(abiHash.isNotBlank())
require(sampledBindings.map { it.resourceIndex } == sampledBindings.indices.toList())
require(sampledBindings.flatMap {
    listOf(it.textureGroup to it.textureBinding, it.samplerGroup to it.samplerBinding)
}.distinct().size == sampledBindings.size * 2)
```

Snapshot the sampled-binding list with `immutableList`.

- [ ] **Step 5: Normalize source families inside Task 3**

Extend internal `PreparedSource` with composable declarations, callable source
function and its current source color contract. Build the canonical wrapper:

```wgsl
fn kanvas_evaluate_material(localPosition: vec2<f32>) -> vec4<f32> {
    let source = kanvas_material_source(localPosition);
    return kanvas_normalize_material_to_premul(source);
}
```

For an already premultiplied source, normalization returns `source`. For an
explicit straight source:

```wgsl
return vec4<f32>(source.rgb * source.a, source.a);
```

Do not inspect or rewrite arbitrary WGSL text. Solid, gradient, image, blend
and runtime-effect providers must emit their canonical callable source with
reserved `kanvas_` identifiers.

- [ ] **Step 6: Assign canonical material bindings**

Use group 1. Assign the uniform block to binding 0 when uniform bytes are
present. Assign each sampled resource in Task 3 order:

```text
resource 0 -> texture binding 1, sampler binding 2
resource 1 -> texture binding 3, sampler binding 4
```

Derive every later pair with checked arithmetic:

```kotlin
val textureBinding = Math.addExact(1, Math.multiplyExact(resourceIndex, 2))
val samplerBinding = Math.addExact(textureBinding, 1)
```

Parser/lower the fragment module and compare reflected bindings to this exact
schema before returning `Ready`.

- [ ] **Step 7: Make runtime-effect output semantics explicit**

Add `sourceColorContract` to the internal registered program. Mark the existing
`SimpleRT` program with its actual straight-linear contract, include the value
in module/binding/route hashes, and make resolver verification reject a
descriptor/program mismatch.

- [ ] **Step 8: Preserve standalone WGSL**

Keep `GPUPreparedMaterialProgram.wgslSource` and `entryPoint` behavior
unchanged for existing consumers. The compiler must build standalone and
composable output from the same `PreparedSource`; tests assert the old
standalone module still parses and retains its original entry point.

- [ ] **Step 9: Run material, gradient, blend and runtime-effect suites**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolverTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.runtimeeffects.RegisteredRuntimeEffectRouteTest"
```

Expected: all selected tests pass; every accepted source has a parser-validated
premultiplied fragment and existing standalone consumers remain green.

- [ ] **Step 10: Review and commit Task 1**

Request an independent review of color normalization, binding assignment,
runtime-effect declaration, hash coverage and absence of text-specific
branching. Fix every legitimate Critical/Important finding and rerun Step 9.

Commit:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects
rtk git commit -m "fix(renderer): normalize prepared material fragments"
```

---

### Task 2: Exact Text Vertex ABI and Final WGSL Composer

**Files:**
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8Shader.kt`
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GPUPreparedTextShaderComposer.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedTextA8ShaderTest.kt`
- Create:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/GPUPreparedTextShaderComposerTest.kt`

**Interfaces:**
- Consumes: `GPUPreparedMaterialProgram.composableFragment`, target format and
  blend-plan identity.
- Produces:

```kotlin
data class GPUPreparedTextVertexAttribute(
    val location: Int,
    val offsetBytes: Long,
    val format: String,
)

data class GPUPreparedTextVertexLayout(
    val arrayStrideBytes: Long,
    val stepMode: String,
    val attributes: List<GPUPreparedTextVertexAttribute>,
)

data class GPUPreparedTextCompositeBindingPlan(
    val drawUniformGroup: Int,
    val drawUniformBinding: Int,
    val materialFragment: GPUPreparedMaterialFragment,
    val atlasTextureGroup: Int,
    val atlasTextureBinding: Int,
    val atlasSamplerGroup: Int,
    val atlasSamplerBinding: Int,
)

data class GPUPreparedTextCompositeProgram(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val bindingPlan: GPUPreparedTextCompositeBindingPlan,
    val vertexLayout: GPUPreparedTextVertexLayout,
    val sourceHash: String,
    val abiHash: String,
    val pipelineKey: String,
)

sealed interface GPUPreparedTextCompositeProgramResult {
    data class Ready(val program: GPUPreparedTextCompositeProgram) :
        GPUPreparedTextCompositeProgramResult
    data class Refused(val code: String, val message: String) :
        GPUPreparedTextCompositeProgramResult
}

object GPUPreparedTextShaderComposer {
    fun compose(
        material: GPUPreparedMaterialProgram,
        targetFormatClass: String,
        blendPlanIdentity: String,
    ): GPUPreparedTextCompositeProgramResult
}
```

All list-bearing implementations in this task snapshot their inputs with
`immutableList`; no returned layout or binding list retains caller mutation.

- [ ] **Step 1: Replace shallow shader tests with failing ABI tests**

Assert the canonical vertex layout:

```kotlin
@Test
fun `text uses the exact 64 byte instance vertex ABI`() {
    assertEquals(64L, PreparedTextA8Shader.VertexLayout.arrayStrideBytes)
    assertEquals("Instance", PreparedTextA8Shader.VertexLayout.stepMode)
    assertEquals(
        listOf(0L, 8L, 16L, 24L, 32L),
        PreparedTextA8Shader.VertexLayout.attributes.map { it.offsetBytes },
    )
    assertFalse(PreparedTextA8Shader.vertexWgsl.contains("var<storage"))
}
```

Delete the tautological legacy-formula assertion. Retain parser and formula
assertions, but make them consume the final composed program.

- [ ] **Step 2: Write failing four-corner, UV and NDC tests**

Expose pure test-visible vertex math:

```kotlin
data class GPUPreparedTextVertexResult(
    val deviceX: Float,
    val deviceY: Float,
    val ndcX: Float,
    val ndcY: Float,
    val uvX: Float,
    val uvY: Float,
    val localX: Float,
    val localY: Float,
)
```

Test:

```kotlin
@Test
fun `skewed quad preserves TL TR BR TL BR BL and UV LTRB`() {
    val vertices = PreparedTextA8Shader.vertexOracle(
        deviceQuad = listOf(10f, 10f, 30f, 12f, 28f, 32f, 8f, 30f),
        uvLTRB = listOf(0.25f, 0.5f, 0.75f, 1f),
        targetWidth = 100f,
        targetHeight = 50f,
        deviceToLocal = identityAffine(),
    )
    assertEquals(
        listOf(10f to 10f, 30f to 12f, 28f to 32f, 10f to 10f, 28f to 32f, 8f to 30f),
        vertices.map { it.deviceX to it.deviceY },
    )
    assertEquals(-1f to 1f, PreparedTextA8Shader.deviceToNdc(0f, 0f, 100f, 50f))
    assertEquals(1f to -1f, PreparedTextA8Shader.deviceToNdc(100f, 50f, 100f, 50f))
}
```

- [ ] **Step 3: Write the failing composition matrix**

Compile solid, all four gradients, image, supported blend and registered
runtime effect through Task 1, then compose each. Assert:

```kotlin
val report = reflectWgslModule(ready.program.wgslSource)
assertEquals(
    listOf("vs_main" to "vertex", "fs_main" to "fragment"),
    report.entryPoints.map { it.name to it.stage },
)
assertEquals(report.bindings.distinct(), report.bindings)
assertEquals(3, report.bindings.maxOf { it.group } + 1)
```

Also assert one draw-uniform binding at group 0/binding 0, exact Task 1 bindings
in group 1, and atlas texture/sampler at group 2/bindings 0/1.

- [ ] **Step 4: Run tests and observe the prototype failures**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.PreparedTextA8ShaderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextShaderComposerTest"
```

Expected: failures show the 32-byte storage record, missing NDC transform,
missing fragment composition and binding collisions.

- [ ] **Step 5: Implement the fixed vertex/coverage template**

Use vertex attributes:

```wgsl
struct PreparedTextVertexInput {
    @location(0) deviceTL: vec2<f32>,
    @location(1) deviceTR: vec2<f32>,
    @location(2) deviceBR: vec2<f32>,
    @location(3) deviceBL: vec2<f32>,
    @location(4) uvLTRB: vec4<f32>,
}
```

Use `vertex_index` mapping `0,1,2,0,2,3`. Convert the selected device corner to
NDC with target width/height. Apply the two affine rows to produce continuous
`localPosition`.

The fixed fragment body must be:

```wgsl
let materialPremul = kanvas_evaluate_material(input.localPosition);
let coverage = textureSample(textAtlas, textSampler, input.uv).r;
let modulation = clamp(drawUniforms.targetSizeAndPaintAlpha.z, 0.0, 1.0) *
                 coverage;
return materialPremul * modulation;
```

- [ ] **Step 6: Implement deterministic composition and reflection**

Concatenate only canonical Task 1 fragments with reserved identifiers; do not
rename arbitrary WGSL. Parse, lower and reflect the final source. Return
`unsupported.material.composition` for source composition failure.

Compute:

```text
sourceHash = SHA-256(final WGSL)
abiHash = SHA-256(vertex layout + reflected bindings + entry points + color/coordinate contracts)
pipelineKey = SHA-256(sourceHash + abiHash + target format + blend plan)
```

Do not include `materialKey`, uniform bytes, `paintAlpha` or texture content.

- [ ] **Step 7: Add cross-glyph coordinate continuity and key tests**

Test two glyph device points under the same non-identity `deviceToLocal`.
Assert the second point continues the first coordinate system. Compile two
solid colors with the same ABI and assert equal pipeline keys; compile image
versus solid and assert different pipeline keys.

- [ ] **Step 8: Run composer and all WGSL tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialFragmentTest"
```

Expected: all final sources parse/reflect, the vertex ABI is 64-byte instanced,
and no binding collision exists.

- [ ] **Step 9: Review and commit Task 2**

Request independent ABI/coordinate and Graphite+Dawn-pragmatism reviews. Fix
every legitimate Critical/Important finding and rerun Step 8.

Commit:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl
rtk git commit -m "fix(renderer): compose prepared text shaders"
```

---

### Task 3: Exact Coordinate Snapshot and Draw-Uniform Planning

**Files:**
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify:
  `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt`
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedTextDrawUniformPlan.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedTextPayloadTest.kt`
- Modify:
  `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilderTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilderTextTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanIntegrityTest.kt`

**Interfaces:**
- Consumes: exact `GPUPreparedTextSubRun.draw.transform`, Task 2 composite,
  target dimensions, material `paintAlpha` and observed uniform alignment.
- Produces:

```kotlin
data class GPUPreparedTextDeviceToLocalAffine(
    val m00: Float,
    val m01: Float,
    val m02: Float,
    val m10: Float,
    val m11: Float,
    val m12: Float,
)

data class GPUPreparedTextDrawUniformSlice(
    val packetId: GPUDrawPacketID,
    val offsetBytes: Long,
    val sizeBytes: Long,
    val contentHash: String,
)

class GPUPreparedTextDrawUniformBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val alignmentBytes: Long,
    val logicalSliceSizeBytes: Long,
    val byteSize: Long,
    val contentHash: String,
    val slices: List<GPUPreparedTextDrawUniformSlice>,
    uploadBytes: ByteArray,
) {
    fun bytesForUpload(): ByteArray
}
```

The implementation snapshots `slices` and `uploadBytes`; `bytesForUpload()`
always returns a copy.

- `GPUDrawSemanticPayload.TextA8` and `GPUPreparedTextA8PayloadInput` gain:

```kotlin
val deviceToLocal: GPUPreparedTextDeviceToLocalAffine
```

- `GPUPreparedTextRenderBinding` gains:

```kotlin
val drawUniformBufferPlan: GPUPreparedTextDrawUniformBufferPlan
val drawUniformSlice: GPUPreparedTextDrawUniformSlice
val compositeProgram: GPUPreparedTextCompositeProgram
```

- [ ] **Step 1: Write failing transform-snapshot tests**

Build a skewed, translated `GPUPreparedTextSubRun`. Assert the semantic payload
contains the exact inverse transform bits and remains unchanged after caller
mutation:

```kotlin
assertEquals(expectedInverse.map(Float::toRawBits), payload.deviceToLocal.rawBits())
```

Add a test proving two different transforms change the payload canonical hash.

- [ ] **Step 2: Run semantic tests and observe the missing snapshot**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextSemanticBuilderTest"
```

Expected: compilation fails because the affine snapshot is absent.

- [ ] **Step 3: Add the immutable affine payload**

Validate all six values are finite. In `GPUPreparedTextSemanticBuilder`, invert
the admitted affine transform once. Use checked determinant math:

```text
det = scaleX × scaleY - skewX × skewY
```

The lowerer already refuses non-finite, perspective and singular transforms;
if that invariant is violated here, return
`GPUTextRefusalCodes.TRANSFORM_SINGULAR` rather than substituting identity.

Include all six raw float bits in the TextA8 canonical hash and snapshot copy.

- [ ] **Step 4: Write failing draw-uniform layout tests**

For two subruns with observed alignment 256, assert:

```kotlin
assertEquals(48L, plan.logicalSliceSizeBytes)
assertEquals(listOf(0L, 256L), plan.slices.map { it.offsetBytes })
assertEquals(512L, plan.byteSize)
assertTrue(plan.bytesForUpload().sliceArray(48 until 256).all { it == 0.toByte() })
```

Decode each logical payload and assert:

```text
offset 0  : target width, target height, paintAlpha, 0
offset 16 : m00, m01, m02, 0
offset 32 : m10, m11, m12, 0
```

Add overflow, `maxBufferSize`, alignment and transactionality refusal tests.

- [ ] **Step 5: Build the frame-global draw-uniform plan**

Create one zero-filled slab with:

```kotlin
val stride = alignUp(48L, limits.minUniformBufferOffsetAlignment)
val totalBytes = Math.multiplyExact(stride, textSemantics.size.toLong())
```

Pack little-endian floats. Derive one content hash after finalization. Publish
no task list if composition, alignment, range, size or budget validation fails.

- [ ] **Step 6: Compose each TextA8 program before publication**

Call `GPUPreparedTextShaderComposer.compose()` using the exact material, target
format class and blend identity. Store the `Ready.program` in the binding. A
composer refusal becomes the Task 2 production refusal and exposes no partial
graph.

Do not compose ColorGlyph; it remains Task 11.

- [ ] **Step 7: Seal frame-plan identity**

Add composite source/ABI/pipeline key, vertex layout, draw-uniform plan/slice
and exact affine bits to:

- binding preflight seal;
- frame-plan canonical hash;
- stable dump;
- resource allocations and preparation requests.

Keep the old Core/Image hash path byte-for-byte unchanged when no prepared
text exists.

- [ ] **Step 8: Run payload, recording and integrity tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextPayloadTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedTextSemanticBuilderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanIntegrityTest"
```

Expected: exact affine/draw-uniform/composite facts pass; Core/Image identities
and Task 8 ordering remain unchanged.

- [ ] **Step 9: Review and commit Task 3**

Request independent reviews of transform order, float bits, alignment,
transactionality, pipeline-key value exclusion and Core/Image compatibility.
Fix legitimate Critical/Important findings and rerun Step 8.

Commit:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording \
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilder.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedTextSemanticBuilderTest.kt
rtk git commit -m "fix(renderer): plan prepared text draw uniforms"
```

---

### Task 4: Composite Program and Vertex ABI Preflight

**Files:**
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextCompositePreflight.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Create:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextCompositePreflightTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextNativePreflightTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflightTest.kt`

**Interfaces:**
- Consumes: complete Task 3 binding/seal, observed `GPUCapabilities`, Task 2
  parser/reflection output and Task 9 exact scope/resource authority.
- Produces:

```kotlin
object GPUPreparedTextCompositePreflightRefusalCodes {
    const val COMPOSITE_SOURCE = "invalid.preflight.text.composite_source"
    const val COMPOSITE_ABI = "invalid.preflight.text.composite_abi"
    const val INSTANCE_VERTEX_ABI = "invalid.preflight.text.instance_vertex_abi"
    const val DRAW_UNIFORM = "invalid.preflight.text.draw_uniform"
    const val BINDING_LAYOUT = "invalid.preflight.text.composite_binding_layout"
}

object GPUPreparedTextCompositePreflight {
    fun validate(
        binding: GPUPreparedTextRenderBinding,
        semantic: GPUDrawSemanticPayload.TextA8,
        capabilities: GPUCapabilities,
        framePlan: GPUFramePlan,
        renderSourceStepIndex: Int,
    ): GPUPreparedTextCompositePreflightRefusal?
}

data class GPUPreparedTextCompositePreflightRefusal(
    val code: String,
    val message: String,
)
```

- [ ] **Step 1: Write one failing mutation test per new invariant**

Start from one accepted frame and mutate exactly one fact per dynamic test:

```text
composite source hash
fragment entry point
reflected binding
vertex stride
vertex step mode
vertex attribute offset
draw uniform alignment
draw uniform range
draw uniform content
target size
paintAlpha bits
deviceToLocal bits
pipeline key
```

Every row asserts its production constant and:

```kotlin
assertEquals(0, nativeProbe.totalCreations)
assertEquals(0, materializerProbe.invocations)
```

- [ ] **Step 2: Add the Task 10 guard-order regression**

Corrupt a composite source while TextA8 materialization remains disabled.
Assert the composite refusal is returned before
`unsupported.preflight.prepared_text_unmaterialized`. For a valid frame,
assert the Task 10 guard still returns that existing code with zero side
effects.

- [ ] **Step 3: Run the tests and observe missing validation**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest"
```

Expected: new corruptions pass through to the Task 10 guard or receive a
generic code.

- [ ] **Step 4: Implement the focused pure validator**

Validate in this order:

1. semantic/binding canonical hash and exact affine bits;
2. instance buffer `Vertex` usage, stride 64, step mode and attributes;
3. draw-uniform buffer/slice bytes, range, alignment and observed limits;
4. final source hash, entry points and parser/lowering/reflection result;
5. exact material, atlas and draw binding layout;
6. pipeline key from code/ABI/target/blend only;
7. resource ownership and upload-before-every-consumer dependencies.

Use checked arithmetic for every offset/range. Do not parse WGSL with regexes.

- [ ] **Step 5: Delegate from the existing preflight**

Call the focused validator from
`GPUPreparedSurfaceNativePreflight.validateFramePlan()`. Keep the existing 28
Task 9 mutation codes and behavior unchanged. Remove no Core/Image validation.

Keep `GPUFramePreflighter` order:

```text
all pure frame/semantic/composite validation
-> valid TextA8 Task 10 materialization guard
-> native resource provider/materializer
```

- [ ] **Step 6: Run Task 9, Task 8 and FP-04 regressions**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextCompositePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest"
```

Expected: composite matrix and existing 28 rows pass with zero native
creation; Core/Image/FP-04 remain green.

- [ ] **Step 7: Review and commit Task 4**

Request independent correctness and Graphite+Dawn-pragmatism reviews. Fix
legitimate Critical/Important findings and rerun Step 6.

Commit:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "fix(renderer): preflight prepared text composite programs"
```

---

### Task 5: Native TextA8 Materialization and Completion Ownership

**Files:**
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextSessionCache.kt`
- Create:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializer.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Modify:
  `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Create:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedTextRenderRunMaterializerTest.kt`
- Create:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedTextOwnershipTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest.kt`
- Modify:
  `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcherTest.kt`

**Interfaces:**
- Consumes: accepted TextA8 bindings, exact scope keys, generic R8 and material
  texture plans, frame-global instance/draw/material uniform plans.
- Produces:

```kotlin
data class GPUPreparedTextRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.TextA8>,
    val bindings: List<GPUPreparedTextRenderBinding>,
    val exactScopeKeys: List<GPUPreparedNativeScopeKey>,
)

internal class GPUWgpu4kPreparedTextSessionCache {
    fun acquireBatch(
        programs: List<GPUPreparedTextCompositeProgram>,
        generation: GPUDeviceGenerationID,
    ): GPUPreparedTextCacheBatchAcquire
}

internal class GPUWgpu4kPreparedTextPipelineAcquisition(
    val pipeline: io.ygdrasil.webgpu.GPURenderPipeline,
    val drawBindGroupLayout: io.ygdrasil.webgpu.GPUBindGroupLayout,
    val materialBindGroupLayout: io.ygdrasil.webgpu.GPUBindGroupLayout,
    val atlasBindGroupLayout: io.ygdrasil.webgpu.GPUBindGroupLayout,
    val atlasSampler: io.ygdrasil.webgpu.GPUSampler,
    val materialSamplersByResourceKey: Map<String, io.ygdrasil.webgpu.GPUSampler>,
)

sealed interface GPUPreparedTextCacheBatchAcquire {
    data class Ready(
        val pipelinesByKey: Map<String, GPUWgpu4kPreparedTextPipelineAcquisition>,
    ) : GPUPreparedTextCacheBatchAcquire

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedTextCacheBatchAcquire
}

internal class GPUWgpu4kPreparedTextRenderRunMaterializer {
    fun materializeAcceptedRun(
        plan: GPUPreparedTextRenderRunPlan,
        actualDeviceGeneration: GPUDeviceGenerationID,
    ): GPUPreparedRenderRunMaterialization
}
```

The acquisition snapshots `materialSamplersByResourceKey` and never exposes a
mutable cache map.

- [ ] **Step 1: Write the failing batching/write/draw test**

Use 100 instances split 64/36 across two subruns sharing one page:

```kotlin
assertEquals(1, native.createdInstanceBuffers.size)
assertEquals(1, native.createdDrawUniformBuffers.size)
assertEquals(1, native.createdMaterialUniformBuffers.size)
assertEquals(1, native.createdR8Textures.size)
assertEquals(1, native.writeTextureCalls.count { it.role == "text-atlas" })
assertEquals(1, native.writeBufferCalls.count { it.role == "instances" })
assertEquals(1, native.writeBufferCalls.count { it.role == "draw-uniforms" })
assertEquals(listOf(64, 36), native.draws.map { it.instanceCount })
assertEquals(listOf(0, 64), native.draws.map { it.firstInstance })
assertTrue(native.vertexBindings.all { it.offset == 0L && it.stride == 64L })
```

Assert each draw uses `vertexCount = 6` and `firstVertex = 0`.

- [ ] **Step 2: Write the failing material-resource matrix**

For solid, gradient, image and registered runtime effect, assert:

- exact pipeline key acquired;
- material uniform slice bound;
- image material texture uploaded once and bound before the atlas group;
- no texture is created for solid/gradient/runtime rows without sampled
  resources;
- final bind groups match groups 0, 1 and 2 from Task 2.

- [ ] **Step 3: Write rollback and completion tests**

Inject failure at each creation point:

```text
shader module
bind-group layout
pipeline layout
pipeline
R8 texture
R8 view
material texture/view
instance buffer
draw-uniform buffer
material-uniform buffer
bind group
```

For setup failure, assert every created object closes once. For failure after
submit or during readback, assert all payload-owned resources stay open until
completion and then close once. Close/recreate must not reuse a frame-local
page or buffer.

- [ ] **Step 4: Run tests and observe the Task 10 guard/missing classes**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedTextRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextOwnershipTest"
```

Expected: compilation fails because the cache/materializer do not exist and
the dispatcher still stops at the Task 10 guard.

- [ ] **Step 5: Implement the pipeline-only session cache**

Cache:

```text
shader module
bind-group layouts
pipeline layout
samplers
render pipeline
```

Key only by Task 2 `pipelineKey` plus exact device generation. Cache ownership
is `Borrowed`. On generation change or cache close, close invariant handles
once.

Never cache atlas/material textures, views, instance/uniform buffers, bind
groups or upload state.

- [ ] **Step 6: Implement frame-local resources and logical uploads**

Follow the existing prepared-image setup-owner pattern. Create frame-local
buffers/textures/views/bind groups and return:

- texture upload scope operands for every exact R8/material resource;
- `GPUPreparedNativeBufferUpload` for each frame-global buffer;
- render scope commands using Task 2 pipeline/layout;
- transferable completion owners.

Use the existing encoding backend so real calls remain:

```text
queue.writeTexture(...) once per texture plan
queue.writeBuffer(...) once per frame-global buffer plan
```

Do not invent mapped staging buffers.

- [ ] **Step 7: Emit exact instanced commands**

For each ordered subrun:

```kotlin
GPUPreparedNativeRenderCommand.SetPipeline(pipeline)
GPUPreparedNativeRenderCommand.SetBindGroup(0, drawGroup)
GPUPreparedNativeRenderCommand.SetBindGroup(1, materialGroup)
GPUPreparedNativeRenderCommand.SetBindGroup(2, atlasGroup)
GPUPreparedNativeRenderCommand.SetVertexBuffer(
    slot = 0,
    buffer = instanceBuffer,
    offset = 0L,
    size = requireNotNull(instanceBuffer.byteCapacity),
    vertexStrideBytes = 64L,
)
GPUPreparedNativeRenderCommand.Draw(
    GPUPreparedNativeDrawCall.Draw(
        vertexCount = 6,
        instanceCount = binding.instanceCount,
        firstVertex = 0,
        firstInstance = binding.firstInstance,
    ),
)
```

Do not bind a sliced vertex-buffer offset in addition to `firstInstance`.

- [ ] **Step 8: Integrate by exact scope order**

Materialize TextA8 operands by source step. Merge them with existing Core/Image
operands, then order only with:

```kotlin
accepted.exactScopeKeys.map { key ->
    operandsByStep.getValue(key.sourceStepIndex)
}
```

Remove the valid-TextA8 Task 10 guard only after the text run materializer is
installed. Keep ColorGlyph on its existing Task 11 guard. Do not create a
second encoder, submit or readback.

Add the prepared-text pipeline acquisition authority to
`GPUPreparedNativeFramePayload.kt` so `SetPipeline` accepts only a pipeline
returned by `GPUWgpu4kPreparedTextSessionCache`; do not use the generic
`BindGroupRequired` escape hatch.

- [ ] **Step 9: Run focused native proxy tests**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedTextRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedTextOwnershipTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFramePayloadMaterializerDispatcherTest"
```

Expected: writes/draws/order/rollback/completion all pass; ColorGlyph remains
unmaterialized.

- [ ] **Step 10: Run Task 3/8/9 and FP-04 regressions**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.materials.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.wgsl.*" \
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTextTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedText*Test" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest"
```

Expected: all selected tests pass without router/gate/animation changes.

- [ ] **Step 11: Review and commit Task 5**

Request:

1. native ABI/writes/draws/order/rollback/ownership/cache correctness review;
2. Graphite+Dawn/wgpu4k pragmatism and FP-04/Core/Image non-regression review.

Fix every legitimate Critical/Important finding, rerun Steps 9–10 and obtain
C0/I0.

Commit:

```bash
rtk git add gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution
rtk git commit -m "feat(renderer): materialize prepared A8 text"
```

---

### Task 6: Aggregate Validation and Independent End-to-End Review

**Files:**
- Update ignored ledger:
  `.superpowers/sdd/2026-07-28-fp-05-prepared-text-route/progress.md`
- Create ignored report:
  `.superpowers/sdd/2026-07-28-fp-05-prepared-text-route/task-10-composite-report.md`
- No tracked production or test file is added solely for this task.

**Interfaces:**
- Consumes: committed Tasks 1–5.
- Produces: serial validation evidence, classified review findings and a clean
  branch ready for FP-05 Task 11.

- [ ] **Step 1: Run focused modules serially**

Run:

```bash
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

Record exact passed/failed/skipped counts. A random
`failed.surface.prepared.session-close` must be reproduced separately and
reported as native cleanup instability; do not hide it or add an unrelated
workaround.

- [ ] **Step 2: Run package and source boundaries**

Run:

```bash
rtk proxy ./gradlew :gpu-renderer:test --no-parallel \
  --tests "org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.GPURendererLayoutSurfaceTest"
rtk rg -n "LegacyDisplayOpFamily\\.Text|legacy\\.surface\\.prepared\\.family\\.text" \
  kanvas/src/main gpu-renderer/src/main font
rtk git diff --check
```

Expected: boundary tests pass, product-route search remains unchanged from
before this corrective plan, and diff check is clean.

- [ ] **Step 3: Request two independent end-to-end reviews**

Reviewer A checks every requirement in the validated design: color contract,
material authority, binding allocation, 64-byte vertex ABI, coordinate
continuity, preflight and native ownership.

Reviewer B compares the final code to Graphite+Dawn's useful principles and
checks that Kanvas did not add Graphite's multi-backend abstractions, duplicate
material compilers, regex WGSL rewriting or unnecessary pipeline variants.

Classify findings as Critical, Important, Minor or invalid.

- [ ] **Step 4: Fix and re-review legitimate findings**

For each legitimate Critical/Important finding:

1. write or retain the smallest failing reproducer;
2. apply the correction in the task that owns the authority;
3. rerun that task's focused suite;
4. rerun Steps 1–2;
5. request a re-review of the corrected lines.

Do not declare completion until both reviewers report C0/I0.

- [ ] **Step 5: Record final evidence and verify branch state**

Record commits, commands, counts, review outcomes, non-claims and any
wgpu4k/wgsl4k issue in the ignored report. Then run:

```bash
rtk git status --short --branch
rtk git log --oneline --decorate -12
rtk git diff --check origin/codex/graphite-dawn-frame-plan-design...HEAD
rtk git ls-files .superpowers/sdd
```

Expected: tracked tree clean, corrective commits ordered, no SDD file staged,
router/gate/allowlist/animation unchanged, and Task 11 still pending.
