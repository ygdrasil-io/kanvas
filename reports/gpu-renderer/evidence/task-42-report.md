# W62 — Picture record/replay evidence

Audit of `PictureRecorder`, immutable `Picture`, and `DisplayOp` confirms that
recorded operations are captured in insertion order and replayed with nested
save/restore state. `Picture` exposes nested traversal and resource walkers,
while `DisplayOp` carries transform/clip state on each draw operation.

The current API does not expose an executable GPU prepared-picture route or a
resource lease/expiry state. Consequently this slice adds no hidden fallback or
native claim: recursion/resource-expiry/non-replayable operation refusal remains
dependency-gated at the prepared route boundary. Existing playback behavior is
tested as CPU compatibility behavior only.

Verification:

```text
./gradlew --no-daemon :kanvas:test --tests '*PictureTest'
```

No `gpu-renderer-scenes` files were modified and no commit was created.

Result: 27 `PictureTest` tests passed. This is CPU record/replay evidence; it
does not promote a GPU picture route or claim support for resource leases that
are not represented in the current API.
