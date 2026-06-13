# GPU Renderer Architecture Kernel

Status: Draft
Date: 2026-06-13

## Purpose

Define the small architecture kernel that every future GPU renderer spec and
implementation ticket must preserve. This is a target contract, not an
implementation patch.

The kernel documents the full technical scope first. Implementation slices are
planned after the specs are coherent and must not narrow the architectural
contract to their initial feature subset.

The direction is Graphite-inspired but inline: Kanvas borrows Graphite's
separation of recording, task preparation, draw passes, render steps, material
keys, and pipeline keys. Kanvas does not port Graphite code, Ganesh code, SkSL
IR, SkSL compilation, or backend abstraction layers.

## Module Boundary

The new renderer lives in `:gpu-renderer`. The module owns the Kanvas GPU
renderer core and is built on the `GPU` facade used with `wgpu4k`.

Core contracts live in `:gpu-renderer`. Legacy adapters may call into those
contracts, but they must not define the core command, analysis, pass, layer,
resource, material, pipeline, or diagnostic shapes outside this module.

The module owns:

- `NormalizedDrawCommand` contracts and normalized draw intake;
- GPU recording and immutable recordings;
- explicit draw analysis;
- task lists and draw passes;
- draw-layer planning;
- draw invocation insertion and sort-window policy;
- occlusion tracking;
- render-step selection;
- sort-key generation;
- material and pipeline keys;
- material dictionary and WGSL snippet registry;
- payload gathering and payload slot assignment;
- WGSL module assembly requests;
- WGSL layout and binding ABI contracts;
- blend, color, and target-state planning;
- texture descriptors, image-source descriptors, texture ownership plans,
  sampled texture bindings, imported texture descriptors, and surface texture
  leases;
- image pipeline, encoded source, codec registry, decode, animation frame,
  color/orientation, upload artifact, cache, budget, and diagnostic contracts;
- path atlas, coverage atlas, atlas entry, atlas budget, atlas mutation, atlas
  upload, and atlas diagnostic contracts;
- captured clip descriptor, clip element, clip bounds, scissor, analytic clip,
  stencil clip, coverage-mask clip, clip shader, clip budget, clip ordering,
  and clip diagnostic contracts;
- destination-read requirement, strategy, bounds, target snapshot, binding,
  budget, token, and diagnostic contracts;
- filter/effect graph, node, bounds, crop, tile, sampling, intermediate,
  runtime-effect, cache, budget, ordering, and diagnostic contracts;
- text run, text subrun, glyph artifact route, text atlas, text upload, text
  instance, text binding, and text diagnostic contracts;
- resource-provider contracts;
- GPU execution context and submission contracts;
- telemetry, cache, and performance-gate contracts;
- route selection and diagnostics.

The module must not own:

- Skia-like public API compatibility;
- `SkCanvas` state stack interpretation;
- font shaping;
- arbitrary codec loading outside the accepted Kanvas codec registry;
- arbitrary SkSL parsing or compilation;
- native windowing and event loops;
- broad CPU raster rendering.

## Package Policy

Packages in `:gpu-renderer` use Kanvas responsibility names, not
Graphite-mirrored source names. All implementation packages must use
`org.graphiks.kanvas` as the base package. The expected renderer module root is
`org.graphiks.kanvas.gpu.renderer`, with subpackages named for Kanvas
responsibilities:

- `commands`
- `recording`
- `analysis`
- `passes`
- `layers`
- `clips`
- `filters`
- `images`
- `text`
- `materials`
- `pipelines`
- `resources`
- `payloads`
- `execution`
- `state`
- `routing`
- `diagnostics`
- `telemetry`
- `wgsl`

Specs should document Graphite equivalents in an equivalence table for
orientation, but implementation packages must not mirror `skgpu::graphite`,
Graphite file names, or Graphite class ownership as a package taxonomy.

Exact class placement can evolve with implementation evidence, but new package
roots outside `org.graphiks.kanvas.gpu.renderer` are not allowed for renderer
core contracts without an explicit spec change.

## Naming Policy

Public concept names in the new renderer use uppercase acronyms:

