# M36 - Image Pipeline Extension

**Status:** active (2026-06-28) — Wave C Track 1


## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HEIF*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*YUV*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*Mipmap*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HardwareCodec*'
```

## Non-Claims

- This milestone does not ship codec binaries or link against platform-specific
  hardware codec libraries.
- HEIF/AVIF gate promotion does not silently accept patent-encumbered profiles.
- YUV conversion does not claim pixel-exact parity with all platform decoder
  outputs.
- Mipmap generation does not cover arbitrary non-power-of-two 3D texture arrays.
- Hardware codec descriptors do not imply Android Bitmap/MediaCodec leakage
  into `:gpu-renderer`.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
