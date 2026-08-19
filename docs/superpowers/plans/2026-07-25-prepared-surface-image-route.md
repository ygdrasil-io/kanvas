# Prepared Surface Image Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Do not start a later task until the current task has passed its RED/GREEN checks, code review, and exact-file commit.

**Goal:** Migrate `DrawImage`, `DrawImageNine`, `DrawImageLattice`, and `DrawAtlas` to one handle-free prepared Surface frame route with native WebGPU texture/sampler ownership, exact mixed-draw order, affine atlas geometry, and route-tagged pixel/alpha evidence.

**Architecture:** First make decoded image alpha, color, orientation, provenance, and pixel layout explicit, then snapshot them into an immutable tight premultiplied-RGBA8 artifact. Build one closed sampled-image semantic and one heterogeneous task/resource plan. Before mixed-frame work, consolidate Tasks 1-5 around one reflected ABI112 identity, command-exact source mapping, Task-4 resource sharing, immutable device-generation ownership, canonical diagnostics, and measured pipeline specialization. Prove the sRGB source/sample/store contract against an independent oracle and remove destination CPU snapshot/upload from the main route. Pure preflight then validates the complete core/image frame before any native factory call; one mixed-frame materializer owns target, readback, late surface, resource ledger, and final draft. Product routing remains closed until `DrawImage`, nine/lattice, and affine atlas have all passed native pixel evidence, then all four operations are admitted atomically and `Images` is removed from the legacy allowlist.

**Tech Stack:** Kotlin/JVM, JUnit 5 and `kotlin.test`, Gradle 9.2 wrapper, Eclipse Temurin JDK 25, wgsl4k parser/reflection, wgpu4k/WebGPU.

## Global Constraints

- Follow `AGENTS.md`, `.upstream/target/high-performance-wgsl-pipeline-target.md`, `.upstream/target/skia-like-realtime-renderer-target.md`, and `.upstream/specs/skia-like-realtime/README.md`.
- Treat `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md` as the active FP-04 acceptance and `docs/superpowers/specs/2026-07-25-prepared-surface-image-route-design.md` as the approved design.
- Use these Draft specs only as bounded technical references: `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`, `22-image-bitmap-codec-pipeline.md`, `29-color-management-pipeline.md`, `30-coordinate-transform-bounds-policy.md`, and `31-material-source-paint-pipeline.md`.
- Do not treat their historical sequencing, complete codec matrix, general image-shader/material pipeline, or spec 29's `Initial SDR Implementation` as FP-04 acceptance. The active target and FP-04 design override them.
- Do not port Ganesh or Graphite and do not build a SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend. CPU code may snapshot/convert decoded pixels and provide an oracle, but may not rasterize an unsupported draw into a compatibility texture.
- Keep the frame plan handle-free. No `GPUTexture`, view, sampler, buffer, bind group, or pipeline is created until the complete mixed frame passes pure native preflight.
- Use exactly one `GPUPreparedNativeFrameDraft` and one completion owner for a mixed frame. Internal route helpers may return run operands/resources, never child drafts.
- Decoded source bytes are first snapshotted as immutable premultiplied RGBA8. Task 5.4 derives a separately keyed physical sampling artifact. The expected color candidate unpremultiplies to straight encoded sRGB bytes, uses sRGB source sampling, re-premultiplies in linear shader space, and stores through `RGBA8UnormSrgb`; A8 coverage stays linear `RGBA8Unorm`. Do not preserve `RGBA8Unorm + EncodedPremulSrgb` without oracle evidence.
- Accept only decoded SDR sRGB `RGBA_8888`, `BGRA_8888`, and `ALPHA_8` with explicit `AlphaType.PREMUL` or `AlphaType.OPAQUE` authority. Never infer premultiplication from `ColorType`.
- Keep source stride, normalized tight RGBA8 stride, and native upload stride distinct. Padding bytes are never pixels and never enter the content hash.
- Accept clamp sampling with `NEAREST` and `LINEAR`. Refuse cubic, anisotropic, mipmapped, repeat, mirror, decal, perspective, HDR/YUV, imported textures, unresolved orientation/profile conversion, and unproved alpha with stable codes.
- Preserve order exactly. Nine/lattice expansion and atlas batch validation are transactional; no valid prefix survives a refusal.
- Keep pixels, source IDs, hashes, artifact keys, generations, and native handles out of pipeline keys. Serialize every canonical hash field explicitly; never use a Kotlin `toString()` as a protocol.
- Keep upload-artifact, sampler-descriptor, binding, uniform-allocation, pipeline, and native-generation identities separate.
- Share a texture/view per artifact, a sampler per descriptor, and a bind group per binding identity within one frame. Use aligned dynamic uniform offsets so different geometry/tints never alias.
- Treat `sourceId` as provenance, never as a one-draw uniqueness constraint. Map each prepared command to its exact source operation and share only by immutable artifact/binding keys.
- Use one reflected group-0 image binding authority: dynamic uniform binding 0 with 112-byte minimum size, sampled texture binding 1, and sampler binding 2. Builder, preflight, shader, cache, and materializer must consume its exact identity.
- A native session cache is immutable with respect to its `GPUDevice` and generation. A stale generation refuses; the runtime closes and reconstructs the cache for the new device.
- Preserve the stable FP-04 code through Surface, recording, preflight, and native layers. Add context in diagnostic facts; never prefix or rename the code.
- Classify every image pipeline-key axis as layout-, code-, pipeline-state-affecting, or uniform-only. Uniform-only axes remain out of the key unless a recorded measurement justifies specialization.
- Do not add an inter-frame image texture/view/sampler cache. A session cache may retain only WGSL modules, pipeline layouts, and pipelines and must close/invalidate them on device-generation loss.
- The immutable CPU snapshot of decoded source pixels is allowed. A CPU snapshot of the destination followed by a compatibility upload is forbidden on the FP-04 main route.
- Keep the gate closed through Tasks 1-5, 5.1-5.4, and 6-9. Task 10 admits all four image operations and removes legacy `Images` in one cutover.
- Preserve and never stage user-local `buildSrc/build.gradle.kts`, `gradle/verification-metadata.xml`, or `hs_err_pid*.log` files when present.
- Dependency-verification metadata and reproducible-build policy are outside FP-04. Every Gradle command uses `--dependency-verification=off`.
- Every Gradle block uses the repository wrapper for the current host, the JDK 25 toolchain, `--max-workers=1`, and a fresh no-daemon JVM for native checks.
- Git commits use command-scoped `user.name=ygdrasil-io` and `user.email=alexandre.mommers@gmail.com`.

## Stable FP-04 Refusal Table

| Boundary | Stable code |
|---|---|
| Missing pixels | `unsupported.image.pixels_missing` |
| Empty/overflowing dimensions | `unsupported.image.dimensions` |
| Invalid source row stride/length | `unsupported.image.pixel.row_stride`, `unsupported.image.pixel.length` |
| Unsupported channel format | `unsupported.image.pixel.format` |
| Unproved/unpremultiplied RGBA/BGRA alpha | `unsupported.image.alpha_interpretation` |
| Non-sRGB/unresolved profile or gamut | `unsupported.color.image_profile_conversion`, `unsupported.color.gamut_transform` |
| Unresolved orientation | `unsupported.image.orientation` |
| YUV/YUVA or HDR/gainmap | `unsupported.color.yuv_conversion`, `unsupported.color.hdr_transfer` |
| Encoded/animated source at this boundary | `unsupported.image.codec.unregistered`, `unsupported.image.animation` |
| Imported texture | `unsupported.texture.import_unvalidated` |
| Upload budget/texture limit | `unsupported.image.upload.budget_exceeded`, `unsupported.image.texture_limit` |
| Mipmap/cubic/anisotropic | `unsupported.image.mip_required`, `unsupported.image.sampling_cubic`, `unsupported.image.sampling_anisotropic` |
| Repeat/mirror/decal | `unsupported.image.tile_mode` |
| Perspective/singular transform | `unsupported.image.perspective_sampling` |
| Invalid nine/lattice geometry | `unsupported.image.nine_geometry`, `unsupported.image.lattice_geometry` |
| Atlas array mismatch/invalid sprite | `unsupported.image.atlas.array_lengths`, `unsupported.image.atlas.geometry` |
| Unsupported atlas source blend | `unsupported.image.atlas.source_blend` |
| Stale device/target or incomplete binding | `unsupported.image.native_generation`, `unsupported.image.native_binding` |
| Missing/invalid WGSL capability | `unsupported.image.wgsl_validation` |

---

### Task 1: Add explicit alpha authority and build the immutable upload artifact

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/image/ImageTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/image/BitmapTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/ImageEncoderTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`
- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSource.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceTest.kt`

**Interfaces:**