- `GPURecorder`
- `GPURecording`
- `GPUTaskList`
- `GPUDrawAnalysis`
- `GPUOcclusionTracker`
- `GPULayerPlan`
- `GPULayerExecutionPlan`
- `GPULayerScopeID`
- `GPULayerSaveRecord`
- `GPULayerRestorePlan`
- `GPULayerBoundsPlan`
- `GPULayerTargetPlan`
- `GPULayerInitializationPlan`
- `GPULayerBackdropPlan`
- `GPULayerSourcePlan`
- `GPULayerFilterChainPlan`
- `GPULayerCompositePlan`
- `GPULayerElisionPlan`
- `GPULayerTaskPlan`
- `GPULayerResourcePlan`
- `GPULayerOrderingToken`
- `GPULayerCachePlan`
- `GPULayerBudgetPolicy`
- `GPULayerDiagnostic`
- `GPUFilterPlan`
- `GPUFilterGraphDescriptor`
- `GPUFilterNodeID`
- `GPUFilterNodeDescriptor`
- `GPUFilterNodePlan`
- `GPUFilterNodeRoute`
- `GPUFilterInputPlan`
- `GPUFilterSourcePlan`
- `GPUFilterBackdropPlan`
- `GPUFilterBoundsPlan`
- `GPUFilterCropPlan`
- `GPUFilterTilePlan`
- `GPUFilterSamplingPlan`
- `GPUFilterIntermediatePlan`
- `GPUFilterRenderNodePlan`
- `GPUFilterComputeNodePlan`
- `GPUFilterKernelPlan`
- `GPUFilterRuntimeEffectPlan`
- `GPUFilterColorPlan`
- `GPUFilterOrderingToken`
- `GPUFilterCachePlan`
- `GPUFilterBudgetPolicy`
- `GPUFilterDiagnostic`
- `GPUDrawLayer`
- `GPUDrawLayerPlanner`
- `GPUDrawInvocation`
- `GPUDrawInsertion`
- `GPUDrawPass`
- `GPURenderStep`
- `GPUResourceProvider`
- `GPUCapabilities`
- `GPUExecutionContext`
- `GPUSharedScope`
- `GPURecorderScope`
- `GPUFrameScope`
- `GPUAtlasScope`
- `GPUCommandSubmission`
- `GPUSurfaceTarget`
- `GPUReadbackRequest`
- `WGSLBindingLayout`
- `WGSLUniformLayout`
- `WGSLPackingPlan`
- `GPUMaterialDictionary`
- `GPUMaterialProgramID`
- `WGSLSnippet`
- `WGSLSnippetID`
- `WGSLSnippetNode`
- `GPUMaterialAssemblyPlan`
- `GPUPayloadGatherer`
- `GPUPayloadGatherPlan`
- `GPUPayloadWritePlan`
- `GPUMaterialPayload`
- `GPUPayloadSlotID`
- `GPUUniformPayloadBlock`
- `GPUUniformPayloadSlot`
- `GPUResourceBindingBlock`
- `GPUResourceBindingSlot`
- `GPUPayloadBindingPlan`
- `GPUPayloadUploadPlan`
- `GPUPayloadFingerprint`
- `GPUGradientPayloadStore`
- `GPUDrawPayloadRef`
- `GPUImageSourceDescriptor`
- `GPUTextureDescriptor`
- `GPUTextureViewDescriptor`
- `GPUSamplerDescriptor`
- `GPUTextureOwnershipPlan`
- `GPUTextureAllocationPlan`
- `GPUTextureResourceRef`
- `GPUImportedTextureDescriptor`
- `GPUTargetTextureDescriptor`
- `GPUSurfaceTextureLease`
- `GPUSampledTextureBinding`
- `GPUTextureDiagnostic`
- `GPUImagePipelinePlan`
- `GPUEncodedImageSource`
- `GPUCPUImageSource`
- `GPUImageCodecRegistry`
- `KanvasImageCodec`
- `GPUImageCodecDescriptor`
- `GPUImageDecodeRequest`
- `GPUImageDecodePlan`
- `GPUImageDecodeResult`
- `GPUImageFrameInfo`
- `GPUAnimatedImagePlan`
- `GPUImageFrameSelection`
- `GPUImageColorDecodePlan`
- `GPUImageOrientationPlan`
- `GPUImagePixelPlan`
- `GPUImageMipmapPlan`
- `GPUImageUploadPlan`
- `GPUImageUploadArtifactKey`
- `GPUUploadedImageArtifactDescriptor`
- `GPUImageCachePlan`
- `GPUImageBudgetPolicy`
- `GPUImageDiagnostic`
- `GPUShapeDescriptor`
- `GPUPathDescriptor`
- `GPUPathVerbSlice`
- `GPUPathFillRule`
- `GPUStrokeDescriptor`
- `GPUStrokeStyle`
- `GPUPathEffectDescriptor`
- `GPUGeometryPlan`
- `GPUGeometryRoute`
- `GPUPathRoute`
- `GPUStrokeRoute`
- `GPUPathBoundsPlan`
- `GPUPathTransformPlan`
- `GPUPathTolerancePlan`
- `GPUPathFlatteningPlan`
- `GPUPathTessellationPlan`
- `GPUStrokeExpansionPlan`
- `GPUStencilCoverPlan`
- `GPUPreparedGeometryPlan`
- `GPUGeometryBufferPlan`
- `GPUGeometryRenderStepPlan`
- `GPUGeometryCachePlan`
- `GPUGeometryBudgetPolicy`
- `GPUGeometryDiagnostic`
- `GPUVerticesDescriptor`
- `GPUVertexMode`
- `GPUVertexAttributeDescriptor`
- `GPUVertexLayoutPlan`
- `GPUVertexPositionPlan`
- `GPUVertexColorPlan`
- `GPUVertexTexCoordPlan`
- `GPUPrimitiveColorPlan`
- `GPUPrimitiveBlendPlan`
- `GPUIndexBufferPlan`
- `GPUVertexBufferPlan`
- `GPUVerticesRoute`
- `GPUVerticesRenderStepPlan`
- `GPUVerticesBoundsPlan`
- `GPUMeshDescriptor`
- `GPUMeshAttributeDescriptor`
- `GPUMeshBufferPlan`
- `GPUMeshRoute`
- `GPUVerticesCachePlan`
- `GPUVerticesBudgetPolicy`
- `GPUVerticesDiagnostic`
- `GPURuntimeEffectRegistry`
- `GPURuntimeEffectRegistrySnapshot`
- `GPURuntimeEffectID`
- `GPURuntimeEffectKind`
- `GPURuntimeEffectDescriptor`
- `GPURuntimeEffectDescriptorVersion`
- `GPURuntimeEffectCompatibilityKey`
- `GPURuntimeEffectLookupPlan`
- `GPURuntimeEffectRegistrationPlan`
- `GPURuntimeEffectUniformSchema`
- `GPURuntimeEffectUniform`
- `GPURuntimeEffectUniformBlockPlan`
- `GPURuntimeEffectChildSlotPlan`
- `GPURuntimeEffectResourcePlan`
- `GPURuntimeEffectWGSLPlan`
- `GPURuntimeEffectCPUOracle`
- `GPURuntimeEffectParameterPlan`
- `GPURuntimeEffectLiveEditPlan`
- `GPURuntimeEffectRouteContract`
- `GPURuntimeEffectCachePlan`
- `GPURuntimeEffectBudgetPolicy`
- `GPURuntimeEffectDiagnostic`
- `GPUPathAtlasPlan`
- `GPUCoverageAtlasPlan`
- `GPUAtlasPolicy`
- `GPUAtlasBudgetPolicy`
- `GPUAtlasDescriptor`
- `GPUAtlasPageDescriptor`
- `GPUAtlasPlotDescriptor`
- `GPUAtlasEntryDescriptor`
- `GPUAtlasEntryRef`
- `GPUAtlasGeneration`
- `GPUAtlasUseToken`
- `GPUAtlasMutationPlan`
- `GPUAtlasUploadPlan`
- `GPUCoverageMaskDescriptor`
- `GPUCoverageAtlasBinding`
- `GPUComputeCoverageAtlasPlan`
- `GPUAtlasDiagnostic`
- `GPUPathAtlasKey`
- `GPUCoverageAtlasKey`
- `GPUClipStackDescriptor`
- `GPUClipSaveRecordDescriptor`
- `GPUClipElementID`
- `GPUClipElementDescriptor`
- `GPUClipState`
- `GPUClipOperation`
- `GPUClipPlan`
- `GPUClipElementPlan`
- `GPUClipBoundsPlan`
- `GPUClipScissorPlan`
- `GPUClipAnalyticPlan`
- `GPUClipStencilPlan`
- `GPUClipMaskPlan`
- `GPUClipShaderPlan`
- `GPUClipRoute`
- `GPUClipAtomicGroup`
- `GPUClipOrderingToken`
- `GPUClipCachePlan`
- `GPUClipBudgetPolicy`
- `GPUClipDiagnostic`
- `GPUDestinationReadPlan`
- `GPUDestinationReadRequirement`
- `GPUDestinationReadStrategy`
- `GPUDestinationReadClass`
- `GPUDestinationReadBounds`
- `GPUDestinationReadAction`
- `GPUDestinationReadBudgetPolicy`
- `GPUDestinationCopyPlan`
- `GPUDestinationCopyTextureDescriptor`
- `GPUDestinationReadBinding`
- `GPUDestinationReadToken`
- `GPUDestinationReadDiagnostic`
- `GPUTextRunPlan`
- `GPUTextSubRunPlan`
- `GPUTextRepresentation`
- `GPUTextRoute`
- `GPUTextRenderStep`
- `GPUTextAtlasPlan`
- `GPUTextAtlasDescriptor`
- `GPUTextAtlasPageDescriptor`
- `GPUTextAtlasEntryRef`
- `GPUTextUploadPlan`
- `GPUTextResourcePlan`
- `GPUTextInstanceLayout`
- `GPUTextInstanceBufferPlan`
- `GPUTextBinding`
- `GPUTextSDFParams`
- `GPUColorGlyphCompositePlan`
- `GPUTextBatchKey`
- `GPUTextOrderingToken`
- `GPUTextBudgetPolicy`
- `GPUTextDiagnostic`
- `GPUBlendPlan`
- `GPUColorPlan`
- `GPUTargetState`
- `GPUTelemetryLedger`
- `GPUPerformanceGate`
- `GPUNative`
- `CPUPreparedGPU`
- `CPUReferenceOnly`
- `WGSLFragment`
- `WGSLModule`

