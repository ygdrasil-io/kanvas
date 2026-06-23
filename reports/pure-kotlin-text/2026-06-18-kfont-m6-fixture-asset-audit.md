# 2026-06-18 KFONT M6 Fixture Asset Audit

Status: documentation-only blocker audit.

## Scope

- Re-audit the three merged `M6` tickets that were still marked `review`:
  `KFONT-M6-002`, `KFONT-M6-004`, and `KFONT-M6-006`.
- Verify whether compatible real-font assets now exist for the missing GSUB and
  GPOS fixture families.
- Distinguish real candidate assets from contextual-only or otherwise
  insufficient substitutes.

## Findings

- `reports/font/fixtures/fonts/scaler/SourceSerif4-Regular.otf` remains a
  reviewed `SIL-OFL-1.1` asset in-repo. Drift-only local HarfBuzz inspection
  confirms it is a credible real fixture candidate for:
  `gsub-ligature-fi.otf` (`fi` / `office` ligatures), single-substitution via
  `smcp` / `c2sc`, and pair kerning via `kern`.
- `unicode-org/text-rendering-tests` is available under `Unicode-3.0` and
  provides real OpenType test fonts such as `TestGPOSOne.ttf`,
  `TestGPOSTwo.otf`, `TestGSUBOne.otf`, and `TestGSUBThree.ttf`.
- Those Unicode test fonts help for pair-positioning and contextual/reference
  GSUB coverage, but they do not by themselves satisfy the exact remaining M6
  acceptance gates:
  - `KFONT-M6-002` still lacks a reviewed real simple GSUB LookupType 2
    multiple-substitution fixture.
  - `KFONT-M6-004` still lacks a reviewed real simple GPOS LookupType 1
    single-positioning fixture.
  - `KFONT-M6-006` still depends on the absent per-script shaping fixture
    families owned by `KFONT-M6-007`, `KFONT-M6-008`, and `KFONT-M6-009`, plus
    runtime `ResolvedFeatureSet` adoption and explicit `drawString`
    non-enablement evidence.

## Outcome

- `KFONT-M6-002` moves from `review` to `blocked`.
- `KFONT-M6-004` moves from `review` to `blocked`.
- `KFONT-M6-006` moves from `review` to `blocked`.

The merged bounded implementation slices remain valid prerequisite evidence.
The blocker status now reflects that the remaining work is fixture/runtime
delivery-gated rather than pending independent review.

## Sources

- In-repo provenance:
  - `reports/font/fixtures/provenance/index.json`
  - `reports/font/fixtures/licenses/source-serif-OFL-1.1.txt`
  - `reports/font/fixtures/licenses/noto-OFL-1.1.txt`
- External source of compatible reference test fonts:
  - `https://github.com/unicode-org/text-rendering-tests`
  - `https://github.com/unicode-org/text-rendering-tests/blob/main/LICENSE`
  - `https://github.com/unicode-org/text-rendering-tests/blob/main/testcases/GPOS-1.html`
  - `https://github.com/unicode-org/text-rendering-tests/blob/main/testcases/GPOS-2.html`
  - `https://github.com/unicode-org/text-rendering-tests/blob/main/testcases/GSUB-1.html`
  - `https://github.com/unicode-org/text-rendering-tests/blob/main/testcases/GSUB-3.html`

## Validation

```bash
rtk git diff --check
```

## Non-Claims

- No complete GSUB support claim.
- No complete GPOS support claim.
- No claim that contextual GSUB substitutes for simple LookupType 2 evidence.
- No claim that pair-positioning substitutes for simple LookupType 1 evidence.
- No complex-script shaping promotion.
