# 2026-06-19 KFONT M13 Readiness Audit

Date: 2026-06-19
Status: coordination evidence.

## Scope

- Re-audit the `M13` Skia-like facade migration tickets against the current
  pure Kotlin text backlog on `master`.
- Distinguish facade work that is merely specified from facade work that is
  truly actionable now.
- Record precise remaining gates without retiring any legacy gate or broadening
  any support claim.

## Findings

- `KFONT-M13-001` remains actionable as a coordination slice: its purpose is to
  inventory still-open facade routes and gates, including the shaping gap in
  `.upstream/specs/pure-kotlin-text/tickets/M6-opentype-layout-shaping/KFONT-M6-010-implement-gsub-gpos-extension-chaining-and-variation-adjustment-lookups.md`
  and the GPU handoff gap in
  `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-010-add-materialkey-leakage-tests.md`,
  rather than to wait for those tickets to be done before the inventory exists.
- `KFONT-M13-002` depends only on done pure Kotlin identity/table facts beyond
  `KFONT-M13-001`, so its true remaining gate is the missing facade adapter
  inventory and approved PM/dashboard classification for the `SkTypeface`
  route.
- `KFONT-M13-003` remains additionally gated by `KFONT-M6-010`, so explicit
  `SkShaper` routing must not imply bounded complex-shaping parity while the
  advanced lookup fixture family and variation-adjustment evidence are still
  absent.
- `KFONT-M13-004` depends on done typed handoff prerequisites beyond
  `KFONT-M13-001`, but it must still wait for the approved facade route
  inventory before descriptor or no-`Sk*` parity evidence is promoted.
- `KFONT-M13-005` remains downstream-only: stale docs and stub labels must stay
  visible until the inventory and route-specific evidence exist.

## Outcome

- `KFONT-M13-001` stays `proposed` as the next deblocking coordination ticket.
- `KFONT-M13-002` through `KFONT-M13-005` move from `proposed` to `blocked`.
- `M13` now distinguishes one actionable coordination slice from four
  downstream blocked route/retirement slices.
- This audit does not claim any facade route support, GPU route readiness, or
  legacy-gate retirement.

## Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`
- `.upstream/specs/pure-kotlin-text/tickets/STATUS.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-010-add-materialkey-leakage-tests.md`
- `.upstream/specs/pure-kotlin-text/tickets/M6-opentype-layout-shaping/KFONT-M6-010-implement-gsub-gpos-extension-chaining-and-variation-adjustment-lookups.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/KFONT-M13-001-add-facade-adapter-inventory.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/KFONT-M13-002-route-sktypeface-opentype-facts-through-pure-kotlin-core.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/KFONT-M13-003-route-explicit-skshaper-apis-through-pure-kotlin-shaping.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/KFONT-M13-004-route-sktextblob-glyph-runs-through-typed-descriptors.md`
- `.upstream/specs/pure-kotlin-text/tickets/M13-skia-facade-migration/KFONT-M13-005-retire-stale-font-docs-and-stubs-after-evidence-promotion.md`

## Validation

```bash
rtk git diff --check
```

## Non-Claims

- No `SkTypeface` pure Kotlin route claim.
- No explicit `SkShaper` pure Kotlin route claim.
- No `SkTextBlob` typed descriptor route claim.
- No legacy gate retirement claim.
- No GPU route readiness claim.
