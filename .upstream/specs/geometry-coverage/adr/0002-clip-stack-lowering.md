# ADR 0002: Lower Clip Stack Before GeometryPlan Consumption

Status: Proposed
Date: 2026-05-26

## Context

`SkCanvas` owns clip state. CPU can represent arbitrary AA clips with
`SkAAClip`; WebGPU supports only selected analytic or mask-backed clips today.
If paint sees raw clip operations, coverage semantics can drift per backend.

## Decision

Lower clip stack before `GeometryPlan` is consumed. The result is a
`ClipInteraction` referenced by `GeometryPlan`.

Paint receives only coverage/clip modulation, not raw clip stack operations.

## Consequences

Positive:

- Clip semantics are centralized.
- WebGPU unsupported clips can fail with stable diagnostics.
- CPU can keep native `SkAAClip` without leaking the storage type into paint.

Negative:

- Clip lowering must preserve enough detail for both analytic and mask paths.
- Multi-shape clip composition needs explicit future design.

## Non-Goals

- No silent scissor approximation for arbitrary path clips.
- No Graphite clip stack port.
