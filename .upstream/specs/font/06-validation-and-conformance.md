# Font Validation And Conformance

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Define how font work is validated. This spec owns focused tests, GM
classification, generated evidence requirements, route diagnostics, fallback
reason stability, and dashboard readiness for font scenes.

It does not own generic dashboard UX or non-font rendering gates.

## Validation Layers

| Layer | Purpose |
|---|---|
| Unit parser tests | Prove OpenType table parsing, malformed table behavior, and generated fixtures. |
| API tests | Prove `SkFont`, `SkTypeface`, `SkFontMgr`, `SkTextBlob`, and `SkShaper` contracts. |
| CPU visual tests | Prove CPU rendering against upstream references or Kanvas-owned fixtures. |
| WebGPU tests | Prove adapter-backed GPU behavior or explicit refusal. |
| GM rebaseline | Classify upstream GM row status and blockers. |
| Dashboard evidence | Promote font scenes only with artifacts, diffs, route diagnostics, and status policy. |

## Required Focused Commands

For OpenType parser and manager changes:

```bash
rtk ./gradlew --no-daemon :font:test :kanvas:test
```

For public font API changes:

```bash
rtk ./gradlew --no-daemon :font:test :kanvas:test
```

For shaping changes:

```bash
rtk ./gradlew --no-daemon :font:test :kanvas:test
```

For WebGPU text route changes:

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test :integration-tests:skia:test
```

For broader font changes:

```bash
rtk ./gradlew --no-daemon :font:test :kanvas:test
```

## GM Classification Policy

GM rows must use precise blocker wording:

| Classification | Meaning |
|---|---|
| `PORTED` | The current row has enough local evidence for its scoped contract. |
| `PARTIAL` | Some sub-slices are active but the row still has known open behavior. |
| `TEST_DISABLED` | The row is intentionally disabled with a stable blocker. |
| `font-gated` | A real font delivery is missing; do not substitute. |
| `fixture-gated` | Missing fixture or unsupported fixture format blocks the row. |
| `implementation` | Internal implementation remains, but not because of missing dependency. |
| `gpu-intractable` | Upstream row depends on Ganesh/Graphite or GPU-only internals outside target. |

Do not use generic "font missing" wording when a narrower reason exists.

## Font Dashboard Scene Requirements

A promoted font scene should include:

- scene id and exact support scope;
- font source and fixture provenance;
- text input and shaping mode;
- glyph route diagnostics;
- CPU image and stats;
- GPU image and stats when GPU support is claimed;
- reference image or CPU oracle statement;
- diff artifacts;
- fallback reason for unsupported or refused rows;
- raw JSON route data.

Rows may be:

- `pass` only when required evidence exists;
- `tracked-gap` only when a closure path is concrete;
- `expected-unsupported` when refusal is intentional and reason-coded;
- `fail` when a support claim regresses.

## Suggested Font Scene Families

Font scene generation should be incremental:

| Family | First useful scenes |
|---|---|
| Simple outline text | Latin glyph run, size/scale/skew, text blob positions. |
| Typeface selection | bundled Liberation family/style matching and fallback glyph. |
| Kerning | `kern` and limited `GPOS` pair-position fixture. |
| Variation | simple `gvar` axis with outline delta evidence. |
| Shaping | explicit `SkShaper` clusters and fallback diagnostics. |
| Color glyph | COLRv0 layered outline and one bounded COLRv1 slice. |
| Bitmap glyph | CBDT/CBLC or sbix generated fixture once internal decode exists. |
| Emoji | expected-unsupported rows until internal table dispatch and sequence shaping land. |
| SDF/LCD | expected-unsupported rows until internal route contracts and artifacts exist. |
| Glyph mask | expected-unsupported or tracked-gap until text-owned mask/atlas handoff exists. |

## Refusal And Fallback Evidence

Every refusal must have:

- stable reason code;
- owning spec section;
- test or report that asserts the reason;
- clear statement of whether CPU, GPU, or both refuse;
- statement of whether monochrome outline fallback is used.

Do not replace a refusal by silently drawing a different font, different glyph,
or platform-rendered image.

## Performance Validation

Font performance is not release-blocking until explicit baseline ownership
exists. Initial font performance can report:

- glyph parsing time;
- glyph path generation time;
- shaping time;
- glyph mask generation time;
- atlas hit/miss counters;
- WebGPU upload count and bytes;
- SDF generation time.

Metrics are informational until a separate performance gate names owners,
sample count, environment, variance policy, and rollback behavior.

## Acceptance Criteria

- New font support has focused unit/API tests.
- New visual support has reference, CPU/GPU or refusal, diff/stat, and route
  artifacts.
- Gated GM rows have precise blocker names.
- Dashboard rows distinguish outline, shaped, color, emoji, SDF, LCD, and mask
  routes.
- No validation path depends on external font libraries.
