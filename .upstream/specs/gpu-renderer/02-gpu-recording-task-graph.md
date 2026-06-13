# GPU Recording And Task Graph

Status: Draft
Date: 2026-06-13

## Purpose

Define the Graphite-inspired recording and task concepts for the new GPU
renderer. The design is inline on the Kanvas `GPU` facade and does not preserve
Graphite's backend plugin architecture.

## Responsibilities

`GPURecorder` owns command intake and recording construction.

`GPURecording` owns the immutable result of recording.

`GPUTaskList` owns ordered GPU work and resource dependencies.

`GPUDrawPass` owns immutable draw-pass data close to GPU submission.

`GPURenderStep` owns the geometry/coverage technique used by a draw inside a
pass.

## `GPURecorder`

`GPURecorder` accepts normalized draw commands and a target configuration. It
does not accept `SkCanvas` operations.

It is responsible for:

- validating command invariants;
- selecting candidate routes;
- deriving or requesting `MaterialKey` values;
- selecting `GPURenderStep` candidates;
- assigning recording-local command IDs;
- creating tasks;
- collecting deterministic diagnostics.

It must not:

- mutate legacy Canvas state;
- compile arbitrary SkSL;
- submit work directly to a surface;
- silently render unsupported work through CPU fallback.

## `GPURecording`

`GPURecording` is the immutable product of a recorder snap.

It contains:

- target facts;
- task list;
- material and pipeline key references;
- required resource declarations;
- route diagnostics;
- feature and capability assumptions.

A `GPURecording` may be replayed only when its resource and capability
assumptions are still valid. If replay safety cannot be proven, the recording
must be treated as one-shot.

## `GPUTaskList`

`GPUTaskList` is an ordered collection of tasks. A task may prepare resources,
encode commands, or represent a synchronization boundary.

Task phases:

1. `prepareResources`: allocate or resolve pipelines, buffers, textures,
   samplers, atlases, bind groups, and upload payloads.
2. `addCommands`: encode commands through the `GPU` facade.

The split exists so route selection, resource failure, and command encoding
failures are reported separately.

Task outcomes:

- `Success`: task remains valid and may be replayable under its assumptions.
- `Discard`: task was consumed or optimized away.
- `Fail`: recording is invalid and must report a stable diagnostic.

## `GPUDrawPass`

`GPUDrawPass` is immutable after creation.

It contains:

- target attachment facts;
- load/store operations;
- clear/discard intent;
- draw commands expanded into render-step invocations;
- pipeline keys;
- dynamic uniform and resource binding references;
- pass-level barriers and diagnostics.

`GPUDrawPass` is allowed to sort, cull, and merge draw-step invocations when
ordering facts prove correctness.

## `GPURenderStep`

`GPURenderStep` describes the fixed geometry and coverage contribution for a
draw invocation.

It owns:

- vertex/instance input requirements;
- primitive topology;
- depth/stencil requirements;
- coverage behavior;
- WGSL geometry/coverage fragment contribution;
- fixed state contribution to `PipelineKey`;
- supported geometry/material compatibility checks.

A command may produce multiple render-step invocations. For example, a filled
stroke or a coverage technique with an inner fill may need more than one step.

## Ordering And Barriers

The task graph must preserve:

- paint-order dependencies for blending;
- clip or stencil preparation dependencies;
- destination-read dependencies;
- atlas mutation and upload dependencies;
- target load/store correctness.

Optimization is allowed only after those dependencies are explicit.

## Resource Preparation

Resource preparation uses `GPUResourceProvider`. It must produce deterministic
diagnostics for:

- unsupported capabilities;
- pipeline creation failure;
- WGSL validation or reflection failure;
- resource allocation failure;
- atlas capacity failure;
- texture format mismatch;
- device loss or invalid generation.

## Non-Goals

- Do not model all Graphite task classes.
- Do not add a backend abstraction beyond the existing `GPU` facade.
- Do not expose `GPUDrawPass` as a public Skia-like API.
- Do not assume recordings are reusable across devices or capability changes.
- Do not implement broad render-graph scheduling in this kernel.
