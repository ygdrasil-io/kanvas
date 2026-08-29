# W147 — radial stroke under translated triangle clip

The native radial hard-path clip route already admits the bounded two-point stroke
consumer. The public inventory test `translated radial draw reaches the hard path
clip stroke stencil route` verifies a translated triangle clip, two-stop clamp radial
gradient, miter stroke, and the native route diagnostic.

No new catalogue case is promoted here: the existing radial stroke CPU oracles cover
unclipped annuli, while no independent combined stroke-plus-triangle-clip oracle is
available. This keeps the evidence honest and avoids claiming pixel parity without a
matching oracle.
