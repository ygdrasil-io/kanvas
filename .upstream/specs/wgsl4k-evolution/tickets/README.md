# wgsl4k Evolution Ticket Catalog

This catalog tracks the repo-native work needed to turn the Kanvas wgsl4k gate
into a reviewed submodule contribution and a consumable Kanvas validation
dependency.

The tickets are deliberately separate from the GPU renderer milestone catalog.
They do not promote product rendering support. They prepare dependency evidence
that blocked GPU/Text tickets may consume later.

## Ticket Section Order

Tickets use this body order:

1. `PM Note`
2. `Problem`
3. `Scope`
4. `Non-Goals`
5. `Spec Sources`
6. `Acceptance Criteria`
7. `Required Evidence`
8. `Fallback / Refusal Behavior`
9. `Validation`
10. `Status Notes`
11. `Labels`

## Status Model

| Status | Meaning |
|---|---|
| `proposed` | Written but not yet accepted for execution. |
| `ready` | Accepted and ready to implement. |
| `in-progress` | Currently being implemented. |
| `blocked` | Cannot progress until a named blocker is resolved. |
| `review` | Spec or evidence exists and requires review before it can be treated as accepted. |
| `done` | Completed with accepted review, linked evidence, and no pending claim ambiguity. |
| `deferred` | Intentionally postponed. |

## Tickets

| Ticket | Status | Purpose |
|---|---|---|
| `WGSL4K-EVO-001` | `done` | Define the validation/reflection contract and contribution packet. |
| `WGSL4K-EVO-002` | `done` | Import wgsl4k as a tracked submodule. |
| `WGSL4K-EVO-003` | `done` | Prepare the wgsl4k fixture branch and PR. |
| `WGSL4K-EVO-004` | `review` | Consume wgsl4k reflection in Kanvas reports. |
| `WGSL4K-EVO-005` | `review` | Re-evaluate blocked GPU/Text tickets after fresh dependency evidence. |

## Status

See [STATUS.md](STATUS.md).
