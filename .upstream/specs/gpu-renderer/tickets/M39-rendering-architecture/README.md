# M39 - Rendering Architecture

**Status:** active (2026-06-28) — Wave C Track 4


## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MSAA*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*InstancedBatch*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*SubpassMerge*'
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DeferredDL*'
```

## Non-Claims

- This milestone does not activate product routing by being created.
- MSAA, instanced batching, subpass merging, and deferred display list
  activation does not imply tile-deferred rendering, multi-threaded recording,
  or Hi-Z occlusion culling (M40).
- A local adapter-backed pass does not by itself make any route release
  blocking.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
