#!/usr/bin/env python3
"""Generate a deterministic measured-run comparison from two untouched device CSVs."""

from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
from statistics import median


EXPECTED_TRANSCRIPT = (
    "and so my fellow americans ask not what your country can do for you ask what you can do"
)
CONFIGURATIONS = ("generic", "kleidiai-experimental")
THREADS = (2, 4, 6, 8)
REQUIRED_COLUMNS = {
    "timestamp_utc",
    "git_commit",
    "build_variant",
    "configuration",
    "apk_sha256",
    "whisper_commit",
    "kleidiai_version",
    "model",
    "model_sha256",
    "model_quantization",
    "phase",
    "run_index",
    "threads",
    "thermal_status",
    "kleidiai_integration_enabled",
    "kleidiai_sources_included",
    "kleidiai_kernel_selection_observed",
    "model_eligible_for_kleidiai",
    "selected_compute_path",
    "fallback_reason",
    "success",
    "inference_ms",
    "transcript_sha256",
    "normalized_transcript",
    "error",
}
OUTPUT_COLUMNS = (
    "configuration",
    "threads",
    "measured_runs",
    "failures",
    "median_inference_ms",
    "p95_inference_ms",
    "min_inference_ms",
    "max_inference_ms",
    "max_thermal_status",
    "git_commit",
    "apk_sha256",
    "build_variant",
    "model",
    "model_sha256",
    "model_quantization",
    "whisper_commit",
    "kleidiai_version",
    "kleidiai_integration_enabled",
    "kleidiai_sources_included",
    "kleidiai_kernel_selection_observed",
    "model_eligible_for_kleidiai",
    "selected_compute_path",
    "fallback_reason",
    "transcript_sha256",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--generic", type=Path, required=True)
    parser.add_argument("--experimental", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def load_raw(path: Path, expected_configuration: str) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source)
        missing = REQUIRED_COLUMNS.difference(reader.fieldnames or ())
        if missing:
            raise ValueError(f"{path}: missing columns: {', '.join(sorted(missing))}")
        rows = list(reader)

    expected_rows = len(THREADS) * (2 + 10)
    if len(rows) != expected_rows:
        raise ValueError(f"{path}: expected {expected_rows} raw rows, found {len(rows)}")
    configurations = {row["configuration"] for row in rows}
    if configurations != {expected_configuration}:
        raise ValueError(f"{path}: configuration was {sorted(configurations)}")
    for field in ("git_commit", "apk_sha256", "build_variant", "model", "model_sha256",
                  "model_quantization", "whisper_commit", "kleidiai_version"):
        values = {row[field] for row in rows}
        if len(values) != 1 or not next(iter(values)):
            raise ValueError(f"{path}: {field} must contain one non-empty value")
    return rows


def one_value(rows: list[dict[str, str]], field: str) -> str:
    values = {row[field] for row in rows}
    if len(values) != 1:
        raise ValueError(f"measured rows disagree on {field}: {sorted(values)}")
    return next(iter(values))


def summarize(rows: list[dict[str, str]], configuration: str) -> list[dict[str, str]]:
    measured = [row for row in rows if row["phase"] == "measured"]
    if len(measured) != len(THREADS) * 10:
        raise ValueError(f"{configuration}: expected 40 measured rows, found {len(measured)}")
    if any(row["normalized_transcript"] != EXPECTED_TRANSCRIPT for row in measured):
        raise ValueError(f"{configuration}: a measured transcript failed the canonical correctness gate")
    if len({row["normalized_transcript"] for row in measured}) != 1:
        raise ValueError(f"{configuration}: measured transcripts are inconsistent")

    output: list[dict[str, str]] = []
    for threads in THREADS:
        group = [row for row in measured if int(row["threads"]) == threads]
        if len(group) != 10:
            raise ValueError(f"{configuration}/{threads}: expected 10 measured rows, found {len(group)}")
        successful = [row for row in group if row["success"].lower() == "true"]
        timings = sorted(float(row["inference_ms"]) for row in successful)
        if not timings:
            raise ValueError(f"{configuration}/{threads}: no successful measured timings")
        output.append({
            "configuration": configuration,
            "threads": str(threads),
            "measured_runs": str(len(group)),
            "failures": str(len(group) - len(successful)),
            "median_inference_ms": f"{median(timings):.3f}",
            "p95_inference_ms": f"{timings[math.ceil(0.95 * len(timings)) - 1]:.3f}",
            "min_inference_ms": f"{timings[0]:.3f}",
            "max_inference_ms": f"{timings[-1]:.3f}",
            "max_thermal_status": str(max(int(row["thermal_status"]) for row in group)),
            **{field: one_value(group, field) for field in (
                "git_commit", "apk_sha256", "build_variant", "model", "model_sha256",
                "model_quantization", "whisper_commit", "kleidiai_version",
                "kleidiai_integration_enabled", "kleidiai_sources_included",
                "kleidiai_kernel_selection_observed", "model_eligible_for_kleidiai",
                "selected_compute_path", "fallback_reason", "transcript_sha256",
            )},
        })
    return output


def main() -> None:
    args = parse_args()
    generic = load_raw(args.generic, CONFIGURATIONS[0])
    experimental = load_raw(args.experimental, CONFIGURATIONS[1])
    rows = summarize(generic, CONFIGURATIONS[0]) + summarize(experimental, CONFIGURATIONS[1])
    if len({row["model_sha256"] for row in rows}) != 1:
        raise ValueError("generic and experimental runs used different models")
    if len({row["transcript_sha256"] for row in rows}) != 1:
        raise ValueError("generic and experimental runs produced different normalized transcripts")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(destination, fieldnames=OUTPUT_COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


if __name__ == "__main__":
    main()
