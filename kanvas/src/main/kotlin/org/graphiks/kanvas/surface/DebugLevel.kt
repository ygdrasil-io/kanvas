package org.graphiks.kanvas.surface

/**
 * Controls the depth of diagnostic capture during rendering.
 *
 * - [OFF]: no capture, zero overhead.
 * - [PIXEL]: Layer 1 — enriched pixel diff (heatmap, SSIM, zones).
 * - [OP]: Layer 1 + 2 — per-operation isolation and blame.
 * - [TRACE]: Layer 1 + 2 + 3 — full pipeline trace per operation.
 *
 * Each level includes all lower levels.
 */
enum class DebugLevel { OFF, PIXEL, OP, TRACE }
