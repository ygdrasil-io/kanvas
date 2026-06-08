#!/usr/bin/env python3
"""Generate and validate the M89 GM registry PM closeout."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
REGISTRY_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/registry.json"
CLOSEOUT_JSON = ROOT / "reports/wgsl-pipeline/m89-gm-registry/closeout.json"
CLOSEOUT_MD = ROOT / "reports/wgsl-pipeline/m89-gm-registry/closeout.md"

EXPECTED_COUNTERS = {
    "totalRows": 47,
    "supportClaims": 22,
    "policyOnlyRows": 20,
    "rowSpecificRefusalRows": 7,
    "dependencyGateLinkRows": 4,
    "groupedPolicyRefusalRows": 9,
    "edgeBudgetGateLinkRows": 2,
    "imageFilterPrepassGateLinkRows": 1,
    "textGlyphDependencyGateLinkRows": 2,
    "unlinkedUnsupportedRows": 0,
    "expectedUnsupportedWithFallback": 25,
    "linkedM66Rows": 18,
    "linkedM86Rows": 18,
}
EXPECTED_STATUS = {
    "expected-unsupported": 25,
    "pass": 22,
}
EXPECTED_FAMILY = {
    "bitmap-image": 7,
    "blend-color": 2,
    "gradient": 4,
    "image-filter": 5,
    "path-aa": 18,
    "runtime-effect": 3,
    "text-glyph": 7,
    "transform-layer": 1,
}
SPECIALIZED_LINK_FIELDS = [
    "rowSpecificRefusals",
    "dependencyGateLinks",
    "groupedPolicyRefusals",
    "edgeBudgetGateLinks",
    "imageFilterPrepassGateLinks",
    "textGlyphDependencyGateLinks",
]
NEXT_SLICES = [
    {
        "milestone": "M90",
        "family": "path-aa",
        "scope": "Path AA, strokes, dash, hairline, and clip backlog wave.",
        "nextAction": "Promote bounded support only with Skia reference and GPU evidence, or add explicit refusal proofs for unsupported clusters.",
        "supportClaimAllowedFromCloseout": False,
    },
    {
        "milestone": "M91",
        "family": "image-filter",
        "scope": "Image-filter DAG and layer/intermediate ownership wave.",
        "nextAction": "Require graph dump, texture ownership, CPU/GPU/reference/diff evidence before any bounded DAG promotion.",
        "supportClaimAllowedFromCloseout": False,
    },
    {
        "milestone": "M92",
        "family": "text-glyph",
        "scope": "Text/glyph dependency-gated production slice.",
        "nextAction": "Keep complex shaping, emoji/color glyphs, fallback stacks, and text GM rows dependency-gated until real implementations land.",
        "supportClaimAllowedFromCloseout": False,
    },
]


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} root must be an object")
    return data


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def linked_unsupported_rows(registry: dict[str, Any]) -> int:
    rows = registry.get("rows")
    require(isinstance(rows, list), "registry rows must be a list")
    linked = 0
    for row in rows:
        require(isinstance(row, dict), "registry rows must be objects")
        if row.get("status") == "pass":
            continue
        has_specialized_link = any(bool(row.get(field)) for field in SPECIALIZED_LINK_FIELDS)
        if has_specialized_link:
            linked += 1
    return linked


def build_closeout(registry: dict[str, Any]) -> dict[str, Any]:
    counters = registry.get("counters")
    evidence = registry.get("evidencePackages")
    require(isinstance(counters, dict), "registry counters must be an object")
    require(isinstance(evidence, dict), "registry evidencePackages must be an object")
    m86 = evidence.get("m86")
    m88 = evidence.get("m88")
    require(isinstance(m86, dict), "M86 evidence package must be an object")
    require(isinstance(m88, dict), "M88 evidence package must be an object")

    for key, expected in EXPECTED_COUNTERS.items():
        require(counters.get(key) == expected, f"registry counter changed: {key}")
    require(counters.get("status") == EXPECTED_STATUS, "registry status counters changed")
    require(counters.get("family") == EXPECTED_FAMILY, "registry family counters changed")
    require(linked_unsupported_rows(registry) == 25, "all 25 unsupported rows must retain specialized visibility links")
    require(m86.get("globalThresholdWeakened") is False, "M86 threshold guard changed")
    require(m88.get("failRows") == 0, "M88 failRows changed")
    require(m88.get("trackedGapRows") == 0, "M88 trackedGapRows changed")

    return {
        "schemaVersion": 1,
        "generatedBy": "scripts/m89_registry_closeout.py",
        "classification": "pm-closeout-no-new-rendering-support",
        "status": "generated evidence",
        "milestone": "M89",
        "scope": "GM registry normalization closeout and next-slice handoff.",
        "registry": {
            "json": "reports/wgsl-pipeline/m89-gm-registry/registry.json",
            "report": "reports/wgsl-pipeline/m89-gm-registry/registry.md",
            "totalRows": counters["totalRows"],
            "supportClaims": counters["supportClaims"],
            "expectedUnsupportedWithFallback": counters["expectedUnsupportedWithFallback"],
            "policyOnlyRows": counters["policyOnlyRows"],
            "unlinkedUnsupportedRows": counters["unlinkedUnsupportedRows"],
            "statusCounts": counters["status"],
            "familyCounts": counters["family"],
        },
        "readiness": {
            "readinessBefore": 67.75,
            "readinessAfter": 67.75,
            "readinessDelta": 0.0,
            "reason": "M89 closes visibility and PM handoff only; it does not add row-specific pass evidence, move denominators, or change release gates.",
        },
        "supportGuard": {
            "supportClaimsChanged": False,
            "renderPathsChanged": False,
            "thresholdsChanged": False,
            "globalThresholdWeakened": False,
            "policyOnlyRowsPromoted": False,
            "belowThresholdCountedAsProductionGap": False,
            "unexpectedFailRows": m88["failRows"],
            "trackedGapRows": m88["trackedGapRows"],
        },
        "unsupportedVisibility": {
            "expectedUnsupportedRows": counters["expectedUnsupportedWithFallback"],
            "linkedUnsupportedRows": linked_unsupported_rows(registry),
            "unlinkedUnsupportedRows": counters["unlinkedUnsupportedRows"],
            "rowSpecificRefusalRows": counters["rowSpecificRefusalRows"],
            "dependencyGateLinkRows": counters["dependencyGateLinkRows"],
            "groupedPolicyRefusalRows": counters["groupedPolicyRefusalRows"],
            "edgeBudgetGateLinkRows": counters["edgeBudgetGateLinkRows"],
            "imageFilterPrepassGateLinkRows": counters["imageFilterPrepassGateLinkRows"],
            "textGlyphDependencyGateLinkRows": counters["textGlyphDependencyGateLinkRows"],
        },
        "nextRecommendedSlices": NEXT_SLICES,
        "nonClaims": {
            "ganeshPort": False,
            "graphitePort": False,
            "dynamicSkSLCompiler": False,
            "dynamicSkSLIR": False,
            "dynamicSkSLVM": False,
            "broadSkiaParity": False,
            "nativeKadreRequiredForHeadlessValidation": False,
            "broadPathAASupport": False,
            "broadImageFilterDAGSupport": False,
            "broadTextGlyphSupport": False,
        },
        "validationCommands": [
            "rtk python3 scripts/m89_gm_registry.py",
            "rtk python3 scripts/validate_m89_gm_registry.py",
            "rtk python3 scripts/m89_registry_closeout.py",
            "rtk ./gradlew --no-daemon pipelineM89GmRegistry validateM89GmRegistry pipelineM89RegistryCloseout pipelinePmBundle",
        ],
    }


def render_markdown(closeout: dict[str, Any]) -> str:
    registry = closeout["registry"]
    readiness = closeout["readiness"]
    guard = closeout["supportGuard"]
    visibility = closeout["unsupportedVisibility"]
    lines = [
        "# M89 GM Registry Closeout",
        "",
        "Status: generated evidence",
        "",
        "This closeout freezes the M89 registry visibility contract and hands off the next backlog slices. It does not promote rendering support, weaken thresholds, or change render paths.",
        "",
        "## Registry Counters",
        "",
        f"- Total rows: `{registry['totalRows']}`",
        f"- Support claims: `{registry['supportClaims']}`",
        f"- Expected unsupported with fallback: `{registry['expectedUnsupportedWithFallback']}`",
        f"- Policy-only rows: `{registry['policyOnlyRows']}`",
        f"- Unlinked unsupported rows: `{registry['unlinkedUnsupportedRows']}`",
        "",
        "## Unsupported Visibility",
        "",
        f"- Linked unsupported rows: `{visibility['linkedUnsupportedRows']}`",
        f"- Row-specific refusal rows: `{visibility['rowSpecificRefusalRows']}`",
        f"- Dependency gate link rows: `{visibility['dependencyGateLinkRows']}`",
        f"- Grouped policy refusal rows: `{visibility['groupedPolicyRefusalRows']}`",
        f"- Edge-budget gate link rows: `{visibility['edgeBudgetGateLinkRows']}`",
        f"- Image-filter prepass gate link rows: `{visibility['imageFilterPrepassGateLinkRows']}`",
        f"- Text/glyph dependency gate link rows: `{visibility['textGlyphDependencyGateLinkRows']}`",
        "",
        "## Readiness",
        "",
        f"- Before: `{readiness['readinessBefore']}`",
        f"- After: `{readiness['readinessAfter']}`",
        f"- Delta: `{readiness['readinessDelta']}`",
        f"- Reason: {readiness['reason']}",
        "",
        "## Support Guard",
        "",
    ]
    lines.extend(f"- {key}: `{value}`" for key, value in guard.items())
    lines.extend(
        [
            "",
            "## Next Recommended Slices",
            "",
        ]
    )
    for item in closeout["nextRecommendedSlices"]:
        lines.extend(
            [
                f"### {item['milestone']} - {item['family']}",
                "",
                f"- Scope: {item['scope']}",
                f"- Next action: {item['nextAction']}",
                f"- Support claim allowed from this closeout: `{item['supportClaimAllowedFromCloseout']}`",
                "",
            ]
        )
    lines.extend(
        [
            "## Non-Claims",
            "",
        ]
    )
    lines.extend(f"- {key}: `{value}`" for key, value in closeout["nonClaims"].items())
    lines.extend(
        [
            "",
            "## Validation Commands",
            "",
        ]
    )
    lines.extend(f"- `{command}`" for command in closeout["validationCommands"])
    return "\n".join(lines) + "\n"


def validate_closeout(closeout: dict[str, Any]) -> None:
    require(closeout.get("classification") == "pm-closeout-no-new-rendering-support", "classification mismatch")
    readiness = closeout.get("readiness")
    guard = closeout.get("supportGuard")
    visibility = closeout.get("unsupportedVisibility")
    non_claims = closeout.get("nonClaims")
    next_slices = closeout.get("nextRecommendedSlices")
    require(isinstance(readiness, dict), "readiness must be an object")
    require(readiness.get("readinessDelta") == 0.0, "readiness delta must stay zero")
    require(readiness.get("readinessBefore") == readiness.get("readinessAfter") == 67.75, "readiness must stay 67.75")
    require(isinstance(guard, dict), "supportGuard must be an object")
    require(guard.get("supportClaimsChanged") is False, "support claims changed")
    require(guard.get("renderPathsChanged") is False, "render paths changed")
    require(guard.get("thresholdsChanged") is False, "thresholds changed")
    require(guard.get("policyOnlyRowsPromoted") is False, "policy-only rows promoted")
    require(guard.get("belowThresholdCountedAsProductionGap") is False, "below-threshold rows counted as production gap")
    require(guard.get("unexpectedFailRows") == 0, "unexpected fail rows changed")
    require(guard.get("trackedGapRows") == 0, "tracked-gap rows changed")
    require(isinstance(visibility, dict), "unsupportedVisibility must be an object")
    require(visibility.get("expectedUnsupportedRows") == 25, "expected unsupported count changed")
    require(visibility.get("linkedUnsupportedRows") == 25, "linked unsupported count changed")
    require(visibility.get("unlinkedUnsupportedRows") == 0, "unlinked unsupported rows changed")
    require(isinstance(non_claims, dict), "nonClaims must be an object")
    for key, value in non_claims.items():
        require(value is False, f"non-claim changed: {key}")
    require(isinstance(next_slices, list) and len(next_slices) == 3, "nextRecommendedSlices must list M90/M91/M92")
    require([item.get("milestone") for item in next_slices] == ["M90", "M91", "M92"], "next slice order mismatch")
    for item in next_slices:
        require(item.get("supportClaimAllowedFromCloseout") is False, f"{item.get('milestone')}: closeout cannot authorize support")


def main() -> int:
    try:
        registry = load_json(REGISTRY_JSON)
        closeout = build_closeout(registry)
        validate_closeout(closeout)
        write_json(CLOSEOUT_JSON, closeout)
        CLOSEOUT_MD.write_text(render_markdown(closeout), encoding="utf-8")
        validate_closeout(load_json(CLOSEOUT_JSON))
    except AssertionError as error:
        print(f"m89_registry_closeout: FAIL: {error}", file=sys.stderr)
        return 1
    print("M89 registry closeout validation passed: readinessDelta=0.0 supportClaims=22 unlinkedUnsupportedRows=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
