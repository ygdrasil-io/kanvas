---
id: KGPU-M32-020
title: "Legacy decommission: vertices-points-meshes formal refusal (dependency-gated)"
status: done
status: review
milestone: M32
priority: P1
owner_area: legacy-cleanup
claim_impact: DependencyGated
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M32-001, KGPU-M8-003]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-020 - Legacy decommission: vertices-points-meshes formal refusal (dependency-gated)

## PM Note

Les dessins de vertices, de points et de maillages (mesh) n'ont aucun chemin GPU
ni même de type de commande sur le bridge Kanvas : ce ticket les refuse
formellement, avec un diagnostic stable, et renvoie le portage à KGPU-M8-003. Le
PM suit ce ticket pour savoir que ces dessins ne sont pas supportés et qu'aucun
substitut temporaire ne sera ajouté.

## Problem

`GpuRendererLegacyRouteFamily.vertices-points-meshes` is a **full refuse**,
dependency/spec-gated family (decision matrix row 11). No dispatch exists, and the
`NormalizedDrawCommand` sealed interface has no `DrawVertices`/`DrawPoints`/
`DrawMesh` command types, nor any `dispatchVerticesX` function in `Surface.kt`.
The port is blocked on the vertices/points/mesh spec + dispatch infrastructure
and is owned by KGPU-M8-003.

## Scope

- Emit a stable `refuse:mesh:unsupported_mesh_command` diagnostic on the bridge
  when a vertices / points / mesh draw is attempted (so it cannot be silently
  dropped).
- Add a hermetic regression test asserting the diagnostic and linking
  KGPU-M8-003 as the dependency/spec-gated port owner.

## Non-Goals

- Do NOT add a short-lived vertices/points/mesh substitute (per AGENTS.md:
  dependency/spec-gated families must not get short-lived substitutes).
- Do not add `DrawVertices`/`DrawPoints`/`DrawMesh` command types or dispatch
  here — those belong to KGPU-M8-003.
- Do not add hidden CPU-rendered mesh texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 11)

## Graphite Algorithm References

- n/a — not required for this slice (refuse-only; vertices/points/mesh algorithm
  study belongs to KGPU-M8-003).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "RefuseDiagnostic"
    val dumpRefs: List<String>,       // hermetic refuse-test log
    val diagnostics: List<String>,    // refuse:mesh:unsupported_mesh_command
)
```

## Acceptance Criteria

- [ ] Vertices / points / mesh draws attempted via the bridge emit a stable
      `refuse:mesh:unsupported_mesh_command` diagnostic (no silent drop).
- [ ] A hermetic regression test asserts the diagnostic and references
      KGPU-M8-003 as the dependency/spec-gated port ticket.
- [ ] No short-lived vertices/points/mesh substitute is introduced (per
      AGENTS.md).

## Required Evidence

- No dispatch and no command type currently exist; a hermetic regression test
  binding vertices/points/mesh draws to a stable refuse diagnostic for this
  family — **not produced yet** (`proposed`).
- Dependency link to KGPU-M8-003 for the future vertices/points/mesh port.

## Fallback / Refusal Behavior

- Vertices / points / mesh routes emit a stable
  `refuse:mesh:unsupported_mesh_command` diagnostic; silent CPU-rendered mesh
  compatibility is not allowed.
- The `gpu-raster legacy` gate remains visible for this family until KGPU-M8-003
  delivers vertices/mesh support with real parity evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.vertices-points-meshes`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no — dependency/spec-gated refuse; support is claimed
  only by a future KGPU-M8-003 port.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test --tests "*VerticesPointsMeshRefuse*"
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 11 (`refuse`,
  dependency/spec-gated). No dispatch and no command types exist; formal refusal
  + hermetic test required when the command types are introduced. Port deferred
  to KGPU-M8-003. No evidence yet.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): documented **refuse-by-absence**.
  Verified by reading `KanvasSkiaBridge.kt` that there is NO drawVertices /
  drawPoints / mesh bridge entrypoint and no vertices/points command family on
  the Kanvas route, so such a draw cannot be silently served. No fabricated
  refuse test was added for a non-existent API (the existing
  `bridge unsupported emits diagnostic to stderr` test covers the
  `unsupported("drawVertices")` diagnostic). Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Real port =
  KGPU-M8-003 (dependency-gated).


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
