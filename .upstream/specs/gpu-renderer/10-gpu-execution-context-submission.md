# GPU Execution Context And Submission

Status: Draft
Date: 2026-06-13

## Purpose

Define the GPU execution, surface, command encoding, submission, readback, and
device-generation contracts for the new GPU renderer.

The renderer is inline on the Kanvas `GPU` facade used with `wgpu4k`. This spec
does not introduce a backend plugin layer. It defines the renderer-owned
execution boundary that prevents task, resource, and evidence code from
depending on ad hoc surface or queue behavior.

## Ownership Boundary

The `:gpu-renderer` core owns:

- execution-context contracts used by `GPUTaskList`;
- target and surface attachment facts used by `GPUDrawPass`;
- command encoding scopes for render, compute, copy, upload, and readback work;
- submission diagnostics;
- device-generation validation;
- readback evidence requests used by conformance and PM reporting.

The core does not own:

- native window creation;
- browser canvas setup;
- Kadre event loops;
- Dawn-specific or pure-Kotlin backend implementations;
- direct Skia-like surface APIs.

Windowing, presentation, and platform setup stay outside `:gpu-renderer`. They
provide a configured `GPUExecutionContext` and target descriptors to the core.

## `GPUExecutionContext`

`GPUExecutionContext` is the renderer-facing execution handle for one selected
`GPU` facade implementation and device generation.

It records:

- implementation identity: browser, native `wgpu4k`, Dawn bridge, or pure
  Kotlin facade implementation when available;
- device generation;
- queue generation;
- `GPUCapabilities` snapshot;
- supported command classes: render, compute, copy, upload, readback, timestamp
  queries, and presentation;
- surface target availability;
- diagnostic labels and PM-visible adapter facts.

The context must be immutable for a recording snap. If the underlying device or
queue changes, a new context generation is required and stale resources must be
rebuilt, discarded, or refused.

## Shared And Recorder-Local Scopes

The renderer uses explicit resource scopes:

| Scope | Purpose |
|---|---|
| `GPUSharedScope` | Device-generation caches such as pipeline, module, sampler, and stable layout caches. |
| `GPURecorderScope` | Recording-local transient allocations, upload staging, command scratch, and one-shot task state. |
| `GPUFrameScope` | Frame-local targets, intermediate textures, readback requests, and telemetry counters. |
| `GPUAtlasScope` | Atlas-resident artifacts with generation, budget, and invalidation policy. |

Shared state may be reused only when keys, capabilities, device generation, and
lifetime facts still match. Recorder-local and frame-local resources must not
escape into reusable caches unless a spec names the promotion rule.
Path and coverage atlas scope rules, use tokens, retry/split behavior, and
mutation diagnostics are defined in `19-path-coverage-atlas-strategy.md`.
Destination-read target snapshots, existing intermediate validation,
copy-before-sample ordering, and pass-split actions are defined in
`20-destination-read-strategy.md`.

This mirrors Graphite's separation of shared context, recorder-owned resource
provider state, scratch resources, and atlas managers conceptually, but Kanvas
does not inherit Graphite classes or backend ownership.

## Target And Surface Facts

`GPUSurfaceTarget` describes the current render target without exposing
platform surface objects to planning code.

It includes:

- logical target ID and generation;
- dimensions and device-pixel scale;
- color format, alpha type, premul convention, and color-space tag;
- usage flags: render attachment, texture binding, storage binding, copy
  source, copy destination, and presentation;
- sample count or coverage mode;
- load, clear, discard, store, and present intent;
- readback availability and limitations;
- resize and device-generation facts.

Swapchain, browser canvas, imported texture, offscreen texture, and layer
intermediate targets are represented by the same target descriptor shape. A
target descriptor is not a resource handle and must be safe to dump.

When command encoding needs the current surface or swapchain texture, the
execution layer creates a `GPUSurfaceTextureLease`. The lease is scoped to the
frame and target generation, records available usage flags, and cannot be
stored in material keys, pipeline keys, reusable recordings, or shared caches.
Sampling, copying, rendering to, or reading back from a leased surface texture
requires the corresponding usage flag and a non-stale lease.

## Command Encoding Scopes

`GPUTaskList.addCommands` encodes work through explicit scopes:

- render pass scope;
- compute pass scope;
- copy/upload scope;
- readback scope;
- submission barrier scope.

A task must declare the scope it needs before encoding. Encoding a render draw
inside a compute scope, sampling a target while it is active as an attachment,
or copying from a texture without the required usage flags must refuse with a
stable diagnostic before submitting commands.

Command scopes must preserve:

- resource state and usage compatibility;
- target load/store correctness;
- render/compute/copy ordering;
- upload-before-use dependencies;
- readback-after-write dependencies;
- destination-copy-before-sample dependencies;
- clip stencil producer-before-consumer dependencies;
- clip mask upload-before-sample and compute-write-before-sample dependencies;
- atlas mutation ordering;
- atlas compute-write-before-sample and upload-before-sample dependencies;
- text atlas upload-before-sample dependencies;
- text instance-buffer upload-before-draw dependencies;
- text atlas generation and eviction/compaction ordering;
- device-generation checks.

