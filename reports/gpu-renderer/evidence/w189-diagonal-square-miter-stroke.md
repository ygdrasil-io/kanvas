# W189 — Diagonal square/miter stroke

W189 promotes the standalone diagonal width-four square-cap/miter stroke with
fractional endpoints `(5.25,8.25) → (21.25,20.25)` on the native path-stroke
route. The evidence covers both the finite stroke body and the tangent-aligned
two-pixel cap extensions.

The independent pixel-center CPU oracle and native offscreen RGBA8 readback
agree exactly, with one submit and one readback copy recorded.