This intentionally matches the `GPU` vocabulary of the facade used with
`wgpu4k`. Kotlin import aliases are acceptable when names collide with lower
level facade types.

Concepts without an acronym keep standard PascalCase, including `SortKey`.

## Core Purity

The core renderer must not depend directly on Skia-like types such as
`SkPaint`, `SkShader`, `SkPath`, `SkCanvas`, or `SkRuntimeEffect`.

Compatibility adapters may depend on those types. They are responsible for
turning stateful legacy calls into normalized commands before calling the new
core.

The core may depend on backend-neutral Kanvas value types when those types are
stable and do not pull in Skia-like API ownership. If an existing type carries
legacy semantics that are too broad or ambiguous, the adapter must translate it
into a narrower GPU renderer value object.

## Graphite Equivalence Table

| Graphite idea | Kanvas target concept | Constraint |
|---|---|---|
| `Recorder` | `GPURecorder` | Records normalized commands, not `SkCanvas` operations. |
| `Recording` | `GPURecording` | Immutable product of recording; reusable only under explicit resource rules. |
| `TaskList` | `GPUTaskList` | Prepares resources and emits commands in dependency order. |
| Draw-list analysis and ordering | `GPUDrawAnalysis` | Explicit analysis product; not hidden in pass construction. |
| `SortKey` | `SortKey` | Deterministic Kanvas value for legal draw ordering; no Graphite bit-layout requirement. |
| Occlusion culling | `GPUOcclusionTracker` | Dedicated conservative culling capability; not an incidental pass-builder side effect. |
| SaveLayer and layer semantics | `GPULayerPlan` | Captured layer/saveLayer semantics, offscreen target needs, restore/composite behavior, and attached filters. |
| SaveLayer execution | `GPULayerExecutionPlan` / `GPULayerTaskPlan` | Executable layer lowering with bounds, offscreen targets, initialization/backdrop, source filters, restore composite, elision, resources, ordering, budgets, and diagnostics. |
| Skia device-backed layer surface | `GPULayerTargetPlan` / `GPUTargetTextureDescriptor` | Provider-owned offscreen target with explicit format, usage, load/store, lifetime, generation, and budget facts. |
| `SkSpecialImage` layer/filter source | `GPULayerSourcePlan` / `GPUFilterIntermediatePlan` | Bounded sampled source or intermediate with texture ownership and provenance; not a Skia class or CPU fallback object. |
| SaveLayer restore paint | `GPULayerRestorePlan` / `GPULayerCompositePlan` | Restore alpha, color filter, image filter, blend, clip, color-space, and destination-read facts are explicit composite plans. |
| Image filter graph planning | `GPUFilterPlan` | Filter DAG, intermediate resources, render/compute routes, and filter refusals outside `MaterialKey`. |
| `SkImageFilter` DAG | `GPUFilterGraphDescriptor` / `GPUFilterNodePlan` | Inputs, node kinds, bounds, crops, local matrices, and source semantics are dumpable graph facts; no Skia flattenable ownership. |
| `SkImageFilters` factory surface | `GPUFilterNodeDescriptor` / `GPUFilterNodeRoute` | Blur, crop, image, color filter, merge, matrix, morphology, lighting, and runtime shader families are target node descriptors with explicit route/refusal policy. |
| `SkImageFilter::filterBounds()` | `GPUFilterBoundsPlan` | Forward/reverse bounds, kernel expansion, finite-bounds proof, and crop/tile behavior are explicit validation inputs. |
| Graphite image filtering backend | `GPUFilterRenderNodePlan` / `GPUFilterComputeNodePlan` / `GPUFilterIntermediatePlan` | Filter execution uses planned render/compute nodes and provider-owned intermediates; no backend provider callbacks in core. |
| `SkSpecialImage` filter input/output | `GPUFilterSourcePlan` / `GPUFilterIntermediatePlan` | Source and intermediate images are typed resource plans with texture ownership and generation facts. |
| `SkImageFilters::RuntimeShader` | `GPUFilterRuntimeEffectPlan` | Runtime filter effects require registered Kanvas descriptors and WGSL validation; no arbitrary SkSL. |
| Layer/draw-context planning | `GPUDrawLayer` / `GPUDrawLayerPlanner` | Logical layer and composite scopes from captured state; not Graphite context classes. |
| `DrawListLayer` insertion | `GPUDrawInvocation` / `GPUDrawInsertion` | Graphite-inspired backward/forward insertion, sort windows, and merge policy; no C++ arena or bit-layout inheritance. |
| `Shape` / `Geometry` | `GPUShapeDescriptor` / `GPUGeometryPlan` | Immutable shape facts and selected geometry route; no mutable Skia objects in the core. |
| `SkPath` path data | `GPUPathDescriptor` / `GPUPathVerbSlice` | Canonical path verbs, points, fill rule, inverse flag, bounds, and stable key facts. |
| `SkStrokeRec` / `StrokeStyle` | `GPUStrokeDescriptor` / `GPUStrokeExpansionPlan` | Stroke width, cap, join, miter, hairline, dash/path-effect, and expansion route are explicit diagnostics. |
| Graphite renderer selection | `GPUGeometryRoute` / `GPUGeometryRenderStepPlan` | Analytic, tessellation, stencil-cover, prepared geometry, atlas, mask, or refusal route with dumpable render-step expansion. |
| Tessellation render steps | `GPUPathTessellationPlan` / `GPUPreparedGeometryPlan` | GPU-native or CPU-prepared geometry buffers with WGSL/layout evidence and no shaded CPU fallback. |
| `SkVertices` / mesh geometry | `GPUVerticesDescriptor` / `GPUMeshDescriptor` | Immutable user-provided vertex facts and future 2D mesh descriptors; no mutable source arrays in the core. |
| Graphite `VerticesRenderStep` | `GPUVerticesRenderStepPlan` | Topology, vertex layout, primitive-color ABI, texcoord ABI, and draw-call facts are explicit executable plans. |
| `SkVerticesPriv` index/color/texcoord facts | `GPUVertexLayoutPlan` / `GPUIndexBufferPlan` / `GPUVertexColorPlan` / `GPUVertexTexCoordPlan` | Attribute presence, formats, indices, colors, texcoords, and canonicalization are dumpable route facts. |
| Graphite primitive blender for vertices | `GPUPrimitiveColorPlan` / `GPUPrimitiveBlendPlan` | Per-vertex colors feed material evaluation before final target blend; arbitrary Skia blender source does not enter the core. |
| `ClipStack` | `GPUClipStackDescriptor` / `GPUClipPlan` | Captured clip state and per-draw effective clip plan; no mutable Graphite or Canvas stack inside the core. |
| `ClipStack::Element` and save records | `GPUClipElementDescriptor` / `GPUClipSaveRecordDescriptor` | Shape, operation, transform, bounds, generation, and lifetime are dumpable descriptor facts. |
| Graphite scissor and analytic clip selection | `GPUClipScissorPlan` / `GPUClipAnalyticPlan` | Simple clip routes are explicit and validated; no hidden approximation for complex clips. |
| Graphite depth-only clip draws | `GPUClipStencilPlan` / `GPUClipAtomicGroup` | Producer-consumer clip sequences are ordering-sensitive planner facts. |
| `ClipAtlasManager` and coverage mask render step | `GPUClipMaskPlan` plus spec 19 atlas objects | Coverage masks use typed artifacts, atlas generations, bindings, and upload/write-before-sample ordering. |
| Graphite `clipShader()` open route | `GPUClipShaderPlan` | Only registered descriptor-based clip shaders may route; arbitrary SkSL/source strings refuse. |
| `DrawPass` | `GPUDrawPass` | Immutable pass close to what the GPU facade will execute. |
| `Renderer` / `RenderStep` | `GPURenderStep` | Geometry/coverage technique with fixed shader and state contribution. |
| `PaintParamsKey` | `MaterialKey` | Paint/material identity; no SkSL. |
| `ShaderCodeDictionary` | `GPUMaterialDictionary` | Interns material keys, owns WGSL snippet metadata, and produces material assembly plans; no SkSL codegen. |
| `ShaderSnippet` | `WGSLSnippet` | Structured material WGSL function ABI with uniforms, resources, children, versions, and requirements. |
| `ShaderNode` | `WGSLSnippetNode` | Decompressed material tree node with propagated requirements and diagnostic provenance. |
| `UniquePaintParamsID` | `GPUMaterialProgramID` | Dictionary-local compact ID for an equivalent `MaterialKey`; not a portable identity by itself. |
| Graphite `RuntimeEffectDictionary` | `GPURuntimeEffectRegistrySnapshot` plus usage diagnostics | Recordings pin a runtime-effect descriptor generation; Kanvas does not retain arbitrary SkSL effects for later compilation. |
| Graphite runtime-effect snippet map | `GPURuntimeEffectRegistry` / `GPURuntimeEffectDescriptor` | Descriptor ID/version, uniform schema, child slots, WGSL plan, CPU oracle, and route contract are the support identity. |
| Skia `SkRuntimeEffect` compatibility source | `GPURuntimeEffectCompatibilityKey` / `GPURuntimeEffectLookupPlan` | Known source/stable-key inputs may map to registered descriptors; unknown source refuses. |
| `PipelineDataGatherer` | `GPUPayloadGatherer` | Collects concrete uniform/resource payload values after keys and layouts are accepted. |
| `UniformDataBlock` / `UniformDataCache` | `GPUUniformPayloadBlock` / `GPUUniformPayloadSlot` | Pass-local payload bytes and de-duplicated slots; values are not durable key facts. |
| `TextureDataBlock` / `TextureDataCache` | `GPUResourceBindingBlock` / `GPUResourceBindingSlot` | Ordered resource binding payloads and pass-local slots; no raw GPU handle identity. |
| `FloatStorageManager` | `GPUGradientPayloadStore` | Pass-local gradient stop storage when an accepted route uses buffer-backed gradient data. |
| `TextureProxy` | `GPUTextureDescriptor` / `GPUTextureOwnershipPlan` / `GPUTextureResourceRef` | Logical texture, ownership, and concrete resource ref are separated; no public lazy callback or raw pointer identity. |
| `TextureProxyView` | `GPUTextureViewDescriptor` | Texture view facts such as origin, swizzle policy, sample type, and subset; concrete handles stay in resources. |
| `Texture` | Provider-owned texture resource behind `GPUTextureResourceRef` | Concrete `GPU` facade object owned by `GPUResourceProvider`, not by material keys. |
| `Image_Graphite` | `GPUImageSourceDescriptor` | Logical image source plus color/sampling facts without leaking `SkImage` or raw handles into the core. |
| `SkCodec` | `KanvasImageCodec` plus `GPUImageCodecDescriptor` | Codec capability, metadata, decode result, and error behavior are selected through the Kanvas registry; no Skia codec API leaks into renderer core. |
| `SkAndroidCodec` output policy | `GPUImageDecodeRequest` / `GPUImageColorDecodePlan` / `GPUImagePixelPlan` | Output color type, alpha type, color space, sample size, and profile behavior are explicit plan facts. |
| `SkCodecAnimation` | `GPUAnimatedImagePlan` / `GPUImageFrameInfo` / `GPUImageFrameSelection` | Frame duration, required prior frame, disposal, blend, loop count, and selected-frame upload are planned explicitly. |
| `SkPixmapUtils::Orient` | `GPUImageOrientationPlan` | Encoded origin/orientation is either applied during preparation, represented in sampling, or refused. |
| `SkCodec::queryYUVAInfo()` / `SkYUVAPixmaps` | `GPUImagePixelPlan` plus future multi-plane route | Planar YUV/YUVA sources convert to accepted interleaved texture formats or refuse until a multi-plane WGSL route is specified. |
| `SkGainmapInfo` / gainmap codec paths | `GPUImageColorDecodePlan` plus `GPUColorPlan` | Gainmap/HDR metadata is preserved, tone-mapped, or refused through explicit color planning; platform codec success alone is not conformance. |
| `MakeBitmapProxyView()` | `GPUImageDecodePlan` / `GPUImageUploadPlan` / `UploadedTextureArtifact` / `GPUTextureOwnershipPlan` | CPU pixels become an explicit upload artifact, then ordinary texture ownership; no CPU-rendered fallback texture. |
| `TextureDataBlock` / `TextureDataCache` | `GPUSampledTextureBinding` inside `GPUResourceBindingBlock` / `GPUResourceBindingSlot` | Ordered sampled texture and sampler payloads; no raw handle or pointer identity in durable keys. |
| `DstUsage` | `GPUDestinationReadRequirement` / `GPUDestinationReadClass` | Destination dependency and planner ordering facts; no Skia bitmask API. |
| `DstReadStrategy` | `GPUDestinationReadStrategy` / `GPUDestinationReadPlan` | WebGPU-safe destination reads through fixed-function blend, target copy snapshots, existing intermediates, layer isolation, or refusal; no framebuffer-fetch assumption. |
| Graphite `dstCopy` texture | `GPUDestinationCopyPlan` / `GPUDestinationCopyTextureDescriptor` / `GPUDestinationReadBinding` | Explicit provider-owned target snapshot with bounds, generation, usage flags, payload binding, and budget diagnostics. |
| `DrawAtlas` | `GPUAtlasDescriptor` / `GPUAtlasEntryRef` / `GPUAtlasMutationPlan` | Atlas page/entry/generation/use-token concept; no Graphite page bit packing or C++ cache ownership. |
| `PathAtlas` / `RasterPathAtlas` | `GPUPathAtlasPlan` plus `PathAtlasArtifact` | Reusable path coverage route through typed `CPUPreparedGPU`; no generic CPU fallback proxy. |
| `ComputePathAtlas` | `GPUComputeCoverageAtlasPlan` | Future compute-written coverage atlas with WGSL compute validation and explicit storage/transition policy. |
| `ClipAtlasManager` | `GPUCoverageAtlasPlan` plus `CoverageMaskArtifact` | Clip/coverage mask route with separate path and save-record-style keys; no merged glyph/image/path atlas lifetime. |
| `AtlasProvider` | `GPUResourceProvider` plus `GPUAtlasScope` | Resource provider owns atlas textures, uploads, compaction, invalidation, and diagnostics. |
| `GlyphRunList` | `NormalizedDrawCommand.DrawTextRun` plus `GPUTextRunPlan` | Text stack emits glyph descriptors/artifacts; GPU renderer does not shape or own font data. |
| `SubRunContainer` / `AtlasSubRun` | `GPUTextSubRunPlan` | Renderer-owned subrun value object split by representation, atlas page, transform, material, clip, destination-read, and budget; no Graphite class hierarchy. |
| `SubRunData` | `GPUTextSubRunPlan` / `GPUTextInstanceLayout` / `GPUTextBinding` | Carries mask bounds, transform, glyph span, SDF facts, atlas references, and binding facts without raw pointers. |
| `TextAtlasManager` | `GPUTextAtlasPlan` / `GPUTextAtlasDescriptor` / `GPUTextAtlasEntryRef` | Text atlas resource, generation, upload, eviction, and page/entry validation through `GPUResourceProvider`. |
| `BitmapTextRenderStep` / `SDFTextRenderStep` | `GPUTextRenderStep` with `A8TextMaskStep` / `SDFTextMaskStep` | WGSL text render steps for atlas sampling; no SkSL and no LCD target claim. |
| `GlyphVector` backend data | `GPUTextAtlasEntryRef` plus `GPUTextUploadPlan` | Per-glyph atlas residency and upload facts are resource/payload data, not material identity. |
| `Slug` / text blob redraw cache | `GPUTextRunPlan` cacheable diagnostics and text-stack artifact keys | Reuse decisions depend on dumpable artifact keys and generations, not Skia object identity. |
| `GraphicsPipelineDesc` | `GPURenderPipelineKey` | Render step, material, target state, fixed state, and capabilities. |
| `ResourceProvider` | `GPUResourceProvider` | Pipelines, buffers, textures, samplers, atlases, and cache ownership. |
| `SharedContext` / `Caps` | `GPUExecutionContext` / `GPUCapabilities` | Facade implementation, device generation, queue facts, and capability snapshot. |
| `CommandBuffer` / `QueueManager` | `GPUCommandSubmission` | Encoded command scopes, submission result, readback, and device-loss diagnostics. |
| `RenderPassDesc` | `GPUTargetState` | Attachment format, load/store, sample count, write state, and target assumptions. |
| `Uniform` / payload layout | `WGSLUniformLayout` / `WGSLPackingPlan` | WGSL reflection-backed ABI and Kotlin packing; no SkSL type ownership. |
| `GlobalCache` / recorder-local resources | `GPUSharedScope` / `GPURecorderScope` | Conceptual scope split for cache and transient resource lifetimes. |

