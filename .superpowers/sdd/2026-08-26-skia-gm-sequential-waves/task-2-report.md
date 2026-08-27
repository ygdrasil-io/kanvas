# Task 2 — Controlled transforms

## Scope delivered

- Kept the Wave 1 canonical `scale` identity and its strict positive-uniform
  admission unchanged.
- Added the only additionally proven `FillPath` transform shapes: exact 90°
  rotation (`[0,-1;1,0]`) and exact 180° rotation (`[-1,0;0,-1]`), with finite
  translation (including pivot rotations).
- The mapper labels these as `right-angle-rotation`; the planner admits them
  to the existing native stencil-cover route and device-vertex lowering.
- Skew, arbitrary affine, reflections, non-uniform/negative/zero scale,
  singular matrices, perspective, AA expansion, and unsupported clip shapes
  retain their existing stable refusal boundaries. No clip ABI/consumer change
  was made.
- Fill rule, inverse-fill, AA, edge budgets, and canonical content path keys
  remain owned by the existing routes.

## TDD evidence

RED (before production code):

```text
:kanvas:test GPUFramePathApiInventoryTest.public exact quarter turn FillPath…
  expected native.path_fill.stencil_cover, got refused.unsupported.transform.class_downgrade
:gpu-renderer:test BasicPathFillPreparedRouteTest.exact right angle rotation…
  expected Prepared, got Refused(unsupported.transform.path_class)
```

GREEN:

```text
./gradlew --no-daemon :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUFramePathApiInventoryTest
./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

Both commands passed. The added behavioral coverage proves 90° device vertices
and `Stencil1x`, 180° native routing, and stable skew/perspective refusal.

## GM validation

`rotatedcubicpath` exists as a source GM but is not registered in the runtime
service list. Its attempted isolated generation therefore failed safely before
rendering with `No GMs match the selected filters`; no reference image,
generated render, threshold, or `gpu-renderer-scenes` file was modified. The
registered mapper/planner tests above are the transformed-route substitute.

## Commit

Implementation SHA: 13cd1d3ef1b715eab592fc4c418f9fa4d36ddae9
