# GPU Renderer M4-004 Sampler Boundary Gate

Date: 2026-06-17
Branch: `codex/kgpu-m4-004-sampler-boundary`
Ticket: `KGPU-M4-004`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M4-004 | `done` | Added `GPUImageSamplerBoundaryPlanner`, extended dumpable sampler descriptors with LOD/compare/anisotropy/capability facts, and added `ImageSamplerBoundaryGateTest` coverage for sampler, tile, mip, key, and refusal dumps. | Accepted by independent review `019ed4c5-41ac-7f80-8c45-58ac57e4b08e`; native sampler execution/readback, broad tile-mode support, mipmap generation/sampling, product activation, and release readiness remain unpromoted. |

## Evidence

- `ImageSamplerBoundaryGateTest` records the
  `gpu-renderer.sampler-boundary` row with `routeKind=GPUNative`,
  `classification=TargetNative`, `promoted=false`, and
  `productActivation=false`.
- The accepted clamp/linear/no-mip fixture dumps a deterministic sampler
  descriptor hash, tile boundary facts, mip boundary facts, a sampler behavior
  key, and a pipeline key boundary.
- Sampler behavior keys include tile mode, filter mode, mipmap mode, LOD
  clamp, compare mode, anisotropy, and coordinate-transform class while
  excluding texture handles, upload artifact keys, pixel content, row bytes,
  and sampler objects.
- Pipeline keys include only binding layout, sample type, and
  coordinate-transform class. Tests prove changing ordinary sampler filter
  facts changes the sampler behavior key but not the pipeline key, and changed
  pixel content/row bytes do not enter either key.
- Unsupported mipmap, cubic, anisotropic, and perspective sampling refuse with
  `unsupported.texture.mipmap_unavailable`,
  `unsupported.image.sampling_cubic`,
  `unsupported.image.sampling_anisotropic`, and
  `unsupported.image.perspective_sampling`.
- Invalid scalar sampler facts refuse before accepted boundary dumps:
  `anisotropy < 1` uses `unsupported.image.sampler_anisotropy`, and
  nondeterministic or non-zero LOD clamps use
  `unsupported.image.sampler_lod_clamp`.
- `DecodedImageShaderPreparedRouteTest` preserves the spec 22 image-route
  mip diagnostic `unsupported.image.mip_required`, while the sampler/texture
  boundary uses spec 18 `unsupported.texture.mipmap_unavailable`.

## Validations

```bash
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.ImageSamplerBoundaryGateTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.resources.UploadedTextureArtifactOwnershipGateTest --tests org.graphiks.kanvas.gpu.renderer.images.CodecProvenanceDependencyGateTest
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk git diff --check
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
rtk ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.images.ImageSamplerBoundaryGateTest --tests org.graphiks.kanvas.gpu.renderer.images.DecodedImageShaderPreparedRouteTest
```

The targeted commands passed after the initial RED compile failure for the
missing sampler-boundary planner and sampling fields. The first full
`:gpu-renderer:check` pass found missing KDoc on the new public planner method;
the KDoc was added and the full check then passed. `rtk git diff --check`
passed. Before independent review acceptance, the catalog count was `done 31`,
`blocked 14`, and `review 1`.
After first independent review, the targeted sampler/image tests passed again
with the spec 22 mip diagnostic restored for prepared image routes and invalid
sampler scalar refusals added.

## Independent Review

Review `019ed4c5-41ac-7f80-8c45-58ac57e4b08e` found two issues before
acceptance:

- prepared image mip refusal had been changed away from the spec 22
  `unsupported.image.mip_required` diagnostic;
- accepted sampler-boundary evidence did not refuse invalid LOD clamp strings
  or `anisotropy < 1`.

Both findings were remediated. The follow-up review accepted KGPU-M4-004 for
`done`, with residual risk limited to the intended contract/refusal-only scope:
no adapter-backed native sampler execution, readback, broad tile support,
mipmaps, or product activation is proven.

After acceptance, the catalog count is 32 `done` and 14 `blocked`; no
`proposed`, `ready`, `in-progress`, or `review` GPU renderer tickets remain.

## Non-Claims

- No product route activation.
- No adapter-backed execution evidence.
- No native sampler support claim.
- No broad tile-mode support.
- No mipmap generation or mipmapped image sampling support.
- No perspective sampling support.
- No CPU-rendered compatibility texture.
- No release-blocking or readiness movement.
