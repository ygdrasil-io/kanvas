# W5e Decoded Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote already-decoded images into the sealed plan-first material path for the four agreed pixel formats, all three sampling families, all four tile modes, direct image families, and image shaders on Rect and Path fill, without retaining a second semantic authority or a fallback after admission.

**Architecture:** `:kanvas` captures immutable public image facts and explicit sampling into Scene IR v2. `:gpu-plan` is the sole authority for image admission, coordinates, color/alpha interpretation, sampling, tile modes, family decomposition, budgets and typed refusals; it emits an immutable, handle-free `ImageSampleExecutionPlanV1`. `:gpu-renderer` materializes the sealed upload and bindings, then executes one shared manual WGSL texel evaluator from both direct-image and W5 material lanes. Existing prepared-image code may be refactored for physical upload/cache/ownership only and must not reclassify semantics.

**Tech Stack:** Kotlin/JVM and Kotlin/JS, `:math:geometry`, `:math:matrix`, `:color-management`, `:render-ir`, `:gpu-plan`, `:gpu-renderer`, `:kanvas`, WebGPU/WGSL, JUnit 5.

**Spec:** `refactor/specs/2026-09-12-w5e-decoded-images-design.md`, governed by `refactor/specs/2026-09-09-w5-material-graph-design.md` and the sealed W5a–W5d contracts.

## Global Constraints

- Branch `codex/w5e-decoded-images` is stacked on `codex/w5d-gradient-addressing`; its PR targets `codex/w5d-gradient-addressing`.
- Only already-decoded `ImageResourceSnapshot.Pixels` values in `RGBA_8888`, `BGRA_8888`, `SRGBA_8888` and `ALPHA_8` are promoted. External image codecs, `ExternalImageReference`, fonts, mipmaps and anisotropic filtering remain excluded.
- W5e covers `DrawImage`, `ImageNine`, `ImageLattice`, `Atlas`, and image shaders on Rect and Path fill. Image shaders on RRect, Path stroke/hairline, Point(s), Text and Vertices/Mesh remain W5h and must stay pre-admission.
- Geometry values are reused from or added to `:math:geometry`; no geometry data class is created in `:kanvas`, `:render-ir`, `:gpu-plan` or `:gpu-renderer`. New public numeric names use explicit I32/I64/U32/F32/F64 suffixes. Fixed public APIs and WGSL ABI names are not renamed opportunistically.
- `DisplayOp.DrawImage.sampling` is explicit and defaults to `SamplingOptions.NEAREST`. No code may transport direct-image sampling by manufacturing `Paint.shader`.
- `Picture`, `SceneArchiveCodec`, both Scene/DisplayOp adapters, snapshot copying and their witnesses advance atomically to Picture v10 / archive schema v4. v8/v9 remain readable; missing sampling normalizes to Nearest; Cubic B/C bits survive round-trip.
- `ImageSampleExecutionPlanV1` is immutable, handle-free and complete before native allocation. It contains copied image facts, checked layout, canonical upload identity, coordinate plan, sampling/tile graph, alpha/color program, decomposed cells and budget facts.
- Logical size, stride and total-byte arithmetic uses checked I64 operations. Source row padding is neither uploaded nor hashed. WebGPU row padding is allocated independently, initialized to zero and never sampled.
- Physical storage is `RGBA8_UNORM` for color and `R8_UNORM` for A8. No automatic sRGB texture is used. Swizzle, transfer, gamut conversion, alpha interpretation and premultiplication occur exactly once in the shared evaluator.
- The sampled color domain is linear premultiplied RGBA. OPAQUE forces alpha to one; UNPREMUL converts straight RGB then premultiplies; PREMUL safely unpremultiplies in source space, converts, then premultiplies; alpha zero produces zero RGB. A8 is a scalar mask and never undergoes color-space conversion.
- Direct RGBA applies only paint alpha before the established effect/blend/coverage chain. Direct A8 applies its mask to paint color or paint shader, including paint alpha. Paint RGB never tints a direct RGBA image.
- Coordinates use pixel centers. Nearest selects `floor(s)`, Linear uses four taps around `s - 0.5`, Cubic uses sixteen Mitchell–Netravali taps. Every tap is tiled before fetch. DECAL contributes transparent taps without weight renormalization.
- `ImageNumericAuthorityV1` authenticates the coordinate, sampling, tile and color/alpha operation graph against `WgslFloatEnvelopeV1`. Projective validity and every F32→I32 index conversion are bounded before admission; an unbounded envelope refuses instead of relying on backend conversion behavior.
- Direct image operations force CLAMP/CLAMP. Image shaders preserve independent X/Y CLAMP, REPEAT, MIRROR or DECAL. Sampling and tile modes never participate in the upload key.
- Once the W5e gate claims an operation, planner, budget, allocation, shader compilation, submission or readback failure is terminal. There is no prepared-image or legacy-material fallback after ownership.
- Public behavior tests use `Surface`, `Canvas`, `Picture`, `render()`, public pixels, public statistics and public diagnostics only. They do not inspect cache entries, artifacts, lowerers, plan internals, resource providers, handles, bind groups, uniform bytes or call counters. No new infrastructure test is introduced.
- The independent CPU oracle duplicates the published equations; it does not call production sampling, tiling, conversion, decomposition or hashing helpers. Exact fixtures close to one or two adjacent RGBA8 codes through the existing float envelope; similarity thresholds are forbidden.
- Targeted Skia validation is limited to decoded-image GMs that do not require excluded codecs or `jpg-color-cube`. It complements, but does not replace, public Kanvas gates.
- Implementers are selected for the task and are not Sol. Task-level and final code reviews use Sol only. This written plan receives the explicitly requested independent Astra review before Task 1.
- Each task follows RED → implementation → focused GREEN → preceding-wave regression → spec review → quality review → commit. Durable state lives only in this plan, `refactor/waves/W05-material-graph/status.md` and `refactor/README.md`; transient SDD packets live under the ignored `.superpowers/sdd/` tree.