The mapping is conceptual. Kanvas is not required to preserve Graphite class
names, inheritance, virtual dispatch shape, backend plugin model, or task
implementation.

## Data Flow

```text
legacy stateful API
  -> adapter captures transform/clip/layer/material/bounds
  -> NormalizedDrawCommand
  -> GPULayerPlan / GPUFilterPlan
  -> GPULayerExecutionPlan + GPULayerTaskPlan when saveLayer scopes are used
  -> GPUFilterNodePlan + GPUFilterIntermediatePlan when filters are used
  -> GPUGeometryPlan + GPUGeometryRoute
  -> GPUVerticesDescriptor + GPUVerticesRoute when DrawVertices is used
  -> GPURuntimeEffectLookupPlan + GPURuntimeEffectDescriptor when registered runtime effects are used
  -> GPURecorder
  -> GPUDrawAnalysis
  -> GPUOcclusionTracker + GPUDrawLayerPlanner
  -> GPURecording
  -> GPUTaskList
  -> GPUDrawPass
  -> GPURenderStep + MaterialKey
  -> GPUMaterialDictionary + WGSLSnippetNode tree
  -> GPUImagePipelinePlan + UploadedTextureArtifact when encoded/CPU image pixels are used
  -> GPUImageSourceDescriptor + GPUTextureOwnershipPlan when images/textures are used
  -> GPUPathAtlasPlan / GPUCoverageAtlasPlan when path or coverage masks are used
  -> GPUVertexLayoutPlan + GPUVertexBufferPlan when vertices/mesh are used
  -> GPURuntimeEffectUniformBlockPlan + GPURuntimeEffectChildSlotPlan when effects are used
  -> GPUTextRunPlan + GPUTextSubRunPlan when text/glyph artifacts are used
  -> GPUBlendPlan + GPUColorPlan + GPUTargetState
  -> WGSLBindingLayout + WGSLPackingPlan
  -> GPUPayloadGatherer + payload slots
  -> GPURenderPipelineKey
  -> GPUResourceProvider
  -> GPUExecutionContext + GPUCommandSubmission
  -> GPU facade command submission
```

