# Draw Packet And Command Stream Materialization

Status: Draft
Date: 2026-06-16

## Purpose

Define the bridge between the `GPUDrawLayerPlanner` output and concrete
`GPUExecutionContext.submit()` work.

The gap this closes is not a missing Graphite class name. It is the executable
stream boundary: accepted draw invocations must become stable, materialized draw
packets, then pass commands that can be encoded against the Kanvas `GPU` facade
used with `wgpu4k`.

This spec owns that bridge for `:gpu-renderer`. It keeps Graphite and Dawn as
algorithm references only; it does not port Graphite `DrawList`, `DrawPass`,
`Renderer`, `RenderStep`, Ganesh, Dawn C++ classes, or SkSL machinery.

## Reference Model

Graphite records one sort key per render-step/draw pair, prepares pipelines,
uniform data, texture bindings, vertex data, and then emits a command stream
that minimizes state changes while preserving stencil, destination-read, clip,
and paint-order constraints.

Graphite's Dawn backend then maps that stream to WebGPU-style operations:
render pass setup, pipeline binding, bind-group binding, buffer binding,
scissor, direct/indexed/instanced draws, copies, barriers, and readback-related
work.

Kanvas adopts these responsibilities with Kanvas-owned records:

```text
GPUDrawAnalysis
  -> GPUDrawInvocation
  -> GPUDrawLayerPlanner / SortKey
  -> GPUDrawPacketStream
  -> GPUResourceProvider materialization
  -> GPUPassCommandStream
  -> GPUCommandEncoderPlan
  -> GPUCommandSubmission
```

The stream is a renderer contract, not a new public graphics API.

## Ownership Boundary

`passes` owns:

- `GPUDrawPacket`;
- `GPUDrawPacketStream`;
- `GPUPassCommand`;
- `GPUPassCommandStream`;
- packet ordering, batching eligibility, and diagnostic preimages.

`resources` owns:

- resolving packet references to pipelines, buffers, bind groups, textures,
  samplers, target leases, and upload/readback resources;
- late refusal when concrete resources cannot be materialized.

`execution` owns:

- `GPUCommandEncoderPlan`;
- facade command encoding;
- queue submission and completion diagnostics.

No package outside `execution` may call command-submission APIs on the `GPU`
facade. No package outside `resources` may treat concrete GPU handles as durable
resource identity.

## `GPUDrawPacket`

`GPUDrawPacket` is the materialized draw-unit record produced from one
`GPUDrawInvocation` after payload slots, pipeline key preimages, binding-layout
facts, and resource-binding slots are known.

One normalized command may produce multiple packets. A stencil-cover path, clip
producer/consumer sequence, text subrun split, layer composite, or filter node
may produce separate packets linked by ordering tokens.

Each packet records:

- packet ID scoped to the pass materialization product;
- original command ID and analysis record ID;
- layer ID, binding-list ID, insertion token, and sort-key hash/preimage;
- render-step identity and version;
- packet role: shading, depth-only, stencil-producer, stencil-consumer,
  clip-producer, clear, discard, copy, upload, compute, composite, or readback;
- `GPURenderPipelineKey` or `GPUComputePipelineKey` when applicable;
- `WGSLBindingLayout` group identity;
- `GPUUniformPayloadSlot` and `GPUResourceBindingSlot` references;
- vertex, instance, index, or indirect argument source descriptors;
- scissor, primitive clip, stencil, depth, blend, sample, and target-state
  facts that affect command encoding;
- `GPUDestinationReadToken`, `GPUClipOrderingToken`, `GPUAtlasMutationPlan`,
  `GPUTextOrderingToken`, `GPULayerOrderingToken`, or `GPUFilterOrderingToken`
  when those domains affect legal movement or submission order;
- original paint order and any dependency edge that prevents reordering;
- resource-generation assumptions required for materialization;
- stable diagnostic provenance.

Packet IDs, payload slots, binding slots, and transient buffer offsets are scoped
to one pass or recording product. They are not durable keys and must not leak
into `MaterialKey`, `GPURenderPipelineKey`, `GPUComputePipelineKey`, or reusable
recording compatibility keys.

