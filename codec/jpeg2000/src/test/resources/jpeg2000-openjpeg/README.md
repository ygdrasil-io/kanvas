# OpenJPEG JPEG 2000 lossless fixtures

The `*.j2k.base64` files are Base64 transport encodings of raw J2K Part 1
codestreams. Tests decode them before opening them, so their decoded bytes are
byte-for-byte the pinned fixtures described below.

## Provenance

* **Generator / pixel oracle:** OpenJPEG `opj_compress` and `opj_decompress`
  2.5.4, distributed by the OpenJPEG project under the 2-clause BSD licence.
  The optional executable is validation evidence only: neither executable,
  library nor JNI binding is included in or used by the Kanvas production
  runtime or normal Kotlin-only CI.

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

### Two reversible 5/3 levels: 8×8

* **Source P2 PGM:** [`source-ndecomp2-8x8.pgm`](source-ndecomp2-8x8.pgm),
  SHA-256 `776f58efb28e49ed6656bd5d331757c8546b99fda4754f8d3ca7e3ee36601ed9`.
  Its samples are generated in row-major order by `(17 * x + 29 * y) & 255`.
* **Raw J2K SHA-256:**
  `75abf991e34966f1e929baee2666b63f53f8c49be900c35e8c5ec14ae56b2a78`.
* **Profile:** one unsigned 8-bit grayscale component; origin `(0,0)`; one
  8×8 tile; LRCP; one layer; three resolutions (`Ndecomp=2`); a declared
  64×64 code-block; default single precinct; reversible 5/3 transform; no
  quantization; no SOP/EPH/PPM/PPT. The tile contains three contiguous LRCP
  packets: `LL2`, then `HL2/LH2/HH2`, then `HL1/LH1/HH1`. Each subband has
  one separately bounded EBCOT body.
* **Odd counterpart:** the same source as the high-pass regression below is
  also encoded as the 5×5, three-resolution fixture
  `openjpeg-2.5.4-lossless-ndecomp2-5x5-random.j2k.base64`, SHA-256
  `fba82a726aa8992d948e763c443b48491e0106d1b3cdc23b9d8b00390dd1a03f`.
  It covers both odd 5/3 synthesis edges across two levels and a 25-pass
  EBCOT body.

### Odd high-pass context regression: 5×5

* **Source P2 PGM:** [`source-ndecomp2-5x5-random.pgm`](source-ndecomp2-5x5-random.pgm),
  SHA-256 `6e2ee7ce0880c67527f1a1dd6fed83703de8b66943dd9d623d1efb0ba5c8b612`.
  The non-empty detail bands exercise the directional `HL` zero-coding context
  where horizontal and vertical significant-neighbour counts differ.
* **Raw J2K SHA-256:** `4d20cb6c3b76efbb54d3bba59490e0f0174c118ad053e15ea62bbe89ee561875`.
* **Profile:** the same lossless, one-tile, one-layer, 64×64-codeblock
  constraints as above, with two resolutions (`Ndecomp=1`). It has an odd
  5×5 frame and one LL plus one HL/LH/HH packet.

### JP2 wrapper pixel route: 5×5

* **Source P5 PGM:** generated with the 25 nonuniform unsigned grayscale
  samples, row-major: `0, 255, 1, 128, 64, 16, 224, 63, 192, 170, 85, 9,
  128, 254, 127, 32, 240, 45, 209, 66, 190, 12, 99, 153, 201`. The exact
  generated 5×5 PGM SHA-256 was
  `cc0ac2eedf419fe48476315fd61535af5f26a37dd19dd51f25d76f16298b79f1`.
  Its exact 25-byte pixel payload SHA-256 is
  `60b8e4ad938a578733ca6b1584abd17ead3583b7e00466b3578d2091e4d30323`.
* **JP2 SHA-256:**
  `b4472ef2e88573ff29f3b1813e10b1ec413d1591532fcb7aa0a88af42f77ac45`.
* **Profile:** OpenJPEG 2.5.4; one 5×5 tile; LRCP; one layer; one resolution
  (`Ndecomp=0`); one unsigned 8-bit grayscale component; 64×64 codeblock;
  reversible 5/3 transform; no quantization. Its `jp2h` contains the matching
  `ihdr` and one safe `colr` declaration only: method 1, precedence 0,
  approximation 0, enumerated colorspace 17 (grayscale).
