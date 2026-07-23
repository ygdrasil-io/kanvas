# JPEG hierarchical DHP/EXP differential corpus

These are binary, reproducible full-hierarchy fixtures for the six
differential JPEG SOF families. They are pixel-oracle inputs for the pure
Kotlin Kanvas decoder; they are not marker-mutation probes.

## Provenance

* **Reference implementation:** `https://github.com/thorfdbg/libjpeg`
  (ISO/IEC 10918-7 reference software; the source itself calls this a complete
  ITU-T T.81 / ISO/IEC 10918-1 implementation).
* **Pinned commit:** `2503e8f9b7f014cd21bbd6e24aaa5c7a232e9798`
  (2026-06-03T16:41:00+02:00).
* **Reference licence:** GPLv3 or ITU Software Licence Annex A Option 2,
  recorded by the upstream `README.license.gpl` and `README.license.itu`.
  No reference source is copied into Kanvas.
* **Reference build:** macOS clang, `make SETTINGS=clang COMPILER_CMD=clang++ debug`.
* **Source pixels:** `source-gradient-4x4.pgm`, SHA-256
  `5fdf4b07a3bc9c986fa8c85e431116cd318714d06192038e938411dba7a8b922`.
  It was made once with local `libjpeg-turbo 3.2.0` `djpeg` from Kanvas’s
  checked-in non-hierarchical arithmetic fixture
  `codec/jpeg/src/test/resources/jpeg-arithmetic/gradient-sequential-sof9.jpg`.
  libjpeg-turbo was used only to make the ordinary P5 source; it is not an
  oracle for, nor capable of decoding, DHP/EXP hierarchy streams.
* **SOF15 context/restart source:** `source-context-8x8.pgm`, SHA-256
  `162efe9f003ace832662686a9172fe7226a28ff82580ec3d83a15f489aeaa03a`.
  It is a checked-in P5 grayscale pattern with alternating signs, changing
  magnitudes, and endpoint samples; it was authored for this corpus rather
  than derived from a decoder.
* **SOF15 threshold source:** `source-context-threshold-16x16.pgm`, SHA-256
  `9289424b2da37c52392eb321b2d363ee99ff2f4d6c7f7f4f29d7a423c6cbb3d5`.
  Its low-quality base intentionally creates signed residuals at `|D| = 2`
  and above, crossing the DAC `L=0,U=1` classification threshold.

The fixtures were generated and decoded by the pinned external reference
binary. Every encoded output was regenerated a second time and had the same
SHA-256. The PGM paired with each JPEG is the independently maintained
reference pixel oracle that Kanvas tests must match; Kanvas must not call the
reference implementation at runtime or fall back to a non-differential decoder.

## Reproduction commands

```text
jpeg -y 2 -h    -q 100 -c source-gradient-4x4.pgm sof5-huffman-sequential-exp11.jpg
jpeg -y 2 -v -h -q 100 -c source-gradient-4x4.pgm sof6-huffman-progressive-exp11.jpg
jpeg -y 0 -h    -q 100 -c source-gradient-4x4.pgm sof7-huffman-lossless-exp00.jpg
jpeg -y 2 -a    -q 100 -c source-gradient-4x4.pgm sof13-arithmetic-sequential-exp11.jpg
jpeg -y 2 -a -v -q 100 -c source-gradient-4x4.pgm sof14-arithmetic-progressive-exp11.jpg
jpeg -y 0 -a    -q 100 -c source-gradient-4x4.pgm sof15-arithmetic-lossless-exp00.jpg
jpeg -y 0 -a -q 100 -c -z 8 source-context-8x8.pgm sof15-arithmetic-lossless-context-rst.jpg
jpeg -y 0 -a -q 10 -c -z 32 source-context-threshold-16x16.pgm sof15-arithmetic-lossless-context-threshold-rst.jpg

jpeg <fixture.jpg> <fixture.pgm>
```

`-y 0` is the reference tool’s documented hierarchical lossless mode: it
emits a non-differential DCT reference then a differential lossless frame.
It is used for SOF7 and SOF15 because invoking `-p` as the first hierarchical
frame hits that tool’s `DQT marker missing` encoder defect before producing a
codestream. This corpus therefore does not treat that failing command as a
fixture or an oracle.

All streams contain `DHP` (`FFDE`). SOF5/6/13/14 use `EXP=0x11` (horizontal
and vertical expansion); SOF7/15 use the explicit no-expansion marker
`EXP=0x00` required by the reference mode. The lossless oracles round-trip to
the P5 source exactly.

