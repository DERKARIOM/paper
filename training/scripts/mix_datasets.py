#!/usr/bin/env python3
"""
Mixing script for DocQuadNet Training (V1 / V2).

Creates a new mixed trainable dataset from multiple existing trainable
datasets with controlled mixing strategy.

The script strictly follows the existing data and label format
of the MakeACopy / DocQuadNet project.
"""

from __future__ import annotations

import argparse
import json
import random
import shutil
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


def _read_json(path: Path) -> dict[str, Any]:
    """Read JSON file and return parsed content."""
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, obj: dict[str, Any], indent: int = 2) -> None:
    """Write JSON object to file atomically."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    data = json.dumps(obj, ensure_ascii=False, indent=indent)
    tmp.write_text(data + "\n", encoding="utf-8")
    tmp.replace(path)


def _ensure_empty_output_dir(out_dir: Path, overwrite: bool) -> None:
    """Ensure output directory is empty or create it."""
    if out_dir.exists():
        if any(out_dir.iterdir()):
            if overwrite:
                shutil.rmtree(out_dir)
                out_dir.mkdir(parents=True, exist_ok=False)
            else:
                raise SystemExit(
                    f"Output directory already exists and is not empty: {out_dir}\n"
                    "Use --overwrite to replace it."
                )
    else:
        out_dir.mkdir(parents=True, exist_ok=False)


def _require_trainable_layout(root: Path) -> tuple[Path, Path]:
    """Validate trainable dataset layout and return images/labels directories."""
    if not root.exists() or not root.is_dir():
        raise SystemExit(f"FATAL: trainable root missing/not a directory: {root}")
    images_dir = root / "images"
    labels_dir = root / "labels"
    if not images_dir.is_dir():
        raise SystemExit(f"FATAL: images dir missing: {images_dir}")
    if not labels_dir.is_dir():
        raise SystemExit(f"FATAL: labels dir missing: {labels_dir}")
    return images_dir, labels_dir


def _list_image_files(images_dir: Path) -> list[Path]:
    """List all image files in directory, excluding dotfiles."""
    out: list[Path] = []
    for p in images_dir.iterdir():
        if not p.is_file():
            continue
        if p.name.startswith("."):
            continue
        out.append(p)
    return sorted(out, key=lambda x: x.name)


def _list_label_files(labels_dir: Path) -> list[Path]:
    """List all JSON label files in directory."""
    return sorted(labels_dir.glob("*.json"), key=lambda p: p.name)


def _get_paired_samples(
    images_dir: Path, labels_dir: Path, dataset_name: str
) -> list[tuple[str, Path, Path]]:
    """
    Get list of (image_name, image_path, label_path) tuples with strict pairing validation.
    """
    images = _list_image_files(images_dir)
    labels = _list_label_files(labels_dir)

    image_by_stem: dict[str, tuple[str, Path]] = {}
    for ip in images:
        stem = ip.stem
        if stem in image_by_stem:
            raise SystemExit(
                f"FATAL: {dataset_name}: duplicate image stem '{stem}' "
                f"(at least: {image_by_stem[stem][0]!r} and {ip.name!r})"
            )
        image_by_stem[stem] = (ip.name, ip)

    label_by_stem: dict[str, Path] = {}
    for lp in labels:
        stem = lp.stem
        if stem in label_by_stem:
            raise SystemExit(
                f"FATAL: {dataset_name}: duplicate label stem '{stem}' "
                f"(at least: {label_by_stem[stem].name!r} and {lp.name!r})"
            )
        label_by_stem[stem] = lp

    missing_labels = sorted([s for s in image_by_stem.keys() if s not in label_by_stem])
    missing_images = sorted([s for s in label_by_stem.keys() if s not in image_by_stem])
    if missing_labels or missing_images:
        raise SystemExit(
            f"FATAL: strict pairing violation in {dataset_name}: "
            f"images_without_label={missing_labels[:5]}{'...' if len(missing_labels) > 5 else ''} "
            f"labels_without_image={missing_images[:5]}{'...' if len(missing_images) > 5 else ''}"
        )

    pairs: list[tuple[str, Path, Path]] = []
    for stem in sorted(image_by_stem.keys()):
        image_name, image_path = image_by_stem[stem]
        label_path = label_by_stem[stem]
        label = _read_json(label_path)
        img_field = label.get("image", None)
        if img_field != image_name:
            raise SystemExit(
                f"FATAL: {dataset_name}: label-image mismatch: {label_path} "
                f"has image={img_field!r}, expected {image_name!r}"
            )
        pairs.append((image_name, image_path, label_path))
    return pairs


def _parse_ratio_string(ratio_input: str | list[str]) -> dict[str, float]:
    """
    Parse ratio string like 'uvdoc=0.4 core=0.5 hard=0.1' into dict.
    Accepts either a single string or a list of strings (from nargs='+').
    
    Handles multiple input formats:
    - Single string: "a=0.4 b=0.3 c=0.3"
    - List of strings: ["a=0.4", "b=0.3", "c=0.3"]
    - List with single quoted string: ["a=0.4 b=0.3 c=0.3"]
    """
    ratios: dict[str, float] = {}
    # Handle both single string and list of strings
    if isinstance(ratio_input, list):
        # Flatten: split each element by whitespace and collect all parts
        parts = []
        for item in ratio_input:
            parts.extend(item.split())
    else:
        parts = ratio_input.split()
    for part in parts:
        if "=" not in part:
            raise SystemExit(f"Invalid ratio format: '{part}'. Expected 'name=value'.")
        name, value_str = part.split("=", 1)
        try:
            value = float(value_str)
        except ValueError:
            raise SystemExit(f"Invalid ratio value for '{name}': '{value_str}'")
        if value < 0 or value > 1:
            raise SystemExit(f"Ratio for '{name}' must be between 0 and 1, got {value}")
        ratios[name] = value
    return ratios


def _sample_deterministic(
    samples: list[tuple[str, Path, Path]],
    count: int,
    seed: int,
) -> list[tuple[str, Path, Path]]:
    """
    Deterministically sample 'count' items from samples.
    If count > len(samples), samples are repeated (with shuffling).
    """
    if count <= 0:
        return []
    if len(samples) == 0:
        return []

    rng = random.Random(seed)
    
    if count <= len(samples):
        # Sample without replacement
        shuffled = list(samples)
        rng.shuffle(shuffled)
        return shuffled[:count]
    else:
        # Need to repeat samples
        result: list[tuple[str, Path, Path]] = []
        while len(result) < count:
            shuffled = list(samples)
            rng.shuffle(shuffled)
            result.extend(shuffled)
        return result[:count]


def _copy_samples_with_prefix(
    samples: list[tuple[str, Path, Path]],
    prefix: str,
    out_images: Path,
    out_labels: Path,
    dataset_name: str,
) -> list[str]:
    """
    Copy selected samples to output directory with prefix.
    Returns list of output image names.
    """
    out_names: list[str] = []
    
    for idx, (image_name, image_path, label_path) in enumerate(samples):
        # Create unique name with prefix and index to handle duplicates from oversampling
        base_stem = Path(image_name).stem
        base_ext = Path(image_name).suffix
        
        # Use index to ensure uniqueness when oversampling
        dst_img_name = f"{prefix}{idx:06d}_{image_name}"
        dst_label_name = f"{prefix}{idx:06d}_{label_path.name}"

        dst_img_path = out_images / dst_img_name
        dst_label_path = out_labels / dst_label_name

        if dst_img_path.exists() or dst_label_path.exists():
            raise SystemExit(
                f"FATAL: output collision for {dataset_name}: "
                f"{dst_img_path.name} / {dst_label_path.name} already exists"
            )

        shutil.copy2(image_path, dst_img_path)

        label = _read_json(label_path)
        label["image"] = dst_img_name
        _write_json(dst_label_path, label)
        out_names.append(dst_img_name)

    return out_names


def _write_splits(
    out_dir: Path, image_names: list[str], val_ratio: float, seed: int
) -> tuple[int, int]:
    """Write train/val split files deterministically."""
    names = sorted(list(image_names))
    random.Random(seed).shuffle(names)

    total = len(names)
    if total == 0:
        val_count = 0
    else:
        val_count = int(round(float(total) * float(val_ratio)))
        val_count = max(1, min(total, val_count))

    val = names[:val_count]
    train = names[val_count:]

    (out_dir / "split_val.txt").write_text(
        "".join([n + "\n" for n in val]), encoding="utf-8"
    )
    (out_dir / "split_train.txt").write_text(
        "".join([n + "\n" for n in train]), encoding="utf-8"
    )
    return len(val), len(train)


def _generate_report(
    out_report: Path,
    datasets: dict[str, dict[str, Any]],
    mode: str,
    target_size: int,
    ratios: dict[str, float],
    val_ratio: float,
    seed: int,
    total_samples: int,
    val_count: int,
    train_count: int,
) -> None:
    """Generate JSON report with statistics and reproducibility info."""
    report = {
        "generated_at": datetime.now().isoformat(),
        "mode": mode,
        "target_size": target_size,
        "actual_size": total_samples,
        "val_ratio": val_ratio,
        "val_count": val_count,
        "train_count": train_count,
        "seed": seed,
        "ratios_requested": ratios,
        "datasets": {},
    }

    for name, info in datasets.items():
        report["datasets"][name] = {
            "source_dir": str(info["source_dir"]),
            "total_available": info["total_available"],
            "requested_ratio": info.get("requested_ratio", 0.0),
            "requested_count": info.get("requested_count", 0),
            "actual_count": info["actual_count"],
            "oversampled": info["actual_count"] > info["total_available"],
        }

    _write_json(out_report, report, indent=2)


def _main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(
        description=(
            "Mixing script for DocQuadNet Training.\n"
            "Creates a mixed dataset from multiple trainable datasets."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Example (generic a/b mode):
  python3 training/scripts/mix_datasets.py \\
      --mode ratio \\
      --a_dir training/data/smartdoc_core_trainable \\
      --b_dir training/data/docquad_uvdoc_all_trainable \\
      --ratio "a=0.70 b=0.30" \\
      --target_size 6000 \\
      --out_dir training/data/mix_v1_trainable \\
      --out_report training/reports/mix_v1.json

Example (semantic names):
  python3 training/scripts/mix_datasets.py \\
      --mode ratio \\
      --uvdoc_dir training/data/docquad_uvdoc_all_trainable \\
      --core_dir training/data/docquad_smartdoc_trainable \\
      --ratio "uvdoc=0.4 core=0.6" \\
      --target_size 6000 \\
      --out_dir training/data/docquad_mix_v1_trainable \\
      --out_report training/reports/mix_v1.json

""",
    )

    # Dataset inputs - generic names (a, b)
    p.add_argument(
        "--a_dir",
        type=Path,
        help="Trainable dataset A (generic name 'a' for ratio)",
    )
    p.add_argument(
        "--b_dir",
        type=Path,
        help="Trainable dataset B (generic name 'b' for ratio)",
    )
    # Dataset inputs - semantic names
    p.add_argument(
        "--uvdoc_dir",
        type=Path,
        help="UVDoc trainable dataset directory",
    )
    p.add_argument(
        "--core_dir",
        type=Path,
        help="SmartDoc CORE trainable dataset directory",
    )
    p.add_argument(
        "--hard_dir",
        type=Path,
        help="SmartDoc HARD trainable dataset directory",
    )
    p.add_argument(
        "--extra_dir",
        type=str,
        action="append",
        default=[],
        help="Additional dataset in format 'name=path' (repeatable)",
    )

    # Mode and parameters
    p.add_argument(
        "--mode",
        type=str,
        choices=["ratio"],
        default="ratio",
        help="Mixing mode (default: ratio)",
    )
    p.add_argument(
        "--ratio",
        type=str,
        nargs="+",
        required=True,
        help="Ratio specification, e.g. 'uvdoc=0.4 core=0.5 hard=0.1' or uvdoc=0.4 core=0.5 hard=0.1",
    )
    p.add_argument(
        "--target_size",
        type=int,
        required=True,
        help="Target total number of samples in output dataset",
    )

    # Output
    p.add_argument(
        "--out_dir",
        type=Path,
        required=True,
        help="Output trainable dataset directory",
    )
    p.add_argument(
        "--out_report",
        type=Path,
        required=True,
        help="Output JSON report path",
    )

    # Options
    p.add_argument(
        "--val_ratio",
        type=float,
        default=0.10,
        help="Validation split ratio (default: 0.10)",
    )
    p.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for deterministic sampling (default: 42)",
    )
    p.add_argument(
        "--overwrite",
        action="store_true",
        help="Overwrite existing output directory",
    )

    args = p.parse_args(argv)

    # Validate val_ratio
    if not (0.0 < args.val_ratio < 1.0):
        raise SystemExit(f"FATAL: --val_ratio must be between 0 and 1 (exclusive), got: {args.val_ratio}")

    # Validate target_size
    if args.target_size <= 0:
        raise SystemExit(f"FATAL: --target_size must be positive, got: {args.target_size}")

    # Collect all input datasets
    input_datasets: dict[str, Path] = {}
    
    # Generic names (a, b)
    if args.a_dir:
        input_datasets["a"] = args.a_dir
    if args.b_dir:
        input_datasets["b"] = args.b_dir
    # Semantic names
    if args.uvdoc_dir:
        input_datasets["uvdoc"] = args.uvdoc_dir
    if args.core_dir:
        input_datasets["core"] = args.core_dir
    if args.hard_dir:
        input_datasets["hard"] = args.hard_dir
    
    for extra in args.extra_dir:
        if "=" not in extra:
            raise SystemExit(f"Invalid --extra_dir format: '{extra}'. Expected 'name=path'.")
        name, path_str = extra.split("=", 1)
        if name in input_datasets:
            raise SystemExit(f"Duplicate dataset name: '{name}'")
        input_datasets[name] = Path(path_str)

    if not input_datasets:
        raise SystemExit("FATAL: No input datasets specified. Use --uvdoc_dir, --core_dir, --hard_dir, or --extra_dir.")

    # Parse ratios
    ratios = _parse_ratio_string(args.ratio)

    # Validate that all ratio keys have corresponding datasets
    for name in ratios.keys():
        if name not in input_datasets:
            raise SystemExit(
                f"FATAL: Ratio specified for '{name}' but no dataset provided. "
                f"Available datasets: {list(input_datasets.keys())}"
            )

    # Validate ratio sum
    ratio_sum = sum(ratios.values())
    if abs(ratio_sum - 1.0) > 0.001:
        print(f"WARNING: Ratios sum to {ratio_sum:.4f}, not 1.0. Normalizing...", file=sys.stderr)
        for name in ratios:
            ratios[name] /= ratio_sum

    # Prepare output directory
    _ensure_empty_output_dir(args.out_dir, args.overwrite)
    out_images = args.out_dir / "images"
    out_labels = args.out_dir / "labels"
    out_images.mkdir(parents=True, exist_ok=True)
    out_labels.mkdir(parents=True, exist_ok=True)

    # Load and validate all datasets
    dataset_samples: dict[str, list[tuple[str, Path, Path]]] = {}
    dataset_info: dict[str, dict[str, Any]] = {}

    print(f"[mix] Loading datasets...", flush=True)
    for name, path in input_datasets.items():
        images_dir, labels_dir = _require_trainable_layout(path)
        samples = _get_paired_samples(images_dir, labels_dir, name)
        dataset_samples[name] = samples
        dataset_info[name] = {
            "source_dir": path,
            "total_available": len(samples),
            "actual_count": 0,
        }
        print(f"  [{name}] {len(samples)} samples available", flush=True)

    # Calculate sample counts per dataset based on ratios
    print(f"\n[mix] Mode: {args.mode}", flush=True)
    print(f"[mix] Target size: {args.target_size}", flush=True)
    print(f"[mix] Ratios: {ratios}", flush=True)

    sample_counts: dict[str, int] = {}
    total_allocated = 0

    for name in ratios.keys():
        count = int(round(args.target_size * ratios[name]))
        sample_counts[name] = count
        total_allocated += count
        dataset_info[name]["requested_ratio"] = ratios[name]
        dataset_info[name]["requested_count"] = count

    # Adjust for rounding errors
    if total_allocated != args.target_size:
        diff = args.target_size - total_allocated
        # Add/subtract from the largest dataset
        largest = max(sample_counts.keys(), key=lambda k: sample_counts[k])
        sample_counts[largest] += diff

    print(f"\n[mix] Sample allocation:", flush=True)
    for name, count in sample_counts.items():
        available = dataset_info[name]["total_available"]
        oversample = "OVERSAMPLE" if count > available else ""
        print(f"  [{name}] {count} samples (available: {available}) {oversample}", flush=True)

    # Sample and copy
    all_output_names: list[str] = []
    
    for name, count in sample_counts.items():
        if count == 0:
            continue
        
        samples = dataset_samples[name]
        selected = _sample_deterministic(samples, count, args.seed)
        
        print(f"\n[mix] Copying {len(selected)} samples from {name}...", flush=True)
        output_names = _copy_samples_with_prefix(
            selected,
            prefix=f"{name}_",
            out_images=out_images,
            out_labels=out_labels,
            dataset_name=name,
        )
        all_output_names.extend(output_names)
        dataset_info[name]["actual_count"] = len(output_names)

    # Write splits
    val_count, train_count = _write_splits(
        args.out_dir, all_output_names, args.val_ratio, args.seed
    )

    # Generate report
    args.out_report.parent.mkdir(parents=True, exist_ok=True)
    _generate_report(
        args.out_report,
        dataset_info,
        args.mode,
        args.target_size,
        ratios,
        args.val_ratio,
        args.seed,
        len(all_output_names),
        val_count,
        train_count,
    )

    # Summary
    print(f"\n{'='*60}", flush=True)
    print(f"[mix] SUMMARY", flush=True)
    print(f"{'='*60}", flush=True)
    print(f"Output directory: {args.out_dir}", flush=True)
    print(f"Total samples:    {len(all_output_names)}", flush=True)
    print(f"Train samples:    {train_count}", flush=True)
    print(f"Val samples:      {val_count}", flush=True)
    print(f"Report:           {args.out_report}", flush=True)
    print(f"{'='*60}", flush=True)
    print(f"[mix] Done.", flush=True)

    return 0


if __name__ == "__main__":
    raise SystemExit(_main(sys.argv[1:]))
