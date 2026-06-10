# Runtime Child Shader Effect Lane

Derived evidence for KAN-030. The lane selects one CPU-backed child shader runtime effect and keeps WebGPU support refused until child bindings, WGSL layout, and resource axes have parser/reflection and render evidence.
Status counts: total-candidates=1; cpu-supported=1; gpu-supported=0; gpu-expected-unsupported=1; child-bindings=1; uniform-values-in-pipeline-key=0.

| Stable id | Descriptor | CPU | GPU | Child bindings | Fallback |
|---|---|---|---|---:|---|
| runtime.unsharp_rt | descriptor-backed | cpu-only | expected-unsupported | 1 | runtime-effect.child-binding-unsupported |

## Route

- Selected route: `webgpu.runtime-effect.child-shader.expected-unsupported`
- Fallback: `runtime-effect.child-binding-unsupported`
- Child `child`: index `0`, type `kShader`, binding `child[0]`, state `cpu-supported-gpu-unsupported`.

## Non-Claims
- No GPU child shader support claim.
- No dynamic SkSL compilation.
- No SkSL IR or VM.
- No arbitrary runtime-effect DAG support.
- No child shader texture/resource binding allocation claim.
- No uniform values in PipelineKey.
