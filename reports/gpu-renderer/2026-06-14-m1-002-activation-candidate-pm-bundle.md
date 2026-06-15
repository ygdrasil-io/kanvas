# GPU Renderer M1-002 Activation-Candidate PM Bundle

Date: 2026-06-14
Ticket: `KGPU-M1-002`
Branch: `codex/gpu-renderer-m1-wave`

## Decision Scope

KGPU-M1-001 accepted launching the controlled M1 promotion path. This report
documents the follow-up PM packaging change for KGPU-M1-002. The root PM bundle
is now an activation candidate package, not a product route activation.

## Evidence

- Root PM manifest entry status is `ActivationCandidate`.
- Root PM manifest entry packaging state is `activation-candidate`.
- Root validation report status remains `Incomplete`.
- Adapter-backed evidence provenance is explicit:
  `opt-in-adapter-backed-r6-executed-diagnostic`.
- Adapter-backed evidence requirement is explicit:
  `required-before-product-activation`.
- `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0` remain required by validators.
- Promotion boundary report:
  `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`.

## Validations

```bash
rtk python3 scripts/test_validate_gpu_renderer_r6_pm_evidence_bundle.py
rtk python3 scripts/test_validate_gpu_renderer_r6_promotion_readiness_boundary.py
rtk ./gradlew --no-daemon pipelinePmBundle
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py .
rtk rg -n 'product_activation:[[:space:]]*true|productRouteActivated[=:][[:space:]]*true|Product route activated:[[:space:]]*`true`|releaseBlocking[=:][[:space:]]*true|Release blocking:[[:space:]]*`true`|readinessDelta[=:][[:space:]]*[1-9]|Readiness delta:[[:space:]]*`[1-9]|Product Activation[[:space:]]+[|][[:space:]]+`true`' .upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation reports/gpu-renderer/2026-06-14-m1-002-activation-candidate-pm-bundle.md reports/gpu-renderer/2026-06-14-m1-m2-ticket-wave.md reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md
rtk git diff --check
```

All commands above passed after RED/GREEN implementation of the M1-002
packaging contract. The targeted claim scan returned no matches.

## Non-Claims

- This is not product route activation.
- This does not add a product flag, default route, or rollback path.
- This does not make adapter-backed evidence a hidden root PM dependency.
- This does not make the route release-blocking.
- This does not move readiness; `readinessDelta` remains `0.0`.

## Remaining Gate

None for KGPU-M1-002. Independent review
`019ec714-40ab-73b1-a242-9dc36c3b2694` approved moving the ticket to `done`.
The next M1 gate is KGPU-M1-003: add the controlled first-route flag while
keeping it disabled by default and preserving legacy rollback behavior.
