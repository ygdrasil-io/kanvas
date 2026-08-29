# W173 — Half-turn Winding clip with an opaque butt stroke

W173 promotes the 180-degree transform variant of the path-stroke route. The
public `KanvasSurface` scene clips to a hard Winding triangle, rotates a local
diagonal segment by a half turn around a bounded pivot, and draws an opaque
width-four butt-cap miter stroke.

The independent CPU oracle evaluates the transformed device-space segment and
triangle at pixel centres, with no reuse of the renderer's path expansion. The
catalog requires exact opaque RGBA8 output and keeps the non-right-angle
transform refusal boundary explicit.

The native offscreen smoke validates the same stencil-cover route, transformed
geometry, and exact readback counters. No generic transform fallback is added.
