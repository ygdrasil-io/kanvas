# D53 dashboard visibility reconcile

Date: 2026-06-08

## Scope

D53 replays policy-only dashboard visibility rows from
`codex/add-gm-dashboard-dash-hairline` onto the D52-5 baseline. It only
materializes visibility rows and validators. It does not change render paths,
WGSL, thresholds, fallback policy, or `PipelineKey` taxonomy.

## D52-5 interaction

D52-5 promotes `d52-drawminibitmaprect` as the active passing dashboard row.
The older D50 visibility row `skia-gm-drawminibitmaprect` is therefore excluded
from the D50 policy-only visibility contract in this replay. Keeping both rows
active would make the dashboard show one DrawMiniBitmapRect pass row and one
DrawMiniBitmapRect expected-unsupported row at the same time.

This exclusion is not a new support claim. It only prevents a stale
policy-only expected-unsupported row from contradicting the D52-5 passing row.

## Counter impact

Baseline at D52-5:

| Counter | Value |
| --- | ---: |
| Total rows | 94 |
| Pass rows | 71 |
| Expected-unsupported rows | 23 |

D50 visibility after D52-5 reconcile:

| Counter | Value |
| --- | ---: |
| Added D50 policy-only rows | 11 |
| Total rows | 105 |
| Pass rows | 71 |
| Expected-unsupported rows | 34 |

D53 final projection with dash/hairline/stroke rows:

| Counter | Value |
| --- | ---: |
| Added dash/hairline/stroke policy-only rows | 9 |
| Total rows | 114 |
| Pass rows | 71 |
| Expected-unsupported rows | 43 |
| Inventory-derived rows | 66 |

All D53-added rows remain policy-only `expected-unsupported` rows with stable
fallback reasons and `risk.expected-unsupported` tags.

## Validation

```bash
rtk python3 scripts/validate_d50_dashboard_visibility.py
rtk python3 scripts/validate_dash_hairline_stroke_dashboard_visibility.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-visibility.json
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/dash-hairline-stroke-gm-dashboard-visibility.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d53-dashboard-visibility-pycache python3 -m py_compile scripts/validate_d50_dashboard_visibility.py scripts/validate_dash_hairline_stroke_dashboard_visibility.py
rtk git diff --check
```

Additional sanity check from the generated dashboard:

- `d52-drawminibitmaprect`: exactly one row, `status=pass`, `gpu.status=pass`,
  `fallbackReason=none`.
- `skia-gm-drawminibitmaprect`: zero active dashboard rows.
