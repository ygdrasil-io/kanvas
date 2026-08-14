# FP-13 Task 0 (M86 burn-down wave) — ranked candidate list

Ranking formula: PM value score (high=3, medium=2, low=1) divided by risk score (high=3, medium=2, low=1); ties broken by row count desc, then item number. Per-row machine-readable source: `residual-inventory.csv` (341 rows).

Reference-kind note: **every residual row is `cpu-oracle`** (pure-Kotlin pixel oracle of the blend/clip suites). CPU-oracle rows do not count as Skia-comparable fidelity (M86 statement, evidence §1.3).

| rank | item | rows | refusal code | expected GPU route | PM value | risk | owner task |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 3 | 2 | invalid.preflight.core_primitive_clip_producer_authority | `prepared-composite-analytic-clip` | **medium** | **low** | Task 5 |
| 2 | 4 | 209 | unsupported.recording.core_primitive_mixed_uniform_layouts | `split-uniform64-160` | **high** | **medium** | Task 6 |
| 3 | 1 | 32 | unsupported.native-core-primitive.dst-read-formula | `prepared-dst-read-formula` | **high** | **medium** | Task 3 |
| 4 | 6 | 60 | unsupported.native-core-primitive.path-destination-read | `stencil-continuation-path-cover` | **high** | **high** | Task 8 |
| 5 | 2 | 2 | unsupported.native-core-primitive.analytic-shape-multi-key | `prepared-dst-read-formula` | **low** | **low** | Task 4 |
| 6 | 5 | 4 | unsupported.recording.core_primitive_analytic_clip_non_direct_geometry | `analytic-clip-non-direct` | **low** | **medium** | Task 7 |

## Item 3 (rank 1) — 2 rows

- **Root cause**: core_primitive_clip_producer_authority: mask-blur composite under a complex (multi-rect) analytic clip refused at the clip producer preflight
- **Expected GPU route**: `prepared-composite-analytic-clip`
- **PM value**: medium — blur under complex clip is common product behavior; extends the shipped FP-11 Task 7 ABI
- **Risk**: low — preflight/clip-producer admission on the already-shipped analytic-clip ABI
- **Owner task**: Task 5 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | clip:coverage | n/a | complex-clip(AA-intersect+path-difference) | `invalid.preflight.core_primitive_clip_producer_authority` | cpu-oracle | `prepared-composite-analytic-clip` | medium | low | Task 5 |
| 2 | clip:coverage | sigma=1.5 | complex-clip(AA-intersect+path-difference) | `invalid.preflight.core_primitive_clip_producer_authority` | cpu-oracle | `prepared-composite-analytic-clip` | medium | low | Task 5 |

## Item 4 (rank 2) — 241 rows (209 primary + 32 Task-6 split-resource fallout)