```kotlin
data class Image(
    val width: Int,
    val height: Int,
    val colorType: ColorType = ColorType.RGBA_8888,
    val sourceId: String,
    val pixels: ByteArray? = null,
    val colorSpace: ColorSpace = ColorSpace.SRGB,
    val alphaType: AlphaType = AlphaType.UNPREMUL,
)

enum class GPUPreparedImageSourceClass { DecodedCpu, Encoded, Animated, Yuv, Hdr, Imported }
enum class GPUPreparedImageSourceFormat { Rgba8, Bgra8, A8, Unsupported }
enum class GPUPreparedImageProfile { Srgb, Other, Unresolved }
enum class GPUPreparedImageOrientation { AppliedIdentity, Unresolved }
enum class GPUPreparedImageProvenance { CallerPixels, SurfaceReadback, RegisteredDecode }

class GPUPreparedImageSourceInput(
    val sourceClass: GPUPreparedImageSourceClass,
    val sourceId: String,
    val width: Int,
    val height: Int,
    val sourceFormat: GPUPreparedImageSourceFormat,
    val alphaType: AlphaType,
    val sourceRowBytes: Long,
    val profile: GPUPreparedImageProfile,
    val orientation: GPUPreparedImageOrientation,
    val provenance: GPUPreparedImageProvenance,
    val sourceGeneration: Long,
    pixelBytes: ByteArray?,
) {
    internal fun snapshotBytesOrNull(): ByteArray?
}

data class GPUPreparedImagePixelLayout(
    val sourceRowBytes: Long,
    val normalizedRgba8RowBytes: Long,
    val rowCount: Int,
)

class GPUPreparedImageUploadArtifact internal constructor(
    val key: GPUImageUploadArtifactKey,
    val width: Int,
    val height: Int,
    val pixelLayout: GPUPreparedImagePixelLayout,
    val sourceGeneration: Long,
    val contentHash: String,
    val alphaOnly: Boolean,
    val colorInterpretation: String,
    rgba8PremulBytes: ByteArray,
) {
    fun tightRgba8BytesForUpload(): ByteArray
}

sealed interface GPUPreparedImageArtifactResult {
    data class Ready(val artifact: GPUPreparedImageUploadArtifact) : GPUPreparedImageArtifactResult
    data class Refused(val code: String, val facts: Map<String, String>) : GPUPreparedImageArtifactResult
}

object GPUPreparedImageArtifactFactory {
    fun prepare(
        input: GPUPreparedImageSourceInput,
        maxUploadBytes: Long = 64L * 1024L * 1024L,
    ): GPUPreparedImageArtifactResult
}
```

- [ ] **Step 1: Write failing API and artifact tests**

Test all `Image` copy/equality/hash/reinterpret paths with explicit `alphaType`.
Test Picture format v5 round-trip of `PREMUL`, `OPAQUE`, and `UNPREMUL`, plus
v1-v4 compatibility defaulting to `UNPREMUL`. Test that surface snapshots and
`RenderResult.toImage()` preserve their known premultiplied authority, while
`Bitmap.toImage()` remains explicitly `UNPREMUL`. Test that the factory copies
bytes at entry and returns copies on access. Test width `3` fixtures:

```kotlin
assertEquals(3L, a8Artifact.pixelLayout.sourceRowBytes)
assertEquals(12L, a8Artifact.pixelLayout.normalizedRgba8RowBytes)
assertContentEquals(
    byteArrayOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3),
    a8Artifact.tightRgba8BytesForUpload(),
)
```

Also prove BGRA conversion, opaque RGBA acceptance, premultiplied RGBA copy, `UNPREMUL` refusal, and every source/profile/orientation refusal in the table. `OPAQUE` RGBA/BGRA is accepted only when every stored alpha byte is `255`; `ALPHA_8` requires `PREMUL`. Equal physical bytes and metadata yield equal upload keys; changed content, dimensions, source stride, format, alpha authority, provenance, or generation changes the key.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.image.ImageTest" `
  --tests "org.graphiks.kanvas.image.BitmapTest" `
  --tests "org.graphiks.kanvas.picture.PictureTest" `
  --tests "org.graphiks.kanvas.surface.ImageEncoderTest" `
  --tests "org.graphiks.kanvas.surface.SurfaceTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: the new alpha/source/artifact contracts are unresolved.

- [ ] **Step 3: Implement the source boundary**

Add `alphaType` at the end of `Image`'s constructor and `Image.fromPixels`,
preserve it in `reinterpretColorSpace`, and include it in equality/hash. Do not
silently change existing caller bytes. Bump Picture serialization to format v5,
write/read the enum, and retain v1-v4 decoding with the conservative
`UNPREMUL` default. Mark only proven render-result/surface snapshot producers
as `PREMUL`; keep Bitmap-derived images explicitly `UNPREMUL`.

`GPUPreparedSurfaceImageSource.prepare(image)` maps exact `ColorSpace.SRGB`, source format, alpha authority, tight source row bytes, and decoded CPU provenance. It accepts `PREMUL`/`OPAQUE`; `ALPHA_8` must carry explicit `PREMUL`. It refuses `UNPREMUL` rather than guessing.

The factory executes:

```text
copy nullable source bytes
-> classify unsupported source/profile/orientation/alpha
-> validate dimensions, source stride, exact source length, and budget
-> convert each source row to tight premultiplied RGBA8
-> SHA-256 only the normalized pixel bytes
-> serialize version + hash + dimensions + both strides + format + alpha
   + profile + orientation + generation into GPUImageUploadArtifactKey
```

Use `Math.multiplyExact`; never call `Image.expandToRgbaForGpu()`. Raw pixels, padding, and `sourceId` never appear in a pipeline key.

- [ ] **Step 4: Run GREEN and regressions**

Rerun Step 2, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.images.ImageAcceptanceTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUImagePixelsTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/image/ImageTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/image/Bitmap.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/image/BitmapTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/picture/PictureTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/ImageEncoder.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/ImageEncoderTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/Surface.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/SurfaceTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSource.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSourceTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): snapshot authoritative image pixels"
```

Expected: source and upload identity are proven; product gate unchanged.

---

### Task 2: Define the closed sampled-image semantic and canonical keys

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayloadTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`

**Interfaces:**

```kotlin
enum class GPUPreparedImageGeometryClass { Rect, Quad }
enum class GPUPreparedImageSampling { Nearest, Linear }
enum class GPUPreparedAtlasSourceBlend { Src, Dst, SrcOver, Plus, Modulate }

data class GPUPreparedImageVertex(val x: Float, val y: Float, val u: Float, val v: Float)

class GPUPreparedImageGeometry internal constructor(
    val geometryClass: GPUPreparedImageGeometryClass,
    vertices: List<GPUPreparedImageVertex>,
    indices: List<Int>,
)

data class GPUPreparedImagePipelineKey(
    val geometryAbi: String,
    val alphaOnly: Boolean,
    val atlasColorMode: String,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
    val destinationBlendState: String,
    val clipClass: String,
    val targetFormat: String,
    val bindingLayoutHash: String,
)

data class GPUPreparedImagePayloadInput(
    val payloadRef: GPUDrawPayloadRef,
    val artifact: GPUPreparedImageUploadArtifact,
    val geometry: GPUPreparedImageGeometry,
    val sampling: GPUPreparedImageSampling,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>?,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val blendPlanIdentity: String,
    val frameProvenance: GPUFrameProvenance,
)
```

Add `GPUDrawSemanticPayload.SampledImage`. Its canonical hash includes the artifact key and every semantic field through explicit ordered serializers. Its pipeline key excludes artifact identity, content, source ID, generation, sampling filter, and uniform values. Nearest/linear select different sampler descriptors later but the same pipeline when all structural facts match.

- [ ] **Step 1: Write failing semantic/hash tests**

Prove:

- different content changes artifact/canonical hash but not pipeline key;
- nearest/linear changes semantic hash but not pipeline key;
- four positions/UVs survive rotation/reflection/skew without bounding-box reduction;
- lists/colors are copied and validated as finite premultiplied values;
- fixed indices are `0,1,2,0,2,3`;
- frame hash/dump contains artifact identity and semantic facts but never pixels;
- no hash uses `pipelineKey.toString()`;
- only the closed atlas blend enum is representable.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: sampled-image types are unresolved.

- [ ] **Step 3: Implement semantic integrity and analysis**

Validate finite geometry, nonempty target/scissor contained in target, canonical fixed indices, premultiplied colors, accepted clamp/nearest/linear sampling, explicit source bounds, and affine-only transforms. Extend all exhaustive frame hash/dump/analysis `when` expressions. Draw-image analysis refuses cubic, mipmap, anisotropy, non-clamp tile modes, missing pixel/alpha facts, perspective, and singular transforms with the table codes. Do not claim a native route yet.

- [ ] **Step 4: Run GREEN**

Rerun Step 2; expected `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/PayloadContracts.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayloadTest.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): define sampled image semantics"
```

Expected: handle-free semantic contract committed; gate unchanged.

---

### Task 3: Build the heterogeneous semantic map and logical frame task list

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilderTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilderTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlanTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`

**Interfaces:**

