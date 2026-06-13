# OpenType Layout Shaping Engine

Status: Draft
Date: 2026-06-13

## Purpose

Define the complete pure Kotlin shaping target. The shaping engine converts
Unicode text and resolved fonts into glyph runs with stable clusters,
positions, features, fallback diagnostics, and script-specific behavior.

This spec owns text-to-glyph shaping. It does not own paragraph line breaking,
visual line layout, glyph mask generation, or GPU artifact planning.

## Entry Points

The shaping engine is explicit. Supported entry points:

- single-run shaping for `SkShaper`-style APIs;
- paragraph shaping invoked by the paragraph engine;
- text blob compatible shaping when text rather than glyph IDs is provided;
- direct glyph ID runs that bypass shaping but still require metrics and
  artifact planning.

`SkCanvas.drawString` remains simple unless a future accepted compatibility
spec changes that behavior.

## Shaping Pipeline

Target pipeline:

```text
TextInput
  -> Unicode normalization policy
  -> grapheme clusters
  -> bidi analysis
  -> script itemization
  -> language and feature resolution
  -> font fallback run splitting
  -> cmap glyph mapping
  -> GSUB substitutions
  -> GPOS positioning
  -> run compaction
  -> ShapedGlyphRun diagnostics
```

Each stage must be independently testable and dumpable.

## Unicode Version Policy

Kanvas must pin a Unicode data version for:

- script data;
- bidi classes;
- grapheme cluster rules;
- line break classes used by paragraph layout;
- emoji properties;
- variation selector handling.

Updating the Unicode data version is a deliberate compatibility change with
fixture updates and drift reports. Product behavior must not depend on the JDK
Unicode version unless that version is captured in diagnostics.

## Kanvas Required Script Matrix

The complete target requires support for the scripts in this table. Scripts not
listed may be added later through explicit matrix rows.

| Script family | OpenType scripts | Required defaults | Minimum fixtures | Refusal boundary |
|---|---|---|---|---|
| Latin | `latn` | `ccmp`, `locl`, `liga`, `rlig`, `clig`, `calt`, `kern`, `mark`, `mkmk` when tables exist. | ASCII run, accented marks, `fi` ligature, contextual alternate, kerning pair. | Unsupported discretionary features diagnose without blocking required defaults. |
| Greek | `grek` | Same simple-script feature class as Latin, with Greek script/language gating. | Monotonic run, polytonic marks, kerning pair. | Missing script-specific feature data falls back only when glyph mapping remains valid. |
| Cyrillic | `cyrl` | Same simple-script feature class as Latin, with Cyrillic script/language gating. | Basic Cyrillic run, localized Serbian/Bulgarian form when fixture exists, kerning pair. | Localized form absence diagnoses; base glyph mapping remains valid. |
| Hebrew | `hebr` | Bidi run direction, `ccmp`, `locl`, `mark`, `mkmk`, `kern` when present. | RTL word, niqqud marks, mixed LTR/RTL paragraph handoff. | Paragraph-level bidi required diagnostics when single-run shaping lacks paragraph context. |
| Arabic | `arab` | Joining, `init`, `medi`, `fina`, `isol`, `rlig`, `liga`, `calt`, `mark`, `mkmk`, cursive attachment. | Isolated/initial/medial/final forms, lam-alef, marks, cursive attachment, mixed bidi. | Missing cursive or mark positioning refuses shaped support for that run; no presentation-form approximation can claim support. |
| Devanagari | `deva`, `dev2` | Indic syllable formation, reordering, `nukt`, `akhn`, `rphf`, `blwf`, `half`, `pstf`, `vatu`, `pres`, `abvs`, `blws`, `psts`, `haln`, `dist`, `abvm`, `blwm`. | Consonant cluster, reph, pre-base matra, below-base form, mark placement. | Unsupported syllable state or required phase refuses the affected cluster. |
| Thai | `thai` | Script itemization, `ccmp`, `locl`, `mark`, `mkmk`, kerning when present. | Base+above/below marks, tone marks, mixed Latin/Thai run. | Dictionary word breaking is paragraph-owned; missing dictionary refinement diagnoses layout, not glyph shaping. |
| CJK simple | `hani`, `kana`, `hira`, `hang` | Direct mapping, `vert`/`vrt2` when vertical text is requested, `locl`, variation selectors through `cmap` format 14. | Han run, kana run, vertical alternate, variation selector. | Complex ruby or East Asian line breaking refinements are paragraph-owned diagnostics. |
| Emoji | `Zsye`, `Zsym` plus Unicode emoji data | Variation selectors, emoji modifiers, ZWJ sequence shaping, color glyph dispatch hooks. | VS15/VS16, skin tone, family ZWJ sequence, fallback emoji font. | Unsupported sequence refuses the sequence as a unit, not individual code point approximations. |

