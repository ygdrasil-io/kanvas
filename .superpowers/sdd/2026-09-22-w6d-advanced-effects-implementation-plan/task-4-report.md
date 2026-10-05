# Task 4 report — NEEDS_CONTEXT

## Status

`NEEDS_CONTEXT` — no production or test implementation was started, and the
worktree remains clean.  The requested vertical slice cannot be completed
without choosing an owner and frozen contract for turning a filter-owned
`SceneSnapshot` into a resource inside the existing W6 graph.

## Preflight evidence

Base is `d1c7fc3bf` on `codex/w6d-advanced-effects` and `git status --short`
was empty before this report.

The required immutable capture/wire pieces already exist:

* `ImageFilterNode.Picture` and `CapturedFilterNodeV1.Picture` own a
  `SceneSnapshot`, and copy cull/source rectangles.
* `PaintSceneAdapter` captures `ImageFilter.Picture` through
  `capturePicture(picture)`.
* Picture 15/schema 9 serializes the captured table node and its bounded
  scene recursively; table IDs preserve capture identity for shared captured
  filters while equal distinct public filters receive separate entries.

Execution is deliberately incomplete, rather than a small missing arm:

* `W6bFilterGraphConstruction.bindInput` rejects `CapturedFilterInputV1.Picture`
  as a W6d input.
* Its `materializeNode` switch has no `CapturedFilterNodeV1.Picture` branch.
* `W6aLayerPlanCompiler` classifies `FilterPassOperationV1.Picture` as not
  materialized.
* `GPUWgpu4kW6aLayerFramePayloadMaterializer` throws for
  `FilterPassOperationV1.Picture` before rendering.

The existing W6 graph only discovers nested scenes when they occur as
`GeometryNode.Picture` in the root scene.  It has no frozen source/resource
bridge for a `SceneSnapshot` held by a filter-table node.  Implementing one
requires deciding between (at least) an inline graph expansion with nested
occurrence/source mappings, or a first-class graph-owned captured-scene source
pass.  The former changes recursive W6c occurrence discovery; the latter
changes `PlanPass`, resource/lifetime validation, scheduling, and renderer
materialization.  Selecting either without an approved contract would risk a
second graph/allocator/submit path, precisely what Task 4 forbids.

## Requested RED selectors

Both commands were first blocked by the sandboxed Gradle wrapper lock; reruns
with the approved local Gradle cache access reached Gradle normally.

| Command | Result | Relevant output |
| --- | --- | --- |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.picture.W6dPictureRuntimeEffectPictureTest'` | exit 1 | `No tests found for given includes` |
| `rtk ./gradlew :kanvas:test --tests 'org.graphiks.kanvas.surface.W6dPictureFilterSurfacePixelTest'` | exit 1 | `No tests found for given includes` |

These are not behavioral REDs, so no production code was written.  The named
test files do not exist at this base.

## GREEN gates

Not run: without an approved source/resource bridge there is no valid minimal
implementation to verify.  No GREEN claim is made.

## XML and native custody

No requested test class exists, therefore there is no class XML method count
(`F/E/S`: N/A) for either selector.  No test worker reached native rendering;
native 133/134 status is N/A, not GREEN and not `UNKNOWN`.

## Smallest causal split / decision required

Approve one narrow preceding contract slice:

1. Define a single W6 graph-owned `CapturedSceneSource` pass/resource that
   recursively lowers a bounded `SceneSnapshot` through the existing W6c
   occurrence and source-context rules, including target/uniform/image/staging
   and lease accounting before graph freeze.
2. Define its exact frozen renderer operands and lifecycle validation.
3. Then Task 4 can add only the `Picture` operation arm, public memory/wire
   mutation witnesses, and materializer consumption of those frozen operands.

This split retains W6c as the only graph/source-context authority and avoids
live `Surface`, codec, allocator, or submit coupling.  It also makes the
required public RED meaningful: before the `Picture` arm is wired, a real
public surface witness will reach the documented W6d terminal refusal rather
than a missing test selector.

## Self-review / concerns

No in-scope dirty implementation was created.  The only changed file is this
blocker report.  Main concern: treating the existing recursive archive as
sufficient execution ownership would hide the missing physical source bridge
and invite an illegal replay or second graph.
