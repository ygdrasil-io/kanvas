# M35 - Color Fidelity

## Goal

Promote HDR transfer functions, wide-gamut working spaces, gain map pipeline, and ICC profile parsing from TargetNative specs to accepted routes with evidence.

## Dependencies

Depends on M0 (R0-R6 boundary review) and M1 (first-route product activation) for baseline contract shapes, route taxonomy, and diagnostics. M35-003 depends on codec support for Ultra HDR JPEG gain map metadata.

## Exit Criteria

- [ ] HDR transfer functions (PQ, HLG, scRGB) produce accepted GPUNative routes with EOTF and tone map evidence.
- [ ] Wide-gamut working spaces (Display P3, Adobe RGB, Rec.2020) are accepted with conversion and intermediate format evidence.
- [ ] Gain map pipeline decodes, applies, and display-adapts with GPU evidence and CPU oracle parity.
- [ ] ICC profile parsing (v2/v4 matrix/TRC) is accepted; v5 and LUT profiles produce stable refusals.
- [ ] All accepted routes have WGSL validation through wgsl4k, pipeline key evidence, and route diagnostics.
- [ ] No CPU-rendered texture fallback for any promoted route.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M35-001 - HDR transfer functions (PQ, HLG, scRGB)](KGPU-M35-001-hdr-transfer-functions.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `color` | `KGPU-M1-001` | `legacy color transform` |
| [KGPU-M35-002 - Wide-gamut working spaces](KGPU-M35-002-wide-gamut-working-spaces.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `color` | `KGPU-M1-001` | `legacy color transform` |
| [KGPU-M35-003 - Gain map pipeline](KGPU-M35-003-gain-map-pipeline.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `color` | `KGPU-M1-001` | `legacy color transform` |
| [KGPU-M35-004 - ICC profile parsing](KGPU-M35-004-icc-profile-parsing.md) | `proposed` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `color` | `KGPU-M1-001` | `legacy color transform` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
```

## Non-Claims

- This milestone does not activate product routing by being created.
- HDR transfer functions do not imply HDR support on SDR-only targets beyond tone-mapped fallback.
- Gain map pipeline does not claim support without codec-backed Ultra HDR JPEG metadata extraction.
- ICC parsing does not claim ICC v5, LUT profiles (A2B0/B2A0), or named color profiles.
- No readiness movement is claimed without reviewed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and ../STATUS.md in the same change.