```kotlin
sealed interface GPUPreparedSurfaceSemanticGatherResult {
    data class Gathered(
        val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    ) : GPUPreparedSurfaceSemanticGatherResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceSemanticGatherResult
}

data class GPUPreparedSurfaceFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    val readbackRequestId: GPUReadbackRequestID,
)

sealed interface GPUPreparedSurfaceFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUPreparedSurfaceFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameResult
}

class GPUPreparedImageUploadLayout internal constructor(
    val logicalBytesPerRow: Long,
    val bytesPerRow: Long,
    val rowsPerImage: Int,
    val width: Int,
    val height: Int,
    paddedUploadBytes: ByteArray,
) {
    fun bytesForUpload(): ByteArray
}

data class GPUPreparedImageUniformAllocation(
    val packetId: String,
    val offset: Long,
    val size: Long,
)

data class GPUImageBindingRequest(
    val packetId: String,
    val artifactKey: GPUImageUploadArtifactKey,
    val texture: GPUTextureDescriptor,
    val view: GPUTextureViewDescriptor,
    val sampler: GPUSamplerDescriptor,
    val bindingLayoutHash: String,
    val uniformAllocation: GPUPreparedImageUniformAllocation,
)

data class GPUImageFrameResourcePlan(
    val stagingRef: GPUFrameBufferRef,
    val textureRef: GPUTextureResourceRef,
    val textureDescriptor: GPUTextureDescriptor,
    val uploadLayout: GPUPreparedImageUploadLayout,
    val bindingRequests: List<GPUImageBindingRequest>,
)

data class GPURecordedImageUpload(
    val taskId: GPUTaskID,
    val resources: GPUImageFrameResourcePlan,
)
```

> **Package-boundary correction (2026-07-27):** the resource plan is a
> handle-free resource descriptor and therefore must not own or import
> `GPUTaskID`. Recording owns task identity through `GPURecordedImageUpload`;
> the resource builder is `buildImageFrameResourcePlanFromBindings` and returns
> only `GPUImageFrameResourcePlan`. This explicitly supersedes the earlier
> `GPUPreparedImageFrameResourcePlan.uploadTaskId` sketch without changing the
> one-upload-per-artifact or upload-before-consumer requirements.

- [ ] **Step 1: Write failing mixed semantic/task tests**

Build synthetic `core -> image -> core` and `image -> core -> image` requests without opening the product gate. Assert:

```kotlin
assertEquals(listOf(0, 1, 2), gathered.semanticsByCommandId.keys.toList())
assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semanticsByCommandId.getValue(0))
assertIs<GPUDrawSemanticPayload.SampledImage>(gathered.semanticsByCommandId.getValue(1))
```

Assert unique command IDs, exact paint order, contiguous render runs only,
`Load` after the first target-writing run, one exact resource plan and logical
upload task per artifact, upload dependency before every consuming run, no
image resource use for solid packets, and atomic refusal for a
missing/extra/duplicate semantic. Width-3 A8/BGRA fixtures prove logical RGBA8
stride, backend-aligned upload stride, zero padding, texture usage flags,
binding slots, and aligned dynamic uniform allocations.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlanTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceSemanticBuilderTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: heterogeneous builders are unresolved.

- [ ] **Step 3: Implement ordered heterogeneous assembly**

Keep `GPUCorePrimitivePreparedFrameTaskListBuilder` as a compatibility wrapper around shared task assembly. Validate the complete semantic map and budget first; only then create ordered resource preparation, upload, render, readback, and output tasks. Group only contiguous packets of the same route. Preserve core clip/blend/coverage authorities and the then-current `validateEncodedPremulSrgbOutput()`. Task 3 records the baseline image interpretation `EncodedPremulSrgb` and physical `RGBA8Unorm`; Task 5.4 must replace these facts everywhere with the oracle-proven sRGB contract before any native pixel or admission claim.

Construct `GPUImageFrameResourcePlan` before emitting image tasks, then attach
its recording-owned `GPUTaskID` through `GPURecordedImageUpload`.
`GPUTask.Upload` consumes the association's exact staging/texture/layout authority; later
tasks may not reconstruct labels, strides, usages, binding slots, or uniform
offsets independently.

Add stable dump facts:

Task-3 baseline dump, explicitly superseded by Task 5.4:

```text
image.upload.format=RGBA8Unorm
image.target.format=RGBA8Unorm
image.color.interpretation=EncodedPremulSrgb
image.attachment.srgbConversion=false
```

- [ ] **Step 4: Run GREEN and builder regressions**

Rerun Step 2, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameExecutorTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilder.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceSemanticBuilderTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilderTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlanTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUCorePrimitivePreparedFrameTaskListBuilder.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUCorePrimitiveSemanticBuilder.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): build heterogeneous surface tasks"
```

Expected: mixed logical frames build through direct test inputs; gate unchanged.

---

### Task 4: Preflight texture uploads and define frame-owned native keys

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlanTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureSamplerMaterializationProviderTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageUploadScopeTest.kt`

**Interfaces:**

```kotlin
data class GPUPreparedImageUploadKey(
    val artifactKey: GPUImageUploadArtifactKey,
    val deviceGeneration: Long,
    val textureDescriptorHash: String,
    val viewDescriptorHash: String,
)

data class GPUPreparedImageSamplerKey(
    val deviceGeneration: Long,
    val descriptorHash: String,
)

data class GPUPreparedImageBindingKey(
    val layoutHash: String,
    val uploadKey: GPUPreparedImageUploadKey,
    val samplerKey: GPUPreparedImageSamplerKey,
)

internal interface GPUPreparedImageNativeHandleFactory {
    fun createTexture(request: GPUImageFrameResourcePlan): GPUTexture
    fun createTextureView(texture: GPUTexture, request: GPUImageFrameResourcePlan): GPUTextureView
    fun createSampler(descriptor: GPUSamplerDescriptor): GPUSampler
    fun createUniformBuffer(size: Long): GPUBuffer
    fun createBindGroup(
        request: GPUImageBindingRequest,
        uniformBuffer: GPUBuffer,
        textureView: GPUTextureView,
        sampler: GPUSampler,
    ): GPUBindGroup
}

internal interface GPUPreparedImageNativeResourceSet : AutoCloseable {
    fun uploadKey(artifactKey: GPUImageUploadArtifactKey): GPUPreparedImageUploadKey
    fun texture(artifactKey: GPUImageUploadArtifactKey): GPUPreparedNativeTextureOperand
    fun binding(packetId: String): GPUPreparedNativeBindGroupOperand
    fun dynamicUniformOffset(packetId: String): Long
}
```

`GPUPreparedImageNativeResourceSet` is a frame-owned lookup of opaque texture/view/sampler/binding/uniform operands plus a reverse-order release ledger. It is created only after Task 6's full-frame preflight; Task 4 defines and unit-tests the factory seam but does not call it from production routing.

- [ ] **Step 1: Write failing layout/key/resource-plan tests**

Assert:

- width-3 A8/BGRA artifacts keep source and logical RGBA8 strides distinct;
- native `bytesPerRow` follows the backend-required alignment, padding is zeroed, and padding never becomes a sampled pixel or hash input;
- `GPUTask.Upload` names the exact staging buffer, destination texture, `GPUUploadLayout`, preparation refs, copy-destination/sample usages, and payload resource slots;
- same artifact/different sampler gives one upload key, two sampler/binding keys;
- same artifact/sampler/different uniform values gives one binding key and two aligned dynamic offsets;
- device generation changes every native key;
- active attachment, usage, limit, owner, or generation mismatches refuse before the fake factory is called;
- fake-factory partial failure closes all created handles once in reverse order.
- `GPUUploadDestinationKind.Texture` propagates unchanged from `GPUTask.Upload`
  through `GPUFrameStep.UploadResourceStep`, `PreparedGPUFrame` scope commands,
  `GPUFramePreflighter` keys, and the later native operand seal;
- buffer uploads retain `GPUUploadDestinationKind.Buffer` and their existing
  buffer-to-buffer command/key contract.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlanTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageUploadScopeTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: native key/provider types and the texture-upload scope discriminant
are unresolved.

- [ ] **Step 3: Implement pure resource planning and texture-upload scope identity**

Generate tight or padded upload bytes only from the artifact's tight RGBA8 copy. Split the existing combined texture/sampler cache identity for the prepared image lane. Preserve non-image behavior.

Add a closed `GPUUploadDestinationKind { Buffer, Texture }` to
`GPUTask.Upload` and propagate it into `GPUFrameStep.UploadResourceStep`.
`GPUFramePlanner`/`PreparedGPUFrame` must emit `writeTexture` for `Texture`
and preserve `writeBufferOrCopyBuffer` for `Buffer`.

Teach `GPUFramePreflighter` to recognize an image texture upload by that
handle-free discriminant plus its typed destination texture plan. Emit an
`Upload` scope whose exact scope keys are:

```text
immutable upload-data key for the logical staging ref
destination GPUPreparedNativeTextureOperand key
```

