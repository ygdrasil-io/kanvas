# M38 - Runtime Effects V2

**Status:** active (2026-06-28) — Wave C Track 3


## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*LiveEdit*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*EffectKinds*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ShaderGraph*'
```

## Non-Claims

- This milestone does not activate product routing for runtime effects.
- Live parameter editing does not imply general-purpose shader authoring tools.
- Extended effect kinds do not include PixelLocal or tile-shading compute.
- Shader graph assembly does not cover general-purpose WGSL linker/combiner.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
