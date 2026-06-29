# M40 - Architecture Capabilities

**Status:** active (2026-06-28) — Wave D


## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*TileDeferred*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MultiThreaded*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HiZ*'
```

## Non-Claims

- This milestone does not activate product routing by being created.
- Tile-deferred rendering does not imply GPU-native tiled rendering for non-rect geometry types.
- Multi-threaded recording does not imply thread-safe unguarded GPUResource access or concurrent GPUExecutionContext mutation.
- Hi-Z occlusion does not imply GPU-native occlusion for translucent draws or depth-unavailable targets.
- No readiness movement is claimed without reviewed evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
