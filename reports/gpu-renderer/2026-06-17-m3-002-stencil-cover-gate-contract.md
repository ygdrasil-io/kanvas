# KGPU-M3-002 Stencil-Cover Gate Contract

Date: 2026-06-17

## Summary

KGPU-M3-002 is closed as a contract-gate candidate after independent review,
not promoted native path coverage. The new `GPUStencilCoverGatePlanner` records a dumpable
`GPUStencilCoverPlan` only when adapter, depth/stencil, sample-count, target,
clip, stencil-state, producer-before-cover ordering, pass/resource, and
readback evidence labels are explicit. Missing native facts produce stable
skipped-lane refusals.

## Files

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/StencilCoverGatePlannerTest.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog/SceneEvidenceDiagnostics.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/catalog/SceneHumanDocumentation.kt`
- `.upstream/specs/gpu-renderer/tickets/M3-path-coverage-stroke-clip/KGPU-M3-002-add-stencil-cover-path-route-candidate.md`
- `.upstream/specs/gpu-renderer/tickets/M3-path-coverage-stroke-clip/README.md`
- `.upstream/specs/gpu-renderer/tickets/STATUS.md`

## Evidence

- Candidate dump line:
  `geometry:stencil-cover.candidate row=gpu-renderer.path.stencil-cover routeKind=GPUNative classification=TargetNative promoted=false`.
- Ordering dump line includes `atomic-group:path-stencil-cover:<path-key>`,
  `producer-before-cover`, and `atomic-no-interleave`.
- Refusals cover missing depth/stencil capability, illegal producer/cover
  ordering, unsupported target or clip facts, missing pass/resource evidence,
  missing readback evidence, and unsupported fill-rule facts.
- Candidate and skipped-lane dumps include explicit depth/stencil, sample-count,
  target, stencil-state, and clip labels.
- Scene diagnostics now report `pathStencilCoverTicketStatus=done` with
  `pathStencilCoverClosure=contract-gate-complete-no-product-promotion`.
- Independent review `019ed2a0-44e5-77d1-bae8-3b8e9926ffca` accepted the
  remediated evidence with no remaining Critical, Important, or Minor issues.

## Validation

```bash
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/cf31/kanvas/.gradle-codex ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverGatePlannerTest
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/cf31/kanvas/.gradle-codex ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.AtlasPolicyRefusalGateTest --tests org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverGatePlannerTest
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer-scenes:gpuRendererScenesCatalogReport
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.reports.SceneCatalogReportTest --tests org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest --tests org.graphiks.kanvas.gpu.renderer.scenes.windowed.RunGpuRendererSceneKadreMainTest
rtk env GRADLE_USER_HOME=/Users/chaos/.codex/gradle-cache ./gradlew --no-daemon -Dkotlin.compiler.execution.strategy=in-process :gpu-renderer:check
rtk git diff --check
rtk rg -n 'pathStencilCoverTicketStatus=(blocked|review)|pathStencilCoverClosure=contract-gate-implemented-awaiting-independent-review|^status: (blocked|proposed|review)$' .upstream/specs/gpu-renderer/tickets/M3-path-coverage-stroke-clip/KGPU-M3-002-add-stencil-cover-path-route-candidate.md gpu-renderer-scenes/src reports/gpu-renderer-scenes -S
rtk awk '/^status: / {count[$2]++} END {for (s in count) print s, count[s]}' .upstream/specs/gpu-renderer/tickets/M*-*/KGPU-*.md
```

All Gradle validation commands passed locally. `git diff --check` produced no
output, the stale active-status/scene-status search produced no matches,
and the ticket status count is now `done 31`, `blocked 15`.

## Non-Claims

- No native stencil-cover product support is promoted.
- No product route is activated.
- No release-blocking gate or readiness delta is claimed.
- KGPU-M3-001 prepared-path evidence does not count as stencil-cover support.
- No Graphite/Ganesh port, SkSL compiler, hidden CPU-rendered texture fallback,
  or broad Path AA support is implied.
