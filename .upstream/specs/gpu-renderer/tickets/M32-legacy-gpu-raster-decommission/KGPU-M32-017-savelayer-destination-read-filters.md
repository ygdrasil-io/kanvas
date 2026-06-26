---
id: KGPU-M32-017
title: "Legacy decommission: savelayer-destination-read-filters formal refusal (dependency-gated)"
status: review
milestone: M32
priority: P1
owner_area: legacy-cleanup
claim_impact: DependencyGated
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M32-001, KGPU-M11-006]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-017 - Legacy decommission: savelayer-destination-read-filters formal refusal (dependency-gated)

## PM Note

Les `saveLayer`, la lecture de la destination et les DAG de filtres n'ont aucun
chemin GPU sur le bridge Kanvas : seul le layer racine est accepté, tout autre
layer est refusé. Ce ticket formalise le refus et renvoie le portage à
KGPU-M11-006. Le PM suit ce ticket pour savoir que les calques et filtres ne sont
pas supportés et qu'aucun substitut temporaire ne sera ajouté.

## Problem

`GpuRendererLegacyRouteFamily.savelayer-destination-read-filters` is a **full
refuse**, dependency-gated family (decision matrix row 8). All 4 dispatch
functions refuse non-`Root` layers with `unsupported_layer` at
`Surface.kt:194,248,346` and `TextRunDispatch.kt:103`. No destination-read
dispatch exists, no filter-DAG dispatch exists, and the `NormalizedDrawCommand`
sealed interface has no `SaveLayer`/`Restore` command types. The port is blocked
on layer/destination-read/filter-DAG infrastructure and is owned by
KGPU-M11-006.

## Scope

- Emit a stable `refuse:layer:unsupported_layer` / `refuse:filter:unsupported_filter_dag`
  diagnostic on the bridge for saveLayer / destination-read / filter-DAG draws
  (so the production-default path cannot silently flatten them).
- Add a hermetic regression test asserting the diagnostic(s) and linking
  KGPU-M11-006 as the dependency-gated port owner.

## Non-Goals

- Do NOT add a short-lived saveLayer / destination-read / filter substitute (per
  AGENTS.md: dependency-gated families must not get short-lived substitutes).
- Do not implement layer scopes, destination read, or filter DAGs here.
- Do not add hidden CPU-rendered layer/filter texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md` (row 8)

## Graphite Algorithm References

- n/a — not required for this slice (refuse-only; layer/destination-read/filter
  algorithm study belongs to KGPU-M11-006).

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,            // "RefuseDiagnostic"
    val dumpRefs: List<String>,       // hermetic refuse-test log
    val diagnostics: List<String>,    // refuse:layer:unsupported_layer, refuse:filter:unsupported_filter_dag
)
```

## Acceptance Criteria

- [ ] saveLayer / destination-read / filter-DAG draws emit a stable refuse
      diagnostic on the bridge (no silent flatten to Root, no silent drop).
- [ ] A hermetic regression test asserts the diagnostic(s) and references
      KGPU-M11-006 as the dependency-gated port ticket.
- [ ] No short-lived saveLayer/filter substitute is introduced (per AGENTS.md).

## Required Evidence

- The non-`Root` layer `unsupported_layer` refuse already exists
  (`Surface.kt:194,248,346`, `TextRunDispatch.kt:103`). A dedicated hermetic
  regression test binding saveLayer / destination-read / filter-DAG to a stable
  diagnostic for this family — **not produced yet** (`proposed`).
- Dependency link to KGPU-M11-006 for the future saveLayer/filter port.

## Fallback / Refusal Behavior

- saveLayer / destination-read / filter routes emit stable refuse diagnostics;
  silent fallback to a flattened Root layer or CPU-rendered layer texture is not
  allowed.
- The `gpu-raster legacy` gate remains visible for this family until KGPU-M11-006
  delivers layer/filter support with real parity evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.savelayer-destination-read-filters`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no — dependency-gated refuse; support is claimed only
  by a future KGPU-M11-006 port.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test --tests "*SaveLayerRefuse*"
rtk git diff --check
```

## Status Notes

- `proposed`: Phase 2.A ticket created from KGPU-M32-001 row 8 (`refuse`,
  dependency-gated). Non-Root layer refuse already exists; saveLayer/Restore
  command types are absent, so a hermetic test binding the family to a stable
  diagnostic is still owed. Port deferred to KGPU-M11-006. No new evidence here.
- 2026-06-26 (Phase 2.B(ii), still `proposed`): documented **refuse-by-absence**.
  Verified by reading `KanvasSkiaBridge.kt` that there is NO saveLayer /
  save / restore / layer-scope bridge entrypoint, so a saveLayer/destination-read/
  filter draw cannot be silently served by the Kanvas route. No fabricated refuse
  test was added for a non-existent API. Report:
  `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`. Real port =
  KGPU-M11-006 (dependency-gated).


- `review` (2026-06-26): promoted after maintainer review of PR #1892 (https://github.com/ygdrasil-io/kanvas/pull/1892) — no blocking issues found.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
