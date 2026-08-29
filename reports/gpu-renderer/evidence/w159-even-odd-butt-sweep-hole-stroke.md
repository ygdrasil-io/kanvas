# W159 — EvenOdd hole with a sweep butt stroke

W159 adds the butt-cap variant of the EvenOdd hole route. The public
`KanvasSurface` scene clips with an outer rectangle and an inner EvenOdd hole,
then draws the opaque two-stop `0..360°` sweep gradient on a width-four
butt-cap miter stroke.

The independent CPU oracle evaluates pixel-centre EvenOdd XOR membership and
finite butt-stroke coverage (no cap extension), then stores the linear-light
sweep interpolation as sRGB RGBA8. The catalog requires 100% similarity with
one-channel LSB tolerance.

The native offscreen smoke validates the EvenOdd stencil-cover route and
endpoint behavior. Square-cap and inverse/Difference variants remain separate
explicit contracts; no generic stroke fallback is introduced.
