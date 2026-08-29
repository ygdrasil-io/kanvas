# Evidence catalogue v2 convergence

## Scope

The current executable catalogue contains 73 promoted scene IDs.  Seventy-one
are current public `Surface` cases: 70 renders with independent CPU oracles and
one stable public refusal (`linear-gradient-three-stops`).  The remaining two
entries are explicitly marked `HistoricalStandaloneRefusal`:

- `custom-runtime-effect-unregistered-refusal`;
- `aggregate-memory-budget-refusal`.

They preserve immutable, already-promoted diagnostic bundles, but are not
evidence that the public `Surface` supports those internal recorder routes.
They cannot be declared as render cases, have no image oracle, and cannot be
silently reclassified as public evidence.

## Enforced invariants

- A current public case must use `KanvasSurfaceProgram` and therefore records
  only through the public Kanvas `Surface` API.
- A historical standalone entry is refusal-only and must carry a routed product
  diagnostic; it cannot carry an image oracle.
- Scene IDs remain globally unique across both boundaries.
- A public Surface refusal must happen before GPU submission and its observed
  reason code must exactly match the descriptor.  A rendered refusal, or a
  refusal of a render claim, is an execution failure rather than a promotable
  observation.
- The promoted v2 verifier continues to bind every scene to its catalogue
  manifest SHA-256, expected route, oracle/refusal contract, source commit and
  root adapter/environment metadata.

## Verification

Executed on this branch:

```text
./gradlew --no-daemon :integration-tests:gpu-evidence:test
./gradlew --no-daemon :integration-tests:gpu-evidence:verifyPromotedGpuEvidence
```

The promoted verification accepted all 73 immutable bundles.  This report does
not promote a new render, rebaseline pixels, or claim that either historical
standalone refusal is public `Surface` support.