- **Root cause**: unwired analytic-clip 64/160 split: builder gate GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2132; needs the per-step continuation/ownership design (fp-11 §4); split-lane lease cleanup already landed (3bd78e180)
- **Expected GPU route**: `split-uniform64-160`
- **PM value**: high — largest row family (209); mechanical split; biggest breadth-score delta
- **Risk**: medium — mechanical but needs the continuation/ownership design; deterministic session-close residual documented
- **Owner task**: Task 6 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | DrawRRect | DST | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 2 | DrawRRect | DST | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 3 | DrawRRect | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 4 | DrawRRect | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 5 | DrawRRect | DST | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 6 | DrawRRect | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 7 | DrawRRect | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 8 | DrawRRect | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 9 | DrawRRect | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 10 | DrawRRect | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 11 | DrawRRect | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 12 | DrawRRect | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 13 | DrawRRect | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 14 | DrawRRect | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 15 | DrawRRect | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 16 | DrawRRect | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 17 | DrawRRect | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 18 | DrawRRect | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 19 | DrawRRect | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 20 | DrawRRect | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 21 | DrawRRect | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 22 | DrawRRect | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 23 | DrawRRect | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 24 | DrawRRect | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 25 | DrawRRect | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 26 | DrawRRect | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 27 | DrawRRect | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 28 | DrawRRect | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 29 | DrawRRect | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 30 | DrawRRect | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 31 | DrawRRect | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 32 | DrawRect | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 33 | DrawRect | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 34 | DrawRect | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 35 | DrawRect | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 36 | DrawRect | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 37 | DrawRect | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 38 | DrawRect | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 39 | DrawRect | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 40 | DrawRect | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 41 | DrawRect | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 42 | DrawRect | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 43 | DrawRect | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 44 | DrawRect | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 45 | DrawRect | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 46 | DrawRect | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 47 | DrawRect | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 48 | DrawRect | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 49 | DrawRect | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 50 | DrawRect | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 51 | DrawRect | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 52 | DrawRect | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 53 | DrawRect | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 54 | DrawRect | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 55 | DrawRect | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 56 | DrawRect | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 57 | DrawRect | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 58 | DrawRect | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 59 | DrawRect | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 60 | DrawColor | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 61 | DrawColor | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 62 | DrawColor | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 63 | DrawColor | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 64 | DrawColor | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 65 | DrawColor | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 66 | DrawColor | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 67 | DrawColor | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 68 | DrawColor | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 69 | DrawColor | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 70 | DrawColor | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 71 | DrawColor | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 72 | DrawColor | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 73 | DrawColor | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 74 | DrawColor | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 75 | DrawColor | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 76 | DrawColor | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 77 | DrawColor | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 78 | DrawColor | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 79 | DrawColor | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 80 | DrawColor | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 81 | DrawColor | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 82 | DrawColor | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 83 | DrawColor | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 84 | DrawColor | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 85 | DrawColor | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 86 | DrawColor | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 87 | DrawColor | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 88 | DrawPath | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 89 | DrawPath | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 90 | DrawPath | DST | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 91 | DrawPath | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 92 | DrawPath | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 93 | DrawPath | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 94 | DrawPath | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 95 | DrawPath | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 96 | DrawPath | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 97 | DrawPath | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 98 | DrawPath | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 99 | DrawPath | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 100 | DrawPath | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 101 | DrawPath | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 102 | DrawPath | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 103 | DrawPath | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 104 | DrawPath | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 105 | DrawPath | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 106 | DrawPath | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 107 | DrawPath | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 108 | DrawPath | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 109 | DrawPath | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 110 | DrawPath | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 111 | DrawPath | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 112 | DrawPath | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 113 | DrawPath | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 114 | DrawPath | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 115 | DrawPath | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 116 | DrawPath | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 117 | DrawDRRect | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 118 | DrawDRRect | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 119 | DrawDRRect | DST | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 120 | DrawDRRect | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 121 | DrawDRRect | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 122 | DrawDRRect | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 123 | DrawDRRect | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 124 | DrawDRRect | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 125 | DrawDRRect | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 126 | DrawDRRect | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 127 | DrawDRRect | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 128 | DrawDRRect | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 129 | DrawDRRect | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 130 | DrawDRRect | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 131 | DrawDRRect | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 132 | DrawDRRect | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 133 | DrawDRRect | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 134 | DrawDRRect | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 135 | DrawDRRect | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 136 | DrawDRRect | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 137 | DrawDRRect | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 138 | DrawDRRect | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 139 | DrawDRRect | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 140 | DrawDRRect | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 141 | DrawDRRect | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 142 | DrawDRRect | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 143 | DrawDRRect | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 144 | DrawDRRect | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 145 | DrawDRRect | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 146 | DrawPoint | PLUS | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 147 | DrawPoint | MULTIPLY | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 148 | DrawPoint | OVERLAY | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 149 | DrawPoint | DARKEN | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 150 | DrawPoint | LIGHTEN | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 151 | DrawPoint | COLOR_DODGE | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 152 | DrawPoint | COLOR_BURN | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 153 | DrawPoint | HARD_LIGHT | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 154 | DrawPoint | SOFT_LIGHT | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 155 | DrawPoint | DIFFERENCE | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 156 | DrawPoint | EXCLUSION | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 157 | DrawPoint | HUE | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 158 | DrawPoint | SATURATION | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 159 | DrawPoint | COLOR | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 160 | DrawPoint | LUMINOSITY | UNCLIPPED | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 161 | DrawPoint | PLUS | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 162 | DrawPoint | MULTIPLY | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 163 | DrawPoint | OVERLAY | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 164 | DrawPoint | DARKEN | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 165 | DrawPoint | LIGHTEN | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 166 | DrawPoint | COLOR_DODGE | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 167 | DrawPoint | COLOR_BURN | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 168 | DrawPoint | HARD_LIGHT | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 169 | DrawPoint | SOFT_LIGHT | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 170 | DrawPoint | DIFFERENCE | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 171 | DrawPoint | EXCLUSION | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 172 | DrawPoint | HUE | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 173 | DrawPoint | SATURATION | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 174 | DrawPoint | COLOR | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 175 | DrawPoint | LUMINOSITY | SCISSOR | `invalid.preflight.core_primitive_direct_geometry_resources` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 176 | DrawPoint | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 177 | DrawPoint | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 178 | DrawPoint | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 179 | DrawPoint | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 180 | DrawPoint | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 181 | DrawPoint | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 182 | DrawPoint | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 183 | DrawPoint | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 184 | DrawPoint | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 185 | DrawPoint | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 186 | DrawPoint | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 187 | DrawPoint | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 188 | DrawPoint | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 189 | DrawPoint | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 190 | DrawPoint | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 191 | DrawPoint | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 192 | DrawPoint | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 193 | DrawPoint | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 194 | DrawPoint | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 195 | DrawPoint | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 196 | DrawPoint | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 197 | DrawPoint | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 198 | DrawPoint | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 199 | DrawPoint | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 200 | DrawPoint | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 201 | DrawPoint | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 202 | DrawPoint | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 203 | DrawPoint | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 204 | DrawPoints | CLEAR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 205 | DrawPoints | SRC | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 206 | DrawPoints | SRC_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 207 | DrawPoints | DST_OVER | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 208 | DrawPoints | SRC_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 209 | DrawPoints | DST_IN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 210 | DrawPoints | SRC_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 211 | DrawPoints | DST_OUT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 212 | DrawPoints | SRC_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 213 | DrawPoints | DST_ATOP | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 214 | DrawPoints | XOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 215 | DrawPoints | PLUS | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 216 | DrawPoints | MODULATE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 217 | DrawPoints | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 218 | DrawPoints | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 219 | DrawPoints | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 220 | DrawPoints | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 221 | DrawPoints | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 222 | DrawPoints | COLOR_DODGE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 223 | DrawPoints | COLOR_BURN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 224 | DrawPoints | HARD_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 225 | DrawPoints | SOFT_LIGHT | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 226 | DrawPoints | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 227 | DrawPoints | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 228 | DrawPoints | HUE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 229 | DrawPoints | SATURATION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 230 | DrawPoints | COLOR | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 231 | DrawPoints | LUMINOSITY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 232 | clip:coverage | n/a | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 233 | clip:advanced | MULTIPLY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 234 | clip:advanced | SCREEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 235 | clip:advanced | OVERLAY | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 236 | clip:advanced | DARKEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 237 | clip:advanced | LIGHTEN | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 238 | clip:advanced | DIFFERENCE | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 239 | clip:advanced | EXCLUSION | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 240 | clip:advanced | DARKEN(partial-alpha) | ALPHA_MASK | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |
| 241 | clip:path | n/a | device-rect-clip | `unsupported.recording.core_primitive_mixed_uniform_layouts` | cpu-oracle | `split-uniform64-160` | high | medium | Task 6 |