---

### Task 1: Version the public capture and Scene IR contract

**Files:**
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOp.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayOpSnapshot.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/DisplayListBuffer.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/canvas/Canvas.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/DisplayOpSceneAdapter.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneDisplayOpAdapter.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/GeometryNode.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/EffectNode.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/ResourceSnapshot.kt`
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/SceneArchiveCodec.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W5ePictureImageSamplingTest.kt`

**Interfaces:**
- Consumes: public `Image`, `SamplingOptions`, image draw APIs and immutable math geometry.
- Produces: stride-preserving `Image`/resource conversion, explicit direct sampling, `ImagePatch` v2, distinct `ImageNine`, Picture v10/schema v4, and backward-compatible public Picture payload replay.

- [ ] **Step 1: Write public RED tests for capture and compatibility.** Add methods `drawImageSamplingPayloadRoundTripIsStable`, `cubicBitsProduceDistinctStablePicturePayloads`, `legacyImagePayloadDefaultsToNearest`, `imageNinePayloadRemainsDistinctFromImagePatch`, `pixelRowBytesPayloadRoundTripIsStable`, and `recordingBudgetCountsPaddingAndPayloadTailBeforeSnapshotCopy`. Exercise only public Picture recording/serialize/deserialize/serialize APIs and `Surface`/`SceneCaptureLimits`; use a checked-in minimal v9 byte fixture generated from the existing format before changing the writer. The budget fixture sets a small limit, then uses a legal logical row plus padding and trailing bytes whose full `pixels.size` exceeds it; recording must refuse before defensive `copyOf`. These tests prove payload conservation without requiring a backend that Tasks 2/5/6 have not delivered. The corresponding pixel/replay semantics are explicit gates in those later tasks. Do not call `SceneArchiveCodec` directly from the tests.

- [ ] **Step 2: Run the focused methods and verify RED.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5ePictureImageSamplingTest*' --no-parallel --rerun-tasks
```

Expected: explicit direct sampling is currently encoded in a fabricated shader, Picture replay loses source/sampling, `ImageNine` aliases `ImagePatch`, and public row stride is unavailable.

- [ ] **Step 3: Make direct sampling and row stride explicit without breaking source calls.** Add trailing defaults:

```kotlin
public data class Image(
    public val width: Int,
    public val height: Int,
    public val colorType: ColorType = ColorType.RGBA_8888,
    public val sourceId: String,
    public val pixels: ByteArray? = null,
    public val colorSpace: ColorSpace = ColorSpace.SRGB,
    public val alphaType: AlphaType = colorType.defaultAlphaType(),
    public val rowBytesI32: Int = logicalRowBytesI32(width, colorType),
)

