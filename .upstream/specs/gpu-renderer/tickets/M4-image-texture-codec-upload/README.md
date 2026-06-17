# M4 - Image, Texture, Codec, And Upload

## Goal

Add image shader and texture upload routes with explicit ownership, codec,
sampler, color, and animation boundaries.

## Dependencies

Depends on M2 material/WGSL/resource foundations. Codec routes remain
dependency-gated until accepted codec descriptors and deterministic evidence
exist.

## Exit Criteria

- [ ] Image sources have dumpable texture ownership and upload plans.
- [ ] Codec provenance is visible and unsupported formats refuse stably.
- [ ] Sampler, tile, mip, color, and orientation facts are explicit.
- [ ] No arbitrary image/codec or CPU-rendered compatibility texture is hidden.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M4-001 - Add image shader route for already-decoded pixels](KGPU-M4-001-add-image-shader-route-for-already-decoded-pixels.md) | `done` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `images-textures` | `KGPU-M2-002` | `bitmap legacy` |
| [KGPU-M4-002 - Add uploaded texture artifact ownership gates](KGPU-M4-002-add-uploaded-texture-artifact-ownership-gates.md) | `done` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `resources-images` | `KGPU-M4-001` | - |
| [KGPU-M4-003 - Add codec provenance and dependency-gated refusals](KGPU-M4-003-add-codec-provenance-and-dependency-gated-refusals.md) | `done` | `P1` | `DependencyGated` | `RefuseDiagnostic` | `false` | `false` | `images-codecs` | `KGPU-M4-002` | `codec legacy` |
| [KGPU-M4-004 - Add sampler tile and mipmap boundary evidence](KGPU-M4-004-add-sampler-tile-and-mipmap-boundary-evidence.md) | `done` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `textures-samplers` | `KGPU-M4-001` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Image*' --tests '*Bitmap*'
```

## Non-Claims

- No broad codec, animation, mipmap, perspective sampling, or color-managed
  decode support.
- No implicit first-frame animation behavior.
- M4-001/M4-002 evidence is contract-only. It does not activate product image
  drawing, adapter-backed execution, cache residency claims, or CPU-rendered
  compatibility textures.
- M4-003 evidence is provenance/refusal-only. It does not implement codecs,
  accept decoded output, or promote uploaded-texture support from metadata.
- M4-004 is `done` as independently reviewed contract/refusal-only sampler
  boundary evidence. It does not promote native sampler execution, broad
  tile-mode support, mipmap support, or product activation.

## Current Evidence

- `DecodedImageShaderPreparedRouteTest` records one already decoded CPU pixel
  input as a bounded `CPUPreparedGPU` image shader contract route. Dumps expose
  `GPUImageSourceDescriptor`, `UploadedTextureArtifact`, texture/view/sampler,
  sampled binding, material-key boundary, route diagnostics, and stable
  non-claims. Material keys exclude upload artifact keys, pixel content hashes,
  row bytes, and resource handles. Upload artifact keys now include descriptor
  version, alpha type, color profile, orientation, conformance tier, budget
  class, generator version, and mip facts after independent review found the
  initial key under-specified.
- `UploadedTextureArtifactOwnershipGateTest` records uploaded-texture ownership
  gates for artifact type, artifact generation, required usage labels, device
  generation, active-attachment sampling, and descriptor compatibility before
  an `UploadFromArtifact` allocation plan can be returned. Dumps intentionally
  omit live resource debug labels and handles. The refusal matrix covers
  `artifactType != UploadedTextureArtifact` after independent review found that
  diagnostic unproven by tests.
- `CodecProvenanceDependencyGateTest` records codec registry/provenance dumps
  and `dependency.image.*` refusal diagnostics for missing, external, or
  nondeterministic codec capabilities. Decode output without provenance refuses
  explicitly.
- Independent review `019ec815-a637-7e92-baa9-24bd28b69904` rejected the first
  M4-001..003 pass because the upload artifact key lacked enough invalidation
  facts and the M4-002 missing-provenance diagnostic lacked fixture evidence.
  Both findings were remediated. Post-remediation independent review
  `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95` accepted KGPU-M4-001, KGPU-M4-002,
  and KGPU-M4-003 for `done` and confirmed no hidden activation, support-claim
  widening, package-cycle risk, material-key/resource-handle leak, or M4-004
  promotion.
- `ImageSamplerBoundaryGateTest` records the
  `gpu-renderer.sampler-boundary` row for deterministic sampler descriptor
  hashes, clamp tile/mip boundary dumps, sampler behavior keys, and pipeline
  keys that exclude ordinary sampler/resource/content facts. Refusal coverage
  includes `unsupported.texture.mipmap_unavailable`,
  `unsupported.image.sampling_cubic`,
  `unsupported.image.sampler_anisotropy`,
  `unsupported.image.sampling_anisotropic`,
  `unsupported.image.sampler_lod_clamp`, and
  `unsupported.image.perspective_sampling`.
- Independent review `019ed4c5-41ac-7f80-8c45-58ac57e4b08e` first found a
  prepared-image mip diagnostic mismatch with spec 22 and invalid sampler
  scalar gaps. After remediation, the review accepted KGPU-M4-004 for `done`.
  Native sampler/tile/mipmap product support still requires real
  WebGPU/adapter execution, reference/readback artifacts, and product
  activation review.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