## Item 1 (rank 3) — 32 rows

- **Root cause**: no closed analytic-shape dst-read formula pipeline on the prepared lane (fp-11 §5; the 30 frame-global re-point rows share this root)
- **Expected GPU route**: `prepared-dst-read-formula`
- **PM value**: high — closes the dst-read formula root and the 30-row frame-global fallout with one pipeline
- **Risk**: medium — new prepared-lane pipeline wiring on an execution surface not exercised before
- **Owner task**: Task 3 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | DrawRRect | PLUS | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 2 | DrawRRect | MULTIPLY | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 3 | DrawRRect | OVERLAY | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 4 | DrawRRect | DARKEN | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 5 | DrawRRect | LIGHTEN | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 6 | DrawRRect | COLOR_DODGE | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 7 | DrawRRect | COLOR_BURN | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 8 | DrawRRect | HARD_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 9 | DrawRRect | SOFT_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 10 | DrawRRect | DIFFERENCE | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 11 | DrawRRect | EXCLUSION | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 12 | DrawRRect | HUE | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 13 | DrawRRect | SATURATION | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 14 | DrawRRect | COLOR | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 15 | DrawRRect | LUMINOSITY | UNCLIPPED | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 16 | DrawRRect | PLUS | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 17 | DrawRRect | MULTIPLY | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 18 | DrawRRect | OVERLAY | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 19 | DrawRRect | DARKEN | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 20 | DrawRRect | LIGHTEN | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 21 | DrawRRect | COLOR_DODGE | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 22 | DrawRRect | COLOR_BURN | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 23 | DrawRRect | HARD_LIGHT | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 24 | DrawRRect | SOFT_LIGHT | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 25 | DrawRRect | DIFFERENCE | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 26 | DrawRRect | EXCLUSION | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 27 | DrawRRect | HUE | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 28 | DrawRRect | SATURATION | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 29 | DrawRRect | COLOR | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 30 | DrawRRect | LUMINOSITY | SCISSOR | `unsupported.native-core-primitive.frame-global-pipeline` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 31 | clip:coverage | DARKEN | UNCLIPPED | `unsupported.native-core-primitive.dst-read-formula` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |
| 32 | clip:coverage | COLOR_DODGE | UNCLIPPED | `unsupported.native-core-primitive.dst-read-formula` | cpu-oracle | `prepared-dst-read-formula` | high | medium | Task 3 |

