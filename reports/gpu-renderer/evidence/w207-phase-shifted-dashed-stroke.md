# W207 — phase-shifted dashed stroke

W207 expands the exact horizontal dash route by admitting the second phase
covered by the native implementation: phase `0` and phase `4` for the
`[8,4]` butt/miter pattern. The bounded policy is shared by analysis,
semantic lowering, payload validation, and coverage materialization so the
route cannot be selected by only one pipeline stage.

The public `Surface` case draws a non-AA width-four line from `(4,16)` to
`(28,16)` with phase `4`. The independent CPU oracle expects the device-space
on-runs produced by that phase (`64` opaque RGBA8 pixels). The native smoke
proof records zero refusals and positive draw, pipeline, submission, and
readback counters.

Phase `2`, arbitrary dash arrays, non-integral transforms, and other stroke
variants remain explicitly refused with
`unsupported.core_primitive.stroke.dash_exact_lowering`.
