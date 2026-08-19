# FP-06 Prepared Vertices And Mesh Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `DisplayOp.DrawVertices` and `DisplayOp.DrawMesh` to the common prepared Surface frame with immutable vertex/index artifacts, registered `MeshProgram` materials, full preflight, native WebGPU draws, exact ownership, and no legacy continuation.

**Architecture:** Surface lowering snapshots and validates public vertices/mesh inputs into a frame-local inventory. The inventory produces one closed `GPUDrawSemanticPayload.Vertices` per draw, while the common prepared-material compiler remains the only material/runtime-effect authority. The heterogeneous task graph plans upload-before-draw dependencies; one full-frame preflight validates every artifact, ABI, binding, budget, generation, and range before the wgpu4k materializer creates buffers and issues ordered `draw`/`drawIndexed` calls.

**Tech Stack:** Kotlin/JVM, Gradle 9.2, Java toolchain 25, wgpu4k/WebGPU, parser-validated WGSL through wgsl4k, `kotlin.test`, JUnit Jupiter, existing prepared Surface frame contracts.

## Global Constraints

- Read `docs/superpowers/specs/2026-07-29-fp-06-prepared-vertices-mesh-design.md` before implementation.
- Do not start Task 1 until FP-05 Tasks 1-15 are accepted, committed, and the target branch is clean.
- Do not port Ganesh or Graphite.
- Use Skia Graphite+Dawn only as a bounded reference at commit `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`.
- Keep WebGPU as the only GPU backend and WGSL as the shader target.
- Do not create a general Mesh IR, 3D API, `Recorder`, `RendererProvider`, `RenderStep` hierarchy, or backend-polymorphic resource provider.
- Reuse `GPUPreparedMaterialProgramCompiler`, `KanvasPreparedRuntimeEffectResolver`, `GPUBlendPlan`, prepared clip authorities, task graph, preflight, and ownership ledger.
- Supported `MeshProgram` effects require a registered Kanvas descriptor, Kotlin/CPU behavior, and parser-validated WGSL.
- Do not compile arbitrary SkSL or accept arbitrary user vertex shader source.
- Keep vertex/index buffers frame-owned in FP-06; persistent residency belongs to FP-09.
- Keep the product gate closed until Task 15.
- A refusal after prepared-route admission is terminal: no immediate, path, CPU texture, or legacy continuation.
- Preserve FP-04 image and FP-05 text behavior.
- Do not regenerate GM renders or scores before FP-11.
- Do not add `.superpowers/sdd/` to commits.
- Run shell commands through `rtk`; run Gradle through `rtk proxy ./gradlew`.
- Every task uses TDD, ends with focused green tests, `rtk git diff --check`, and one reviewable commit.

---

## Prerequisite Gate

Before Task 1, run:

```bash
rtk git status --short --branch
rtk rg -n "Status: `completed`" \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.text|LegacyDisplayOpFamily\\.Text" \
  kanvas/src/main gpu-renderer/src/main font
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

Expected:

- the worktree is clean;
- FP-05 closure evidence is committed;
- production contains no legacy text family;
- the accepted FP-05 aggregate is green;
- no agent is modifying the target branch.

If the gate is not satisfied, stop. Do not adapt FP-06 around an unfinished
FP-05 interface.

---

## File And Interface Map

### Canonical artifacts and refusals

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/GPUPreparedVerticesUploadArtifact.kt`
  - immutable exact vertex/index bytes and structural identity;
  - no native handles.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesRefusalCodes.kt`
  - one canonical source for `unsupported.vertices.*` and
    `unsupported.mesh.*` codes.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesPacker.kt`
  - validates attributes and canonicalizes topology into exact upload bytes.

### Surface lowering and inventory

- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesContracts.kt`
  - handle-free ready/refused contracts.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowerer.kt`
  - pure `DrawVertices`/`DrawMesh` lowering.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedVerticesFrameInventory.kt`
  - exact frame-local deduplication, budgets, and command mapping.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesFramePreparer.kt`
  - orchestrates lowering, inventory, and mapping without native work.
- Create `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesSemanticBuilder.kt`
  - produces closed semantic payloads from lowerer/inventory facts.

### Material and MeshProgram authority

- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt`
  - typed `MeshProgram` mapping through existing prepared authorities.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
  - typed runtime-effect child descriptors with exact role identity.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/MaterialContracts.kt`
  - reflected registered child slots and compiled child programs.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
  - compile registered shader/color-filter/blender children; no second compiler.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
  - validate exact registered child schema alongside uniforms and bindings.

### Payload, WGSL, resources, recording, and execution

- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedVerticesPayload.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShader.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUVerticesFrameResourcePlan.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`.
- Create `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt`.
- Modify `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`.

### Product integration

- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`.
- Modify `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`.
- Remove migrated production branches from
  `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`.

### Stable interfaces carried across tasks

```kotlin
data class GPUPreparedVerticesArtifactInput(
    val topology: GPUVertexMode,
    val positions: FloatArray,
    val colorsRgba8: ByteArray?,
    val texCoords: FloatArray?,
    val indices: IntArray?,
    val provenance: String,
)

data class GPUPreparedVerticesFloatBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class GPUPreparedVerticesPackingLimits(
    val maxVertices: Int,
    val maxIndices: Int,
    val maxVertexBytes: Long,
    val maxIndexBytes: Long,
    val maxFanExpandedIndices: Int,
)

sealed interface GPUPreparedVerticesPackingResult {
    data class Ready(
        val artifact: GPUPreparedVerticesUploadArtifact,
        val sourceBounds: GPUPreparedVerticesFloatBounds,
    ) : GPUPreparedVerticesPackingResult

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedVerticesPackingResult
}

sealed interface GPUPreparedVerticesLowering {
    data class Ready(val draw: GPUPreparedVerticesDraw) :
        GPUPreparedVerticesLowering

    data class Refused(
        val code: String,
        val operationIndex: Int,
        val facts: Map<String, String>,
    ) : GPUPreparedVerticesLowering
}

enum class GPUPreparedVerticesOperationKind {
    DrawVertices,
    DrawMesh,
}

class GPUPreparedVerticesDraw internal constructor(
    val operationIndex: Int,
    val operationKind: GPUPreparedVerticesOperationKind,
    val artifact: GPUPreparedVerticesUploadArtifact,
    val transform: Matrix33,
    val clipContentKey: String,
    val material: GPUPreparedMaterialProgram,
    val primitiveBlendPlan: GPUBlendPlan?,
    val finalBlendPlan: GPUBlendPlan,
    val sourceBounds: GPUPreparedVerticesFloatBounds,
    val targetColorFormat: String,
    val capabilitySnapshotHash: String,
    val provenance: String,
)

data class GPUPreparedMeshProgramMapping(
    val descriptor: GPUMaterialDescriptor.RuntimeEffect,
    val paintAlpha: Float,
)

sealed interface PreparedVerticesFrameInventoryResult {
    data class Ready(val inventory: PreparedVerticesFrameInventory) :
        PreparedVerticesFrameInventoryResult

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : PreparedVerticesFrameInventoryResult
}
```

