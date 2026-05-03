# Colorspace fingerprint — Phase 0 outcome

> Source of truth for the destination color space targeted by the GM tests.
> Output of [PngChunkDumpTest.kt](../kotlin/org/skia/diagnostics/PngChunkDumpTest.kt) +
> [ColorMathSanityTest.kt](../kotlin/org/skia/diagnostics/ColorMathSanityTest.kt).

## Conclusion

All Skia DM reference PNGs in `kanvas/src/test/resources/original-888/` are encoded
with the **same Rec.2020 ICC profile** (named `"Rec.2020"`, authored by
`"DM unified Rec.2020"` per the `tEXt Author` chunk).

| Property | Value |
|---|---|
| Profile name | `Rec.2020` |
| Author | `DM unified Rec.2020` |
| ICC version | 4.3.0 |
| Device class | `mntr` (display) |
| Color space | `RGB ` |
| PCS | `XYZ ` (D50) |
| Profile size | 536 bytes |
| White point | D50 = (0.9642, 1.0000, 0.8249) |
| `iCCP` chunk | present in every PNG sampled |
| `sRGB`, `cICP`, `cHRM`, `gAMA` | none of these chunks are present |

PNGs sampled: `bigrect.png`, `simplerect.png`, `aarectmodes.png`, `addarc.png`,
`aaclip.png`, `all_bitmap_configs.png`. All use the byte-identical ICC profile.

## Profile parameters

### `toXYZD50` matrix (Bradford-adapted Rec.2020 primaries, row-major, encoded as s15Fixed16)

```
[ 0.6734619140625,  0.1656646728515625,  0.1251068115234375 ]
[ 0.2790374755859375, 0.6753387451171875, 0.0456237792968750 ]
[-0.0019378662109375, 0.0299835205078125, 0.7971649169921875 ]
```

These are the canonical `skcms_*Rec2020` D50-adapted primaries. (Match
`SkNamedGamut::kRec2020` upstream to ~6 decimals.)

### Transfer function (single curve shared by `rTRC`, `gTRC`, `bTRC`)

ICC v4 parametric type **4** (7-parameter `(g, a, b, c, d, e, f)`), encoded
direction = encoded → linear (i.e., this is the **EOTF**):

```
g = 2.22221374511718750
a = 0.90966796875
b = 0.09033203125
c = 0.22222900390625
d = 0.08123779296875
e = 0.0
f = 0.0
```

So:
- `linear = (a·X + b)^g`     for `X ≥ d`
- `linear = c·X`             for `X < d`

The encoder (linear → encoded) is the inverse, parametric form
`(A·Y + B)^G + E` for `Y ≥ D`, `C·Y + F` otherwise:
- `G = 1/g  = 0.45000…`
- `A = 1/a^g`
- `B = -e/a^g = 0`
- `C = 1/c  = 4.5`
- `D = c·d + f = 0.018054…`
- `E = -b/a`
- `F = 0`

That `C = 4.5` and `D ≈ 0.018054` confirm this **is BT.2020 OETF**: the
linear segment is exactly `Y = 4.5·E_linear` for small `E_linear`, matching
ITU-R BT.2020-2 Recommendation Annex 2 §1.3 (10-bit and 12-bit unified).

## Numerical confirmation (sanity test)

Given:
- Source = canonical sRGB (skcms `*sRGB_profile()->toXYZD50`)
- Destination = the ICC profile above
- Pipeline: `8-bit sRGB → linear sRGB → D50 XYZ → linear Rec.2020 → 8-bit Rec.2020`
  (no chromatic adaptation step needed because both `toXYZD50` matrices are
  already Bradford-adapted to D50)

Pure sRGB blue `(0, 0, 255)`:

| Stage | Value |
|---|---|
| linear sRGB | `(0.000, 0.000, 1.000)` |
| D50 XYZ | `(0.1431, 0.0606, 0.7141)` |
| linear Rec.2020 | `(0.0433, 0.0114, 0.8955)` |
| encoded Rec.2020 (float) | `(0.16823, 0.05118, 0.94672)` |
| encoded Rec.2020 (8-bit) | **`(43, 13, 241)`** |
| observed in `bigrect.png` | `(43, 13, 242)` |

Match to within 1 ulp on B (241 vs 242). The 1-unit gap is rounding noise from
8-bit quantization plus s15Fixed16 truncation in the ICC matrix. Perfectly
within the "1 ulp" target of the plan.

Cross-checks:

| Source 8-bit | → Rec.2020 8-bit |
|---|---|
| sRGB pure red `(255,0,0)` | `(202, 59, 19)` |
| sRGB pure green `(0,255,0)` | `(145, 245, 69)` |
| sRGB pure blue `(0,0,255)` | `(43, 13, 241)` |

These shifts are consistent with the wide-gamut Rec.2020 hypothesis — the
"de-saturated" appearance (R picks up green, G picks up red+blue, B picks up
some red+green) is the signature of mapping smaller-gamut sRGB into the larger
Rec.2020 numerical space.

## Implication for the migration

The plan's Phase 5 destination is now concrete:

```kotlin
// kanvas-skia internal singleton
val DM_REFERENCE_COLOR_SPACE = SkColorSpace.MakeRGB(
    transferFn = SkcmsTransferFunction(
        g = 2.22221374511718750f,
        a = 0.90966796875f,
        b = 0.09033203125f,
        c = 0.22222900390625f,
        d = 0.08123779296875f,
        e = 0.0f, f = 0.0f,
    ),
    toXYZ = SkcmsMatrix3x3(arrayOf(
        floatArrayOf( 0.6734619140625f,  0.1656646728515625f,  0.1251068115234375f),
        floatArrayOf( 0.2790374755859375f, 0.6753387451171875f, 0.0456237792968750f),
        floatArrayOf(-0.0019378662109375f, 0.0299835205078125f, 0.7971649169921875f),
    )),
)
```

— the same constants are present in skcms upstream as `SkNamedTransferFn::kRec2020`
+ `SkNamedGamut::kRec2020` (allowing for s15Fixed16 vs float rounding).

Phase 1's named-constant module can re-use these values verbatim.

## Reproducing this output

```
./gradlew :kanvas-skia:test --tests "org.skia.diagnostics.PngChunkDumpTest"
./gradlew :kanvas-skia:test --tests "org.skia.diagnostics.ColorMathSanityTest" -i
```

Full ICC dump is written to
`kanvas-skia/build/diagnostics/png-chunk-dump.txt`.
