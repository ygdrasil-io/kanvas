# M11 - Typed GPU Handoff

## Goal

Expose text artifacts to the GPU renderer through typed contracts, route diagnostics, WGSL validation, and no Skia-like leakage.

## Dependencies

M8 paragraph outputs, M9 glyph artifacts, and M10 color/emoji plans.

## Exit Criteria

- [ ] DrawTextRun and text artifact registry are immutable value-object contracts.
- [ ] Unsupported GPU routes refuse with dependency-gated diagnostics.
- [ ] A8 route and WGSL validation produce focused GPU evidence before any support promotion.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M11-001 - Align `font:gpu-api` with target artifact registry](KFONT-M11-001-align-font-gpu-api-with-target-artifact-registry.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M9-002`, `KFONT-M10-010` | `dftext`, `scaledemoji_rendering`, `coloremoji_blendmodes` |
| [KFONT-M11-002 - Add no-`Sk*` leakage validation](KFONT-M11-002-add-no-sk-leakage-validation.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-001` | - |
| [KFONT-M11-003 - Add normalized `DrawTextRun` contract](KFONT-M11-003-add-normalized-drawtextrun-contract.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M8-002`, `KFONT-M9-002`, `KFONT-M11-002` | - |
| [KFONT-M11-004 - Wire atlas A8 artifact route](KFONT-M11-004-wire-atlas-a8-artifact-route.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M9-003`, `KFONT-M9-005`, `KFONT-M11-003` | - |
| [KFONT-M11-005 - Wire dependency-gated diagnostics for unsupported routes](KFONT-M11-005-wire-dependency-gated-diagnostics-for-unsupported-routes.md) | `proposed` | `P0` | `DependencyGated` | `gpu-api` | `KFONT-M11-001`, `KFONT-M11-003` | `dftext`, `scaledemoji_rendering`, `coloremoji_blendmodes` |
| [KFONT-M11-006 - Add `GPUTextSubRunPlan` splitting tests](KFONT-M11-006-add-gputextsubrunplan-splitting-tests.md) | `proposed` | `P1` | `GPU-gated` | `gpu-api` | `KFONT-M11-003`, `KFONT-M11-004`, `KFONT-M11-005` | - |
| [KFONT-M11-007 - Add resource/upload/instance/binding plan contracts](KFONT-M11-007-add-resource-upload-instance-binding-plan-contracts.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-006` | `dftext` |
| [KFONT-M11-008 - Add upload-before-sample ordering validation](KFONT-M11-008-add-upload-before-sample-ordering-validation.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-004`, `KFONT-M11-007` | `dftext` |
| [KFONT-M11-009 - Add WGSL parser/reflection validation for text routes](KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-004`, `KFONT-M11-007` | `dftext`, `coloremoji_blendmodes` |
| [KFONT-M11-010 - Add `MaterialKey` leakage tests](KFONT-M11-010-add-materialkey-leakage-tests.md) | `proposed` | `P0` | `GPU-gated` | `gpu-api` | `KFONT-M11-006`, `KFONT-M11-007` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test
```

## Non-Claims

- Registration or refusal rows do not claim GPU support without adapter-backed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
