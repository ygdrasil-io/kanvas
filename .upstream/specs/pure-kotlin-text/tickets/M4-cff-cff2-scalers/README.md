# M4 - CFF and CFF2 Scalers

## Goal

Add bounded CFF/CFF2 parsing, Type 2 execution, subroutine safety, and deterministic path output.

## Dependencies

M2 parser facts and M3 variation/scaler foundations.

## Exit Criteria

- [ ] CFF INDEX/DICT and Type 2 execution have traces and diagnostics.
- [ ] CFF scaler path output is deterministic for supported fixtures.
- [ ] CFF2 variation behavior is either implemented with evidence or refused precisely.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M4-001 - Implement CFF INDEX and DICT parser](KFONT-M4-001-implement-cff-index-and-dict-parser.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M2-001`, `KFONT-M2-002`, `KFONT-M2-004` | - |
| [KFONT-M4-002 - Implement Type 2 charstring stack machine](KFONT-M4-002-implement-type-2-charstring-stack-machine.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M4-001` | - |
| [KFONT-M4-003 - Add CFF subroutine limits and diagnostics](KFONT-M4-003-add-cff-subroutine-limits-and-diagnostics.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M4-001`, `KFONT-M4-002` | - |
| [KFONT-M4-004 - Implement CFF scaler path output](KFONT-M4-004-implement-cff-scaler-path-output.md) | `proposed` | `P0` | `tracked-gap` | `font-scaler` | `KFONT-M4-002`, `KFONT-M4-003`, `KFONT-M2-004` | - |
| [KFONT-M4-005 - Implement CFF2 variation path output](KFONT-M4-005-implement-cff2-variation-path-output.md) | `proposed` | `P1` | `tracked-gap` | `font-scaler` | `KFONT-M4-004`, `KFONT-M2-004`, `KFONT-M3-002`, `KFONT-M3-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test
```

## Non-Claims

- Full hinting parity and GPU support are not claimed.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
