# OpenJPEG JPEG 2000 lossless fixtures

The `*.j2k.base64` files are Base64 transport encodings of raw J2K Part 1
codestreams. Tests decode them before opening them, so their decoded bytes are
byte-for-byte the pinned fixtures described below.

## Provenance

* **Generator / pixel oracle:** OpenJPEG `opj_compress` and `opj_decompress`
  2.5.4, distributed by the OpenJPEG project under the 2-clause BSD licence.
  Neither executable, library nor JNI binding is included in or used by the
  Kanvas production runtime.

## Pinned fixtures

### One codeblock: 5×3

* **Source P2 PGM:** [`source.pgm`](source.pgm), SHA-256
  `2bdf55049e85c305eb510df45d10ce0150d92bac8663cf55e8e8d8b550fbd702`.
  It is the exact file passed to `opj_compress`; its 5×3 unsigned samples are,
  row-major: `0, 1, 127, 254, 255, 16, 32, 64, 128, 240, 17, 34, 68, 136, 238`.
* **Raw J2K SHA-256:**
  `078395a38f631ae8eb94476d01fe54a1d002a706ca7ad86849b15917cae937b4`.
* **Profile:** one unsigned 8-bit grayscale component; origin `(0,0)`; one
  5×3 tile; LRCP; one layer; one resolution; 64×64 code-block; default single
  precinct; reversible 5/3 transform; no quantization; no SOP/EPH/PPM/PPT.
  Its main header is `SOC SIZ COD QCD COM`; `QCD` uses `Sqcd=0x40,
  SPqcd=0x40`, the valid no-quantization representation for this profile.

### Two horizontal codeblocks: 96×17

* **Source P2 PGM:** [`source-two-codeblocks-96x17.pgm`](source-two-codeblocks-96x17.pgm),
  SHA-256 `8ea8d1148129457247b37c889415d3f5edbfde4dff929c09280618899a9eaeca`.
  The exact source exercises samples on both sides of the 64-pixel codeblock
  boundary and values on both sides of the unsigned level shift.
* **Raw J2K SHA-256:**
  `edcce815346bf3c8ffc439aea70b831428afb3d0f7b13d58292083c22f032ae7`.
* **Profile:** one unsigned 8-bit grayscale component; origin `(0,0)`; one
  96×17 tile; LRCP; one layer; one resolution; a declared 64×64 codeblock;
  default single precinct; reversible 5/3 transform; no quantization; no
  SOP/EPH/PPM/PPT. The tile contains exactly two horizontal codeblocks, with
  active extents 64×17 then 32×17. Its packet header has two codeblock entries
  and two separately bounded EBCOT bodies.

## Reproduction

```text
opj_compress -i source.pgm -o openjpeg-2.5.4-lossless-5x3.j2k \
  -n 1 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-5x3.j2k -o decoded.pgm

opj_compress -i source-two-codeblocks-96x17.pgm \
  -o openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k \
  -n 1 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k -o decoded.pgm
```

The optional `Jpeg2000OracleTest` requires the explicit Gradle property
`-Pjpeg2000OracleOpenJpeg=/absolute/path/to/opj_decompress`. It writes this
fixture set to temporary files, invokes only that supplied path, and compares
each P5 output exactly with its source PGM. Normal CI is Kotlin-only.

## Current boundary

Kanvas decodes these fixtures through its bounded raw JPEG 2000
Tier-2/MQ/EBCOT route. The two-codeblock claim validates packet-header BIO bit
stuffing, independent inclusion and zero-bit-plane tag trees with two leaves,
per-codeblock coding-pass and segment-length syntax, separately bounded
EBCOT bodies, MQ arithmetic decoding, and style-0 Tier-1 EBCOT bit-plane
passes. General JPEG 2000 profiles remain outside this fixture scope. No pixel
fallback to OpenJPEG is allowed.
