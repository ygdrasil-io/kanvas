# FOR-381 M60 F16 source/color sub-zone audit

Decision: `M60_F16_SOURCE_COLOR_SUBZONE_AUDIT_RECORDED`

Classification: `subzone-predicate-plausible-local-correction-needs-distinct-coverage-composition`

Artifact: `reports/wgsl-pipeline/scenes/artifacts/m60-f16-source-color-subzone-audit-for381/m60-f16-source-color-subzone-audit-for381.json`

FOR-381 keeps the FOR-380 probe diagnostic-only. The correction remains
`correctionKept=false`, is disabled by default, and no support score,
threshold, fallback, WGSL, GPU runtime, or renderer behavior changes.

The full-scene guard from FOR-380 is preserved: similarity regresses
`95.91% -> 87.06%`, mismatch pixels increase `1004 -> 3181`, and `>8`
residual pixels increase `10 -> 3164`.

## Pixel sets

| Set | Pixels | Residual before | Residual after | Delta | Bounds |
|---|---:|---:|---:|---:|---|
| All audited pixels | 24576 | 2014 | 231162 | 229148 | `(0,0)-(191,127)` |
| Improved | 8 | 734 | 669 | -65 | `(17,74)-(93,80)` |
| Regressed | 3171 | 1274 | 230487 | 229213 | `(14,37)-(191,84)` |
| Unchanged | 21397 | 6 | 6 | 0 | `(0,0)-(191,127)` |
| FOR-379 critical | 10 | 856 | 816 | -40 | `(17,74)-(93,81)` |

The probe improves only 8 scene pixels by residual, including the preserved
FOR-379 cluster, while 3171 pixels regress. The regressed set dominates the
scene delta by `229213` residual units, so a draw-wide source/color correction
is too broad.

## Band distribution

Band/cap/join is inferred from the fixture x-range only; it identifies the
owning lane but does not prove stroke coverage membership.

| Set | Band | Cap/join | Pixels | Delta |
|---|---|---|---:|---:|
| Improved | `round-round` | round/round | 7 | -61 |
| Improved | `butt-bevel` | butt/bevel | 1 | -4 |
| Regressed | `square-bevel` | square/bevel | 1734 | 109163 |
| Regressed | `round-round` | round/round | 984 | 82492 |
| Regressed | `butt-bevel` | butt/bevel | 453 | 37558 |

The best improved pixel remains one of the FOR-379 samples at `(92,75)`:
current `[181,191,230,255]`, probe `[159,198,236,255]`, reference
`[133,150,214,255]`, residual `105 -> 96`, direct source proof residual `2`.
The worst regressed sampled pixel is `(69,80)`, distance `1` from a critical
sample: current matches reference `[68,121,68,255]`, while the probe writes
`[0,138,76,255]`, residual `0 -> 93`.

Conclusion: the sub-zone predicate is plausible, but it must distinguish local
source/color pixels from coverage/composition pixels before any correction can
be considered. FOR-381 records that diagnostic split only.
