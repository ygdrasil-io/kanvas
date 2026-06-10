# KAN-033 Runtime Effect Uniform Preview

Status: `pass`
Status counts: effects=2; edited-states=4; GPU-parity-states=4; pipeline-key-changes=0; invalid-edits=2.

KAN-033 proves a bounded headless preview contract for two registered runtime effects. Uniform values update payload state and telemetry, but do not change `PipelineKey`, compile new WGSL, or create live controls for arbitrary SkSL input.

Kadre native: `opt-in`

## Preview Effects

| Effect | Parameter | Updates | PipelineKey stable | Compile delta | Fallback |
|---|---|---:|---|---:|---|
| `runtime.simple_rt` | `gColor.b` | `2` | `True` | `0` | `none` |
| `runtime.spiral_rt` | `rad_scale` | `2` | `True` | `0` | `none` |

## Stable Refusals

- `runtime-effect.preview-uniform-out-of-range`: Invalid preview values are clamped or refused by parameter policy.
- `runtime-effect.preview-effect-not-registered`: Preview controls are limited to registered Kanvas descriptors.
- `runtime-effect.arbitrary-sksl-unsupported`: Kanvas does not provide a live SkSL editor or dynamic SkSL compilation.

## Non-Claims

- No live SkSL editor.
- No live controls for unregistered effects.
- No new WGSL generated per uniform value.
- No broad runtime-effect support beyond registered descriptors.
- No Kadre native window requirement for headless validation.
