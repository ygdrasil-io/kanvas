# M67 Family Performance Budgets

Status: mixed candidate/reporting-only budget inventory.

## Budgets

| Family | Tier | Status | Measured | Lane | Metric | Reason |
|---|---|---|---|---|---|---|
| core paint/blend | P0 pipeline | `candidate` | True | `frame.headless-webgpu` | 9.2756 / 12.2369 ms | M67 candidate from M65 headless/offscreen smoke telemetry. |
| runtime effect | P1 family | `reporting-only` | False | `frame.headless-webgpu` | not measured | M65 times a synthetic preview slot, but runtime display-list replay is refused; do not count as runtime-effect performance support. |
| path/clip | P1 family | `reporting-only` | False | `family.path-clip` | not measured | No isolated M67 Path/clip timing payload exists yet; do not count the baseline transform smoke as a Path/clip measurement. |
| image/bitmap | P1 family | `reporting-only` | False | `family.image-bitmap` | not measured | No isolated M67 bitmap sampling or upload timing payload exists yet. |
| image-filter DAG | P1 family | `reporting-only` | False | `family.image-filter-dag` | not measured | No M67 measured intermediate texture payload exists yet; do not count as measured. |
| text/glyph atlas | P1 family | `reporting-only` | False | `family.text-glyph-atlas` | not measured | M65 does not render text, so glyph atlas misses remain schema/planning evidence only. |
| native frame loop | P0 frame | `reporting-only` | False | `frame.kadre-windowed` | not measured | Native Kadre timing remains outside M67 unless a real Kadre-hosted frame loop is measured. |

## Non-Claims

- Reporting-only rows are not counted as measured performance evidence.
- The native frame loop remains `frame.kadre-windowed` reporting-only for M67.
- Family budgets define what will become measurable; they do not promote unsupported rendering features.
