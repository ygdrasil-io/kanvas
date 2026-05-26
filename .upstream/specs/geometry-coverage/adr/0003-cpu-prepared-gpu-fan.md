# ADR 0003: CPU-Prepared Convex Fan Before Compute Tessellation

Status: Proposed
Date: 2026-05-26

## Context

The current WebGPU backend prepares path vertices on the CPU and draws them via
WebGPU. Compute tessellation would be a larger architecture shift and has not
yet been justified by profiling.

## Decision

Keep the convex fan strategy CPU-prepared for the first Geometry/Coverage
implementation phase. WebGPU receives triangle lists or stencil-cover inputs
prepared by host-side geometry lowering.

Compute tessellation remains out of scope until profiling shows CPU-side
geometry preparation is a real bottleneck.

## Consequences

Positive:

- Preserves current working WebGPU model.
- Keeps the first spec/implementation milestones measurable.
- Avoids importing Graphite/Vello-style compute architecture prematurely.

Negative:

- Host-side tessellation may become a bottleneck in path-heavy scenes.
- A future compute path will need a separate ADR and validation suite.

## Trigger For Revisit

Revisit only when benchmark evidence shows CPU geometry preparation dominates
path-heavy frames or prevents an accepted PM/user workflow from meeting frame
budget.
