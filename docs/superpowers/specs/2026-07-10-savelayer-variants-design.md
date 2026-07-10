# SaveLayer variants — design

## Goal

Promote the three `SaveLayerRec` variants currently refused by the WebGPU renderer in one coherent `GPURenderer` layer-dispatch section, in this order: non-null `bounds`, composite `paint`, then `backdrop` image filters.

The work retains the ordinary-layer stack introduced by #2042. It must not silently flatten an unsupported request and must not add Ganesh, Graphite, a CPU fallback, or dynamic SkSL compilation.

## Current state and boundary

`GPURenderer` owns a single `BeginLayer` / `EndLayer` dispatcher. It supports only `saveLayer(null, null)` and `saveLayer(null, Paint())`, using a transparent full-surface texture composed with `SRC_OVER`. It suppresses children and emits a stable fatal diagnostic for `unsupported.layer.bounds`, `unsupported.layer.paint`, or `unsupported.layer.backdrop_filter`.

The same dispatcher remains the sole owner of all three variants. Picture replay that embeds a layer remains refused until picture replay itself becomes layer-aware.

## Architecture

Introduce a request classification directly before a layer is pushed:

```text
SaveLayerRec
  -> classifyLayerRequest(rec)
  -> SupportedLayerPlan | RefusedLayerPlan(reason)
  -> push LayerFrame / suppress subtree
  -> EndLayer applies the frame's composite plan
```

`LayerFrame` contains its parent scene target, child target, child-content flag, and immutable composite plan. The plan holds only restore-time state: device-space bounds/scissor, composite opacity, a supported fixed-function blend mode, and an optional prepared backdrop texture.

Normal draw commands keep targeting the active child scene. Only layer entry and restore perform allocation, clipping, filtering, and parent composition. This is the one code section requested by the user; no per-draw special cases are introduced.

## Stage 1 — bounded layers

`saveLayer(bounds, null)` and `saveLayer(bounds, Paint())` are supported when mapped bounds are finite and non-empty.

- The correctness promotion keeps the current full-surface child texture but uses mapped device bounds as the layer scissor during drawing and composition. This retains global draw coordinates and avoids a cross-shader origin/UV migration.
- Bounds intersect the surface. An empty intersection is a supported empty layer that cannot change its parent; non-finite or perspective bounds remain an explicit refusal.
- A physical bounded texture is a later performance optimization, requiring a separate origin/UV contract and measured evidence.

Acceptance: pixels outside the mapped bounds are unchanged; nested bounded and unbounded layers retain stack order; the existing ordinary-layer contract remains unchanged.

## Stage 2 — composite paint

The same `LayerFrame` adds `LayerCompositePaint`.

The first promoted subset is paint alpha (the alpha of `Paint.color`) plus `SRC_OVER` and individually tested fixed-function blend modes that the existing composite pass already maps. Opacity is applied exactly once at layer restore. Plain paint RGB is not used as a tint; it supplies opacity for the normal saveLayer paint use found in the GM corpus.

`shader`, `colorFilter`, `maskFilter`, `pathEffect`, `imageFilter`, custom `blender`, and destination-read blend modes remain refused until a dedicated registered GPU implementation and oracle exist. Diagnostics become field-specific, for example `unsupported.layer.paint.image_filter`.

Acceptance: a 50%-opacity `SRC_OVER` layer matches a linear-premultiplied pixel oracle; every promoted blend mode matches its surface-level equivalent; unsupported paint fields produce no child pixels and one stable refusal.

## Stage 3 — backdrop filters

Backdrop support reuses the dispatcher and frame with explicit intermediate ownership:

```text
parent scene snapshot -> backdrop source -> supported filter -> layer backdrop
child draws into layer -> composite layer into parent
```

The initial promotion is limited to the existing registered GPU blur plan, finite bounds, and the Stage 1 scissor contract. The parent is snapshotted before child rendering; blur input/output textures are unique per layer depth, preventing stale intermediate pixels. Other filter DAGs, crop/tile variants, arbitrary runtime effects, and backdrops combined with an unsupported layer paint remain explicit refusals. A backdrop failure suppresses the complete subtree.

Acceptance: a bounded backdrop blur changes only its requested region and matches a CPU/reference oracle; nested backdrops have no texture leak; unsupported DAGs leave the parent unchanged with one diagnostic.

## Diagnostics, tests, and evidence

- Retain one fatal diagnostic per refused `BeginLayer`, complete-subtree suppression, and unbalanced-layer diagnostics.
- Add route facts for ordinary, bounded, paint-composite, and backdrop-blur layers.
- Each stage follows red-green-refactor: focused failing fixture, minimal implementation, focused GPU + AAX tests, then GM diff/dashboard evidence.
- The fixture matrix covers transformed and clipped bounds, empty bounds, nested layers, 50% alpha, each promoted blend mode, unsupported paint fields, bounded backdrop blur, nested backdrop isolation, and refusal preservation.
- New support claims require reference plus CPU/GPU evidence. Generated renders and scores are updated only after green tests; thresholds are never weakened.

## Non-goals

- broad image-filter DAGs;
- arbitrary layer-paint shader, color-filter, blender, or destination-read blend chains;
- layer replay inside `DrawPicture`;
- physical bounded-target optimization in the correctness promotion;
- a CPU fallback or a non-WebGPU backend.