public data class DrawImage(
    public val image: Image,
    public val src: RectF32,
    public val dst: RectF32,
    public val paint: Paint?,
    public val transform: Matrix3x3F32,
    public val clip: ClipStack,
    public val sampling: SamplingOptions = SamplingOptions.NEAREST,
) : DisplayOp
```

`logicalRowBytesI32` performs checked I64 multiplication, requires I32 representability and throws `IllegalArgumentException("image.row-bytes-overflow")` at the public construction boundary. Add trailing `colorSpace` and `rowBytesI32` parameters to `Image.fromPixels`, plus a `drawImageRect(image, src, dst, sampling, paint)` overload, and have every no-sampling overload record Nearest. Remove the `paint.copy(shader = image.makeShader(...))` bridge. Preserve `rowBytesI32` in `reinterpretColorSpace`, surface capture and every snapshot copy. Validate only representation-level invariants in public constructors; planner-level budget/device limits remain in `:gpu-plan`.

In `ResourceSceneAdapter`, pass `image.rowBytesI32` into `ImageResourceSnapshot.fromPixels` and restore it in `toImage`; never recompute a tight stride. Add `SceneCaptureLimits.maxImageBytesI64` as a retained-capture-memory limit distinct from the later logical upload identity. `RecordingImageByteBudget` is injected into `GeometrySnapshotContext`: before `Image.pixels.copyOf()`, it reserves the full `pixels.size` with checked I64 arithmetic, including producer row padding and any trailing payload bytes, deduplicates aliases within/across accepted operations, and commits or rolls back with the append transaction. `SnapshotDisplayListBuffer.ops()` preflights the complete already-budgeted retained byte total before making its second public defensive copy. `DisplayOpSceneAdapter` repeats the retained-byte preflight for operations originating in a custom buffer before `ResourceSceneAdapter` constructs `ImmutableBytes`. Only after these capture copies are bounded does `ImageUploadPlanV1` derive and hash row-by-row logical bytes while ignoring padding and trailing bytes. All limit failures use a stable public recording/capture diagnostic.

- [ ] **Step 4: Version the IR geometry without creating geometry outside math.** Change `GeometryNode.ImagePatch` to canonical ID `geometry-image-patch-v2` and add `sampling: ImageSampling`. Add `GeometryNode.ImageNine(image, center: RectF32, destination: RectF32, sampling = Nearest)` with its own canonical tag. Validate `DrawOrigin.IMAGE` ↔ `ImagePatch` and `DrawOrigin.IMAGE_NINE` ↔ `ImageNine` in `EffectNode`; retain `RectF32` from `:math:geometry`.

- [ ] **Step 5: Advance internal codecs atomically.** Write Picture v10/archive schema v4, accept Picture v8/v9/v10, and enforce schema maxima 2/3/4 respectively. Schema v4 writes ImagePatch sampling and a new ImageNine tag. Schema ≤3 reads ImagePatch without sampling as Nearest and normalizes an `IMAGE_NINE` origin plus historical ImagePatch into current ImageNine. Preserve Cubic `B` and `C` with raw F32 bits and include sampling in canonical identities. Update both adapters and Picture replay to use source rect plus explicit sampling.

- [ ] **Step 6: Verify public payload GREEN, compilation and backward decode.**

```bash
rtk ./gradlew :render-ir:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5ePictureImageSamplingTest*' --tests '*ImageTest*' --no-parallel --rerun-tasks
rtk git diff --check
```

- [ ] **Step 7: Request spec and quality reviews, resolve findings, then commit.**

```bash
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/image/Image.kt kanvas/src/main/kotlin/org/graphiks/kanvas/canvas kanvas/src/main/kotlin/org/graphiks/kanvas/picture/Picture.kt kanvas/src/main/kotlin/org/graphiks/kanvas/render/ir render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir kanvas/src/test/kotlin/org/graphiks/kanvas/picture/W5ePictureImageSamplingTest.kt
rtk git commit -m "feat(image): version W5e image capture semantics"
```

### Task 2: Seal the first plan-first decoded-image vertical slice

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanDiagnostics.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageUploadPlanV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageNumericOperationGraphV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageNumericAuthorityV1.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanCacheResourceRequest.kt`
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanPasses.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/PlanResources.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/CapabilityCompilerChain.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/color/ColorContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUMaterialTextureFrameResourcePlan.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/recording/GPUFramePlan.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5eImagePlanLowerer.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5ePreparedFrameWitnessV1.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eDecodedImageSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuPlanTaskListLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/GpuRenderContext.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kFramePayloadMaterializerDispatcher.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageCpuOracle.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageSurfacePixelTest.kt`

**Interfaces:**
- Consumes: one RGBA_8888/PREMUL `ImagePatch`, Nearest, CLAMP/CLAMP, W5b final blend and W4 target/coverage contracts.
- Produces: the first terminal `DrawImage` path through a sealed image plan, content-keyed upload, texture/view binding and public exact pixels.

- [ ] **Step 1: Write public RED tests.** Add `rgbaPremulNearestDrawImageIsPlanOwned`, `sourceRectAndNegativeDestinationPreservePixelCenters`, `capturedPixelsIgnoreLaterSourceMutation`, `unsupportedExternalResourceStaysPreAdmission`, `unboundedProjectiveImageCoordinatesRefuseAndRecover`, and `failedPromotedDrawRecoversOnSameRuntime`. Choose a 3×2 asymmetric fixture so a legacy stretch, wrong origin, or shader bridge cannot match. Assert public pixels and public routed statistics only.

- [ ] **Step 2: Run the first vertical-slice methods and verify RED at the current gate.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest.rgbaPremulNearestDrawImageIsPlanOwned' --tests '*W5eDecodedImageSurfacePixelTest.sourceRectAndNegativeDestinationPreservePixelCenters' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Define one sealed semantic and numeric contract.** `ImageUploadPlanV1` stores copied width/height I32, logical row bytes I64, source row bytes I64, byte count I64, logical format, physical format and a content identity computed row-by-row over logical bytes. `ImageSampleExecutionPlanV1` stores the upload, `ImageCoordinatePlanV1`, `ImageSamplingPlanV1`, tile X/Y, `ImageColorAlphaPlanV1`, immutable cells, cache request and total pessimistic budget. It exposes copies, stable IDs and scalar facts only—never `Image`, `ByteBuffer`, native device, texture, view, sampler or bind group.

```kotlin
public enum class ImageChannelOrderV1 { RGBA, BGRA, ALPHA }
public enum class ImageTransferPlanV1 { SRGB, LINEAR, NONE }
public enum class ImageGamutPlanV1 { SRGB, DISPLAY_P3, NONE }

public sealed interface ImageMaterialProgramV3 : MaterialProgramPlan {
    public data class ColorV3(
        public val channelOrder: ImageChannelOrderV1,
        public val alphaType: ImageAlphaType,
        public val transfer: ImageTransferPlanV1,
        public val gamut: ImageGamutPlanV1,
    ) : ImageMaterialProgramV3
    public class MaskV3(public val child: MaterialProgramPlan) : ImageMaterialProgramV3
}

public class ImageSampleV3 private constructor(
    public val execution: ImageSampleExecutionPlanV1,
) : MaterialBindingPlan {
    override val versionI32: Int = 3
    public companion object {
        public fun of(execution: ImageSampleExecutionPlanV1): ImageSampleV3
    }
}