---

### Task 1: Canonical refusal authority and immutable upload artifact

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesRefusalCodes.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/GPUPreparedVerticesUploadArtifact.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/GPUPreparedVerticesUploadArtifactTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesRefusalCodesTest.kt`

**Interfaces:**

- Consumes: existing `GPUVertexMode` and `GPUVertexLayoutPlan`.
- Produces: immutable `GPUPreparedVerticesUploadArtifact`,
  `GPUPreparedVerticesArtifactInput`, and canonical refusal constants used by
  every later task.

- [ ] **Step 1: Write artifact mutation and identity tests**

```kotlin
@Test
fun `artifact snapshots vertex and index bytes`() {
    val vertices = byteArrayOf(1, 2, 3, 4)
    val indices = byteArrayOf(0, 0, 1, 0)
    val artifact = preparedArtifact(vertices, indices)

    vertices.fill(99)
    indices.fill(88)

    assertContentEquals(byteArrayOf(1, 2, 3, 4), artifact.vertexBytesForUpload())
    assertContentEquals(byteArrayOf(0, 0, 1, 0), artifact.indexBytesForUpload())
}

@Test
fun `artifact identity excludes command state and includes exact bytes`() {
    val first = preparedArtifact(byteArrayOf(1, 2, 3, 4), null)
    val same = preparedArtifact(byteArrayOf(1, 2, 3, 4), null)
    val changed = preparedArtifact(byteArrayOf(1, 2, 3, 5), null)

    assertEquals(first.key, same.key)
    assertNotEquals(first.key, changed.key)
}
```

- [ ] **Step 2: Run the new artifact test and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedVerticesUploadArtifactTest" --no-parallel
```

Expected: compilation fails because the artifact does not exist.

- [ ] **Step 3: Add canonical refusal constants**

Define one object containing the exact design codes. Existing constants with
the same semantic boundary must be moved or delegated here; do not leave
duplicate strings.

```kotlin
object GPUPreparedVerticesRefusalCodes {
    const val Topology = "unsupported.vertices.topology"
    const val PositionCount = "unsupported.vertices.position_count"
    const val AttributeCount = "unsupported.vertices.attribute_count"
    const val NonFinite = "unsupported.vertices.non_finite"
    const val IndexOutOfRange = "unsupported.vertices.index_out_of_range"
    const val IndexFormat = "unsupported.vertices.index_format"
    const val AttributeLayout = "unsupported.vertices.attribute_layout"
    const val Transform = "unsupported.vertices.transform"
    const val ColorConversion = "unsupported.vertices.color_conversion_unvalidated"
    const val PrimitiveBlender = "unsupported.vertices.primitive_blender_unregistered"
    const val Material = "unsupported.vertices.material"
    const val Budget = "unsupported.vertices.budget"
    const val MeshBounds = "unsupported.mesh.bounds"
    const val MeshProgramUnregistered = "unsupported.mesh.program_unregistered"
    const val MeshProgramCpuUnavailable = "unsupported.mesh.program_cpu_not_available"
    const val MeshProgramWgslUnavailable = "unsupported.mesh.program_wgsl_not_available"
    const val MeshProgramWgslValidation = "unsupported.mesh.program_wgsl_validation"
    const val MeshProgramAbi = "unsupported.mesh.program_abi"
    const val MeshProgramChild = "unsupported.mesh.program_child"
    const val MeshProgramResource = "unsupported.mesh.program_resource"
    const val MeshBudget = "unsupported.mesh.budget"
}
```

- [ ] **Step 4: Implement the immutable artifact**

The constructor snapshots bytes and layout collections. Accessors return
copies. The key is SHA-256 over a versioned canonical preimage containing
topology, layout, counts, formats, and exact byte hashes.

```kotlin
class GPUPreparedVerticesUploadArtifact internal constructor(
    topology: GPUVertexMode,
    layout: GPUVertexLayoutPlan,
    vertexBytes: ByteArray,
    indexBytes: ByteArray?,
    val vertexCount: Int,
    val indexCount: Int?,
    val indexFormat: String?,
    val provenance: String,
) {
    private val vertexSnapshot = vertexBytes.copyOf()
    private val indexSnapshot = indexBytes?.copyOf()
    val topology: GPUVertexMode = topology
    val layout: GPUVertexLayoutPlan = layout.deepSnapshot()
    val vertexContentHash: String = sha256Hex(vertexSnapshot)
    val indexContentHash: String? = indexSnapshot?.let(::sha256Hex)
    val key: String = CanonicalIdentityEncoder("prepared-vertices-artifact-v1")
        .text("topology", topology.sourceLabel)
        .int("vertexCount", vertexCount)
        .int("indexCount", indexCount ?: 0)
        .text("indexFormat", indexFormat ?: "none")
        .int("strideBytes", layout.strideBytes)
        .texts("attributes", layout.attributes)
        .texts(
            "offsets",
            layout.offsets.toSortedMap().map { (name, offset) -> "$name=$offset" },
        )
        .texts(
            "locations",
            layout.shaderLocations.toSortedMap().map { (name, location) -> "$name=$location" },
        )
        .text("vertexHash", vertexContentHash)
        .text("indexHash", indexContentHash ?: "none")
        .digestHex()

    fun vertexBytesForUpload(): ByteArray = vertexSnapshot.copyOf()
    fun indexBytesForUpload(): ByteArray? = indexSnapshot?.copyOf()
}

private fun GPUVertexLayoutPlan.deepSnapshot(): GPUVertexLayoutPlan =
    GPUVertexLayoutPlan(
        attributes = attributes.toList(),
        strideBytes = strideBytes,
        offsets = offsets.toSortedMap(),
        shaderLocations = shaderLocations.toSortedMap(),
    )
```

- [ ] **Step 5: Run artifact and refusal tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedVerticesUploadArtifactTest" \
  --tests "*GPUPreparedVerticesRefusalCodesTest" --no-parallel
rtk git diff --check
```

Expected: all selected tests pass and no duplicate literal is found by the
refusal test.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): define prepared vertices artifacts"
```

