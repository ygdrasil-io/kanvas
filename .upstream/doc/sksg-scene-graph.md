# SkSG Scene Graph

`modules/sksg/` is Skia's retained-mode **scene graph** — a small DAG
of `sk_sp`-counted nodes carrying geometry, paint, transforms, and
effects. It exists to back animation systems that need to mutate a
handful of properties per frame and re-render efficiently, without
re-issuing every draw call. [Skottie](skottie.md) builds an `sksg`
DAG from a Lottie JSON; the [SVG Module](svg-module.md) shares the
same vocabulary (paints, gradients, mask / clip / filter effects)
even though it walks its own DOM.

By contrast, `SkCanvas` is **immediate-mode**: it forgets each call
the moment it returns. SkSG keeps the structure live and re-emits it
into a canvas on demand — see [Canvas & Recording API](canvas-and-recording.md)
for the immediate-mode side.

## Module layout

| Header | Role |
|--------|------|
| `SkSGNode.h` | DAG base class — invalidation tracking, bounds caching |
| `SkSGRenderNode.h` | Mixin for nodes that render to a canvas |
| `SkSGGeometryNode.h` | Geometry source (path, rect, plane, …) |
| `SkSGPath.h`, `SkSGRect.h`, `SkSGPlane.h`, `SkSGMerge.h` | Concrete geometries |
| `SkSGGeometryEffect.h` | Geometry transforms (trim, round-corners, dash, offset) |
| `SkSGPaint.h`, `SkSGGradient.h` | `PaintNode` + linear/radial gradient |
| `SkSGDraw.h` | Pairs a geometry with a paint — the leaf "draw" node |
| `SkSGGroup.h` | Container that aggregates children's bounds |
| `SkSGTransform.h` | 2D affine / 3D matrix transform of a child subtree |
| `SkSGOpacityEffect.h` | Per-subtree opacity |
| `SkSGClipEffect.h`, `SkSGMaskEffect.h` | Geometric clip / luma-or-alpha mask |
| `SkSGColorFilter.h`, `SkSGRenderEffect.h` | Image / colour filter wrappers |
| `SkSGEffectNode.h` | Base for "wraps a single child" effects |
| `SkSGImage.h` | Renders an `SkImage` |
| `SkSGText.h` | Renders an `SkTextBlob` |
| `SkSGScene.h` | Convenience root wrapper: `revalidate()` + `render()` + `nodeAt()` |
| `SkSGInvalidationController.h` | Collects per-frame damage rects |

Implementations live in `modules/sksg/src/` with one `.cpp` per
header. `SkSGNodePriv.h` holds private friend helpers shared between
the base classes.

## The DAG and `Node` — `modules/sksg/include/SkSGNode.h`

`sksg::Node` is the abstract base: a refcounted vertex with optional
**ingress edges** (parents observing this node for invalidation).
Subclasses such as `Group`, `Effect`, and `Draw` add **egress
edges** (their children). Nodes carry two pieces of cached state:

- `fBounds` — the local-space bounding rect, recomputed in
  `onRevalidate`.
- A flag word with `kInvalidated_Flag`, `kDamage_Flag`,
  `kInTraversal_Flag` (cycle detection), plus per-subclass node
  flags.

Two **invalidation traits** modulate damage propagation:

- `kBubbleDamage_Trait` — node never emits damage itself; it lets
  children's damage bubble up.
- `kOverrideDamage_Trait` — node masks descendants' damage and
  emits its own (e.g. an opacity / mask change repaints a different
  rect than the underlying geometry change would).

### Invalidate / revalidate

The model is two-phase:

1. **Mutation.** Setters call `Node::invalidate()`. Each node walks
   its observers (parents) and tags them invalidated; the flag stops
   propagating once it hits an already-invalidated ancestor.
