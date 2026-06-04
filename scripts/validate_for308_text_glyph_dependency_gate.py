#!/usr/bin/env python3
"""Generate and validate FOR-308 text/glyph dependency gate evidence."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True


PROJECT_ROOT = Path(__file__).resolve().parents[1]
LINEAR_ID = "FOR-308"
SOURCE_MEMORY = "global/kanvas/ticket-drafts/draft-for-next-text-glyph-dependency-gate-ticket"

ARTIFACT_DIR = (
    PROJECT_ROOT
    / "reports/wgsl-pipeline/scenes/artifacts/text-glyph-dependency-gate-for308"
)
ARTIFACT = ARTIFACT_DIR / "text-glyph-dependency-gate-for308.json"
REPORT = PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-308-text-glyph-dependency-gate.md"

DECISION_APPLIED = "TEXT_GLYPH_DEPENDENCY_GATE_APPLIED"
DECISION_UNSAFE = "TEXT_GLYPH_DEPENDENCY_UNSAFE_SUBSTITUTE_FOUND"
DECISION_AMBIGUOUS = "TEXT_GLYPH_DEPENDENCY_GATE_AMBIGUOUS"

GENERATED_RESULTS = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/results.json"
M52_PACK = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m52-inventory-promotion-pack.json"
M62_PACK = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m62-font-fallback-evidence.json"
M66_PACK = PROJECT_ROOT / "reports/wgsl-pipeline/scenes/generated/m66-gm-promotion-wave.json"

SOURCE_REPORTS = [
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-05-31-m50-font-text-evidence-pack.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-01-m62-font-text-baseline-audit.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-01-m62-glyph-route-dashboard-diagnostics.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-01-m62-missing-glyph-fallback-evidence.md",
    PROJECT_ROOT / "reports/wgsl-pipeline/2026-06-04-for-304-renderer-feature-conversion-wave-closeout.md",
    PROJECT_ROOT / ".upstream/specs/font/README.md",
    PROJECT_ROOT / ".upstream/specs/font/05-color-fonts-emoji-and-fixtures.md",
    PROJECT_ROOT / ".upstream/specs/font/06-validation-and-conformance.md",
]

SIMPLE_PASS_ROWS = {
    "font-latin-outline-drawstring": "webgpu.text.outline.simple-latin",
    "font-textblob-positioned-glyph-run": "webgpu.text.outline.positioned-glyph-run",
    "font-kerning-style-fixture": "webgpu.text.outline.kerning-style-fixture",
}

M66_SIMPLE_PASS_ROWS = {
    "m66-font-latin-outline-cpu-oracle": "webgpu.text.outline.simple-latin",
    "m66-font-positioned-glyph-run-cpu-oracle": "webgpu.text.outline.positioned-glyph-run",
    "m66-font-kerning-style-cpu-oracle": "webgpu.text.outline.kerning-style-fixture",
}

REFUSAL_ROWS = {
    "font-emoji-color-glyph-refusal": "font.color-glyph-emoji-unsupported",
    "font-complex-shaping-refusal": "font.complex-shaping-requires-explicit-shaper",
    "m52-color-emoji-blendmodes-refusal": "font.color-glyph-emoji-unsupported",
    "m62-missing-glyph-fallback-refusal": "font.missing-glyph-fallback-unsupported",
    "m66-font-complex-shaping-refusal": "font.complex-shaping-requires-explicit-shaper",
}

REQUIRED_FUTURE_PROMOTION_PROOF = [
    "owning font spec section",
    "font fixture and provenance",
    "text input and shaping mode",
    "glyph diagnostics",
    "reference or CPU oracle",
    "CPU artifact and stats",
    "adapter-backed GPU artifact and stats when support is claimed",
    "CPU/GPU diff artifacts when support is claimed",
    "route diagnostics",
    "stable fallback policy for non-selected rows",
    "focused font or WebGPU text tests",
]

FORBIDDEN_SUBSTITUTES = [
    "hidden HarfBuzz/FreeType/Fontations/CoreText/DirectWrite/fontconfig/JNI dependency",
    "native emoji renderer",
    "platform font fallback",
    "different font or glyph substitution",
    "glyph atlas, mask, SDF, or LCD claim without artifacts",
    "fallback reason or status relabel without implementation evidence",
]


def fail(message: str) -> None:
    raise SystemExit(f"FOR-308 validation failed: {message}")


def rel(path: Path) -> str:
    return str(path.relative_to(PROJECT_ROOT))


def read_text(path: Path) -> str:
    if not path.is_file():
        fail(f"missing source report: {rel(path)}")
    return path.read_text(encoding="utf-8")


def compact(text: str) -> str:
    return " ".join(text.split())


def load_json(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        fail(f"{rel(path)} must contain a JSON object")
    return data


def rows_from(path: Path) -> list[dict[str, Any]]:
    data = load_json(path)
    scenes = data.get("scenes")
    if not isinstance(scenes, list):
        fail(f"{rel(path)} must contain a scenes list")
    rows: list[dict[str, Any]] = []
    for scene in scenes:
        if not isinstance(scene, dict):
            fail(f"{rel(path)} scene entries must be objects")
        row = scene.get("row", scene)
        if not isinstance(row, dict):
            fail(f"{rel(path)} row entries must be objects")
        rows.append(row)
    return rows


def fallback_reason(row: dict[str, Any]) -> str | None:
    direct = row.get("fallbackReason")
    if isinstance(direct, str):
        return direct
    gpu = row.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict):
            reason = route.get("fallbackReason")
            if isinstance(reason, str):
                return reason
    return None


def selected_route(row: dict[str, Any]) -> str | None:
    direct = row.get("gpuRoute")
    if isinstance(direct, str):
        return direct
    gpu = row.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict):
            selected = route.get("selectedRoute")
            if isinstance(selected, str):
                return selected
    return None


def pipeline_key(row: dict[str, Any]) -> str:
    direct = row.get("pipelineKey")
    if isinstance(direct, str):
        return direct
    gpu = row.get("gpu")
    if isinstance(gpu, dict):
        route = gpu.get("route")
        if isinstance(route, dict):
            key = route.get("pipelineKey")
            if isinstance(key, str):
                return key
    return ""


def glyph_diagnostics(row: dict[str, Any]) -> str | None:
    font = row.get("font")
    if isinstance(font, dict):
        diagnostic = font.get("glyphDiagnostics")
        if isinstance(diagnostic, str):
            return diagnostic
    cpu = row.get("cpu")
    if isinstance(cpu, dict):
        font_info = cpu.get("font")
        if isinstance(font_info, dict):
            diagnostic = font_info.get("glyphDiagnostics")
            if isinstance(diagnostic, str):
                return diagnostic
    return None


def row_map(paths: list[Path]) -> dict[str, dict[str, Any]]:
    rows: dict[str, dict[str, Any]] = {}
    for path in paths:
        for row in rows_from(path):
            scene_id = row.get("id")
            if isinstance(scene_id, str):
                rows[scene_id] = row
    return rows


def validate_source_reports() -> dict[str, str]:
    snippets = {
        SOURCE_REPORTS[0]: [
            "does not claim broad font, emoji, complex shaping",
            "font.color-glyph-emoji-unsupported",
            "font.complex-shaping-requires-explicit-shaper",
        ],
        SOURCE_REPORTS[1]: [
            "not a glyph mask atlas",
            "Complex shaping rows cannot silently become `pass`",
            "No fallback-family selection support is claimed.",
        ],
        SOURCE_REPORTS[2]: [
            "No. The current passing text rows use generated glyph outlines",
            "m62-missing-glyph-fallback-refusal",
        ],
        SOURCE_REPORTS[3]: [
            "font.missing-glyph-fallback-unsupported",
            "No fallback-family selection support is claimed.",
        ],
        SOURCE_REPORTS[4]: [
            "Text/glyph dependency gate",
            "Real shaper/font/color-glyph dependency delivery",
        ],
        SOURCE_REPORTS[5]: [
            "Do not add external font libraries",
            "Do not clear font-gated rows with short-lived substitutes",
        ],
        SOURCE_REPORTS[6]: [
            "Do not clear them by routing through native emoji",
            "font.emoji-table-dispatch-unavailable",
        ],
        SOURCE_REPORTS[7]: [
            "Do not replace a refusal by silently drawing a different font",
            "No validation path depends on external font libraries",
        ],
    }
    summary: dict[str, str] = {}
    for path, needles in snippets.items():
        text = read_text(path)
        compact_text = compact(text)
        compact_lower = compact_text.lower()
        for needle in needles:
            compact_needle = compact(needle)
            if needle not in text and compact_needle not in compact_text and compact_needle.lower() not in compact_lower:
                fail(f"{rel(path)} missing required text `{needle}`")
        summary[rel(path)] = "verified"
    return summary


def validate_pass_rows(rows: dict[str, dict[str, Any]]) -> list[dict[str, str]]:
    evidence: list[dict[str, str]] = []
    expected = {**SIMPLE_PASS_ROWS, **M66_SIMPLE_PASS_ROWS}
    for scene_id, route in expected.items():
        row = rows.get(scene_id)
        if row is None:
            fail(f"missing text pass row {scene_id}")
        if row.get("status") != "pass":
            fail(f"{scene_id} must remain pass")
        if fallback_reason(row) != "none":
            fail(f"{scene_id} must keep fallbackReason=none")
        actual_route = selected_route(row)
        if actual_route != route:
            fail(f"{scene_id} route expected {route}, got {actual_route}")
        key = pipeline_key(row)
        if "glyphRepresentation=outline" not in key:
            fail(f"{scene_id} must remain an outline glyph representation")
        if "atlas" in key.lower() or "mask" in key.lower() or "sdf" in key.lower() or "lcd" in key.lower():
            fail(f"{scene_id} must not claim atlas/mask/SDF/LCD text support")
        if glyph_diagnostics(row) is None:
            fail(f"{scene_id} must keep glyph diagnostics")
        evidence.append(
            {
                "id": scene_id,
                "status": "pass",
                "route": route,
                "pipelineKey": key,
                "fallbackReason": "none",
            }
        )
    return evidence


def validate_refusal_rows(rows: dict[str, dict[str, Any]]) -> list[dict[str, str]]:
    evidence: list[dict[str, str]] = []
    for scene_id, expected_reason in REFUSAL_ROWS.items():
        row = rows.get(scene_id)
        if row is None:
            fail(f"missing text/glyph refusal row {scene_id}")
        if row.get("status") != "expected-unsupported":
            fail(f"{scene_id} must remain expected-unsupported")
        actual_reason = fallback_reason(row)
        if actual_reason != expected_reason:
            fail(f"{scene_id} fallback expected {expected_reason}, got {actual_reason}")
        route = selected_route(row) or ""
        if "refuse" not in route:
            fail(f"{scene_id} must keep a refuse route, got {route}")
        key = pipeline_key(row)
        if "unsupported" not in key and "missing" not in key:
            fail(f"{scene_id} pipelineKey must keep unsupported/missing signal")
        if glyph_diagnostics(row) is None:
            fail(f"{scene_id} must keep glyph diagnostics")
        evidence.append(
            {
                "id": scene_id,
                "status": "expected-unsupported",
                "route": route,
                "pipelineKey": key,
                "fallbackReason": expected_reason,
            }
        )
    return evidence


def classify_policy_case(
    *,
    support_claim: bool,
    external_dependency: bool,
    native_api: bool,
    changed_font_or_glyph: bool,
    relabels_refusal: bool,
    atlas_or_mask_claim: bool,
    complete_proof: bool,
) -> tuple[str, bool, str]:
    if external_dependency or native_api or changed_font_or_glyph:
        return (
            "forbidden",
            False,
            "Hidden dependency, native API, or font/glyph substitution cannot clear text/glyph gates.",
        )
    if relabels_refusal:
        return (
            "forbidden",
            False,
            "Changing a refusal reason or status requires a dedicated implementation proof.",
        )
    if atlas_or_mask_claim and not complete_proof:
        return (
            "forbidden",
            False,
            "Atlas/mask/SDF/LCD claims require explicit artifacts and diagnostics.",
        )
    if not support_claim:
        return (
            "diagnostic-gate",
            True,
            "Diagnostic guard is allowed because it preserves existing support and refusal boundaries.",
        )
    if not complete_proof:
        return (
            "ambiguous",
            False,
            "Support claim lacks the complete text/glyph promotion proof set.",
        )
    return (
        "future-promotion-candidate",
        True,
        "Complete proof belongs in a future implementation ticket, not this gate-only ticket.",
    )


def validate_policy_cases() -> list[dict[str, Any]]:
    cases = [
        {
            "name": "FOR-308 diagnostic dependency gate",
            "support_claim": False,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": False,
            "expected": "diagnostic-gate",
        },
        {
            "name": "Hidden shaping or font library substitute is forbidden",
            "support_claim": True,
            "external_dependency": True,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": True,
            "expected": "forbidden",
        },
        {
            "name": "Native emoji or platform fallback is forbidden",
            "support_claim": True,
            "external_dependency": False,
            "native_api": True,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": True,
            "expected": "forbidden",
        },
        {
            "name": "Different font or glyph substitution is forbidden",
            "support_claim": True,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": True,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": True,
            "expected": "forbidden",
        },
        {
            "name": "Fallback relabel without implementation proof is forbidden",
            "support_claim": True,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": True,
            "atlas_or_mask_claim": False,
            "complete_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Atlas or glyph mask claim without artifacts is forbidden",
            "support_claim": True,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": True,
            "complete_proof": False,
            "expected": "forbidden",
        },
        {
            "name": "Support claim without full proof is ambiguous",
            "support_claim": True,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": False,
            "expected": "ambiguous",
        },
        {
            "name": "Complete local proof can become a future promotion candidate",
            "support_claim": True,
            "external_dependency": False,
            "native_api": False,
            "changed_font_or_glyph": False,
            "relabels_refusal": False,
            "atlas_or_mask_claim": False,
            "complete_proof": True,
            "expected": "future-promotion-candidate",
        },
    ]
    rows: list[dict[str, Any]] = []
    for case in cases:
        decision, allowed, reason = classify_policy_case(
            support_claim=case["support_claim"],
            external_dependency=case["external_dependency"],
            native_api=case["native_api"],
            changed_font_or_glyph=case["changed_font_or_glyph"],
            relabels_refusal=case["relabels_refusal"],
            atlas_or_mask_claim=case["atlas_or_mask_claim"],
            complete_proof=case["complete_proof"],
        )
        if decision != case["expected"]:
            fail(f"policy case `{case['name']}` expected {case['expected']}, got {decision}")
        rows.append(
            {
                "name": case["name"],
                "decision": decision,
                "allowed": allowed,
                "reason": reason,
            }
        )
    return rows


def validate_gate() -> dict[str, Any]:
    source_reports = validate_source_reports()
    rows = row_map([GENERATED_RESULTS, M52_PACK, M62_PACK, M66_PACK])
    pass_evidence = validate_pass_rows(rows)
    refusal_evidence = validate_refusal_rows(rows)
    policy_cases = validate_policy_cases()
    return {
        "linear": LINEAR_ID,
        "sourceMemory": SOURCE_MEMORY,
        "decision": DECISION_APPLIED,
        "supportDecision": "KEEP_TEXT_GLYPH_DEPENDENCY_GATED_UNTIL_REAL_DELIVERY",
        "sourceReports": source_reports,
        "supportedBoundary": pass_evidence,
        "preservedRefusals": refusal_evidence,
        "forbiddenSubstitutes": FORBIDDEN_SUBSTITUTES,
        "requiredFuturePromotionProof": REQUIRED_FUTURE_PROMOTION_PROOF,
        "policyCases": policy_cases,
        "alternateDecisions": {
            "unsafe": DECISION_UNSAFE,
            "ambiguous": DECISION_AMBIGUOUS,
        },
    }


def write_artifact(data: dict[str, Any]) -> None:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACT.write_text(json.dumps(data, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_report(data: dict[str, Any]) -> None:
    supported_rows = "\n".join(
        "| `{id}` | `{status}` | `{route}` | `{fallbackReason}` |".format(**row)
        for row in data["supportedBoundary"]
    )
    refusal_rows = "\n".join(
        "| `{id}` | `{status}` | `{route}` | `{fallbackReason}` |".format(**row)
        for row in data["preservedRefusals"]
    )
    policy_rows = "\n".join(
        "| {name} | `{decision}` | {allowed} | {reason} |".format(**row)
        for row in data["policyCases"]
    )
    substitute_rows = "\n".join(f"- {item}" for item in data["forbiddenSubstitutes"])
    proof_rows = "\n".join(f"- {item}" for item in data["requiredFuturePromotionProof"])

    report = f"""# FOR-308 Text/Glyph Dependency Gate