public data class MaterialV3(
    public val ref: MaterialPlanRef,
    public val imageCoordinates: ImageCoordinatePlanV1,
) : PlanDrawMaterialAuthority
```

Extend `NumericOperationGraphV1` with explicit image-color and image-mask graph factories, then implement `copyNumericOperationGraphV1()` on both V3 programs. Extend `MaterialPlanTable` validation/interning explicitly for ColorV3, MaskV3 and their child adjacency. Do not reinterpret V1/V2 authorities. In this slice, only ColorV3 + RGBA_8888/PREMUL + Nearest is admitted.

`ImageNumericOperationGraphV1` describes inverse projection, homogeneous divide, source/destination mapping, pixel-center shift, `floor`, tap offsets, integer conversion, tile addressing, texel conversion and accumulation. `ImageNumericAuthorityV1.seal(...)` evaluates that graph over the transformed device bounds with `WgslFloatEnvelopeV1`; it refuses a projective denominator interval containing zero, non-finite/unbounded samples, Cubic offset overflow, or any floor result outside the WGSL I32 conversion domain. The authority authenticates program, binding, coordinates, sampling and tile topology. The WGSL consumer first applies the sealed projective-validity mask and never lets an invalid value reach `floor` or I32 conversion.

Add `PlanResourceLifetime.DeviceSessionCache` and a handle-free `PlanCacheResourceRequest` carrying canonical physical identity, kind, format, dimensions, usages, ABI version and byte size. Existing W4 lowerers remain exhaustive and reject that lifetime outside the W5e path. Frame budgeting always includes the full texture size even on a later cache hit.

- [ ] **Step 4: Compile a dedicated image graph without one-draw-per-cell leakage.** `W5eImagePlanCompiler` selects only direct ImagePatch scenes in this slice, validates all semantic facts before Ready, creates one logical `ImageDrawV1` per public command and keeps its cell list internal to that draw. Visual command counts, ordering and final blend remain per public operation. Register the compiler before legacy gaps in `GpuRenderContext.createProduction` and dispatch its capability in `GpuPlanTaskListLowerer`.

- [ ] **Step 5: Materialize only physical facts with session ownership.** Generalize the staging half of `GPUMaterialTextureFrameResourcePlan` to R8/RGBA8 with an explicit bytes-per-pixel I32 and add `GPUColorFormat.R8Unorm`; remove sampling from upload identity. `GPUW5eDecodedImageSessionCache` owns texture/view entries under a key prefixed by device generation, accepts only `PlanCacheResourceRequest`, and has injected `maxEntriesI32`/`maxBytesI64` limits. A miss evicts least-recently-used zero-lease entries until both limits fit, then uploads transactionally; a hit only increments its lease. A lease is held through GPU completion and released in success/failure cleanup; device loss closes the whole generation. Pipeline/layout caching remains separate. `GPUW5eImageNativeV1` validates generation and witness, acquires the cache lease and builds bindings; it cannot inspect `DisplayOp`, `Paint`, `Shader` or public `Image`.

- [ ] **Step 6: Close the router after admission.** Extend `GPUPlanSurfaceCandidateGate` only for the exact Task 2 slice. A typed pre-admission result remains eligible for existing behavior; Candidate/Ready becomes terminal. The router must not call `GPUPreparedDrawImageLowerer` after W5e ownership, including allocation or native failures.

- [ ] **Step 7: Verify GREEN, W5d continuity and commit.**

```bash
rtk ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest*' --tests '*W5dGradientAddressingSurfacePixelTest.linearClampWithAffineLocalMatrixIsPlanOwned' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageCpuOracle.kt kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageSurfacePixelTest.kt
rtk git commit -m "feat(gpu): deliver W5e nearest image slice"
```

### Task 3: Normalize pixel formats, color spaces and alpha exactly once

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageUploadPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Create: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W5eImageTexelEvaluatorV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/resources/GPUMaterialTextureFrameResourcePlan.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageSurfacePixelTest.kt`

**Interfaces:**
- Consumes: the four promoted formats, named sRGB/Display P3/Linear sRGB color facts, OPAQUE/PREMUL/UNPREMUL and arbitrary legal row stride.
- Produces: checked logical uploads and one shared WGSL texel conversion that yields linear premultiplied RGBA.

- [ ] **Step 1: Write public RED format/alpha/layout matrices.** Add parameterized coverage for RGBA, BGRA, SRGBA and A8; OPAQUE/PREMUL/UNPREMUL; zero alpha; Display P3 and Linear sRGB; padded source rows; two images with identical logical bytes but different padding; too-small stride; short payload; zero/overflow dimensions; contradictory SRGBA metadata; and recovery after each public typed refusal. Include `rgbaPaintRgbDoesNotTintButPaintAlphaApplies` and `a8UsesPaintColorAndAlpha`.

- [ ] **Step 2: Run the format matrix and verify RED.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest.formatsAlphaAndColorSpaceMatchOracle' --tests '*W5eDecodedImageSurfacePixelTest.rowPaddingIsIgnoredAndWebGpuPaddingDoesNotLeak' --tests '*W5eDecodedImageSurfacePixelTest.a8UsesPaintColorAndAlpha' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Seal checked layout and typed refusals before allocation.** Use `Math.multiplyExact`/`addExact` on I64 for `width * bytesPerPixel`, `rowBytes * height`, aligned row bytes and total upload bytes. Copy exactly logical row bytes into the content payload and hash. Publish stable codes rooted at `unsupported.material.image.*` for external resource, format, color-space/format mismatch, alpha, dimensions, stride, payload, overflow, texture limit, binding limit and frame budget.

