# ICC fixture manifest

## Kanvas-authored fixtures

The following fixtures were authored for the Kanvas parser tests on
2026-07-10. Attribution: Kanvas contributors. License/SPDX identifier:
`CC0-1.0`. Their matrix and transfer-function inputs are colorimetric facts
copied from the Task 1 `ColorProfiles` constants; no third-party ICC binary was
used to generate them.

| File | SHA-256 | Profile class | Color space / PCS | Provenance and purpose |
| --- | --- | --- | --- | --- |
| `srgb-matrix-trc.icc` | `2bd44e930ac110a28963e50729203c020fe3e84aa431042bc05c3e6fafb4c59a` | ICC v4.3 display (`mntr`) | sRGB / XYZ D50 | Synthetic 496-byte matrix/TRC profile with required `desc`, `cprt`, and `wtpt` tags, zero reserved fields, an MD5 profile ID, and a standards-ordered ICC `para` type 4 sRGB transfer function. Positive parser fixture. |
| `display-p3-matrix-trc.icc` | `21da86ce09dc9d61a80b506bc118b2e45d6242f97c7be52c20ae7de12a4d3f53` | ICC v4.3 display (`mntr`) | Display P3 / XYZ D50 | Synthetic 496-byte matrix/TRC profile with the same required structural fields and `ColorProfiles.displayP3()` matrix. Wide-gamut positive parser fixture. |
| `invalid-tag-offset.icc` | `927f05dbe65d51e581e5c4ccbac9f36690f821513d2408894875f8359d42c236` | Deliberately malformed derivative of the synthetic sRGB fixture | RGB / XYZ D50 | The `rXYZ` tag offset was changed from 396 to 500, four bytes beyond the declared 496-byte profile. Negative range-validation fixture. |

The synthetic profiles use contiguous four-byte-aligned tag elements. Their
three TRC signatures exactly share one 40-byte payload; the `para` parameters
are encoded in ICC order `g,a,b,c,d,e,f` using signed 15.16 fixed point.

## Independent interoperability fixture

| File | SHA-256 | Profile class | Color space / PCS | Provenance, license, and purpose |
| --- | --- | --- | --- | --- |
| `icc-rec709-v2.icc` | `4d10744483b3448daceacc880566b004cd90aa0ca535f651c8c88ba61646c965` | ICC v2.0 display (`mntr`) | ITU-R BT.709 reference display / XYZ D50 | Unmodified `ITU-RBT709ReferenceDisplay.icc`, downloaded 2026-07-10 from the [ICC Profile Library](https://registry.color.org/profile-library/profiles/ITU-RBT709ReferenceDisplay.icc). Its embedded copyright is `Copyright International Color Consortium, 2011`. The ICC Profile Library terms permit ICC-owned profiles to be copied, distributed, embedded, made, used, and sold without restriction; altered versions must remove the original identification and copyright. This fixture independently exercises v2 `desc`/`text`, shared single-value `curv` TRCs, required tags, and RGB matrix parsing. |

The ICC interoperability fixture is checked in unchanged. Its SHA-256 matches
the bytes served by the source URL on the download date.
