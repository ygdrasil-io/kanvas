# M12 - Performance and Telemetry

## Goal

Make font/text costs visible with deterministic telemetry before turning budgets into release gates.

## Dependencies

Subsystem contracts from M1 through M11.

## Exit Criteria

- [ ] Parser, scaler, shaping, paragraph, glyph cache, and GPU handoff metrics are emitted separately.
- [ ] Telemetry distinguishes CPU generation, cache behavior, and GPU upload costs.
- [ ] Dashboard trend warnings do not silently promote support claims.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M12-001 - Define font telemetry schema](KFONT-M12-001-define-font-telemetry-schema.md) | `done` | `P0` | `tracked-gap` | `telemetry` | `KFONT-M0-004` | - |
| [KFONT-M12-002 - Add parser and scaler metrics](KFONT-M12-002-add-parser-and-scaler-metrics.md) | `proposed` | `P1` | `tracked-gap` | `telemetry` | `KFONT-M12-001`, `KFONT-M2-004`, `KFONT-M3-005`, `KFONT-M4-004` | - |
| [KFONT-M12-003 - Add shaping and paragraph metrics](KFONT-M12-003-add-shaping-and-paragraph-metrics.md) | `proposed` | `P1` | `tracked-gap` | `telemetry` | `KFONT-M12-001`, `KFONT-M6-001`, `KFONT-M8-002` | `scaledemoji` |
| [KFONT-M12-004 - Add glyph artifact and cache metrics](KFONT-M12-004-add-glyph-artifact-and-cache-metrics.md) | `in-progress` | `P1` | `tracked-gap` | `telemetry` | `KFONT-M12-001`, `KFONT-M9-006`, `KFONT-M10-010` | `dftext` |
| [KFONT-M12-005 - Add GPU handoff metrics](KFONT-M12-005-add-gpu-handoff-metrics.md) | `proposed` | `P1` | `tracked-gap` | `telemetry` | `KFONT-M12-001`, `KFONT-M11-004`, `KFONT-M11-005` | `dftext` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test :font:sfnt:test :font:scaler:test :font:text:test :font:glyph:test :font:gpu-api:test
rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings pipelinePmBundle
```

## Non-Claims

- Indicative budgets are not blocking release gates until baselines exist.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
