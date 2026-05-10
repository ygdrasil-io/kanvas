# SkCMS

`modules/skcms/` is a **stand-alone, plain-C, dependency-free
colour-management library**. It parses ICC profiles, fits canonical
transfer-function forms, decomposes gamut transforms, and JIT-compiles
optimised pixel-format conversions. SkCMS is what `SkColorSpace`
delegates to under the hood — see [Color Management](color-management.md)
for the C++ wrappers — but it is also vendored verbatim into Chromium,
ANGLE, and other projects that need ICC handling without pulling in
all of Skia.

The module is unusual in Skia: a single public header
(`skcms.h` -> `src/skcms_public.h`), a single translation unit
(`skcms.cc`), no C++ in the public API, and per-microarchitecture
SIMD builds compiled separately and dispatched at runtime.

## Layout

| File | Role |
|------|------|
| `skcms.h` | Public umbrella header (just includes `src/skcms_public.h`) |
| `src/skcms_public.h` | Entire C public API: types, parse, transform, transfer-function fitting |
| `skcms.cc` | Aggregating translation unit; pulls in the implementation `.cc`s |
| `src/skcms_internals.h` | Cross-TU helpers shared between the SIMD variants |
| `src/skcms_Transform.h`, `src/Transform_inl.h` | Pixel-transform pipeline core, included into each ISA variant |
| `src/skcms_TransformBaseline.cc` | Portable / scalar fallback build of the transform |
| `src/skcms_TransformHsw.cc` | Haswell (AVX2 + FMA + F16C) build, runtime-dispatched on x86-64 |
| `src/skcms_TransformSkx.cc` | Skylake-X (AVX-512) build |
| `version.sha1`, `README.chromium` | Vendor metadata for downstream consumers |

## Core data types — `src/skcms_public.h`

The API is entirely POD `struct`s; no constructors, no allocations.
Profiles live in caller-owned memory.

- `skcms_TransferFunction` — the canonical 7-parameter piecewise
  curve `g, a, b, c, d, e, f`. A `skcms_TFType` discriminator
  distinguishes plain `sRGBish`, `PQish`, `HLGish`, and the unit
  `PQ` / `HLG` HDR variants. Helpers
  `skcms_TransferFunction_makePQ`, `…_makeScaledHLGish`, and
  `…_isSRGBish` cover the standard ones; `…_invert` and
  `…_eval` evaluate them.
- `skcms_Curve` — a tagged union: either an inline
  `skcms_TransferFunction` (parametric) or pointers to a 1D LUT
  (`table_8` / `table_16`).
- `skcms_Matrix3x3` / `skcms_Matrix3x4` — row-major matrices for
  primary / gamut transforms.
- `skcms_A2B` / `skcms_B2A` — full ICC `mAB`/`mBA` pipelines:
  optional A-curves + N-D CLUT, optional M-curves + 3x4 matrix,
  required B-curves. These describe arbitrary device-to-PCS and
  PCS-to-device conversions for non-3-channel profiles (CMYK, n-channel
  presses, lookup-driven cameras).
- `skcms_CICP` — Coding-Independent Code Points (ITU-T H.273) used
  by HEIF / AVIF / video metadata.
- `skcms_ICCProfile` — the parsed profile: raw buffer, tag count,
  `data_color_space`, `pcs`, plus `has_trc` / `has_toXYZD50` /
  `has_A2B` / `has_B2A` / `has_CICP` quick-access fields populated
  by `skcms_Parse`.

Canonical instances are exposed through
`skcms_sRGB_profile()`, `skcms_XYZD50_profile()`,
`skcms_sRGB_TransferFunction()`, `…_Inverse_…`, and
`skcms_Identity_TransferFunction()`.

## Parsing & approximation

`skcms_Parse(buf, n, profile)` validates an ICC blob and fills the
quick-access fields. `skcms_ParseWithA2BPriority` lets callers
choose the A2B0 / A2B1 / A2B2 (perceptual / relative-colorimetric /
saturation) tag preference order.

