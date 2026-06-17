# GPU Renderer M10-002 Shadow Parity Migration Gates

Date: 2026-06-17
Branch: `codex/kgpu-m10-002-shadow-parity-gates`
Ticket: `KGPU-M10-002`

## Ticket Status

| Ticket | Status | Evidence | Remaining gate |
|---|---|---|---|
| KGPU-M10-002 | `done` | Added `GpuRendererShadowParityMigrationGate`, a deterministic per-family policy gate for legacy `gpu-raster` migration evidence. The gate records all M10 inventory families, requires route-scoped adapter-backed parity evidence, before/after dump hashes, PM row IDs, rollback labels, and replacement tickets, and refuses missing, duplicate, broad, activated, release-blocking, or readiness-moving evidence. | No default route switch or legacy retirement is performed. KGPU-M10-003 remains required before any concrete legacy path can be retired. |

## Evidence

- `GpuRendererLegacyRouteFamily` enumerates the M10 inventory families so each
  family is validated independently rather than inferred from another route.
- `GpuRendererShadowParityEvidence` names the family, shadow route test ID,
  before/after dump hashes, PM row ID, rollback label, accepted replacement
  ticket, adapter-backed flag, product activation flag, release-blocking flag,
  and readiness delta.
- `GpuRendererShadowParityMigrationGate.evaluate` produces one
  `GpuRendererShadowParityFamilyGate` per family. Missing evidence emits
  `shadow.parity.missing_adapter_backed_evidence`; duplicate family rows emit
  `shadow.parity.duplicate_family_evidence`.
- Unsafe evidence refuses with stable diagnostics for missing adapter-backed
  proof, non-family-scoped PM rows, non-family-scoped rollback labels, product
  activation, release-blocking state, and readiness movement.
- Accepted evidence still keeps `legacyDefaultActive=true` and
  `defaultRouteChanged=false`, because this ticket only defines the gate before
  default migration. Default switching and retirement remain separate.
- Report dumps include the PM row
  `gpu-renderer.shadow-parity-gates`, per-family `gpu-renderer.shadow-parity.*`
  rows, rollback labels, replacement tickets, and a non-claim line with
  `defaultsChanged=false`, `productRouteActivated=false`,
  `releaseBlocking=false`, and `readinessDelta=0.0`.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererShadowParityMigrationGateTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Review

Independent review `019ed714-fd15-72e2-a8f8-b1b0f9fbe2f5` found three
blocking issues:

- Broad/shared evidence could satisfy multiple families.
- Tests used the production enum as the only family-coverage oracle.
- `done` status lacked a linked independent review note.

The follow-up patch added shared-evidence detection, stricter
`:<familyId>:` route-test scoping, an explicit M10 inventory `familyId` list in
tests, and linked this review in the ticket/report evidence. Re-review found no
remaining P0/P1 code or gate-model issue; the final P2 was this missing review
linkage, now addressed in this report and ticket.

## Non-Claims

- No default `gpu-raster` route is changed.
- No legacy code path is removed, bypassed, or disabled.
- No family migration is inferred from another family.
- No product route activation, release-blocking gate, or readiness delta is
  claimed.
- No broad renderer parity or support expansion is claimed.
- Archived migration plans remain historical evidence only.
