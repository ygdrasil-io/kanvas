# W204 — bounded horizontal dashed stroke

W204 promotes the first positive `PathEffect.Dash` route through the public
Kanvas `Surface` API. The case uses a non-AA horizontal width-four butt/miter
stroke from `(4,16)` to `(28,16)` with the bounded `[8,4]` pattern and zero
phase.

The production route expands the dash runs on the CPU and submits their native
stroke geometry. The independent CPU oracle checks the two opaque runs at
`x=4..11` and `x=16..23` over the four covered rows (`64` pixels total).

Evidence:

- catalogue case: `horizontal-dashed-butt-miter-stroke`;
- route: `kanvas.surface.render`, native prepared stroke path;
- oracle: `surface-srgb-dashed-stroke`, exact RGBA8 pixel-center comparison;
- native smoke proof: `GPUPreparedSurfaceProductNativeSmokeTest` reports zero
  refused operations, positive draw/pipeline/submission/readback counters;
- explicit boundary: empty dash arrays and non-bounded patterns remain refusal
  cases and are not implied by this promotion.

This wave changes no retired scene layer and does not broaden support to
rotated, round-cap, multi-segment, or complex dash patterns.
