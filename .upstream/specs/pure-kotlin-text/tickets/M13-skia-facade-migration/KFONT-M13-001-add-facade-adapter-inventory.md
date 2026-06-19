---
id: "KFONT-M13-001"
title: "Add facade adapter inventory"
status: "proposed"
milestone: "M13"
priority: "P0"
owner_area: "skia-facade"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-004", "KFONT-M2-005", "KFONT-M6-010", "KFONT-M8-006", "KFONT-M9-006", "KFONT-M11-010", "KFONT-M12-005"]
legacy_gate: ["coloremoji_blendmodes", "scaledemoji", "scaledemoji_rendering", "dftext", "fontations", "fontations_ft_compare", "pdf_never_embed"]
---

# KFONT-M13-001 - Add facade adapter inventory

## PM Note

Ce ticket donne au PM la carte des APIs Skia-like: ce qui passe déjà par le cœur pure Kotlin, ce qui reste gated, et pourquoi.

## Problem

M13 must turn `:kanvas-skia` into a facade over the pure Kotlin text stack instead of a second font system. The current migration target lists reusable prototypes and durable legacy gates, but there is no adapter inventory that maps each facade route to its target owner, support status, legacy gate, diagnostic namespace, and required evidence. Without that inventory, stale stubs can hide refusals and facade tests can pass through divergent code paths.

## Scope

- Inventory facade routes for `SkFontMgr`, `SkTypeface`, `SkFont`, `SkCanvas.drawString`, explicit `SkShaper`, `SkTextBlob`, and paragraph-compatible APIs.
- For each route, record the target pure Kotlin contract, target owner package/module, migration category (`reuse-as-is`, `promote-with-contract`, `replace`, `keep-current-gate`, or `expected-unsupported`), diagnostics, claim impact, and required evidence.
- Map durable legacy gates from `09-migration-from-current-font-pack.md`: `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, and `pdf_never_embed`.
- Identify facade routes that must stay simple, especially `SkCanvas.drawString`, versus explicit shaping/paragraph routes.
- Define the inventory dump consumed by PM bundle and dashboard rows before any adapter route is marked ready.

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
enum class FacadeMigrationCategory {
    ReuseAsIs,
    PromoteWithContract,
    Replace,
    KeepCurrentGate,
    ExpectedUnsupported,
)

data class FacadeAdapterInventoryRow(
    val facadeApi: String,
    val facadeRoute: String,
    val targetContract: String,
    val targetOwner: String,
    val category: FacadeMigrationCategory,
    val claimImpact: ClaimImpact,
    val legacyGates: List<String>,
    val requiredEvidence: List<String>,
    val diagnostics: List<String>,
)
```

## Acceptance Criteria

- [ ] Every public font/text facade route listed in scope has an inventory row with owner, target contract, migration category, claim impact, diagnostics, and required evidence.
- [ ] Every durable legacy gate from `09-migration-from-current-font-pack.md` appears in at least one row or is explicitly marked adjacent/out-of-scope with rationale.
- [ ] `SkCanvas.drawString` is inventoried as the simple deterministic path and not as broad complex shaping support.
- [ ] Inventory rows distinguish optional drift-only native comparison from normative pure Kotlin behavior.
- [ ] PM bundle/dashboard output can show which facade routes are blocked by dependencies, fixtures, GPU evidence, or expected unsupported policy.

## Required Evidence

- `facade-adapter-inventory.md` or `facade-adapter-inventory.json` with rows for all scoped APIs.
- Legacy gate mapping table covering `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, and `pdf_never_embed`.
- Diagnostic mapping from facade refusal codes to target `font.*`, `text.*`, `glyph.*`, `text.gpu.*`, or `expected-unsupported` categories.
- PM bundle/dashboard diff showing facade rows and remaining gates.

## Fallback / Refusal Behavior

- Inventory rows that lack target evidence must classify the route as `tracked-gap`, `DependencyGated`, `fixture-gated`, `GPU-gated`, `expected-unsupported`, or `drift-only`; no blank status is allowed.
- Unsupported facade paths must emit a route-specific facade diagnostic and link to the target diagnostic family.
- Legacy gate(s) `coloremoji_blendmodes`, `scaledemoji`, `scaledemoji_rendering`, `dftext`, `fontations`, `fontations_ft_compare`, `pdf_never_embed` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Skia facade adapter inventory`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no; inventory is a prerequisite for later route promotions.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :kanvas-skia:test
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial markdown ticket written from the pure Kotlin font roadmap.
- `proposed` (2026-06-19 readiness audit): `KFONT-M1-004`, `KFONT-M2-005`,
  `KFONT-M8-006`, `KFONT-M9-006`, and `KFONT-M12-005` are `done`. The still-open
  `KFONT-M6-010` and `KFONT-M11-010` slices are inputs that the inventory must
  classify as shaping/GPU-gated facade rows, not blockers that prevent writing
  the inventory itself. Remaining gate to move `ready`: review the exact
  facade-route surface, PM/dashboard row shape, diagnostic mapping, and legacy
  gate coverage expected from the inventory output before implementation starts.
- Move to `ready` only after scope, dependencies, evidence, and validation commands are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M13`
- `area:skia-facade`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
- `legacy:scaledemoji`
- `legacy:scaledemoji_rendering`
- `legacy:dftext`
- `legacy:fontations`
- `legacy:fontations_ft_compare`
- `legacy:pdf_never_embed`
