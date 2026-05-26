# ADR 0001: Keep CoveragePlan Between Geometry And Paint

Status: Proposed
Date: 2026-05-26

## Context

Geometry and paint are currently easy to couple because backend draw methods
often choose shape execution and color execution together. The target requires
one semantic pipeline that CPU and WebGPU can both consume.

## Decision

Introduce `CoveragePlan` as the boundary between geometry lowering and paint
lowering.

Geometry produces `GeometryPlan` and `CoveragePlan`. Paint consumes
`CoveragePlan` as coverage/clip modulation input. Paint does not inspect raw
path, stroke, or clip-stack state.

## Consequences

Positive:

- CPU and WebGPU can use different low-level coverage storage while sharing
  semantics.
- Unsupported coverage can be diagnosed before paint specialization.
- Backend strategy selection becomes reviewable.

Negative:

- Initial descriptor work adds ceremony before pixels change.
- Existing backend-local shortcuts must be mapped carefully during migration.

## Non-Goals

- No Graphite port.
- No SkSL or Graphite paint-key machinery.
- No universal coverage buffer.
