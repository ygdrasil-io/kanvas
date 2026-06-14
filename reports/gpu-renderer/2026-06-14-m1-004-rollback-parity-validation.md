# GPU Renderer M1-004 Rollback Parity Validation

Date: 2026-06-14
Ticket: `KGPU-M1-004`
Branch: `codex/gpu-renderer-m1-wave`

## Scope

KGPU-M1-004 validates rollback/parity around the controlled first-route flag
from KGPU-M1-003. It compares legacy-before, product-flagged, and
legacy-rollback output checksums while keeping `productRouteActivated=false`,
`releaseBlocking=false`, and `readinessDelta=0.0`.

## Evidence

- `GpuRendererFirstRouteRollbackParityValidator` consumes explicit snapshots
  and emits a stable transcript.
- Adapter-backed test runs compare SHA-256 checksums from disabled legacy,
  product-flagged, and rollback-to-disabled legacy runs.
- `ProductFlagged` evidence remains diagnostic: no `GPUCommandSubmission` or
  `GPUReadbackResult.Completed` marker is allowed in the transcript.
- `StrokeAndFill` remains unsupported in product-flag mode via
  `unsupported.adapter.paint_style`.
- Checksum mismatch returns `gatePassed=false` and
  `rollback.parity.checksum_mismatch` while keeping activation, release
  blocking, and readiness movement false.

## TDD Evidence

- RED: `rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererFirstRouteRollbackParityTest`
  failed before implementation on missing rollback/parity validator symbols.
- GREEN: the same command passed after adding the validator and transcript
  checks.

## Validations

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.GpuRendererFirstRouteRollbackParityTest
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk rg -n 'productRouteActivated[=:][[:space:]]*true|Product route activated:[[:space:]]*`true`|releaseBlocking[=:][[:space:]]*true|Release blocking:[[:space:]]*`true`|readinessDelta[=:][[:space:]]*[1-9]|Readiness delta:[[:space:]]*`[1-9]' gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/GpuRendererFirstRouteRollbackParity.kt gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GpuRendererFirstRouteRollbackParityTest.kt .upstream/specs/gpu-renderer/tickets/M1-first-route-product-activation reports/gpu-renderer/2026-06-14-m1-004-rollback-parity-validation.md reports/gpu-renderer/2026-06-14-m1-m2-ticket-wave.md
rtk git diff --check
```

All commands above passed after RED/GREEN implementation. The targeted claim
scan returned no matches.

## Non-Claims

- This does not enable the first route by default.
- This does not make the first route release-blocking.
- This does not move readiness.
- This does not claim broad GPU/WebGPU support beyond the controlled solid
  `FillRect` flag diagnostic.
- This does not retire legacy `drawRect`.

## Review

Independent review `019ec731-4bf3-7e60-9ab6-af513036a6e9` approved moving
KGPU-M1-004 to `done`.

## Remaining Gate

None for KGPU-M1-004. M1 is complete without default product route activation,
release blocking, or readiness movement.
