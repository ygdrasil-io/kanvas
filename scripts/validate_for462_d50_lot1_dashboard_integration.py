#!/usr/bin/env python3
"""Validate and report FOR-462 D50 lot 1 dashboard integration status."""

from __future__ import annotations

import json
import subprocess
import sys
from collections import Counter
from pathlib import Path
from typing import Any


sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[1]
INVENTORY = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json"
DASHBOARD = ROOT / "build/reports/wgsl-pipeline-scenes/data/scenes.json"
GATE_JSON = ROOT / "build/reports/wgsl-pipeline-scene-gate/scene-dashboard-gate.json"
REPORT = ROOT / "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md"
EVIDENCE = ROOT / "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json"

EXPECTED_LOT1 = [
    "skia-gm-drawbitmaprect",
    "skia-gm-drawminibitmaprect",
    "skia-gm-bitmappremul",
    "skia-gm-image",
    "skia-gm-imagesource",
    "skia-gm-localmatriximageshader",
    "skia-gm-gradientsdegenerate",
    "skia-gm-offsetimagefilter",
    "skia-gm-matriximagefilter",
    "skia-gm-imageblur",
    "skia-gm-simpleaaclip",
    "skia-gm-pathfill",
]
EXPECTED_MATERIALIZED = {
    "skia-gm-drawbitmaprect": ["m66-bitmap-rect-nearest-skia"],
    "skia-gm-bitmappremul": ["m53-bitmap-premul-alpha"],
    "skia-gm-localmatriximageshader": ["m54-local-matrix-blend-composition"],
    "skia-gm-gradientsdegenerate": ["m53-degenerate-gradient-linear"],
    "skia-gm-matriximagefilter": ["m54-matrix-imagefilter-affine"],
    "skia-gm-imageblur": ["m53-imageblur-bounded-prepass"],
    "skia-gm-simpleaaclip": ["m54-simple-aa-clip"],
}
EXPECTED_MISSING = [
    "skia-gm-drawminibitmaprect",
    "skia-gm-image",
    "skia-gm-imagesource",
    "skia-gm-offsetimagefilter",
    "skia-gm-pathfill",
]
EXPECTED_FILES = {
    "reports/wgsl-pipeline/2026-06-06-for-462-d50-lot1-dashboard-integration-gate.md",
    "reports/wgsl-pipeline/scenes/generated/d50-lot1-dashboard-integration-for462.json",
    "scripts/validate_for462_d50_lot1_dashboard_integration.py",
}
FORBIDDEN_PATHS = {
    "reports/wgsl-pipeline/scenes/data/scenes.json",
    "reports/wgsl-pipeline/scenes/generated/results.json",
    "reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json",
}
FORBIDDEN_PREFIXES = (
    ".upstream/",
    "gpu-raster/",
    "render-pipeline/",
    "cpu-raster/",
    "skia-integration-tests/",
)


def fail(message: str) -> None:
    raise SystemExit(f"FOR-462 validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"missing JSON file: {rel(path)}")
    data = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(data, dict), f"{rel(path)} must contain a JSON object")
    return data


