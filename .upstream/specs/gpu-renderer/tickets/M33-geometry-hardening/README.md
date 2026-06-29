# M33 - Geometry Hardening

**Status:** active (2026-06-28) — Wave A Track 2


## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
```

## Non-Claims

- This milestone does not activate product routing by being created.
- Compute tessellation does not imply GPU-native path tessellation for all path types or stroke styles.
- Perspective acceptance does not extend to text, image, filter, or layer routes.
- No readiness movement is claimed without reviewed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and ../STATUS.md in the same change.
