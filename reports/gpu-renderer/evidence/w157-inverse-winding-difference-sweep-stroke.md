# W157 — inverse Winding Difference with a sweep square stroke

W157 promotes the inverse-Winding `ClipOp.DIFFERENCE` route for a bounded
full-turn sweep square stroke. The public `KanvasSurface` scene subtracts an
inverse-Winding triangle from the current clip, leaving the triangle interior
for the opaque two-stop `0..360°` sweep gradient on a width-four square-cap
miter stroke.

The independent CPU oracle models the cancellation of inverse fill and
Difference membership, applies pixel-centre triangle and square-stroke
coverage, and computes the sweep in linear light before sRGB RGBA8 storage.
The catalog requires 100% similarity with one-channel LSB tolerance.

The native offscreen smoke validates the inverse-Winding Difference stencil
route (`NotEqual`) and readback oracle. Ordinary Winding, EvenOdd, and inverse
EvenOdd variants remain separate explicit contracts; no generic clip fallback
is introduced.
