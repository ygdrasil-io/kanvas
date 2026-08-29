# W194 — Horizontal butt/miter stroke

W194 promotes the baseline public horizontal width-four butt-cap/miter stroke
`(4,16) → (28,16)` on the native path-stroke route. The oracle keeps the
segment finite, so rows `y=14..17` are painted only between the half-open
endpoint columns `x=4..27`.

Native offscreen RGBA8 readback matches the independent pixel-center oracle
exactly, with one submit and one readback copy.
