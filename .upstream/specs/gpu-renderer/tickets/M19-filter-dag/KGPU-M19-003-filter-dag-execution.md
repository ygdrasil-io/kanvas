---
id: KGPU-M19-003
title: "Add filter DAG execution: multi-node graphs with intermediate texture ownership"
status: proposed
milestone: M19
priority: P0
owner_area: filters-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M19-001, KGPU-M19-002]
legacy_gate: null
---

# KGPU-M19-003 - Add filter DAG execution: multi-node graphs with intermediate texture ownership

## PM Note

Le DAG de filtres permet de chaîner les filtres: flou puis matrix, ou matrix puis flou. La gestion des textures intermédiaires est le défi principal.

## Problem

Multiple filters must be chainable into a DAG (directed acyclic graph) where each filter node owns its intermediate texture and passes it to the next node. Without DAG execution, only single filters are possible.

## Scope

- Add filter DAG node graph construction from filter list
- Add intermediate texture allocation and ownership per node
- Add DAG execution ordering (topological sort of filter nodes)
- Produce filter DAG rendering fixture dumps

## Non-Goals

- Bounded DAG only: single-node + 2-node chains max
- No arbitrary filter DAG beyond accepted bounds
- No filter node sharing or deduplication

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_FILTER_BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source src/gpu/graphite/DrawContext.cpp filter; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class FilterDAG(val nodes: List<FilterNode>) {{\n  fun execute(input: GpuTexture): GpuTexture;\n}}\n// Each node: input texture -> filter -> output texture
```

## Acceptance Criteria

- [ ] Single-node filter DAG executes correctly (blur or matrix alone)
- [ ] 2-node chain executes correctly (blur->matrix, matrix->blur)
- [ ] Intermediate textures are correctly allocated and owned per node
- [ ] DAG exceeding depth limit emits stable diagnostic

## Required Evidence

- Single-node filter DAG GPU rendering fixture dump
- 2-node chain GPU rendering fixture dump (blur->matrix, matrix->blur)
- Intermediate texture lifecycle trace
- DAG depth limit refusal diagnostic

## Fallback / Refusal Behavior

DAG exceeding accepted bounds emits stable diagnostic; last valid node output preserved.

## Dashboard Impact

- Expected row: `gpu-renderer.m19.filter-dag-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*FilterDAG*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M19`
- `area:filters-passes`