---

### Task 2: Canonical topology conversion and vertex/index packing

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesPacker.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/GPUPreparedVerticesPackerTest.kt`

**Interfaces:**

- Consumes: Task 1 artifact and refusal authority.
- Produces: `GPUPreparedVerticesPackingResult`, canonical triangle-fan
  expansion, one exact interleaved layout, and conservative source bounds.

- [ ] **Step 1: Write failing topology and packing tests**

Cover:

```kotlin
@Test
fun `indexed fan becomes deterministic triangle-list indices`() {
    val result = pack(
        topology = GPUVertexMode.TriangleFan,
        positions = floatArrayOf(0f, 0f, 4f, 0f, 4f, 4f, 0f, 4f),
        indices = intArrayOf(2, 3, 0, 1),
    ).ready()

    assertEquals(GPUVertexMode.Triangles, result.artifact.topology)
    assertContentEquals(
        intArrayOf(2, 3, 0, 2, 0, 1),
        decodeIndices(result.artifact),
    )
}

@Test
fun `packer rejects an out-of-range source index`() {
    val refusal = pack(
        topology = GPUVertexMode.Triangles,
        positions = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
        indices = intArrayOf(0, 1, 3),
    ).refused()

    assertEquals(GPUPreparedVerticesRefusalCodes.IndexOutOfRange, refusal.code)
}
```

Also test:

- triangle list count multiple of three;
- strip with at least three vertices;
- implicit indices;
- colors/UV counts equal vertex count;
- non-finite positions/UVs;
- canonical RGBA8 premultiplication;
- `uint16`/`uint32` selection;
- exact stride and offsets for all four attribute combinations;
- source bounds;
- integer overflow and byte budget failures.

- [ ] **Step 2: Run the packer test and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedVerticesPackerTest" --no-parallel
```

- [ ] **Step 3: Implement closed validation before packing**

Validate all counts, finiteness, indices, multiplication, and configured
budgets before allocating the final byte arrays.

```kotlin
object GPUPreparedVerticesPacker {
    fun pack(
        input: GPUPreparedVerticesArtifactInput,
        limits: GPUPreparedVerticesPackingLimits,
        supportsUint32Index: Boolean,
    ): GPUPreparedVerticesPackingResult {
        validateCounts(input, limits)?.let { return it }
        val canonical = canonicalizeTopology(input, limits)
            ?: return refused(GPUPreparedVerticesRefusalCodes.Topology, input)
        return packCanonical(canonical, limits, supportsUint32Index)
    }
}
```

- [ ] **Step 4: Implement exact little-endian interleaving and index bytes**

Pack `position`, optional premultiplied RGBA8, then optional UV. Zero all
alignment padding. Use checked arithmetic for every byte count.

- [ ] **Step 5: Run packer plus existing vertices contract suites**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedVerticesPackerTest" \
  --tests "*VerticesBufferPlanTest" \
  --tests "*VerticesRouteDecisionTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): pack prepared vertices exactly"
```

---

### Task 3: Typed MeshProgram mapping

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUMaterialMapper.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/commands/NormalizedDrawCommand.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedMeshProgramMapperTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/commands/GPURuntimeEffectChildDescriptorTest.kt`

**Interfaces:**

- Consumes: public `MeshProgram`, `MeshChildren`, `UniformBlock`, and existing
  prepared material descriptor assembly.
- Produces: `GPUPreparedMeshProgramMapping` and typed
  `GPURuntimeEffectChildDescriptor` snapshots.

- [ ] **Step 1: Write mapping tests**

Tests must prove:

- exact effect ID/version;
- exact uniform values including copied matrices;
- stable ordered child names;
- child role retained as `Shader`, `ColorFilter`, or `Blender`;
- duplicate child names refused before map construction;
- caller mutation cannot change the descriptor;
- `DrawMesh.blendMode ?: paint.blendMode` is retained separately as final
  target blend.

```kotlin
@Test
fun `mesh program child roles are part of descriptor identity`() {
    val shader = meshProgram(children = MeshChildren.of("child" to ShaderChild(redShader())))
    val filter = meshProgram(children = MeshChildren.of("child" to ColorFilterChild(matrixFilter())))

    assertNotEquals(
        shader.toPreparedMeshProgramMapping().descriptor,
        filter.toPreparedMeshProgramMapping().descriptor,
    )
}
```

- [ ] **Step 2: Run the tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPURuntimeEffectChildDescriptorTest" \
  --tests "*GPUPreparedMeshProgramMapperTest" --no-parallel
```

- [ ] **Step 3: Add typed runtime-effect child descriptors**

```kotlin
enum class GPURuntimeEffectChildRole {
    Shader,
    ColorFilter,
    Blender,
}

sealed interface GPUPreparedColorFilterChildDescriptor {
    data class Matrix(val values: List<Float>) :
        GPUPreparedColorFilterChildDescriptor

    data class Blend(
        val rgba: List<Float>,
        val mode: GPUBlendMode,
    ) : GPUPreparedColorFilterChildDescriptor

    data class Compose(
        val outer: GPUPreparedColorFilterChildDescriptor,
        val inner: GPUPreparedColorFilterChildDescriptor,
    ) : GPUPreparedColorFilterChildDescriptor

    data class RegisteredRuntimeEffect(
        val effect: GPUMaterialDescriptor.RuntimeEffect,
    ) : GPUPreparedColorFilterChildDescriptor
}

sealed interface GPUPreparedBlenderChildDescriptor {
    data class Mode(val mode: GPUBlendMode) :
        GPUPreparedBlenderChildDescriptor

    data class Arithmetic(
        val k1: Float,
        val k2: Float,
        val k3: Float,
        val k4: Float,
    ) : GPUPreparedBlenderChildDescriptor
}

sealed interface GPURuntimeEffectChildDescriptor {
    val role: GPURuntimeEffectChildRole

    data class Shader(val material: GPUMaterialDescriptor) :
        GPURuntimeEffectChildDescriptor {
        override val role: GPURuntimeEffectChildRole =
            GPURuntimeEffectChildRole.Shader
    }

    data class ColorFilter(val filter: GPUPreparedColorFilterChildDescriptor) :
        GPURuntimeEffectChildDescriptor {
        override val role: GPURuntimeEffectChildRole =
            GPURuntimeEffectChildRole.ColorFilter
    }