- [ ] **Step 4: Implement one texel evaluator shared by both lanes.** `W5eImageTexelEvaluatorV1` emits WGSL for logical channel load, BGRA swizzle, explicit sRGB/linear transfer, gamut-to-linear-sRGB matrix, safe alpha handling and final premultiplication. It takes only sealed structural facts and binding indices. Direct rendering and material rendering compose this exact source; neither duplicates conversion. A8 returns a scalar mask. Unsupported transfer/gamut combinations refuse in the planner rather than approximating in the renderer.

- [ ] **Step 5: Encode the two material equations.** ColorV3 evaluates image color and wraps only paint opacity. MaskV3 references an adjacent child source: Solid for paint color or the admitted paint shader root, then multiplies all premultiplied channels by the A8 mask and applies paint alpha exactly once. Preserve the global color-filter/final-blend/coverage order outside these nodes.

- [ ] **Step 6: Verify GREEN, regress direct semantics and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest.linearRectUsesLocalCoordinatesAndMoreThanSixteenStops' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): normalize W5e image color and alpha"
```

### Task 4: Promote image shaders on Rect and Path fill

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/MaterialPlan.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/RawMaterialRequirementsV2.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4aAnalyticRectPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4cPathFillPlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aMaterialSourceStage.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/materials/W5aPacketMaterialSourceV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5aMaterialPlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/passes/W5aMaterialPlanAuthorityV2.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/W5aPreparedFrameMaterialRegistry.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageShaderSurfacePixelTest.kt`

**Interfaces:**
- Consumes: `MaterialNode.ImageSample`, Rect and Path fill W4 geometry, paint opacity, image local matrix and all pixel semantics from Task 3.
- Produces: MaterialV3-backed image shader evaluation in the existing W4 coverage lanes with no change to geometry/tessellation.

- [ ] **Step 1: Write public RED tests.** Add `rectImageShaderUsesIndependentLocalCoordinates`, `pathFillImageShaderUsesCoverageAndFinalBlend`, `a8ImageShaderMasksPaintColor`, `imageShaderPictureRoundTripPreservesLocalMatrix`, and one test per deferred lane proving RRect, Path stroke, Points and Vertices remain outside W5e admission with their existing public route/refusal.

- [ ] **Step 2: Run Rect/Path positives and deferred-lane negatives.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageShaderSurfacePixelTest*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Capture shader coordinates exactly once.** Compose inverse CTM and inverse image local matrix in F64 using `:math:matrix`, project finite coefficients to F32 and seal them in `ImageCoordinatePlanV1`. Preserve transform order and projective validity. Reject singular, non-finite or non-F32-representable inverses with image-specific public codes. Use `ImageNumericAuthorityV1` over the Rect/Path device bounds to reject a projective horizon or coordinate envelope whose floor/tap indices cannot be converted to I32; include a public `hugeFiniteImageShaderCoordinatesRefuseAndRecover` gate. The renderer consumes the sealed matrix/validity contract and never reconstructs API conventions.

- [ ] **Step 4: Extend the material path, not W4 geometry.** Teach `EffectiveMaterialPlanner` to build ColorV3 for a color image shader and MaskV3 over Solid for an A8 image shader. Extend W5 source packets, binding layout and native source stage with stable texture bindings after material uniform/gradient storage while preserving destination read at group 2. W4a Rect and W4c Path fill retain their existing draws, clips, coverage and witnesses.

- [ ] **Step 5: Hold the W5h boundary.** Gate image shaders only when origin/geometry is Rect or Path with FILL. The composite compiler recognizes the material as a normal Rect/Path lane. RRect, stroke/hairline, Points, Text and Vertices/Mesh cannot produce MaterialV3 in W5e.

- [ ] **Step 6: Verify GREEN, W4 coverage continuity and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageShaderSurfacePixelTest*' --tests '*GPUPlanSurfacePixelTest*' --tests '*W5dGradientAddressingSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageShaderSurfacePixelTest.kt
rtk git commit -m "feat(gpu): promote W5e rect and path image shaders"
```

### Task 5: Implement shared Linear sampling and all tile modes

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W5eImageTexelEvaluatorV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5aSourceStageNativeV2.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageShaderSurfacePixelTest.kt`

**Interfaces:**
- Consumes: sealed image coordinates, Nearest/Linear, independent X/Y tile modes and the shared texel conversion.
- Produces: deterministic manual one-/four-tap sampling for direct CLAMP and shader CLAMP/REPEAT/MIRROR/DECAL.

- [ ] **Step 1: Write public RED boundary matrices.** Test Nearest and Linear at exact centers, half-pixels and just outside all four edges. For image shaders cross all four X modes with all four Y modes using non-square 2×3 data; include negative coordinates, length-one axes, REPEAT floor-mod, MIRROR period `2n`, and DECAL partial filters. Assert DECAL is not renormalized.

- [ ] **Step 2: Run the boundary methods and verify RED for Linear/tile modes.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest.nearestAndLinearUsePixelCenters' --tests '*W5eImageShaderSurfacePixelTest.tileModesApplyPerTapOnBothAxes' --tests '*W5eImageShaderSurfacePixelTest.decalLinearDoesNotRenormalizeWeights' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Seal sampling/tile topology in the plan.** `ImageSamplingPlanV1.Nearest` and `.Linear` are structural variants. `ImageTileModePlanV1` records X/Y modes and a stable topology ID; direct compiler emits CLAMP/CLAMP regardless of paint. Validate positive image extents before any modulo operation. Reseal `ImageNumericAuthorityV1` for the selected tap halo and addressing graph; REPEAT/MIRROR are permitted only when every pre-reduction value and integer conversion has a finite proved envelope.

