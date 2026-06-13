# Package And Class Layout

Status: Draft
Date: 2026-06-13

## Purpose

Define the target package and class ownership layout for the `:gpu-renderer`
module.

This closes the remaining package-placement ambiguity in the GPU renderer
target. It is still a target contract, not an implementation slice. File names,
private helper names, and small internal refactors may evolve with
implementation evidence, but core concept ownership must follow this layout
unless a future accepted spec changes it.

## Package Root

All production contracts owned by `:gpu-renderer` live under:

```text
org.graphiks.kanvas.gpu.renderer
```

Package segments are lowercase ASCII. Public class and concept names keep
uppercase `GPU`, `CPU`, and `WGSL` acronyms.

The module must not define renderer core contracts outside this root. Legacy
adapters in other modules may depend on this root, but this root must not
depend on Skia-like public API objects such as `SkCanvas`, `SkPaint`,
`SkShader`, or `SkPath`.

## Package Bands

The layout uses responsibility bands. Bands are dependency guidance, not
source-set names.

| Band | Packages | Role |
|---|---|---|
| Foundation facts | `diagnostics`, `telemetry`, `capabilities`, `state`, `color`, `coordinates` | Shared immutable facts, status, limits, target state, color, and coordinate contracts. |
| Input and semantic plans | `commands`, `materials`, `runtimeeffects`, `geometry`, `vertices`, `clips`, `destination`, `layers`, `filters`, `images`, `text` | Normalized command shape and domain-specific semantic plans. |
| Shader, keys, and payload | `wgsl`, `payloads`, `pipelines` | WGSL ABI/module contracts, payload packing/gathering, render/compute pipeline keys. |
| Recording and planning | `routing`, `analysis`, `recording`, `passes` | Route decisions, immutable analysis, recordings, task lists, draw passes, render steps, sort windows. |
| Materialization and submission | `resources`, `execution` | Resource provider, concrete resource lifetime, materialization, target preparation, command submission, readback. |
| Evidence | `validation` | Contract fixtures, dump schemas, evidence helpers used by tests and PM bundles. |

Packages should prefer dependencies on packages in the same row or earlier
rows of this table. The explicit rules in `Dependency Rules` are authoritative
for allowed exceptions. No production package cycle is allowed.

## Canonical Packages

### `diagnostics`

Owns stable diagnostic data and canonical reason-code helpers.

Primary concepts:

- `GPUDiagnostic`
- `GPUDiagnosticCode`
- `GPUDiagnosticDomain`
- `GPUDiagnosticSeverity`
- `GPUDiagnosticSink`
- `GPUDiagnosticDump`

This package implements the naming policy from
`32-target-authority-taxonomy-diagnostics.md`. Domain packages may define
domain-specific diagnostic payloads, but canonical code validation belongs
here.

### `telemetry`

Owns counters, ledgers, budget observations, warmup facts, and PM evidence
records.

Primary concepts:

- `GPUTelemetryLedger`
- `GPUTelemetryCounter`
- `GPUCacheTelemetry`
- `GPUBudgetTelemetry`
- `GPUPromotionEvidence`
- `GPUPerformanceGate`

Telemetry must not decide route support. It observes decisions made by routing,
analysis, resources, execution, and validation.

### `capabilities`

Owns facade, adapter, device, and implementation capability facts.

Primary concepts:

- `GPUCapabilities`
- `GPUCapabilityFact`
- `GPUFeatureRequirement`
- `GPULimitRequirement`
- `GPUImplementationIdentity`
- `GPUCapabilityDiagnostic`

Domain planners may read `GPUCapabilities`, but they must not import
`execution` just to learn limits.

### `state`

Owns target, blend, alpha, attachment, and high-level captured render-state
facts that are not specific to one domain.

Primary concepts:

- `GPUTargetState`
- `GPUTargetTextureDescriptor`
- `GPUBlendPlan`
- `GPUBlendMode`
- `GPUAlphaPlan`
- `GPUStorePlan`
- `GPULoadStorePlan`
- `GPUSampleState`

