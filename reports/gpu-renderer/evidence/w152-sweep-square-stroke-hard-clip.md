# W152 — full-turn sweep square stroke under a winding triangle clip

W152 promotes the full-turn sweep-gradient variant of the bounded path-stroke
route. The public `KanvasSurface` scene uses a hard non-AA Winding triangle clip
and one opaque two-stop `0..360°` `SweepGradient` on a width-four square-cap miter
stroke.

The independent CPU oracle evaluates pixel-centre winding membership and square-cap
stroke coverage in device space, then computes the angular sweep interpolation in
linear light before sRGB RGBA8 storage. The catalog requires 100% similarity with
one-channel LSB tolerance.

The existing native offscreen smoke validates the same sweep square-stroke route,
including stencil-cover clip operations, native submission, and readback against
an independent oracle. Partial-angle, multi-stop, and non-full-turn variants remain
outside this bounded promotion.

