#!/usr/bin/env python3
"""Generate and validate the M51 Skia GM inventory."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ALLOWED_STATUSES = {
    "dashboard-promoted",
    "promotion-candidate",
    "expected-unsupported",
    "dependency-gated",
    "not-triaged",
    "non-rendering-or-utility",
    "duplicate-or-variant",
}

UPSTREAM_ROOT_DEFAULT = Path("/Users/chaos/workspace/kanvas-forge/skia-main/gm")
KOTLIN_ROOT_DEFAULT = Path("skia-integration-tests/src/main/kotlin/org/skia/tests")

DASHBOARD_LINKS = {
    "bitmapshader": ["bitmap-shader-repeat-tile", "bitmap-shader-local-matrix"],
    "bitmaprect": ["bitmap-rect-nearest", "bitmap-subset-local-matrix-repeat"],
    "bitmaprecttest": ["m52-bitmap-rect-test-nearest"],
    "bitmapimage": ["m52-bitmap-image-basic"],
    "cropimagefilter": ["crop-image-filter-nonnull-prepass", "image-filter-crop-nonnull-prepass-required"],
    "gradients": ["linear-gradient-rect", "gradient-color-filter-linear-kplus"],
    "fillrectgradient": ["m52-fillrect-gradient-linear"],
    "hardstopgradients": ["m52-hardstop-gradient-linear"],
    "sweepgradient": ["sweep-gradient-path-clamp"],
    "aarectmodes": ["m52-aa-rect-modes-tight-aa"],
    "androidblendmodes": ["m52-android-blend-src-over-screen"],
    "clipdrawdraw": ["draw-paint-full-clip", "draw-paint-clipped-rect"],
    "clippedbitmapshaders": ["m52-clipped-bitmap-shader-rect"],
    "scaledrects": ["scaled-rects-transform-stack"],
    "runtimecolorfilter": ["runtime-effect-simple"],
    "runtimeshader": ["runtime-effect-simple"],
    "batchedconvexpaths": ["analytic-aa-convex", "path-aa-convexpaths-edge-budget"],
    "convexpaths": ["analytic-aa-convex", "path-aa-convexpaths-edge-budget"],
    "strokerect": ["path-aa-stroke-primitive"],
    "strokerects": ["path-aa-stroke-primitive"],
    "closedcappedhairlines": ["m52-closed-capped-hairlines-edge-budget"],
    "bigtileimagefilter": ["m52-big-tile-image-filter-dag-refusal"],
    "fontscaler": ["font-latin-outline-drawstring"],
    "textblob": ["font-textblob-positioned-glyph-run"],
    "fontmgr": ["font-kerning-style-fixture"],
    "coloremoji": ["font-emoji-color-glyph-refusal"],
    "coloremojiblendmodes": ["m52-color-emoji-blendmodes-refusal"],
    "textblobblockreordering": ["font-complex-shaping-refusal"],
}

PROMOTION_CANDIDATE_KEYS = {
    "aarectmodes",
    "androidblendmodes",
    "arithmode",
    "badpaint",
    "bitmapfilters",
    "bitmapimage",
    "bitmaprecttest",
    "bitmappremul",
    "clippedbitmapshaders",
    "clipshader",
    "complexclip",
    "convexpolyclip",
    "drawbitmaprect",
    "drawminibitmaprect",
    "fillrectgradient",
    "gradients2ptconical",
    "gradientsdegenerate",
    "hardstopgradients",
    "image",
    "imageblur",
    "imagemakewithfilter",
    "imagefilterscropped",
    "imagefilterstransformed",
    "imagesource",
    "localmatriximageshader",
    "matriximagefilter",
    "modecolorfilters",
    "offsetimagefilter",
    "pathfill",
    "rectpolystroke",
    "runtimeimagefilter",
    "runtimeintrinsics",
    "shadertext3",
    "simpleaaclip",
    "strokerects",
    "textblobtransforms",
}

EXPECTED_UNSUPPORTED_RULES = [
    (("convexpaths", "linepaths", "manypaths", "dash", "pathaa", "hairline", "largeclippedpath"), "coverage.edge-count-exceeded"),
    (("imagefiltersgraph", "bigtileimagefilter", "pictureimagefilter"), "image-filter.dag-or-picture-prepass-required"),
]

DEPENDENCY_RULES = [
    (("animcodec", "animatedgif", "animatedimage", "codec", "exif", "webp", "yuv"), "codec/image decode dependency remains gated"),
    (("coloremoji", "emoji"), "font.color-glyph-emoji-unsupported"),
    (("dftext", "lcdtext", "gammatext", "fontations", "fontmgr", "fontscaler", "textblobrandomfont"), "font/glyph backend dependency remains gated"),
    (("shaping", "arabic", "devanagari", "mixedtextblobs", "blockreordering"), "font.complex-shaping-requires-explicit-shaper"),
    (("svg",), "SVG/color glyph renderer dependency remains gated"),
]

NON_RENDERING_RULES = [
    ("attributes", "GM registration or metadata utility"),
    ("fontcache", "font cache stress utility"),
    ("fontregen", "font regeneration utility"),
    ("hellobazelworld", "build smoke utility"),
    ("resources", "resource helper utility"),
]

VARIANT_TOKENS = (
    "skbug",
    "bug",
    "crbug",
    "variant",
    "many",
    "small",
    "large",
    "tiled",
    "repeat",
    "mip",
    "aniso",
    "orientation",
)

FAMILY_RULES = [
    ("codec/image-decode", ("codec", "animated", "gif", "exif", "webp", "yuv", "imagefromyuv")),
    ("text/font", ("text", "font", "glyph", "emoji", "lcd", "dftext", "typeface")),
    ("runtime-effects", ("runtime", "skruntime", "shadertext")),
    ("image-filters", ("imagefilter", "blur", "drop", "matriximagefilter", "cropimagefilter", "filter")),
    ("bitmap/image", ("bitmap", "image", "pixmap", "texture", "yuv")),
    ("gradients", ("gradient", "grad", "conical", "radial", "sweep")),
    ("clip/transform", ("clip", "matrix", "transform", "scale", "skew", "rotate", "persp")),
    ("path-aa", ("path", "aa", "stroke", "hairline", "convex", "concave", "cubic", "quad")),
    ("paint/blend", ("blend", "xfer", "mode", "colorfilter", "paint", "color4f", "srcover", "alpha")),
]

FAMILY_DETAILS = {
    "paint/blend": {
        "cpu": "CPU PipelineIR paint/blend route with scalar oracle first.",
        "gpu": "Generated WGSL BlendPlan route when fallbackReason remains none.",
        "risk": "Blend/color filter tolerances and premul semantics.",
    },
    "bitmap/image": {
        "cpu": "CPU bitmap/image sampling oracle with explicit subset/local matrix behavior.",
        "gpu": "WebGPU image sampling route or explicit refusal for unsupported sampling.",
        "risk": "Reference image availability, tiling, mip/aniso boundaries.",
    },
    "gradients": {
        "cpu": "CPU gradient PipelineIR oracle.",
        "gpu": "Generated WGSL gradient route for bounded gradient kind.",
        "risk": "Color interpolation, hard stops, degenerate gradients.",
    },
    "clip/transform": {
        "cpu": "CPU coverage/clip oracle with route diagnostics.",
        "gpu": "WebGPU coverage/clip route when coverage plan is bounded.",
        "risk": "Nested clip, perspective, and coverage expansion.",
    },
    "path-aa": {
        "cpu": "CPU coverage oracle with edge-budget diagnostics.",
        "gpu": "WebGPU coverage route or stable coverage edge-budget refusal.",
        "risk": "Edge budget, stroke joins, dashing, and AA thresholds.",
    },
    "image-filters": {
        "cpu": "CPU layer/prepass image-filter oracle.",
        "gpu": "WebGPU explicit prepass/layer route or stable DAG refusal.",
        "risk": "Intermediate texture ownership and DAG scope.",
    },
    "runtime-effects": {
        "cpu": "Registered runtime-effect CPU descriptor route.",
        "gpu": "Registered WGSL runtime-effect descriptor route or explicit refusal.",
        "risk": "Only registered Kotlin/WGSL effects are in scope; no SkSL compiler.",
    },
    "text/font": {
        "cpu": "Portable OpenType/simple text CPU route when dependency is present.",
        "gpu": "Glyph outline/mask WebGPU route or stable font refusal.",
        "risk": "Font, shaping, emoji, SDF, LCD, and glyph-mask dependencies.",
    },
    "codec/image-decode": {
        "cpu": "Codec boundary fixture decoded through pure Kotlin codec when available.",
        "gpu": "Image upload/sampling route after decode, or dependency refusal.",
        "risk": "Codec delivery and reference asset availability.",
    },
}


def normalize_key(path: Path, is_kotlin: bool) -> str:
    stem = path.stem
    if is_kotlin and stem.endswith("GM"):
        stem = stem[:-2]
    if not is_kotlin and stem.lower().endswith("gm"):
        stem = stem[:-2]
    normalized = re.sub(r"[^a-z0-9]", "", stem.lower())
    return normalized or re.sub(r"[^a-z0-9]", "", path.stem.lower())


def display_name(key: str, upstream: Path | None, kotlin: Path | None) -> str:
    stem = kotlin.stem[:-2] if kotlin and kotlin.stem.endswith("GM") else (upstream.stem if upstream else key)
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", stem.replace("_", " ").replace("-", " "))
    words = re.sub(r"\s+", " ", words).strip()
    return words or key


def families_for(key: str, name: str) -> list[str]:
    haystack = f"{key} {name.lower()}"
    families = [family for family, tokens in FAMILY_RULES if any(token in haystack for token in tokens)]
    return families or ["misc/rendering"]


def status_for(key: str, families: list[str], source_kind: str) -> tuple[str, str]:
    if key in DASHBOARD_LINKS:
        return "dashboard-promoted", "Linked to existing dashboard scene evidence; inventory link does not broaden support."
    for rule_key, reason in NON_RENDERING_RULES:
        if rule_key in key:
            return "non-rendering-or-utility", reason
    for tokens, reason in DEPENDENCY_RULES:
        if any(token in key for token in tokens):
            return "dependency-gated", reason
    for tokens, reason in EXPECTED_UNSUPPORTED_RULES:
        if any(token in key for token in tokens):
            return "expected-unsupported", reason
    if key in PROMOTION_CANDIDATE_KEYS:
        return "promotion-candidate", "Selected M52+ candidate; no support claim until generated evidence exists."
    if source_kind != "matched" and any(token in key for token in VARIANT_TOKENS):
        return "duplicate-or-variant", "Variant source should roll up under a canonical promoted target before support claims."
    return "not-triaged", ""


def source_kind(upstream: Path | None, kotlin: Path | None) -> str:
    if upstream and kotlin:
        return "matched"
    if upstream:
        return "upstream-only"
    return "kotlin-only"


def rel(path: Path | None, base: Path) -> str | None:
    if path is None:
        return None
    try:
        return str(path.resolve().relative_to(base.resolve())).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def load_dashboard_scene_ids(paths: list[Path]) -> list[str]:
    ids: list[str] = []
    for path in paths:
        if not path.is_file():
            continue
        data = json.loads(path.read_text())
        for scene in data.get("scenes", []):
            scene_id = scene.get("id")
            if isinstance(scene_id, str) and scene_id:
                ids.append(scene_id)
    return sorted(set(ids))


def build_inventory(upstream_root: Path, kotlin_root: Path, project_root: Path, dashboard_json: list[Path]) -> dict[str, Any]:
    upstream_files = sorted(upstream_root.glob("*.cpp"))
    kotlin_files = sorted(kotlin_root.glob("*GM.kt"))
    upstream_by_key: dict[str, list[Path]] = defaultdict(list)
    kotlin_by_key: dict[str, list[Path]] = defaultdict(list)
    for path in upstream_files:
        upstream_by_key[normalize_key(path, False)].append(path)
    for path in kotlin_files:
        kotlin_by_key[normalize_key(path, True)].append(path)

    rows: list[dict[str, Any]] = []
    duplicate_groups: list[dict[str, Any]] = []
    all_keys = sorted(set(upstream_by_key) | set(kotlin_by_key))
    for key in all_keys:
        ups = upstream_by_key.get(key, [])
        kts = kotlin_by_key.get(key, [])
        if len(ups) > 1 or len(kts) > 1:
            duplicate_groups.append(
                {
                    "key": key,
                    "upstreamPaths": [rel(path, project_root) for path in ups],
                    "kotlinPaths": [rel(path, project_root) for path in kts],
                }
            )
        count = max(len(ups), len(kts), 1)
        for index in range(count):
            upstream = ups[index] if index < len(ups) else None
            kotlin = kts[index] if index < len(kts) else None
            kind = source_kind(upstream, kotlin)
            name = display_name(key, upstream, kotlin)
            families = families_for(key, name)
            status, reason = status_for(key, families, kind)
            if count > 1 and status == "not-triaged":
                status = "duplicate-or-variant"
                reason = "Duplicate normalized source key; triage under a canonical inventory id before promotion."
            row_id = f"skia-gm-{key}" if count == 1 else f"skia-gm-{key}-{index + 1}"
            source_filename = (upstream or kotlin).name if (upstream or kotlin) else key
            row = {
                "id": row_id,
                "sourceKind": kind,
                "upstreamPath": rel(upstream, project_root),
                "kotlinPath": rel(kotlin, project_root),
                "sourceFilename": source_filename,
                "normalizedDisplayName": name,
                "familyTags": families,
                "status": status,
                "reason": reason,
            }
            links = [scene for scene in DASHBOARD_LINKS.get(key, []) if scene in load_dashboard_scene_ids(dashboard_json)]
            if links:
                row["dashboardSceneIds"] = links
            rows.append(row)

    rows.sort(key=lambda item: item["id"])
    mismatches = {
        "upstreamOnly": sorted(row["id"] for row in rows if row["sourceKind"] == "upstream-only"),
        "kotlinOnly": sorted(row["id"] for row in rows if row["sourceKind"] == "kotlin-only"),
        "duplicateNormalizedKeys": duplicate_groups,
    }
    status_counts = Counter(row["status"] for row in rows)
    family_counts = Counter(family for row in rows for family in row["familyTags"])
    source_counts = Counter(row["sourceKind"] for row in rows)
    promoted_links = {
        scene: row["id"]
        for row in rows
        for scene in row.get("dashboardSceneIds", [])
    }
    return {
        "schemaVersion": 1,
        "generatedBy": "pipelineSkiaGmInventory",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "description": "M51 inventory of upstream Skia GM C++ files and Kotlin GM sources. Inventory status is planning evidence and is not a dashboard support claim.",
        "inputs": {
            "upstreamRoot": str(upstream_root),
            "kotlinRoot": str(kotlin_root),
            "dashboardSceneSources": [str(path) for path in dashboard_json if path.is_file()],
        },
        "summary": {
            "upstreamGmFiles": len(upstream_files),
            "kotlinGmFiles": len(kotlin_files),
            "inventoryRows": len(rows),
            "matchedRows": source_counts.get("matched", 0),
            "upstreamOnlyRows": source_counts.get("upstream-only", 0),
            "kotlinOnlyRows": source_counts.get("kotlin-only", 0),
            "classifiedRows": sum(1 for row in rows if row["status"] != "not-triaged"),
            "notTriagedRows": status_counts.get("not-triaged", 0),
            "dashboardPromotedRows": status_counts.get("dashboard-promoted", 0),
            "promotionCandidateRows": status_counts.get("promotion-candidate", 0),
            "dependencyGatedRows": status_counts.get("dependency-gated", 0),
            "expectedUnsupportedRows": status_counts.get("expected-unsupported", 0),
            "duplicateOrVariantRows": status_counts.get("duplicate-or-variant", 0),
            "nonRenderingOrUtilityRows": status_counts.get("non-rendering-or-utility", 0),
        },
        "statusCounts": dict(sorted(status_counts.items())),
        "familyCounts": dict(sorted(family_counts.items())),
        "sourcePresenceCounts": dict(sorted(source_counts.items())),
        "mismatches": mismatches,
        "dashboardInventoryLinks": dict(sorted(promoted_links.items())),
        "rows": rows,
        "promotionCandidates": select_candidates(rows),
    }


def select_candidates(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    family_targets = [
        "paint/blend",
        "bitmap/image",
        "gradients",
        "clip/transform",
        "path-aa",
        "image-filters",
        "runtime-effects",
        "text/font",
        "codec/image-decode",
    ]
    candidates = [row for row in rows if row["status"] in {"promotion-candidate", "dependency-gated", "expected-unsupported"}]
    for family in family_targets:
        family_rows = [row for row in candidates if family in row["familyTags"]]
        limit = 4 if family != "codec/image-decode" else 2
        for row in family_rows[:limit]:
            if row["id"] not in {item["inventoryId"] for item in selected}:
                selected.append(candidate_payload(row, family))
    for row in candidates:
        if len(selected) >= 34:
            break
        if row["id"] not in {item["inventoryId"] for item in selected}:
            selected.append(candidate_payload(row, row["familyTags"][0]))
    return selected[:40]


def candidate_payload(row: dict[str, Any], family: str) -> dict[str, Any]:
    details = FAMILY_DETAILS.get(family, FAMILY_DETAILS["paint/blend"])
    return {
        "inventoryId": row["id"],
        "displayName": row["normalizedDisplayName"],
        "family": family,
        "upstreamSource": row.get("upstreamPath"),
        "kotlinSource": row.get("kotlinPath"),
        "referenceAvailable": "unknown until candidate-specific capture/rebaseline is run",
        "expectedCpuRoute": details["cpu"],
        "expectedGpuRouteOrRefusal": details["gpu"],
        "riskOrDependency": row.get("reason") or details["risk"],
        "suggestedValidationCommand": "rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate",
        "supportClaim": "none; candidate requires generated evidence before dashboard promotion",
    }


def write_markdown(inventory: dict[str, Any], path: Path) -> None:
    summary = inventory["summary"]
    rows = inventory["rows"]
    candidates = inventory["promotionCandidates"]
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Skia GM Inventory",
        "",
        "Generated by `pipelineSkiaGmInventory`.",
        "",
        "> Inventory rows are planning evidence only. They are not rendering support claims and do not change scene dashboard counters.",
        "",
        "## Summary",
        "",
        "| Signal | Count |",
        "|---|---:|",
    ]
    for key in [
        "upstreamGmFiles",
        "kotlinGmFiles",
        "inventoryRows",
        "classifiedRows",
        "notTriagedRows",
        "dashboardPromotedRows",
        "promotionCandidateRows",
        "dependencyGatedRows",
        "expectedUnsupportedRows",
        "duplicateOrVariantRows",
        "nonRenderingOrUtilityRows",
    ]:
        lines.append(f"| `{key}` | {summary.get(key, 0)} |")
    lines += [
        "",
        "## Status Counts",
        "",
        "| Status | Count |",
        "|---|---:|",
    ]
    for status, count in inventory["statusCounts"].items():
        lines.append(f"| `{status}` | {count} |")
    lines += [
        "",
        "## Family Counts",
        "",
        "| Family | Count |",
        "|---|---:|",
    ]
    for family, count in inventory["familyCounts"].items():
        lines.append(f"| `{family}` | {count} |")
    lines += [
        "",
        "## Source Presence",
        "",
        "| Source presence | Count |",
        "|---|---:|",
    ]
    for kind, count in inventory["sourcePresenceCounts"].items():
        lines.append(f"| `{kind}` | {count} |")
    lines += [
        "",
        "## Mismatches",
        "",
        f"- Upstream-only rows: {len(inventory['mismatches']['upstreamOnly'])}",
        f"- Kotlin-only rows: {len(inventory['mismatches']['kotlinOnly'])}",
        f"- Duplicate normalized keys: {len(inventory['mismatches']['duplicateNormalizedKeys'])}",
        "",
        "## Dashboard Inventory Links",
        "",
    ]
    if inventory["dashboardInventoryLinks"]:
        lines += ["| Dashboard scene | Inventory id |", "|---|---|"]
        for scene, inventory_id in inventory["dashboardInventoryLinks"].items():
            lines.append(f"| `{scene}` | `{inventory_id}` |")
    else:
        lines.append("No dashboard rows could be linked to inventory rows.")
    lines += [
        "",
        "## M52+ Promotion Candidate Backlog",
        "",
        "| Inventory id | Family | Upstream | Kotlin | Risk/dependency |",
        "|---|---|---|---|---|",
    ]
    for candidate in candidates:
        lines.append(
            "| `{}` | `{}` | `{}` | `{}` | {} |".format(
                candidate["inventoryId"],
                candidate["family"],
                candidate["upstreamSource"] or "",
                candidate["kotlinSource"] or "",
                candidate["riskOrDependency"],
            )
        )
    lines += [
        "",
        "## Inventory Rows",
        "",
        "| Id | Status | Family | Source | Upstream | Kotlin | Reason |",
        "|---|---|---|---|---|---|---|",
    ]
    for row in rows:
        lines.append(
            "| `{}` | `{}` | `{}` | `{}` | `{}` | `{}` | {} |".format(
                row["id"],
                row["status"],
                ", ".join(row["familyTags"]),
                row["sourceKind"],
                row.get("upstreamPath") or "",
                row.get("kotlinPath") or "",
                row.get("reason") or "",
            )
        )
    path.write_text("\n".join(lines) + "\n")


def generate(args: argparse.Namespace) -> int:
    project_root = Path(args.project_root).resolve()
    upstream_root = Path(args.upstream_root)
    kotlin_root = project_root / args.kotlin_root
    if not upstream_root.is_dir():
        print(
            f"Warning: missing upstream GM root: {upstream_root}; "
            "generating Kotlin-only inventory for CI/release-gate environments."
        )
    if not kotlin_root.is_dir():
        raise SystemExit(f"Missing Kotlin GM root: {kotlin_root}")
    dashboard_json = [project_root / item for item in args.dashboard_json]
    inventory = build_inventory(upstream_root, kotlin_root, project_root, dashboard_json)
    output_dir = project_root / args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "inventory.json").write_text(json.dumps(inventory, indent=2, sort_keys=True) + "\n")
    write_markdown(inventory, output_dir / "inventory.md")
    print(f"Wrote Skia GM inventory: {output_dir / 'inventory.json'}")
    print(f"Wrote Skia GM inventory markdown: {output_dir / 'inventory.md'}")
    return 0


def validate(args: argparse.Namespace) -> int:
    project_root = Path(args.project_root).resolve()
    inventory_path = project_root / args.inventory_json
    report_dir = project_root / args.report_dir
    if not inventory_path.is_file():
        raise SystemExit(f"Missing inventory JSON: {inventory_path}")
    inventory = json.loads(inventory_path.read_text())
    rows = inventory.get("rows", [])
    failures: list[str] = []
    warnings: list[str] = []
    ids = [row.get("id") for row in rows if isinstance(row, dict)]
    for inventory_id, count in Counter(ids).items():
        if not inventory_id or count > 1:
            failures.append(f"duplicate or blank id `{inventory_id}` count={count}")
    for row in rows:
        if not isinstance(row, dict):
            failures.append("row is not an object")
            continue
        row_id = row.get("id", "<missing>")
        if row.get("status") not in ALLOWED_STATUSES:
            failures.append(f"{row_id}: invalid or missing status `{row.get('status')}`")
        if not row.get("upstreamPath") and not row.get("kotlinPath"):
            failures.append(f"{row_id}: missing both upstreamPath and kotlinPath")
        families = row.get("familyTags")
        if not isinstance(families, list) or not any(isinstance(item, str) and item for item in families):
            failures.append(f"{row_id}: missing familyTags")
        if row.get("status") != "not-triaged" and not row.get("reason"):
            failures.append(f"{row_id}: non not-triaged row requires reason")
    mismatches = inventory.get("mismatches", {})
    warnings.append(
        "Mismatch snapshot: upstream-only={}, kotlin-only={}, duplicate-normalized-keys={}".format(
            len(mismatches.get("upstreamOnly", [])),
            len(mismatches.get("kotlinOnly", [])),
            len(mismatches.get("duplicateNormalizedKeys", [])),
        )
    )
    report_dir.mkdir(parents=True, exist_ok=True)
    report = {
        "schemaVersion": 1,
        "generatedBy": "pipelineSkiaGmInventoryGate",
        "inventoryJson": args.inventory_json,
        "failed": bool(failures),
        "failures": failures,
        "warnings": warnings,
        "summary": inventory.get("summary", {}),
        "statusCounts": inventory.get("statusCounts", {}),
        "sourcePresenceCounts": inventory.get("sourcePresenceCounts", {}),
    }
    (report_dir / "inventory-gate.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    (report_dir / "inventory-gate.md").write_text(render_gate_markdown(report))
    if failures:
        print(render_gate_markdown(report), file=sys.stderr)
        return 1
    print(f"Wrote Skia GM inventory gate report: {report_dir / 'inventory-gate.md'}")
    return 0


def render_gate_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Skia GM Inventory Gate",
        "",
        f"Result: {'fail' if report['failed'] else 'pass'}",
        "",
        "## Summary",
        "",
        "| Signal | Count |",
        "|---|---:|",
    ]
    for key, value in report.get("summary", {}).items():
        lines.append(f"| `{key}` | {value} |")
    lines += ["", "## Warnings", ""]
    for warning in report.get("warnings", []):
        lines.append(f"- {warning}")
    lines += ["", "## Failures", ""]
    if report.get("failures"):
        for failure in report["failures"]:
            lines.append(f"- {failure}")
    else:
        lines.append("No failures.")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--project-root", default=".")
    generate_parser = sub.add_parser("generate", parents=[common])
    generate_parser.add_argument("--upstream-root", default=str(UPSTREAM_ROOT_DEFAULT))
    generate_parser.add_argument("--kotlin-root", default=str(KOTLIN_ROOT_DEFAULT))
    generate_parser.add_argument("--dashboard-json", action="append", default=[
        "reports/wgsl-pipeline/scenes/data/scenes.json",
        "reports/wgsl-pipeline/scenes/generated/results.json",
    ])
    generate_parser.add_argument("--output-dir", default="build/reports/wgsl-pipeline-skia-gm-inventory")
    validate_parser = sub.add_parser("validate", parents=[common])
    validate_parser.add_argument("--inventory-json", default="build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json")
    validate_parser.add_argument("--report-dir", default="build/reports/wgsl-pipeline-skia-gm-inventory-gate")
    args = parser.parse_args()
    if args.command == "generate":
        return generate(args)
    if args.command == "validate":
        return validate(args)
    raise AssertionError(args.command)


if __name__ == "__main__":
    raise SystemExit(main())
