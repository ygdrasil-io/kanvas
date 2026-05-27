# M32 Sprint Report

Date: 2026-05-27
Milestone: M32 -- Bitmap/ImageRect GPU Similarity Remediation

## Summary

M32 is complete. The bitmap/image-rect GPU similarity regressions inherited
from the full inventory are fixed, verified, and closed with PM evidence.

Final M32 state:

- `DrawBitmapRect3WebGpuTest`: fixed to 100 percent similarity.
- `DrawBitmapRect3CrossBackendTest`: fixed to 100 percent similarity on the
  GPU lane.
- `DrawBitmapRectSkbug4734WebGpuTest`: fixed to 100 percent similarity and
  promoted to required GPU smoke.
- `DrawBitmapRectSkbug4734CrossBackendTest`: fixed to 100 percent similarity
  on the GPU lane.
- No similarity floor was lowered.
- No image-rect failure was reclassified as expected unsupported.

## Completed Tickets

| Ticket | Outcome |
|---|---|
| GRA-93 | Parent M32 epic closed. |
| GRA-94 | Reproduced and artifacted original image-rect failures. |
| GRA-95 | Fixed strict-nearest `DrawBitmapRect3` sampling. |
| GRA-96 | Verified `DrawBitmapRectSkbug4734` resolution. |
| GRA-97 | Added image-rect smoke promotion guard. |
| GRA-99 | Promoted `DrawBitmapRectSkbug4734WebGpuTest` to required smoke. |
| GRA-98 | Closed M32 with final report. |
| GRA-100 | Verified the separate `SaveLayer kScreen` inventory exception resolved. |

## PRs

| Ticket | PR | Merge commit |
|---|---|---|
| GRA-94 | https://github.com/ygdrasil-io/kanvas/pull/1169 | `918643139025da8cbbc4735e45a0691b21598720` |
| GRA-95 | https://github.com/ygdrasil-io/kanvas/pull/1170 | `dcf38fb3805f29c71638524f85f279043acc0fe2` |
| GRA-96 | https://github.com/ygdrasil-io/kanvas/pull/1171 | `e1c1f0f08b0f3904ab0ee96bffeed5d440fde1bf` |
| GRA-97 | https://github.com/ygdrasil-io/kanvas/pull/1172 | `488d06aacf8e0a94c49f97a43b1c30efef9ce597` |
| GRA-99 | https://github.com/ygdrasil-io/kanvas/pull/1173 | `f795ae11ece749cf84454394960167209192609a` |
| GRA-98 | https://github.com/ygdrasil-io/kanvas/pull/1174 | `3b1a119e7ababe9e375f9ff3aebe470a5fa23fe8` |
| GRA-100 | https://github.com/ygdrasil-io/kanvas/pull/1175 | `e7fd596a832a0674d9caa34b410e01b3ab07165c` |

## Evidence

Primary closeout:

- `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-closeout.md`

Supporting reports:

- `reports/wgsl-pipeline/2026-05-27-m32-bitmap-imagerect-reproduction.md`
- `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect3-strict-nearest-fix.md`
- `reports/wgsl-pipeline/2026-05-27-m32-drawbitmaprect-skbug4734-resolution.md`
- `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion-guard.md`
- `reports/wgsl-pipeline/2026-05-27-m32-image-rect-smoke-promotion.md`
- `reports/wgsl-pipeline/2026-05-27-gra100-savelayer-kscreen-inventory.md`

## Handoff To M33-M35

M33 owns Path AA inventory breadth and `coverage.edge-count-exceeded`.
M34 owns the image-filter MVP lane and `Crop(input = nonNull)` pre-pass
decision. M35 owns the final release-readiness gate and PM evidence package.

M32 does not leave any unresolved bitmap/image-rect similarity regression.