Do not encode it as buffer-to-buffer. Task 5 supplies a matching `TextureUpload` operand and the backend encoder. The pure preflighter must still accept/reject without creating handles.

- [ ] **Step 4: Run GREEN and ownership regressions**

Rerun Step 2, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.images.ImageSamplerBoundaryGateTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.images.ImageSamplerMaterializationPreimageTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlanTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/RecordingContracts.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlanner.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUConcreteResourceProvider.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUTextureSamplerMaterializationProviderTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighter.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUFramePreflighterTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/PreparedGPUFrame.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageUploadScopeTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): plan prepared image resources"
```

Expected: exact upload/binding authority exists; no production native allocation yet.

---

### Task 5: Add parser-reflected WGSL and an operand-only image run materializer

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFrameEncodingBackend.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShaderTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFrameEncodingBackendOwnershipTest.kt`

**Interfaces:**

```kotlin
internal const val PREPARED_IMAGE_BINDING_LAYOUT_HASH =
    "prepared-image.group0.dynamic-uniform-texture-sampler.v1"

internal data class GPUPreparedImageShaderContract(
    val sourceHash: String,
    val bindingLayoutHash: String,
    val reflectedBindingsHash: String,
)

internal data class GPUPreparedImageUniformInput(
    val positions: List<Pair<Float, Float>>,
    val uvs: List<Pair<Float, Float>>,
    val tintPremultipliedRgba: List<Float>,
    val atlasColorPremultipliedRgba: List<Float>?,
    val alphaOnly: Boolean,
    val atlasSourceBlend: GPUPreparedAtlasSourceBlend?,
)

internal object GPUPreparedImageUniformAbi {
    const val BYTE_SIZE: Int
    fun pack(input: GPUPreparedImageUniformInput): ByteArray
}

internal data class GPUPreparedImageRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packets: List<GPUDrawSemanticPayload.SampledImage>,
    val resources: List<GPUImageFrameResourcePlan>,
    val uniformAllocations: List<GPUPreparedImageUniformAllocation>,
)

internal sealed interface GPUPreparedRenderRunMaterialization {
    data class Ready(
        val scopeOperands: List<GPUPreparedNativeScopeOperand>,
        val ownedResources: List<AutoCloseable>,
    ) : GPUPreparedRenderRunMaterialization
    data class Refused(val code: String, val message: String) : GPUPreparedRenderRunMaterialization
}

internal class GPUWgpu4kPreparedImageRenderRunMaterializer(
    private val sessionCache: GPUWgpu4kPreparedImageSessionCache,
    private val handleFactory: GPUPreparedImageNativeHandleFactory,
) {
    fun materializeAcceptedRun(
        plan: GPUPreparedImageRenderRunPlan,
    ): GPUPreparedRenderRunMaterialization
}
```

The run materializer never acquires a scene target, readback, late surface, or `GPUPreparedNativeFrameDraft`. It accepts only a full-preflight-approved run plan from Task 6.

- [ ] **Step 1: Write failing WGSL/ABI tests**

Parse with `KanvasWGSLValidator`, reflect with `KanvasWGSLReflectionProvider`, and assert exactly:

```text
@group(0) @binding(0) dynamic uniform buffer
@group(0) @binding(1) texture_2d<f32>
@group(0) @binding(2) filtering sampler
```

Assert the uniform packer byte-for-byte, four independent positions/UVs, A8 coverage colorization, paint alpha once, and closed atlas blend formulas for `Src`, `Dst`, `SrcOver`, `Plus`, and `Modulate`. Add CPU oracle vectors for all five modes. Any other `BlendMode` must have no mapping and later refuse.

- [ ] **Step 2: Write failing run/upload tests**

With fake handles, assert:

- one explicit `TextureUpload` scope precedes the first render consuming its artifact;
- the upload operand's keys exactly match `GPUFramePreflighter`;
- `GPUQueue.writeTexture` receives normalized/padded layout without treating padding as pixels;
- same artifact/sampler with two geometries creates two dynamic offsets and renders distinct uniforms;
- nearest and linear produce different native samplers but may share one pipeline;
- run materialization returns operands/resources only and cannot construct a draft;
- refusal/exception closes pre-registration handles exactly once.

- [ ] **Step 3: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageShaderTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFrameEncodingBackendOwnershipTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: shader/cache/run/texture-upload types are unresolved.

- [ ] **Step 4: Implement shader, session policy, and exact upload operand**

Extend `GPUPreparedNativeScopeOperand` with `exactOperandKeys`, defaulted from
its native `operands`. Add immutable upload data that is not a native handle and:

```kotlin
class TextureUpload(
    override val sourceStepIndex: Int,
    val data: GPUPreparedNativeUploadData,
    val destination: GPUPreparedNativeTextureOperand,
    val destinationKey: GPUPreparedNativeOperandKey,
    val layout: GPUPreparedImageUploadLayout,
) : GPUPreparedNativeScopeOperand {
    override val operationKind = GPUEncoderOperationKind.Upload
    override val operands = listOf(destination)
    override val exactOperandKeys = listOf(data.key, destinationKey)
}
```

`GPUPreparedNativeUploadData` defensively owns copied bytes plus the logical
staging key and has no `AutoCloseable`/native-handle identity. The
`exactOperandKeys` sequence must equal the preflight scope.
`GPUWgpu4kFrameEncodingBackend` handles `TextureUpload` with
`GPUQueue.writeTexture`; existing buffer upload semantics remain unchanged.

The session cache owns only shader module, reflected layout, pipeline layout, and render pipelines keyed by `GPUPreparedImagePipelineKey`. It owns no texture/view/sampler. It records device generation and closes all handles once on close, stale generation, or device-loss invalidation.

- [ ] **Step 5: Run GREEN**

Rerun Step 3; expected `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFrameEncodingBackend.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShaderTest.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFrameEncodingBackendOwnershipTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): materialize prepared image runs"
```

Expected: approved image runs can produce typed native operands; no mixed frame draft or product route yet.

---

### Task 5.1: Canonicalize ABI112 and binding reuse

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShaderTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt`

**Interfaces:**

```kotlin
internal data class GPUPreparedImageBindingLayoutContract(
    val identity: String,
    val reflectedBindingsHash: String,
    val uniformMinBindingSize: Long,
    val group: Int,
    val uniformBinding: Int,
    val textureBinding: Int,
    val samplerBinding: Int,
)

internal fun preparedImageBindingLayoutContract(): GPUPreparedImageBindingLayoutContract
```

`preparedImageBindingLayoutContract()` is the only authority for the image
binding layout. It is derived from parser-backed reflection and must report
group `0`, bindings `0/1/2`, and uniform minimum binding size `112`.

- [ ] **Step 1: Write failing identity tests**

In `GPUPreparedImageShaderTest`, assert:

```kotlin
val contract = preparedImageBindingLayoutContract()
assertEquals(0, contract.group)
assertEquals(0, contract.uniformBinding)
assertEquals(1, contract.textureBinding)
assertEquals(2, contract.samplerBinding)
assertEquals(112L, contract.uniformMinBindingSize)
assertEquals(contract.identity, preparedImageShaderContract().bindingLayoutHash)
```

Build a task list with a different layout identity and assert pure refusal
`unsupported.image.native_binding`. Assert the fake handle factory count stays
zero. Delete every test that expects the materializer to repair a mismatched
key.

- [ ] **Step 2: Write failing bind-group reuse tests**

Create three packets:

- packets 1 and 2 use the same artifact, sampler and binding key but different
  uniform offsets;
- packet 3 uses the same artifact with `Linear` instead of `Nearest`.

Assert one texture/view, two samplers, two bind groups and three dynamic
uniform offsets. Assert two byte-identical binding keys produce the same
`GPUBindGroup` object, while different sampler keys do not.

- [ ] **Step 3: Run RED**

```bash
./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageShaderTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: the two current layout strings disagree and Task 5 creates one bind
group per request.

- [ ] **Step 4: Implement one reflected layout authority**

Replace both `PREPARED_IMAGE_BINDING_LAYOUT_HASH` constants with
`preparedImageBindingLayoutContract().identity`. The task-list builder
serializes that exact identity. The run materializer compares the incoming key
to it and refuses mismatch; it must not call `packet.pipelineKey.copy(...)` to
substitute the value.

Move native texture/view/sampler/bind-group acquisition behind the Task-4
`GPUPreparedImageBindingKey` authority. Keep one uniform buffer slab per
resource plan and bind it once; dynamic offsets select per-draw uniform data.

- [ ] **Step 5: Run GREEN and exact regressions**

Rerun Step 3, then:

```bash
./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUFramePreflighterTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPURuntimeResourceAdapterTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 6: Commit**

```bash
git add -- \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShaderTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt
git -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com \
  commit -m "fix(gpu): canonicalize prepared image bindings"
```

Expected: ABI112 and binding reuse have one tested authority; product gate
unchanged.

---

### Task 5.2: Preserve command-exact image sources and canonical refusals

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt`

