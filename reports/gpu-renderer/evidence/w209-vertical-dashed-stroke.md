# W209 — vertical dashed stroke

W209 extends the exact `[8,4]` dash route from horizontal to vertical
geometry. The accepted domain is unchanged otherwise: non-AA width-four,
butt cap, miter join, integral device coordinates, identity or integral
translation, and phase `0` or `4`.

The public `Surface` scene draws the vertical segment `(16,4)`–`(16,28)` at
phase `0`. The independent CPU oracle expects two eight-pixel on-runs across
four columns (`64` opaque RGBA8 pixels). The native smoke proof checks both
dash gaps, zero refused operations, and positive draw, pipeline, submission,
and readback counters.

The horizontal proof remains a distinct lowering identity, while arbitrary
angles, dash arrays, phases outside `{0,4}`, caps/joins, and transforms beyond
the bounded policy continue to refuse before GPU submission.
