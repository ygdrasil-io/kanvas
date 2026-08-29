# W158 — scaled translated inverse Winding sweep stroke

W158 promotes the bounded uniform scale-plus-translation route for an inverse
Winding path clip. The public `KanvasSurface` scene applies a `1.5×` scale and
`(2,1)` translation, clips with the inverse of a triangle, and draws a width-two
square-cap miter stroke carrying a full-turn two-stop sweep gradient.

The independent CPU oracle evaluates the transformed triangle and stroke in
device space, inverse-maps device pixel centres for shader sampling, and stores
the linear-light interpolation as sRGB RGBA8. The catalog requires 100%
similarity with one-channel LSB tolerance.

The native offscreen smoke validates the `uniform-positive-scale-translate`
stencil-cover route and inverse-Winding readback. Perspective and reflected
transforms remain refused; no generic transform fallback is introduced.
