# GPU Renderer M3-005 Atlas Refusal Policy Gates

Date: 2026-06-14
Branch: `codex/gpu-renderer-m3-atlas-refusal`
Base: stacked on `codex/gpu-renderer-m3-path-prepared`, which is stacked on
`codex/gpu-renderer-m1-wave`.

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M3-005 | `done` | Added `GPUAtlasPolicyRefusalGate`, `GPUAtlasPolicyRequest`, `GPUAtlasDiagnostic`, and deterministic refusal dumps for path/coverage atlas policy gates. Independent review `019ec7d2-85bf-7d90-977d-8c7ee86f2710` accepted the evidence with no findings. | Future atlas promotion still requires real key, budget, generation, eviction, synchronization, upload-before-sample, GPU sampling, and visual evidence; no atlas generation, path atlas support, coverage atlas support, selector-only support, product activation, or hidden CPU-rendered texture fallback is implied. |

## Evidence

- Path atlas selector-only evidence refuses with
  `unsupported.atlas.policy_unavailable` and `RefuseRequired`.
- Coverage atlas routes missing upload or synchronization facts refuse with
  `unsupported.atlas.sync_unavailable`.
- Nondeterministic content keys refuse with
  `unsupported.atlas.key_nondeterministic` without dumping raw handles or
  pointer-like values.
- Required fact dumps list budget, generation, eviction, use-token, mutation,
  upload-before-sample, synchronization, and GPU sampling evidence gates before
  future promotion.
- Dumps include:
  `atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support no-selector-only-support no-hidden-cpu-texture-fallback`.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.AtlasPolicyRefusalGateTest
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.GPURendererLayoutSurfaceTest."main scaffold declarations are documented"
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

The targeted test passed after RED/GREEN implementation. `:gpu-renderer:check`
and `rtk git diff --check` passed after adding required KDoc for the new public
contract functions.

## Non-Claims

- No atlas generation implementation.
- No path atlas or coverage atlas support promotion.
- No product route activation.
- No adapter-backed execution evidence.
- No selector-only evidence counted as support.
- No hidden CPU-rendered draw/layer/scene texture fallback.