## Packet Stream Rules

`GPUDrawPacketStream` is an ordered, dumpable stream of packets for one pass or
pass-like task. It is the first artifact that can answer both questions:

- what work will execute;
- which grouping and state-change opportunities are legal.

Rules:

- The stream is created only after `GPUDrawLayerPlanner` has produced insertion,
  sort-window, atomic-group, and merge decisions.
- The stream may preserve original order even when all packet fields are present.
  First-slice rect/rrect work should start order-preserving.
- Sorting is allowed only inside the legal sort window named by
  `15-draw-layer-planner-and-sort-policy.md`.
- A packet cannot move across destination-read, active target, clip/stencil,
  upload, atlas mutation, layer, filter, text upload, or unknown-overlap
  boundaries unless the producing spec gives an explicit proof.
- Packet grouping can reduce pipeline, bind-group, buffer, scissor, or texture
  changes only when it is observably correctness-neutral.
- Cache hit/miss state, raw GPU handles, concrete texture contents, and object
  addresses are forbidden packet ordering axes.
- A refused or culled command must remain visible through packet-stream
  diagnostics even when it emits no executable packet.

The stream is valid only for the context, target, device generation, and
materialization assumptions that created it.

## Resource Materialization Handoff

The packet stream still references logical descriptors and scoped slots. Before
execution, `GPUResourceProvider` must resolve or refuse:

- render pipelines from `GPURenderPipelineKey`;
- compute pipelines from `GPUComputePipelineKey`;
- WGSL shader modules and pipeline layouts;
- uniform, storage, vertex, instance, index, and indirect buffers;
- bind groups for each `WGSLBindingLayout` plus payload/resource binding slot;
- sampled textures, texture views, storage textures, and samplers;
- surface texture leases and offscreen/layer/intermediate targets;
- destination-copy snapshots and readback resources;
- atlas, clip mask, glyph atlas, image upload, filter, and layer resources.

The materialization product must expose stable references that command streams
can consume without exposing raw backend object addresses in public dumps.

Late materialization failure must not silently change route kind. It must return
one of:

- rebuild within the same route and device generation;
- split/retry when an owning spec explicitly allows it;
- deterministic refusal with stable diagnostics;
- skipped evidence for readback-only lanes when support evidence is not being
  claimed.

It must not CPU-render an unsupported draw, layer, filter, or scene into a
texture for GPU composition.

## `GPUPassCommandStream`

`GPUPassCommandStream` is the backend-near stream emitted after materialization.
It is still Kanvas-owned and dumpable; it is not a raw Dawn/WebGPU command buffer.

Command classes:

- begin render pass with target, load/store, clear/discard, depth/stencil, and
  resolve facts;
- end render pass;
- set render pipeline;
- set compute pipeline;
- set bind group with dynamic offsets when accepted;
- set vertex, instance, index, storage, or indirect argument buffer;
- set scissor and viewport when accepted by the target/facade;
- set blend constants when required by `GPUBlendPlan`;
- draw, draw indexed, draw instanced, draw indexed instanced, and accepted
  indirect variants;
- dispatch compute and accepted indirect dispatch variants;
- copy buffer/texture operations for uploads, destination snapshots,
  intermediates, and readback;
- barrier/pass-boundary markers used for diagnostics and validation.

The command stream may elide redundant state-setting commands only when the
previous command state is known inside the same render or compute pass and no
barrier invalidates the state. Elision is an optimization; correctness must hold
if every packet emits a full state setup.

## WGPU/Dawn-Aligned Encoding Contract

`GPUCommandEncoderPlan` maps `GPUPassCommandStream` to the selected `GPU` facade
implementation.

The plan records:

- context implementation identity and device/queue generation;
- target and surface lease generation;
- command encoder scope: render, compute, copy/upload, readback, or barrier;
- pass command count and packet count;
- bound pipeline, bind group, buffer, texture, sampler, and target generations;
- unsupported facade operations before they reach queue submission;
- queue-submission result and asynchronous completion status when available.

Encoding rules:

- Render commands are encoded only inside a render pass scope.
- Compute commands are encoded only inside a compute pass scope.
- Copy/upload/readback commands are encoded only inside copy or submission
  scopes.
