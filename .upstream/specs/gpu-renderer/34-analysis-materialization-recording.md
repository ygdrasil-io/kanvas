# Analysis, Materialization, And Recording Lifetime

Status: Draft
Date: 2026-06-13

## Purpose

Define the boundary between immutable draw analysis and later GPU resource
materialization, and define recording lifetime, replay, lazy resource,
scratch/intermediate, and late-diagnostic rules.

Graphite-inspired renderers can discover failures after initial draw analysis:
atlas insertion can fail, upload budgets can be exceeded, a promised texture
can be unavailable, a pipeline can fail validation, or a target copy can become
illegal. Kanvas must make those late outcomes explicit without introducing
hidden CPU fallback.

## Stage Model

The target stage order is:

```text
NormalizedDrawCommand
  -> GPUDrawAnalysisDecision
  -> GPULayerExecutionPlan / GPUFilterPlan / GPUClipPlan
  -> GPUDrawInvocation / sort-window plan
  -> GPUMaterialAssemblyPlan / handle-free pipeline descriptor
  -> GPURecording / GPUTaskList dependency finalization
  -> GPUFramePlanner / GPUFramePlan linear order
  -> GPUFramePreflighter
       -> GPUResourceMaterializationDecision
       -> GPUPassCommandStream / GPUCommandEncoderPlan
       -> PreparedGPUFrame | terminal preflight refusal + rollback
  -> GPUFrameExecutor / one GPUCommandSubmission
  -> GPUQueueCompletionTicket terminal result
```

Each stage may add diagnostics. A later stage may refuse an earlier accepted
candidate only with a stable reason code and visible evidence.

Analysis, recording, task, and frame-plan products contain no concrete GPU
handles, surface leases, transient offsets, or pass command streams.
`GPUFrameCoordinator` is the sole product entry across planner finalization,
preflight, and execution. It adds no route decision and preserves a planning or
preflight refusal as a terminal frame outcome.

## `GPUDrawAnalysisDecision`

`GPUDrawAnalysisDecision` is the immutable pre-materialization decision for a
normalized command or derived draw invocation.

It records:

- command ID and provenance;
- route kind candidate or analysis-time refusal;
- material lowering result or material refusal;
- geometry, clip, layer, filter, text, image, vertices, color, transform, and
  destination-read plan summaries;
- render-step candidate list;
- dependency and ordering facts;
- cull or discard proof when applicable;
- resource declarations known before allocation;
- diagnostics.

It must not record:

- concrete GPU resource handles;
- bind group instances;
- transient buffer offsets;
- atlas entry coordinates that are not yet allocated;
- surface texture leases;
- cache hit/miss results;
- upload staging resources;
- one-frame scratch texture identities.

An analysis decision can say "this route is possible if materialization
succeeds". It cannot claim product support by itself.

## `GPUResourceMaterializationDecision`

`GPUResourceMaterializationDecision` is the late decision produced only by
`GPUFramePreflighter`, after `GPUTaskList` dependencies have been projected
into final `GPUFramePlan` order, when the renderer resolves resources,
pipelines, atlases, uploads, target snapshots, intermediates, and
device-generation facts for a frame.

It records:

- analysis decision ID or derived task ID;
- confirmed route kind;
- materialized pipeline, module, layout, and reflection status;
- accepted or refused resource descriptors;
- typed artifact keys and resolved artifact states;
- atlas insertion, upload, mutation, eviction, and retry decisions;
- destination-read copy or layer-isolation resource decisions;
- lazy/promise resource fulfillment status;
- scratch/intermediate allocation and lifetime token;
- stable late diagnostic.

It must not silently change an analysis `GPUNative` candidate into
`CPUPreparedGPU` unless the analysis decision already named that alternate
route and the domain spec accepts the alternate artifact. It must never change
a refused or unsupported feature into broad CPU-rendered texture
compatibility.

## Late Failure Classes

Late failures are expected and must be first-class diagnostics:

| Failure class | Example canonical domain |
|---|---|
| Atlas capacity or insertion failure | `unsupported.atlas.capacity` |
| Atlas entry in use and retry exhausted | `unsupported.atlas.in_use_try_again_limit` |
| Upload or staging budget exceeded | `budget.resource.upload_exceeded` |
| Artifact stale for device or generation | `stale.artifact.generation` |
| Texture ownership invalid or promise unfulfilled | `unsupported.texture.ownership_missing` |
| Surface lease expired or wrong generation | `stale.texture.surface_lease` |
| Destination copy illegal after pass partitioning | `unsupported.destination_read.pass_split_illegal` |
| Active attachment sampled | `unsupported.texture.active_attachment_sampled` |
| WGSL reflection or ABI mismatch | `validation.wgsl.abi_mismatch` |
| Pipeline creation failed for capability reasons | `capability.pipeline.missing_feature` |
| Device lost or generation mismatch | `unsupported.resource.device_lost` |

Domain specs may add more concrete reasons. They must use the canonical
diagnostic policy from `32-target-authority-taxonomy-diagnostics.md`.

## Recording Lifetime Policy

`GPURecording` is immutable after recording closes.

The target supports replay only under an explicit compatibility contract:

- a recording has a `GPURecordingCompatibilityKey`;
- the key includes command-shape version, material dictionary version, runtime
  registry snapshot facts, required capability class, target format class,
  attachment/sample requirements, and resource topology requirements;
