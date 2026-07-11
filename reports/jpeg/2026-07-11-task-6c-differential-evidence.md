# JPEG Task 6c — differential evidence and refusal boundary

## Decision

This subtask does **not** claim decoding support for JPEG differential frames.
It adds a stable, pre-entropy refusal for all six differential Start Of Frame
(SOF) markers when `JpegDocument.decode()` is called without a validated
hierarchical reference:

| SOF | Entropy coding | Sample process | Result in this subtask |
| --- | --- | --- | --- |
| `0xC5` (SOF5) | Huffman | differential sequential DCT | `jpeg.differential.reference.required` |
| `0xC6` (SOF6) | Huffman | differential progressive DCT | `jpeg.differential.reference.required` |
| `0xC7` (SOF7) | Huffman | differential lossless | `jpeg.differential.reference.required` |
| `0xCD` (SOF13) | arithmetic | differential sequential DCT | `jpeg.differential.reference.required` |
| `0xCE` (SOF14) | arithmetic | differential progressive DCT | `jpeg.differential.reference.required` |
| `0xCF` (SOF15) | arithmetic | differential lossless | `jpeg.differential.reference.required` |

The diagnostic result is `Codec.Result.kUnimplemented`; its offset is the
first differential SOF marker. No entropy bytes are decoded, no component
state is fabricated, and no non-differential decoder is reused as a fallback.

## Normative dependency

ITU-T T.81 defines these as differential frame processes. Their output is
formed relative to reference components from the JPEG hierarchy; an `EXP`
marker can also change the reference resolution. A standalone `JpegDocument`
therefore cannot reconstruct pixels from a differential SOF without the
document-level DHP/EXP hierarchy, frame ordering, reference-component mapping,
and validated reference state.

Primary reference consulted: [ITU-T T.81 / ISO/IEC 10918-1](https://www.itu.int/rec/T-REC-T.81)
(marker and hierarchical-process definitions; consulted 2026-07-11).

## Reproducible structural probes

`JpegDifferentialRefusalTest` starts with the repository-generated
`CodecTestFixtures.simpleGrayscaleJpeg()` and deterministically replaces its
SOF0 marker with each marker in the table. `JpegDocument.open()` still validates
the container and exposes the changed SOF. The test then proves the exact
diagnostic, `kUnimplemented` result, null bitmap, and SOF offset.

These are **structural refusal probes**, not differential decode fixtures and
not pixel oracles: their entropy stream is intentionally never interpreted.
They must not be used to claim any differential DCT, arithmetic, lossless,
point-transform, or DRI reconstruction support.

Run:

```text
rtk ./gradlew :codec:jpeg:test --tests org.graphiks.kanvas.codec.jpeg.JpegDifferentialRefusalTest --no-daemon
```

## Oracle/corpus audit

- The checked-in `jpeg-arithmetic` corpus proves only non-differential SOF9
  and SOF10 DCT decoding with libjpeg-turbo 3.2.0 pixel oracles.
- Its README records that the same libjpeg-turbo tool refuses arithmetic
  lossless SOF11 generation. This remains outside 6c and has no support claim.
- No checked-in or independently reproducible DHP/EXP hierarchy contains a
  differential reference frame for SOF5/6/7 or SOF13/14/15. No trustworthy
  pixel oracle exists for these six modes in the current repository.

## Task 6d handoff

Before replacing this refusal with support, 6d must provide all of the
following in one document-level implementation and evidence package:

1. Parse and validate DHP, each frame, and any EXP scaling before decoding
   dependent frames.
2. Build immutable reference component state and reject missing, mismatched,
   cyclic, or resolution-incompatible references with dedicated diagnostics.
3. Route the six SOF markers to the appropriate Huffman/arithmetic and
   sequential/progressive/lossless differential reconstruction only after that
   state is available; preserve DRI reset rules and lossless point transforms.
4. Add reproducible full-hierarchy fixtures and independent pixel oracles for
   every promoted SOF family. A marker mutation or a decoded residual alone is
   insufficient evidence.

Until those conditions are met, `jpeg.differential.reference.required` is the
required stable behavior.