The new core receives fully captured draw state. It does not replay save,
restore, clip, or matrix operations.

## GPU-First Route Order

The renderer prefers route selection in this order:

1. `GPUNative`
2. `CPUPreparedGPU`
3. `RefuseDiagnostic`

`CPUReferenceOnly` is not a production route. It exists to produce oracles,
diffs, diagnostics, and conformance evidence.

`CPUPreparedGPU` is allowed only when CPU work prepares an artifact that the
GPU consumes, such as a coverage mask, path atlas entry, uploaded image
texture, composed animated-image frame, geometry buffer, or another registered
typed artifact. It must not become silent full CPU rendering.

## WGSL-Only Shader Implementation

WGSL is the only shader implementation target for the new renderer.

Graphite's SkSL paint-key machinery maps to Kanvas `MaterialKey`,
`GPURenderPipelineKey`, `WGSLFragment`, and `WGSLModule` concepts. Compute work
uses `GPUComputeProgramKey`, `WGSLComputeModule`, and `GPUComputePipelineKey`
instead of `MaterialKey`. SkSL may appear only as compatibility vocabulary
around Skia-facing APIs. It must not appear as a runtime shader language for
new GPU renderer implementation.

Before GPU submission, the complete assembled WGSL module for a route must be
validated and reflected through `wgsl4k`. Validating individual fragments is
useful evidence, but it is not enough to claim that a GPU-submitted shader is
supported.

