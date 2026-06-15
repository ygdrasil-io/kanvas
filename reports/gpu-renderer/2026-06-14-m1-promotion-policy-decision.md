# GPU Renderer M1 Promotion Policy Decision

Date: 2026-06-14
Branch: `codex/gpu-renderer-m1-wave`
Decision scope: KGPU-M1-001 product/release policy gate for the first solid
`FillRect` route.

## Decision

The M1 controlled promotion path is accepted in the current state. The user
confirmed that the independent review already performed is sufficient for now,
and that the same independent-review gate must be repeated for each future
milestone before moving milestone evidence to accepted status.

This closes the policy blocker only. It does not enable product routing,
change default rendering behavior, mark the route release-blocking, or move any
readiness denominator.

## Evidence Basis

- M0 R6 evidence review tickets are `done`.
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md` records
  `Executed diagnostic promotion gate passed: true`.
- The same boundary report records `Product route activated: false`,
  `Release blocking: false`, `Readiness delta: 0.0`, and
  `Promotion decision required: true`; this report supplies that decision.
- Independent review accepted the M1/M2 wave status and found no blocking
  claim/status issue.

## Policy For This Promotion Wave

- KGPU-M1-001 may move to `done` because the activation policy decision is now
  explicit and reviewed.
- KGPU-M1-002 may move to `ready`; it must update PM packaging before any
  activation-candidate wording is claimed.
- KGPU-M1-003 remains blocked until KGPU-M1-002 lands. The first-route product
  flag must stay off by default and scoped to solid `FillRect`.
- KGPU-M1-004 remains blocked until the controlled flag exists and rollback /
  parity validation can exercise a candidate route.
- Independent review is required at each milestone boundary. For now it is the
  acceptance gate, but the review must name the evidence class and explicit
  non-claims when adapter-backed evidence is absent, skipped, or opt-in only.

## Non-Claims

- No `gpu-raster` route was enabled.
- No product flag, default route, rollback route, or release-blocking gate was
  added by this decision.
- No broad rrect, gradient, path, image, text, filter, saveLayer,
  runtime-effect, or Skia parity support is implied.
- Adapter-backed evidence remains explicit and opt-in unless a later ticket
  promotes it into PM packaging or release policy.

## Validation

```bash
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk ./gradlew --no-daemon pipelinePmBundle --dry-run
rtk git diff --check
```

Validation status: passed on 2026-06-14.

Fresh validation result:

- `validateGpuRendererR6AdapterBackedPromotionReadinessBoundary` passed with
  `classification=promotion-boundary-held`, `rootStatus=Incomplete`,
  `executedStatus=Passed`, and `productRouteActivated=False`.
- `pipelinePmBundle --dry-run` passed; no PM bundle generation dependency was
  added by this decision record.
- `git diff --check` passed.
