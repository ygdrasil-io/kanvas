# 2026-06-19 KFONT-M13-001 Facade Adapter Inventory

Date: 2026-06-19
Status: implementation evidence.

## Scope

- Inventory the M13 Skia-like facade routes before any adapter route is marked
  ready.
- Keep the current simple `SkCanvas.drawString` boundary explicit.
- Record the remaining shaping, paragraph, typed handoff, and legacy-gate
  blockers without broadening support claims.

## Route summary

- `current-supported`: `SkFontMgr`, `SkFont`, and the simple deterministic
  `SkCanvas.drawString` route.
- `tracked-gap`: `SkTypeface` OpenType facts and paragraph-compatible facade
  APIs.
- `DependencyGated`: explicit `SkShaper` and `SkTextBlob` typed handoff routes.

## Key findings

- `SkCanvas.drawString` stays the simple deterministic compatibility path. It
  is inventoried as `reuse-as-is` and must not absorb implicit complex shaping,
  paragraph layout, or multi-font fallback splitting.
- `SkTypeface` remains the main `fontations` / `fontations_ft_compare` facade
  boundary because high-level facts such as PostScript name and localized family
  names still carry `STUB.FONTATIONS` expectations today.
- `SkTextBlob` is the right M13 owner route for the durable `dftext` gate
  because typed glyph-run descriptors, artifact references, and no-`Sk*`
  payload evidence still belong to the M11 handoff chain.
- No public `org.skia.paragraph` facade package exists in `:kanvas-skia`.
  Paragraph-compatible APIs therefore remain an explicit tracked gap even though
  the pure Kotlin paragraph owner package already exists.
- `pdf_never_embed` remains adjacent to the runtime facade. The inventory keeps
  that gate visible instead of pretending a runtime text facade route owns PDF
  subsetting.

## Evidence files

- `reports/pure-kotlin-text/facade-adapter-inventory.json`
- `reports/pure-kotlin-text/font-claim-dashboard.json`
- `reports/pure-kotlin-text/font-diagnostic-taxonomy.json`

## Remaining gates

- `KFONT-M13-002` still needs route-specific `SkTypeface` parity evidence.
- `KFONT-M13-003` is still blocked on `KFONT-M6-010` advanced lookup and
  variation-adjustment evidence.
- `KFONT-M13-004` still needs typed descriptor and GPU handoff evidence.
- Paragraph facade scope is still absent from `:kanvas-skia`.

## PM bundle

`pipelinePmBundle` now carries the facade inventory JSON and this markdown
summary as advisory coordination evidence. The dashboard row remains
`tracked-gap`, warning-only, and non-promotional.

## Non-Claims

- No facade route support promotion.
- No GPU route readiness claim.
- No legacy-gate retirement claim.
- No hidden native engine dependency claim.

## Validation

```bash
rtk python3 -m unittest scripts/test_validate_kfont_m13_001_facade_inventory.py
rtk python3 scripts/validate_kfont_m13_001_facade_inventory.py
rtk ./gradlew --no-daemon validateKfontM13001FacadeInventory
rtk git diff --check
```