Detailed color-management facts live in `color`; detailed destination-read
facts live in `destination`.

### `color`

Owns color-management plans, value specs, color-space/profile facts,
conversion, HDR/gainmap, and store conversion policy.

Primary concepts:

- `GPUColorManagementPlan`
- `GPUColorValueSpec`
- `GPUColorSpaceDescriptor`
- `GPUColorProfileDescriptor`
- `GPUColorConversionPlan`
- `GPUColorTransformPlan`
- `GPUWorkingColorSpacePlan`
- `GPUGradientColorPlan`
- `GPUColorUniformPlan`
- `GPUHDRColorPlan`
- `GPUGainmapPlan`
- `GPUColorStorePlan`
- `GPUColorCachePlan`
- `GPUColorDiagnostic`

### `coordinates`

Owns coordinate spaces, transforms, bounds proofs, pixel-grid facts, rounding,
and precision policy.

Primary concepts:

- `GPUCoordinateSpace`
- `GPUTransformPlan`
- `GPUInverseTransformPlan`
- `GPUPixelGridPlan`
- `GPUBoundsPlan`
- `GPUBoundsProof`
- `GPURoundingPlan`
- `GPUClipReductionProof`
- `GPUCoordinatePayloadPlan`
- `GPUTransformDiagnostic`

### `commands`

Owns normalized command intake contracts.

Primary concepts:

- `NormalizedDrawCommand`
- `GPUDrawCommandID`
- `GPUDrawCommandFamily`
- `GPUDrawCommandProvenance`
- `GPUDrawOrderingToken`
- `GPUCommandBounds`
- `GPUCommandCapture`

Commands may reference domain descriptors by interface or immutable data class,
but domain packages must not depend on `commands`. Domain lowering consumes
command facts through analysis or explicit planner input objects.

### `materials`

Owns paint/material-source planning, material keys, material dictionary, WGSL
material snippet metadata, and material root sets.

Primary concepts:

- `GPUPaintDescriptor`
- `GPUPaintPipelinePlan`
- `GPUPaintStagePlan`
- `GPUPaintEvaluationOrder`
- `GPUMaterialSourceDescriptor`
- `GPUMaterialSourceKind`
- `GPUMaterialSourcePlan`
- `GPUSolidColorPlan`
- `GPUGradientPlan`
- `GPUGradientKind`
- `GPUGradientGeometryPlan`
- `GPUGradientStopPlan`
- `GPUGradientStopStorePlan`
- `GPUMaterialTileMode`
- `GPUMaterialSamplingPlan`
- `GPUImageShaderPlan`
- `GPULocalMatrixShaderPlan`
- `GPUShaderBlendSourcePlan`
- `GPUPaintColorPlan`
- `MaterialKey`
- `GPUMaterialDictionary`
- `GPUMaterialProgramID`
- `GPUMaterialLoweringContext`
- `GPUMaterialRootSet`
- `WGSLSnippet`
- `WGSLSnippetID`
- `WGSLSnippetNode`
- `GPUMaterialAssemblyPlan`
- `GPUMaterialSourcePayloadPlan`
- `GPUMaterialSourceDiagnostic`
- `GPUPaintPipelineDiagnostic`

Generic WGSL module, reflection, binding, and packing contracts live in
`wgsl`. `materials` owns `WGSLSnippet` because snippets are material dictionary
entries, not standalone shader modules.

### `runtimeeffects`

Owns registered runtime-effect descriptors and registry lookup. The package
name is one lowercase segment to keep Kotlin package style stable.

Primary concepts:

- `GPURuntimeEffectRegistry`
- `GPURuntimeEffectDescriptor`
- `GPURuntimeEffectID`
- `GPURuntimeEffectDescriptorVersion`
- `GPURuntimeEffectUniformSchema`
- `GPURuntimeEffectUniformBlockPlan`
- `GPURuntimeEffectChildSlotPlan`
- `GPURuntimeEffectResourcePlan`
- `GPURuntimeEffectWGSLPlan`
- `GPURuntimeEffectCPUOracle`
- `GPURuntimeEffectRouteContract`
- `GPURuntimeEffectLiveEditPlan`
- `GPURuntimeEffectUsageSet`
- `GPURuntimeEffectDiagnostic`