## `KanvasPipelineIR` Position

`KanvasPipelineIR` remains relevant historical and compatibility context. It
does not become the durable semantic center of the new GPU renderer.

New specs and tickets may reuse proven `KanvasPipelineIR` facts when they are
useful, but the core contract is `NormalizedDrawCommand` plus
`MaterialKey` and render/compute pipeline-key families, not
`KanvasPipelineIR` execution.

## Implementation Slicing Policy

This kernel does not choose the first implementation slice. Implementation
plans come later and must cite the full target contracts instead of narrowing
the renderer architecture to an initial feature subset.

Future slices must keep `:gpu-renderer` contract tests isolated from
`gpu-raster` integration until the touched contracts have deterministic dumps,
key preimages, route diagnostics, resource-planning behavior, and WGSL
validation evidence. Integration must not silently change the default legacy
route or pixels; route activation needs explicit evidence.

## Non-Goals

- No Ganesh port.
- No Graphite port.
- No SkSL implementation, including arbitrary SkSL compiler behavior.
- No broad CPU fallback.
- No new browser-only assumption.
- No hidden workaround for `wgsl4k` parser or reflection behavior.
- No render behavior change during cleanup-only phases.

## Acceptance Rules

The architecture kernel can be treated as accepted only when:

- the target direction is approved by project owners;
- the module boundary is referenced by implementation tickets;
- cleanup tickets prove no render changes;
- isolated `:gpu-renderer` tests pass before `gpu-raster` integration;
- complete GPU-submitted WGSL modules validate through `wgsl4k`;
- WGSL binding layouts, reflection, and Kotlin packing plans match;
- blend/color/target-state plans are explicit for promoted routes;
- execution-context and device-generation assumptions are tested;
- telemetry distinguishes correctness support from performance readiness;
- image/bitmap/codec routes expose codec registry, decode, color, animation,
  artifact, upload, texture ownership, and stable refusal evidence before
  support claims;
- the first promoted route reports `GPUNative`, `CPUPreparedGPU`, or
  `RefuseDiagnostic` deterministically;
- the old `KanvasPipelineIR` center is not silently reintroduced through
  adapter code.