- replay is legal only when the execution target satisfies that key;
- surface texture leases, one-frame scratch resources, bind groups, and
  transient handles are never stored as durable recording identity.

`GPURecording` may be submitted more than once only if every lazy, promised,
imported, surface, and volatile resource in the recording can be revalidated or
rematerialized for the new frame/device generation. Otherwise replay refuses
with a stable diagnostic.

If an implementation chooses a one-shot recording for an early slice, it must
still expose the compatibility key and refusal semantics so later replayable
recordings do not reinterpret the data model.

## Ordered Recordings

Multiple recordings inserted into one target execution must preserve explicit
ordering:

- each recording has an insertion order token;
- cross-recording destination reads, layer composites, and target load/store
  behavior are barriers unless a future accepted spec proves reordering;
- resource uploads that must happen before sampling carry dependency tokens;
- a refusal in one recording does not invalidate later recordings unless the
  target state itself cannot be made valid.

This is the Kanvas target equivalent of Graphite's ordered recording/task
submission model, without inheriting Graphite class ownership.

## Lazy, Promise, Imported, And Volatile Resources

Resource plans may refer to deferred resources only through explicit
descriptors:

| Resource class | Policy |
|---|---|
| `GPULazyResourcePlan` | Descriptor is known during analysis; concrete allocation may occur during materialization. |
| `GPUPromiseResourcePlan` | External provider promises a texture/buffer before encoding. Missing or mismatched fulfillment refuses. |
| `GPUImportedResourcePlan` | Imported handle is accepted only through ownership, usage, lifetime, release, and device-generation validation. |
| `GPUVolatileResourcePlan` | Resource may disappear between frames; every replay must revalidate or rematerialize. |
| `GPUSurfaceTextureLease` | Frame/target scoped lease. Never durable recording identity. |

Deferred resources must expose:

- descriptor preimage;
- owner scope;
- expected usage;
- fulfillment deadline;
- invalidation facts;
- release policy;
- diagnostic reason on failure.

## Target Preparation Context

`GPUFramePreflighter` is the only materialization boundary. Its internal
`GPUTargetPreparationContext` is target-scoped state for late preparation, not
a second coordinator or public API compatibility object.

It coordinates:

- upload tasks;
- destination copies;
- layer/intermediate target allocation;
- atlas mutation and upload;
- filter intermediate allocation;
- text/glyph atlas upload;
- pipeline and module preflight;
- pending reads and writes;
- task dependency tokens.

It does not own material semantics, public Canvas state, codec policy, or
arbitrary CPU raster fallback.

Preflight reserves all pipelines, bind groups, buffers, textures, views,
scratch intervals, destination bindings, optional readback layout, and one
version-scoped `GPUQueueCompletionTicket`. Surface acquisition is its final
ephemeral operation. Success returns one `PreparedGPUFrame` pairing the
immutable semantic frame plan with a one-to-one command-encoder plan and an
opaque prepared resource set. Failure runs every recorded rollback action and
creates no encoder or submission.

## Scratch And Intermediate Lifetime

Scratch and intermediate resources must be governed by explicit tokens:

| Token | Purpose |
|---|---|
| `GPUUseToken` | Orders reads, writes, uploads, copies, and mutations. |
| `GPUPendingReadToken` | Prevents reuse or mutation while a later task still samples or reads a resource. |
| `GPUPendingWriteToken` | Prevents reads before upload, render, compute, or copy completion. |
| `GPUScratchResourceToken` | Names a scratch allocation class and lifetime, not a durable resource handle. |
| `GPUIntermediateResourceToken` | Names a layer/filter/destination intermediate and dependency scope. |

Lifetime classes:

- command-local;
- pass-local;
- layer-local;
- recording-local;
- frame-local;
- shared cache;
- imported external;
- surface lease.

Reuse is legal only when pending reads/writes are satisfied by real queue
completion, usage flags match, format/sample/size constraints match, and
device generation is current. Presentation, target close, or command-buffer
creation never fabricates completion.

## Negative CPU-Fallback Contract

The following are forbidden product routes:

- CPU-rendering a full unsupported draw into a texture for GPU composition;
- CPU-rendering a full saveLayer into a texture for GPU composition;
- CPU-rendering a full filter DAG result into a texture for GPU composition;
- CPU-rendering a full scene or recording into a texture for GPU composition;
- hiding unsupported text/glyph rendering as a precomposed CPU image;
- hiding unsupported path/clip/filter behavior inside a generic
  `FilterIntermediateArtifact`.

Allowed `CPUPreparedGPU` artifacts remain narrow and typed: path masks, glyph
atlases, decoded/uploaded images, prepared geometry, validated filter
intermediates, and other artifact types explicitly registered by their domain
spec.

Every promoted family that touches layers, filters, text, atlases, images, or
paths must include negative tests proving the forbidden routes stay refused.

## Validation Requirements

Promoted routes must provide evidence for:

- analysis decision dumps before resource allocation;
- materialization decision dumps after resource preparation;
- late failure diagnostics for at least one representative resource or
  capability failure in the route family;
- replay compatibility key dumps;
- one-shot refusal behavior when replay is not legal;
- ordered recording dependency tokens when multiple recordings are used;
- scratch/intermediate lifetime and pending-read/write tokens;
- no hidden CPU-rendered compatibility path.