This package must not accept arbitrary SkSL or arbitrary WGSL strings as
product shader support.

### `geometry`

Owns shape, path, stroke, coverage, stencil-cover, tessellation, prepared
geometry, and path/coverage atlas plan contracts.

Primary concepts:

- `GPUShapeDescriptor`
- `GPUPathDescriptor`
- `GPUStrokeDescriptor`
- `GPUGeometryPlan`
- `GPUGeometryRoute`
- `GPUPathBoundsPlan`
- `GPUStrokeExpansionPlan`
- `GPUStencilCoverPlan`
- `GPUPreparedGeometryPlan`
- `GPUGeometryRenderStepPlan`
- `GPUPathAtlasPlan`
- `GPUCoverageAtlasPlan`
- `GPUAtlasPolicy`
- `GPUAtlasBudgetPolicy`
- `GPUAtlasEntryRef`
- `GPUAtlasMutationPlan`
- `PathAtlasArtifact`
- `CoverageMaskArtifact`
- `PrecomputedGeometryArtifact`
- `GPUGeometryDiagnostic`

Actual atlas textures and buffers are materialized by `resources`.

### `vertices`

Owns `DrawVertices` and future 2D mesh-like route contracts.

Primary concepts:

- `GPUVerticesDescriptor`
- `GPUVertexLayoutPlan`
- `GPUVertexColorPlan`
- `GPUVertexTexCoordPlan`
- `GPUPrimitiveBlendPlan`
- `GPUIndexBufferPlan`
- `GPUVertexBufferPlan`
- `GPUVerticesRoute`
- `GPUVerticesRenderStepPlan`
- `GPUMeshDescriptor`
- `GPUVerticesDiagnostic`

Vertex and index buffer resource creation is owned by `resources`; this
package owns layout, packing intent, route facts, and diagnostics.

### `clips`

Owns captured clip stack descriptors and clip execution plans.

Primary concepts:

- `GPUClipStackDescriptor`
- `GPUClipElementPlan`
- `GPUClipPlan`
- `GPUClipBoundsPlan`
- `GPUClipScissorPlan`
- `GPUClipAnalyticPlan`
- `GPUClipStencilPlan`
- `GPUClipMaskPlan`
- `GPUClipShaderPlan`
- `GPUClipOrderingToken`
- `GPUClipDiagnostic`

Clip masks may reference typed artifacts from `geometry`, but concrete
resources remain in `resources`.

### `destination`

Owns destination/backdrop read requirements and strategies.

Primary concepts:

- `GPUDestinationReadRequirement`
- `GPUDestinationReadPlan`
- `GPUDestinationReadStrategy`
- `GPUDestinationReadBounds`
- `GPUDestinationReadBinding`
- `GPUDestinationReadToken`
- `GPUDestinationReadDiagnostic`

This package owns destination-read legality. `execution` owns actual copy
command submission.

### `layers`

Owns semantic and executable layer/saveLayer plans plus low-level draw-layer
planning structures.

Primary concepts:

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
- `GPUDrawLayer`
- `GPUDrawLayerPlanner`
- `GPULayerDiagnostic`

`layers` may depend on `filters` and `destination` for semantic plans, but it
must not allocate textures or submit passes.

### `filters`

Owns filter/effect graph contracts and filter node route plans.

Primary concepts:

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
- `FilterIntermediateArtifact`
- `GPUFilterDiagnostic`

Filter intermediates are descriptors and artifact contracts here. Concrete
intermediate textures are owned by `resources`.

### `images`

Owns image-source, encoded image, codec, decode, animation-frame, pixel
preparation, orientation, mip, and upload-plan contracts.

Primary concepts:

