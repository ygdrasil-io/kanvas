# Color Management Foundation Audit

## Scope and baseline

- Current `SkcmsCompat` accepts only the synthetic 132-byte profile shape.
- Current production tree has no `:color-management` module.
- Historical commit `8c457424b` contains ICC curve, CLUT, A2B/B2A, parser, and tests.
- Historical commit `676c4a2b` contains PQ/HLG and color-transform-step evidence.
- Historical source is an oracle; every adopted file is reviewed, renamed into `org.graphiks.kanvas.color`, and covered by current tests.

The current compatibility layer is not an ICC engine: `skcmsParse` checks a
132-byte Kanvas-shaped profile, reads two synthetic selector bytes, and returns
named transfer/gamut values. `SkICC.WriteToICC` writes that same synthetic
shape, while `SkICC.Make` returns `null`. The production tree therefore cannot
parse general ICC profiles, evaluate ICC curves or CLUTs, prepare reusable
pixel transforms, or process CICP PQ/HLG HDR content.

## Validation

| Command | Result |
| --- | --- |
| `rtk ./gradlew projects --console=plain` | Passed. The hierarchy contains `:math`, `:kanvas`, and `:codec` with codec submodules, and contains no `:color-management`. |
| `rtk ./gradlew :kanvas:test --rerun-tasks` | Passed. `:kanvas:test` completed with `BUILD SUCCESSFUL` in 18 seconds; 37 tasks executed. |

The earlier filtered command, `rtk ./gradlew :kanvas:test --tests
'*SkColorSpace*' --rerun-tasks`, was a plan defect: the current test suite has
no matching test class, so Gradle correctly reported `No tests found for given
includes`. It was superseded by the unfiltered baseline command above; this is
not a production or test failure.

An additional source audit found no `ColorTransform` declaration or reference
under `kanvas/src/main` or `kanvas/src/test`. Current `SkColorSpace` is a
small compatibility type in `SkCodecCompat.kt`; it exposes named spaces and
matrix/transfer-function construction only, not a transform API.

## Historical algorithm and file inventory

The following historical material is implementation evidence, not an active
backlog and not a source tree to restore wholesale.

| Evidence | Audited contents | Adoption condition |
| --- | --- | --- |
| `8c457424b:.../SkcmsCurve.kt`, `SkcmsICCTag.kt`, `SkcmsParse.kt`, `SkcmsICCProfile.kt`, `SkcmsSignature.kt` | ICC tag parsing; parametric and sampled curves; profile/tag representation; RGB/gray and CICP metadata handling. | Revalidate bounds, tag typing, malformed-input behavior, and profile applicability against the new portable API. |
| `8c457424b:.../SkcmsA2B.kt`, `SkcmsB2A.kt`, `SkcmsA2BEval.kt`, `SkcmsMatrix3x4.kt` | A2B/B2A pipeline data, 1-4D CLUT interpolation, endian-aware 16-bit samples, curve/matrix/CLUT ordering, and CMYK alpha handling. | Review numerical precision, channel contracts, ICC intent selection, and bulk-transform integration with current tests. |
| `8c457424b:.../Skcms.kt`, `SkcmsTransferFunction.kt`, `SkcmsTFType.kt`, `SkNamedTransferFn.kt`, `SkNamedGamut.kt`, `SkcmsMatrix3x3.kt` | Transfer-function classification/evaluation/inversion, named CICP transfer/gamut values, and matrix math. | Revalidate API ownership and precision for U16/F16 and alpha-aware transforms. |
| `8c457424b:.../SkcmsCICP.kt`, `SkcmsKnownProfiles.kt` | CICP data shape and known-profile evidence. | Map only supported CICP combinations into the new module with explicit refusal for unsupported values. |
| `8c457424b:.../src/test/kotlin/org/skia/skcms/` | Parser, curve/LUT, A2B/B2A CLUT, known-profile, primaries, and type tests. | Port cases as newly owned tests; retain independent ground truth where available. |
| `676c4a2b:.../Skcms.kt`, `SkcmsTFType.kt`, and adjacent color-space transform-step tests | PQ, PQish, HLG, HLGish, HLGinvish classification/evaluation/inversion; HDR reference-white scale and HLG OOTF evidence. | Review sentinel representation and HDR math before a new explicit transform-step design; test PQ/HLG and SDR tone mapping independently. |

The current module boundary must be a new portable
`org.graphiks.kanvas.color` implementation. It must not use AWT, ImageIO,
JNI, LCMS, or a system color-management service. General ICC, CICP, and HDR
support remains unavailable until that implementation and its current tests
exist; no sRGB fallback may be represented as support.

## Baseline conclusion

The project graph is ready to add an isolated color-management module, but the
current compatibility facade does not satisfy the professional static PNG
codec's mandatory ICC/CICP/HDR transformation capability. Historical SkCMS
ports provide algorithms and test evidence to audit selectively, not files to
revive.

## Task 7 Validation

- `rtk ./gradlew :color-management:test :kanvas:test checkProductionCodecImageClasspathNoJavaDesktop checkPureKotlinPngEncoderNoAwt --rerun-tasks`: passed (`BUILD SUCCESSFUL`; 44 executed tasks).
- `:color-management:test` and `:kanvas:test` passed; the production codec classpath and pure-Kotlin PNG encoder guards passed.
- Supported paths verified: bounded RGB matrix/TRC ICC, supported RGB ICC LUT routes, RGB CICP matrix profiles, PQ, and Rec.2020 HLG.
- Typed refusals verified: malformed ICC and configured resource limits, unsupported ICC profile endpoints/LUT directions, unsupported CICP matrix/primaries/transfer values, premultiplied LUT composition, and HDR/LUT composition.
- Repeated LUT and premultiplied HDR batch transforms produce identical RGBA output, including unchanged alpha storage.
