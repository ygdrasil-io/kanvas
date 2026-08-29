# W188 — Diagonal butt/miter stroke

W188 promotes the standalone diagonal width-four butt-cap/miter stroke with
fractional endpoints `(5.25,8.25) → (21.25,20.25)` on the native path-stroke
route. The fractional fixture avoids pixel-tie ambiguity while proving finite
segment coverage and the absence of cap extension.

The independent pixel-center CPU oracle matches native offscreen RGBA8
readback exactly, with one submit and one readback copy.