2. **Frame start.** The host calls `Scene::revalidate()` (or
   `root->revalidate(ic, ctm)` directly). This DFS walks only the
   invalidated subtrees, recomputes bounds bottom-up via
   `onRevalidate`, and pushes per-node damage rects into the supplied
   `InvalidationController`.

The damage list is the union of every changed rect since the previous
revalidation. A host that owns a back-buffer can use it as a clip
hint to repaint only the dirty regions.

The `SG_ATTRIBUTE(name, type, container)` macro generates a
getter / setter pair that calls `invalidate()` on change.

## RenderNode and rendering — `SkSGRenderNode.h`

`RenderNode` is the mixin layer for nodes that actually emit canvas
calls. `RenderNode::render(SkCanvas*, const RenderContext*)` is the
entry point; subclasses override `onRender`. The `RenderContext`
threads inherited paint state (opacity, blend mode, colour /
image filter, mask) down the tree without each layer creating its own
`saveLayer`. `onNodeAt(const SkPoint&)` answers the inverse —
hit-testing.

Container subclasses (`Group`, `Effect`, transforms) walk children in
order. `Draw` calls `SkCanvas::drawPath(geometry, paint)` (or the
shape-specific variant). Effect nodes either modify the
`RenderContext` in place (cheap effects like opacity, clip) or
trigger a `saveLayer` (image filters, mask effects, blend isolation).

## Effects taxonomy

| Header | Effect | Implementation |
|--------|--------|----------------|
| `SkSGOpacityEffect.h` | Per-subtree alpha | Multiplies into `RenderContext`; isolates only when needed |
| `SkSGClipEffect.h` | Geometric clip from a `GeometryNode` | `SkCanvas::clipPath` |
| `SkSGMaskEffect.h` | Alpha or luma mask | Two-pass `saveLayer` + `SkBlendMode::kDstIn` (or luma colour filter) |
| `SkSGColorFilter.h` | Wraps an `SkColorFilter` | Sets paint colour filter / `saveLayer` |
| `SkSGRenderEffect.h` | Wraps an `SkImageFilter` / blend mode / mask filter | `saveLayer` with the filter |
| `SkSGGeometryEffect.h` | Path-trimming / corner-rounding / dashing / offset | Materialises a new path from the input geometry |

## Scene wrapper — `modules/sksg/include/SkSGScene.h`

`Scene::Make(sk_sp<RenderNode>)` returns a small owning handle.
`Scene::render(canvas)` revalidates internally and emits draws;
`Scene::revalidate(ic)` is exposed separately so animation drivers
(Skottie's `seek()` plus `render()`) can split mutation from drawing.
`Scene::nodeAt(point)` returns the leaf render node under a screen
point — the basis for hit testing.

## Source map

| File | Role |
|------|------|
| `skia-main/modules/sksg/src/SkSGNode.cpp` | Invalidation propagation, bounds caching, observer storage |
| `skia-main/modules/sksg/src/SkSGRenderNode.cpp` | `RenderContext` plumbing, `saveLayer` decisions |
| `skia-main/modules/sksg/src/SkSGDraw.cpp` | The geometry-and-paint leaf draw |
| `skia-main/modules/sksg/src/SkSGGroup.cpp` | Children aggregation, child-list mutation |
| `skia-main/modules/sksg/src/SkSGRenderEffect.cpp` | Image filter / blender / mask filter wrapping |
| `skia-main/modules/sksg/src/SkSGScene.cpp` | High-level wrapper used by Skottie |

## Cross-references

- [Skottie](skottie.md) — biggest consumer; builds an `sksg` DAG
  from Lottie JSON and re-evaluates property animators each frame.
- [SVG Module](svg-module.md) — sibling renderer; reuses the same
  geometry / paint / effect vocabulary but walks its own SVG DOM.
- [Canvas & Recording API](canvas-and-recording.md) — the
  immediate-mode counterpart; SkSG ultimately renders into one of
  these.
- [Image Filters](image-filters-and-mask-filters.md) — backing for
  `SkSGRenderEffect`.
