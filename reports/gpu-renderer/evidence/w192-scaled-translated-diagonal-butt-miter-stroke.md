# W192 — Scaled and translated diagonal butt/miter stroke

W192 promotes the fractional diagonal width-two butt/miter stroke under the
public Canvas transform sequence `translate(2,3) · scale(2,2)`. Its device-space
segment is `(10.25,11.25) → (26.25,20.25)` with width four and finite butt
endpoints.

The independent transformed device-space CPU oracle matches native offscreen
RGBA8 readback exactly, with one submit and one readback copy.
