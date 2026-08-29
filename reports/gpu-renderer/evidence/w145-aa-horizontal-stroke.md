# W145 — bounded anti-aliased horizontal path stroke

The native admission is deliberately limited to a public two-point horizontal path,
solid SrcOver, width `4`, butt cap, miter join, no dash/effect, and identity or
translation transform. Device coordinates may be integral or half-pixel aligned.

The semantic lowering keeps this case on `StrokeStencilEdgeFan` with
`coverageMode=StencilAA`; frame preparation promotes the render to
`GPUSamplePlan.MultisampleFrame(4)`. Non-horizontal anti-aliased strokes remain on
the prepared route.

Evidence:

* `FirstRoutePlannerTest`: positive native route and negative non-horizontal route.
* `GPUFramePathApiInventoryTest.bounded anti aliased horizontal stroke crosses native preparation with MSAA`:
  native preparation records a 4x sample plan and StencilAA semantic packet.
* Validation commands:
  `./gradlew --no-daemon --no-build-cache :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.analysis.FirstRoutePlannerTest`
  and the targeted Kanvas inventory test both pass.

This is structural native-route evidence; no pixel-perfect oracle or GM promotion is
claimed by this slice.
