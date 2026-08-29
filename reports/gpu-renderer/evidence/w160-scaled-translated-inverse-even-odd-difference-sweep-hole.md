# W160 — scaled translated inverse EvenOdd Difference sweep hole

W160 promotes the transformed inverse-EvenOdd `ClipOp.DIFFERENCE` route. The
public `KanvasSurface` scene applies a `1.5×` scale and `(2,1)` translation,
subtracts an inverse-EvenOdd outer rectangle plus inner hole from the current
clip, and draws a width-two square-cap miter stroke with a full-turn two-stop
sweep gradient.

The independent CPU oracle evaluates the transformed device-space shell,
inverse-maps pixel centres for shader sampling, applies square-stroke coverage,
and stores linear-light interpolation as sRGB RGBA8. The catalog requires 100%
similarity with one-channel LSB tolerance.

The native offscreen smoke validates the `uniform-positive-scale-translate`
stencil-cover route with inverse EvenOdd Difference (`Invert`/`NotEqual`) and
readback. Perspective and reflected transforms remain refused; no generic
fallback is introduced.