    data class Blender(val blender: GPUPreparedBlenderChildDescriptor) :
        GPURuntimeEffectChildDescriptor {
        override val role: GPURuntimeEffectChildRole =
            GPURuntimeEffectChildRole.Blender
    }
}
```

Update `GPUMaterialDescriptor.RuntimeEffect` equality, hashing,
canonicalization, snapshotting, and depth limits so names, roles, and exact
child descriptors are authoritative.

The closed FP-06 color-filter child set is matrix, blend, composition of the
same accepted filters, and registered runtime effect. The closed blender child
set is canonical blend mode plus arithmetic only when its existing CPU/WGSL
authority accepts it. Every other public child variant returns
`unsupported.mesh.program_child`; it is not silently approximated.

- [ ] **Step 4: Implement MeshProgram mapping through existing authorities**

Add internal mapping functions in `GPUMaterialMapper.kt`. Reuse the existing
shader prepared mapping, canonical color-filter mapping, blend-mode formulas,
and uniform conversion. Do not create a mesh-only material compiler.

```kotlin
internal fun MeshProgram.toPreparedMeshProgramMapping(
    paintAlpha: Float,
    descriptorAssembly: GPUMaterialDescriptorAssemblySession =
        GPUMaterialDescriptorAssemblySession(),
): GPUPreparedMeshProgramMapping
```

Return typed refusal for duplicate names, unsupported child variants, blank
effect IDs, invalid alpha, or descriptor graph depth.

- [ ] **Step 5: Run mapper and material regression tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPURuntimeEffectChildDescriptorTest" \
  --tests "*GPUPreparedMeshProgramMapperTest" \
  --tests "*GPUMaterialMapperTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test \
  kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): map registered mesh programs"
```

---

### Task 4: Compile registered runtime-effect children once

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/MaterialContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/PreparedMaterialContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgram.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolver.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/materials/GPUPreparedMaterialProgramChildrenTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/runtimeeffects/KanvasPreparedRuntimeEffectResolverTest.kt`

**Interfaces:**

- Consumes: Task 3 typed child descriptors.
- Produces: reflected registered child slots and one
  `GPUPreparedRuntimeEffectChildProgram` list embedded in the common prepared
  material program identity.

- [ ] **Step 1: Write failing child-schema tests**

Test a registered descriptor with:

- one shader child;
- one matrix color-filter child;
- one mode blender child;
- exact name/role matching;
- missing child;
- extra child;
- wrong role;
- child program ABI mismatch;
- recursive graph over the existing depth budget.

```kotlin
@Test
fun `prepared runtime material refuses a child role mismatch`() {
    val result = compileRegistered(
        expected = childSlot("source", role = "shader"),
        supplied = mapOf("source" to blenderChild(SRC_OVER)),
    )

    assertRefused(result, "unsupported.material.runtime_effect.child_role")
}
```

- [ ] **Step 2: Run the child tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedMaterialProgramChildrenTest" \
  --tests "*KanvasPreparedRuntimeEffectResolverTest" --no-parallel
```

- [ ] **Step 3: Extend the registered program authority with child slots**

```kotlin
data class GPUPreparedRuntimeEffectChildProgram(
    val name: String,
    val role: GPUPreparedRuntimeEffectChildRole,
    val programKey: String,
    val abiHash: String,
    val uniformBytes: List<Int>,
    val resourceFacts: List<String>,
)

data class GPUPreparedRuntimeEffectChildSlot internal constructor(
    val name: String,
    val role: GPUPreparedRuntimeEffectChildRole,
    val bindingIndex: Int?,
    val abiHash: String,
)
```

Include ordered child-slot facts in `moduleHash`, `reflectionHash`,
`bindingPlanHash`, `routeContractHash`, validation, and dumps.

- [ ] **Step 4: Compile children through canonical authorities**

- shader children recurse through `GPUPreparedMaterialProgramCompiler`;
- color-filter children compile through the canonical prepared color-filter
  lowering already used by paint materials;
- mode blenders use the exhaustive canonical blend formulas;
- arithmetic blenders are accepted only if the existing canonical arithmetic
  authority has CPU and WGSL parity; otherwise they refuse.

The compiler must retain child role, material key, ABI hash, resource facts,
and uniform bytes. It must not inline a magenta placeholder or silently drop a
child.

- [ ] **Step 5: Include exact children in material key and ABI hash**

Test that child names, order, roles, source hashes, uniform bytes, resource
identities, and ABI hashes change the correct identity while native handles
and upload offsets do not.

- [ ] **Step 6: Run all prepared-material and runtime-effect tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedMaterialProgram*" \
  --tests "*KanvasPreparedRuntimeEffectResolverTest" \
  --tests "*runtimeeffects*" --no-parallel
rtk git diff --check
```

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): compile registered material children"
```

---

### Task 5: Pure DrawVertices and DrawMesh lowerer

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesContracts.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowerer.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesLowererTest.kt`

**Interfaces:**

- Consumes: Tasks 1-4 packer, artifact, material mapping, blend, clip, transform,
  and registered runtime-effect authorities.
- Produces: immutable `GPUPreparedVerticesDraw` or a typed terminal refusal.

- [ ] **Step 1: Write lowerer RED tests**

Cover every public combination and refusal boundary from the design. Include:

```kotlin
@Test
fun `draw mesh resolves final blend exactly once`() {
    val op = drawMesh(
        paintBlend = BlendMode.MULTIPLY,
        overrideBlend = BlendMode.PLUS,
        program = registeredMeshProgram(),
    )

    val draw = lower(op).ready()

    assertEquals(GPUBlendMode.PLUS, draw.finalBlend.mode)
    assertEquals(1, draw.paintAlphaApplicationCount)
}

@Test
fun `unregistered mesh program is terminal before native work`() {
    val refusal = lower(drawMesh(program = unregisteredMeshProgram())).refused()
    assertEquals(
        GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered,
        refusal.code,
    )
}
```

- [ ] **Step 2: Run lowerer test and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*GPUPreparedVerticesLowererTest" --no-parallel
```

- [ ] **Step 3: Implement immutable draw contracts**

`GPUPreparedVerticesDraw` contains artifact, exact operation kind, material
program, transform snapshot, clip snapshot, final blend facts, bounds,
operation index, and provenance. Arrays and mutable public objects never
escape into the prepared draw.

- [ ] **Step 4: Implement transactional lowering**

Build all intermediate results locally. Call the packer and common material
compiler. Publish `Ready` only after geometry, material, transform, clip,
bounds, and blend all succeed.

For `DrawMesh` without a program, use the same internal vertices path as the
public `Canvas.drawMesh` normalization. Do not create a second no-program mesh
route.

- [ ] **Step 5: Prove refusals preserve canonical codes**

