# W191 — Scaled and translated butt/miter stroke

W191 promotes the horizontal width-two butt/miter stroke under the public
Canvas transform sequence `translate(2,3) · scale(2,2)`. The device-space
contract is the width-four segment `(10,19) → (30,19)` with no cap extension.

The independent translated device-space oracle matches native offscreen RGBA8
readback exactly; native counters show one submit and one readback copy.
