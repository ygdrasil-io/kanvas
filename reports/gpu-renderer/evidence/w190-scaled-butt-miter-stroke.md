# W190 — Uniformly scaled butt/miter stroke

W190 promotes a horizontal width-two butt/miter stroke under a uniform 2x
Canvas scale. In device space it becomes the width-four segment
`(8,16) → (28,16)`; the finite butt caps remain exactly at those endpoints.

The independent device-space CPU stroke oracle matches the native offscreen
RGBA8 readback exactly, with one submit and one readback copy.