Add parameterized tests for every code in
`GPUPreparedVerticesRefusalCodes`. Assert operation index and deterministic
facts.

- [ ] **Step 6: Run lowerer, mapper, and packer suites**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPUPreparedVerticesPackerTest" \
  --tests "*GPUPreparedMeshProgramMapperTest" \
  --tests "*GPUPreparedVerticesLowererTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 7: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): lower prepared vertices and meshes"
```

---

### Task 6: Frame-local vertices inventory and budgets

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/PreparedVerticesFrameInventory.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesFramePreparer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/PreparedVerticesFrameInventoryTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesFramePreparerTest.kt`

**Interfaces:**

- Consumes: Task 5 ready draws.
- Produces: all-or-nothing `PreparedVerticesFrameInventory`, exact
  command/artifact mapping, deduplication, upload ranges, and aggregate budget
  evidence.

- [ ] **Step 1: Write inventory RED tests**

Test:

- identical geometry deduplicates to one artifact;
- different bytes never deduplicate;
- same artifact with different transforms/materials remains two commands;
- hash equality without byte/structure equality refuses;
- vertex/index/material/frame budgets;
- checked aligned ranges do not overlap;
- source command order is unchanged;
- first refusal exposes no partial inventory.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*PreparedVerticesFrameInventoryTest" \
  --tests "*GPUPreparedVerticesFramePreparerTest" --no-parallel
```

- [ ] **Step 3: Implement limits and all-or-nothing builder**

```kotlin
data class PreparedVerticesFrameInventoryLimits(
    val maxDraws: Int,
    val maxUniqueArtifacts: Int,
    val maxVertexBytes: Long,
    val maxIndexBytes: Long,
    val maxTotalUploadBytes: Long,
    val maxRuntimeUniformBytes: Long,
    val maxRuntimeChildren: Int,
)
```

Use checked addition and alignment. Record configured and effective limits plus
their capability source.

- [ ] **Step 4: Implement frame preparation before mapping**

`GPUPreparedVerticesFramePreparer.prepare()` follows the FP-05 pattern:
lower every vertices family operation, build one inventory, then call
`GPUOpMapper` with the complete inventory. A refusal maps no operations.

- [ ] **Step 5: Run inventory plus FP-04/FP-05 mapping regressions**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*PreparedVerticesFrameInventoryTest" \
  --tests "*GPUPreparedVerticesFramePreparerTest" \
  --tests "*GPUPreparedSurfaceFrameBuilderTest" \
  --tests "*GPUPreparedSurfaceFrameBuilderTextTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): inventory prepared vertices per frame"
```

---

### Task 7: Closed vertices semantic payload

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedVerticesPayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesSemanticBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedVerticesPayloadTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesSemanticBuilderTest.kt`

**Interfaces:**

- Consumes: Task 6 inventory and existing recording packet/analysis authority.
- Produces: `GPUDrawSemanticPayload.Vertices` with canonical hash integrity.

- [ ] **Step 1: Write payload integrity RED tests**

Mutate every canonical axis independently:

- artifact bytes/layout/topology;
- transform;
- material key/ABI/uniform bytes/children;
- primitive-color facts;
- clip identity;
- blend identity;
- target/scissor;
- capability hash;
- provenance.

Each mutation must change or invalidate the canonical hash. Changing a native
offset or cache-hit fact must not be representable in the semantic payload.

- [ ] **Step 2: Run payload tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPUPreparedVerticesPayloadTest" \
  --tests "*GPUPreparedVerticesSemanticBuilderTest" --no-parallel
```

- [ ] **Step 3: Add the closed payload**

```kotlin
data class GPUPreparedVerticesPayloadInput(
    val payloadRef: GPUDrawPayloadRef,
    val artifact: GPUPreparedVerticesUploadArtifact,
    val material: GPUPreparedMaterialProgram,
    val topology: GPUVertexMode,
    val transformBytes: List<Int>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val clipIdentity: String,
    val primitiveBlendIdentity: String?,
    val finalBlendIdentity: String,
    val capabilitySnapshotHash: String,
    val frameProvenance: GPUFrameProvenance,
)

class Vertices internal constructor(
    input: GPUPreparedVerticesPayloadInput,
) : GPUDrawSemanticPayload {
    override val canonicalType: String = "Vertices"
    override val payloadRef: GPUDrawPayloadRef = input.payloadRef.deepSnapshot()
    val artifact: GPUPreparedVerticesUploadArtifact = input.artifact
    val material: GPUPreparedMaterialProgram = input.material.preparedVerticesSnapshot()
    // exact immutable transform, clip, blend, bounds, provenance
    val canonicalHash: String = input.canonicalHash()
}

private fun GPUPreparedMaterialProgram.preparedVerticesSnapshot() =
    copy(
        uniformBytes = uniformBytes.toList(),
        sampledResources = sampledResources.map { resource ->
            GPUPreparedMaterialSampledResource(
                width = resource.width,
                height = resource.height,
                samplingFilterMode = resource.samplingFilterMode,
                alphaOnly = resource.alphaOnly,
                rgba8Bytes = resource.rgba8Bytes(),
                resourceKey = resource.resourceKey,
            )
        },
    )
```

Add a gatherer/factory that rejects malformed payloads instead of exposing a
partially valid instance.

- [ ] **Step 4: Implement semantic builder bijection**

Require exact bijection among:

- visual command IDs;
- prepared vertices inventory command IDs;
- recording analysis IDs;
- render packet IDs;
- semantic IDs.

Verify the normalized recording facts match the lowerer/inventory facts before
building the semantic.

- [ ] **Step 5: Run payload, semantic, image, and text semantic suites**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPUPreparedVerticesPayloadTest" \
  --tests "*GPUPreparedVerticesSemanticBuilderTest" \
  --tests "*GPUPreparedSurfaceSemanticBuilderTest" \
  --tests "*GPUPreparedTextSemanticBuilderTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test \
  kanvas/src/main kanvas/src/test
rtk git commit -m "feat(renderer): define prepared vertices semantics"
```

---

### Task 8: Parser-validated vertices WGSL and exact ABI

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShader.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/contracts/PreparedMaterialContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShaderTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/PreparedVerticesShaderAbiTest.kt`

**Interfaces:**

- Consumes: Task 7 layout/topology/material semantic.
- Produces: `GPUPreparedVerticesShaderProgram` with parser-reflected entry
  points, vertex attributes, varyings, bind groups, uniform ABI, and pipeline
  layout hash.

- [ ] **Step 1: Write eight-variant WGSL RED tests**

Generate and parse:

