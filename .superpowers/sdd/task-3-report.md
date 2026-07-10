# Task 3 — WebGPU bounded image-blur routing

## Delivered

- Added `GPUBackendOffscreenTarget.renderImageCommand(...)` for accepted
  `GPUImageFilterPlan.Blur` commands.
- The route allocates three bounded local textures (`source`, `horizontal`,
  `vertical`), uploads/draws the source image once, runs generated horizontal
  and vertical Gaussian WGSL passes, then composites the vertical texture into
  the scene with `SrcOver`.
- Added `FILTERED_IMAGE_COMPOSITE_WGSL` and a 32-byte uniform packer for the
  local-texture-to-scene mapping.
- Routed every `GPURenderer` `DrawImageRect` call site through one local gate:
  direct image, picture expansion, nine-patch/lattice cells, and atlas cells.
  `None` and `Identity` retain ordinary dispatch; `Refused` records its
  terminal diagnostic; only accepted `Blur` uses the local route.
- Preserved the direct `dispatchImageRect` blur guard, so a caller cannot
  bypass the route.

## Coordinate decision

The local source destination is `command.dst - plan.outputBounds.origin`, not
unconditionally `(haloX, haloY)`. For an unclipped plan these are identical.
When the output bounds are clipped at a scene edge, subtracting the actual
output origin keeps the source and its available halo aligned with the
allocated texture and avoids losing content at the clipped edge. The source
pass is clipped to the entire local texture; the final 32-byte composite
uniform maps that texture back to `plan.outputBounds` in scene coordinates.

Tint is applied exclusively by the source `IMAGE_TEXTURE_WGSL` pass. Both blur
passes only sample premultiplied intermediate data, and the final pass uses
`GPUBlendMode.SRC_OVER`.

## TDD evidence

RED was observed before implementation:

```text
GPUImageFilterDispatchTest.kt:37:29 Unresolved reference 'renderImageCommand'
```

The failing test specifies the exact pass sequence
`source`, `blur-h`, `blur-v`, `scene`, and verifies three intermediate textures
with the expected 16×22 source dimensions.

GREEN verification completed successfully:

```text
rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUImageFilterDispatchTest --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :kanvas:test --tests org.graphiks.kanvas.surface.gpu.GPUAlphaImageMaterialTest --console=plain
BUILD SUCCESSFUL
```

## Scope and concerns

Scope is limited to the already accepted single-node CLAMP blur route
(`input == null`, sigma bounds owned by Tasks 1–2). No CPU fallback,
`MaskFilter`, pixel oracle, telemetry, or native backend API was added.
`drawCompositePass` already binds offscreen texture labels, so the route needs
no runtime-contract extension. Pixel-reference/oracle validation remains
intentionally outside this task's scope.