Beyond raw parsing, SkCMS includes a numerical fitter that looks at
arbitrary table-based curves and tries to recognise them as one of
the canonical analytical forms (`skcms_ApproximateCurve`,
`skcms_TRCs_AreApproximateInverse`). Hits let downstream code skip
the LUT and use a closed-form transfer function — both faster and
exact under further composition.

`skcms_PrimariesToXYZD50` derives a 3x3 gamut matrix from
chromaticity coordinates. `skcms_AdaptToXYZD50` chromatic-adapts an
arbitrary D-illuminant white point onto D50 (the ICC PCS reference)
using the Bradford matrix.

## Transform pipeline

The pixel-conversion API takes source bytes, a source profile, a
destination format, and a destination profile, and runs the
sequence `unpack -> linearise -> gamut adapt -> tone map -> reencode -> pack`
once for the call. Source / destination pixel formats are described
by an `skcms_PixelFormat` enum (`RGBA_8888`, `BGRA_1010102`,
`RGBA_hhhh`, `RGBA_ffff`, …) and an `skcms_AlphaFormat`
(`Unpremul`, `PremulAsEncoded`, `Opaque`).

The implementation is a small interpreter over an op list assembled
on the fly from the profile pair (`Transform_inl.h`). The same op
list is built into three machine variants:

- `skcms_TransformBaseline.cc` — portable scalar / 4-wide intrinsics.
- `skcms_TransformHsw.cc` — Haswell (AVX2 + FMA + F16C).
- `skcms_TransformSkx.cc` — Skylake-X (AVX-512).

At runtime, `skcms.cc` cpuid-dispatches to the best variant
available. This is how SkCMS can claim "comparable to LCMS but ~10x
faster" on common conversions: most workloads (sRGB <-> Display P3
<-> sRGB-linear half-float) compile down to a few SIMD-vectorised ops
without any LUT lookups at all.

## Embedding into SkColorSpace

`SkColorSpace` (in `include/core/SkColorSpace.h`) is a thin C++
wrapper that holds an `skcms_TransferFunction` and an
`skcms_Matrix3x3`. Conversions go through `SkColorSpaceXformSteps`,
which decomposes the source-to-destination transform into ordered
steps (premultiplication, transfer-function decode, gamut matrix,
tone-map, transfer-function encode) and ultimately calls
`skcms_Transform` for bulk pixel work. See
[Color Management](color-management.md) for the full wrapper design,
[Bitmap, Pixmap & Image](bitmap-pixmap-image.md) for the consuming
APIs, and [HDR & Gainmaps](hdr-and-gainmaps.md) for the PQ / HLG
specifics.

## Source map

| File | Role |
|------|------|
| `skia-main/modules/skcms/src/skcms_public.h` | Entire public API |
| `skia-main/modules/skcms/skcms.cc` | TU aggregator + runtime dispatch |
| `skia-main/modules/skcms/src/Transform_inl.h` | Per-op pipeline interpreter (included into ISA variants) |
| `skia-main/modules/skcms/src/skcms_TransformBaseline.cc` | Portable build |
| `skia-main/modules/skcms/src/skcms_TransformHsw.cc` | AVX2 / FMA build |
| `skia-main/modules/skcms/src/skcms_TransformSkx.cc` | AVX-512 build |

## Cross-references

- [Color Management](color-management.md) — `SkColorSpace`,
  `SkColorSpaceXformSteps`, and the C++ side of the conversions.
- [HDR & Gainmaps](hdr-and-gainmaps.md) — the PQ / HLG transfer
  functions SkCMS encodes via `skcms_TransferFunction_makePQ` and
  `…_makeHLG`.
- [Bitmap, Pixmap & Image](bitmap-pixmap-image.md) — pixel storage
  whose conversions are dispatched through SkCMS.
