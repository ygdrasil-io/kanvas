# W210 — reverse horizontal dashed stroke

W210 removes an accidental direction restriction from the bounded dash
classifier. The exact horizontal `[8,4]` route now accepts either traversal
direction while preserving phase semantics from the source path start.

The public `Surface` scene draws `(28,16)` to `(4,16)` with a width-four
non-AA butt/miter stroke and phase `0`. The independent CPU oracle evaluates
distance along that reverse segment and expects two opaque eight-pixel runs
(`64` pixels). The native smoke proof checks the run/gap pattern, zero
refusals, and positive draw, pipeline, submission, and readback counters.

The route remains limited to horizontal/vertical integral segments, phases
`0` or `4`, the `[8,4]` pattern, and the existing transform and stroke policy.
