---
id: "KFONT-M7-005"
title: "Add host-dependent system scan diagnostics"
status: "done"
milestone: "M7"
priority: "P1"
owner_area: "fallback"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M1-001", "KFONT-M1-003", "KFONT-M7-001", "KFONT-M7-002"]
legacy_gate: null
---

# KFONT-M7-005 - Add host-dependent system scan diagnostics

## PM Note

Ce ticket permet d'utiliser des fontes systeme localement tout en indiquant clairement qu'elles ne sont pas une preuve normative.

## Problem

System font scanning is useful for local demos, but host-installed fonts vary by OS, user, package set, and permissions. The target allows pure Kotlin directory scans only when every source is marked host-dependent, skipped files are diagnosed, ordering is deterministic, and normative evidence never depends on unavailable host bytes.

## Scope

- Implement or specify pure Kotlin directory scanning for configured font roots without platform font APIs, fontconfig, AWT, CoreText, or DirectWrite.
- Record path, length, mtime policy, content hash policy, supported table facts, face count, parser diagnostics, duplicate detection, and host-dependent marker for each scanned source.
- Emit `system-font-scan.json` and link host-scanned entries into `font-catalog.json` and `fallback-decision-trace.json` only as non-normative sources.
- Add diagnostics for unreadable files, skipped directories, malformed fonts, unsupported wrappers, duplicate faces, and host-dependent evidence.
- Keep deterministic sorting and stable IDs for the same captured bytes and scan configuration.

## Non-Goals

- Do not make host system fonts part of normative support evidence unless their bytes are captured as fixtures.
- Do not use OS font APIs, fontconfig, native libraries, or platform fallback.
- Do not guarantee identical fallback results across machines for host-dependent scans.
- Do not implement UI font picker behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class SystemFontScanConfig(
    val roots: List<FileSystemPath>,
    val includeHidden: Boolean,
    val maxBytesPerFile: Long,
    val hashPolicy: FontHashPolicy,
)

data class SystemFontScanEntry(
    val sourceId: FontSourceID,
    val pathFingerprint: String,
    val contentHash: Sha256?,
    val hostDependent: Boolean = true,
    val faceCount: Int?,
    val tableFacts: Set<TableTag>,
    val diagnostics: List<RouteDiagnostic>,
)

data class SystemFontScanReport(
    val hostFingerprint: HostFontScanFingerprint,
    val entries: List<SystemFontScanEntry>,
    val skipped: List<SystemFontSkipDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Scanning the same fixture directory twice with the same config produces deterministic entry ordering and stable diagnostics.
- [ ] Unreadable, malformed, unsupported-wrapper, duplicate, and oversized file fixtures emit precise diagnostics.
- [ ] Every system-scanned entry is marked host-dependent in `system-font-scan.json`, `font-catalog.json`, and fallback traces.
- [ ] Host-dependent entries are excluded from normative support evidence unless source bytes are captured as fixtures.
- [ ] The scanner does not call platform font APIs or native libraries.

## Required Evidence

- `system-font-scan.json` with scan config hash, host marker, deterministic ordering, entries, skipped paths, content hash policy, parser table facts, and diagnostics.
- `font-catalog.json` and `fallback-decision-trace.json` examples showing host-dependent markers propagated.
- Fixtures: `system-scan-fixture-dir/valid.ttf`, `system-scan-fixture-dir/duplicate.ttf`, `system-scan-fixture-dir/malformed.otf`, `system-scan-fixture-dir/unsupported-wrapper.pfb`, `system-scan-fixture-dir/oversized.ttf`, `system-scan-fixture-dir/unreadable.ttf`.
- Diagnostics asserted in tests: `font.source.host-dependent`, `font.source.unreadable`, `font.outline-format.unsupported-wrapper`, `font.required-table-missing`, `font.catalog.duplicate-face`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.source.host-dependent`, `font.source.unreadable`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add host-dependent system scan diagnostics`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*SystemFont*' --tests '*HostDependent*'
```

## Status Notes

- `proposed`: System scanning is allowed only as pure Kotlin, host-dependent evidence.
- `done`: bounded deterministic fixture-dir evidence now lands `system-font-scan.json` plus linked host-dependent catalog/fallback examples without promoting bundled-catalog, normative fallback, or platform-font claims. The reviewed product decision about whether host-dependent links should be folded into the main `font-catalog.json` / shared fallback dumps remains explicit follow-up scope, and any broader scan-root expansion still requires reviewed provenance.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:tracked-gap`
