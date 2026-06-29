# M35 - Color Fidelity

**Status:** active (2026-06-28) — Wave B Track 3


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
