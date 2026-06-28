# Multi-Threaded Recording

Status: Draft
Date: 2026-06-28

## Purpose

Define the multi-threaded recording and concurrency model for the GPU-first
renderer. When enabled, command intake, draw analysis, and recording
construction may execute across multiple threads, producing recording fragments
that are merged before task-list preparation.

The architecture kernel (`00-architecture-kernel.md`) declares that `SkCanvas`
and per-draw builders are not thread-safe, but that distinct surfaces may render
in parallel. This spec defines how `:gpu-renderer` exposes thread-safe recording
primitives while preserving correctness, diagnostics, and deterministic output.

This is a target architecture spec, not an implementation slice.

## Source Specs

This spec depends on:
- `00-architecture-kernel.md` for module boundary and concurrency contract;
- `02-gpu-recording-task-graph.md` for `GPURecorder`, `GPURecording`, and task-list construction;
- `04-pipeline-key-cache-resources.md` for `GPUResourceProvider` thread-safety;
- `13-performance-telemetry-cache-gates.md` for concurrency-related telemetry.

## Contracts

| Contract | Purpose |
|---|---|
| `GPURecordingFragment` | Immutable partial recording produced by one thread. Contains a subset of `NormalizedDrawCommand` entries with thread-local analysis decisions. |
| `GPURecordingFragmentMerger` | Merges ordered recording fragments into a single `GPURecording`. Validates ordering tokens, resolves cross-fragment dependencies, and produces merged analysis. |
| `GPUThreadBoundArena` | Thread-local arena for temporary allocations during recording and analysis. Released when the recording fragment is produced. |
| `GPUMergeOrderingToken` | Per-command ordering token that survives fragment boundaries. Consumer in fragment B must not merge before producer in fragment A when a dependency edge exists. |
| `GPUConcurrencyTelemetry` | Telemetry facts: fragment count, thread count, merge time, cross-fragment dependency count, and thread-starvation diagnostics. |
| `GPUConcurrencyDiagnostic` | Reason codes for merge failure, cross-fragment cycle detected, incompatible thread-bound resources, or non-deterministic merge output. |

## Thread Model

### Ownership Rules

1. `GPURecorder` instances are not shared across threads. Each thread creates
   its own `GPURecorder` bound to a `GPURecordingContext`.
2. `GPUResourceProvider` is shared and must be thread-safe. Cached resources
   (pipelines, textures, buffers) are accessed through concurrent-safe maps.
   Mutation of shared resources is serialized through the provider's internal
   lock.
3. `GPUExecutionContext` is thread-safe for read operations (capability queries,
   format checks). Submission is single-threaded through the backend queue.
4. `GPUMaterialDictionary` and `GPURuntimeEffectRegistry` are thread-safe for
   read access. Write access (new material registration, effect registration)
   is serialized.
5. Diagnostics and telemetry collectors are thread-safe with atomic counters.

### Recording Context

```
GPURecordingContext (per thread)
  -> GPURecorder (thread-local)
  -> GPUThreadBoundArena (thread-local)
  -> GPURecordingFragment (immutable output)
```

Each thread records a disjoint subset of draw commands. Disjointness is the
caller's responsibility: a command sequence may be split by draw order index
ranges, layer scopes, or tile assignments.

### Fragment Merge

After all threads produce their fragments, the merger:
1. Verifies ordering tokens: no producer-consumer pair is split across fragments
   without an explicit `GPUMergeOrderingToken`.
2. Concatenates fragments in command-ID order.
3. Re-runs analysis that depends on cross-fragment state (e.g., occlusion
   tracking, layer-scope nesting validation).
4. Produces a single `GPURecording` ready for task-list preparation.

## Determinism Contract

Multi-threaded recording must produce the same `GPURecording` as single-threaded
recording for the same input commands, regardless of thread count or scheduling.

### Determinism Rules

1. `MaterialKey` derivation is deterministic and independent of thread context.
2. `SortKey` assignment is deterministic given the same command set and layer plan.
3. `GPUPayloadGatherer` slot assignment is deterministic per pass.
4. Fragment merge order does not affect final recording content when ordering
   tokens are satisfied.
5. Diagnostics are accumulated in command-ID order after merge.

Non-deterministic behavior (e.g., atlas entry selection due to concurrent
allocation) must be detected and reported as `GPUConcurrencyDiagnostic`, not
silently accepted.

## Cache And Resource Thread Safety

### Pipeline Cache

`WgslPipelineCache` is shared with concurrent-read / exclusive-write semantics.
Pipeline creation is serialized through the cache. A thread that misses the
cache queues pipeline creation; other threads that need the same pipeline block
until creation completes.

### Texture And Buffer Cache

`GPUResourceProvider` texture and buffer caches use concurrent LRU maps.
Eviction is serialized. Upload staging buffers are thread-local.

### Atlas Thread Safety

Atlas allocation (path, coverage, text, glyph) is serialized. Concurrent
recording may produce atlas entry requests that are resolved during merge
or task-list preparation, not during per-thread recording.

## Parallel Recording Strategies

### Strategy: Disjoint Command Ranges

```
thread 1: commands[0..N/2]
thread 2: commands[N/2..N]
```

Simplest strategy. Requires no cross-thread ordering tokens. Used when paint
order is the only dependency and no clip/stencil/destination-read ordering
spans the split point.

Refusal: split cannot occur inside an atomic group, layer scope, or
destination-read dependency chain.

### Strategy: Layer-Scope Partitioning

```
thread 1: commands in layer scope A
thread 2: commands in layer scope B (independent of A)
```

When saveLayer scopes are provably independent (no shared destination read,
no stencil interaction), each scope can record in parallel.

### Strategy: Tile-Parallel Recording

Uses `38-tile-deferred-rendering.md` tile grid. Each thread records one tile's
bin. Threads share the same target but operate on disjoint scissor regions.
Ordering tokens are tile-local by construction.

## Acceptance Gates

- At least one recording split into two fragments with disjoint command ranges,
  merged correctly, producing identical output to single-threaded recording.
- Cross-thread dependency detection: producer in fragment 1, consumer in
  fragment 2 correctly blocked until merge.
- Recording fragment merge is deterministic: repeated runs produce identical
  `GPURecording`.
- Pipeline cache hit rate is not degraded by concurrent misses (telemetry).
- Thread-bound arena memory is released after fragment production, not leaked.
- Stable refusal for split inside atomic group, layer scope, or destination-read
  chain when splitting is unsafe.

## Non-Goals

- Do not implement a general work-stealing executor.
- Do not make `GPUExecutionContext.submit()` multi-threaded (submission remains
  single-threaded).
- Do not remove or weaken single-threaded recording (it remains the default and
  fallback).
- Do not claim parallel execution speedup without measured benchmarks.
- Do not expose thread management as a product API.
