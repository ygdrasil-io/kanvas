# ICC fixture manifest

## Kanvas-authored fixtures

The following fixtures were authored for the Kanvas parser tests on
2026-07-10. Attribution: Kanvas contributors. License/SPDX identifier:
`CC0-1.0`. Their matrix and transfer-function inputs are colorimetric facts
based on the Task 1 `ColorProfiles` constants; no third-party ICC binary was
used to generate them. The sRGB transfer parameters were quantized to signed
15.16 values and adjusted within `7e-5` of the Task 1 coefficients so both
branches meet exactly at `d`. This is a writer property of the synthetic
fixtures; the parser also accepts valid monotone upward parametric gaps.

| File | SHA-256 | Profile class | Color space / PCS | Provenance and purpose |
| --- | --- | --- | --- | --- |
| `srgb-matrix-trc.icc` | `4d944a40632a8c62479ad9ab2b56f17e4070ef24de3bb05e169813e78ffe1328` | ICC v4.3 display (`mntr`) | sRGB / XYZ D50 | Synthetic 496-byte matrix/TRC profile with required `desc`, `cprt`, and `wtpt` tags, zero reserved fields, a recomputed MD5 profile ID, and a standards-ordered, exactly continuous ICC `para` type 4 sRGB transfer function. Positive parser fixture. |
| `display-p3-matrix-trc.icc` | `48786ef9677c1bf7c347e9989c115f80207af0d45f066ad584074dc0d2e789d2` | ICC v4.3 display (`mntr`) | Display P3 / XYZ D50 | Synthetic 496-byte matrix/TRC profile with the same required structural fields, exactly continuous shared TRC, recomputed MD5 profile ID, and `ColorProfiles.displayP3()` matrix. Wide-gamut positive parser fixture. |
| `invalid-tag-offset.icc` | `bf05cf55525e685173b04cfa2c70a5fa730cbf1648ddd4c60beca727733e59d7` | Deliberately malformed derivative of the synthetic sRGB fixture | RGB / XYZ D50 | The `rXYZ` tag offset was changed from 396 to 500, four bytes beyond the declared 496-byte profile. Negative range-validation fixture. |
| `rgb-lut-a2b-b2a.icc` | `d4ce01da0a211433b24770ba8043a98d38073667682d37dc05b40c443d08b47b` | ICC v4.3 display (`mntr`) | Synthetic RGB swap / XYZ D50 | Kanvas-authored 49,764-byte CC0 profile with conforming `A2B0` and `B2A0` `mft2` routes, the Display-class required `desc`/`cprt`/`wtpt` tags, contiguous aligned tag elements, zero reserved fields, and recomputed ICC Profile ID `86d537006b823260dd6fb9251bcb65b3`. The A2B route uses 4,096-entry sampled sRGB decoding, a 16-bit CLUT mapping RGB to the D50 XYZ values of BGR, and identity output tables. The inverse route uses the PCSXYZ encoding scale, a signed fixed-point inverse matrix, a 16-bit identity CLUT, and 4,096-entry sampled sRGB output tables. |

The synthetic profiles use contiguous four-byte-aligned tag elements. Their
three TRC signatures exactly share one 40-byte payload; the `para` parameters
are encoded in ICC order `g,a,b,c,d,e,f` using signed 15.16 fixed point.

The LUT fixture's independent golden vectors are:

| Direction | Input RGBA | Expected RGBA | Tolerance |
| --- | --- | --- | --- |
| `mft2` `A2B0`, then matrix/TRC sRGB destination | `[0.25, 0.50, 0.75, alpha]` | `[0.75, 0.50, 0.25, alpha]` | `0.002` per colour channel |
| matrix/TRC sRGB source, then `mft2` `B2A0` | `[0.25, 0.50, 0.75, alpha]` | `[0.75, 0.50, 0.25, alpha]` | `0.002` per colour channel |

These vectors deliberately rule out an identity transform and clamp-only
behavior. The profile and vectors were generated from the published sRGB
transfer equation and the Task 1 D50 sRGB matrix constants; no third-party
binary or copyrighted profile payload was used.

The standards-defined 16-bit PCSXYZ A2B path also has an independent
reference-CMM golden. LittleCMS 2.19 `transicc`, with precomputation disabled
and relative colorimetric intent (`-c0 -t1`), converted encoded device RGB
`63.75 127.5 191.25` through this fixture to built-in `*XYZ` as
`31.7566 27.2797 6.4392`. The corresponding normalized PCS assertion is
`[0.317566, 0.272797, 0.064392]` with tolerance `3e-5`. LittleCMS was used
only to record this vector; it is not a build or runtime dependency.

`mft1` with PCSXYZ has no standard 8-bit PCSXYZ encoding. Kanvas continues to
test its documented direct-normalized, forward-only interpretation using an
in-memory synthetic payload, but that test is implementation-specific and is
not presented as reference-CMM or cross-CMM evidence.

## Independent fixtures

| File | SHA-256 | Profile class | Color space / PCS | Provenance, license, and purpose |
| --- | --- | --- | --- | --- |
| `compact-dci-p3-v4.icc` | `58ac463ea778e9b70692971dd4ba9071363a03aba77043894162b1ad6c051da7` | ICC v4.2 display (`mntr`) | DCI-P3 / XYZ D50 | Unmodified `profiles/DCI-P3-v4.icc`, downloaded 2026-07-10 from the independent [saucecontrol/Compact-ICC-Profiles repository](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/profiles/DCI-P3-v4.icc). The repository releases all included profiles under its [CC0 1.0 Universal license](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/license) (`CC0-1.0`), permitting copying, modification, distribution, and use without permission. This fixture independently exercises v4 `mluc` metadata, an exact-size shared `para` type 0 TRC, required tags, and RGB matrix parsing. |
| `compact-display-p3-v4.icc` | `cb51de38e482ee974c0c76b9689e16aad04bad16e226fed2f30c842d15ff3a3d` | ICC v4.2 display (`mntr`) | Display P3 / XYZ D50 | Unmodified `profiles/DisplayP3-v4.icc`, downloaded 2026-07-10 from [saucecontrol/Compact-ICC-Profiles](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/profiles/DisplayP3-v4.icc), under the repository's [CC0 1.0 Universal license](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/license) (`CC0-1.0`). This fixture independently exercises v4 `mluc` metadata and a shared exact-size `para` type 3 TRC with a valid quantization-induced upward gap at its branch threshold. |
| `icc-rec709-v2.icc` | `4d10744483b3448daceacc880566b004cd90aa0ca535f651c8c88ba61646c965` | ICC v2.0 display (`mntr`) | ITU-R BT.709 reference display / XYZ D50 | Unmodified `ITU-RBT709ReferenceDisplay.icc`, downloaded 2026-07-10 from the [ICC Profile Library](https://registry.color.org/profile-library/profiles/ITU-RBT709ReferenceDisplay.icc). Its embedded copyright is `Copyright International Color Consortium, 2011`. The ICC Profile Library terms permit ICC-owned profiles to be copied, distributed, embedded, made, used, and sold without restriction; altered versions must remove the original identification and copyright. It is a positive bounded-compatibility fixture: its non-conforming one-entry `curv` declares 16 bytes rather than the canonical 14, and is accepted only because the size is exactly `align4(14)` and its two surplus bytes are zero. Other surplus sizes or non-zero surplus bytes return `icc.curve.range`. |

All independent fixtures are checked in unchanged. Their SHA-256 values match
the bytes served by their source URLs on the download date.
