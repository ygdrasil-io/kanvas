---
id: "KFONT-M13-005"
title: "Retire stale font docs and stubs after evidence promotion"
status: "done"
milestone: "M13"
priority: "P1"
owner_area: "docs-validation"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M13-001", "KFONT-M13-002", "KFONT-M13-003", "KFONT-M13-004", "KFONT-M12-005"]
legacy_gate: ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering", "dftext", "fontations", "fontations_ft_compare", "pdf_never_embed"]
---

# KFONT-M13-005 - Retire stale font docs and stubs after evidence promotion

## PM Note

Ce ticket empêche de fermer des anciens blockers par simple nettoyage de docs: chaque retrait doit pointer vers une preuve complète.

## Problem

After M13 route work lands, stale docs, stub constants, and old blocker labels can contradict the pure Kotlin target. Removing them too early is worse than leaving them: it can erase durable gates such as `scaledemoji`, `dftext`, or `fontations` before the new route has fixtures, dumps, diagnostics, CPU/GPU evidence, and dashboard updates. This ticket defines the retirement decision record for docs and stubs.

## Scope

- Inventory stale font docs, legacy stub names, dashboard rows, and blocker labels that are candidates for retirement after M13 adapter evidence.
- For each candidate, record owning target spec, replacement ticket/evidence, legacy gate, required retirement evidence, decision (`retire`, `keep-current-gate`, `expected-unsupported`, or `drift-only`), and dashboard action.
- Retire only rows whose new route has implementation tests, fixture provenance, semantic dump, CPU oracle evidence, GPU evidence when GPU support is claimed, stable diagnostics for remaining subcases, and PM/dashboard updates.
- Preserve expected-unsupported and drift-only documentation for native engine parity, Fontations/FreeType comparison, SkSL compiler work, LCD, and PDF subset workstreams.
- Produce a deterministic retirement report that can be reviewed before stale docs or stubs are removed.

## Non-Goals

- Do not promote support without the Required Evidence section attached.
- Do not claim GPU renderer support unless a dedicated GPU route ticket provides evidence.
- Do not use HarfBuzz, FreeType, Fontations, AWT, JNI, CoreText, DirectWrite, or fontconfig as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/00-architecture-and-module-boundaries.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
enum class LegacyRetirementAction {
    Retire,
    KeepCurrentGate,
    ExpectedUnsupported,
    DriftOnly,
)

data class LegacyRetirementDecision(
    val legacyGate: String,
    val currentArtifact: String,
    val targetSpec: String,
    val replacementEvidenceRefs: List<String>,
    val requiredEvidenceSatisfied: Boolean,
    val action: LegacyRetirementAction,
    val dashboardRow: String,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Every candidate legacy doc/stub/blocker row has a `LegacyRetirementDecision` with target spec, evidence refs, dashboard row, and action.
- [ ] No legacy gate is retired unless all retirement evidence from `09-migration-from-current-font-pack.md` is present.
- [ ] `fontations` and `fontations_ft_compare` remain `drift-only` or `expected-unsupported`; they are never converted into normative dependencies.
- [ ] `pdf_never_embed` remains adjacent PDF/subset work unless a future runtime font target explicitly adopts it.
- [ ] Dashboard and PM bundle diffs show retired rows, kept gates, expected-unsupported rows, and drift-only rows separately.

## Required Evidence

- `legacy-retirement-decisions.json` covering `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, and `pdf_never_embed`.
- `stale-font-docs-inventory.md` listing each stale doc/stub/blocker candidate and its replacement target spec section.
- Evidence links for each retired row: implementation tests, fixture provenance, semantic dump, CPU oracle, GPU evidence when claimed, stable diagnostics, and dashboard update.
- PM bundle/dashboard diff showing retired versus retained gates.
- Diagnostic snapshot for any attempted retirement refused by missing evidence.

## Fallback / Refusal Behavior

- Missing evidence produces a `font.docs.retirement-evidence-missing` diagnostic and keeps the current gate visible.
- Stale wording can be corrected to point at the target spec, but blocker removal waits for the retirement decision evidence.
- Legacy gate(s) `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected rows: `Legacy font gate retirement`, `Expected unsupported font rows`, `Drift-only font rows`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless each retired row has a complete retirement decision record.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- `blocked` (2026-06-19): Readiness audit confirmed that `KFONT-M12-005` is
  `done`, but stale docs and stub retirement must remain behind
  `KFONT-M13-002` through `KFONT-M13-004`; `KFONT-M13-001` now only provides
  the prerequisite inventory and does not retire any legacy row by itself.
  Remaining gate: land the `SkTypeface`, explicit `SkShaper`, and `SkTextBlob`
  route evidence first, then review each retirement candidate with linked
  implementation tests, fixture provenance, semantic dumps, CPU/GPU evidence
  where claimed, stable diagnostics, and PM/dashboard updates.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M13`
- `area:docs-validation`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:dftext`
- `legacy:fontations`
- `legacy:fontations_ft_compare`
- `legacy:pdf_never_embed`