- [ ] **Step 4: Emit one shared manual evaluator.** Implement integer address functions and explicit `textureLoad`: Nearest uses `floor(s)`; Linear uses `u=s-0.5`, base `floor(u)` and four bilinear weights. Address each tap before fetch, return transparent for DECAL and never renormalize. Apply channel/color/alpha conversion per tap before interpolation so filtering occurs in linear premultiplied space. Do not create or bind a hardware sampler.

- [ ] **Step 5: Verify GREEN across both direct and material paths, then commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest*' --tests '*W5eImageShaderSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): add W5e linear sampling and tile modes"
```

### Task 6: Add validated Mitchell–Netravali Cubic sampling

**Files:**
- Modify: `render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/GeometryNode.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanDiagnostics.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/EffectiveMaterialPlanner.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/wgsl/W5eImageTexelEvaluatorV1.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageCpuOracle.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eDecodedImageSurfacePixelTest.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageShaderSurfacePixelTest.kt`

**Interfaces:**
- Consumes: Cubic(B,C) raw F32 bits and the same per-tap tile/color evaluator.
- Produces: sixteen-tap Mitchell–Netravali sampling with finite `[0,1]` parameters and stable refusals.

- [ ] **Step 1: Write public RED cubic tests.** Cover Mitchell `(1/3,1/3)`, Catmull–Rom `(0,0.5)`, two non-default bit-distinct values, all tile modes at a boundary, direct draw, Rect shader and Path fill shader. Add non-finite and out-of-range B/C public refusal/recovery tests.

- [ ] **Step 2: Run the cubic matrix and verify RED.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest.cubicDrawImageMatchesMitchellNetravaliOracle' --tests '*W5eImageShaderSurfacePixelTest.cubicTileBoundariesMatchOracle' --tests '*W5eImageShaderSurfacePixelTest.invalidCubicParametersRefuseAndRecover' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Validate and seal Cubic.** Reject B or C unless finite and in `[0,1]`; store raw F32 bits in the binding/canonical ID and preserve them through Picture. Add `ImageSamplingPlanV1.Cubic(bBitsI32, cBitsI32)` with a structural sixteen-tap program identity while leaving numeric values outside the pipeline key.

- [ ] **Step 4: Implement the published kernel once.** Evaluate four separable Mitchell–Netravali weights per axis from `abs(distance)`, load sixteen independently tiled taps, sum in deterministic row-major order and do not clamp or renormalize intermediate weights. The established final target conversion handles representable output.

- [ ] **Step 5: Verify GREEN, replay preservation and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eDecodedImageSurfacePixelTest*' --tests '*W5eImageShaderSurfacePixelTest*' --tests '*W5ePictureImageSamplingTest.cubicBitsProduceDistinctStablePicturePayloads' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add render-ir/src/main/kotlin/org/graphiks/kanvas/render/ir/GeometryNode.kt gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface
rtk git commit -m "feat(gpu): add W5e cubic image sampling"
```

### Task 7: Decompose and render ImageNine as a distinct family

**Files:**
- Create: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageCellDecomposerV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5eImagePlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageFamiliesSurfacePixelTest.kt`

**Interfaces:**
- Consumes: `GeometryNode.ImageNine`, source image bounds, center `RectF32`, destination `RectF32`, Nearest and paint semantics.
- Produces: zero-to-nine immutable `ImageCellPlanV1` entries under one public draw command.

- [ ] **Step 1: Write public RED Nine tests.** Add `imageNinePreservesCornersAndStretchesEdgesAndCenter`, `imageNineHandlesSmallAndFlippedDestinations`, `imageNinePaintAlphaAndBlendApplyOnce`, `imageNineTransformAndClipMatchPublicSemantics`, and invalid/non-finite center diagnostics with recovery. Use a 3×3 uniquely colored source so treating the center as one stretched patch is visibly wrong.

- [ ] **Step 2: Run the Nine methods and verify RED at the W5e family gate.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageFamiliesSurfacePixelTest.imageNinePreservesCornersAndStretchesEdgesAndCenter' --tests '*W5eImageFamiliesSurfacePixelTest.imageNineHandlesSmallAndFlippedDestinations' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Decompose before sampling.** `ImageCellDecomposerV1.nine` validates finite center/destination, clamps the center to image bounds according to public semantics, partitions both axes, handles compressed destinations deterministically and returns copied source/destination RectF32 cells. Empty cells are omitted. It does not create a new rectangle type and does not attach GPU resources.

- [ ] **Step 4: Keep one operation authority.** `W5eImagePlanCompiler` stores all Nine cells in one ImageDrawV1, one material root, one final blend and one command index. The lowerer may issue multiple native quads inside the command but cannot increment public visual operation statistics per cell or reorder cells around another command.

- [ ] **Step 5: Verify GREEN, Picture distinction and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageFamiliesSurfacePixelTest*' --tests '*W5ePictureImageSamplingTest.imageNinePayloadRemainsDistinctFromImagePatch' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageFamiliesSurfacePixelTest.kt
rtk git commit -m "feat(gpu): promote W5e image nine"
```

### Task 8: Promote ImageLattice and Atlas without flattening operation semantics