## Submission Model

`GPUCommandSubmission` is the immutable record of encoded command work submitted
to the facade.

It includes:

- submission ID;
- context and device generation;
- target generation;
- task IDs and pass IDs;
- command scope list;
- resource usage summary;
- submitted route counts;
- explicit barriers or pass boundaries;
- readback requests;
- timestamp/query requests when enabled;
- stable diagnostics.

Submission may fail before encoding, during resource preparation, during command
encoding, at queue submission, or during asynchronous completion. Each class is
reported separately.

## Readback And Evidence Requests

`GPUReadbackRequest` exists for tests, PM evidence, diagnostics, and migration
comparison. It is not a production fallback route.

Product destination reads use `GPUDestinationReadPlan`,
`GPUDestinationCopyPlan`, `GPUDestinationReadBinding`, and GPU copy or
intermediate resources from `20-destination-read-strategy.md`. They are
separate from CPU-facing readback requests.

It must record:

- target, texture, or buffer source identity;
- bounds and pixel format requested;
- color-space and premul interpretation;
- synchronization point;
- expected checksum, diff, or artifact path when used by a test harness;
- failure reason when readback is unavailable.

Readback support can be capability-gated. A skipped or unavailable readback
lane must be reported as skipped or risk evidence, not as GPU support.

## Device Loss And Generation Policy

Device loss invalidates all GPU handles owned by the old generation:

- pipelines;
- shader modules;
- bind group layouts and bind groups;
- buffers;
- textures;
- samplers;
- atlases;
- staging resources;
- command encoders and pending submissions.

Logical descriptors may survive when they do not own GPU handles:

- normalized commands;
- analysis records;
- material and pipeline key preimages;
- WGSL source and reflection descriptors;
- CPU-side artifact descriptors;
- CPU reference oracle inputs.

On device loss, the renderer must either rebuild resources under a new
generation, discard one-shot recordings, or return a deterministic refusal. It
must not reuse stale GPU handles silently.

## Diagnostics

Execution diagnostics must include:

- context implementation identity;
- device and queue generation;
- target generation and usage flags;
- surface texture lease generation and usage flags when present;
- command scope list;
- task and pass IDs;
- submitted route counts;
- resource usage summary;
- readback availability;
- queue submission result;
- device-loss or stale-generation reason.

Stable reason-code examples:

- `unsupported.execution.compute_unavailable`
- `unsupported.execution.copy_unavailable`
- `unsupported.execution.readback_unavailable`
- `unsupported.execution.surface_unavailable`
- `unsupported.execution.usage_missing`
- `unsupported.execution.active_attachment_sampled`
- `unsupported.destination_read.copy_unavailable`
- `unsupported.destination_read.pass_split_illegal`
- `unsupported.clip.stencil_unavailable`
- `unsupported.clip.mask_upload_unavailable`
- `unsupported.clip.stencil_ordering_illegal`
- `unsupported.atlas.sync_unavailable`
- `unsupported.atlas.storage_texture_unavailable`
- `unsupported.text.upload_plan_missing`
- `unsupported.text.atlas_generation_stale`
- `unsupported.text.instance_buffer_upload_failed`
- `unsupported.texture.surface_lease_stale`
- `unsupported.execution.device_generation_stale`
- `unsupported.execution.queue_submission_failed`
- `unsupported.execution.device_lost`

## Validation Requirements

Promoted execution behavior requires:

- deterministic context and target dumps;
- usage-flag validation tests;
- stale device-generation tests;
- render/compute/copy ordering tests where supported;
- destination-copy-before-sample and pass-split ordering tests before shader
  destination-read routes are promoted;
- image upload-before-sample ordering tests for still images, selected
  animated frames, mip generation, and prepared CPU pixel sources before image
  routes from `22-image-bitmap-codec-pipeline.md` are promoted;
- atlas upload-before-sample and compute-write-before-sample ordering tests
  before path/coverage atlas routes are promoted;
- clip stencil producer-before-consumer, clip mask upload-before-sample,
  clip mask compute-write-before-sample, clip shader mask production, and clip
  atomic group ordering tests before routes from
  `24-clip-stencil-mask-pipeline.md` are promoted;
- text atlas upload-before-sample, text instance-buffer upload-before-draw, and
  atlas generation ordering tests before text/glyph routes are promoted;
- filter render/compute/copy ordering tests for `GPUFilterNodePlan`,
  `GPUFilterIntermediatePlan`, `GPUFilterOrderingToken`,
  destination/backdrop reads, read/write aliasing, and runtime-effect bindings
  before filter routes from `23-filter-effect-pipeline.md` are promoted;
- readback success or skipped-lane diagnostics;
- device-loss refusal or rebuild tests for touched resources;
- PM evidence that distinguishes encoded, submitted, completed, skipped, and
  failed work.

## Non-Goals

- Do not add a backend plugin model.
- Do not expose Dawn, browser, or native-window objects as core contracts.
- Do not treat readback as production rendering.
- Do not hide device loss by falling back to CPU rendering.
- Do not claim presentation support from offscreen submission alone.
