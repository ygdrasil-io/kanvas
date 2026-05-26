# ADR 0004: PipelineKey Axis Taxonomy

Status: Accepted

## Context

Generated WGSL and WebGPU pipelines need cache keys. Adding every draw fact to
the key would create cache pressure and hide uniform-only variation as code or
pipeline specialization.

## Decision

Classify every candidate axis before adding it to a generated GPU key:

- layout-affecting;
- code-affecting;
- pipeline-state-affecting;
- uniform-only.

Only layout, code, and pipeline-state axes belong in generated GPU keys by
default. Uniform-only values stay in uniforms unless profiling justifies a
focused exception.

The canonical key preimage is the versioned human-readable dump. It is hashed
with SHA-256 for cache lookup; debug artifacts keep both dump and hash.

## Consequences

- Pipeline cache behavior is measurable.
- Uniform changes do not cause shader-module explosions.
- New axes require review and tests.
- Diagnostics can explain why a fact is or is not part of the key.
- Collision handling is explicit: incompatible preimages with the same hash are
  fatal in debug/test builds and safe misses in production.
