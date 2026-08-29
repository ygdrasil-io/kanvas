# W172 — Winding Difference clip with a sweep-gradient square stroke

W172 promotes the non-inverse Winding Difference variant of the sweep-gradient
stroke route. The public `KanvasSurface` scene subtracts a hard Winding triangle
from the current clip, then draws an opaque width-four square-cap miter stroke
with a two-stop full-turn sweep gradient.

The independent CPU oracle evaluates the exterior of the triangle, square-cap
stroke coverage, and full-turn linear-light sweep interpolation at pixel
centres. The catalog keeps the one-channel tolerance and requires a stable
native path-stroke stencil-cover route.

The native offscreen smoke validates the same Difference stencil comparison,
exact readback witnesses, and one submit/readback pair. Unsupported inverse or
non-right-angle transform classes remain explicit boundaries.