- triangles/strip;
- position only;
- position + color;
- position + UV;
- position + color + UV.

Assert exact shader locations, WGSL types, interpolation, transform uniform,
primitive color, local coordinates, fragment material function, and target
output.

- [ ] **Step 2: Run shader tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*PreparedVerticesShaderTest" \
  --tests "*PreparedVerticesShaderAbiTest" --no-parallel
```

- [ ] **Step 3: Implement a bounded shader assembler**

The assembler provides the canonical vertex stage and wraps the existing
prepared fragment material source. It must not alter the material's uniform
bytes, child functions, resources, or color semantics.

```kotlin
object PreparedVerticesShaderAssembler {
    fun assemble(
        layout: GPUVertexLayoutPlan,
        topology: GPUVertexMode,
        material: GPUPreparedMaterialProgram,
        hasPrimitiveColor: Boolean,
    ): GPUPreparedVerticesShaderResult
}

data class GPUPreparedVerticesShaderProgram(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val vertexLayoutHash: String,
    val bindingLayoutHash: String,
    val reflectedAbiHash: String,
    val pipelineKeyHash: String,
)

sealed interface GPUPreparedVerticesShaderResult {
    data class Ready(val program: GPUPreparedVerticesShaderProgram) :
        GPUPreparedVerticesShaderResult

    data class Refused(
        val code: String,
        val message: String,
    ) : GPUPreparedVerticesShaderResult
}
```

- [ ] **Step 4: Reflect and validate the final module**

Use wgsl4k parser/lowerer. Validate:

- entry points;
- attribute locations/types;
- uniform size/alignment;
- resource group/binding order;
- child bindings;
- vertex/fragment interface;
- output type.

Parser absence or ambiguity is a refusal. If wgsl4k behavior is incorrect,
create a minimized wgsl4k issue; do not add a hidden Kanvas workaround.

- [ ] **Step 5: Run shader and prepared-material tests**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*PreparedVerticesShader*" \
  --tests "*GPUPreparedMaterialProgram*" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): assemble prepared vertices wgsl"
```

---

### Task 9: Vertex/index resource plans and heterogeneous task graph

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUVerticesFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUVerticesFrameResourcePlanTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceVerticesTaskListBuilderTest.kt`

**Interfaces:**

- Consumes: Tasks 7-8 semantic and shader program.
- Produces: frame-owned vertex/index resource plans plus exact upload-before-draw
  task dependencies.

- [ ] **Step 1: Write resource and graph RED tests**

Assert:

- usage flags include `COPY_DST|VERTEX` and optional `INDEX`;
- aligned non-overlapping subranges;
- same artifact uploads once;
- every draw depends on exact artifact upload;
- sampled material uploads also precede the draw;
- paint order remains exact across Core/Image/Text/Vertices;
- budget refusal constructs no partial task list.

- [ ] **Step 2: Run tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUVerticesFrameResourcePlanTest" \
  --tests "*GPUPreparedSurfaceVerticesTaskListBuilderTest" --no-parallel
```

- [ ] **Step 3: Implement frame resource planning**

```kotlin
data class GPUVerticesFrameResourcePlan(
    val artifactKey: String,
    val vertexBuffer: GPUVertexBufferPlan,
    val indexBuffer: GPUIndexBufferPlan?,
    val uploadBeforeUseToken: String,
    val ownerScope: String = "PayloadOwnedCompletion",
)
```

Keep handles absent. Include expected device/buffer generations, usage,
alignment, ranges, byte counts, and invalidation facts.

- [ ] **Step 4: Extend the common task builder**

Accept `GPUDrawSemanticPayload.Vertices` alongside every FP-05 accepted
semantic. Build all plans and validate aggregate budgets before constructing
the output task collection.

- [ ] **Step 5: Run task-graph aggregate regressions**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedSurfaceFrameTaskListBuilderTest" \
  --tests "*GPUPreparedSurfaceVerticesTaskListBuilderTest" \
  --tests "*GPUVerticesFrameResourcePlanTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): plan prepared vertices resources"
```

---

### Task 10: Full-frame native preflight

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceVerticesNativePreflightTest.kt`

**Interfaces:**

- Consumes: Task 9 closed frame plan.
- Produces: accepted immutable native preflight plan or terminal diagnostic
  before any native seam call.

- [ ] **Step 1: Add a native seam counter and RED refusal matrix**

Parameterized cases cover every design refusal plus:

- canonical hash mismatch;
- stale device/buffer/registry generation;
- usage mismatch;
- overlapping/out-of-bounds ranges;
- upload missing or after draw;
- shader attribute mismatch;
- material/child ABI mismatch;
- target format/sample count mismatch;
- aggregate bytes overflow.

Each case asserts all counters remain zero:

```kotlin
assertEquals(0, seam.targetBorrowCount)
assertEquals(0, seam.bufferCreateCount)
assertEquals(0, seam.encoderCreateCount)
assertEquals(0, seam.queueWriteCount)
assertEquals(0, seam.submitCount)
```

- [ ] **Step 2: Run the preflight tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedSurfaceVerticesNativePreflightTest" --no-parallel
```

- [ ] **Step 3: Extend preflight in dependency order**

Validate:

1. frame/command bijection;
2. artifact hash and bytes;
3. topology/layout/counts;
4. transform/bounds/clip/blend;
5. material/program/child ABI;
6. resource generations/usages/ranges;
7. upload-before-use graph;
8. budgets;
9. target compatibility;
10. exact encoder/draw plan.

Return immutable accepted operands grouped by exact render scope.

- [ ] **Step 4: Prove FP-04/FP-05 preflight behavior unchanged**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUPreparedSurfaceNativePreflightTest" \
  --tests "*GPUPreparedSurfaceVerticesNativePreflightTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 5: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): preflight prepared vertices frames"
```

---

### Task 11: wgpu4k vertices materializer and close-once ownership

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializerTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest.kt`

**Interfaces:**

- Consumes: Task 10 accepted native operands.
- Produces: frame-owned buffers, pipeline/material bindings, ordered native
  draws, and transferable completion owners.

- [ ] **Step 1: Write native-fake RED tests**

Assert exact call order:

```text
create vertex/index buffers
write vertex/index bytes
begin render scope
set pipeline
set vertex buffer
set optional index buffer
set material bindings
set scissor
draw or drawIndexed
end scope
submit once
close on completion
```

Cover success and failure at each acquisition point. Every created object
closes exactly once; uncreated objects never close.

- [ ] **Step 2: Run materializer tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUWgpu4kPreparedVerticesRenderRunMaterializerTest" \
  --tests "*GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" --no-parallel
```