**Files:**
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageCellDecomposerV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/ImageSampleExecutionPlanV1.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5eImagePlanCompiler.kt`
- Modify: `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W5aCompositePlanCompiler.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/planning/W5eImagePlanLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageFamiliesSurfacePixelTest.kt`

**Interfaces:**
- Consumes: IR ImageLattice cells/flags/colors/sampling and Atlas entries/transforms/source rects/colors/operation blend with Atlas fixed to Nearest.
- Produces: bounded immutable family plans with one command authority per public operation and original entry order.

- [ ] **Step 1: Write public RED family matrices.** For Lattice cover valid divs, stretch/fixed/transparent cells, per-cell colors, Linear/Cubic, clipped/flipped destination, paint alpha and invalid cardinality/order. For Atlas cover independent transforms, disjoint source rects, optional colors, operation blend, paint alpha, clipping and entry order. Add mixed DrawImage → Lattice → Atlas → Rect ordering on one frame.

- [ ] **Step 2: Run the family positives and typed refusals.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageFamiliesSurfacePixelTest.latticeCellsMatchPublicSemantics' --tests '*W5eImageFamiliesSurfacePixelTest.atlasEntriesKeepTransformsColorsAndBlend' --tests '*W5eImageFamiliesSurfacePixelTest.imageFamiliesKeepMixedCommandOrder' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Seal Lattice decomposition.** Validate division ordering/range, rectangle/flag/color cardinalities and checked cell count before allocation. The cell contract is exhaustive: `SampledV1(source: RectF32, destination: RectF32)`, `SolidV1(destination: RectF32, color: ColorARGB)` or `OmittedV1(destination: RectF32)`. A present `cellRects` table supplies the destination rectangle for the corresponding cell; otherwise destination bounds come from fixed/stretch band decomposition. `DEFAULT` emits SampledV1 with declared sampling, `TRANSPARENT` emits OmittedV1, and `FIXED_COLOR` emits SolidV1 without sampling the image. Apply paint/effects/final blend once to the selected cell source. Include a fixture with red FIXED_COLOR over blue source texels so modulation cannot pass. Refuse an unsupported lattice flag instead of approximating.

- [ ] **Step 4: Seal Atlas entries.** Preserve every entry transform and source rect; never merge to a bounding box. Atlas sampling is Nearest because the public operation exposes none. Apply optional entry color with the public Atlas blend, then paint alpha/effects and operation final blend once. Keep Atlas entry count and byte budgets checked in I64.

- [ ] **Step 5: Extend mixed-frame planning.** Add image as a distinct W5a composite lane kind. Intern MaterialV3 programs and content-identical uploads without merging draw authorities. Require strictly increasing original command indices across Rect/RRect/Path/Image lanes and retain a single target lifetime.

- [ ] **Step 6: Verify GREEN, mixed W5 regression and commit.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageFamiliesSurfacePixelTest*' --tests '*W5eDecodedImageSurfacePixelTest*' --tests '*W5eImageShaderSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --no-parallel --rerun-tasks
rtk git diff --check
rtk git add gpu-plan/src/main/kotlin gpu-renderer/src/main/kotlin kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageFamiliesSurfacePixelTest.kt
rtk git commit -m "feat(gpu): promote W5e lattice and atlas"
```

### Task 9: Remove duplicate semantic authority and close W5e

**Files:**
- Modify or delete semantic branches in: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageSource.kt`
- Modify or delete semantic branches in: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedDrawImageLowerer.kt`
- Modify or delete semantic branches in: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedImageGridLowerer.kt`
- Modify or delete semantic branches in: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPreparedAtlasLowerer.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/artifacts/PreparedImageArtifactContracts.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUPreparedImageNativeResources.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUWgpu4kPreparedImageSessionCache.kt`
- Modify: `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/execution/GPUW5eImageNativeV1.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceCandidateGate.kt`
- Modify: `kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUPlanSurfaceRouter.kt`
- Create: `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageConvergenceSurfaceTest.kt`
- Modify: `refactor/waves/W05-material-graph/status.md`
- Modify: `refactor/README.md`

**Interfaces:**
- Consumes: all completed W5e lanes and existing physical resource ownership.
- Produces: one semantic authority, bounded content/device caches, public failure/recovery evidence, targeted decoded-image GM evidence and durable W5e status.

- [ ] **Step 1: Write final public convergence tests.** Add `allPromotedImageLanesArePlanOwned`, `alignedOneToOneDrawsWithDifferentPaddingAndSamplingRenderIdentically`, `repeatedRenderAndSubsequentSurfaceRemainStable`, and separate capture/frame-budget recovery witnesses. Observe only public result/diagnostic/pixels/stats and the public native Render/Readback evidence; do not inject a fake device, read counters or assert cache internals. Surface has no public close API: repeated rendering and a subsequent Surface do not prove explicit close. Capture rollback must render on the same Surface; immutable frame-budget configuration permits only same-runtime recovery on a subsequent Surface, not target-level numeric rollback. Allocation ordering and post-allocation native failure terminality are verified by independent static review because fabricating such failures would violate the public-test boundary.

- [ ] **Step 2: Run convergence tests before cleanup and record which legacy branches still satisfy or shadow them.**

```bash
rtk ./gradlew :kanvas:test --tests '*W5eImageConvergenceSurfaceTest*' --no-parallel --rerun-tasks
```

