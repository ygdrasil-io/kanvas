# Layer And Filter Plans

Status: Draft
Date: 2026-06-13

## Purpose

Define the high-level layer, saveLayer, and image-filter planning contracts for
the new GPU renderer.

`GPULayerPlan` captures Canvas-style layer semantics after legacy state has been
normalized. `GPUFilterPlan` captures filter graph execution. `GPUDrawLayer` and
`GPUDrawLayerPlanner` in `02-gpu-recording-task-graph.md` are lower-level
planning structures that consume these semantic plans to build pass partitions.

## Ownership Boundary

The legacy adapter owns capture of stateful save/restore and saveLayer calls.
It must not pass a raw Canvas stack into `:gpu-renderer`.

The GPU renderer owns:

- validating captured layer semantics;
- producing `GPULayerPlan`;
- producing `GPUFilterPlan` when a layer or draw needs filter execution;
- deciding whether offscreen targets are required;
- declaring resource and intermediate requirements;
- reporting stable refusals.

`GPUDrawLayerPlanner` owns lowering accepted plans into low-level draw layers,
pass partitions, and sort windows.

## `GPULayerPlan`

`GPULayerPlan` is an immutable semantic plan for one normalized layer scope.

It includes:

- stable layer ID and parent ID;
- source command provenance;
- layer bounds and conservative device bounds;
- local-to-device transform facts at layer creation;
- clip facts active at layer creation;
- target format, color-space, alpha, and premul facts;
- load, clear, discard, and store intent;
- isolation requirements;
- destination-read and source-read requirements;
- layer alpha and composite blend;
- optional attached `GPUFilterPlan`;
- child draw command IDs;
- restore/composite operation into the parent;
- required resources and intermediate targets;
- stable support or refusal diagnostics.

`GPULayerPlan` is not a draw-pass optimization object. It describes required
semantics. `GPUDrawLayerPlanner` may later choose a direct-to-parent path,
offscreen texture path, or refusal, but it must preserve the plan's semantics.

## Direct-To-Parent Eligibility

A layer may draw directly into its parent only when the plan proves all of the
following:

- no attached filter requires an isolated source image;
- layer alpha and composite are equivalent to direct drawing;
- blend mode does not require a post-layer composite;
- clip and bounds do not require an isolated target;
- destination reads are not changed by eliding the layer;
- child draws do not rely on layer-local clears or prior contents;
- diagnostics can explain the elision.

If any proof is missing, the planner must keep the layer isolated or refuse.

## Offscreen Target Requirements

An isolated layer declares:

- target size and coordinate mapping;
- color format and alpha type;
- usage flags: render attachment, texture binding, storage binding, copy source,
  or copy destination;
- clear/load/store policy;
- sample count or coverage mode;
- parent composite operation;
- lifetime class;
- memory budget class;
- device-generation requirements.

Offscreen targets are ordinary GPU resources unless CPU preparation creates a
typed artifact under `CPUPreparedGPUArtifactRegistry`.

## `GPUFilterPlan`

`GPUFilterPlan` is separate from `MaterialKey`. It represents filter graph
execution, not paint/material identity.

It includes:

- filter graph identity and version;
- node list and dependency edges;
- source input requirements;
- crop and tile-mode facts;
- intermediate bounds;
- coordinate-space rules;
- sample/filtering policy;
- render pipeline references when a node uses render work;
- compute program and pipeline references when a node uses compute work;
- intermediate texture/resource declarations;
- allowed `CPUPreparedGPU` artifact requests;
- stable node-level and graph-level diagnostics.

`GPUFilterPlan` may be attached to a `GPULayerPlan`, a draw command, or a future
filter-specific task. It must not be encoded in `MaterialKey`.

## Filter Execution Routes

A filter node may route through:

- `GPUNative` render pass;
- `GPUNative` compute pass;
- `CPUPreparedGPU` typed artifact when the artifact policy allows it;
- `RefuseDiagnostic`.

`CPUReferenceOnly` may provide oracle behavior for tests, but it is not a
product fallback.

Filter routes must name required intermediate resources, read/write usages,
edge handling, and validation evidence before promotion.

## Layer And Filter Interaction

When a layer has an attached filter:

- the layer source must be isolated unless the filter spec proves direct
  evaluation is equivalent;
- the filter plan owns intermediate graph execution;
- the layer plan owns final composite into the parent;
- destination reads and source reads must be explicit;
- culling cannot remove inputs observed by the filter;
- diagnostics must show both layer and filter reasons.

An unsupported filter must refuse the layer or draw with a stable reason. It
must not silently render an unfiltered layer.

## Culling And Layer Elision

`GPUOcclusionTracker` may prove culling at layer or draw granularity only when
the layer and filter plans allow it.

Culling is forbidden when:

- an attached filter observes the would-be culled content;
- layer composite depends on accumulated alpha or color;
- destination reads are required;
- a later restore/composite needs the offscreen contents;
- the proof crosses target, generation, or parent-scope boundaries.

Layer elision and draw culling require separate diagnostics. A layer can be
eligible for direct-to-parent drawing without being culled.

## Diagnostics

Layer and filter diagnostics must include:

- layer ID, parent ID, bounds, and provenance;
- direct-to-parent, offscreen, or refusal decision;
- target format and usage flags;
- attached filter plan ID when present;
- filter node route decisions;
- intermediate resource declarations;
- culling and elision decisions;
- stable reason codes;
- PM/report counters.

Stable reason-code examples:

- `unsupported.layer.alpha_composite_requires_offscreen`
- `unsupported.layer.destination_read`
- `unsupported.layer.bounds_invalid`
- `unsupported.filter.graph_unregistered`
- `unsupported.filter.node_unimplemented`
- `unsupported.filter.intermediate_unvalidated`
- `unsupported.filter.storage_texture_capability_missing`
- `unsupported.layer_filter.culling_observed_input`

## Validation Requirements

Promoted layer/filter behavior requires:

- normalized layer command fixtures;
- `GPULayerPlan` canonical dumps;
- `GPUFilterPlan` canonical dumps;
- key determinism for intermediate resources and compute/render pipelines;
- WGSL validation for all render or compute modules used by filter nodes;
- CPU reference or explicit refusal evidence;
- GPU evidence for GPU support claims;
- PM-visible route and refusal counters.

Layer elision and culling must have negative tests that prove refusal when
alpha, blend, destination reads, filters, or target boundaries make the
optimization unsafe.

## Non-Goals

- Do not encode layer semantics in `MaterialKey`.
- Do not hide filter DAG execution inside a single draw material.
- Do not silently drop filters or layer alpha when unsupported.
- Do not treat `GPUDrawLayer` as the semantic saveLayer contract.
- Do not use CPU-rendered full-layer textures as an implicit fallback.
