# W193 — Scaled and translated diagonal square/miter stroke

W193 promotes the fractional diagonal width-two square-cap/miter stroke under
`translate(2,3) · scale(2,2)`. Device space contains the width-four segment
`(10.25,11.25) → (26.25,20.25)` plus tangent-aligned cap extension.

The independent transformed CPU oracle matches native offscreen RGBA8
readback exactly, with one submit and one readback copy.
