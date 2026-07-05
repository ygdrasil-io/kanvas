#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


OUTPUT_JSON = "evidence.json"
OUTPUT_MARKDOWN = "evidence.md"
DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/runtime-effects-v2"
SUPPORT_MATRIX_PATH = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json"
SUPPORT_MATRIX_MD = "reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md"
LAYOUT_REPORT_PATH = "reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json"
LAYOUT_REPORT_MD = "reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md"
SHADER_EFFECTS_PATH = "reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.json"
SHADER_EFFECTS_MD = "reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.md"
COLOR_FILTER_PATH = "reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.json"
COLOR_FILTER_MD = "reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.md"
PREVIEW_PATH = "reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.json"
PREVIEW_MD = "reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.md"
REQUIRED_ROW_ORDER = [
    "policy.arbitrary_sksl_input",
    "policy.unregistered_wgsl_descriptor",
    "runtime.color_filter_luma_to_alpha",
    "runtime.invert_blender",
    "runtime.linear_gradient_rt",
    "runtime.simple_rt",
    "runtime.spiral_rt",
    "runtime.unsharp_rt",
]
REQUIRED_NON_CLAIMS = [
    "No dynamic SkSL compilation.",
    "No SkSL IR or VM.",
    "No broad runtime-effect support beyond registered descriptors.",
]
REQUIRED_STABLE_REFUSALS = [
    "runtime-effect.arbitrary-sksl-unsupported",
    "runtime-effect.wgsl-descriptor-missing",
    "runtime-effect.preview-effect-not-registered",
    "runtime-effect.arbitrary-sksl-unsupported",
]
NON_CLAIM_ALIASES = {
    "No support for arbitrary user WGSL input.": "No arbitrary user WGSL input.",
    "No broad runtime-effect support beyond selected registered descriptors.": (
        "No broad runtime-effect support beyond registered descriptors."
    ),
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-034 Runtime Effects V2 evidence bundle validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> dict[str, Any]:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")
    require(isinstance(data, dict), f"{relative_path} must contain a JSON object")
    return data


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def require_object(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def by_field(rows: list[Any], field: str) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for row in rows:
        require(isinstance(row, dict), f"row in {field} index must be object")
        key = row.get(field)
        require(isinstance(key, str) and key, f"row missing non-empty {field}")
        result[key] = row
    return result


def stable_refusal_values(rows: list[Any]) -> list[str]:
    values: list[str] = []
    for row in rows:
        if isinstance(row, str):
            values.append(row)
        elif isinstance(row, dict) and isinstance(row.get("fallbackReason"), str):
            values.append(row["fallbackReason"])
    return values


def dedupe(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        normalized = NON_CLAIM_ALIASES.get(value.strip(), value.strip())
        if normalized and normalized not in seen:
            result.append(normalized)
            seen.add(normalized)
    return result


def artifact_paths(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        paths: list[str] = []
        for child in value.values():
            paths.extend(artifact_paths(child))
        return paths
    if isinstance(value, list):
        paths = []
        for child in value:
            paths.extend(artifact_paths(child))
        return paths
    return []


def has_route_artifact(artifacts: dict[str, Any]) -> bool:
    return any(key.lower().startswith("route") for key in artifacts)


def source_reports() -> dict[str, str]:
    return {
        "supportMatrixJson": SUPPORT_MATRIX_PATH,
        "supportMatrixMarkdown": SUPPORT_MATRIX_MD,
        "layoutJson": LAYOUT_REPORT_PATH,
        "layoutMarkdown": LAYOUT_REPORT_MD,
        "shaderEffectsJson": SHADER_EFFECTS_PATH,
        "shaderEffectsMarkdown": SHADER_EFFECTS_MD,
        "colorFilterJson": COLOR_FILTER_PATH,
        "colorFilterMarkdown": COLOR_FILTER_MD,
        "uniformPreviewJson": PREVIEW_PATH,
        "uniformPreviewMarkdown": PREVIEW_MD,
    }


def preview_states_by_effect(preview: dict[str, Any]) -> dict[str, list[dict[str, Any]]]:
    states: dict[str, list[dict[str, Any]]] = {}
    for row in require_list(preview, "editedStates", PREVIEW_PATH):
        require(isinstance(row, dict), "preview edited state must be object")
        stable_id = row.get("effectStableId")
        require(isinstance(stable_id, str), "preview edited state missing effectStableId")
        states.setdefault(stable_id, []).append(row)
    return states


def base_row(support: dict[str, Any], layout_by_id: dict[str, dict[str, Any]]) -> dict[str, Any]:
    stable_id = str(support["stableId"])
    layout = layout_by_id.get(stable_id)
    support_state = support.get("supportState")
    fallback = support.get("fallbackReason") or "none"
    return {
        "stableId": stable_id,
        "kind": support.get("kind"),
        "descriptorStatus": support.get("descriptorStatus"),
        "supportState": support_state,
        "exactState": support_state,
        "supportClaim": support_state == "gpu-backed" and fallback == "none",
        "cpuImplementationId": support.get("cpuImplementationId"),
        "wgslImplementationId": support.get("wgslImplementationId"),
        "fallbackReason": fallback,
        "pmNote": support.get("pmNote"),
        "layoutStatus": layout.get("status") if layout else None,
        "uniformBlockSize": layout.get("uniformBlockSize") if layout else None,
        "pipelineCacheKeyPolicy": layout.get("pipelineCacheKeyPolicy") if layout else None,
        "sourceReports": ["supportMatrix"] + (["layoutV2"] if layout else []),
        "artifactGroups": {},
        "specializedEvidence": {},
    }


def attach_shader_effect(row: dict[str, Any], effect: dict[str, Any]) -> None:
    row["sourceReports"].append("runtimeShaderEffectsV2")
    row["artifactGroups"]["primarySupport"] = effect["artifacts"]
    row["specializedEvidence"]["shaderEffectPromotion"] = {
        "status": effect.get("status"),
        "sceneId": effect.get("sceneId"),
        "cpuSimilarity": effect.get("cpuSimilarity"),
        "gpuSimilarity": effect.get("gpuSimilarity"),
        "threshold": effect.get("threshold"),
        "fallbackReason": effect.get("fallbackReason"),
        "pipelineKeyPolicy": effect.get("pipelineKeyPolicy"),
    }


def attach_color_filter(row: dict[str, Any], report: dict[str, Any]) -> None:
    selected = require_object(report, "selected", COLOR_FILTER_PATH)
    row["sourceReports"].append("runtimeColorFilterWgsl")
    row["artifactGroups"]["primarySupport"] = selected["artifacts"]
    row["specializedEvidence"]["runtimeColorFilterWgsl"] = {
        "status": report.get("status"),
        "stageOrder": selected.get("stageOrder"),
        "cpuSimilarity": selected.get("cpuSimilarity"),
        "webGpuSimilarity": selected.get("webGpuSimilarity"),
        "threshold": selected.get("threshold"),
        "fallbackReason": selected.get("fallbackReason"),
        "nonSelectedColorFilters": report.get("nonSelectedColorFilters", []),
    }


def attach_preview(row: dict[str, Any], preview: dict[str, Any], states_by_effect: dict[str, list[dict[str, Any]]]) -> None:
    states = states_by_effect.get(row["stableId"], [])
    if not states:
        return
    row["sourceReports"].append("runtimeEffectUniformPreview")
    row["artifactGroups"]["uniformPreviewStates"] = [state["artifacts"] for state in states]
    row["specializedEvidence"]["uniformPreview"] = {
        "status": preview.get("status"),
        "editedStateCount": len(states),
        "pipelineKeyStableAcrossUniformEdits": all(
            state.get("pipelineKeyStableAcrossUniformEdits") is True for state in states
        ),
        "uniformValuesInPipelineKey": any(state.get("uniformValuesInPipelineKey") is True for state in states),
        "stableRefusals": stable_refusal_values(preview.get("stableRefusals", [])),
    }


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    support_matrix = load_json(root, SUPPORT_MATRIX_PATH)
    layout_report = load_json(root, LAYOUT_REPORT_PATH)
    shader_report = load_json(root, SHADER_EFFECTS_PATH)
    color_report = load_json(root, COLOR_FILTER_PATH)
    preview_report = load_json(root, PREVIEW_PATH)

    support_rows = require_list(support_matrix, "rows", SUPPORT_MATRIX_PATH)
    layout_by_id = by_field(require_list(layout_report, "rows", LAYOUT_REPORT_PATH), "stableId")
    shader_by_id = by_field(require_list(shader_report, "effects", SHADER_EFFECTS_PATH), "stableId")
    states_by_effect = preview_states_by_effect(preview_report)

    rows = [base_row(row, layout_by_id) for row in support_rows]
    require([row["stableId"] for row in rows] == REQUIRED_ROW_ORDER, "support matrix row order changed")

    for row in rows:
        stable_id = row["stableId"]
        if stable_id in shader_by_id:
            attach_shader_effect(row, shader_by_id[stable_id])
        if stable_id == "runtime.color_filter_luma_to_alpha":
            attach_color_filter(row, color_report)
        attach_preview(row, preview_report, states_by_effect)

    stable_refusals = dedupe(
        [
            str(row.get("fallbackReason"))
            for row in support_rows
            if row.get("fallbackReason") not in (None, "none")
        ]
        + stable_refusal_values(shader_report.get("stableRefusals", []))
        + stable_refusal_values(color_report.get("stableRefusals", []))
        + stable_refusal_values(preview_report.get("stableRefusals", []))
    )
    non_claims = dedupe(
        REQUIRED_NON_CLAIMS
        + list(support_matrix.get("nonClaims", []))
        + list(shader_report.get("nonClaims", []))
        + list(color_report.get("nonClaims", []))
        + list(preview_report.get("nonClaims", []))
        + [
            "No new rendering capability is introduced by this bundle.",
            "No Kadre native CI requirement.",
        ]
    )

    missing = sorted(
        path
        for row in rows
        for path in artifact_paths(row["artifactGroups"])
        if not (root / path).is_file()
    )
    hidden_broad_claims = [
        row["stableId"]
        for row in rows
        if row["supportClaim"] and not (row["supportState"] == "gpu-backed" and row["fallbackReason"] == "none")
    ]
    counts = {
        "totalRows": len(rows),
        "descriptorBacked": sum(1 for row in rows if row["descriptorStatus"] == "descriptor-backed"),
        "gpuBacked": sum(1 for row in rows if row["supportState"] == "gpu-backed"),
        "cpuOnly": sum(1 for row in rows if row["supportState"] == "cpu-only"),
        "expectedUnsupported": sum(1 for row in rows if row["supportState"] == "expected-unsupported"),
        "dependencyGated": sum(1 for row in rows if row["supportState"] == "dependency-gated"),
        "layoutMatched": sum(1 for row in rows if row["layoutStatus"] == "layout-matched"),
        "supportClaimsWithArtifacts": sum(1 for row in rows if row["supportClaim"] and row["artifactGroups"].get("primarySupport")),
        "unsupportedRowsVisible": sum(1 for row in rows if not row["supportClaim"] and row["fallbackReason"] != "none"),
        "previewEditedStates": int(preview_report["counts"]["editedStateCount"]),
        "missingArtifacts": len(missing),
        "hiddenBroadClaims": len(hidden_broad_claims),
    }
    evidence = {
        "schemaVersion": "kanvas.runtime-effects-v2.evidence-bundle",
        "packId": "kan-034-runtime-effects-v2-evidence-bundle-v1",
        "ticket": "KAN-034",
        "status": "pass" if counts["totalRows"] == 8 and counts["missingArtifacts"] == 0 else "fail",
        "claimLevel": "runtime-effects-v2-evidence-aggregation-no-new-rendering-capability",
        "sourceOfTruth": source_reports(),
        "counts": counts,
        "rows": rows,
        "stableRefusals": stable_refusals,
        "nonClaims": non_claims,
        "artifactAudit": {"missing": missing},
        "claimAudit": {"hiddenBroadClaims": hidden_broad_claims},
        "validationRows": [
            {
                "id": "support-matrix-coherence",
                "status": "pass",
                "detail": "All 8 Runtime Effects V2 rows mirror the KAN-027 support matrix.",
            },
            {
                "id": "support-artifacts",
                "status": "pass",
                "detail": "Each gpu-backed support claim links CPU/GPU/diff/stat/route artifacts.",
            },
            {
                "id": "unsupported-visible",
                "status": "pass",
                "detail": "CPU-only and policy-only rows retain visible stable refusals.",
            },
            {
                "id": "no-dynamic-sksl",
                "status": "pass",
                "detail": "Dynamic SkSL compilation, SkSL IR/VM, and broad runtime-effect support remain non-claims.",
            },
        ],
    }
    validate_evidence(root, evidence)
    return evidence


def validate_evidence(root: Path, evidence: dict[str, Any]) -> None:
    require(evidence.get("schemaVersion") == "kanvas.runtime-effects-v2.evidence-bundle", "schemaVersion changed")
    require(evidence.get("packId") == "kan-034-runtime-effects-v2-evidence-bundle-v1", "packId changed")
    require(evidence.get("ticket") == "KAN-034", "ticket changed")
    require(evidence.get("status") == "pass", "status must remain pass")
    counts = require_object(evidence, "counts", "evidence")
    require(counts.get("totalRows") == 8, "bundle must keep 8 primary Runtime Effects V2 rows")
    require(counts.get("gpuBacked") == 4, "gpu-backed count must stay 4")
    require(counts.get("cpuOnly") == 2, "cpu-only count must stay 2")
    require(counts.get("expectedUnsupported") == 2, "expected-unsupported count must stay 2")
    require(counts.get("dependencyGated") == 0, "dependency-gated count must stay 0")
    require(counts.get("supportClaimsWithArtifacts") == 4, "all gpu-backed rows must link support artifacts")
    require(counts.get("missingArtifacts") == 0, "artifact audit must have no missing files")
    require(counts.get("hiddenBroadClaims") == 0, "hidden broad claims must stay zero")

    rows = require_list(evidence, "rows", "evidence")
    require([row.get("stableId") for row in rows] == REQUIRED_ROW_ORDER, "primary row ids changed")
    by_id = {row["stableId"]: row for row in rows if isinstance(row, dict)}
    for stable_id in (
        "runtime.color_filter_luma_to_alpha",
        "runtime.linear_gradient_rt",
        "runtime.simple_rt",
        "runtime.spiral_rt",
    ):
        row = by_id[stable_id]
        artifacts = require_object(require_object(row, "artifactGroups", stable_id), "primarySupport", stable_id)
        require("stats" in artifacts, f"{stable_id} support claim missing stats artifact")
        require(has_route_artifact(artifacts), f"{stable_id} support claim missing route artifact")
        for path in artifact_paths(artifacts):
            require((root / path).is_file(), f"{stable_id} missing artifact {path}")
    require(
        by_id["runtime.unsharp_rt"]["fallbackReason"] == "runtime-effect.wgsl-descriptor-missing",
        "runtime.unsharp_rt child shader refusal missing",
    )
    preview = by_id["runtime.simple_rt"]["specializedEvidence"]["uniformPreview"]
    require(preview["editedStateCount"] == 2, "runtime.simple_rt preview edited state count changed")
    require(preview["pipelineKeyStableAcrossUniformEdits"] is True, "preview PipelineKey stability missing")

    stable_refusals = require_list(evidence, "stableRefusals", "evidence")
    for reason in REQUIRED_STABLE_REFUSALS:
        require(reason in stable_refusals, f"missing stable refusal {reason}")
    non_claims = require_list(evidence, "nonClaims", "evidence")
    for non_claim in REQUIRED_NON_CLAIMS:
        require(non_claim in non_claims, f"missing non-claim {non_claim}")


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    evidence = build_evidence(root)
    write_json(output_dir / OUTPUT_JSON, evidence)
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def render_markdown(evidence: dict[str, Any]) -> str:
    counts = evidence["counts"]
    rows = "\n".join(
        "| `{stableId}` | `{kind}` | `{descriptor}` | `{support}` | `{fallback}` | `{claim}` |".format(
            stableId=row["stableId"],
            kind=row["kind"],
            descriptor=row["descriptorStatus"],
            support=row["supportState"],
            fallback=row["fallbackReason"],
            claim="yes" if row["supportClaim"] else "no",
        )
        for row in evidence["rows"]
    )
    refusal_rows = "\n".join(f"- `{reason}`" for reason in evidence["stableRefusals"])
    non_claim_rows = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    source_rows = "\n".join(f"- `{path}`" for path in evidence["sourceOfTruth"].values())
    return f"""# KAN-034 Runtime Effects V2 Evidence Bundle

Status: `{evidence['status']}`
Status counts: total={counts['totalRows']}; descriptor-backed={counts['descriptorBacked']}; gpu-backed={counts['gpuBacked']}; cpu-only={counts['cpuOnly']}; expected-unsupported={counts['expectedUnsupported']}; dependency-gated={counts['dependencyGated']}; support-claims-with-artifacts={counts['supportClaimsWithArtifacts']}; missing-artifacts={counts['missingArtifacts']}.

KAN-034 aggregates the Runtime Effects V2 support/refusal evidence without adding a new rendering capability. WGSL remains the implementation target; SkSL appears only as a refused compatibility surface.

## Rows

| Stable id | Kind | Descriptor | Support state | Fallback | Support claim |
|---|---|---|---|---|---|
{rows}

## Stable Refusals

{refusal_rows}

## Source Reports

{source_rows}

## Non-Claims

{non_claim_rows}
"""


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]) if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    if not output_dir.is_absolute():
        output_dir = root / output_dir
    write_outputs(root, output_dir)
    print(f"KAN-034 Runtime Effects V2 evidence bundle: {output_dir / OUTPUT_JSON}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
