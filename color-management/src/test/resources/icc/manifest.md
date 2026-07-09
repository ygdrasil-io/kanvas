# ICC fixture manifest

All three fixtures were authored for the Kanvas color-management parser tests
on 2026-07-10. Attribution: Kanvas contributors. License/SPDX identifier:
CC0-1.0. Their numeric matrix and transfer-function inputs are colorimetric facts copied from
the Task 1 `ColorProfiles` constants; no third-party ICC binary was copied.

| File | SHA-256 | Profile class | Color space / PCS | Provenance and purpose |
| --- | --- | --- | --- | --- |
| `srgb-matrix-trc.icc` | `7277eaebaa8077b3a8b05f58936b6998a5c83300039a42a30099954ea7ca8033` | ICC v4.3 display (`mntr`) | RGB / XYZ D50 | Synthetic 336-byte matrix/TRC profile generated from `ColorProfiles.sRGB()` fixed-point matrix values and the ICC `para` type 4 encoding of the sRGB transfer function. Positive parser fixture. |
| `display-p3-matrix-trc.icc` | `5c38910008b58539d2ac0784f10f033c85a3832d5fd7c9f0c4ab2ee518a8d352` | ICC v4.3 display (`mntr`) | Display P3 RGB / XYZ D50 | Synthetic 336-byte matrix/TRC profile generated from `ColorProfiles.displayP3()` fixed-point matrix values and the same sRGB `para` type 4 transfer function. Wide-gamut positive parser fixture. |
| `invalid-tag-offset.icc` | `27e53ebbd1ea80db8c780c94a8b23847ad4b8e94c0d32551ab54551d46cc1e7f` | Deliberately malformed derivative of the sRGB fixture | RGB / XYZ D50 | The `rXYZ` tag offset was changed from 216 to 340, four bytes beyond the declared 336-byte profile. Negative range-validation fixture. |

The fixture generator wrote ICC integers in big-endian order, shared one
40-byte `para` payload among `rTRC`, `gTRC`, and `bTRC`, and quantized all
matrix/curve values to signed 15.16 fixed point as required by those tag types.
