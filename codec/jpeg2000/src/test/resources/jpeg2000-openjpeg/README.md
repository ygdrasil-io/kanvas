# OpenJPEG JPEG 2000 lossless fixture

`openjpeg-2.5.4-lossless-5x3.j2k.base64` is the Base64 transport encoding of
one raw J2K Part 1 codestream.  The test decodes it before opening it; the
encoded bytes are therefore byte-for-byte the fixture described below.

## Provenance

* **Generator / pixel oracle:** OpenJPEG `opj_compress` and `opj_decompress`
  2.5.4, distributed by the OpenJPEG project under the 2-clause BSD licence.
  Neither executable, library nor JNI binding is included in or used by the
  Kanvas production runtime.
* **Source P2 PGM SHA-256:**
  `2bdf55049e85c305eb510df45d10ce0150d92bac8663cf55e8e8d8b550fbd702`.
  Its 5×3 unsigned samples are, row-major:
  `0, 1, 127, 254, 255, 16, 32, 64, 128, 240, 17, 34, 68, 136, 238`.
* **Raw J2K SHA-256:**
  `078395a38f631ae8eb94476d01fe54a1d002a706ca7ad86849b15917cae937b4`.
* **Profile:** one unsigned 8-bit grayscale component; origin `(0,0)`; one
  5×3 tile; LRCP; one layer; one resolution; 64×64 code-block; default single
  precinct; reversible 5/3 transform; no quantization; no SOP/EPH/PPM/PPT.
  Its main header is `SOC SIZ COD QCD COM`; `QCD` uses `Sqcd=0x40,
  SPqcd=0x40`, the valid no-quantization representation for this profile.

## Reproduction

```text
opj_compress -i source.pgm -o openjpeg-2.5.4-lossless-5x3.j2k \
  -n 1 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-5x3.j2k -o decoded.pgm
```

The optional `Jpeg2000OracleTest` requires the explicit Gradle property
`-Pjpeg2000OracleOpenJpeg=/absolute/path/to/opj_decompress`. It writes this
fixture to a temporary file, invokes only that supplied path, and compares the
P5 output exactly with the listed samples. Normal CI is Kotlin-only.

## Current boundary

Kanvas opens the fixture structurally and then returns
`jpeg2000.entropy.unimplemented` / `kUnimplemented`. This fixture is not yet a
Kanvas pixel-decode claim. A real decoder must first implement and validate the
packet header (BIO bit-stuffing, inclusion and zero-bit-plane tag trees,
coding-pass and segment-length syntax), MQ arithmetic decoder, and Tier-1
EBCOT bit-plane passes. No pixel fallback to OpenJPEG is allowed.
