# Validation, Conformance, And Drift

Status: Draft
Date: 2026-06-13

## Purpose

Define how the complete pure Kotlin text stack is validated. Normative
validation is Kanvas-owned. External engines may be used for informative drift
reports, but they are not pass/fail oracles.

## Normative Oracle Policy

Normative validation uses:

- generated fixtures;
- bundled deterministic fonts;
- versioned expected parser/scaler/shaper/layout dumps produced from reviewed
  fixture inputs;
- Kanvas CPU oracle artifacts;
- GPU artifacts only when GPU support is claimed;
- serialized diagnostics and route dumps.

HarfBuzz, FreeType, Fontations, CoreText, DirectWrite, platform emoji engines,
browser layout, and Skia native output are optional comparison sources only.
Their output may reveal drift, but it does not define whether Kanvas passes.

Kanvas-owned does not mean self-rebaselining. Expected outputs must be
versioned artifacts or checked-in structured expectations. A generator may
produce those expectations, but updates require:

- deterministic generator source and input fixture provenance;
- old/new expectation diff in review;
- reason for the rebaseline;
- statement of whether behavior changed intentionally or a previous expectation
  was wrong;
- no automatic overwrite of goldens in ordinary test runs.

## Validation Layers

| Layer | Purpose |
|---|---|
| Table parser tests | Required/optional table parsing, malformed bounds, table replacement fixtures. |
| Scaler tests | `glyf`, CFF, CFF2, metrics, variation deltas, path hashes, bounds. |
| Shaping tests | Unicode segmentation, scripts, GSUB, GPOS, clusters, fallback runs, emoji sequences. |
| Paragraph tests | Rich text, line breaking, bidi lines, ellipsis, placeholders, metrics, selection, hit testing. |
| Glyph artifact tests | A8, SDF, atlas packing, cache keys, invalidation, upload plans. |
| Color glyph tests | COLRv0/v1, PNG bitmap glyphs, SVG glyphs, emoji dispatch. |
| Handoff tests | `DrawTextRun` payloads, artifact registry, route diagnostics, refusal paths. |
| CPU visual tests | Kanvas CPU oracle images or stat artifacts for rendered glyph output. |
| GPU evidence tests | Adapter-backed evidence for GPU support claims. |
| Drift reports | Non-normative comparison against Skia/HarfBuzz/FreeType or other external engines. |

## Fixture Strategy

Fixtures must be:

- small;
- deterministic;
- license-compatible;
- generated when practical;
- table-specific;
- committed with provenance;
- independent of native toolchains at test runtime.

Preferred fixture flow:

1. Start from a bundled deterministic font when possible.
2. Replace or append one table family under test.
3. Keep malformed fixtures narrow.
4. Keep shaping fixtures script-specific.
5. Keep color glyph fixtures split by COLR, bitmap, SVG, and emoji sequence.
6. Record generation source and expected route diagnostics.

## Target Fixture Manifest

Before implementation tickets claim complete-target coverage, the fixture set
must include at least these families:

| Family | Minimum fixture coverage |
|---|---|
| Font source/SFNT | Single TTF, TTC face index, malformed required table, malformed optional table, system-scan skipped file diagnostic. |
| TrueType scaler | Simple glyph, composite glyph, component transform, `gvar` simple delta, `gvar` composite delta, `avar` coordinate mapping. |
| CFF/CFF2 scaler | CFF Type 2 line/curve/flex glyph, local/global subroutines, malformed stack, unsupported operator, CFF2 `blend`/`vsindex`. |
| Shaping scripts | One positive and one refusal/diagnostic fixture for every row in the Kanvas required script matrix. |
| Paragraph | Rich style runs, bidi paragraph, hard/soft line breaks, ellipsis, placeholders, selection boxes, hit testing. |
| Color glyphs | COLRv0 layers, COLRv1 solid, gradients, transform, composite, clip, cycle refusal, budget refusal. |
| PNG bitmap glyphs | CBDT/CBLC PNG, sbix PNG, unavailable strike, malformed PNG, non-PNG payload refusal. |
| SVG glyphs | Static path glyph, gradient, transform, clip, `use` recursion, external resource refusal, unsupported feature refusal. |
| Emoji | VS15/VS16, skin tone, ZWJ family sequence, emoji fallback unavailable, color glyph unavailable. |
| A8/SDF artifacts | A8 atlas pack, SDF normalization, SDF transform refusal, atlas capacity refusal, stale generation refusal. |
| GPU handoff | `DrawTextRun` with registered artifact, unregistered artifact refusal, upload plan dump, no CPU-rendered texture route. |

## Evidence Artifacts

A promoted support claim needs:

- fixture or font provenance;
- text input and style input;
- Unicode data version;
- font source and typeface IDs;
- shaping dump;
- paragraph layout dump when paragraph layout is involved;
- glyph artifact dump;
- CPU oracle image, mask hash, or stat artifact;
- GPU image/stat artifact when GPU support is claimed;
- route diagnostics;
- refusal diagnostics when expected unsupported behavior is involved;
- command used to generate evidence.

## Drift Reports

External drift reports are useful but non-normative. They may compare:

- glyph IDs and clusters against HarfBuzz;
- outlines and metrics against FreeType or Fontations;
- paragraph metrics against Skia Paragraph;
- color glyph output against Skia;
- emoji sequence behavior against platform renderers.

Drift reports must label:

- external engine and version;
- fonts used;
- Unicode data version if known;
- platform facts;
- observed difference;
- whether Kanvas behavior intentionally differs.

External drift must not cause hidden changes to normative fixtures.

## GM And Dashboard Classification

Font/text rows must distinguish:

| Classification | Meaning |
|---|---|
| `target-supported` | Complete target contract has implementation evidence. |
| `current-supported` | Current code supports a narrower behavior documented elsewhere. |
| `tracked-gap` | Complete target requires support but implementation evidence is missing. |
| `expected-unsupported` | Behavior is outside complete Kanvas target and has stable refusal. |
| `fixture-gated` | A fixture or provenance issue blocks evidence. |
| `GPU-gated` | CPU/text support exists, but GPU artifact route or adapter evidence is missing. |
| `drift-only` | External comparison exists but no normative support claim changes. |

Generic "font missing" labels are not acceptable once a narrower blocker is
known.

## Refusal Evidence

Every refusal row needs:

- stable reason code;
- owning spec section;
- text range or glyph ID affected;
- CPU/GPU scope;
- fallback behavior if any;
- test or report asserting the refusal.

Silent fallback to a different font, glyph, layout, or CPU-rendered texture is
not valid evidence.

## Required Review Checks

Before a support claim is promoted:

- no route dump contains nondeterministic object identity;
- all caches include full key preimages;
- unsupported behavior has stable diagnostics;
- all external comparisons are labeled non-normative;
- GPU support claims have GPU evidence;
- CPU-only claims do not imply GPU renderer support.

## Acceptance Criteria

- Kanvas-owned fixtures and CPU oracles define pass/fail behavior.
- External engines provide drift visibility only.
- Each subsystem has focused tests and evidence dumps.
- Dashboard rows separate current support, complete-target gaps, GPU gates, and
  expected unsupported behavior.
- Refusals are precise, stable, and tested.
