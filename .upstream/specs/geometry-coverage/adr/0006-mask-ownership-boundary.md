# ADR 0006: Keep Mask Ownership Outside Geometry Primitives

Status: Proposed
Date: 2026-05-26

## Context

Glyph masks, path masks, blur/filter masks, and cached coverage masks have
different lifetimes and invalidation keys. Putting atlas references directly
inside geometry primitives would make geometry own cache policy it cannot
validate.

## Decision

Geometry primitives describe the coverage request. Coverage selection resolves
that request to `AlphaMask`, `CoverageAtlas`, or backend-native storage through
an opaque coverage-resource reference.

Text/glyph infrastructure owns glyph discovery and glyph atlas invalidation.
Coverage backends own path/filter mask materialization and frame-local or
persistent coverage cache lifetimes.

## Consequences

Positive:

- `GeometryPrimitive.GlyphMask` does not carry glyph atlas ownership.
- CPU can keep `SkAAClip` or A8 masks behind opaque references.
- WebGPU can upload or atlas masks without changing geometry primitives.

Negative:

- Coverage selection needs an explicit resource lookup/materialization step.
- Dumps must include resource references carefully without exposing unstable
  object identity.

## Non-Goals

- No unified image/glyph/path atlas lifetime policy in the first slice.
- No persistent coverage atlas before profiling justifies it.
