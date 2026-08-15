# Skia Fidelity Wave 0 Gate Review

status: approved
wave: 0
modecolorfilters: rendered-through-native-route
session-close: absent-from-targeted-final-xml
svg-terminal-masking: fixed
thresholds-weakened: false
known-baselines: listed-individually

## Scope

- Worktree: `/tmp/opencode/kanvas-agentic-skia-fidelity-wave0`
- Branch: `codex/agentic-skia-fidelity-wave0`
- Reviewed source HEAD: `dce75d98bd8a3361dd077f48f2b99fc05fe0d071`
- Reviewed range: `f2e68b895..dce75d98bd8a3361dd077f48f2b99fc05fe0d071`
- All twelve commits in the range were inspected, including the Task 5 evidence-owner delta.

## Final Targeted Evidence

- Skia command exit status: `0`.
- Final Skia JUnit: `2` tests, `0` failures, `0` errors, `0` skips.
- Final Skia cases: `ModeColorFilterGm` and `ModeColorFiltersGm`.
- Final Skia route output: `modecolorfilters`, similarity `27.06%`, threshold `0.0%`, `dispatch=2382`, `refuse=0`.
- SVG command attempt 1 exit status: `1`; `texture-3` recorded a pixel assertion at `0.00%` against the unchanged `1.0%` threshold while stderr recorded an external W3C DTD HTTP `429`.
- SVG exact rerun exit status: `0`.
- Final SVG JUnit after the exact rerun: `17` tests, `0` failures, `0` errors, `12` skips.
- Final SVG passed cases include `texture-3` at `1.49%` against `1.0%`.
- The four expected SVG refusal codes remain explicit skips: `unsupported.core_primitive.geometry.invalid`, `unsupported.material.linear_gradient_capability_missing`, `unsupported.geometry.path_key_nondeterministic`, and `unsupported.core_primitive.stencil_edge_fan_budget`.
- Targeted final XML contains zero aggregate-budget, one-copy, frame-global, frame-pool-saturation, session-cache-saturation, and `failed.surface.prepared.session-close` diagnostics.

## Reconciliation

- The exact dated-input reconciliation shape was rerun against the final current Skia and SVG XML files with temporary outputs under `.superpowers/sdd/2026-08-14-agentic-skia-fidelity-wave0/`.
- Temporary reconciliation `--check` exit status: `0`, with no output.
- Temporary reconciliation kind: `skia-fidelity-wave-0-delta`.
- Temporary reconciliation current counts: Skia `2/0/0/0`; SVG `17/0/0/12`; dashboard `576` rows; CPU-oracle `0` rows.
- Temporary reconciliation policy: `globalThresholdWeakened=false`, `scoresDirectlyEdited=false`, `readinessDelta=0.0`.
- The tracked Task 5 JSON and reconciliation Markdown were not overwritten. Their committed source provenance remains `037d1fdd04419edd173837dac3522008e3c22373`; the temporary final manifest records reviewed source HEAD `dce75d98bd8a3361dd077f48f2b99fc05fe0d071`.

## Ownership Checks

- No changed path is `GPUBlendPlanning`.
- The core-primitive frame budget literal remains `1L shl 30` at the same source location as the base, with no budget-literal delta.
- No global similarity threshold, SVG threshold, JUnit assertion, or reference path was weakened.
- The runner-produced score side effect was recorded as a timestamp-only change after the final Skia probe and restored to the reviewed Task 5 state. No generated PNG diff remained after the probes.
- No generated render, dated input, temporary reconciliation output, score side effect, or historical report was staged.
- The historical FP-13 report was not changed.
- The final worktree was clean after restoring the known score side effect.

## Known Baselines

- Package boundary baseline: `GPURendererPackageBoundaryTest > gpu renderer production source satisfies package boundary rules()` remains the one GPU renderer module failure.
- Kanvas UNORM baseline: `GPUPreparedSurfaceImagePixelTest > all image families retain the direct prepared route and native pixel contract()` remains the known one-LSB failure.
- Focused `GPUAllApiBlendSurfaceTest` baseline: the exact `1,864`-case JUnit run has `105` failing cases, separate from the package and UNORM baselines: `60` `failed.native-core-primitive.frame-global-materialization` terminals (DrawPath and DrawDRRect, 15 modes across UNCLIPPED/SCISSOR), `30` DrawPoint UNCLIPPED/SCISSOR expectation mismatches, and `15` `invalid.preflight.core_primitive_direct_geometry_resources` DrawPoint ALPHA_MASK cases. The run has `0` errors, `0` skips, and `0` `failed.surface.prepared.session-close` or `GPUOwnedNativeCloseIncompleteException` occurrences.
- Full Skia inventory baseline: `615` tests with `497` failures, `40` skips, and `78` passes; the inventory contains `488` terminal/refusal failures, `7` missing references, `1` size mismatch, and the named below-threshold `text_scale_skew` row. This is separate from the focused two-case gate.
- Dashboard baseline: `576` nonblocking rows with `540` passing, `6` failing, and `30` no-score rows; these rows are not substituted for the focused JUnit population.
- SVG network/pixel baseline: the first exact rerun saw `texture-3` at `0.00%` after an external DTD `429`; the immediate exact rerun passed at `1.49%`. Neither XML contained a lifecycle terminal.
- Historical FP-13 baseline: `615` tests, `498` failures, `40` skips, `0` errors, `acceptanceBaseline=false`; historical context only and not an acceptance baseline.

## Decision

All Wave 0 acceptance predicates are satisfied by the final targeted XML and temporary reconciliation. The gate is approved without changing source thresholds, assertions, references, budget policy, or Task 5 evidence ownership.
