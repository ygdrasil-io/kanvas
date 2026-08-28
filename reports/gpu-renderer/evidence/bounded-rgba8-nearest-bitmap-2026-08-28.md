# Bounded RGBA8 nearest bitmap — 2026-08-28

## Scope

This promotion covers one immutable, known-pixel in-memory RGBA8 bitmap drawn
through the public Kanvas `Surface` API at an integer destination rectangle.
The WebGPU path uploads one native texture and samples it with the existing
nearest/clamp sampler under opaque `SrcOver`. Integer Canvas translations are
folded into the normalized destination rectangle, so native image dispatch
receives an identity transform.

It does not claim a codec path, generic image fallback, linear/cubic/mipmap or
anisotropic filtering, non-integer/affine transforms, MSAA, destination reads,
or broad image shader support.

## Evidence

`bounded-rgba8-nearest-bitmap` is a public Surface program with a hand-written
CPU nearest-sampling oracle. The promoted bundle records 100% similarity, zero
different pixels, one submission, one uploaded texture, and zero destination
copies/readback snapshots:

- `correctness/promoted/bounded-rgba8-nearest-bitmap/cpu.png`
- `correctness/promoted/bounded-rgba8-nearest-bitmap/gpu.png`
- `correctness/promoted/bounded-rgba8-nearest-bitmap/diff.png`
- `correctness/promoted/bounded-rgba8-nearest-bitmap/{manifest,route,stats,diagnostics,verdict}.json`

`bounded-bitmap-linear-refusal` retains the negative boundary. It returns
`unsupported.image.sampling_filter` with zero submissions, created textures,
or destination reads. The same lowerer rejects unsupported affine transforms
with `unsupported.image.affine_sampling` before native image submission.

Both bundles were promoted from source commit
`1d2c1d1d718c95e8f685d89d271c1ee23015caa6`.

## Reproduction

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:generateGpuEvidence \
  -PsourceCommit=1d2c1d1d718c95e8f685d89d271c1ee23015caa6 \
  -PscenesFile=/absolute/path/to/task-14-scenes.txt
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```
