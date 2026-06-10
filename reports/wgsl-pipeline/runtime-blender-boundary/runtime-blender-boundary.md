# Runtime Blender Boundary

Ticket: `KAN-032`
Status: `expected-unsupported`
Status counts: candidates=1; CPU-supported=1; GPU-supported=0; expected-unsupported=1; implicit-readback=0.

## Candidate

- Stable id: `runtime.invert_blender`
- Kind: `kBlender`
- Support state: `cpu-only`
- CPU implementation: `kotlin/invert_blender`
- WGSL implementation: `none`
- Fallback reason: `runtime-effect.blender-dst-read-unsupported`

## Route Diagnostics

- Route: `webgpu.runtime-blender.expected-unsupported`
- Status: `expected-unsupported`
- Fallback: `runtime-effect.blender-dst-read-unsupported`
- Layer requirement: `blend.shader-layer-required`
- Requires destination color: `True`
- Requires layer composite: `True`
- Implicit destination read allowed: `False`
- CPU readback allowed: `False`

## BlendPlan Dump

- Kind: `ShaderLayerComposite`
- Status: `required-not-implemented`
- Fixed-function allowed: `False`
- Implicit destination read allowed: `False`
- Reason: The candidate computes its output from dstColor, so WebGPU cannot use a fixed-function blend state or silently read destination pixels. A future GPU pass must prove an explicit shader/layer composite BlendPlan with no CPU readback.

## Validation Rows

| ID | Status | Detail |
|---|---|---|
| `descriptor` | `pass` | runtime.invert_blender is descriptor-backed, kBlender, CPU-only, and has no WGSL id. |
| `cpu-fixture` | `pass` | SkRuntimeBlenderTest checks destination-color inversion. |
| `webgpu-boundary` | `pass` | WebGPU refuses fill and stroke runtime blender destination reads with stable diagnostics. |
| `blend-plan-gate` | `pass` | GPU pass remains forbidden until an explicit shader/layer composite BlendPlan exists. |

## Non-Claims

- No support for all blend modes.
- No GPU runtime blender support.
- No implicit destination read.
- No CPU readback fallback.
- No hidden layer compatibility path.
- No dynamic SkSL compilation.
- No SkSL IR or VM.
