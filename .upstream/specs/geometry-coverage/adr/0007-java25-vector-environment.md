# ADR 0007: Use Java 25 Vector API With Scalar Fallbacks

Status: Proposed
Date: 2026-05-26

## Context

The CPU pipeline wants SIMD where it helps row-local pixel work. The repository
already configures `:render-pipeline` Java compilation and tests with
`--add-modules jdk.incubator.vector`.

## Decision

Use the Java 25 Vector API for CPU SIMD experiments and production candidates.
Every vector path must keep a scalar reference loop and deterministic fallback.

Required environment:

- compile/test/runtime add `jdk.incubator.vector`;
- benchmarks report selected vector species and fallback count;
- CI or local validation can still run scalar correctness when vector support
  is unavailable.

## Consequences

Positive:

- No native SIMD dependency or custom intrinsic layer.
- Vector pilots stay colocated with `:render-pipeline` CPU execution work.
- Scalar and vector outputs can be compared directly.

Negative:

- The API is still incubating and may require build flag maintenance.
- Some environments may run scalar fallback only.

## Non-Goals

- No vectorization of path topology, contour classification, or winding
  decisions until profiling proves those scalar algorithms are the bottleneck.
