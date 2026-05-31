# M56 GRA-334 Image-Filter Promotion Decision

Date: 2026-05-31
Ticket: GRA-334
Scope: bounded image-filter expected-unsupported promotion check

## Decision

No GRA-334 candidate row is promoted to `pass` in this patch.

The only bounded crop/prepass route with real generated CPU/GPU/reference/diff
and stats evidence is already represented by the generated `pass` row
`crop-image-filter-nonnull-prepass`. Re-labelling any of the selected
`expected-unsupported` rows would over-claim broader image-filter DAG support
or require emitting a fake GPU image/diff, which is explicitly disallowed.

## Candidate Audit

| Candidate row | Current status | Promotion result | Blocker |
|---|---|---|---|
| `image-filter-crop-nonnull-prepass-required` | `expected-unsupported` | Rejected | This row is an explicit refusal for non-selected `Crop(input = nonNull)` graph shapes. It has CPU/reference diagnostic artifacts and a GPU refusal route, but no GPU image or GPU diff. |
| `m52-big-tile-image-filter-dag-refusal` | `expected-unsupported` | Rejected | BigTile requires a picture/layer prepass DAG route. The row is inventory-derived refusal evidence, not rendered GPU support evidence. |
| `m53-imagefilters-cropped-boundary` | `expected-unsupported` | Rejected | Derived from `image-filter-crop-nonnull-prepass-required` and keeps the same crop/prepass refusal reason for broader cropped DAG shapes. |
| `m54-imagefilters-graph-boundary` | `expected-unsupported` | Rejected | Derived from the same unsupported base and intentionally covers broad graph or picture-prepass shapes with `image-filter.dag-or-picture-prepass-required`. |

## Existing Supported Route

The existing supported bounded crop/prepass row is:

```text
crop-image-filter-nonnull-prepass
```

It already has generated `pass` evidence:

- reference: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/skia.png`
- CPU image: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/cpu.png`
- GPU image: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/gpu.png`
- CPU diff: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/cpu-diff.png`
- GPU diff: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/gpu-diff.png`
- CPU route: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-cpu.json`
- GPU route: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/route-gpu.json`
- stats: `reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass/stats.json`

Generated row facts from `reports/wgsl-pipeline/scenes/generated/results.json`:

```text
status=pass
referenceKind=skia-upstream
gpu.status=pass
gpu.route.fallbackReason=none
stats.pixels=128000
stats.matchingPixels=125600
stats.maxChannelDelta=110
stats.threshold=50
```

The renderer-side route is the bounded M38 shape:

```text
webgpu.image-filter.crop-nonnull-offset-prepass
```

It is limited to `Crop(kDecal, input = Offset(input = null))` with no
`paint.colorFilter` and fixed-function layer blend. That route does not cover
the broader GRA-334 candidate rows.

## Technical Blocker

The broader candidates require at least one of the following before promotion:

- a selected child-materialization contract for non-Offset crop children;
- picture/layer prepass scheduling for BigTile or graph shapes;
- route diagnostics that distinguish the bounded supported subgraph from broad
  arbitrary DAG compilation;
- generated GPU render and GPU diff artifacts with `fallbackReason=none`.

Current renderer diagnostics still intentionally refuse broad non-null child
crop and graph/picture-prepass shapes:

```text
image-filter.crop-input-nonnull-prepass-required
image-filter.dag-or-picture-prepass-required
```

Promoting one of those rows by copying the existing
`crop-image-filter-nonnull-prepass` GPU artifact would conflate a narrow
`Crop(Offset(null))` support claim with broad cropped/BigTile/graph support.
That would violate the conformance dashboard rule that generated `pass` rows
must include real row-specific GPU artifacts and `fallbackReason=none`.

## Validation

Commands run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass. `pipelinePmBundle` wrote
`build/reports/wgsl-pipeline-pm-bundle`.
