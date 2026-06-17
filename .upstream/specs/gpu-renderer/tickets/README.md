# GPU Renderer Ticket Catalog

This catalog is the markdown source of truth for `:gpu-renderer` roadmap
tickets. It follows `../36-implementation-roadmap.md`, the route taxonomy in
`../32-target-authority-taxonomy-diagnostics.md`, and the draw-family target
matrix in `../09-draw-family-support-matrix.md`.

Tickets are grouped by milestone for human navigation. Each ticket includes a
French PM note, target spec sources, Graphite algorithm references for agent
implementation context, a Kotlin-like design sketch when useful, acceptance
criteria, required evidence, validation commands, fallback/refusal behavior,
status metadata, dashboard impact, and optional Linear labels.

Existing R0-R6 implementation evidence is intentionally represented as
`review`, not `done`. The catalog must not assert product support or route
activation until an independent review links accepted evidence and an explicit
activation decision.

## Ticket Section Order

Tickets use this body order:

1. `PM Note`
2. `Problem`
3. `Scope`
4. `Non-Goals`
5. `Spec Sources`
6. `Graphite Algorithm References`
7. `Design Sketch`
8. `Acceptance Criteria`
9. `Required Evidence`
10. `Fallback / Refusal Behavior`
11. `Dashboard Impact`
12. `Validation`
13. `Status Notes`
14. `Linear Labels`

Graphite references are pinned in
[GRAPHITE-ALGORITHM-REFERENCES.md](GRAPHITE-ALGORITHM-REFERENCES.md). They are
algorithm references only; they do not authorize porting Graphite or Ganesh,
changing the WebGPU backend, or replacing Kanvas WGSL/Kotlin acceptance
criteria.

## Status Model

| Status | Meaning |
|---|---|
| `proposed` | Written but not yet accepted for execution. |
| `ready` | Accepted and ready to implement. |
| `in-progress` | Currently being implemented. |
| `blocked` | Cannot progress until a named blocker is resolved. |
| `review` | Evidence or implementation exists and requires independent review before it can be treated as accepted. |
| `done` | Completed with accepted review, linked evidence, and no pending claim ambiguity. |
| `superseded` | Replaced by another ticket. |
| `deferred` | Intentionally postponed. |

## Claim Impact Model

`claim_impact` uses the `:gpu-renderer` target vocabulary:

| Claim Impact | Meaning |
|---|---|
| `ImplementationCandidate` | Coherent implementation or evidence exists, but support has not been promoted. |
| `PromotedSupported` | Product support has evidence, diagnostics, and gates accepted for the selected route. |
| `TargetNative` | Intended to become a `GPUNative` route after evidence. |
| `TargetPrepared` | Intended to use typed `CPUPreparedGPU` artifacts after evidence. |
| `DependencyGated` | Blocked on another accepted spec, dependency, or subsystem. |
| `PolicyGated` | Blocked on an explicit product or architecture decision. |
| `ReferenceOnly` | CPU oracle or evidence behavior only, not product rendering. |
| `RefuseRequired` | Stable refusal is the target behavior unless a future accepted spec changes the route. |
| `FutureResearch` | Recognized future area without accepted target semantics. |

## Route Kind Model

`route_kind` must use the route taxonomy from
`../32-target-authority-taxonomy-diagnostics.md`:

| Route Kind | Meaning |
|---|---|
| `GPUNative` | GPU render, compute, copy, clear, or fixed-function work executes the route without CPU-rasterizing the final draw/layer/filter/scene result. |
| `CPUPreparedGPU` | CPU creates a typed artifact consumed by an accepted GPU route. |
| `CPUReferenceOnly` | CPU oracle or report evidence only; not a product fallback. |
| `RefuseDiagnostic` | Deterministic refusal with stable diagnostics because no accepted product route exists. |

Do not use aggregate placeholders such as `mixed`; split the ticket or choose
the primary route-kind outcome.

## Milestones

| Milestone | Directory | Tickets | Purpose |
|---|---|---:|---|
| M0 | [M0-r6-boundary-review](M0-r6-boundary-review/README.md) | 7 | Backfill R0-R6 roadmap evidence as review-required tickets without claiming product activation. |
| M1 | [M1-first-route-product-activation](M1-first-route-product-activation/README.md) | 4 | Decide and, if accepted, prepare controlled product activation for the first solid `FillRect` route. |
| M2 | [M2-rect-rrect-gradient-scissor](M2-rect-rrect-gradient-scissor/README.md) | 4 | Expand the promoted vertical pattern to rrects, linear gradients, scissor clips, and batching evidence. |
| M3 | [M3-path-coverage-stroke-clip](M3-path-coverage-stroke-clip/README.md) | 5 | Add path, coverage, stroke, and clip routes with explicit prepared/native boundaries. |
| M4 | [M4-image-texture-codec-upload](M4-image-texture-codec-upload/README.md) | 4 | Add image shader, texture ownership, upload, codec provenance, and sampler boundaries. |
| M5 | [M5-layer-destination-read-filter](M5-layer-destination-read-filter/README.md) | 4 | Add saveLayer, destination-read, and filter render-node routes. |
| M6 | [M6-text-glyph-handoff](M6-text-glyph-handoff/README.md) | 4 | Consume typed pure Kotlin text artifacts through GPU renderer text routes. |
| M7 | [M7-runtime-effects-color-blend](M7-runtime-effects-color-blend/README.md) | 4 | Add registered runtime effects, blend, and color-management routes without SkSL support. |
| M8 | [M8-vertices-mesh-batching](M8-vertices-mesh-batching/README.md) | 3 | Add `DrawVertices`, mesh-like packing, and batching/key evidence. |
| M9 | [M9-performance-cache-release-gates](M9-performance-cache-release-gates/README.md) | 3 | Promote observed performance/cache telemetry into release-gate candidates. |
| M10 | [M10-legacy-gpu-raster-migration](M10-legacy-gpu-raster-migration/README.md) | 4 | Migrate or retire legacy `gpu-raster` routes only after route-specific evidence is accepted. |
| M11 | [M11-graphite-dawn-execution-gap-closure](M11-graphite-dawn-execution-gap-closure/README.md) | 9 | Cut missing execution/resource materialization tickets from the Graphite/Dawn gap matrix without duplicating M0-M10 gates. |

## Source Of Truth

Markdown remains the source of truth before optional Linear import. Linear
tickets must preserve ticket ID, milestone, status, PM note, spec sources,
Graphite algorithm references, acceptance criteria, required evidence,
validation command, fallback/refusal behavior, route kind, activation flags,
legacy gate, and claim impact.

Ticket IDs use milestone-scoped numbering: `KGPU-M<milestone>-<sequence>`, for
example `KGPU-M3-002`.

A ticket is ready only when it has one primary capability, explicit non-goals,
spec sources, dependencies, fallback/refusal behavior, expected evidence, and
validation commands. A reviewed R0-R6 ticket may move from `review` to `done`
only after an independent reviewer accepts the evidence and confirms that no
product support claim is implied by the status change.

## Templates

- [Milestone template](templates/milestone-template.md)
- [Ticket template](templates/ticket-template.md)

## Status

See [STATUS.md](STATUS.md) for the cross-milestone status summary.