- [ ] **Step 3: Implement exact buffer upload**

Create buffers with preflight-approved sizes/usages. Use `queue.writeBuffer`
with exact aligned ranges. Never repack or rehash on the native hot path.

- [ ] **Step 4: Implement pipeline/material binding and draws**

Use exact preflight shader/layout/pipeline keys. Bind one vertex buffer and
optional index buffer; call `draw` or `drawIndexed` with preflight-approved
counts/offsets.

- [ ] **Step 5: Integrate the common owner ledger**

Vertex/index operands are `PayloadOwnedCompletion`. Pipeline/layout entries are
session-owned. Transfer all completion owners only after successful submission.
On failure, close acquired owners in reverse order.

- [ ] **Step 6: Run ownership and heterogeneous materializer suites**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*GPUWgpu4kPreparedVerticesRenderRunMaterializerTest" \
  --tests "*GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" \
  --tests "*Ownership*" --no-parallel
rtk git diff --check
```

- [ ] **Step 7: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "feat(renderer): materialize prepared vertices natively"
```

---

### Task 12: Compatible batching and telemetry

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/VerticesContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/vertices/PreparedVerticesBatchingTest.kt`
- Test: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/PreparedVerticesTelemetryTest.kt`

**Interfaces:**

- Consumes: Task 11 correct unbatched materialization.
- Produces: buffer coalescing and compatible run batching with measured work
  counters; correctness is unchanged if batching is disabled.

- [ ] **Step 1: Write compatibility and barrier RED tests**

Compatible only when pipeline/layout/topology/material ABI/target/blend/clip
scope permit it. Test barriers for clip, destination read, layer, filter,
sampled-resource upload, incompatible blend, and command order.

- [ ] **Step 2: Run batching tests and verify RED**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*PreparedVerticesBatchingTest" \
  --tests "*PreparedVerticesTelemetryTest" --no-parallel
```

- [ ] **Step 3: Implement aligned packed buffer subranges**

The batch planner computes checked, non-overlapping offsets and retains each
draw's exact first vertex/base index/base vertex. It does not merge across
barriers or reorder draws.

- [ ] **Step 4: Emit deterministic counters**

Record:

- draw count;
- unique artifacts;
- vertex/index bytes;
- fan expansion;
- buffer creations;
- upload count/bytes;
- packed subranges;
- pipeline/layout creations and reuses;
- compatible batches;
- `draw`/`drawIndexed`;
- encoder/submit/readback.

- [ ] **Step 5: Run batching, task graph, and materializer suites**

```bash
rtk proxy ./gradlew :gpu-renderer:test \
  --tests "*PreparedVerticesBatchingTest" \
  --tests "*PreparedVerticesTelemetryTest" \
  --tests "*GPUPreparedSurfaceVerticesTaskListBuilderTest" \
  --tests "*GPUWgpu4kPreparedVerticesRenderRunMaterializerTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/main gpu-renderer/src/test
rtk git commit -m "perf(renderer): batch prepared vertices safely"
```

---

### Task 13: Deterministic fixtures and independent CPU pixel oracle

**Files:**

- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesTestFixtures.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesTestFixturesTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesPixelOracleTest.kt`

**Interfaces:**

- Consumes: public geometry/material semantics, not production packer or WGSL.
- Produces: immutable fixtures, independent barycentric CPU pixels, and exact
  comparison statistics used by native tests.

- [ ] **Step 1: Write fixture isolation RED tests**

Every fixture access returns copied positions, colors, UVs, indices, uniform
bytes, and image pixels.

- [ ] **Step 2: Write CPU oracle RED tests**

Use hand-computable triangles to validate:

- edge inclusion rule;
- barycentric color interpolation;
- perspective-free UV interpolation;
- nearest/linear sampling;
- triangle strip winding;
- fan canonicalization;
- primitive blend;
- paint alpha once;
- final blend;
- clip and transform.

- [ ] **Step 3: Implement independent oracle**

Do not import the production packer, shader builder, or materializer. Use
double precision for barycentric arithmetic, then apply the declared
float/UNORM quantization boundary.

- [ ] **Step 4: Implement pixel comparison**

```kotlin
data class GPUPreparedVerticesPixelDelta(
    val maxChannelDelta: Int,
    val differingChannels: Int,
    val comparedChannels: Int,
) {
    val matchesWithinOneLsb: Boolean
        get() = maxChannelDelta <= 1
}
```

- [ ] **Step 5: Run all fixture/oracle tests**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*GPUPreparedVerticesTestFixturesTest" \
  --tests "*GPUPreparedVerticesPixelOracleTest" --no-parallel
rtk git diff --check
```

- [ ] **Step 6: Commit**

```bash
rtk git add kanvas/src/test
rtk git commit -m "test(surface): add prepared vertices pixel oracles"
```

---

### Task 14: Native pixels, MeshProgram, mixed-frame, and refusal smokes

**Files:**

- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedVerticesNativeSmokeTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedVerticesRefusalMatrixTest.kt`

**Interfaces:**

- Consumes: Tasks 1-13 complete prepared route and CPU oracle.
- Produces: native WebGPU pixel, ownership, mixed-order, and no-allocation
  refusal evidence while the product gate remains closed.

- [ ] **Step 1: Add native capability assumptions**

Report adapter/device, effective limits, target format, index-format support,
and skip reason. Do not represent a skip as passing native evidence.

- [ ] **Step 2: Add one smoke per accepted semantic family**

Cover:

- unindexed triangle;
- indexed triangle;
- strip;
- canonicalized fan;
- color interpolation with partial alpha;
- image UV nearest and linear;
- position-local gradient;
- affine transform and clip;
- registered MeshProgram uniforms;
- registered shader/color-filter/blender children;
- supported final blend classes.

Compare against Task 13 oracle with exact policy or
`maxChannelDelta <= 1` only where documented.

- [ ] **Step 3: Add heterogeneous one-submit smoke**

Render ordered Core → Image → Text → Vertices → Core with overlap. Assert
pixel order, one encoder scope plan where compatible, one queue submit, one
readback, upload-before-use, and close-once ownership.

- [ ] **Step 4: Add the canonical refusal matrix**

Execute every currently reachable public refusal through Surface → lowering →
inventory → semantic → recording → preflight. For preflight refusals, assert
zero target borrow/allocation/write/submit and no legacy invocation.

- [ ] **Step 5: Run native tests serially**

