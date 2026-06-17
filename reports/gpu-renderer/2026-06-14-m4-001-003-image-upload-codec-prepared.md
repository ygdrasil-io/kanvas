# GPU Renderer M4-001..003 Image, Upload, And Codec Gates

Date: 2026-06-14
Branch: `codex/gpu-renderer-m4-image-prepared`
Base: stacked on `codex/gpu-renderer-m3-clip-prepared`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M4-001 | `done` | Added `GPUDecodedImageShaderPreparedPlanner`, `GPUDecodedImagePixelsDescriptor`, and bounded decoded-pixel image shader contract evidence with `CPUPreparedGPU`, `UploadedTextureArtifact`, texture/view/sampler/binding, material-key exclusion, and refusal dumps. | Accepted by post-remediation independent review `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95`; no product image drawing, adapter-backed execution, codec support, mipmaps, broad image support, or CPU-rendered compatibility texture is implied. |
| KGPU-M4-002 | `done` | Added `GPUUploadedTextureArtifactOwnershipGate` resource evidence for artifact type, generation, required usage labels, device generation, active-attachment sampling, descriptor compatibility, and `UploadFromArtifact` allocation planning. | Accepted by post-remediation independent review `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95`; no WebGPU allocation/upload, cache residency claim, product route activation, or live-handle evidence is implied. |
| KGPU-M4-003 | `done` | Added `GPUImageCodecRegistrySnapshot` provenance/refusal evidence with dumpable codec descriptor facts and `dependency.image.*` diagnostics. | Accepted by post-remediation independent review `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95`; no codec implementation, decode output acceptance, platform decoder substitute, or uploaded-texture route from metadata is implied. |
| KGPU-M4-004 | `done` | Follow-up evidence added in `reports/gpu-renderer/2026-06-17-m4-004-sampler-boundary-gate.md`: deterministic sampler-boundary dumps, sampler behavior keys, pipeline-key boundary evidence, and stable mip/cubic/aniso/perspective/scalar refusals. | Accepted by independent review `019ed4c5-41ac-7f80-8c45-58ac57e4b08e`; native sampler/tile/mipmap execution, broad support, product activation, and readback/reference promotion remain unclaimed. |

## Evidence

- `DecodedImageShaderPreparedRouteTest` records one already decoded CPU pixel
  source as `image:decoded.prepared routeKind=CPUPreparedGPU` with a sampled
  texture binding, deterministic artifact key, texture/view/sampler facts, and
  material key boundary:
  `excludes=upload-artifact-key,pixel-content,row-bytes,resource-handle`.
- M4-001 refusal coverage includes invalid source descriptors, unsupported
  pixel format, row stride, unapplied orientation, nondeterministic artifact
  key facts, upload budget overflow, unsupported tile modes, mip requirements,
  and unsupported sampling filters.
- Independent review `019ec815-a637-7e92-baa9-24bd28b69904` found the initial
  M4-001 upload artifact key under-specified. Remediation added key facts for
  descriptor version, alpha type, color-profile label, orientation state,
  conformance tier, budget class, generator version, and mip policy. Targeted
  RED/GREEN coverage proves alpha/color changes derive distinct artifact keys
  while the material key still excludes upload/content facts.
- `UploadedTextureArtifactOwnershipGateTest` records accepted ownership as
  `texture:uploaded-artifact.accepted routeKind=CPUPreparedGPU` with
  `UploadFromArtifact` allocation planning after artifact, usage, generation,
  active-attachment, and descriptor gates pass.
- M4-002 refusal coverage includes stale artifact generation, missing usage,
  stale device generation, active attachment sampling, and descriptor mismatch.
  Dumps exclude `debugLiveResourceLabel` and live handle text.
- The same independent review found the missing-provenance diagnostic unproven
  by fixture. Remediation added a refusal case for
  `artifactType != UploadedTextureArtifact`.
- `CodecProvenanceDependencyGateTest` records a dumpable codec registry
  snapshot and `DependencyGated` provenance rows for descriptor-only planned
  codecs. Unsupported codec cases refuse with `dependency.image.codec.*`
  diagnostics, and decoded output without registry provenance refuses with
  `dependency.image.decode.provenance_missing`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.CodecProvenanceDependencyGateTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest --tests org.graphiks.kanvas.gpu.renderer.images.CodecProvenanceDependencyGateTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest --tests org.graphiks.kanvas.gpu.renderer.images.CodecProvenanceDependencyGateTest --tests org.graphiks.kanvas.gpu.renderer.GPURendererPackageBoundaryTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

The targeted tests failed first on missing M4-001/M4-002/M4-003 contracts, then
passed after the minimal contract implementations. A first full
`:gpu-renderer:check` run exposed a package cycle caused by `resources`
importing `images`; the resource gate was corrected to consume a local
`GPUUploadedTextureArtifactFacts` snapshot instead. The package-boundary test,
full `:gpu-renderer:check`, and `rtk git diff --check` then passed
sequentially.

Independent review `019ec815-a637-7e92-baa9-24bd28b69904` rejected the first
pass with the two findings above. Post-remediation validation passed, and
independent review `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95` accepted
KGPU-M4-001, KGPU-M4-002, and KGPU-M4-003 for `done`.

## Non-Claims

- No product route activation.
- No adapter-backed execution evidence.
- No broad image, codec, animation, mipmap, perspective sampling, or
  color-managed decode support.
- No implicit first-frame animation behavior.
- No hidden CPU-rendered draw/layer/filter/scene compatibility texture.
- No native sampler/tile/mipmap support claim for KGPU-M4-004; the follow-up
  sampler-boundary evidence is independently reviewed contract/refusal-only
  evidence.