- [ ] **Step 3: Delete or neutralize duplicate semantics after admission.** Promoted frames consume only the sealed W5e authority and cannot enter prepared-image sampling, format/color/alpha, tile/family classification or fallback. Execution ruling: the old lowerers still serve whole frames rejected before admission, including excluded formats/effects. Document them explicitly as legacy semantic compatibility, not misleadingly as physical-only; their inputs are not W5e plans. Retain that pre-admission compatibility rather than deleting excluded behavior. A typed attachment snapshot must refuse there with `unsupported.image.prepared.premultiplication`, never be reinterpreted as ordinary source-space PREMUL. Keep the single whole-frame candidate gate and no second W5e transport or predicate.

**Snapshot completion ruling.** Preserve readback bytes and carry typed source-space versus transfer-encoded linear-premultiplied representation through Image, capture, IR, copy/reinterpret, Picture and material/numeric authorities. Complete Picture10/schema4 with a new attachment image tag; ordinary tag1, external tag2 and old ordinary canonical IDs remain unchanged. Older8/9 archives read as before; older intermediate10 readers reject the new tag. Do not claim skippable forward compatibility or repair old snapshots whose bytes were already mislabelled.

- [ ] **Step 4: Audit budgets, identities and lifetimes.** Prove by code path that upload keys ignore object identity, row padding, semantic premultiplication, sampling and tile modes but include logical format/dimensions/content. Texture/view keys begin with device generation. Pipeline keys include structural program facts, not pixel contents or numeric Cubic values. Verify `DeviceSessionCache` requests, pessimistic hit budgeting, entry/byte limits, zero-lease LRU eviction, atomic publication of a miss, completion-held leases and generation invalidation. An unsuccessful miss need not restore previously evicted native victims: do not claim whole-cache rollback. Close per-attempt leases via the preflight/completion journals; only cache-owned resources survive successful completion, uncertain cleanup remains quarantined. Check each owning budget before its governed handle creation.

- [ ] **Step 5: Run the full public W5e and preceding-wave regression.**

```bash
rtk ./gradlew :render-ir:compileKotlin :gpu-plan:compileKotlin :gpu-renderer:compileKotlin :kanvas:compileKotlin --no-parallel
rtk ./gradlew :kanvas:test --tests '*W5e*' --tests '*W5dGradientAddressingSurfacePixelTest*' --tests '*W5cGradientSurfacePixelTest*' --tests '*W5bBlendSurfacePixelTest*' --tests '*W5aMaterialSurfacePixelTest*' --no-parallel --rerun-tasks
```

Capture the Gradle exit status and inspect only fresh XML produced by this run. If native teardown still exits 133 after assertions, report the run as Gradle failure with the precise XML pass/skip/fail counts; never relabel it as success.

- [ ] **Step 6: Run targeted decoded-image Skia GMs only.** Use the existing exact-name runner filter for `nearest_half_pixel_image`, `image-shader`, `localmatriximageshader`, `alpha_image`, and `draw_image_set`, after verifying that each selected fixture consumes in-memory decoded pixels. Exclude any GM requiring external codec evolution, fonts, anisotropic/mipmap behavior, other W5h lanes, or `jpg-color-cube`. Record `image-surface` explicitly as font-dependent and therefore outside this gate. Record unsupported cases as named W5h/integration gaps, not silent exclusions.

```bash
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRunner*' -Dkanvas.gm.name=nearest_half_pixel_image --no-parallel --rerun-tasks
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRunner*' -Dkanvas.gm.name=image-shader --no-parallel --rerun-tasks
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRunner*' -Dkanvas.gm.name=localmatriximageshader -Dkanvas.gm.includeBlocking=true --no-parallel --rerun-tasks
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRunner*' -Dkanvas.gm.name=alpha_image -Dkanvas.gm.includeBlocking=true --no-parallel --rerun-tasks
rtk ./gradlew :integration-tests:skia:test --tests '*SkiaGmRunner*' -Dkanvas.gm.name=draw_image_set --no-parallel --rerun-tasks
```

- [ ] **Step 7: Update durable documentation.** Mark only evidenced lanes complete in `status.md`; list remaining W5h and external-codec gaps with stable diagnostics. Link the design and implementation plan from `refactor/README.md`. Do not create an intermediate status markdown, review ledger or static report.

- [ ] **Step 8: Request independent Sol reviews and resolve every actionable finding.** Execution ruling: one task-scoped Sol reviewer checks both exact spec coverage/absence of promoted fallback or dual authority and code quality/ownership/overflow/public-test integrity. Then one whole-branch Sol reviewer compares W5e and global W5 requirements. Sol remains review-only. Re-run affected public gates after fixes; permit one fix wave and one scoped re-review for whole-branch findings.

- [ ] **Step 9: Verify clean diff and commit closure.**

```bash
rtk git diff --check
rtk git status --short
rtk git add kanvas/src/main/kotlin/org/graphiks/kanvas/surface/gpu gpu-renderer/src/main/kotlin kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W5eImageConvergenceSurfaceTest.kt refactor/waves/W05-material-graph/status.md refactor/README.md
rtk git commit -m "refactor(gpu): converge W5e decoded image authority"
```

## Completion Gate

W5e is complete only when all promoted lanes are plan-owned; the public capture/codecs preserve explicit sampling and stride; the four formats, alpha modes, color spaces, samplings and tile modes pass public pixel gates; Nine/Lattice/Atlas keep their operation semantics; Rect/Path-fill image shaders share the same evaluator; W5h and codec exclusions stay explicit; and no promoted failure can invoke legacy semantic code. A final branch review must compare the resulting diff with both W5e and global W5 specs before the stacked PR is created or updated.
