# ADR 0006: WebGPU Device Thread Ownership

Status: Accepted

## Context

Generated WGSL work introduces shader module, pipeline, resource caches, and
telemetry counters. WebGPU implementations often have strict device ownership
expectations, and unspecific concurrency rules make cache telemetry hard to
audit.

## Decision

Treat `SkWebGpuDevice`, WebGPU handles, generated shader/pipeline caches, and
telemetry counter mutation as owned by one render/device thread.

Background work may prepare immutable descriptors, source strings, and
reflection reports. It must hand those values to the owner thread before
creating WebGPU handles or mutating caches.

## Consequences

- Cache counters can be snapshot-based without atomics for the initial model.
- Tests can reason about deterministic cache hit/miss order.
- Any future shared compilation or cross-thread cache mutation requires a new
  ADR, synchronization policy, and stress tests.