**Interfaces:**

```kotlin
internal data class GPUPreparedImageCommandSource(
    val commandId: Int,
    val operationIndex: Int,
    val operation: DisplayOp.DrawImage,
)

object GPUPreparedImageRefusalCodes {
    const val PIXELS_MISSING = "unsupported.image.pixels_missing"
    const val PIXEL_FORMAT = "unsupported.image.pixel.format"
    const val ALPHA_INTERPRETATION = "unsupported.image.alpha_interpretation"
    const val NATIVE_BINDING = "unsupported.image.native_binding"
}
```

The complete object contains every row of the stable FP-04 refusal table.
Surface diagnostics retain the exact code and add `boundary=surface` in
`facts`.

- [ ] **Step 1: Write failing repeated-source tests**

Record two `DrawImage` operations using the same `Image` object and `sourceId`
but different destination rectangles. Assert:

```kotlin
assertIs<GPUPreparedSurfaceFrameBuildResult.Prepared>(result)
assertEquals(2, result.framePlan.drawPackets.size)
assertEquals(1, result.preparedResources.imageArtifacts.size)
assertNotEquals(
    result.framePlan.drawPackets[0].canonicalHash,
    result.framePlan.drawPackets[1].canonicalHash,
)
```

Also create two distinct images with an equal `sourceId` and different bytes;
assert two artifact keys and exact per-command association. No test may expect
`invalid.surface.prepared.image-source-bijection`.

- [ ] **Step 2: Write failing refusal propagation tests**

For missing pixels, unsupported format, unpremultiplied alpha, invalid stride,
stale generation and missing binding, assert the exact stable code at artifact,
Surface, recording and preflight boundaries. Assert no value starts with
`unsupported.surface.prepared.image-source.`.

- [ ] **Step 3: Run RED**

```bash
./gradlew :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageNativeResourcesTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: repeated `sourceId` refuses and codes differ by boundary.

- [ ] **Step 4: Implement command-exact association**

Build `GPUPreparedImageCommandSource` values while walking operations in paint
order. Associate them with normalized commands by `commandId` and
`operationIndex`; do not call `groupBy(sourceId)`. Prepare one artifact per
exact source operation, then deduplicate physical resources only by
`GPUImageUploadArtifactKey`.

Replace boundary-specific strings with `GPUPreparedImageRefusalCodes`.
Propagate `code` unchanged and merge context into `facts`.

- [ ] **Step 5: Run GREEN**

Rerun Step 3 and the focused 73 Kanvas contract tests listed in the Task-5
audit.

- [ ] **Step 6: Commit**

```bash
git add -- \
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilder.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUPreparedSurfaceFrameTaskListBuilder.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/FirstRoutePlannerTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt
git -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com \
  commit -m "fix(surface): preserve exact prepared image sources"
```

Expected: repeated images are valid, physical resources share by immutable
key, and refusal values are stable end-to-end.

---

### Task 5.3: Seal cache generation and minimize pipeline specialization

**Files:**

- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageSessionCacheTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayloadTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImagePipelineSpecializationTest.kt`

**Interfaces:**

```kotlin
data class GPUPreparedImagePipelineKey(
    val destinationBlendState: String,
    val targetFormat: String,
    val bindingLayoutHash: String,
)

internal sealed interface GPUPreparedImageCacheAcquire {
    data class Ready(val pipeline: GPUPreparedImageCachedPipeline) :
        GPUPreparedImageCacheAcquire
    data class Refused(val code: String, val message: String) :
        GPUPreparedImageCacheAcquire
}
```

Geometry class, `alphaOnly`, atlas color/blend and scissor class remain
uniform/dynamic state. The current WGSL uses the same four-vertex/six-index
entry points for Rect and Quad, so geometry class is not a pipeline axis.

- [ ] **Step 1: Write failing generation tests**

Create a cache for generation 7 and acquire a pipeline successfully. Request
generation 8 and assert `unsupported.image.native_generation`, zero new
handles, unchanged `cache.deviceGeneration == 7`, and no mutation of the
generation-7 handle set. Close once and assert all owned handles close once.

Then close generation 7 and construct a separate cache with the replacement
device and generation 8. Runtime wiring of this close→construct transition is
an explicit Task-6 acceptance test.

- [ ] **Step 2: Write failing specialization tests**

Generate otherwise-identical payloads that vary only:

- `alphaOnly`;
- atlas color presence and source blend;
- full-target versus dynamic scissor.

Assert equal `GPUPreparedImagePipelineKey` values and different canonical
semantic hashes. Assert Rect and Quad also share the key because they use the
same reflected shader, vertex inputs and primitive state. Vary destination
blend, target format and binding layout and assert different keys.

- [ ] **Step 3: Add the measurement evidence**

Run a deterministic 100-draw fixture with alternating alpha/atlas/clip facts.
Record:

```text
draws=100
shaderModules=<value>
pipelines=<value>
pipelineCreatesAfterWarmup=<value>
cacheHits=<value>
cacheMisses=<value>
uniformUploadBytes=<value>
```

The test requires `pipelineCreatesAfterWarmup=0` and fewer pipelines than the
pre-change key. Store the exact report under
`reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-pipeline-key.txt`.

- [ ] **Step 4: Run RED**

```bash
./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSessionCacheTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest" \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImagePipelineSpecializationTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 5: Implement immutable generation and minimal key**

Make `deviceGeneration` a constructor `val`; remove any reassignment. Cache
acquisition compares the request generation before lookup or creation. The
cache exposes close-only invalidation; Task 6 makes
`GPUBackendRuntimeNative` reconstruct it after the old cache has closed.

Remove every uniform-only field from `GPUPreparedImagePipelineKey`; keep those
values in `GPUPreparedImageUniformInput`, canonical semantic hashing and
dynamic scissor state.

- [ ] **Step 6: Run GREEN and commit**

Rerun Step 4, then:

```bash
git add -- \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageSessionCacheTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayloadTest.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImagePipelineSpecializationTest.kt \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-pipeline-key.txt
git -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com \
  commit -m "fix(gpu): seal prepared image pipeline cache"
```

Expected: device generation is immutable and uniform-only facts no longer
fragment pipeline caching.

---

### Task 5.4: Prove and correct the native sRGB source/store contract

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMappingTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageSrgbNativeProbeTest.kt`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-srgb-store.md`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-task-5-review.md`

**Interfaces:**

```kotlin
internal data class GPUPreparedSdrColorContract(
    val colorSourceTextureFormat: GPUTextureFormat,
    val coverageSourceTextureFormat: GPUTextureFormat,
    val colorUploadEncoding: GPUPreparedColorUploadEncoding,
    val targetTextureFormat: GPUTextureFormat,
    val shaderInterpretation: GPUColorInterpretation,
    val readbackInterpretation: GPUColorInterpretation,
)

internal enum class GPUPreparedColorUploadEncoding {
    StraightEncodedSrgb,
}

internal fun preparedSdrColorContract(): GPUPreparedSdrColorContract
```

The expected accepted contract uses an sRGB RGBA/BGRA source view,
`RGBA8Unorm` for A8 coverage, linear-premultiplied shader values and
`GPUTextureFormat.RGBA8UnormSrgb` target store. The native probe, not the
expectation, is the acceptance authority.
Add `GPUColorFormat.RGBA8UnormSrgb =
GPUColorFormat("rgba8unorm-srgb")` and
`GPUColorInterpretation.LinearPremul =
GPUColorInterpretation("linear-premul")`; use these exact values in the
accepted contract and dumps.
Add `GPUColorInterpretation.StraightEncodedSrgb =
GPUColorInterpretation("straight-encoded-srgb")` for color upload artifacts.

- [ ] **Step 1: Record the bounded Graphite+Dawn reference**

Against Skia checkout
`/Users/chaos/workspace/kanvas-forge/skia-main@defc3a5a92966c32cb2a6a901e2fa3036a13bb8a`,
record these exact reference points in `fp-04-srgb-store.md`:

- `src/core/SkColorSpaceXformSteps.cpp:137-140,202-260` for ordered
  unpremul→transfer→premul color conversion;
- `src/gpu/graphite/KeyHelpers.cpp:1015-1051` for Graphite's encoded
  premul/unpremul shader flags;
- `src/gpu/graphite/TextureFormat.h:90,96` for linear versus sRGB RGBA8;
- `src/gpu/graphite/dawn/DawnGraphiteUtils.cpp:327-350` for Dawn format
  mapping;
- `src/gpu/graphite/dawn/DawnGraphicsPipeline.cpp:428` for render-target
  format selection.

Extract invariants only. Do not port Graphite classes, key systems or shader
generation.

- [ ] **Step 2: Write the independent oracle**

