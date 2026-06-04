# FOR-309 M60 Reopen Gate After Supersession

Linear: `FOR-309`

Scene: `m60-bounded-nested-rrect-clip`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-m60-reopen-gate-after-supersession-ticket`

Decision: `M60_REOPEN_GATE_AFTER_SUPERSESSION_APPLIED`

## Result

FOR-309 keeps M60 closed unless a future ticket brings a post-FOR-302
renderer-side hypothesis. Historical FOR-283/FOR-286 next actions remain useful
audit evidence, but they cannot reopen M60 alone because FOR-301/FOR-302
superseded the FOR-293 analytic visibility assumption.

No renderer, shader, runtime, `SkAAClip`, threshold, fallback, scene status, or
readiness score changes.

## Dashboard Row Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
| `m60-bounded-nested-rrect-clip` | `expected-unsupported` | `webgpu.coverage.nested-rrect-clip.expected-unsupported` | `coverage.nested-clip-visual-parity-below-threshold` |

## Supersession Sources

- FOR-301: `SKAACLIP_DIFFERENCE_OP_ALPHA_MERGE_CAUSES_TARGET_HOLE`
- FOR-302: `M60_ANALYTIC_MODEL_RECONCILED_RUNTIME_IS_CORRECT`
- FOR-303: `M60_ANALYTIC_MODEL_SUPERSESSION_GUARD_APPLIED`

## Historical Actions Quarantined

| Source | Historical next action | Classification | Reason |
|---|---|---|---|
| FOR-283 | `PATCH_CPU_MASK_FILTER_A8_SOLID_COLOR_FILTER_DISPATCH_PAYLOAD_AND_REGENERATE_M60` | `historical-pre-supersession-action` | Predates FOR-301/FOR-302 runtime reconciliation and cannot reopen M60 alone. |
| FOR-286 | `TARGET_CPU_ACTIVE_AA_DIFFERENCE_CLIP_STORE_ORDER` | `historical-pre-supersession-action` | Predates FOR-301/FOR-302 runtime reconciliation and cannot reopen M60 alone. |

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
| FOR-309 gate keeps M60 closed without new hypothesis | `keep-closed` | True | No post-supersession renderer hypothesis is available, so the gate keeps M60 closed. |
| FOR-283/FOR-286 action alone is forbidden | `forbidden` | False | Pre-supersession nextAction evidence cannot reopen M60 without FOR-301/FOR-302 and a new hypothesis. |
| Post-supersession hypothesis without FOR-301/FOR-302 is ambiguous | `ambiguous` | False | A post-supersession hypothesis must cite FOR-301 and FOR-302. |
| Threshold weakening is forbidden | `forbidden` | False | Threshold weakening cannot reopen M60. |
| Support claim without complete proof is forbidden | `forbidden` | False | M60 support requires complete rendered evidence. |
| Cited hypothesis can become future audit candidate | `future-audit-candidate` | True | A cited post-supersession hypothesis may open a future audit, but not a support claim. |

## Required Future Reopen Proof

- post-FOR-302 renderer-side hypothesis
- explicit citation of FOR-301 runtime SkAAClip trace
- explicit citation of FOR-302 analytic/runtime reconciliation
- local pixel-level prediction that differs from the reconciled runtime model
- reference, CPU, GPU, diff, stats, and route diagnostics
- preserved fallback policy for non-selected rows
- no threshold weakening
- no use of FOR-293 as standalone oracle

## Validation

- `rtk python3 scripts/validate_for309_m60_reopen_gate_after_supersession.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-reopen-gate-after-supersession-for309.json`
- `rtk git diff --check origin/master...HEAD`