def write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def git_changed_paths() -> set[str]:
    diff_result = subprocess.run(
        ["git", "diff", "--name-only", "origin/master"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    status_result = subprocess.run(
        ["git", "status", "--short"],
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=True,
    )
    changed = {line.strip() for line in diff_result.stdout.splitlines() if line.strip()}
    for line in status_result.stdout.splitlines():
        if len(line) < 4:
            continue
        path = line[3:].strip()
        if path:
            changed.add(path.rstrip("/"))
    return changed


def require_scope() -> None:
    changed = git_changed_paths()
    unexpected = sorted(path for path in changed if path not in EXPECTED_FILES)
    require(not unexpected, f"unexpected local diffs for FOR-462: {unexpected}")
    forbidden_paths = sorted(path for path in changed if path in FORBIDDEN_PATHS)
    require(not forbidden_paths, f"active dashboard/inventory inputs changed: {forbidden_paths}")
    forbidden_prefixes = sorted(path for path in changed if path.startswith(FORBIDDEN_PREFIXES))
    require(not forbidden_prefixes, f"production/source diffs are out of scope: {forbidden_prefixes}")


def dashboard_rows_by_inventory(scenes: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    rows: dict[str, list[dict[str, Any]]] = {}
    for scene in scenes:
        inventory_id = scene.get("inventoryId")
        if isinstance(inventory_id, str) and inventory_id:
            rows.setdefault(inventory_id, []).append(scene)
    return rows


def require_dashboard_gate(gate: dict[str, Any]) -> dict[str, int]:
    failures = gate.get("failures")
    require(failures == [], "pipelineSceneDashboardGate must have zero failures")
    counters = gate.get("counters")
    require(isinstance(counters, dict), "gate counters must be present")
    expected = {
        "total": 93,
        "status.pass": 70,
        "status.expected-unsupported": 23,
        "inventoryDerived": 45,
    }
    normalized: dict[str, int] = {}
    for key, expected_value in expected.items():
        value = counters.get(key)
        require(value == expected_value, f"gate counter {key} mismatch: {value} != {expected_value}")
        normalized[key] = int(value)
    require(counters.get("status.tracked-gap", 0) == 0, "tracked-gap rows must remain 0")
    require(counters.get("status.fail", 0) == 0, "fail rows must remain 0")
    normalized["status.tracked-gap"] = int(counters.get("status.tracked-gap", 0))
    normalized["status.fail"] = int(counters.get("status.fail", 0))
    return normalized


def build_evidence() -> dict[str, Any]:
    inventory = load_json(INVENTORY)
    dashboard = load_json(DASHBOARD)
    gate = load_json(GATE_JSON)
    lot1 = inventory.get("lot1Recommendation", {}).get("candidateIds")
    require(lot1 == EXPECTED_LOT1, "FOR-461 lot 1 order changed")
    scenes = dashboard.get("scenes")
    require(isinstance(scenes, list), "dashboard scenes must be a list")
    scene_dicts = [scene for scene in scenes if isinstance(scene, dict)]
    require(len(scene_dicts) == len(scenes), "all dashboard scenes must be objects")
    gate_counters = require_dashboard_gate(gate)

    rows_by_inventory = dashboard_rows_by_inventory(scene_dicts)
    materialized: list[dict[str, Any]] = []
    missing: list[dict[str, Any]] = []
    for inventory_id in EXPECTED_LOT1:
        rows = rows_by_inventory.get(inventory_id, [])
        row_ids = [row.get("id") for row in rows]
        expected_rows = EXPECTED_MATERIALIZED.get(inventory_id, [])
        if expected_rows:
            require(row_ids == expected_rows, f"{inventory_id} materialized rows mismatch: {row_ids}")
            row_payloads = []
            for row in rows:
                status = row.get("status")
                gpu_route = row.get("gpu", {}).get("route", {})
                fallback = gpu_route.get("fallbackReason")
                require(status == "pass", f"{row.get('id')} must remain pass")
                require(row.get("gpu", {}).get("status") == "pass", f"{row.get('id')} gpu.status must remain pass")
                require(fallback == "none", f"{row.get('id')} pass row must keep fallbackReason=none")
                row_payloads.append(
                    {
                        "id": row["id"],
                        "status": status,
                        "referenceKind": row.get("referenceKind"),
                        "derivationTask": row.get("generation", {}).get("derivationTask"),
                        "fallbackReason": fallback,
                    }
                )
            materialized.append({"inventoryId": inventory_id, "rows": row_payloads})
        else:
            require(not rows, f"{inventory_id} unexpectedly materialized without FOR-462 proof")
            missing.append(
                {
                    "inventoryId": inventory_id,
                    "status": "missing-row-specific-evidence",
                    "requiredEvidence": [
                        "row-specific reference artifact",
                        "CPU artifact and route diagnostics",
                        "GPU artifact or stable expected-unsupported refusal",
                        "diff and stats artifacts",
                        "fallbackReason=none for support rows",
                        "unchanged dashboard thresholds and gate policy",
                    ],
                }
            )

    reference_counts = Counter()
    for item in materialized:
        for row in item["rows"]:
            reference_counts[row["referenceKind"]] += 1

    evidence = {
        "schemaVersion": 1,
        "linear": "FOR-462",
        "date": "2026-06-06",
        "sourceDraftMemory": "global/kanvas/tickets/drafts/brouillon-reprise-for-462-d50-lot-1-dashboard-sans-faux-support",
        "sourceInventory": rel(INVENTORY),
        "sourceFindingMemory": "global/kanvas/findings/for-461-integre-linventaire-d50-des-candidats-gm-dashboard",
        "dashboardGate": rel(GATE_JSON),
        "classification": "lot1-partially-materialized-no-new-support-claims",
        "lot1CandidateCount": len(EXPECTED_LOT1),
        "materializedCandidateCount": len(materialized),
        "missingCandidateCount": len(missing),
        "materializedRowsAddedByFor462": 0,
        "supportClaimsAddedByFor462": 0,
        "skiaComparableClaimsAddedByFor462": 0,
        "dashboardStatusChangedByFor462": False,
        "thresholdChanged": False,
        "scoringChanged": False,
        "fallbackPolicyChanged": False,
        "pipelineKeyChanged": False,
        "productionCodeChanged": False,
        "upstreamSourceChanged": False,
        "broadSkiaGmParityClaim": False,
        "dashboardCounters": gate_counters,
        "materializedReferenceKindCounts": dict(sorted(reference_counts.items())),
        "materialized": materialized,
        "missing": missing,
        "nextAction": "Open row-specific evidence tickets for the five missing lot 1 candidates before any dashboard support promotion.",
        "validationCommands": [
            "rtk ./gradlew --no-daemon pipelineSceneDashboardGate",
            "rtk python3 scripts/validate_for462_d50_lot1_dashboard_integration.py",
            "rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-for462-pycache python3 -m py_compile scripts/validate_for462_d50_lot1_dashboard_integration.py",
            "rtk git diff --check",
        ],
    }
    return evidence


def write_report(evidence: dict[str, Any]) -> None:
    lines = [
        "# FOR-462 - D50 lot 1 dashboard integration gate",
        "",
        "## Resultat",
        "",
        f"Classification : `{evidence['classification']}`",
        "",
        "FOR-462 verifie le premier lot D50 sans ajouter de faux support. Le tableau de bord genere est deja vert avec 0 `tracked-gap` et 0 `fail`, mais seuls 7 des 12 candidats du lot 1 ont actuellement une ligne materialisee avec preuves existantes.",
        "",
        "Aucune ligne dashboard n'est ajoutee par FOR-462 : le ticket documente l'etat reel et bloque les cinq candidats qui n'ont pas encore leurs preuves ligne par ligne.",
        "",
        "## Compteurs",
        "",
        "| Compteur | Valeur |",
        "|---|---:|",
        f"| Candidats lot 1 | {evidence['lot1CandidateCount']} |",
        f"| Candidats materialises | {evidence['materializedCandidateCount']} |",
        f"| Candidats sans preuve suffisante | {evidence['missingCandidateCount']} |",
        f"| Lignes ajoutees par FOR-462 | {evidence['materializedRowsAddedByFor462']} |",
        f"| Claims support ajoutes par FOR-462 | {evidence['supportClaimsAddedByFor462']} |",
        f"| Claims Skia-comparable ajoutes par FOR-462 | {evidence['skiaComparableClaimsAddedByFor462']} |",
        f"| Dashboard total | {evidence['dashboardCounters']['total']} |",
        f"| Dashboard pass | {evidence['dashboardCounters']['status.pass']} |",
        f"| Dashboard expected-unsupported | {evidence['dashboardCounters']['status.expected-unsupported']} |",
        f"| Dashboard tracked-gap | {evidence['dashboardCounters']['status.tracked-gap']} |",
        f"| Dashboard fail | {evidence['dashboardCounters']['status.fail']} |",
        "",
        "## Candidats deja materialises",
        "",
        "| Inventory id | Row | Statut | Reference | Derivation | Fallback |",
        "|---|---|---|---|---|---|",
    ]
    for item in evidence["materialized"]:
        for row in item["rows"]:
            lines.append(
                "| `{inventory}` | `{row}` | `{status}` | `{reference}` | `{derivation}` | `{fallback}` |".format(
                    inventory=item["inventoryId"],
                    row=row["id"],
                    status=row["status"],
                    reference=row["referenceKind"],
                    derivation=row["derivationTask"],
                    fallback=row["fallbackReason"],
                )
            )
    lines += [
        "",
        "## Candidats bloques",
        "",
        "| Inventory id | Raison |",
        "|---|---|",
    ]
    for item in evidence["missing"]:
        lines.append(
            f"| `{item['inventoryId']}` | preuve ligne par ligne manquante ; pas de promotion dashboard sans reference, CPU, GPU ou refus stable, diff/stat et diagnostics de route |"
        )
    lines += [
        "",
        "## Non-claims",
        "",
        "- FOR-462 ne change pas les statuts dashboard actifs.",
        "- FOR-462 n'ajoute aucun claim de support.",
        "- FOR-462 n'ajoute aucun claim de fidelite Skia-comparable.",
        "- FOR-462 ne modifie pas les seuils, le scoring, la politique de fallback, `PipelineKey`, le code de production ou les sources upstream.",
        "- FOR-462 ne revendique pas broad Skia GM parity.",
        "",
        "## Validation",
        "",
        "```bash",
        *evidence["validationCommands"],
        "```",
        "",
        "## Suite",
        "",
        evidence["nextAction"],
        "",
    ]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def require_report_and_evidence(evidence: dict[str, Any]) -> None:
    write_json(EVIDENCE, evidence)
    write_report(evidence)
    written = load_json(EVIDENCE)
    require(written["classification"] == "lot1-partially-materialized-no-new-support-claims", "classification mismatch")
    require(written["materializedCandidateCount"] == 7, "materialized count must be 7")
    require(written["missingCandidateCount"] == 5, "missing count must be 5")
    require([item["inventoryId"] for item in written["missing"]] == EXPECTED_MISSING, "missing candidate order mismatch")
    report = REPORT.read_text(encoding="utf-8")
    for required in (
        "0 `tracked-gap` et 0 `fail`",
        "Aucune ligne dashboard n'est ajoutee par FOR-462",
        "FOR-462 n'ajoute aucun claim de support",
        "broad Skia GM parity",
        "preuve ligne par ligne manquante",
    ):
        require(required in report, f"report missing: {required}")


def main() -> None:
    require_scope()
    evidence = build_evidence()
    require_report_and_evidence(evidence)
    require_scope()
    print(
        "FOR-462 validation passed: "
        f"materialized={evidence['materializedCandidateCount']} "
        f"missing={evidence['missingCandidateCount']} "
        f"dashboardTotal={evidence['dashboardCounters']['total']}"
    )


if __name__ == "__main__":
    main()
