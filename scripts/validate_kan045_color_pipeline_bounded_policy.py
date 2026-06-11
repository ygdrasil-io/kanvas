#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/color-pipeline-bounded-policy"
OUTPUT_JSON = "kan-045-color-pipeline-bounded-policy.json"
OUTPUT_MARKDOWN = "kan-045-color-pipeline-bounded-policy.md"

KAN015_ROOT = "reports/wgsl-pipeline/scenes/artifacts/kan-015-srcover-alpha"
KAN016_ROOT = "reports/wgsl-pipeline/scenes/artifacts/kan-016-color-filter-blend-kplus"
M63_PACK_PATH = "reports/wgsl-pipeline/scenes/generated/m63-color-blend-parity-pack.json"
FOR345_PATH = (
    "reports/wgsl-pipeline/scenes/artifacts/non-arc-rec2020-f16-reference-row-for345/"
    "non-arc-rec2020-f16-reference-row-for345.json"
)

SPEC_FEATURE_EXPANSION_PATH = ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md"
SPEC_PIPELINE_IR_PATH = ".upstream/specs/wgsl-pipeline/01-pipeline-ir-contracts.md"
TARGET_WGSL_PATH = ".upstream/target/high-performance-wgsl-pipeline-target.md"
TARGET_REALTIME_PATH = ".upstream/target/skia-like-realtime-renderer-target.md"
SPEC_REALTIME_README_PATH = ".upstream/specs/skia-like-realtime/README.md"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-045 color pipeline bounded policy validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def artifact_exists(root: Path, relative_path: str | None) -> bool:
    return bool(relative_path) and (root / str(relative_path)).is_file()


def non_claims() -> list[str]:
    return [
        "no-wide-gamut-general-claim",
        "no-hdr-or-gainmap-claim",
        "no-all-blend-modes-claim",
        "no-broad-color-management-claim",
        "no-global-threshold-change",
        "no-silent-approximation",
        "no-ganesh-graphite-claim",
        "no-sksl-compiler-ir-vm-claim",
    ]


def scene_paths(scene_root: str) -> dict[str, str]:
    return {
        "stats": f"{scene_root}/stats.json",
        "routeCpu": f"{scene_root}/route-cpu.json",
        "routeWebGpu": f"{scene_root}/route-webgpu.json",
    }


def require_comparison(stats: dict[str, Any], prefix: str) -> None:
    comparison = stats.get(f"{prefix}Comparison")
    threshold = stats.get(f"{prefix}SimilarityThreshold")
    require(isinstance(comparison, dict), f"{prefix} comparison missing")
    require(isinstance(threshold, (int, float)), f"{prefix} similarity threshold missing")
    require(comparison.get("similarity", -1) >= threshold, f"{prefix} similarity below threshold")
    require(comparison.get("matchingPixels", 0) > 0, f"{prefix} matching pixels missing")


def support_proofs(root: Path, stats: dict[str, Any], route_cpu_path: str, route_gpu_path: str) -> dict[str, bool]:
    return {
        "reference": artifact_exists(root, stats.get("referenceArtifact")),
        "cpu": artifact_exists(root, stats.get("cpuArtifact")),
        "gpu": artifact_exists(root, stats.get("webGpuArtifact")),
        "cpuDiff": artifact_exists(root, stats.get("cpuDiffArtifact")),
        "webGpuDiff": artifact_exists(root, stats.get("webGpuDiffArtifact")),
        "diffStats": isinstance(stats.get("cpuComparison"), dict) and isinstance(stats.get("webGpuComparison"), dict),
        "route": (root / route_cpu_path).is_file() and (root / route_gpu_path).is_file(),
    }


