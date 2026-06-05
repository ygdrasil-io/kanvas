# FOR-382 M60 F16 coverage/composition membership audit

Decision: `M60_F16_COVERAGE_COMPOSITION_MEMBERSHIP_AUDIT_RECORDED`

Classification: `local-source-category-separates-improved-from-regressed-but-renderer-predicate-still-needs-coverage-proof`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-composition-membership-audit-for382/m60-f16-coverage-composition-membership-audit-for382.json`

FOR-382 keeps the FOR-380 probe diagnostic-only. The correction remains
`correctionKept=false`, is disabled by default, and no support score,
threshold, fallback, WGSL, GPU runtime, or renderer behavior changes.

The full-scene guard from FOR-380 is preserved: similarity regresses
`95.91% -> 87.06%`, mismatch pixels increase `1004 -> 3181`, and `>8`
residual pixels increase `10 -> 3164`.

## FOR-381 set preservation

| Set | Pixels | Residual before | Residual after | Delta | Coverage alpha | Source alpha |
|---|---:|---:|---:|---:|---|---|
| All audited pixels | 24576 | 2014 | 231162 | 229148 | `0..255` | `0..255` |
| Improved | 8 | 734 | 669 | -65 | `96..160` | `96..160` |
| Regressed | 3171 | 1274 | 230487 | 229213 | `16..255` | `16..255` |
| Unchanged | 21397 | 6 | 6 | 0 | `0..0` | `0..0` |
| FOR-379 critical | 10 | 856 | 816 | -40 | `64..160` | `64..160` |

The 8 improved pixels all have partial diagnostic coverage/source alpha and
distance `0` to the preserved FOR-379 critical coordinates. The 3171 regressed
pixels also have non-zero diagnostic coverage/source alpha, so raw coverage
presence alone is not a safe predicate.

## Membership categories

| Category | Pixels | Residual before | Residual after | Delta | Main signal |
|---|---:|---:|---:|---:|---|
| `source-locale-plausible` | 8 | 734 | 669 | -65 | improved, FOR-379 coordinate, non-zero coverage/source alpha |
| `coverage-composition-plausible` | 3024 | 847 | 223081 | 222234 | current reference-equivalent, probe introduces residual |
| `mixed` | 147 | 427 | 7406 | 6979 | covered/source-present, but current residual is not isolated |
| `insufficient` | 21397 | 6 | 6 | 0 | unchanged or no source/coverage signal |

The source-local diagnostic category separates the 8 improved pixels from the
3171 regressed pixels. However, this category is outcome-aware and uses the
FOR-379 sample membership. It is evidence for the next predicate search, not a
renderer predicate ready to ship.

## Next move

The next step should be a bounded proof of coverage/composition membership
that does not depend on the probe outcome. A renderer correction remains
blocked until that proof can distinguish the 8 source-local pixels from the
3024 coverage/composition-plausible regressions and the 147 mixed pixels.
