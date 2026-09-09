# W5a Task 1 — Solid / Opacity

## Delivered

`Shader.Opacity` is public and bounded to finite alpha in `[0, 1]`.  Its snapshot, scene material, Picture archive and restoration paths preserve the nested structure defensively.  W5a now normalizes the Solid/Opacity Rect subset into an immutable `MaterialPlanTable`, with `NumericOperationGraphV1` and a closed `PlanDrawMaterialAuthority` union.  The W3 Rect compiler publishes a distinct `w5a-solid-opacity-rect-v1` graph when that material authority is present; its lowerer resolves the sealed material plan rather than using the legacy material mapper.

The public proof is a Surface/Canvas/Picture/render/pixels test only.  Its independent oracle models sRGB decode, linear premultiplication, opacity and paint alpha, attachment sRGB encoding, and a one-code WGSL/UNORM envelope.

## Files changed

- `kanvas`: public shader, snapshots, scene adapter, GPU compatibility traversal, public tests/oracles.
- `render-ir`: `MaterialNode.Opacity` validation and semantic validation admission.
- `gpu-plan`: material program/binding table, numeric graph, effective planner, W5a diagnostics, Rect plan publication and RenderGraph authority.
- `gpu-renderer`: normalized material traversal and W3 lowerer material-plan resolution.

## TDD evidence

RED: `rtk ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest*' --no-daemon --console=plain` initially failed test compilation with seven unresolved `Shader.Opacity` references.  After public API capture, the public test remained RED through the legacy route (`unsupported.composite.paint`; `channel=0 observed=106 expected=48..49`; `channel=0 observed=11 expected=8..9`).

GREEN:

- `rtk proxy ./gradlew :gpu-plan:compileKotlin :gpu-renderer:compileKotlin --no-daemon --console=plain` — BUILD SUCCESSFUL in 5m22s.
- `rtk proxy ./gradlew :kanvas:test --tests '*W5aMaterialSurfacePixelTest*' --no-daemon --console=plain` — BUILD SUCCESSFUL in 1m31s; 3/3 passed.
- `rtk proxy ./gradlew :kanvas:test --tests '*GPUPlanSurfacePixelTest.opaque overlapping rectangles match the independent CPU oracle' --tests '*GPUPlanSurfacePixelTest.translucent rectangles use linear premultiplied SrcOver exactly' --tests '*GPUPlanSurfacePixelTest.DeviceRect clip limits an otherwise larger solid rectangle' --no-daemon --console=plain` — BUILD SUCCESSFUL in 10s; 3/3 passed.
- `rtk git diff --check` — clean.

## Decisions / concerns

The public Picture proof restores the bytes then replays the restored Picture onto the target Canvas.  This preserves a top-level Rect so the W5a Rect capability, rather than unsupported DrawPicture composition, owns the render.

No font, codec, GM, Skia integration, dashboard, baseline or JPEG work was included.  The W5a slice is deliberately only Solid/Opacity integral Rect + SrcOver; broader shader graphs remain outside this capability.
