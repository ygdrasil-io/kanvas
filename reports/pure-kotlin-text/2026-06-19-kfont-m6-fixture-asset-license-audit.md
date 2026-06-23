# 2026-06-19 KFONT M6 Fixture Asset/License Audit Refresh

Date: 2026-06-19
Status: coordination evidence.

## Scope

- Re-audit the remaining `M6` fixture-gated / advanced-lookup tickets that are
  still not actionable on `master`: `KFONT-M6-009` and `KFONT-M6-010`.
- Verify which compatible external or in-repo font assets are credible
  candidates for future reviewed fixture work.
- Distinguish compatible candidate sources from exact ticket-local fixture
  families that are still absent in-repo and therefore still block readiness.

## Findings

- In-repo `SIL-OFL-1.1` assets remain useful as bounded real-font candidates for
  reviewed GSUB/GPOS evidence that already landed:
  `SourceSerif4-Regular.otf` stays credible for ligature and kerning-oriented
  shaping rows, while the checked-in Noto license evidence remains appropriate
  for future script-specific provenance review.
- The public `unicode-org/text-rendering-tests` repository remains available
  under `UNICODE LICENSE V3` / `SPDX-License-Identifier: Unicode-3.0` and still
  publishes OpenType reference testcases and fonts that are relevant to lookup
  auditing, including `GPOS-1`, `GPOS-2`, `GSUB-1`, and `GSUB-3`.
- Those compatible sources are useful as audit inputs, but they do not satisfy
  the exact remaining ticket-local gates on their own:
  - `KFONT-M6-009` still lacks the reviewed Thai/CJK boundary fixture pack
    named in the ticket: `thai-base-marks.otf`, `thai-tone-marks.otf`,
    `thai-latin-mixed.txt`, `cjk-han-variation-selector.otf`,
    `cjk-kana-vertical.otf`, `cjk-hangul-direct.otf`, and
    `cjk-missing-vertical-alt.otf`.
  - `KFONT-M6-010` still lacks the reviewed advanced-lookup fixture pack named
    in the ticket: `gsub-chaining-context.otf`,
    `gsub-extension-substitution.otf`, `gsub-reverse-chaining.otf`,
    `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`,
    `gpos-extension-positioning.otf`, `gpos-variation-device.otf`, and
    `layout-extension-cycle.otf`.
- No compatible source reviewed in this audit justifies changing `KFONT-M6-009`
  or `KFONT-M6-010` from `proposed` to `ready`: the remaining gate is still the
  absence of reviewed ticket-local fixture provenance and the corresponding
  deterministic dump families.

## Outcome

- `KFONT-M6-009` stays `proposed` with a more precise remaining gate.
- `KFONT-M6-010` stays `proposed` with a more precise remaining gate.
- This audit does not promote any shaping claim, script support claim, or
  fixture readiness claim by itself.

## Sources

- In-repo provenance and license evidence:
  - `reports/font/fixtures/provenance/index.json`
  - `reports/font/fixtures/licenses/source-serif-OFL-1.1.txt`
  - `reports/font/fixtures/licenses/noto-OFL-1.1.txt`
- External compatible candidate source verified on 2026-06-19:
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

- No complete Thai shaping claim.
- No complete CJK shaping claim.
- No complete extension/chaining/variation-adjustment lookup claim.
- No claim that compatible external reference fonts substitute for the exact
  ticket-local fixture families still absent in-repo.
