# Native Color and ICC Types Design

## Goal

Remove the `SkICC`, `SkcmsICCProfile`, and `SkColorSpace` compatibility
facades.  The color-management module becomes the sole owner of ICC
provenance and image color-space metadata; codec and image APIs consume those
native types directly.

## Constraints

- This is an incubation API: public API breakage is allowed.
- `math:color` remains the owner of matrices, transfer functions, and numeric
  comparison operations.
- `color-management` must not depend on `kanvas` or on any codec module.
- ICC bytes are immutable at the public boundary: construction snapshots input
  bytes and getters return copies.
- Existing Matrix/TRC acceptance and typed refusal codes remain stable during
  this migration.  Extending HDR or LUT support is a separate feature.

## Module ownership

```text
math:color
  ColorMatrix3x3F32, ColorTransferFunction, numerical predicates

color-management
  ColorProfile, ColorSpace, IccProfile, ImageColorSpace, ICC parser/writer

kanvas
  SkImageInfo, SkBitmap, SkImage; each carries ImageColorSpace

codec:api and format codecs
  produce ImageColorSpace and expose IccProfile provenance
```

## Native types

### IccProfile

`org.graphiks.kanvas.color.icc.IccProfile` is the immutable representation of
an encoded ICC artifact.  It contains a parsed `ColorProfile` and the exact
source bytes.  It provides `bytes`, `size`, `tagCount`, `hasTrc`, and
`hasToXyzD50` as read-only information derived from that artifact.

`IccProfile.parse(bytes, limits)` returns a typed parse result.  It wraps the
existing `IccProfileParser` rather than duplicating ICC parsing.  A
Matrix/TRC `ColorProfile` can be serialized through `IccProfileWriter`; ICC
writing is therefore used directly by codecs instead of through `SkICC`.

`Codec.getICCProfile()` returns `IccProfile?`.  It reports only embedded ICC
provenance, never a synthesized profile made from CICP or default sRGB
metadata.

### ImageColorSpace

`org.graphiks.kanvas.color.ImageColorSpace` is the image-facing color metadata
carrier.  It owns a `ColorProfile`, optionally retains its source
`IccProfile`, and exposes the current support status and refusal code.  It
has factories for sRGB, linear sRGB, custom Matrix/TRC profiles, and
profile-aware ICC input.

The first migration preserves the existing compatibility policy:

- RGB Matrix/TRC profiles are `SUPPORTED`.
- grayscale, HDR, LUT-only, and explicitly unsupported profiles are retained
  but reported as `UNSUPPORTED` with the current refusal codes.

The native type derives sRGB and linear predicates from `ColorProfile`; it
does not own matrix or transfer-function arithmetic.  `ColorSpace` remains
the narrower named public descriptor obtained by classification, not the
replacement for arbitrary image profiles.

## Migration

1. Replace `SkICC.WriteToICC` callers with a color-management ICC writer and
   remove the inert `SkICC.Make` facade.
2. Add `IccProfile`; move the defensive byte ownership and parse wrapping out
   of `SkcmsICCProfile`.  Change every format codec and `Codec.getICCProfile`
   to use it.
3. Add `ImageColorSpace`; migrate `SkImageInfo`, `SkBitmap`, encoders,
   decoders, and Kanvas conversion adapters from `SkColorSpace`.
4. Remove `SkcmsCompat.kt` and the `SkColorSpace`/profile-status facade code
   from `SkCodecCompat.kt`, then delete their facade-only tests.

## Error handling

Malformed embedded ICC bytes remain a parse refusal (`null` at codec
boundaries where that is the established contract).  A valid profile that the
current Matrix/TRC image facade cannot represent produces an
`ImageColorSpace` with `UNSUPPORTED` status and its stable refusal code.
Kanvas conversion continues to map profile, gamut, and transfer failures to
its existing typed codec error.

## Verification

- Unit-test ICC byte immutability, parsed-profile retention, and Matrix/TRC
  serialization in `color-management`.
- Unit-test ImageColorSpace factories, status/refusal classification, and
  named ColorSpace conversion in `color-management`.
- Run the codec API and all affected format-codec tests to prove ICC
  provenance and image info behavior are preserved.
- Compile Kanvas and Skia integration-test consumers; assert no production
  source imports `SkICC`, `SkcmsICCProfile`, or `SkColorSpace`.