- `GPUImageSourceDescriptor`
- `GPUImagePipelinePlan`
- `GPUEncodedImageSource`
- `GPUImageCodecRegistry`
- `GPUImageCodecDescriptor`
- `GPUImageDecodeRequest`
- `GPUImageDecodePlan`
- `GPUImageDecodeResult`
- `GPUAnimatedImagePlan`
- `GPUImageFrameInfo`
- `GPUImageFrameSelection`
- `GPUImageColorDecodePlan`
- `GPUImageOrientationPlan`
- `GPUImagePixelPlan`
- `GPUImageMipmapPlan`
- `GPUImageUploadPlan`
- `GPUImageUploadArtifactKey`
- `UploadedTextureArtifact`
- `GPUImageDiagnostic`

Texture descriptors, views, samplers, ownership, and concrete texture refs are
owned by `resources`.

### `text`

Owns GPU text/glyph handoff contracts after the pure Kotlin text stack has
produced typed text artifacts.

Primary concepts:

- `GPUTextRunPlan`
- `GPUTextSubRunPlan`
- `GPUTextRoute`
- `GPUTextRenderStep`
- `GPUTextAtlasPlan`
- `GPUTextBinding`
- `GPUTextInstancePlan`
- `GPUTextSDFParams`
- `GPUTextOrderingToken`
- `GlyphAtlasArtifact`
- `SDFGlyphAtlasArtifact`
- `GlyphUploadPlan`
- `OutlineGlyphPlan`
- `ColorGlyphPlan`
- `BitmapGlyphPlan`
- `SVGGlyphPlan`
- `GPUTextDiagnostic`

Font shaping and text layout stay outside `:gpu-renderer`.

### `wgsl`

Owns generic WGSL module, fragment, reflection, binding, and Kotlin packing
ABI contracts.

Primary concepts:

- `WGSLFragment`
- `WGSLModule`
- `WGSLComputeModule`
- `WGSLModuleHash`
- `WGSLReflectionResult`
- `WGSLBindingLayout`
- `WGSLUniformLayout`
- `WGSLStorageLayout`
- `WGSLResourceBindingPlan`
- `WGSLPackingPlan`
- `WGSLValidationDiagnostic`

`wgsl` must not own material-source semantics. It validates and describes
complete WGSL modules assembled from domain plans.

### `payloads`

Owns payload gathering, write plans, payload slots, fingerprints, upload
plans, and pass-local value/resource binding records.

Primary concepts:

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
- `GPUPayloadDiagnostic`

Payload values are not durable key identity.

### `pipelines`

Owns executable render and compute program/pipeline identities.

Primary concepts:

- `GPURenderPipelineKey`
- `GPUComputeProgramKey`
- `GPUComputePipelineKey`
- `GPUPipelineKeyPreimage`
- `GPUPipelineCacheKey`
- `GPUPipelineCreationPlan`
- `GPUPipelineDiagnostic`

`MaterialKey` remains in `materials`; render and compute executable pipeline
keys live here.

### `routing`

Owns route taxonomy, route decisions, route preimages, artifact route
registration, and refusal selection.

Primary concepts:

- `GPURouteKind`
- `GPUNativeRoute`
- `CPUPreparedGPURoute`
- `CPUReferenceOnlyRoute`
- `RefuseDiagnostic`
- `GPURouteDecision`
- `GPURoutePreimage`
- `CPUPreparedGPUArtifactRegistry`
- `CPUPreparedGPUArtifactDescriptor`
- `CPUPreparedGPUArtifactKey`
- `GPURouteDiagnostic`

`routing` decides which route kind is selected. It does not allocate resources
or execute CPU preparation.

### `analysis`

Owns immutable draw analysis, occlusion, sort keys, dependency summaries, and
analysis-time decisions.

Primary concepts:

- `GPUDrawAnalysis`
- `GPUDrawAnalysisRecord`
- `GPUDrawAnalysisDecision`
- `GPUOcclusionTracker`
- `GPUOcclusionProof`
- `SortKey`
- `GPUAnalysisDependency`
- `GPUAnalysisDiagnostic`

