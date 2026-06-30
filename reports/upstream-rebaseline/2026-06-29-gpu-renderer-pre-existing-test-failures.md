# Pre-existing gpu-renderer test failures inherited from master merge

Date: 2026-06-29
Branch: `noble-narwhal` (also present on `origin/master`, 27e162a4)

## Context

During M34-002 color font pipeline implementation, a merge of `origin/master`
into `noble-narwhal` was necessary to resolve a pre-existing `:gpu-renderer`
compile failure (missing `GPUColorWgsl.kt` integration). The merge brought in
additional test failures, all proven present on `origin/master` and unrelated
to M34-002.

## Failing Tests (4, stable across all M34 Plan 1–3c regressions)

| # | Test | File:Line | Cause |
|---|---|---|---|
| 1 | `GPURendererLayoutSurfaceTest > main scaffold declarations are documented` | `GPURendererLayoutSurfaceTest.kt:26` | `supportsRectOnlyOffscreen`-introduced filter files (`BlurFilter.kt`, `GPULighting.kt`, `GPUDropShadow.kt`) lack KDoc |
| 2 | `GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules` | `GPURendererPackageBoundaryTest.kt:53` | `gpu-renderer/.../wgsl/WgslReflection.kt` imports `org.graphiks.wgsl.proc` (crosses canonical package root) |
| 3 | `GPUSeparableBlurTest > quality tier HIGH sigma=10 tap count matches spec ceil sigma 3 2 plus 1` | `GPUSeparableBlurTest.kt:94` | Merge-introduced blur filter; tap-count spec mismatch |
| 4 | `GaussianBlurFilterTest > small sigma produces one tap` | `GaussianBlurFilterTest.kt:44` | same blur integration as #3 |

All 4 failures are **present on `origin/master`** (verified: `git show origin/master:...` confirms the violating code exists upstream). Total test count is 1077; 1073 pass + 4 pre-existing failures. All M34-002 color/text/scene tests pass (0 failures introduced by Plans 1–3c).

## Recommended Follow-Up

Separate tickets should address each failure (likely in the owning milestone's backlog):
- #1: add KDoc to the three filter files (trivial doc fix).
- #2: evaluate `WgslReflection.kt`'s `org.graphiks.wgsl.proc` dependency against the package-boundary policy.
- #3, #4: investigate whether the blur test assertions match the current blur implementation spec.

None of these are M34-002 scope. M34's acceptance is verified independently:
- `GPUColorGlyphRenderSmokeTest` — ran on GPU, passed (skipped=0).
- `:gpu-renderer:test --tests '*Color*' --tests '*GPUColorGlyph*'` — all pass.
