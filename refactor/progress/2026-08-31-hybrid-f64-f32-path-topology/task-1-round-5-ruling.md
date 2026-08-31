# Task 1 — Round 5 disjoint-claims ruling

The Task-1 brief originally required the multi-witness transition fixture to
reject conservatively. At that point the legacy projector did not carry enough
provenance to prove that two claims on one source span were independent.

The round-3 ruling authorized a temporary exact-provenance bridge. With that
bridge, the fixture can distinguish disjoint claims locally and preserve all
three filled regions. The controller therefore resolves the transitional test
against the approved final design:

- two claims proven disjoint on the same source span must remain independent and
  may both be kept;
- claims whose source-parameter intervals overlap must reject atomically;
- a `PointF64` witness alone must never authorize an `OverlapF32`;
- any missing or ambiguous provenance rejects with
  `path-f32-projection-collapse` before partial output.

The behavioral test must assert membership of all regions, input immutability,
and stable behavior under the specified permutations/relabelings. Separate
tests retain exact rejection oracles for overlapping claims and for an F32
overlap supported only by `PointF64`.

This ruling does not authorize Task-2 alias groups or projected-coincidence
construction. It only uses exact claims already transported by the temporary
legacy bridge.