Use exact IEC 61966-2-1 piecewise transfer functions in test code. Use
premultiplied source RGBA `[25, 75, 132, 160]`, whose bounded straight recovery
is `[40, 120, 210, 160]`, paint alpha `0.75`, transparent background and one
opaque fixture. Compute expected straight sRGB decode, linear premultiplication
and sRGB store. Add A8 coverage `128` and prove it remains approximately
`0.50196` before tint rather than being sRGB-decoded. Do not call production
color helpers.

- [ ] **Step 3: Write the failing native probe**

Render the fixtures through:

1. current `RGBA8Unorm + EncodedPremulSrgb`;
2. direct premultiplied bytes sampled from `RGBA8UnormSrgb`;
3. straight encoded bytes sampled from `RGBA8UnormSrgb`, followed by
   `vec4(sampled.rgb * sampled.a, sampled.a)` before tint/blend;
4. the legacy reference route, only as comparative evidence.

Read back once after GPU completion. Assert the selected candidate matches the
independent oracle exactly or within one declared LSB per channel. Assert the
current translucent mismatch is reproduced before changing production
mapping.

- [ ] **Step 4: Run RED**

```bash
./gradlew :gpu-renderer:test \
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageSrgbNativeProbeTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
./gradlew :kanvas:test \
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceColorMappingTest" \
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: the prepared translucent candidate does not match the oracle.

- [ ] **Step 5: Implement the proven contract**

Make `preparedSdrColorContract()` the source of the color upload encoding,
color upload-view format, coverage upload-view format, target format, shader
interpretation, readback interpretation and pipeline target key. For each
RGBA/BGRA pixel with alpha byte `a`, produce straight encoded channels
`a == 0 ? 0 : round(channel * 255 / a).coerceIn(0, 255)` and retain `a`.
Include `StraightEncodedSrgb` and the converted-byte hash in
`GPUImageUploadArtifactKey`. The WGSL re-premultiplies sampled color before
tint/blend. `alphaOnly` artifacts keep the existing replicated bytes and use
the coverage source format. Use hardware sRGB decode/store rather than CPU
destination conversion. Refuse unsupported format/view capability with
`unsupported.color.image_profile_conversion`.

If wgpu4k cannot create the required sRGB view/target while WebGPU exposes it,
stop this step, minimize the failing call in the probe, and record the exact
wgpu4k issue URL in `fp-04-srgb-store.md`. Do not add an alternate hidden path.

- [ ] **Step 6: Run GREEN and record evidence**

Rerun Step 4. Write formats, input bytes, oracle bytes, native bytes,
per-channel deltas, adapter/backend, route marker and verdict to
`fp-04-srgb-store.md`. Assert no destination CPU snapshot/upload operand exists
in the accepted image route.

- [ ] **Step 7: Run the Task-5 acceptance set**

Run the 289 focused tests from the 2026-07-26 audit plus the new Task
5.1-5.4 tests. Record exact counts and commands in
`reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-task-5-review.md`.

- [ ] **Step 8: Commit and independent review**

```bash
git add -- \
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMapping.kt \
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceColorMappingTest.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContracts.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/images/PreparedImageContractsTest.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/payloads/GPUPreparedImagePayload.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUPreparedImageFrameResourcePlan.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt \
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageShader.kt \
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageSrgbNativeProbeTest.kt \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-srgb-store.md \
  reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-task-5-review.md
git -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com \
  commit -m "fix(gpu): prove prepared sRGB image storage"
```

Request an independent review of Tasks 5.1-5.4. Resolve every legitimate
blocking or important finding and rerun the affected tests before Task 6.

Expected: Task 5 is formally accepted; color and ownership foundations are
ready for the mixed-frame materializer.

---

### Task 6: Build one globally preflighted mixed-frame materializer and one draft

**Files:**

- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflightTest.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageNativeHandleFactory.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageNativeHandleFactoryTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest.kt`
- Create: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimePreparedImageCacheLifecycleTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Modify: `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcherTest.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt`

**Interfaces:**

```kotlin
internal sealed interface GPUPreparedSurfaceNativeRunPlan {
    data class Core(val plan: GPUCorePrimitiveRenderRunPlan) : GPUPreparedSurfaceNativeRunPlan
    data class Image(val plan: GPUPreparedImageRenderRunPlan) : GPUPreparedSurfaceNativeRunPlan
}

internal data class GPUCorePrimitiveRenderRunPlan(
    val sourceScopeIndices: List<Int>,
    val packetIds: List<GPUDrawPacketID>,
    val routeSeal: GPUCorePrimitiveNativeScopeRouteSeal,
)

internal data class GPUPreparedSurfaceNativePreflightPlan(
    val orderedRuns: List<GPUPreparedSurfaceNativeRunPlan>,
    val exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    val generationSeal: GPUPreparedGenerationSeal,
)

internal sealed interface GPUPreparedSurfaceNativePreflightResult {
    data class Accepted(val plan: GPUPreparedSurfaceNativePreflightPlan) :
        GPUPreparedSurfaceNativePreflightResult
    data class Refused(val code: String, val message: String) :
        GPUPreparedSurfaceNativePreflightResult
}

internal class GPUPreparedSurfaceNativePreflight {
    fun validate(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        shaderContract: GPUPreparedImageShaderContract,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult
}
```

- [x] **Step 1: Write failing global-preflight tests**

Consume the exact handle-free inputs already supplied to
`GPUPreparedNativeFramePayloadMaterializer.materializeReusable`: frame plan,
encoder plan, prepared resource set, and generation seal. Do not construct a
second `PreparedGPUFrame`, completion ticket, rollback journal, dependency
evidence, or host action. Validate all scopes, semantic hashes, resource plans,
upload layouts, WGSL reflection/binding layout, blend/clip facts, generation,
target, readback, and surface decoration. Most importantly:

```kotlin
assertIs<Refused>(materialize(coreThenInvalidImage()))
assertEquals(0, fakeHandleFactory.createCount)
assertIs<Refused>(materialize(imageThenInvalidCore()))
assertEquals(0, fakeHandleFactory.createCount)
```

Unknown semantic mixes, destination-copy splits, broken clip/coverage chains, stale generation, missing binding, or invalid upload refuse with zero handles and zero drafts.
Also assert the specialized preflight returns only run/scope seals, retains
the exact input resource identities, and creates no prepared frame, ticket, or
rollback owner.
Assert the accepted image route contains no destination CPU snapshot,
CPU-raster continuation, or compatibility texture upload.

- [x] **Step 2: Write failing single-owner materialization tests**

Cover `core -> upload -> image -> core`, two image runs, and lattice-shaped `image -> core -> image`. Assert the composed operands match the full encoder plan rather than a hardcoded render-only list:

```kotlin
assertEquals(
    fullEncoderPlan.scopes.map { it.sourceStepIndex },
    draft.payload.scopeOperands.map { it.sourceStepIndex },
)
assertEquals(
    fullEncoderPlan.scopes.map { it.operationKind },
    draft.payload.scopeOperands.map { it.operationKind },
)
```

Assert upload precedes every consumer, target/readback/surface are created once, late binding occurs once, and completion closes the single ownership ledger once.
Simulate a runtime device-generation transition and assert the generation-7
image cache closes before a generation-8 cache is constructed with the
replacement `GPUDevice`; no handle or pipeline from generation 7 is returned
after the transition.

- [x] **Step 3: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kFramePayloadMaterializerDispatcherTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: global preflight and single-owner materializer are unresolved.

- [x] **Step 4: Extract operand-only core runs**

Refactor the current core materializer so its reusable internal
`GPUWgpu4kCorePrimitiveRenderRunMaterializer` materializes all accepted Core
runs as one frame-global lot and returns only run operands/resources. It owns
one shared V/I/U allocation and at most one shared D24S8 allocation; it must not
allocate one lease per run. Keep the existing pure-core public materializer as
a wrapper that performs full preflight and builds one draft. No behavior or
pure-core scope seal may regress.

- [x] **Step 5: Implement global preflight then one draft**

The mixed materializer executes in this order:

```text
pure full-frame preflight
-> if refused: return with zero native factory calls
-> borrow the target once
-> materialize one frame-global Core lot and one frame-global Image lot
-> create each image artifact resource once even across separated image runs
-> convert target-free PreparedImageRenderRun values into encodable Render operands
-> reorder all operands by the exact complete encoder plan
-> verify operand keys equal the complete encoder plan
-> append readback/output/surface operands once
-> construct one GPUPreparedNativeFramePayload
-> construct one GPUPreparedNativeFrameDraft
```

Any post-preflight allocation failure rolls back the one owner ledger. Replace `mixed-semantic-shape` only for the exact closed `{CorePrimitive, SampledImage}` route. Keep pure routes unchanged.

The mixed dispatcher must pass the complete frame and encoder plans directly to
the composite. It must not use the pure-route surface split/decorate helper,
because that helper creates a child draft. `PreparedImageRenderRun` remains an
operand-only intermediate: production payloads contain only the final
target-bound `Render` operands accepted by the encoding backend.

