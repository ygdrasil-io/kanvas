# Pure Kotlin Font Tickets Status

All tickets start as `proposed`. Move a ticket to `ready` only after its scope, spec sources, evidence, fallback/refusal behavior, and validation command have been reviewed.

| Milestone | Proposed | Ready | In Progress | Blocked | Review | Done |
|---|---:|---:|---:|---:|---:|---:|
| M0 | 0 | 0 | 0 | 0 | 0 | 5 |
| M1 | 0 | 0 | 0 | 0 | 0 | 4 |
| M2 | 0 | 0 | 0 | 0 | 0 | 5 |
| M3 | 0 | 0 | 0 | 0 | 0 | 5 |
| M4 | 0 | 0 | 0 | 0 | 0 | 5 |
| M5 | 0 | 0 | 0 | 0 | 0 | 5 |
| M6 | 0 | 0 | 0 | 5 | 0 | 5 |
| M7 | 0 | 0 | 0 | 0 | 0 | 5 |
| M8 | 0 | 0 | 0 | 1 | 0 | 5 |
| M9 | 0 | 0 | 0 | 0 | 0 | 6 |
| M10 | 0 | 0 | 0 | 0 | 0 | 10 |
| M11 | 0 | 0 | 0 | 0 | 0 | 10 |
| M12 | 0 | 0 | 0 | 0 | 0 | 5 |
| M13 | 0 | 0 | 0 | 4 | 0 | 1 |
| **Total** | **0** | **0** | **0** | **10** | **0** | **76** |

2026-06-23 note: M6 counts are unchanged, but the still-missing named fixture
resources for `KFONT-M6-007` through `KFONT-M6-010` are now checked in under
reviewed provenance; those tickets remain `blocked` on refreshed runtime,
trace, and refusal-diagnostic evidence.

2026-06-23 update: `KFONT-M11-010` is now `done` on deterministic
`MaterialKey` leakage validation evidence. All 10 M11 tickets are now
`done`.

## Status Update Rule

When a ticket status changes, update:

- the ticket front matter;
- the ticket `Status Notes`;
- the owning milestone `README.md` table;
- this summary table.