```bash
rtk proxy ./gradlew :gpu-renderer:test :kanvas:test \
  --tests "*GPUWgpu4kPreparedVerticesNativeSmokeTest" \
  --tests "*GPUPreparedSurfaceProductNativeSmokeTest" \
  --tests "*GPUPreparedVerticesRefusalMatrixTest" --no-parallel
rtk git diff --check
```

If wgpu4k crashes or violates its API contract, minimize the failure and open
a wgpu4k issue. Record the URL in Task 16. Do not add a hidden workaround.

- [ ] **Step 6: Commit**

```bash
rtk git add gpu-renderer/src/test kanvas/src/test
rtk git commit -m "test(surface): prove prepared vertices natively"
```

---

### Task 15: Atomic product route and legacy removal

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPURenderer.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt`
- Test: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`

**Interfaces:**

- Consumes: accepted native and refusal evidence from Task 14.
- Produces: product routing for `DrawVertices`/`DrawMesh`, removal of the
  `Vertices` legacy family, and deletion of superseded legacy branches.

- [ ] **Step 1: Write the product cutover RED tests**

Assert:

- vertices/mesh frames choose prepared product routing;
- unsupported vertices/mesh return their exact terminal code;
- no accepted or refused vertices/mesh command increments legacy counters;
- mixed frames remain prepared;
- all non-vertices families retain their FP-05 behavior.

- [ ] **Step 2: Run router tests and verify RED**

```bash
rtk proxy ./gradlew :kanvas:test \
  --tests "*GPUPreparedSurfaceFrameGateTest" \
  --tests "*GPUPreparedSurfaceProductRouterTest" --no-parallel
```

- [ ] **Step 3: Wire the complete preparation before mapper/recorder**

The builder prepares text and vertices inventories transactionally, then maps
all operations once. It gathers image, text, and vertices semantics into the
common bijective map.

- [ ] **Step 4: Remove the legacy family**

Delete `LegacyDisplayOpFamily.Vertices`, its allowlist entry, its prepared
family diagnostic, and vertices/mesh production branches that convert to
paths or use the special textured route.

- [ ] **Step 5: Search production sources**

```bash
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.vertices|LegacyDisplayOpFamily\\.Vertices|gpu_textured_vertices|unsupported\\.mesh\\.program" \
  kanvas/src/main gpu-renderer/src/main
```

Expected:

- no legacy family or legacy textured route;
- no old broad `unsupported.mesh.program`;
- only canonical specific refusal authorities remain.

- [ ] **Step 6: Run product and regression suites**

```bash
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
rtk git diff --check
```

- [ ] **Step 7: Commit**

```bash
rtk git add kanvas/src/main kanvas/src/test
rtk git commit -m "feat(surface): activate prepared vertices routing"
```

---

### Task 16: FP-06 evidence, aggregate validation, and independent review

**Files:**

- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-06-prepared-vertices-mesh-route.md`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Modify only if public behavior changed:
  - `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`
  - `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`

**Interfaces:**

- Consumes: every Task 1-15 commit, test result, native fact, pixel delta,
  counter, refusal, and review finding.
- Produces: bounded FP-06 closure evidence; FP-07 remains the next active
  product task.

- [ ] **Step 1: Write the evidence report from measured output**

Record:

- exact commits and commands;
- test counts and native host/device facts;
- accepted topology/layout/color/UV/index/material/MeshProgram rows;
- exact refusal matrix;
- CPU/GPU pixel deltas;
- upload-before-draw graph;
- buffer/pipeline/batch/draw/submit/readback counters;
- ownership counters;
- explicit FP-09 and FP-11 non-claims;
- any wgsl4k or wgpu4k issue URL and minimized reproduction.

- [ ] **Step 2: Run focused module aggregates**

```bash
rtk proxy ./gradlew :font:core:test :font:glyph:test :font:gpu-api:test \
  :font:test :gpu-renderer:test :kanvas:test --no-parallel
```

Expected: all relevant suites pass. Reproduce and document any unrelated
pre-existing failure separately; do not hide or relabel it.

- [ ] **Step 3: Run source and diff hygiene**

```bash
rtk git diff --check
rtk rg -n \
  "legacy\\.surface\\.prepared\\.family\\.vertices|LegacyDisplayOpFamily\\.Vertices|gpu_textured_vertices" \
  kanvas/src/main gpu-renderer/src/main
rtk git status --short
```

Expected: searches return no production match and only intended evidence/spec
files remain uncommitted.

- [ ] **Step 4: Request two independent reviews**

Use `superpowers:requesting-code-review`:

1. spec-compliance review against every FP-06 design criterion and refusal;
2. technical review comparing Graphite+Dawn pragmatism, topology conversion,
   vertex/color/UV interpolation, material child ABI, batching, preflight,
   rollback, and completion ownership.

Classify findings as Critical, Important, Minor, or invalid. Fix every
legitimate Critical/Important finding, rerun the smallest reproducer, then the
focused aggregate. Repeat review until no legitimate Critical/Important
finding remains.

- [ ] **Step 5: Update the active roadmap only after clean review**

Set FP-06 to `completed` and link the evidence report. Keep FP-07 pending until
its implementation begins. Do not mark FP-09, FP-10, or FP-11 complete.

- [ ] **Step 6: Commit evidence**

```bash
rtk git add reports/upstream-rebaseline .upstream/specs/gpu-renderer
rtk git commit -m "docs(surface): close prepared vertices route"
```

- [ ] **Step 7: Final branch verification**

```bash
rtk git status --short --branch
rtk git log --oneline --decorate -24
rtk git diff --check origin/codex/graphite-dawn-frame-plan-design...HEAD
```

Expected: clean worktree, ordered Task 1-16 commits, no `.superpowers/sdd/`,
and no legitimate unresolved Critical/Important finding.

---

## Plan Self-Review Checklist

- [ ] Every design requirement maps to at least one task.
- [ ] `DrawVertices`, no-program `DrawMesh`, and registered `MeshProgram` each
  have positive and negative evidence.
- [ ] Runtime-effect children keep exact name, role, uniforms, resources, and
  ABI identity.
- [ ] No task creates a second material, blend, clip, runtime-effect, or
  resource authority.
- [ ] No task introduces a general Mesh IR or 3D contract.
- [ ] No native handle exists before Task 11 materialization.
- [ ] Full-frame preflight precedes every target borrow and allocation.
- [ ] Every refusal is terminal and allocation-free.
- [ ] Vertex/index buffers remain frame-owned before FP-09.
- [ ] FP-04 and FP-05 regression suites run before product cutover.
- [ ] GM regeneration and performance equivalence remain FP-11 non-claims.
