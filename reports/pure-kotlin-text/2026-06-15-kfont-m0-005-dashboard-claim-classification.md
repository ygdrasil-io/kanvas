# KFONT-M0-005 Dashboard Claim Classification Evidence

Date: 2026-06-15
Status: review

## Scope

This report covers validation/dashboard infrastructure only. It does not add
font rendering, shaping, fallback, SDF, color, emoji, LCD, or GPU text support.

## RED Observed

The focused test was written before the validator/report existed:

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py
```

Observed result: `FAILED (failures=7)` because
`scripts/validate_pure_kotlin_text_claim_dashboard.py` was missing. The failing
tests covered missing classification values, generic `font missing` handling,
generic labels in claim rows, GPU claims without GPU artifacts, missing legacy
gates, and missing Gradle wiring.

## Evidence

- `font-claim-dashboard.json` records the eight classifications:
  `target-supported`, `current-supported`, `tracked-gap`, `DependencyGated`,
  `fixture-gated`, `GPU-gated`, `expected-unsupported`, and `drift-only`.
- Surface rows are split into `outline/path`, `simple-latin atlas`,
  `complex shaping`, `fallback`, `emoji/color`, `SDF`, and `LCD`.
- Required evidence kinds are explicit: fixture provenance, deterministic
  dumps, CPU oracle, GPU artifact when GPU is claimed, route diagnostics, and
  refusal diagnostics.
- Negative generic labels `font missing`, `text works`, and `emoji supported`
  are recorded as tracked gaps with stable `font.claim.*` or `text.claim.*`
  diagnostics.
- Legacy gates `coloremoji_blendmodes`, `scaledemoji`,
  `scaledemoji_rendering`, `dftext`, `fontations`,
  `fontations_ft_compare`, and `pdf_never_embed` remain visible, open, and
  non-promotable.
- `validatePureKotlinTextClaimDashboard` is wired into
  `pipelineSceneDashboardGate` and `pipelinePmBundle`.
- The pure Kotlin font foundation workflow now triggers on and runs the claim
  dashboard validator.

## GREEN Validation

```bash
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_claim_dashboard.py
rtk python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk python3 -m unittest scripts/test_validate_pure_kotlin_text_ci.py
rtk python3 scripts/validate_pure_kotlin_text_ci.py
rtk ./gradlew --no-daemon validatePureKotlinTextClaimDashboard
rtk git diff --check
```

Result: all commands passed.

## Remaining Gates And Non-Claims

All KFONT-M0-005 legacy gates remain open. The new dashboard blocks
evidence-free promotion; it does not retire any legacy gate or claim target
support for rendering, shaping, fallback, SDF, color, emoji, LCD, or GPU text.
