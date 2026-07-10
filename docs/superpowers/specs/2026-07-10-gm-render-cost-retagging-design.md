# GM render-cost retagging design

## Objective

Replace the current blanket `RenderCost.BLOCKING` classification with a
measurement-backed classification. A GM stays `BLOCKING` only when its render
cost is genuinely excessive under the selected execution environment.

## Classification policy

Each GM is measured three times with a 10,000 ms per-render watchdog. The
classification uses the median of completed render durations:

| Condition | Tag |
|---|---|
| At least two of the three attempts time out or fail to complete within 10,000 ms | `BLOCKING` |
| Median is below 1,000 ms | `FAST` |
| Median is at least 1,000 ms and below 5,000 ms | `MEDIUM` |
| Median is at least 5,000 ms and below 10,000 ms | `SLOW` |

An isolated timeout does not make a GM blocking; the individual fallback
measurement records it as instability evidence. A non-timeout rendering error
is reported separately and does not silently become a timing classification.

## Measurement workflow

1. Enumerate all currently `RenderCost.BLOCKING` GM entries.
2. Partition them into fixed batches of five GM names.
3. Run each batch three times with the existing per-GM scanner and a 10-second
   watchdog, collecting each GM's render duration.
4. If a batch is interrupted by a timeout, run every GM in that batch
   individually for the remaining attempts, retaining all completed samples.
5. Aggregate samples, determine the proposed tag, and emit a durable inventory
   containing sample durations, timeout/error counts, median, and rationale.
6. Review the inventory before changing the `renderCost` source declarations.
7. Apply only the reviewed changes, then re-run focused validation and confirm
   that dashboard/default-render selection reflects the reclassified rows.

## Boundaries

- This work classifies execution cost only; it does not promote rendering
  fidelity or turn expected-unsupported output into supported behavior.
- `BLOCKING` continues to exclude a GM from default generation and dashboard
  data. Non-blocking tags restore normal selection according to the existing
  runner behavior.
- The measurement report records the machine/runtime context so that a future
  rebaseline is distinguishable from an implementation performance change.

## Failure handling

The existing scanner terminates its JVM on a watchdog timeout. The orchestration
therefore treats a timed-out batch as incomplete rather than treating unvisited
GM entries as failures. It decomposes that batch to unit runs and resumes the
next batch independently. This preserves per-GM attribution while avoiding
unnecessary process-start overhead in healthy batches.

## Verification

- Assert every currently blocking GM has exactly three classified attempts or
  an explicitly recorded incomplete/error state.
- Verify all batch timeouts are followed by individual attempts for the batch's
  five members.
- Validate the generated inventory schema and deterministic tag calculation.
- Run focused Skia GM selection tests after applying reviewed source changes.