Wire one `GPUWgpu4kPreparedImageSessionCache` into `GPUBackendRuntimeNative`; close it on runtime close/device-generation invalidation. Telemetry distinguishes pipeline cache from frame-owned texture/sampler counts and makes no inter-frame image-cache claim.

- [x] **Step 6: Run GREEN and pure-route regressions**

Rerun Step 3, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedNativeFramePayloadTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeNativeWgslValidationTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [x] **Step 7: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  docs/superpowers/plans/2026-07-25-prepared-surface-image-route.md `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflight.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedSurfaceNativePreflightTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResourcesTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedNativeFramePayload.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveRenderRunMaterializer.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageNativeHandleFactory.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageNativeHandleFactoryTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializer.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializer.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kCorePrimitiveFramePayloadMaterializerTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializer.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageRenderRunMaterializerTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageSessionCacheTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcherTest.kt `
  gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimeNative.kt `
  gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUBackendRuntimePreparedImageCacheLifecycleTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(gpu): materialize mixed prepared frames"
```

Expected: synthetic mixed frames create exactly one preflighted native draft; gate unchanged.

---

### Task 7: Complete `DrawImage` through the direct prepared route

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowererTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`

**Interfaces:**

```kotlin
data class GPUPreparedImageDrawFacts(
    val artifact: GPUPreparedImageUploadArtifact,
    val sampling: GPUPreparedImageSampling,
    val geometry: GPUPreparedImageGeometry,
    val tintPremultipliedRgba: List<Float>,
)

sealed interface GPUPreparedDrawImageLowering {
    data class Ready(val command: GPUFramePathVisualCommand) : GPUPreparedDrawImageLowering
    data class Refused(val code: String, val facts: Map<String, String>) :
        GPUPreparedDrawImageLowering
}

internal object GPUPreparedDrawImageLowerer {
    fun lower(
        operation: DisplayOp.DrawImage,
        commandId: GPUDrawCommandID,
        paintOrder: Int,
        provenance: GPUFrameProvenance,
        target: GPUTargetFacts,
        config: RenderConfig,
        capabilities: GPUCapabilities,
    ): GPUPreparedDrawImageLowering
}
```

Add `preparedImage: GPUPreparedImageDrawFacts?` to `GPUFramePathVisualCommand`. Do not add image handling to the product gate in this task; direct builder/inventory test seams call the lowerer.

- [ ] **Step 1: Write failing lowering tests**

Assert:

- source pixels are snapshotted before caller mutation;
- two draws of the same `Image` share one artifact/upload while retaining
  distinct geometry, uniform bytes, scissor and dynamic offsets;
- two distinct images with equal `sourceId` retain distinct command-exact
  source associations;
- source rect/UV clamp is exact;
- identity/translation/scale become exact rect or quad geometry;
- rotation/reflection/skew retain four transformed corners;
- perspective/singular transforms refuse;
- `NEAREST` and `LINEAR` survive unchanged;
- cubic refuses and never becomes linear;
- alpha-only tint and RGBA paint alpha are premultiplied/applied once;
- clip/blend/provenance/order facts are preserved;
- a lowering refusal yields no visual command.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedDrawImageLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: direct DrawImage lowerer is unresolved.

- [ ] **Step 3: Implement DrawImage lowering and direct execution**

Map `SamplingOptions` exactly. Build positions from `operation.transform` and `dst` corners; compute normalized UVs from `src` and artifact dimensions. Use the existing canonical clip/blend authorities. The semantic builder must reject any mismatch between normalized `DrawImageRect` facts and `preparedImage`.

Extend the direct prepared inventory/builder/native smoke seam to execute a frame containing:

```text
solid -> DrawImage -> solid
```

The gate and legacy adapter remain unchanged.

- [ ] **Step 4: Run GREEN and native DrawImage evidence**

Rerun Step 2, then run the native class alone:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Assert prepared route markers, upload-before-sample, one target/readback owner, and exact RGBA/BGRA/A8 pixels for nearest plus one-LSB-bounded linear output.

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowererTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(surface): prepare draw image operations"
```

Expected: `DrawImage` is complete through a direct prepared seam; product gate still classifies image family as legacy.

---

### Task 8: Add transactional image-nine and lattice expansion

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowerer.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowererTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/LatticeDecompositionTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`

**Interfaces:**

```kotlin
sealed interface GPUPreparedImageGridLowering {
    data class Ready(val commands: List<GPUFramePathVisualCommand>) :
        GPUPreparedImageGridLowering
    data class Refused(
        val code: String,
        val operationIndex: Int,
        val facts: Map<String, String>,
    ) : GPUPreparedImageGridLowering
}

