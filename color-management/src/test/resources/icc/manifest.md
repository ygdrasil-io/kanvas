# ICC fixture manifest

## Kanvas-authored fixtures

The following fixtures were authored for the Kanvas parser tests on
2026-07-10. Attribution: Kanvas contributors. License/SPDX identifier:
`CC0-1.0`. Their matrix and transfer-function inputs are colorimetric facts
based on the Task 1 `ColorProfiles` constants; no third-party ICC binary was
used to generate them. The sRGB transfer parameters were quantized to signed
15.16 values and adjusted within `7e-5` of the Task 1 coefficients so both
branches meet exactly at `d`; this avoids representing a discontinuous transfer
function after ICC quantization.

| File | SHA-256 | Profile class | Color space / PCS | Provenance and purpose |
| --- | --- | --- | --- | --- |
| `srgb-matrix-trc.icc` | `4d944a40632a8c62479ad9ab2b56f17e4070ef24de3bb05e169813e78ffe1328` | ICC v4.3 display (`mntr`) | sRGB / XYZ D50 | Synthetic 496-byte matrix/TRC profile with required `desc`, `cprt`, and `wtpt` tags, zero reserved fields, a recomputed MD5 profile ID, and a standards-ordered, exactly continuous ICC `para` type 4 sRGB transfer function. Positive parser fixture. |
| `display-p3-matrix-trc.icc` | `48786ef9677c1bf7c347e9989c115f80207af0d45f066ad584074dc0d2e789d2` | ICC v4.3 display (`mntr`) | Display P3 / XYZ D50 | Synthetic 496-byte matrix/TRC profile with the same required structural fields, exactly continuous shared TRC, recomputed MD5 profile ID, and `ColorProfiles.displayP3()` matrix. Wide-gamut positive parser fixture. |
| `invalid-tag-offset.icc` | `bf05cf55525e685173b04cfa2c70a5fa730cbf1648ddd4c60beca727733e59d7` | Deliberately malformed derivative of the synthetic sRGB fixture | RGB / XYZ D50 | The `rXYZ` tag offset was changed from 396 to 500, four bytes beyond the declared 496-byte profile. Negative range-validation fixture. |

The synthetic profiles use contiguous four-byte-aligned tag elements. Their
three TRC signatures exactly share one 40-byte payload; the `para` parameters
are encoded in ICC order `g,a,b,c,d,e,f` using signed 15.16 fixed point.

## Independent fixtures

| File | SHA-256 | Profile class | Color space / PCS | Provenance, license, and purpose |
| --- | --- | --- | --- | --- |
| `compact-dci-p3-v4.icc` | `58ac463ea778e9b70692971dd4ba9071363a03aba77043894162b1ad6c051da7` | ICC v4.2 display (`mntr`) | DCI-P3 / XYZ D50 | Unmodified `profiles/DCI-P3-v4.icc`, downloaded 2026-07-10 from the independent [saucecontrol/Compact-ICC-Profiles repository](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/profiles/DCI-P3-v4.icc). The repository releases all included profiles under its [CC0 1.0 Universal license](https://github.com/saucecontrol/Compact-ICC-Profiles/blob/master/license) (`CC0-1.0`), permitting copying, modification, distribution, and use without permission. This fixture independently exercises v4 `mluc` metadata, an exact-size shared `para` type 0 TRC, required tags, and RGB matrix parsing. |
| `icc-rec709-v2.icc` | `4d10744483b3448daceacc880566b004cd90aa0ca535f651c8c88ba61646c965` | ICC v2.0 display (`mntr`) | ITU-R BT.709 reference display / XYZ D50 | Unmodified `ITU-RBT709ReferenceDisplay.icc`, downloaded 2026-07-10 from the [ICC Profile Library](https://registry.color.org/profile-library/profiles/ITU-RBT709ReferenceDisplay.icc). Its embedded copyright is `Copyright International Color Consortium, 2011`. The ICC Profile Library terms permit ICC-owned profiles to be copied, distributed, embedded, made, used, and sold without restriction; altered versions must remove the original identification and copyright. The authentic profile is retained as interoperability evidence, but this strict subset deliberately returns `icc.curve.range`: its one-entry `curv` declares a 16-byte tag size that includes two alignment bytes, while the canonical element size excluding alignment padding is 14 bytes. |

Both independent fixtures are checked in unchanged. Their SHA-256 values match
the bytes served by their source URLs on the download date.
