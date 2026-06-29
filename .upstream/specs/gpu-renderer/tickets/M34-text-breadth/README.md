# M34 - Text Breadth

**Status:** active (2026-06-28) — Wave B Track 2


## Validation Bundle

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:check
```

## Non-Claims

- This milestone does not claim COLRv1, SVG OpenType, or full emoji sequence
  rendering. The pure Kotlin parsing artifacts are already delivered; these
  remain `DependencyGated` until the GPU renderer delivers rendering execution
  evidence (M10/M11) and the per-script / CFF2 gates close (M6/M4).
- Variable font outline generation and complex shaping/BiDi execution remain
  in the text stack; the GPU renderer only consumes resolved artifacts.
- Font fallback selection logic lives in the text stack; the GPU renderer
  only splits subruns by fallback identity.

## Correction du motif de blocage (2026-06-29)

M34-002/003/004 étaient `blocked` avec un motif faux (« gated on
pure-kotlin-text artifacts »). L'audit `fichier:ligne` montre que le parsing
COLRv0/CPAL/CBDT, la résolution fvar/gvar/avar et le shaping/BiDi sont livrés
et testés. Ces trois tickets **restent `blocked` / `DependencyGated`** car
l'évidence de **rendu GPU** est toujours KO (`product_activation: false`,
contrats GPU absents, refus stable, `GPUDrawLayerPlanner` stub). Le sous-scope
borné handoff + facts portés + refus stable est implémenté et testé
(`ColorFontHandoffRouteTest`, `VariableFontHandoffRouteTest`,
`ShapingIntegrationHandoffRouteTest`) mais **ne promeut pas** les tickets. Vrai
gate : exécution GPU M6/M10/M11 + CFF2 vraies polices M4. Évidence :
`reports/gpu-renderer/m34-text-breadth-rescope/`.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