internal object GPUPreparedImageGridLowerer {
    fun lowerNine(
        operation: DisplayOp.DrawImageNine,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedImageGridLowering

    fun lowerLattice(
        operation: DisplayOp.DrawImageLattice,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedImageGridLowering
}
```

- [ ] **Step 1: Write failing nine/lattice tests**

Assert:

- nine uses the documented default `LINEAR`, shares one artifact/sampler, emits nonempty cells in row order, and preserves affine corners;
- lattice honors its explicit sampling override;
- sampled, `FIXED_COLOR`, and transparent cells become image, core solid, and no command respectively;
- fixed-color multiplies caller alpha once, preserves caller destination blend, and creates no upload/binding;
- command IDs and paint order are contiguous across emitted cells;
- invalid geometry, image, sampling, or any cell refuses the entire logical operation before returning a command;
- a later invalid cell cannot leave a valid prefix.

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageGridLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.LatticeDecompositionTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: grid lowerer is unresolved.

- [ ] **Step 3: Implement fully transactional expansion**

Validate the artifact, dimensions, split arrays/cell metadata, sampling, transforms, budget, and every expanded cell into temporary immutable facts. Only after all validation succeeds assign final command IDs/orders and return commands. Reuse Task 7 image lowering for sampled cells and canonical core semantic building for fixed-color cells.

- [ ] **Step 4: Run GREEN and isolated native evidence**

Rerun Step 2, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameBuilderTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Prove nine 3x3 and mixed lattice pixels, affine transforms, exact route order, one upload per artifact, and no upload for fixed-color-only cells.

- [ ] **Step 5: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowerer.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowererTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/LatticeDecompositionTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(surface): prepare image grid operations"
```

Expected: nine/lattice are complete through direct prepared tests; gate unchanged.

---

### Task 9: Add affine atlas and complete FP-04 pixel/refusal evidence

**Files:**

- Create: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedAtlasLowerer.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedAtlasLowererTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceImagePixelTest.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalMatrixTest.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt`

**Interfaces:**

```kotlin
sealed interface GPUPreparedAtlasLowering {
    data class Ready(val commands: List<GPUFramePathVisualCommand>) :
        GPUPreparedAtlasLowering
    data class Refused(
        val code: String,
        val spriteIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedAtlasLowering
}

internal object GPUPreparedAtlasLowerer {
    fun lower(
        operation: DisplayOp.DrawAtlas,
        firstCommandId: Int,
        firstPaintOrder: Int,
        context: GPUPreparedImageLoweringContext,
    ): GPUPreparedAtlasLowering
}
```

- [ ] **Step 1: Write failing affine/transactional atlas tests**

Assert:

- `transforms.size == texRects.size` and optional `colors.size` equality are checked before the first sprite;
- every source rect is finite, nonempty, and inside the artifact;
- final transform is exactly `operation.transform * transforms[index]`;
- identity, translation, scale, rotation, reflection, and skew preserve all four transformed positions and UVs;
- no bounding-box substitution occurs;
- perspective/singular transforms and any later invalid sprite refuse the full atlas;
- sprites share artifact/sampler but retain distinct dynamic uniforms;
- `Src`, `Dst`, `SrcOver`, `Plus`, and `Modulate` map to the closed enum/formula;
- all other `BlendMode` values refuse `unsupported.image.atlas.source_blend`;
- sprite color, paint alpha, clip, destination blend, and order are applied exactly once.

- [ ] **Step 2: Write failing full pixel and refusal-matrix tests**

Use deterministic fixtures:

```text
2x2 premul RGBA: red, green, blue, half-alpha premul white
2x2 opaque BGRA: same logical colors
3x1 A8: 0, 128, 255
6x6 nine/lattice source with distinct borders and center
4x4 atlas with one unique premul color per quadrant
```

Every native pixel case must assert a prepared route marker and reject the legacy marker. Nearest comparisons are exact; linear UNORM comparisons use `maxChannelDelta <= 1`.

Parameterize the complete stable refusal table. For source classes not constructible through `Image`, test `GPUPreparedImageSourceInput` directly and prove classification before allocation. For each refusal, assert zero fake-native handles and no route fallback. Assert each code is identical at source, Surface, recording and preflight boundaries; boundary context belongs in diagnostic facts, not in a renamed code.

Also assert/dump:

```text
source.color=RGBA8UnormSrgb
source.coverage=RGBA8Unorm
source.colorUploadEncoding=StraightEncodedSrgb
target=RGBA8UnormSrgb
shaderInterpretation=linear-premul
attachmentSrgbConversion=true
oracleMaxChannelDelta<=1
```

- [ ] **Step 3: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedAtlasLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceImagePixelTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageRefusalMatrixTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: atlas lowerer and complete evidence are unresolved/failing.

- [ ] **Step 4: Implement atlas without broadening scope**

Validate the entire atlas into temporary immutable sprite facts, map the closed source blend, then assign command IDs and return quads. Reuse the same artifact/resource plan and dynamic uniform slab. Do not batch if batching changes order or resource ownership; FP-04 does not require atlas instancing.

Fix only defects exposed by route-tagged evidence. Do not weaken pixels, raise tolerance beyond one LSB, invoke the immediate renderer, or accept an unsupported refusal row.

- [ ] **Step 5: Run GREEN in isolated native JVM**

Rerun Step 3, then:

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: all four families and mixed frames have native route/pixel evidence while product gate remains closed.

- [ ] **Step 6: Run non-native image regressions**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUImagePixelsTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUAlphaImageMaterialTest" `
  --tests "org.graphiks.kanvas.surface.gpu.LatticeDecompositionTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUAllApiBlendSurfaceTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUClipCoverageSurfaceTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

- [ ] **Step 7: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedAtlasLowerer.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedAtlasLowererTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceImagePixelTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageRefusalMatrixTest.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameBuilderTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductNativeSmokeTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUAlphaImageMaterialTest.kt
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(surface): prepare affine atlas and image evidence"
```

Expected: complete direct prepared evidence exists; product gate and legacy allowlist remain unchanged.

---

### Task 10: Atomically admit all image operations and close FP-04

**Files:**

- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntryTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt`
- Modify: `reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md`
- Create: `reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-prepared-image-route.md`

- [ ] **Step 1: Invert product admission tests before production code**

Expect every valid image variant to be a prepared candidate and every invalid variant to terminate with its prepared refusal. Assert none can produce `legacy.surface.prepared.family.images`.

```kotlin
assertEquals(
    setOf(
        LegacyDisplayOpFamily.Text,
        LegacyDisplayOpFamily.Vertices,
        LegacyDisplayOpFamily.Composites,
    ),
    GPULegacyImmediatePathAdapter.allowedFamilies,
)
assertFalse(GPULegacyImmediatePathAdapter().accepts(validImageOperation))
```

- [ ] **Step 2: Run RED**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameGateTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductRouterTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductEntryTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: image operations are still legacy and `Images` is still allowed.

- [ ] **Step 3: Perform the one atomic cutover**

In one change:

1. classify `DrawImage`, `DrawImageNine`, `DrawImageLattice`, and `DrawAtlas` as visual candidates;
2. route mapper/builder refusals through the terminal prepared result;
3. remove `LegacyDisplayOpFamily.Images` from enum/allowlist/classification;
4. delete `legacy.surface.prepared.family.images`;
5. preserve Text, Vertices, and Composites exactly;
6. prove the router never invokes legacy after an image candidate is admitted.

- [ ] **Step 4: Run GREEN**

Rerun Step 2; expected `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run targeted FP-04 validation serially**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --tests "org.graphiks.kanvas.gpu.renderer.images.PreparedImageContractsTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImagePayloadTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameTaskListBuilderTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageFrameResourcePlanTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedSurfaceNativePreflightTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUPreparedImageShaderTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedImageRenderRunMaterializerTest" `
  --tests "org.graphiks.kanvas.gpu.renderer.execution.GPUWgpu4kPreparedSurfaceFramePayloadMaterializerTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageSourceTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedDrawImageLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageGridLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedAtlasLowererTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceImagePixelTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedImageRefusalMatrixTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceFrameGateTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductRouterTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductEntryTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest" `
  --tests "org.graphiks.kanvas.surface.gpu.GPUPreparedSurfaceProductNativeSmokeTest" `
  --dependency-verification=off --no-daemon --console=plain --rerun-tasks --max-workers=1
```

Expected: both commands succeed. If the known device/session recreation crash occurs, preserve the crash artifact, rerun only the native class in a fresh process, and record the aggregate as not executed/failed with separate FP-09 attribution. Never report an unexecuted aggregate as green.

- [ ] **Step 6: Run module aggregates**

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :gpu-renderer:test `
  --dependency-verification=off --no-daemon --console=plain --max-workers=1
```

```powershell
$env:JAVA_HOME='C:\Users\Shadow\.jdks\temurin-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :kanvas:test `
  --dependency-verification=off --no-daemon --console=plain --max-workers=1
```

No image route, pixel, alpha, gate, router, entry, ownership, or allowlist failure may be deferred.

- [ ] **Step 7: Write evidence and close only FP-04**

Write `fp-04-prepared-image-route.md` with task commit hashes, exact commands/outcomes, prepared route diagnostics, zero-allocation refusal evidence, upload/texture/view/sampler/binding/uniform counts, physical format/color facts, RGBA/BGRA/A8 statistics, nine/lattice/atlas affine diffs, accepted/refused atlas blends, complete refusal codes, and explicit nonclaims for codecs/animation, HDR/YUV, imports, mips, cubic/aniso, repeat/mirror/decal, perspective, and inter-frame cache.

Change only FP-04 from `in_progress` to `completed`. Do not edit FP-05 acceptance/status.

- [ ] **Step 8: Run final repository checks**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas diff --check
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas status --short
rg -n "legacy\.surface\.prepared\.family\.images|LegacyDisplayOpFamily\.Images" `
  kanvas/src/main kanvas/src/test
```

Expected: `diff --check` exits zero; `rg` finds no production/test reference; status includes only intended FP-04 files plus the three protected local items.

- [ ] **Step 9: Commit**

```powershell
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas add -- `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGate.kt `
  kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPULegacyImmediatePathAdapter.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceFrameGateTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductRouterTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedSurfaceProductEntryTest.kt `
  kanvas/src/test/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventoryTest.kt `
  reports/upstream-rebaseline/graphite-dawn-frame-plan/active-todo.md `
  reports/upstream-rebaseline/graphite-dawn-frame-plan/fp-04-prepared-image-route.md
git -c safe.directory=C:/Users/Shadow/IdeaProjects/kanvas -c user.name=ygdrasil-io -c user.email=alexandre.mommers@gmail.com commit -m "feat(surface): activate prepared image routing"
```

Expected: FP-04 completed; FP-05 remains the next pending item.

---

## Plan Self-Review Checklist

- [ ] The vertical order matches the approved design: semantic -> heterogeneous frame -> resource/preflight -> native run -> Task-5 consolidation -> sRGB proof -> mixed-frame materializer -> DrawImage -> nine/lattice -> affine atlas -> atomic cutover.
- [ ] `Image.alphaType` is authoritative; no code derives premultiplication from `ColorType`.
- [ ] Source stride, tight normalized stride, and native upload stride remain distinct and width-3 A8/BGRA tests cover padding.
- [ ] Builder, preflight, shader, cache and materializer use one reflected group-0 ABI112 identity.
- [ ] Repeated draws of one image are command-exact, share artifact resources, and retain distinct uniform allocations.
- [ ] Texture/view, sampler and bind group reuse are keyed by their distinct Task-4 identities.
- [ ] A prepared-image cache never changes generation while retaining the same device.
- [ ] Every refusal retains the exact stable FP-04 code through all layers.
- [ ] Every pipeline-key axis has a specialization class and no unmeasured uniform-only axis remains.
- [ ] The sRGB source/sample/store contract matches an independent translucent oracle within the declared one-LSB policy.
- [ ] The accepted image route contains no destination CPU snapshot or compatibility reupload.
- [ ] The complete mixed frame passes pure preflight before the first native factory call.
- [ ] Exactly one target/readback/surface owner and one `GPUPreparedNativeFrameDraft` exist per mixed frame.
- [ ] Upload scope keys and `TextureUpload` operand keys match the full encoder plan.
- [ ] Dynamic uniform offsets prevent same-image/sampler draws with different geometry/tint from aliasing.
- [ ] All canonical hashes serialize explicit fields and never use `toString()`.
- [ ] The proven source/target formats and `linear-premul` shader interpretation are asserted in dumps and native evidence.
- [ ] All stable refusal rows are tested with zero allocation/fallback.
- [ ] Product admission stays closed through Task 9 and changes once in Task 10.
- [ ] Every Gradle block runs with the JDK 25 toolchain, one worker, no daemon, and dependency verification disabled.
- [ ] Every task contains RED, implementation, GREEN, exact staging, and commit steps.
- [ ] Protected Gradle files and the native crash log remain unstaged.
- [ ] No unspecified implementation work remains.

## Execution Handoff

Execute this plan with `superpowers:subagent-driven-development`. Assign one implementation task at a time to a fresh subagent, then perform controller-side spec review, code-quality review, serial validation, exact-file staging, and commit before advancing. Do not parallelize Gradle/native runs or tasks that edit the same route.
