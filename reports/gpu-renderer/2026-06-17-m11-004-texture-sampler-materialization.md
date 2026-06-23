# GPU Renderer M11-004 Texture/Sampler Materialization

Date: 2026-06-17
Branch: `codex/kgpu-m11-004-texture-sampler-materialization`
Ticket: `KGPU-M11-004`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M11-004 | `done` | Added provider-owned texture, texture-view, and sampler operand materialization from accepted uploaded texture ownership plans; bridged paired sampled texture/sampler facts through payload bind groups; preserved stable refusal diagnostics and skipped-readback evidence. | Product route activation, broad image/codec delivery, mipmap generation/sampling, native adapter readback success, and release readiness remain unpromoted. |

## Evidence

- `GPUTextureSamplerMaterializationRequest` carries ownership, allocation,
  texture descriptor, view descriptor, sampler descriptor, binding layout,
  usage, generation, upload, swizzle, and sampler-boundary facts without raw
  backend handles.
- `ValidatingTextureSamplerResourceProvider` returns provider-owned
  `Texture`, `TextureView`, and `Sampler` command operands for accepted
  uploaded texture plans. Dumps include `allocation=UploadFromArtifact`,
  `uploadBeforeSample=true`, descriptor facts, generation facts, and
  `cpuRenderedCompatTexture=false`.
- `ValidatingPayloadResourceProvider` now turns paired sampled texture/sampler
  `GPUResourceBindingFact` rows into `TextureView` and `Sampler` bind-group
  operands while keeping the bind group, resource descriptors, and structured
  facts dump-safe. Pairing is exact by texture/sampler binding key; mismatched
  pairs, undeclared structured binding facts, and multiple textures sharing one
  sampler refuse.
- Refusal fixtures cover missing texture usage, stale device/resource
  generation, unavailable mip levels, swizzle requirements, unsupported sampler
  modes, upload/allocation failure, active attachment sampling, and missing
  sampled-texture sampler pairs.
- `GPUTextureAllocationPlan.UploadFromArtifact` rejects handle-like artifact
  keys before they can be copied into refusal diagnostic facts.
- Adapter-backed readback is explicit skipped evidence:
  `failureReason=kgpu-m11-004.adapter-readback-not-promoted`.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.GPUTextureSamplerMaterializationProviderTest --tests org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationProviderTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Non-Claims

- No product route activation.
- No broad image, codec, animation, mipmap, perspective, or color-managed
  decode support.
- No successful adapter-backed sampled texture readback claim.
- No CPU-rendered compatibility texture.
- No Graphite, Ganesh, Dawn C++, SkSL compiler, SkSL IR, or SkSL VM port.
