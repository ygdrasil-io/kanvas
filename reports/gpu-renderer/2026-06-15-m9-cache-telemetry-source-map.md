# M9 Cache Telemetry Source Map

## Ticket

- `KGPU-M9-001 - Add observed cache telemetry source map`

## Status

Implemented and independently reviewed.

## Files

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/TelemetryContracts.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUCacheTelemetrySourceMapTest.kt`
- `.upstream/specs/gpu-renderer/tickets/M9-performance-cache-release-gates/KGPU-M9-001-add-observed-cache-telemetry-source-map.md`
- `.upstream/specs/gpu-renderer/tickets/M9-performance-cache-release-gates/README.md`
- `.upstream/specs/gpu-renderer/tickets/STATUS.md`

## Evidence

- `GPUCacheTelemetrySourceMapTest` records a deterministic source-map report for
  observed, observed-partial, derived, unavailable, and reporting-only cache
  telemetry counters.
- `GPUCacheTelemetrySourceMapper` ties every counter to a named source artifact
  and only treats complete runtime artifact evidence with a source hash and all
  required fields as observed readiness input.
- Comment, report-text, and synthetic-ledger sources are classified as derived
  and cannot count as observed readiness evidence.
- Missing runtime hashes and unknown source kinds remain visible as unavailable
  counters.
- The PM dump line remains `PolicyGated` and records
  `readinessDelta=0.0`, `releaseBlocking=false`, and
  `productRouteActivated=false`.
- Independent review `019ec866-9980-7fe1-bc04-a9806b1d30c3` accepted
  KGPU-M9-001 for `done` with no blocking findings.

## Validation

```bash
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetrySourceMapTest
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetrySourceMapTest --tests org.graphiks.kanvas.gpu.renderer.GPURendererLayoutSurfaceTest
rtk ./gradlew --no-daemon --rerun-tasks :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:gpuRendererR6ExecutedFirstRoutePmEvidenceBundle
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py .
rtk git diff --check
```

The boundary validator initially rejected a stale local
`gpu-raster/build/reports/gpu-renderer-r6-executed-first-route-pm-evidence`
artifact that lacked the current unsupported rrect-family refusal snippets.
Regenerating the executed R6 PM evidence bundle refreshed the untracked build
report, after which the validator passed with
`classification=promotion-boundary-held`, `rootStatus=ActivationCandidate`,
`executedStatus=absent`, and `productRouteActivated=False`.

## Non-Claims

- No release-blocking performance gate is added.
- No readiness delta is claimed.
- No product route is activated.
- Derived comments, reports, or synthetic ledgers do not count as observed
  runtime cache telemetry.

## Remaining Gates

- `KGPU-M9-002` still needs raw frame sample provenance, warmup and variance
  policy, quarantine and rebaseline rules, and skipped-lane diagnostics before
  it can create any release-blocking frame gate policy.
- `KGPU-M9-003` remains dependent on accepted `KGPU-M9-001` and
  `KGPU-M9-002` evidence.