* **Expected Kanvas pixels:** the 5×5 JP2 opens through `Codec.MakeFromData`
  and decodes to opaque `RGBA_8888`; for each source sample `v`, the output
  word is `0xFFvvvvvv` (`A=255`, `R=G=B=v`).
* **OpenJPEG generation and validation:**

  ```text
  opj_compress -i source-5x5.pgm \
    -o openjpeg-2.5.4-lossless-ndecomp0-5x5.jp2 \
    -n 1 -b 64,64 -r 1 -p LRCP
  opj_decompress -i openjpeg-2.5.4-lossless-ndecomp0-5x5.jp2 \
    -o decoded-5x5.pgm
  ```

  OpenJPEG 2.5.4 reproduced the source pixel payload byte-for-byte, with the
  payload checksum above. This executable is opt-in validation evidence, not
  a runtime dependency or a normal-CI requirement.

## Reproduction

```text
opj_compress -i source.pgm -o openjpeg-2.5.4-lossless-5x3.j2k \
  -n 1 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-5x3.j2k -o decoded.pgm

opj_compress -i source-two-codeblocks-96x17.pgm \
  -o openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k \
  -n 1 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-two-codeblocks-96x17.j2k -o decoded.pgm

opj_compress -i source-ndecomp2-8x8.pgm \
  -o openjpeg-2.5.4-lossless-ndecomp2-8x8.j2k \
  -n 3 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-ndecomp2-8x8.j2k -o decoded.pgm

opj_compress -i source-ndecomp2-5x5-random.pgm \
  -o openjpeg-2.5.4-lossless-ndecomp2-5x5-random.j2k \
  -n 3 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-ndecomp2-5x5-random.j2k -o decoded.pgm

opj_compress -i source-ndecomp2-5x5-random.pgm \
  -o openjpeg-2.5.4-lossless-ndecomp1-5x5-random.j2k \
  -n 2 -b 64,64 -r 1 -p LRCP
opj_decompress -i openjpeg-2.5.4-lossless-ndecomp1-5x5-random.j2k -o decoded.pgm

```

The optional `Jpeg2000OracleTest` requires the explicit Gradle property
`-Pjpeg2000OracleOpenJpeg=/absolute/path/to/opj_decompress`. It writes this
fixture set to temporary files, invokes only that supplied path, and compares
each P5 output exactly with its source PGM. The JP2 oracle additionally checks
the 5×5 source payload against Kanvas's opaque grayscale RGBA output. Normal
CI is Kotlin-only; OpenJPEG is validation evidence only.

## Current boundary

Kanvas decodes these fixtures through its bounded raw JPEG 2000
Tier-2/MQ/EBCOT route. The two-codeblock claim validates packet-header BIO bit
stuffing, independent inclusion and zero-bit-plane tag trees with two leaves,
per-codeblock coding-pass and segment-length syntax, separately bounded
EBCOT bodies, MQ arithmetic decoding, and style-0 Tier-1 EBCOT bit-plane
passes. The Ndecomp=2 fixture further validates three adjacent LRCP packet
headers, the seven subband order, and two inverse reversible 5/3 synthesis
stages.

The bounded Part-1 structural foundation also retains general `SIZ`/`COD`/`QCD`
main-header syntax and bounded `SOT` tile plans. This structural ownership is
intentionally separate from public pixels: a general structural document must
not reach `Codec.MakeFromData` and reports
`jpeg2000.container.pixel.unimplemented` from `Jpeg2000Document.decode()`.
Public pixels remain exclusively the raw/JP2 fixture profile documented above;
this foundation does not claim general pixels or a general Tier-2
implementation.

The JP2 fixture is pixel-decodable only for the same bounded grayscale
profile, with no `colr` or one exact enumerated grayscale `colr` documented
above. Well-formed `colr` declarations, including ICC, non-grayscale and
multiple declarations, are retained structurally but never enable the pixel
facade or an ICC/color pipeline; malformed `colr` remains a structural error.
Palette (`pclr`), component mapping (`cmap`), channel definition (`cdef`,
including alpha), and multi-component JP2 profiles remain outside the pixel
facade. Until independently verified follow-on work lands, the pixel facade
also refuses multi-component and multi-tile J2K, every progression other than
LRCP, irreversible 9/7, MCT, JPX, MJ2, HTJ2K, and every encoder route. General
JPEG 2000 pixels remain outside this fixture scope. No pixel fallback to
OpenJPEG is allowed.
