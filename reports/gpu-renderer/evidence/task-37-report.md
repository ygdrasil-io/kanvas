# W51 — Runtime-effect child tree evidence

## Scope

W51 adds a backend-neutral, immutable `GPURuntimeEffectChildTree` contract.
Children are snapshotted, retained in declared slot order and validated against
the registered descriptor before a future CPU reference evaluation or GPU WGSL
lowering. The resulting plan exposes the same ordered child list and uniform
schema identity to both routes, including the local-coordinate flag.

## Stable refusals

- `unsupported.runtime_effect.child_missing` for absent required children;
- `unsupported.runtime_effect.child_extra` for undeclared slots;
- `unsupported.runtime_effect.child_order` for slot-order mismatch;
- `unsupported.runtime_effect.child_kind_mismatch` for Shader/ColorFilter/Blender mismatch;
- `unsupported.runtime_effect.child_depth_exceeded` above depth 64;
- `unsupported.runtime_effect.unregistered_descriptor` and
  `unsupported.runtime_effect.uniform_schema_mismatch` at the descriptor boundary.

Optional null children are accepted only when the descriptor marks the slot as
optional. The current public resolver and route planner do not yet consume this
plan, so W51 does not claim a new pixel-rendering capability; that integration
belongs to the later runtime-effect boundary wave.

## Verification

```text
./gradlew --no-daemon :gpu-renderer:test \
  --tests '*RuntimeEffectChildTreeW51Test' \
  --tests '*RegisteredRuntimeEffectRouteTest'
```

Result: 5 tests passed. `gpu-renderer-scenes` was not modified and no commit
was created.