Analysis cannot materialize concrete GPU resources. Late outcomes belong to
`resources` and `execution` through
`34-analysis-materialization-recording.md`.

### `recording`

Owns recorder scopes, immutable recordings, recording compatibility keys,
ordered recordings, and task-list assembly.

Primary concepts:

- `GPURecorder`
- `GPURecording`
- `GPURecordingCompatibilityKey`
- `GPURecordingID`
- `GPUOrderedRecording`
- `GPUSharedScope`
- `GPURecorderScope`
- `GPUFrameScope`
- `GPUAtlasScope`
- `GPUTask`
- `GPUTaskList`
- `GPUTaskDependency`
- `GPURecordingDiagnostic`

`GPURecorder` accepts normalized commands and target facts; it does not accept
Skia-like state-stack operations.
Recordings may contain a `GPURuntimeEffectUsageSet`, but that type is owned by
`runtimeeffects`.

### `passes`

Owns pass-level draw invocation, insertion, draw-pass, render-step, compute
task, copy task, upload task, and sort-window contracts.

Primary concepts:

- `GPUDrawInvocation`
- `GPUDrawInsertion`
- `GPUDrawPass`
- `GPURenderStep`
- `GPURenderStepID`
- `GPURenderStepPlan`
- `GPUComputeTask`
- `GPUCopyTask`
- `GPUUploadTask`
- `GPUSortWindow`
- `GPUPassDiagnostic`

Passes describe work close to submission, but `execution` owns command
encoding against the `GPU` facade.

### `resources`

Owns resource descriptors, concrete resource references, provider interfaces,
resource materialization, caches, artifact lookup, lazy/promise/imported
resources, and scratch/intermediate lifetime tokens.

Primary concepts:

- `GPUResourceProvider`
- `GPUResourceMaterializationDecision`
- `GPUTargetPreparationContext`
- `GPUTextureDescriptor`
- `GPUTextureViewDescriptor`
- `GPUSamplerDescriptor`
- `GPUTextureOwnershipPlan`
- `GPUTextureAllocationPlan`
- `GPUTextureResourceRef`
- `GPUImportedTextureDescriptor`
- `GPUSurfaceTextureLease`
- `GPUSampledTextureBinding`
- `GPULazyResourcePlan`
- `GPUPromiseResourcePlan`
- `GPUImportedResourcePlan`
- `GPUVolatileResourcePlan`
- `GPUUseToken`
- `GPUPendingReadToken`
- `GPUPendingWriteToken`
- `GPUScratchResourceToken`
- `GPUIntermediateResourceToken`
- `GPUTextureDiagnostic`
- `GPUResourceDiagnostic`

This package owns concrete resource identity. Material and pipeline keys may
only include resource topology, layout, usage, and capability facts.

### `execution`

Owns execution context, target/surface binding, command submission, readback,
device-generation handling, and facade interaction.

Primary concepts:

- `GPUExecutionContext`
- `GPUCommandScope`
- `GPUCommandSubmission`
- `GPUSurfaceTarget`
- `GPUSurfaceTargetDescriptor`
- `GPUFrameSubmission`
- `GPUReadbackRequest`
- `GPUReadbackResult`
- `GPUDeviceGeneration`
- `GPUExecutionDiagnostic`

This package is the only production package allowed to call command-submission
APIs on the `GPU` facade. Other packages describe work through plans and
materialization records.

### `validation`

Owns dump schemas, fixture descriptors, contract checks, package-boundary
checks, and evidence helpers.

Primary concepts:

- `GPUValidationFixture`
- `GPUValidationReport`
- `GPUContractDump`
- `GPUKeyPreimageDump`
- `GPUWGSLReflectionDump`
- `GPUPackageBoundaryCheck`
- `GPUForbiddenImportCheck`
- `GPUPromotionGateCheck`

Production routes must not depend on validation helpers. Tests and PM bundles
may depend on them.

## Dependency Rules

The following dependencies are forbidden:

