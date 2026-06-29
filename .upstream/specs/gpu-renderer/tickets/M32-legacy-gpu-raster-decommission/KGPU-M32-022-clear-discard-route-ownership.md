---
id: KGPU-M32-022
title: "Legacy decommission: clear-discard-target-background route ownership assignment"
status: done
status: review
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M32-001, KGPU-M10-003]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-022 - Clear-discard-target-background route ownership assignment

## PM Note

Le clear / discard et le fond de la cible sont couverts par l'initialisation de
la surface Kanvas (effacement en noir transparent `DEFAULT_CLEAR_COLOR = (0,0,0,0)`
dans `Surface.kt:145,468-470`). Ce ticket assigne KGPU-M32-022 comme ticket de
remplacement de propriete pour la famille `ClearDiscardTargetBackground`, et
remplace le placeholder `route-specific-clear-discard-ticket-required`.

## Problem

`GpuRendererLegacyRouteFamily.ClearDiscardTargetBackground` porte le
`defaultReplacementTicket` `route-specific-clear-discard-ticket-required`,
un placeholder qui ne correspond pas a un vrai ticket — il ne peut donc
pas passer la porte `GpuRendererLegacyRetirementGate` (`diagnosticsFor`).

La famille clear/discard est triviale : l'initialisation de la surface
Kanvas (`Surface.kt:145,468-470`) efface en noir transparent avant tout
dessin. Aucun dispatch de commande explicite n'est necessaire ; la
fonctionnalite est verifiee implicitement dans chaque scene rendue.
Un ticket de propriete reel doit etre assigne.

## Scope

- Creer le ticket KGPU-M32-022 comme proprietaire de remplacement accepte
  pour la famille `clear-discard-target-background`.
- Mettre a jour `GpuRendererShadowParityGates.kt` :
  `ClearDiscardTargetBackground.defaultReplacementTicket = "KGPU-M32-022"`.
- Mettre a jour le M32 README (ligne KGPU-M32-022).
- Mettre a jour le decision-matrix concern #4 (placeholder resolu).

## Non-Goals

- Ne pas ajouter de route de dispatch de commande `ClearDraw`/`Discard` —
  l'effacement fait partie du contrat d'initialisation.
- Ne pas produire de preuve de parity pixel independante — la fonctionnalite
  est verifiee dans chaque scene rendue.

## Spec Sources

- `gpu-raster/.../GpuRendererShadowParityGates.kt:64-68` — enum definition
- `gpu-raster/.../GpuRendererLegacyRetirementGates.kt:106-270` — retirement gate
- `kanvas/.../Surface.kt:145,468-470` — `DEFAULT_CLEAR_COLOR`, `t.encode(clearColor=...)`
- `reports/gpu-renderer/2026-06-26-m32-001-decommission-decision-matrix.md`
  (row 12, concern #4)

## Design Sketch

```kotlin
// In GpuRendererShadowParityGates.kt:
ClearDiscardTargetBackground(
    familyId = "clear-discard-target-background",
    displayName = "Clear/discard and target background",
    defaultReplacementTicket = "KGPU-M32-022",  // was placeholder
),
```

## Acceptance Criteria

- [x] KGPU-M32-022 ticket created with canonical section order, `status: review`
- [x] `ClearDiscardTargetBackground.defaultReplacementTicket` = `"KGPU-M32-022"`
- [x] M32 README updated (KGPU-M32-022 row)
- [x] Decision-matrix concern #4 resolved (note appended)
- [x] No test references the old placeholder `route-specific-clear-discard-ticket-required`
- [x] Retirement gate accepts clear-discard family evidence with `acceptedReplacementTicket = "KGPU-M32-022"`

## Required Evidence

- `GpuRendererShadowParityGates.kt:67` shows `defaultReplacementTicket = "KGPU-M32-022"`
- `GpuRendererLegacyRetirementGate.evaluate(...)` with `acceptedReplacementTicket = "KGPU-M32-022"` passes for `clear-discard-target-background`
- `rtk rg "route-specific-clear-discard" --glob '*.kt'` returns zero hits (old placeholder purged from source)

## Fallback / Refusal Behavior

- Le legacy gate `gpu-raster legacy` reste visible pour cette famille
  jusqu'a ce que la porte de retraite KGPU-M10-003 soit autorisee avec
  les preuves KGPU-M32-022.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.clear-discard-route-ownership`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: after independent review.

## Validation

```bash
rtk rg "route-specific-clear-discard" --glob '*.kt'
# Expected: no matches
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*Gate*'
```

## Status Notes

- `proposed`: Placeholder `route-specific-clear-discard-ticket-required`
  discovered in KGPU-M32-001 concern #4 — no real ticket id assigned.
- `review` (2026-06-26): KGPU-M32-022 assigned. Enum updated, README row
  added, decision-matrix concern resolved. Evidence at
  `reports/gpu-renderer/2026-06-26-m32-003-legacy-retirement-authorization.md`.
  Independent review owed.
- `review → done` (2026-06-28): independently reviewed, evidence accepted, port-or-refuse decision validated.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