The target is architecture-complete for more scripts, but the support claim is
limited to the matrix. Unsupported scripts must emit stable diagnostics instead
of silently approximating complex behavior.

Each matrix row must have:

- script tag selection tests;
- default feature list tests;
- at least one positive fixture and one diagnostic/refusal fixture;
- cluster mapping assertions;
- fallback behavior assertions;
- drift report hooks when an external comparison is useful.

Adding a new required script means adding a new matrix row before
implementation tickets are cut.

## GSUB Target

The pure Kotlin GSUB engine must support the lookup behavior needed by the
required script matrix:

- single substitutions;
- multiple substitutions;
- alternate substitutions when an accepted feature requests them;
- ligature substitutions;
- contextual substitutions;
- chaining contextual substitutions;
- extension substitutions;
- reverse chaining substitutions when required by script rules;
- feature lookup ordering;
- script/language system selection.

Feature policy must be explicit. Default features must be derived from script
requirements, not from hidden platform behavior.

## GPOS Target

The pure Kotlin GPOS engine must support:

- single positioning;
- pair positioning;
- cursive attachment;
- mark-to-base;
- mark-to-ligature;
- mark-to-mark;
- contextual positioning;
- chaining contextual positioning;
- extension positioning;
- device/variation adjustments where supported by the scaler.

The engine must preserve cluster and glyph identity through positioning.

## GDEF And Attachment Data

The target uses GDEF for:

- glyph class definitions;
- mark attachment classes;
- ligature caret positions when exposed through APIs;
- variation store data used by layout tables.

Missing or malformed optional GDEF data must diagnose when a requested feature
needs it.

## Features

Feature input comes from:

- paragraph/text style;
- `SkShaper` compatible feature requests;
- script defaults;
- language system defaults;
- facade-level compatibility choices.

The shaping dump must record:

- requested features;
- enabled features;
- disabled features;
- script/language system used;
- unsupported features and reason codes.

Features must not be enabled implicitly in simple `drawString` paths.

## Clusters

Cluster rules:

- Every output glyph maps to an original UTF-16 range.
- Substitution and reordering preserve enough data for hit testing and
  selection.
- Cluster levels for paragraph hit testing are stable and serialized.
- Glyph ID runs that bypass shaping still carry synthetic clusters when they
  originated from text blob APIs.

Cluster output is part of normative shaping evidence.

## Font Fallback Runs

Fallback is shaping-visible:

- missing code points split into fallback runs;
- fallback font identity appears in the run;
- fallback reason appears in diagnostics;
- emoji fallback can select a color-glyph-capable typeface;
- fallback must preserve original text clusters;
- host-dependent fallback is marked host-dependent.

There is no hidden platform fallback.

## Bidi Scope

The shaping engine owns run-level bidi facts. The paragraph engine owns
paragraph-level visual line ordering.

Shaped runs expose:

- paragraph embedding level;
- run direction;
- logical text range;
- visual ordering key supplied by paragraph layout;
- diagnostics when text requires paragraph-level bidi resolution but only
  single-run shaping was requested.

## Direct Glyph Runs

APIs that provide glyph IDs directly do not run GSUB. They still require:

- font identity;
- glyph count validation;
- glyph bounds and metrics;
- optional GPOS bypass marker;
- clusters if source text ranges are available;
- artifact planning facts.

## Diagnostics

Stable reason-code families:

- `text.shaping.script-unsupported`;
- `text.shaping.feature-unsupported`;
- `text.shaping.lookup-malformed`;
- `text.shaping.lookup-type-unsupported`;
- `text.shaping.gdef-required`;
- `text.shaping.mark-positioning-unavailable`;
- `text.shaping.cursive-attachment-unavailable`;
- `text.shaping.fallback-missing`;
- `text.shaping.emoji-sequence-unsupported`;
- `text.shaping.unicode-data-version-mismatch`;
- `text.shaping.cluster-invariant-failed`.

## Acceptance Criteria

- Required script matrix rows have fixtures and shaping dumps.
- GSUB and GPOS lookup support is implemented only with bounded parsing and
  stable diagnostics.
- Clusters are deterministic and suitable for paragraph hit testing.
- Fallback runs are visible and serializable.
- Unsupported scripts or features refuse or diagnose precisely.
- Native shaping engines are not required for normative behavior.
