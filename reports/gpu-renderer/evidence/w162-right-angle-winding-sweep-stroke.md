# W162 — right-angle Winding sweep stroke

W162 promotes the bounded 90-degree rotation route for a Winding path clip.
The public `KanvasSurface` scene clips with a hard triangle, rotates the draw
by 90 degrees around `(16,16)`, and records a width-two square-cap miter stroke
with a full-turn two-stop sweep gradient.

The independent CPU oracle uses the transformed device-space stroke and clip,
applies square-cap distance coverage, and compensates the quarter-turn shader
angle before linear-light sRGB RGBA8 storage. The selected native geometry is
fully rejected by the clip, so the expected image is the background; this
still proves that the rotated stencil route does not leak pixels.

The native offscreen smoke validates the `right-angle-rotation` stencil-cover
route and exact background readback. Non-right-angle rotations remain refused;
no generic transform fallback is introduced.