## Item 6 (rank 4) — 60 rows

- **Root cause**: path-stencil execution model cannot express dst-read: recording refusal GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2565-2575, preflighter exactly-one-pass gate GPUFramePreflighter.kt:2437-2440, materializer supportedPathComponents exclusion, per-run stencil Clear+Discard (fp-11 §3)
- **Expected GPU route**: `stencil-continuation-path-cover`
- **PM value**: high — 60 rows of the most common paint family (path blends) convert to rendered output
- **Risk**: high — new execution feature (stencil-continuation); fp-11 §3 documents wrong-pixels risk
- **Owner task**: Task 8 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | DrawPath | PLUS | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 2 | DrawPath | MULTIPLY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 3 | DrawPath | OVERLAY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 4 | DrawPath | DARKEN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 5 | DrawPath | LIGHTEN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 6 | DrawPath | COLOR_DODGE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 7 | DrawPath | COLOR_BURN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 8 | DrawPath | HARD_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 9 | DrawPath | SOFT_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 10 | DrawPath | DIFFERENCE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 11 | DrawPath | EXCLUSION | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 12 | DrawPath | HUE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 13 | DrawPath | SATURATION | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 14 | DrawPath | COLOR | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 15 | DrawPath | LUMINOSITY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 16 | DrawPath | PLUS | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 17 | DrawPath | MULTIPLY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 18 | DrawPath | OVERLAY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 19 | DrawPath | DARKEN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 20 | DrawPath | LIGHTEN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 21 | DrawPath | COLOR_DODGE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 22 | DrawPath | COLOR_BURN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 23 | DrawPath | HARD_LIGHT | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 24 | DrawPath | SOFT_LIGHT | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 25 | DrawPath | DIFFERENCE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 26 | DrawPath | EXCLUSION | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 27 | DrawPath | HUE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 28 | DrawPath | SATURATION | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 29 | DrawPath | COLOR | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 30 | DrawPath | LUMINOSITY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 31 | DrawDRRect | PLUS | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 32 | DrawDRRect | MULTIPLY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 33 | DrawDRRect | OVERLAY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 34 | DrawDRRect | DARKEN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 35 | DrawDRRect | LIGHTEN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 36 | DrawDRRect | COLOR_DODGE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 37 | DrawDRRect | COLOR_BURN | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 38 | DrawDRRect | HARD_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 39 | DrawDRRect | SOFT_LIGHT | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 40 | DrawDRRect | DIFFERENCE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 41 | DrawDRRect | EXCLUSION | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 42 | DrawDRRect | HUE | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 43 | DrawDRRect | SATURATION | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 44 | DrawDRRect | COLOR | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 45 | DrawDRRect | LUMINOSITY | UNCLIPPED | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 46 | DrawDRRect | PLUS | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 47 | DrawDRRect | MULTIPLY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 48 | DrawDRRect | OVERLAY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 49 | DrawDRRect | DARKEN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 50 | DrawDRRect | LIGHTEN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 51 | DrawDRRect | COLOR_DODGE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 52 | DrawDRRect | COLOR_BURN | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 53 | DrawDRRect | HARD_LIGHT | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 54 | DrawDRRect | SOFT_LIGHT | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 55 | DrawDRRect | DIFFERENCE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 56 | DrawDRRect | EXCLUSION | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 57 | DrawDRRect | HUE | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 58 | DrawDRRect | SATURATION | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 59 | DrawDRRect | COLOR | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |
| 60 | DrawDRRect | LUMINOSITY | SCISSOR | `unsupported.native-core-primitive.path-destination-read` | cpu-oracle | `stencil-continuation-path-cover` | high | high | Task 8 |