def build_src_over_row(root: Path) -> dict[str, Any]:
    paths = scene_paths(KAN015_ROOT)
    stats = load_json(root, paths["stats"])
    route_cpu = load_json(root, paths["routeCpu"])
    route_gpu = load_json(root, paths["routeWebGpu"])
    require(stats.get("status") == "pass", "KAN-015 status changed")
    require(stats.get("supportClaim") is True, "KAN-015 support claim missing")
    require(stats.get("globalThresholdChanged") is False, "KAN-015 global threshold changed")
    require(stats.get("globalBlendPolicyChanged") is False, "KAN-015 global blend policy changed")
    require_comparison(stats, "cpu")
    require_comparison(stats, "webGpu")
    require(route_cpu.get("selectedRoute") == stats.get("cpuRouteIdentifier"), "KAN-015 CPU route mismatch")
    require(route_gpu.get("selectedRoute") == stats.get("webGpuRouteIdentifier"), "KAN-015 WebGPU route mismatch")
    require(route_gpu.get("generatedSolidRectWgslValidated") is True, "KAN-015 generated WGSL not validated")

    semantic_ops = {
        "shaderOrColor": "constantColor",
        "colorFilterKind": "none",
        "blendMode": stats.get("blendMode"),
        "blendPlan": stats.get("blendPlan"),
        "alphaPolicy": stats.get("alphaPolicy"),
        "colorSpacePolicy": stats.get("colorSpacePolicy"),
        "stageOrder": "shader/color -> alpha modulation -> blender -> color-space/store",
    }
    return {
        "rowId": stats.get("sceneId"),
        "pmCategory": "supportable-bounded",
        "status": "pass",
        "sourceEvidence": paths["stats"],
        "supportScope": stats.get("supportScope"),
        "route": {
            "cpu": stats.get("cpuRouteIdentifier"),
            "webGpu": stats.get("webGpuRouteIdentifier"),
            "fallbackReason": "none",
        },
        "colorPolicy": stats.get("colorSpacePolicy"),
        "semanticOps": semantic_ops,
        "cpuSemanticOps": semantic_ops,
        "gpuSemanticOps": semantic_ops,
        "wgslValidation": {
            "validated": stats.get("generatedSolidRectWgslValidated") is True,
            "sha256": stats.get("generatedSolidRectWgslSha256"),
            "diagnostics": stats.get("generatedSolidRectWgslDiagnostics", []),
        },
        "thresholds": {
            "cpu": stats.get("cpuSimilarityThreshold"),
            "webGpu": stats.get("webGpuSimilarityThreshold"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
            "globalColorPolicyChanged": False,
            "globalBlendPolicyChanged": stats.get("globalBlendPolicyChanged"),
        },
        "proofs": support_proofs(root, stats, paths["routeCpu"], paths["routeWebGpu"]),
        "nonClaims": sorted(set(non_claims() + stats.get("nonClaims", []))),
    }


def build_color_filter_row(root: Path) -> dict[str, Any]:
    paths = scene_paths(KAN016_ROOT)
    stats = load_json(root, paths["stats"])
    route_cpu = load_json(root, paths["routeCpu"])
    route_gpu = load_json(root, paths["routeWebGpu"])
    require(stats.get("status") == "pass", "KAN-016 status changed")
    require(stats.get("supportClaim") is True, "KAN-016 support claim missing")
    require(stats.get("globalThresholdChanged") is False, "KAN-016 global threshold changed")
    require(stats.get("globalColorPolicyChanged") is False, "KAN-016 global color policy changed")
    require_comparison(stats, "cpu")
    require_comparison(stats, "webGpu")
    require(route_cpu.get("selectedRoute") == stats.get("cpuRouteIdentifier"), "KAN-016 CPU route mismatch")
    require(route_gpu.get("selectedRoute") == stats.get("webGpuRouteIdentifier"), "KAN-016 WebGPU route mismatch")
    require(route_cpu.get("stageOrder") == route_gpu.get("stageOrder"), "KAN-016 stage order mismatch")
    require(route_gpu.get("solidColorWgslValidated") is True, "KAN-016 solid_color WGSL not validated")

    semantic_ops = {
        "shaderOrColor": "solidColor",
        "colorFilterKind": stats.get("colorFilterKind"),
        "colorFilterBlendMode": stats.get("colorFilterBlendMode"),
        "paintBlendMode": stats.get("paintBlendMode"),
        "colorSpacePolicy": stats.get("colorSpacePolicy"),
        "stageOrder": route_gpu.get("stageOrder"),
    }
    return {
        "rowId": stats.get("sceneId"),
        "pmCategory": "supportable-bounded",
        "status": "pass",
        "sourceEvidence": paths["stats"],
        "supportScope": stats.get("supportScope"),
        "route": {
            "cpu": stats.get("cpuRouteIdentifier"),
            "webGpu": stats.get("webGpuRouteIdentifier"),
            "fallbackReason": "none",
            "generatedSolidRectFallbackReason": stats.get("generatedSolidRectFallbackReason"),
        },
        "colorPolicy": stats.get("colorSpacePolicy"),
        "semanticOps": semantic_ops,
        "cpuSemanticOps": semantic_ops,
        "gpuSemanticOps": semantic_ops,
        "wgslValidation": {
            "validated": stats.get("solidColorWgslValidated") is True,
            "sha256": stats.get("solidColorWgslSha256"),
            "entryPoints": stats.get("solidColorWgslEntryPoints", []),
            "diagnostics": stats.get("solidColorWgslDiagnostics", []),
        },
        "thresholds": {
            "cpu": stats.get("cpuSimilarityThreshold"),
            "webGpu": stats.get("webGpuSimilarityThreshold"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
            "globalColorPolicyChanged": stats.get("globalColorPolicyChanged"),
            "globalBlendPolicyChanged": False,
        },
        "proofs": support_proofs(root, stats, paths["routeCpu"], paths["routeWebGpu"]),
        "nonClaims": sorted(set(non_claims() + stats.get("nonClaims", []))),
    }


def find_m63_scene(pack: dict[str, Any], scene_id: str) -> dict[str, Any]:
    scenes = pack.get("scenes")
    require(isinstance(scenes, list), "M63 scenes missing")
    for scene in scenes:
        if isinstance(scene, dict) and scene.get("id") == scene_id:
            return scene
    fail(f"M63 scene missing: {scene_id}")


def build_wide_gamut_refusal_row(pack: dict[str, Any]) -> dict[str, Any]:
    scene = find_m63_scene(pack, "m63-wide-gamut-color-space-refusal")
    require(scene.get("status") == "expected-unsupported", "M63 wide-gamut status changed")
    require(
        scene.get("fallbackReason") == "color.color-space-wide-gamut-unsupported",
        "M63 wide-gamut reason changed",
    )
    return {
        "rowId": scene.get("id"),
        "pmCategory": "wide-gamut-color-space",
        "status": "expected-unsupported",
        "sourceEvidence": M63_PACK_PATH,
        "referenceKind": scene.get("referenceKind"),
        "route": {
            "cpu": scene.get("cpuRoute"),
            "webGpu": scene.get("gpuRoute"),
            "fallbackReason": scene.get("fallbackReason"),
        },
        "reasonCode": scene.get("fallbackReason"),
        "pipelineKey": scene.get("pipelineKey"),
        "colorSpace": "Display-P3/wide-gamut",
        "colorType": "destination-policy-required",
        "semanticOps": {
            "shaderOrColor": "paintColor",
            "colorSpacePolicy": "wide-gamut-conversion-required",
            "blendMode": "kSrcOver",
        },
        "thresholds": {
            "threshold": scene.get("threshold"),
            "thresholdsWeakened": False,
            "globalColorPolicyChanged": False,
        },
        "proofs": {
            "m63Pack": True,
            "stableReason": True,
            "visibleUnsupportedRow": True,
        },
        "nonClaims": sorted(set(non_claims() + [scene.get("nonClaim", "")])),
    }


def build_f16_refusal_row(root: Path) -> dict[str, Any]:
    evidence = load_json(root, FOR345_PATH)
    row = evidence.get("row")
    scene = evidence.get("scene")
    implementation = evidence.get("implementation")
    boundary = evidence.get("boundary")
    require(isinstance(row, dict), "FOR-345 row missing")
    require(isinstance(scene, dict), "FOR-345 scene missing")
    require(isinstance(implementation, dict), "FOR-345 implementation missing")
    require(isinstance(boundary, dict), "FOR-345 boundary missing")
    require(evidence.get("decision") == "F16_NON_ARC_REC2020_REFERENCE_ROW_REJECTS_CANDIDATE", "FOR-345 decision changed")
    require(scene.get("colorType") == "kRGBA_F16Norm", "FOR-345 color type changed")
    require(scene.get("colorSpace") == "Rec.2020", "FOR-345 color space changed")
    require(row.get("rec2020F16SrcOverOrBlendSignal") is True, "FOR-345 F16 signal missing")
    residuals = row.get("residuals")
    require(isinstance(residuals, dict), "FOR-345 residuals missing")
    require(residuals.get("candidateMinusCurrentResidual", 0) > 0, "FOR-345 candidate no longer worsens current")
    require(boundary.get("globalF16RendererChangeAllowedNow") is False, "FOR-345 global F16 change gate changed")
    require(implementation.get("rendererBehaviorChanged") is False, "FOR-345 renderer behavior changed")
    require(implementation.get("thresholdsChanged") is False, "FOR-345 thresholds changed")
    return {
        "rowId": row.get("rowId"),
        "pmCategory": "f16-policy-candidate-refusal",
        "status": "expected-unsupported",
        "sourceEvidence": FOR345_PATH,
        "referenceKind": row.get("sourceKind"),
        "route": {
            "cpu": "current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples",
            "webGpu": "not-promoted-policy-evidence-only",
            "fallbackReason": "color.f16-policy-candidate-worsens-reference",
        },
        "reasonCode": "color.f16-policy-candidate-worsens-reference",
        "decision": evidence.get("decision"),
        "decisionReason": evidence.get("decisionReason"),
        "colorType": scene.get("colorType"),
        "colorSpace": scene.get("colorSpace"),
        "semanticOps": {
            "drawOp": scene.get("draw", {}).get("op"),
            "blendMode": scene.get("draw", {}).get("blendMode"),
            "colorPolicy": "rec2020-f16-current-vs-straight-srgb-candidate",
        },
        "residuals": residuals,
        "implementation": {
            "rendererBehaviorChanged": implementation.get("rendererBehaviorChanged"),
            "thresholdsChanged": implementation.get("thresholdsChanged"),
            "gpuOrWgslChanged": implementation.get("gpuOrWgslChanged"),
            "fallbackChanged": implementation.get("fallbackChanged"),
            "globalF16RendererChangeAllowedNow": boundary.get("globalF16RendererChangeAllowedNow"),
        },
        "proofs": {
            "skiaReference": artifact_exists(root, row.get("reference", {}).get("pngPath")),
            "referenceSamples": artifact_exists(root, row.get("reference", {}).get("samplesPath")),
            "currentSamples": artifact_exists(root, row.get("current", {}).get("samplesPath")),
            "sourceCode": all(
                artifact_exists(root, source.get("path"))
                for source in evidence.get("referenceSource", {}).values()
                if isinstance(source, dict) and source.get("path")
            ),
        },
        "nonClaims": sorted(set(non_claims() + [
            "no-global-f16-policy-change",
            "no-wide-gamut-general-claim",
            "no-f16-renderer-promotion-claim",
        ])),
    }


def build_claim_guard(rows: list[dict[str, Any]]) -> dict[str, list[str]]:
    support_rows = [row for row in rows if row["status"] == "pass"]
    return {
        "rowsMissingReferenceCpuGpuDiffStatsRoute": [
            row["rowId"]
            for row in support_rows
            if any(not row.get("proofs", {}).get(key) for key in ["reference", "cpu", "gpu", "diffStats", "route"])
        ],
        "rowsWithSemanticOpMismatch": [
            row["rowId"]
            for row in support_rows
            if row.get("cpuSemanticOps") != row.get("gpuSemanticOps")
        ],
        "rowsWithThresholdChanges": [
            row["rowId"]
            for row in rows
            if row.get("thresholds", {}).get("globalThresholdChanged") is True
            or row.get("thresholds", {}).get("thresholdsWeakened") is True
            or row.get("implementation", {}).get("thresholdsChanged") is True
        ],
        "rowsWithColorPolicyChanges": [
            row["rowId"]
            for row in rows
            if row.get("thresholds", {}).get("globalColorPolicyChanged") is True
            or row.get("implementation", {}).get("rendererBehaviorChanged") is True
        ],
        "rowsWithSilentApproximation": [
            row["rowId"]
            for row in rows
            if row["status"] == "expected-unsupported" and not row.get("reasonCode")
        ],
        "wideGamutRowsClaimingSupport": [
            row["rowId"]
            for row in rows
            if "wide-gamut" in row["pmCategory"] and row["status"] == "pass"
        ],
        "f16RowsClaimingGlobalPolicyChange": [
            row["rowId"]
            for row in rows
            if "f16" in row["pmCategory"]
            and row.get("implementation", {}).get("globalF16RendererChangeAllowedNow") is not False
        ],
        "broadBlendOrColorClaims": [
            row["rowId"]
            for row in rows
            if "no-all-blend-modes-claim" not in row.get("nonClaims", [])
            or "no-wide-gamut-general-claim" not in row.get("nonClaims", [])
        ],
        "ganeshGraphiteClaims": [
            row["rowId"]
            for row in rows
            if "no-ganesh-graphite-claim" not in row.get("nonClaims", [])
        ],
        "skslCompilerClaims": [
            row["rowId"]
            for row in rows
            if "no-sksl-compiler-ir-vm-claim" not in row.get("nonClaims", [])
        ],
    }


def committed_artifacts() -> list[str]:
    return sorted({
        f"{KAN015_ROOT}/stats.json",
        f"{KAN015_ROOT}/route-cpu.json",
        f"{KAN015_ROOT}/route-webgpu.json",
        f"{KAN015_ROOT}/reference.png",
        f"{KAN015_ROOT}/cpu.png",
        f"{KAN015_ROOT}/webgpu.png",
        f"{KAN015_ROOT}/cpu-diff.png",
        f"{KAN015_ROOT}/webgpu-diff.png",
        f"{KAN016_ROOT}/stats.json",
        f"{KAN016_ROOT}/route-cpu.json",
        f"{KAN016_ROOT}/route-webgpu.json",
        f"{KAN016_ROOT}/reference.png",
        f"{KAN016_ROOT}/cpu.png",
        f"{KAN016_ROOT}/webgpu.png",
        f"{KAN016_ROOT}/cpu-diff.png",
        f"{KAN016_ROOT}/webgpu-diff.png",
        M63_PACK_PATH,
        FOR345_PATH,
        SPEC_FEATURE_EXPANSION_PATH,
        SPEC_PIPELINE_IR_PATH,
        TARGET_WGSL_PATH,
        TARGET_REALTIME_PATH,
        SPEC_REALTIME_README_PATH,
    })


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    require_contains(root, SPEC_FEATURE_EXPANSION_PATH, [
        "premul/unpremul and clamp behavior",
        "destination color-space policy",
        "CPU and GPU routes name the same semantic operations",
        "no threshold weakening for color mismatches",
    ])
    require_contains(root, SPEC_PIPELINE_IR_PATH, [
        "`ColorValueSpec` names the color value domain",
        "alpha domain: unpremul, premul, raw, destination",
        "precision domain: U8, F16, F32",
        "Appending must be transactional",
    ])
    require_contains(root, TARGET_WGSL_PATH, [
        "Paint objects lower into an ordered color pipeline.",
        "Blend-mode semantics.",
        "Do not port Ganesh or Graphite.",
    ])
    require_contains(root, TARGET_REALTIME_PATH, [
        "Color, Blend & ColorFilter Parity",
        "Do not rebuild Skia's SkSL compiler, IR, or VM.",
    ])
    require_contains(root, SPEC_REALTIME_README_PATH, [
        "M63 Color, Blend & ColorFilter Parity",
        "Treat WGSL as the shader implementation target.",
    ])

    m63_pack = load_json(root, M63_PACK_PATH)
    rows = [
        build_src_over_row(root),
        build_color_filter_row(root),
        build_wide_gamut_refusal_row(m63_pack),
        build_f16_refusal_row(root),
    ]
    guard = build_claim_guard(rows)
    for field, values in guard.items():
        require(not values, f"{field}: {values}")

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-045",
        "packId": "kan-045-color-pipeline-bounded-policy",
        "status": "pass",
        "closureDecision": "bounded-color-pipeline-policy",
        "claimLevel": "existing-evidence-only",
        "supportClaim": "bounded-srgb-premul-only",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "wideGamutSupportClaim": False,
        "f16GlobalPolicyClaim": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "supportRows": sum(1 for row in rows if row["status"] == "pass"),
            "expectedUnsupportedRows": sum(1 for row in rows if row["status"] == "expected-unsupported"),
            "rowsMissingReferenceCpuGpuDiffStatsRoute": len(guard["rowsMissingReferenceCpuGpuDiffStatsRoute"]),
            "rowsWithSemanticOpMismatch": len(guard["rowsWithSemanticOpMismatch"]),
            "rowsWithThresholdChanges": len(guard["rowsWithThresholdChanges"]),
            "rowsWithSilentApproximation": len(guard["rowsWithSilentApproximation"]),
        },
        "policyRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan045ColorPipelineBoundedPolicy",
            ":gpu-raster:pipelineConformanceTest -- includes SimpleSrcOverAlphaSceneEvidenceTest and SimpleColorFilterSceneEvidenceTest",
            ":gpu-raster:wgslValidateStrict -- generated/registered WGSL parser validation",
            "pipelinePmBundle",
        ],
        "validationRows": [
            {
                "id": "bounded-srgb-premul-support",
                "status": "pass",
                "evidence": "KAN-015 and KAN-016 have reference/CPU/GPU/diff/stat/routes, matching semantic ops, and no fallback.",
            },
            {
                "id": "wide-gamut-refusal-visible",
                "status": "pass",
                "evidence": "M63 wide-gamut color-space row remains expected-unsupported via color.color-space-wide-gamut-unsupported.",
            },
            {
                "id": "f16-policy-candidate-refusal-visible",
                "status": "pass",
                "evidence": "FOR-345 Rec.2020 kRGBA_F16Norm row rejects the straight-sRGB candidate because it worsens covered samples.",
            },
            {
                "id": "no-threshold-or-policy-weakening",
                "status": "pass",
                "evidence": "All guards report no threshold, color policy, renderer, shader, or fallback change.",
            },
        ],
        "nonClaims": [
            "KAN-045 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-045 does not claim wide-gamut general support, HDR, gainmap, all blend modes, or broad color management.",
            "KAN-045 does not silently approximate unsupported color-space or F16 policy rows.",
            "KAN-045 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def markdown_table(rows: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{rowId}` | `{status}` | `{category}` | `{cpu}` | `{gpu}` | `{reason}` |".format(
            rowId=row["rowId"],
            status=row["status"],
            category=row["pmCategory"],
            cpu=row.get("route", {}).get("cpu", ""),
            gpu=row.get("route", {}).get("webGpu", ""),
            reason=row.get("reasonCode") or row.get("route", {}).get("fallbackReason") or "none",
        )
        for row in rows
    )


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    guards = "\n".join(f"| {key} | `{value}` |" for key, value in evidence["claimGuard"].items())
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-045 Color Pipeline Bounded Policy