Linear: `{LINEAR_ID}`

Source memory:
`{SOURCE_MEMORY}`

Decision: `{data["decision"]}`

## Result

FOR-308 applies a text/glyph dependency gate. Existing simple text rows remain
limited to outline/path glyph rendering. Complex shaping, emoji/color glyph,
and missing-glyph/fallback-family rows remain expected unsupported until real
font/text deliveries land with complete evidence.

This ticket does not change renderer code, shader code, font backend behavior,
scene status, fallback reasons, thresholds, or readiness score.

## Supported Boundary Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
{supported_rows}

## Refusals Preserved

| Scene id | Status | GPU route | Fallback reason |
|---|---|---|---|
{refusal_rows}

## Forbidden Substitutes

{substitute_rows}

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
{policy_rows}

## Required Future Promotion Proof

{proof_rows}

## Validation

- `rtk python3 scripts/validate_for308_text_glyph_dependency_gate.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool {rel(ARTIFACT)}`
- `rtk git diff --check origin/master...HEAD`
"""
    REPORT.write_text(report, encoding="utf-8")


def main() -> None:
    data = validate_gate()
    write_artifact(data)
    write_report(data)
    load_json(ARTIFACT)
    if DECISION_APPLIED not in read_text(REPORT):
        fail("report does not contain the applied decision")
    print(f"{LINEAR_ID}: {DECISION_APPLIED}")
    print(f"artifact={rel(ARTIFACT)}")
    print(f"report={rel(REPORT)}")


if __name__ == "__main__":
    main()
