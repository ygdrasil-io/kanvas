# M7 - Fallback and System Fonts

## Goal

Make fallback deterministic, traceable, variation-aware, cluster-safe, and explicit about host-dependent system scans.

## Dependencies

M1 identities, M5 cluster boundaries, and M6 shaping contract.

## Exit Criteria

- [x] Bundled fallback catalog is deterministic.
- [x] Every fallback choice or refusal has a trace.
- [x] Host system scans are marked non-normative unless captured as fixtures.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M7-001 - Add bundled deterministic font catalog](KFONT-M7-001-add-bundled-deterministic-font-catalog.md) | `done` | `P0` | `tracked-gap` | `fallback` | `KFONT-M1-003`, `KFONT-M1-004`, `KFONT-M2-004` | - |
| [KFONT-M7-002 - Add fallback decision trace](KFONT-M7-002-add-fallback-decision-trace.md) | `done` | `P0` | `tracked-gap` | `fallback` | `KFONT-M7-001`, `KFONT-M6-001` | - |
| [KFONT-M7-003 - Add variable-axis-aware fallback](KFONT-M7-003-add-variable-axis-aware-fallback.md) | `done` | `P1` | `tracked-gap` | `fallback` | `KFONT-M7-001`, `KFONT-M7-002`, `KFONT-M3-003`, `KFONT-M4-005` | - |
| [KFONT-M7-004 - Add cluster-safe fallback segmentation tests](KFONT-M7-004-add-cluster-safe-fallback-segmentation-tests.md) | `done` | `P0` | `fixture-gated` | `fallback` | `KFONT-M5-005`, `KFONT-M7-002`, `KFONT-M6-001` | `scaledemoji` |
| [KFONT-M7-005 - Add host-dependent system scan diagnostics](KFONT-M7-005-add-host-dependent-system-scan-diagnostics.md) | `done` | `P1` | `tracked-gap` | `fallback` | `KFONT-M1-001`, `KFONT-M1-003`, `KFONT-M7-001`, `KFONT-M7-002` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FontCatalog*' --tests '*FallbackDecision*' --tests '*VariableFallback*'
rtk ./gradlew --no-daemon :font:text:test --tests '*FallbackSegmentation*' --tests '*VariableFallback*' --tests '*SystemFont*' --tests '*HostDependent*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
```

## Non-Claims

- Fallback does not imply color emoji rendering or platform font engine support.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