The checked-in full-hierarchy pixel tests require matching dimensions and a
maximum absolute error of one final grayscale sample. This applies to the
quality-100 fixtures because every one begins with a lossy DCT base frame
(SOF1 or SOF9): Kanvas's existing floating IDCT and the reference's fixed-point
IDCT can round that base one sample apart, including at 0/255 endpoints. The
one-sample tolerance is limited to final DCT-base composition. Separately, the SOF15 4×4
test asserts the complete raw arithmetic-lossless residual plane exactly as
`[0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0]` before composition. The
8×8 SOF15 oracle exercises the QM contexts and DRI/RST state reset.
Hierarchical composition follows the reference merger by retaining signed
intermediate samples; normal pixel composition is the single saturating
boundary.

The 16×16 `threshold` fixture is a targeted raw arithmetic-lossless residual
regression fixture, not a final-pixel acceptance fixture. Its quality-10 SOF9 base exposes an
existing arithmetic-DCT reconstruction difference in Kanvas of up to 22
samples against the fixed-point reference, outside this hierarchy task's
one-sample DCT evidence envelope. The test asserts selected independent
reference residual anchors before and after DRI=32 restarts, including `-55`,
`-152`, and `-49`; these anchors are not an exhaustive 16×16 raw-residual
oracle. It also requires both `|D| = 2` and `|D| > 2`. This isolates and proves
the SOF15 QM lossless path without presenting that inherited SOF9 variance as
a hierarchy result.

## Inventory and hashes

| Differential SOF | Encoded fixture SHA-256 | Pixel oracle SHA-256 |
| --- | --- | --- |
| SOF5 Huffman sequential, EXP=11 | `b20193b1b4f736a33797303f6ab818cec33407dbbe3ba5a096fc4f151d86eef7` | `4dfe687051685ea767da5bda891af35d6ef70125f8c2ba0e48dc358585775d65` |
| SOF6 Huffman progressive, EXP=11 | `dcc106472e7cead7b790e19143efc5c12f807a14505d04c3956a865c894ffee1` | `4dfe687051685ea767da5bda891af35d6ef70125f8c2ba0e48dc358585775d65` |
| SOF7 Huffman lossless, EXP=00 | `ba3f8948289c2a659599520d5dcc3087b88324d61412a6c26e7e172e52eed6c8` | `5fdf4b07a3bc9c986fa8c85e431116cd318714d06192038e938411dba7a8b922` |
| SOF13 arithmetic sequential, EXP=11 | `868dd8fac9c1b058c5295ebf6132ba43d315c949b2727306de2ba9d99d514381` | `4dfe687051685ea767da5bda891af35d6ef70125f8c2ba0e48dc358585775d65` |
| SOF14 arithmetic progressive, EXP=11 | `a86816a98fe0b2997a3d7ca721210e8ffd8dc9dbaaf226a5e05d2b86cfa22d73` | `4dfe687051685ea767da5bda891af35d6ef70125f8c2ba0e48dc358585775d65` |
| SOF15 arithmetic lossless, EXP=00 | `81a04950ef4116a2f94a94420ca667c5015539c4c018c47c396a12bcf545216a` | `5fdf4b07a3bc9c986fa8c85e431116cd318714d06192038e938411dba7a8b922` |
| SOF15 arithmetic lossless, DRI=8/RST, EXP=00 | `400bd4a0ca41fa47970864db19831b30ab11a1c98228f8cb90be3e38e7d059c7` | `162efe9f003ace832662686a9172fe7226a28ff82580ec3d83a15f489aeaa03a` |
| SOF15 raw threshold context, DRI=32/RST, EXP=00 | `22fdfe7ec81c27fd2ad0445379f36d4dea29a6203cfa7c862443ef699b5b15c2` | `9289424b2da37c52392eb321b2d363ee99ff2f4d6c7f7f4f29d7a423c6cbb3d5` |

These small 4×4 fixtures prove full document hierarchy, frame ordering,
reference selection, EXP handling, and every differential entropy/sample
family. The 8×8 SOF15 fixture additionally certifies arithmetic-lossless
context transitions and DRI/RST reset; the 16×16 threshold fixture crosses the
`L=0,U=1` magnitude classification boundary. For normative interpretation,
T.81 is the controlling specification; the pinned implementation is separate
interoperability evidence. Its `ACLosslessScan::QMContextSet` classifies a
difference as the middle class through `abs(D) <= (1 << U)`, and selects the
high magnitude set only when `abs(Db) > (1 << U)`; this is the rule exercised
by the threshold fixture. Non-zero lossless point transforms and
multi-component differential-lossless sampling still need dedicated,
separately provenanced fixtures before either behaviour is promoted.
