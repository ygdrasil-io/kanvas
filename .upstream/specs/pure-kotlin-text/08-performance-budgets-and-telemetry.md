# Performance Budgets And Telemetry

Status: Draft
Date: 2026-06-13

## Purpose

Define initial performance telemetry and indicative budgets for the complete
pure Kotlin text stack. These budgets guide architecture and regression review;
they are not blocking release gates until Kanvas baselines are measured and a
separate acceptance update promotes them.

## Measurement Policy

Every metric must record:

- environment;
- device and adapter facts when GPU is involved;
- JVM/Kotlin runtime facts when relevant;
- font source set;
- Unicode data version;
- cold or warm cache state;
- sample count;
- median, p90, and max when repeated;
- memory and upload counters where applicable.

Single-run timings are diagnostic only.

## Required Telemetry

Font/scaler metrics:

- font source scan time;
- font parse time;
- table cache hit/miss;
- scaler path extraction time;
- CFF/CFF2 charstring time;
- variation application time;
- glyph bounds and metrics lookup time.

Shaping metrics:

- Unicode segmentation time;
- bidi analysis time;
- script itemization time;
- fallback lookup time;
- GSUB lookup count and time;
- GPOS lookup count and time;
- glyph count;
- cluster count;
- diagnostic count.

Paragraph metrics:

- style run count;
- line break opportunity count;
- shaped run count;
- line count;
- layout time;
- hit-test index build time;
- selection box query time.

Glyph artifact metrics:

- representation route counts;
- A8 generation time;
- SDF generation time;
- COLR paint graph evaluation time;
- SVG glyph evaluation time;
- PNG decode time;
- atlas pack time;
- atlas hit/miss/eviction;
- upload bytes and upload count;
- cache memory.

GPU handoff metrics:

- artifact registry lookup time;
- upload dependency count;
- `DrawTextRun` route count;
- GPU route selected/refused;
- stale generation refusals;
- artifact budget refusals.

## Indicative Budgets

The following are non-blocking initial targets for warm-cache behavior on a
developer desktop. They are architecture signals, not release gates.

| Class | Indicative target |
|---|---|
| Single-line UI text | Shape and layout under 0.5 ms for up to 128 UTF-16 code units after fonts are warm. |
| Paragraph UI text | Layout under 2.5 ms for up to 2,000 UTF-16 code units and 40 style runs after fonts are warm. |
| Long document viewport | Incremental relayout under 8 ms for a visible viewport after a local edit that does not invalidate all paragraphs. |
| Emoji/color-heavy line | Shape, color dispatch, and artifact planning under 3 ms for up to 64 glyph clusters after color font data is warm. |
| A8 atlas steady state | No upload for cache hits; under 256 KiB upload for a typical new UI text batch. |
| SDF atlas generation | Amortized generation under 4 ms for a batch of 64 medium-complexity glyphs after scaler data is warm. |
| SVG glyph cold path | Diagnostic budget under 10 ms per first-use glyph for bounded static SVG glyphs, with cache hits expected afterward. |
| Paragraph hit testing | Under 0.1 ms for point hit tests after layout result is built. |

Mobile, browser-hosted, and CI budgets require separate baselines before they
become gates.

## Cache Budgets

Initial cache domains:

- font table cache;
- scaler outline cache;
- shaping result cache;
- paragraph layout cache;
- A8 glyph mask cache;
- SDF glyph cache;
- COLR paint graph cache;
- SVG glyph plan cache;
- PNG bitmap glyph decode cache;
- atlas artifact cache;
- GPU upload plan cache.

Each cache must define:

- key preimage;
- memory accounting;
- eviction policy;
- generation or invalidation token;
- telemetry counters;
- deterministic dump mode.

## Warmup Policy

Warmup may prepare:

- bundled font metadata;
- fallback catalog;
- common UI font faces;
- common script data;
- shader-independent glyph artifact metadata.

Warmup must not hide support gaps or route unsupported text through broad CPU
fallback. Warmup failures produce diagnostics and keep product behavior
deterministic.

## Budget Promotion

Indicative budgets can become blocking gates only after:

- baseline hardware classes are named;
- variance policy is defined;
- sample counts and warm/cold states are fixed;
- failure triage owner is named;
- rollback criteria are documented;
- PM/report artifacts show stable trends.

Until then, performance reports are required telemetry and advisory evidence.

## Acceptance Criteria

- Every major subsystem emits telemetry.
- Cache keys and memory budgets are inspectable.
- CPU generation and GPU upload costs are separated.
- Indicative budgets guide architecture without becoming hidden gates.
- Future blocking performance gates require an explicit target/spec update.