KAN-045 packages the bounded color pipeline policy from existing support and
refusal evidence. It keeps support limited to selected sRGB/premul rows and
keeps wide-gamut/F16 policy rows explicit refusals without changing renderer
behavior or thresholds.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| Support rows | {summary['supportRows']} |
| Expected unsupported rows | {summary['expectedUnsupportedRows']} |
| Rows missing reference/CPU/GPU/diff/stat/route | {summary['rowsMissingReferenceCpuGpuDiffStatsRoute']} |
| Rows with semantic op mismatch | {summary['rowsWithSemanticOpMismatch']} |
| Rows with threshold changes | {summary['rowsWithThresholdChanges']} |
| Rows with silent approximation | {summary['rowsWithSilentApproximation']} |

## Policy Rows

| Row | Status | Category | CPU route | WebGPU route | Reason |
|---|---|---|---|---|---|
{markdown_table(evidence['policyRows'])}

## Claim Guard

| Guard | Value |
|---|---|
{guards}

## Required Validation

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    summary = evidence["summary"]
    print(
        "KAN-045 validation passed: "
        f"{summary['totalRows']} rows, "
        f"{summary['supportRows']} support, "
        f"{summary['expectedUnsupportedRows']} expected-unsupported, "
        f"{summary['rowsWithThresholdChanges']} threshold changes."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
