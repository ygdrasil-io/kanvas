# GPU Renderer M3-004 Bounded Clip Prepared Route

Date: 2026-06-14
Branch: `codex/gpu-renderer-m3-clip-prepared`
Base: stacked on `codex/gpu-renderer-m3-stroke-prepared`, which is stacked on
`codex/gpu-renderer-m3-atlas-refusal`.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M3-004 | `done` | Added `GPUBoundedClipPreparedPlanner`, ordered `GPUClipElementDescriptor` facts, stable `GPUClipOrderingToken`, `CPUPreparedGPU` route kind, typed `CoverageMaskArtifact` mask plan with `NoAtlas` evidence, deterministic hex-encoded content-specific artifact key excluding handles, and stable refusal dumps. | Accepted by final independent review `019ec7fe-3b8a-77b1-bc93-e9f75f6965b7`; no product clipping, adapter-backed execution, arbitrary clip-stack support, stencil coverage, atlas generation, shader-clip support, or CPU-rendered clipped layer fallback is implied. |

## Evidence

- Accepted bounded rrect+path intersect clip emits:
  `clip:prepared routeKind=CPUPreparedGPU strategy=coverage-mask.standalone consumer=clip-mask.sample`.
- Artifact evidence uses
  `coverage.clip.stack_m3_clip.gen7.<hex-encoded stack and element content>.elements2`
  and keeps lifetime/budget facts as `recording-local` and `clip-bounded`.
- Accepted clip contents are part of the prepared key; changing the path
  `shapeKey`, path bounds, path `fillRule`, separator spelling in canonical
  shape keys, or stack bounds produces a distinct mask artifact key.
- Ordering evidence records
  `clip-order.stack_m3_clip.gen7.elements2` and ordered element descriptors for
  rrect then path.
- Mask evidence records `CoverageMaskArtifact`, `sampling=nearest`, and
  `atlasPolicy=NoAtlas`; it does not allocate or claim atlas residency.
- Refusal coverage includes difference operation, inverse fill, unregistered
  shader clip, mask budget overflow, nondeterministic rrect and path keys,
  shape/key mismatch, and unbounded stack bounds.
- Dumps include:
  `nonclaim:no-product-activation no-adapter-backed-execution no-arbitrary-clip-stack no-stencil-coverage no-atlas-generation no-clip-shader no-cpu-rendered-clipped-layer`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.clips.BoundedClipPreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.clips.BoundedClipPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.AtlasPolicyRefusalGateTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

The targeted test failed first on unresolved `GPUBoundedClipPreparedPlanner`,
`GPUClipElementDescriptor`, mask plan facts, route kind, and dump support. A
second targeted RED covered nondeterministic rrect keys before the canonical key
predicate was fixed. Independent review
`019ec7f0-a288-76f0-b573-a31d94fe8ca7` then found two blockers: the mask
artifact key did not include enough content facts, and shape keys were not tied
to `shapeKind`. Remediating tests failed first on old key/collision behavior
and accepted shape/key mismatch, then passed after adding content-changing
element facts to the key and validating key prefixes. Post-remediation review
`019ec7f6-e111-7a32-9883-06d8e174c429` then found two remaining key issues:
`fillRule` was missing, and lossy sanitation could collapse distinct canonical
shape keys. Remediating tests failed first on those collisions, then passed
after hex-encoding accepted stack/element content facts in the mask key.
Final independent review `019ec7fe-3b8a-77b1-bc93-e9f75f6965b7` accepted the
evidence for `done`.

## Non-Claims

- No product route activation.
- No adapter-backed execution evidence.
- No arbitrary clip-stack support.
- No stencil coverage, atlas generation, or shader-clip support.
- No CPU-rendered clipped draw/layer/scene texture fallback.
