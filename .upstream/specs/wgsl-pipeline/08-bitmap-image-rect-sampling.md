# Spec 08: Bitmap/ImageRect Sampling

Status: Accepted
Target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation

## Purpose

Define the accepted M32 behavior for bitmap and image-rect sampling on the
WebGPU backend.

This spec covers the MVP-visible scope that removed the inherited
`DrawBitmapRect3` and `DrawBitmapRectSkbug4734` GPU similarity regressions
without lowering similarity floors or bulk-rebaselining images.

## Acceptance Evidence

Accepted on 2026-05-27 with the following merged evidence:

| Ticket | PR | Merge commit | Evidence |
|---|---|---|---|
| GRA-94 | #1169 | `918643139025da8cbbc4735e45a0691b21598720` | Reproduced the four M32 image-rect failures and captured before artifacts. |
| GRA-95 | #1170 | `dcf38fb3805f29c71638524f85f279043acc0fe2` | Fixed strict-nearest image-rect sampling for `DrawBitmapRect3`. |
| GRA-96 | #1171 | `e1c1f0f08b0f3904ab0ee96bffeed5d440fde1bf` | Verified `DrawBitmapRectSkbug4734` at 100 percent similarity. |
| GRA-97 | #1172 | `488d06aacf8e0a94c49f97a43b1c30efef9ce597` | Added image-rect smoke promotion guardrails. |
| GRA-99 | #1173 | `f795ae11ece749cf84454394960167209192609a` | Promoted `DrawBitmapRectSkbug4734WebGpuTest` to required GPU smoke. |
| GRA-98 | #1174 | `3b1a119e7ababe9e375f9ff3aebe470a5fa23fe8` | Closed M32 with final PM evidence. |
| GRA-100 | #1175 | `e7fd596a832a0674d9caa34b410e01b3ab07165c` | Verified the non-M32 `SaveLayer kScreen` inventory exception was gone. |

Primary reports:

- `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-reproduction.md`
- `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md`
- `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md`
- `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion-guard.md`
- `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md`
- `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-closeout.md`
- `reports/wgsl-pipeline/2026-05-27-gra100-savelayer-kscreen-inventory.md`

## Accepted Behavior

The accepted M32 image-rect behavior is:

- strict source-rect constraints prevent guard-pixel bleed;
- strict nearest sampling uses integer texel loads that match the CPU path;
- strict linear sampling keeps the constrained tap behavior used by the CPU
  oracle;
- `DrawBitmapRect3` and `DrawBitmapRectSkbug4734` WebGPU outputs reach
  100 percent similarity in the final M32 evidence;
- no image-rect similarity floor was lowered;
- no image-rect failure was reclassified as expected unsupported.

## Smoke Boundary

The required GPU smoke boundary contains only the stable promoted image-rect
fixture:

- `org.skia.gpu.webgpu.DrawBitmapRectSkbug4734WebGpuTest`

The broader image-rect inventory remains useful for release audits, but it is
not all required smoke:

- `org.skia.gpu.webgpu.DrawBitmapRect3WebGpuTest`
- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRect3CrossBackendTest`
- `org.skia.gpu.webgpu.crossbackend.DrawBitmapRectSkbug4734CrossBackendTest`

Promotion remains governed by
`reports/wgsl-pipeline/2026-05-27-m31-gpu-smoke-promotion-policy.md`.

## Non-Goals

This spec does not:

- change color-space image-rect behavior beyond existing tested paths;
- accept lower similarity floors for bitmap/image-rect fixtures;
- promote all image-rect inventory tests into required smoke;
- change Geometry/Coverage ownership for future image-rect lowering work.

## Residual Risks

The final full GPU inventory still contains non-image-rect expected unsupported
records for coverage breadth and image-filter pre-pass gaps. Those are scoped
to M33 and M34, not M32.
