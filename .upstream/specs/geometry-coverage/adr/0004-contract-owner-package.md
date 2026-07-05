# ADR 0004: Own Geometry/Coverage Contracts In Render Pipeline

Status: Proposed
Date: 2026-05-26

## Context

`render-pipeline` already owns `KanvasPipelineIR`, `CoverageModel`,
`FallbackPlan`, value records, and Java 25 CPU pipeline work. Geometry and
coverage contracts need one backend-neutral owner without depending on
`:kanvas` implementation classes or WebGPU.

## Decision

Own `GeometryPlan`, `CoveragePlan`, diagnostic reason records, and their
lowering adapter in `:render-pipeline`.

Use render-pipeline value records or new dependency-free equivalents in the
contract layer. `:kanvas` adapts public Skia-like types such as `SkRect`,
`SkRRect`, `SkPathFillType`, and `SkClipShape` at the module boundary instead
of leaking them into the shared contracts.

## Consequences

Positive:

- CPU, WebGPU, and future utilities share the same contracts.
- `render-pipeline` stays independent of WebGPU and Java Vector runtime state.
- `ClipInteraction.AnalyticShape` can use a dependency-free shape descriptor.

Negative:

- Adapters must translate between Skia-like public types and contract value
  records.
- Some existing docs/tests that mention Skia value types need explicit
  boundary language.

## Non-Goals

- No new dependency from the backend-neutral contract layer to `:kanvas`.
- No move of the public drawing API out of `:kanvas`.
