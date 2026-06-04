# FOR-303 M60 Analytic Model Supersession Guard

Linear: `FOR-303`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED`

## Result

FOR-303 adds an audit-only guard around the superseded FOR-293 analytic
visibility model. Historical FOR-293 through FOR-300 evidence remains
preserved, FOR-301/FOR-302 remain the superseding runtime/reconciliation
evidence, and future M60 promotion/support consumers must cite both
FOR-301 and FOR-302 before using FOR-293-derived visibility facts.

| Decision | Applied |
|---|---|
| `M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED` | True |
| `M60_ANALYTIC_MODEL_UNSAFE_CONSUMER_FOUND` | False |
| `M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_AMBIGUOUS` | False |

## Supersession Evidence

| Metric | Value |
|---|---:|
| Original target pixels | 59 |
| FOR-293 original nonzero coverage | 59 |
| Runtime path full on original targets | 59 |
| Runtime result zero on original targets | 59 |
| FOR-293/runtime mismatches | 59 |
| Runtime formula matches | 59 |
| Candidate-minus-runtime pixels | 3293 |
| Candidate-minus-runtime result zero | 3281 |

FOR-302 decision: `M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT`.
M60 remains `KEEP_EXPECTED_UNSUPPORTED`.

## Inventory Summary

| Classification | Consumers |
|---|---:|
| `historical` | 6 |
| `historical-comparison` | 21 |
| `runtime-reconciliation` | 6 |
| `supersession-guard` | 1 |

Unsafe consumers: `0`.
Ambiguous consumers: `0`.

## Consumer Inventory

| Path | Classification | FOR-301 | FOR-302 | Reason |
|---|---|---:|---:|---|
| `scripts/validate_for280_cpu_aa_difference_clip_coverage_edge.py` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `scripts/validate_for281_cpu_mask_filter_clip_coverage_trace.py` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `scripts/validate_for282_cpu_color_filter_srcin_blend_parity.py` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `scripts/validate_for293_m60_red_drawrrect_runtime_visibility_audit.py` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `scripts/validate_for294_m60_expanded_red_drawrrect_runtime_trace.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for295_m60_red_domain_vs_white_targets.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for296_m60_red_runtime_spatial_separation.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for297_m60_candidate_hole_overbreadth.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for298_m60_a8_srcinpayload_runtime_filter.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for299_m60_a8_predispatch_filter_trace.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for300_m60_active_aa_clip_coverage.py` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `scripts/validate_for301_m60_skaaclip_band_trace.py` | `runtime-reconciliation` | True | False | `FOR-301 runtime SkAAClip trace is the superseding runtime operand evidence.` |
| `scripts/validate_for302_m60_analytic_clip_model_reconciliation.py` | `runtime-reconciliation` | True | True | `FOR-302 reconciles the FOR-293 analytic model against the FOR-301 runtime trace.` |
| `scripts/validate_for303_m60_analytic_model_supersession_guard.py` | `supersession-guard` | True | True | `FOR-303 guard references both superseding runtime/reconciliation records.` |
| `reports/wgsl-pipeline/2026-06-03-for-293-m60-red-drawrrect-runtime-visibility-audit.md` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `reports/wgsl-pipeline/2026-06-03-for-294-m60-expanded-red-drawrrect-runtime-trace.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-295-m60-red-domain-vs-white-targets.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-296-m60-red-runtime-spatial-separation.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-297-m60-candidate-hole-overbreadth.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-298-m60-a8-srcinpayload-runtime-filter.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-299-m60-a8-predispatch-filter-trace.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-300-m60-active-aa-clip-coverage.md` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/2026-06-04-for-301-m60-skaaclip-band-trace.md` | `runtime-reconciliation` | True | False | `FOR-301 runtime SkAAClip trace is the superseding runtime operand evidence.` |
| `reports/wgsl-pipeline/2026-06-04-for-302-m60-analytic-clip-model-reconciliation.md` | `runtime-reconciliation` | True | True | `FOR-302 reconciles the FOR-293 analytic model against the FOR-301 runtime trace.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-a8-predispatch-filter-trace-for299.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-a8-srcinpayload-runtime-filter-for298.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-active-aa-clip-coverage-for300.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-analytic-clip-model-reconciliation-for302.json` | `runtime-reconciliation` | True | True | `FOR-302 reconciles the FOR-293 analytic model against the FOR-301 runtime trace.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-candidate-hole-overbreadth-for297.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-expanded-red-drawrrect-runtime-trace-for294.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-domain-vs-white-targets-for295.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-drawrrect-runtime-visibility-audit-for293.json` | `historical` | False | False | `Historical analytic model source or pre-FOR-293 foundation; preserved as evidence only.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-red-runtime-spatial-separation-for296.json` | `historical-comparison` | False | False | `Historical contradiction/comparison audit created before the FOR-301/FOR-302 reconciliation.` |
| `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-skaaclip-band-trace-for301.json` | `runtime-reconciliation` | True | False | `FOR-301 runtime SkAAClip trace is the superseding runtime operand evidence.` |

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-analytic-model-supersession-guard-for303.json`
