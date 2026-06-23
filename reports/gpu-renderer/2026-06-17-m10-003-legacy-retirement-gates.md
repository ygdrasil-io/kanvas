# GPU Renderer M10-003 Legacy Retirement Gates

Date: 2026-06-17
Branch: `codex/kgpu-m10-003-legacy-retirement-gates`
Ticket: `KGPU-M10-003`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M10-003 | `done` | Added `GpuRendererLegacyRetirementGate`, a deterministic per-family policy gate for legacy `gpu-raster` retirement evidence. The gate records all M10 inventory families, requires accepted replacement tickets, activation decisions, rollback evidence, rollback validation hashes, old-path usage evidence, PM rows, and M10-002 shadow parity results before authorizing a concrete slice. Authorized rows still keep the production legacy route active in this ticket. | Future concrete removals still need route-specific replacement, activation, rollback, and old-path evidence rows accepted by this gate. |

## Evidence

- `GpuRendererLegacyRetirementEvidence` names the legacy family, accepted
  replacement ticket, activation decision, rollback evidence, rollback
  validation hash, PM row, old-path usage evidence, scope label, and M10-002
  shadow parity acceptance.
- `GpuRendererLegacyRetirementGate.evaluate` produces one
  `GpuRendererLegacyRetirementFamilyGate` per M10 inventory family. Missing
  evidence emits `legacy.retirement.missing_promoted_replacement_evidence` and
  keeps `legacyRouteActive=true`.
- Unsafe evidence refuses with stable diagnostics for unaccepted or mismatched
  replacement tickets, missing activation decisions, missing rollback evidence,
  missing rollback hashes, missing old-path usage evidence, non-zero old-path
  usage, non-family-scoped PM rows, non-family-scoped scope labels, missing
  shadow parity acceptance, product activation, release blocking, and readiness
  movement.
- Duplicate family rows and shared evidence across families are refused instead
  of being merged into a broad deletion request. The validator also refuses
  individually shared activation decisions, rollback evidence, or old-path
  usage evidence, even when the rest of the row is family-scoped.
- Archived-only evidence and generic migration gates are refused so archived
  plans remain historical and M10 inventory rows cannot retire routes by
  implication.
- Report dumps include the PM row `gpu-renderer.legacy-retirement`,
  per-family `gpu-renderer.legacy-retirement.*` rows, replacement tickets,
  activation and rollback IDs, old-path usage counts, and a non-claim line with
  `productRouteActivated=false`, `releaseBlocking=false`, and
  `readinessDelta=0.0`.
- Accepted rows set `retirementAuthorized=true` while keeping
  `legacyRouteActive=true`; this gate permits a future route-specific removal
  but does not perform that removal.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererLegacyRetirementGateTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*Gate*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Review

Independent review `019ed5fb-8292-7931-b494-9034a88e15e0` found two issues:

- P1: shared evidence was initially detected only when activation, rollback,
  and old-path usage evidence were all identical.
- P2: the M10 hygiene report still carried stale M5/M7/M8/M9 status wording.

The follow-up added `SharedRetirementEvidenceKeys` with independent detection
for shared activation decisions, rollback evidence, and old-path usage
evidence, plus the red/green regression test
`partially shared activation rollback or usage evidence is refused per family`.
It also refreshed the M10 hygiene matrix for current M5/M7/M8/M9 status.
Re-review found no remaining P0/P1/P2 blockers and accepted KGPU-M10-003 for
`done`.

## Non-Claims

- No production legacy route is deleted, bypassed, or disabled by this ticket.
- No default `gpu-raster` behavior changes.
- No generic M10 inventory row can retire a route.
- No route retirement is inferred from another family.
- No product route activation, release-blocking gate, or readiness delta is
  claimed.
- Archived migration plans remain historical evidence only.