## Item 2 (rank 5) — 2 rows

- **Root cause**: same root as item 1; multi-key analytic shape x dst-read matrix rows
- **Expected GPU route**: `prepared-dst-read-formula`
- **PM value**: low — two AA-coverage edge pins that ride the item 1 pipeline
- **Risk**: low — no new execution feature; semantics already documented (fp-11 §2)
- **Owner task**: Task 4 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | clip:coverage | CLEAR/SRC/DST_IN | ALPHA_MASK | `unsupported.native-core-primitive.analytic-shape-multi-key` | cpu-oracle | `prepared-dst-read-formula` | low | low | Task 4 |
| 2 | clip:coverage | CLEAR/SRC/DST_IN | SCISSOR | `unsupported.native-core-primitive.analytic-shape-multi-key` | cpu-oracle | `prepared-dst-read-formula` | low | low | Task 4 |

## Item 5 (rank 6) — 4 rows

- **Root cause**: analytic_clip_non_direct_geometry gate GPUCorePrimitivePreparedFrameTaskListBuilder.kt:2009 (twin :2016): analytic clip over non-direct/stencil-shaded geometry is a new execution feature (fp-11 §2)
- **Expected GPU route**: `analytic-clip-non-direct`
- **PM value**: low — four ALPHA_MASK x DST edge rows only
- **Risk**: medium — new admission for non-direct shading geometry; depends on the item 4 frame
- **Owner task**: Task 7 (plan §5)

| # | family | mode | context | refusalCode | referenceKind | route | pm | risk | owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | DrawRect | DST | ALPHA_MASK | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | cpu-oracle | `analytic-clip-non-direct` | low | medium | Task 7 |
| 2 | DrawColor | DST | ALPHA_MASK | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | cpu-oracle | `analytic-clip-non-direct` | low | medium | Task 7 |
| 3 | DrawPoint | DST | ALPHA_MASK | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | cpu-oracle | `analytic-clip-non-direct` | low | medium | Task 7 |
| 4 | DrawPoints | DST | ALPHA_MASK | `unsupported.recording.core_primitive_analytic_clip_non_direct_geometry` | cpu-oracle | `analytic-clip-non-direct` | low | medium | Task 7 |

## Cross-checks (fp-11 §0.3 / plan §1 arithmetic)

- 341 rows = 199 mixed-layout blend + 10 clip pins + 60 path-destination-read + 32 direct-geometry re-points (2 DrawRRect DST + 30 DrawPoint) + 30 frame-global-pipeline re-points + 4 analytic-clip-non-direct + 2 dst-read-formula + 2 multi-key + 2 complex-clip blur pins.
- Blend distribution: mixed 199, path-destination-read 60, direct_geometry 32 (incl. DrawPoint), frame-global 30, analytic-clip-non-direct 4.
- Clip pins: mixed 10 (Coverage 1, Advanced 8, PathClip 1), clip-producer-authority 2, dst-read-formula 2, multi-key 2.
