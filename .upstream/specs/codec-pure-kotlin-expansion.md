# Pure Kotlin Codec Expansion — AVIF, JPEG XL, HEIF, RAW

Status: Draft
Date: 2026-06-30

## Purpose

Remove the "dependency-gated" classification for AVIF, JPEG XL, HEIF, and RAW
formats. Implement pure Kotlin decoders for all four families. Video remains out
of scope.

## Architecture

Shared ISOBMFF parser (`codec/isobmff/`) serves both AVIF and HEIF:

```
codec/
├── isobmff/       → Shared ISOBMFF box parser
├── avif/          → AVIF decode (ISOBMFF + AV1 still)
├── heif/          → HEIF container decode (reuses ISOBMFF)
├── jxl/           → JPEG XL decode (standalone)
└── raw/           → DNG + CR2 + NEF + ARW decode
```

## Phase 1 — ISOBMFF (`codec/isobmff/`)

- Parse box hierarchy (ftyp, moov, meta, mdat, iloc, iinf, iprp, ipma, colr,
  pixi, av1C, hvcC)
- Extract items and properties
- CICP, ICC, pixel format metadata
- Used by AVIF and HEIF
- Target: ~400 lines, 1 sub-agent

## Phase 2 — AVIF (`codec/avif/`)

- Parse AV1 bitstream: sequence header, frame header, tile info
- Decode AV1 intra-only tiles (no inter-frame prediction)
- Support 8/10/12-bit, 4:2:0/4:2:2/4:4:4, HDR (PQ/HLG)
- Integrate with `Codec` API: `getICCProfile()`, `getImage()`
- Target: ~2000 lines, 2 sub-agents

## Phase 3 — JPEG XL (`codec/jxl/`)

- Parse JXL codestream boxes
- Decode VarDCT (lossy) and Modular (lossless)
- Support 8/16-bit, float32, HDR
- Target: ~2000 lines, 2 sub-agents

## Phase 4 — RAW (`codec/raw/`)

- DNG: TIFF/EP + EXIF + CFAPattern → simple bilinear demosaic
- CR2 (Canon), NEF (Nikon), ARW (Sony) via public reverse-engineering
- Each format: parse container, extract thumbnail JPEG, expose raw CFA data
- Target: ~800 lines, 1 sub-agent

## Phase 5 — HEIF (`codec/heif/`)

- Reuse shared ISOBMFF parser
- Parse HEIF/HEIC items
- Delegate codec decode via AVIF when possible
- Target: ~200 lines, 1 sub-agent

## Non-Goals

- Video decode (AV1 inter-frame, HEVC inter-frame)
- HEVC codec bitstream decode (patent-encumbered)
- RAW advanced demosaicing (AMaZE, LMMSE, etc.)
- Gainmap / HDR tone-mapping beyond metadata exposure
