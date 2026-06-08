# M89 GM Registry PM Counters

Status: generated evidence

This PM report derives counters from the existing M89 GM registry. It does not mutate registry rows, promote support, change thresholds, change edge budgets, or add rendering support claims.

## Counter Summary

- Total rows: `47`
- Support claims: `22`
- Policy-only rows: `20`
- Expected unsupported with fallback: `25`
- Unlinked unsupported rows: `0`
- Linked M66 rows: `18`
- Linked M86 rows: `18`
- Linked M90 rows: `9`

## Status Counts

- `expected-unsupported`: `25`
- `pass`: `22`

## Family/Status Matrix

| Family | expected-unsupported | pass |
| --- | --- | --- |
| bitmap-image | `2` | `5` |
| blend-color | `0` | `2` |
| gradient | `1` | `3` |
| image-filter | `3` | `2` |
| path-aa | `13` | `5` |
| runtime-effect | `2` | `1` |
| text-glyph | `4` | `3` |
| transform-layer | `0` | `1` |

## Source Counts

- `d50-visibility`: `11`
- `d53-visibility`: `9`
- `generated-dashboard`: `27`

## Support/Refusal Summary

- Pass rows with support claims: `22`
- Pass rows without support claims: `0`
- Expected-unsupported rows without support claims: `25`
- Expected-unsupported rows with fallback: `25`
- Dependency-gated status rows: `0`
- Dependency-gate linked rows: `6`
- Below-threshold/tolerance-only rows excluded from production missing-feature accounting: `0`
- Reporting-only status rows: `0`
- Policy-only visibility rows: `20`

## Non-Claims

- `supportPromotion`: `false`
- `registryRowMutation`: `false`
- `dashboardPromotion`: `false`
- `readinessPromotion`: `false`
- `thresholdChange`: `false`
- `edgeBudgetChange`: `false`
- `broadPathAASupport`: `false`
- `broadDashSupport`: `false`
- `broadHairlineSupport`: `false`
- `broadStrokeSupport`: `false`
- `ganeshPort`: `false`
- `graphitePort`: `false`
- `dynamicSkSLCompiler`: `false`
- `dynamicSkSLIR`: `false`
- `dynamicSkSLVM`: `false`
- `belowThresholdCountedAsProductionGap`: `false`

## Validation Commands

- `rtk python3 scripts/m89_gm_registry_pm_counters.py`
- `rtk python3 scripts/validate_m89_gm_registry_pm_counters.py --check-worktree-scope`
- `rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-m89-pm-counters-pycache python3 -m py_compile scripts/m89_gm_registry_pm_counters.py scripts/validate_m89_gm_registry_pm_counters.py`
- `rtk ./gradlew --no-daemon pipelineM89GmRegistryPmCounters`
- `rtk git diff --check`
