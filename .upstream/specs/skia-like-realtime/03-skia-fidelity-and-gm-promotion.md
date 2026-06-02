# 03 Skia Fidelity And GM Promotion

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

Kanvas should move closer to Skia CPU output through deliberate GM promotion
waves and visual diff burn-down. Inventory rows remain planning data until a
row has generated support/refusal evidence.

## M66 Skia GM Promotion Wave

### Goal

Promote 50-100 high-value GM-derived rows across practical rendering families.

### Selection Criteria

Prioritize rows that:

- exercise common product rendering behavior;
- close known unsupported clusters;
- cover Path AA, image filters, text, blend, gradients, bitmap sampling, and
  transforms;
- can produce row-specific artifacts;
- avoid dependency-gated substitutes;
- have clear upstream reference behavior.

## Reference Provenance Policy

Every promoted row must declare `referenceKind` before it can affect the
Skia-like fidelity score.

| Family | Preferred reference | Allowed fallback | Fidelity score rule |
|---|---|---|---|
| Path AA / coverage | Upstream Skia GM PNG or newly rendered Skia CPU reference from upstream GM logic. | Kanvas-authored scene only when tagged `reference.cpu-oracle`. | Counts toward Skia fidelity only with Skia reference. |
| Image filters | Upstream Skia GM PNG for equivalent DAG or Skia-rendered fixture. | CPU Kanvas oracle for DAG planner smoke. | CPU-only oracle counts toward breadth, not Skia fidelity. |
| Text/glyphs | Upstream Skia reference when font fixture matches; otherwise bundled Liberation CPU oracle. | Kanvas CPU glyph oracle with exact font file recorded. | Counts toward fidelity only when font/reference provenance is comparable. |
| Paint/blend/color | Upstream Skia GM PNG or scalar formula fixture derived from Skia semantics. | CPU Kanvas oracle for unsupported upstream coverage. | Formula fixture must cite operation and tolerance. |
| Bitmap/image sampling | Upstream Skia GM PNG or Skia-rendered fixture. | CPU Kanvas oracle for route smoke. | Counts toward fidelity only with Skia or Skia-derived fixture. |
| Runtime effects | Registered descriptor with CPU oracle plus generated WGSL. | Skia/SkSL API compatibility rows may be refused, but arbitrary SkSL is not a shader implementation target. | Counts toward runtime-effect breadth; Skia fidelity only if matching upstream behavior is documented. |

Rows with `reference.cpu-oracle` are valuable but must not be described as
"closer to Skia" unless a Skia reference exists. They can move rendering
breadth, runtime, and PM operability scores, but not Skia-like fidelity.

### Family Buckets

| Family | Examples | First target |
|---|---|---:|
| Path AA / coverage | stroke, curves, joins, caps, clips | 15-20 rows |
| Image filters | blur, offset, crop, matrix, small DAGs | 10-15 rows |
| Text/glyphs | simple text, transformed text, glyph masks | 8-12 rows |
| Paint/blend/color | blend modes, color matrix, gradients | 15-20 rows |
| Bitmap/image sampling | local matrix, filtering, tiling | 8-12 rows |
| Runtime effects | registered effects only | 3-5 rows |

## Promotion Rules

A promoted support row must include:

- inventory id;
- reference artifact;
- CPU artifact;
- GPU artifact when GPU-eligible;
- CPU and GPU route diagnostics;
- diff and stats artifacts;
- fallback reason `none` for GPU support;
- feature tags;
- source test or generation task;
- PM evidence link.

An unsupported row must include:

- inventory id;
- stable fallback reason;
- CPU/reference artifact where useful;
- refusal route diagnostics;
- reason why unsupported scope remains valuable;
- follow-up family mapping.

## Diff Burn-Down

M69 should treat visual differences as a backlog:

```text
promoted row
  -> diff classification
  -> root-cause bucket
  -> fix or accepted threshold
  -> regression gate
```

Root-cause buckets:

- coverage edge delta;
- blend/premul mismatch;
- color-space mismatch;
- sampling/local-matrix mismatch;
- filter bounds mismatch;
- glyph mask/raster delta;
- unsupported fallback accidentally routed as support;
- reference mismatch.

## M86 Fidelity Burn-Down Wave 2

M86 is the first post-M85 burn-down pass over the cumulative M66 GM/reference
wave. It is not allowed to improve the PM readiness score merely by
reclassifying evidence. It may move readiness only when a later patch adds new
generated support rows, Skia-comparable references, measured gates, or
before/after rendered artifacts for a visual fix.

Required M86 evidence:

- ranked candidate list with family, `referenceKind`, expected GPU route, PM
  value, and risk;
- full support/refusal row preservation from the selected generated evidence;
- root-cause classification for visible diffs and expected unsupported rows;
- explicit high-value remediation targets;
- statement that CPU-oracle rows do not count as Skia-comparable fidelity;
- statement that no global threshold was weakened;
- sprint report explaining whether renderer fixes were actually applied.

For a row to count as "fixed" in M86 or later, the evidence must include:

- the row id and root cause;
- before and after CPU/GPU/reference/diff artifacts;
- old and new similarity/threshold payloads;
- family-specific threshold rationale when changed;
- dashboard gate output with 0 `tracked-gap` and 0 unexpected `fail`.

If no renderer fix is included, the sprint must say that clearly and treat the
output as burn-down planning evidence, not a completed visual correction.

## Threshold Policy

Do not lower thresholds globally. Threshold changes must be:

- family-specific;
- justified by upstream/reference behavior;
- documented in the row or family spec;
- reviewed in sprint report;
- paired with a non-claim if visual parity is limited.

## GM Wave Reports

Each wave must produce:

- selection report;
- promoted/rejected table;
- family counters;
- support/refusal deltas;
- representative screenshots;
- PM summary;
- next wave candidate list.

## Acceptance

- Generated dashboard remains 0 `tracked-gap` and 0 unexpected `fail` for
  promoted rows.
- Unsupported rows remain visible.
- Inventory status is not support status.
- PM can filter progress by feature family.
- At least one diff burn-down report is produced for M69.
