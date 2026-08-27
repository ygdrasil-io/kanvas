# Canvas state evidence — 2026-08-28

## Scope

`Canvas` state queries now operate in the caller's local coordinate system for
scale/translate CTMs.  The public recording contract is covered for
`save`, `restore`, `restoreToCount`, clip snapshots, post-restore sentinels,
and empty-stack no-ops.

## Verified code contracts

- `CanvasTest.clip queries stay in local coordinates after a scale translate capture`
  checks inverse-mapped `localClipBounds` and both overlapping and disjoint
  `quickReject` calls after device-space clip capture.
- `CanvasTest.restore to count restores parent clip for the post restore sentinel`
  checks that a nested clip is removed, the saved parent clip is restored, and
  the final sentinel is recorded wide-open after the outer restore.
- `CanvasTest.restoring an empty save stack is a stable no op` keeps baseline
  `restore` and negative `restoreToCount` deterministic and submission-free.

## Render evidence and oracle

The promoted public `scissor-overlay` bundle remains the native GPU evidence
for the production `save` / `clipRect` / `restore` route.  Its independent
`reference-raster-scissor-intersections` CPU oracle and zero-tolerance diff
remain authoritative; this wave does not promote a synthetic parallel route.

`restoreToCount` itself is a Canvas recording-state operation.  Its observable
rendering effect is the clip carried by the next public draw, asserted above
through the exact `DisplayOp` snapshots consumed by the existing Surface path.

## Validation

```text
./gradlew :kanvas:test --tests 'org.graphiks.kanvas.canvas.CanvasTest.clip queries stay in local coordinates after a scale translate capture'
BUILD SUCCESSFUL

./gradlew :kanvas:test --tests 'org.graphiks.kanvas.canvas.CanvasTest.restore to count restores parent clip for the post restore sentinel'
BUILD SUCCESSFUL
```

No new refusal code is introduced: baseline stack underflow remains the
documented deterministic no-op and emits no partial GPU work.