- Sampling a texture while it is active as the same attachment is refused unless
  a destination-copy, existing-intermediate, or layer-isolation strategy has
  already materialized a separate resource.
- Missing usage flags, stale generations, incompatible bind-group layouts, or
  missing resource bindings refuse before queue submission.
- The encoder must not create shader modules, pipelines, bind group layouts, or
  buffers opportunistically in a way that bypasses `GPUResourceProvider`
  accounting.

This keeps the existing WGPU helper useful as smoke infrastructure while making
the production path cache, resource, and submission aware.

## First Slice Requirements

For the first solid rect route:

- emit one shading `GPUDrawPacket` per accepted rect invocation;
- preserve input order unless adjacent-compatible batching is explicitly proven;
- use a real `GPURenderPipelineKey`, not a placeholder label;
- reference a validated `WGSLBindingLayout` and `WGSLPackingPlan`;
- reference `GPUUniformPayloadSlot` for solid color and rect/transform payload;
- materialize a render pipeline and bind group through `GPUResourceProvider` or
  refuse with a stable diagnostic;
- emit a `GPUPassCommandStream` with begin pass, set pipeline, set bind group,
  optional scissor, draw, and end pass commands;
- produce `GPUCommandSubmission` evidence or an explicit skipped GPU lane reason.

For the first rrect route, the same contract applies, with an additional
render-step identity, rrect payload, and analytic coverage WGSL/reflection facts.

## Diagnostics

Packet and command-stream diagnostics must include:

- command ID, packet ID, pass ID, and target generation;
- source `GPUDrawInvocation` and insertion token;
- packet role and render-step identity;
- sort-key preimage and hash;
- pipeline key preimage and hash;
- payload slot and resource binding slot IDs;
- materialized resource generation labels;
- state-elision decisions;
- command class and facade operation class;
- queue submission status when available;
- refusal stage: packet build, materialization, command stream, encoding,
  submission, completion, or readback.

Stable reason-code examples:

- `invalid.packet.missing_pipeline_key`
- `invalid.packet.missing_payload_slot`
- `invalid.packet.sort_window_crossed`
- `invalid.packet.atomic_group_interleaved`
- `invalid.command_stream.missing_materialized_resource`
- `invalid.command_stream.incompatible_bind_group_layout`
- `unsupported.command_stream.indirect_draw_unavailable`
- `unsupported.command_stream.indirect_dispatch_unavailable`
- `unsupported.command_stream.active_attachment_sampled`
- `stale.command_stream.resource_generation`
- `stale.command_stream.surface_lease_generation`
- `unsupported.execution.facade_operation_unavailable`
- `unsupported.execution.queue_submission_failed`

Promoted diagnostics must normalize to the taxonomy in
`32-target-authority-taxonomy-diagnostics.md` before support is claimed.

## Validation Requirements

Promoted packet and command-stream behavior requires:

- canonical packet-stream dumps;
- canonical pass-command-stream dumps;
- order-preserving first-slice fixture;
- adjacent compatible batching fixture and incompatible non-batching fixture;
- no-sort-across-destination-read fixture before destination-read routes promote;
- no-sort-across-stencil/clip atomic group fixture before those routes promote;
- payload value changes do not change pipeline key fixture;
- resource generation stale refusal fixture;
- active-attachment sampling refusal fixture;
- command-stream-to-submission fixture proving the expected facade operation
  classes are emitted or explicitly skipped;
- cache telemetry showing whether pipeline/module/resource creation happened
  during materialization or steady-state execution;
- PM evidence that distinguishes packet built, resources materialized, commands
  encoded, queue submitted, completion observed, readback completed, skipped, and
  refused states.

## Non-Goals

- Do not copy Graphite's sort-key bit layout or Dawn command-buffer classes.
- Do not make packet order a product API.
- Do not allow packet streams to carry concrete GPU object addresses.
- Do not infer resource materialization success from the existence of a packet.
- Do not claim GPU support from a command stream that was never encoded,
  submitted, or explicitly skipped with stable evidence.
- Do not use command-stream construction as hidden CPU fallback.