- any package importing Skia-like public API types;
- any package importing Graphite/Ganesh source classes;
- foundation packages importing recording, passes, resources, or execution;
- domain packages importing concrete execution APIs;
- `materials` importing concrete texture handles from `resources`;
- `wgsl` importing domain semantics except through declared fragment/module
  inputs;
- `resources` importing legacy API objects;
- `execution` reinterpreting material, clip, layer, filter, image, text, or
  geometry semantics.

Allowed cross-package dependencies:

- domain planners may depend on `diagnostics`, `capabilities`, `state`,
  `color`, `coordinates`, and relevant domain descriptor packages;
- `analysis` may depend on commands, routing, domain plans, layers, clips,
  destination, materials, geometry, vertices, images, text, filters, color, and
  coordinates;
- `recording` may depend on analysis, routing, layers, passes, resources,
  runtimeeffects, diagnostics, and telemetry;
- `passes` may depend on analysis records, pipeline keys, payload plans,
  resource descriptors, layer/destination/clip ordering tokens, and render-step
  plans;
- `resources` may depend on descriptors and keys from domain packages,
  pipelines, payloads, wgsl, capabilities, diagnostics, and telemetry;
- `execution` may depend on recordings, passes, resources, state,
  capabilities, diagnostics, and telemetry.

Any cycle across production packages is a design failure. If implementation
pressure creates a cycle, extract a smaller immutable descriptor into a lower
band instead of adding a back edge.

## Public And Internal Surface

Public API from `:gpu-renderer` is limited to:

- immutable descriptors and plans needed by adapters or tests;
- route decisions and diagnostics;
- keys, preimages, and dumps needed for evidence;
- recorder, recording, resource-provider, and execution interfaces;
- WGSL ABI descriptors and reflection records needed for validation.

Implementation details stay `internal`:

- cache implementations;
- mutable builders;
- hash-map storage;
- arena or pooling helpers;
- sort-key bit packing;
- command encoder adapters;
- upload staging allocators;
- atlas page allocators;
- shader source concatenation helpers;
- retry loops and eviction heuristics.

Public classes must not expose mutable collections as live state. Dumps and
preimages must be deterministic value objects.

## Graphite Orientation Table

This table is for review orientation only. It is not a package mirror.

| Graphite area | Kanvas package owner |
|---|---|
| `Recorder`, `Recording`, task list | `recording` |
| `DrawList`, draw analysis, occlusion, sort keys | `analysis`, `layers`, `passes` |
| `DrawPass`, `RenderStep`, renderers | `passes`, `pipelines` |
| `PaintParamsKey`, shader dictionary, snippets | `materials`, `wgsl` |
| `PipelineDataGatherer` | `payloads` |
| Graphics/compute pipeline descriptions | `pipelines` |
| `Caps` | `capabilities` |
| `ResourceProvider`, proxy/resource cache | `resources` |
| Target/device command submission | `execution` |
| Clip stack and stencil/mask planning | `clips`, `geometry`, `passes` |
| Image, texture, sampler handling | `images`, `resources` |
| Text atlas and glyph routes | `text`, `resources` |

Kanvas must not mirror Graphite file names, namespace layering, inheritance
hierarchies, or backend plugin structure.

## Validation Requirements

Before the first implementation slice is promoted, the project must include
package-boundary evidence:

- a forbidden-import check for Skia-like API, Ganesh, and Graphite classes
  inside `:gpu-renderer`;
- a package cycle check for production source packages;
- a check that public package roots stay under
  `org.graphiks.kanvas.gpu.renderer`;
- fixture dumps showing concept ownership for the first slice classes;
- diagnostics proving package-boundary violations fail deterministically in
  validation tooling;
- review evidence that aliases are used instead of renaming public concepts
  when Kotlin import collisions occur.

## Non-Goals

- Do not freeze exact Kotlin file names.
- Do not freeze private helper classes.
- Do not require one class per file when a small sealed hierarchy is clearer.
- Do not create a Graphite-like package tree.
- Do not move Skia-like compatibility adapters into `:gpu-renderer`.
- Do not introduce a browser-only `webgpu` package.
