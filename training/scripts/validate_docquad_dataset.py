#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

from PIL import Image


@dataclass(frozen=True)
class SampleReport:
    status: str  # "ok" | "invalid"
    reasons: list[str]
    width: int | None
    height: int | None


def _is_number(x: Any) -> bool:
    return isinstance(x, (int, float)) and not (isinstance(x, float) and (math.isnan(x) or math.isinf(x)))


def _load_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        raise ValueError(f"failed to read/parse json: {path} ({e})")


def _extract_corners_px(label: dict[str, Any]) -> list[tuple[float, float]]:
    corners = label.get("corners_px", None)
    if corners is None:
        raise ValueError("missing_field:corners_px")

    if not isinstance(corners, list) or len(corners) != 4:
        raise ValueError("corners_not_4")

    out: list[tuple[float, float]] = []
    for p in corners:
        if not isinstance(p, list) or len(p) != 2:
            raise ValueError("corner_not_pair")
        x, y = p[0], p[1]
        if not _is_number(x) or not _is_number(y):
            raise ValueError("corner_not_numeric")
        out.append((float(x), float(y)))
    return out


def _bounds_ok(pts: list[tuple[float, float]], w: int, h: int) -> bool:
    for x, y in pts:
        if x < 0.0 or x > float(w - 1) or y < 0.0 or y > float(h - 1):
            return False
    return True


def _shoelace_area(pts: list[tuple[float, float]]) -> float:
    # Positive scalar area (abs), independent of CW/CCW.
    s = 0.0
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        s += x1 * y2 - y1 * x2
    return abs(s) * 0.5


def _cross(ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> float:
    # Cross product of AB x AC
    return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


def _is_convex_quad(pts: list[tuple[float, float]], eps: float = 1e-9) -> bool:
    # Convex if all non-zero cross products have the same sign.
    signs: list[int] = []
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        cx, cy = pts[(i + 2) % n]
        z = _cross(ax, ay, bx, by, cx, cy)
        if abs(z) <= eps:
            continue
        signs.append(1 if z > 0.0 else -1)
    if not signs:
        return False
    return all(s == signs[0] for s in signs)


def _on_segment(ax: float, ay: float, bx: float, by: float, px: float, py: float, eps: float) -> bool:
    if min(ax, bx) - eps <= px <= max(ax, bx) + eps and min(ay, by) - eps <= py <= max(ay, by) + eps:
        # collinear check
        return abs(_cross(ax, ay, bx, by, px, py)) <= eps
    return False


def _segments_intersect(a: tuple[float, float], b: tuple[float, float], c: tuple[float, float], d: tuple[float, float], eps: float = 1e-9) -> bool:
    ax, ay = a
    bx, by = b
    cx, cy = c
    dx, dy = d

    o1 = _cross(ax, ay, bx, by, cx, cy)
    o2 = _cross(ax, ay, bx, by, dx, dy)
    o3 = _cross(cx, cy, dx, dy, ax, ay)
    o4 = _cross(cx, cy, dx, dy, bx, by)

    def _sgn(z: float) -> int:
        if abs(z) <= eps:
            return 0
        return 1 if z > 0.0 else -1

    s1, s2, s3, s4 = _sgn(o1), _sgn(o2), _sgn(o3), _sgn(o4)

    # General case
    if s1 != 0 and s2 != 0 and s3 != 0 and s4 != 0:
        return (s1 != s2) and (s3 != s4)

    # Collinear / touching cases
    if s1 == 0 and _on_segment(ax, ay, bx, by, cx, cy, eps):
        return True
    if s2 == 0 and _on_segment(ax, ay, bx, by, dx, dy, eps):
        return True
    if s3 == 0 and _on_segment(cx, cy, dx, dy, ax, ay, eps):
        return True
    if s4 == 0 and _on_segment(cx, cy, dx, dy, bx, by, eps):
        return True

    return False


def _is_self_intersecting_quad(pts: list[tuple[float, float]]) -> bool:
    # Quad edges: (0-1),(1-2),(2-3),(3-0)
    # Non-adjacent pairs: (0-1) with (2-3), and (1-2) with (3-0)
    e01 = (pts[0], pts[1])
    e12 = (pts[1], pts[2])
    e23 = (pts[2], pts[3])
    e30 = (pts[3], pts[0])

    if _segments_intersect(e01[0], e01[1], e23[0], e23[1]):
        return True
    if _segments_intersect(e12[0], e12[1], e30[0], e30[1]):
        return True
    return False


def _require_dataset_layout(root: Path) -> tuple[Path, Path]:
    if not root.exists():
        raise SystemExit(f"FATAL: dataset root does not exist: {root}")
    if not root.is_dir():
        raise SystemExit(f"FATAL: dataset root is not a directory: {root}")

    labels_dir = root / "labels"
    images_dir = root / "images"
    if not labels_dir.is_dir():
        raise SystemExit(f"FATAL: labels dir missing: {labels_dir}")
    if not images_dir.is_dir():
        raise SystemExit(f"FATAL: images dir missing: {images_dir}")
    return labels_dir, images_dir


def validate_dataset(root: Path) -> dict[str, Any]:
    labels_dir, images_dir = _require_dataset_layout(root)
    label_paths = sorted(labels_dir.glob("*.json"), key=lambda p: p.name)

    samples: dict[str, Any] = {}
    errors_by_type: dict[str, int] = {}

    total = 0
    ok = 0
    invalid = 0

    for lp in label_paths:
        total += 1
        reasons: list[str] = []
        width: int | None = None
        height: int | None = None
        status = "ok"

        image_name_for_key = lp.stem  # fallback

        try:
            label = _load_json(lp)
        except Exception:
            status = "invalid"
            reasons.append("label_json_parse_error")
            # key remains lp.stem
            samples[image_name_for_key] = SampleReport(status=status, reasons=reasons, width=None, height=None).__dict__
            errors_by_type["label_json_parse_error"] = errors_by_type.get("label_json_parse_error", 0) + 1
            invalid += 1
            continue

        # (a) label already exists (we iterate over files) + image field matches filename
        img_field = label.get("image", None)
        if not isinstance(img_field, str) or not img_field:
            status = "invalid"
            reasons.append("missing_or_invalid_image_field")
            img_field = None
        else:
            image_name_for_key = img_field
            # JSON label file is <stem>.json; image field should have the same stem.
            if Path(img_field).stem != lp.stem:
                status = "invalid"
                reasons.append("image_field_mismatch")

        # (b) Image exists
        img_path = images_dir / (img_field if isinstance(img_field, str) else "")
        if not img_path.is_file():
            status = "invalid"
            reasons.append("image_missing")

        # (c) width/height in label == Pillow image size
        w = label.get("width", None)
        h = label.get("height", None)
        if not isinstance(w, int) or not isinstance(h, int) or w <= 0 or h <= 0:
            status = "invalid"
            reasons.append("invalid_width_height")
        else:
            width, height = int(w), int(h)

        if img_path.is_file():
            try:
                with Image.open(img_path) as im:
                    iw, ih = im.size
            except Exception:
                status = "invalid"
                reasons.append("image_open_error")
                iw, ih = None, None
            if iw is not None and ih is not None and width is not None and height is not None:
                if int(iw) != int(width) or int(ih) != int(height):
                    status = "invalid"
                    reasons.append("image_size_mismatch")

        # corners & geometry checks only if width/height are valid
        pts: list[tuple[float, float]] | None = None
        if width is not None and height is not None:
            try:
                pts = _extract_corners_px(label)
            except ValueError as e:
                status = "invalid"
                reasons.append(str(e))
                pts = None

        if pts is not None and width is not None and height is not None:
            # (d) bounds
            if not _bounds_ok(pts, width, height):
                status = "invalid"
                reasons.append("bounds_error")

            # (e) self-intersection
            if _is_self_intersecting_quad(pts):
                status = "invalid"
                reasons.append("self_intersection")

            # (f) convexity
            if not _is_convex_quad(pts):
                status = "invalid"
                reasons.append("not_convex")

            # (g) area_fraction
            area = _shoelace_area(pts)
            img_area = float(width) * float(height)
            frac = (area / img_area) if img_area > 0.0 else 0.0
            if not (frac >= 0.05):
                status = "invalid"
                reasons.append("area_fraction_lt_0.05")
            if not (area > 0.0):
                status = "invalid"
                reasons.append("area_zero")

        # report
        if status == "ok":
            ok += 1
        else:
            invalid += 1

        # Count error types
        if status != "ok":
            for r in reasons:
                errors_by_type[r] = errors_by_type.get(r, 0) + 1

        # Deterministic: sort reasons (for stable reports)
        reasons_sorted = sorted(reasons)
        samples[image_name_for_key] = SampleReport(status=status, reasons=reasons_sorted, width=width, height=height).__dict__

    return {
        "total_samples": total,
        "ok": ok,
        "invalid": invalid,
        "errors_by_type": dict(sorted(errors_by_type.items(), key=lambda kv: (-kv[1], kv[0]))),
        "samples": samples,
    }


def write_json(path: Path, obj: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def write_split_files(root: Path, valid_image_names: list[str], seed: int = 0) -> tuple[Path, Path]:
    rng = random.Random(int(seed))
    names = list(valid_image_names)
    # Deterministic order:
    names.sort()
    rng.shuffle(names)

    n = len(names)
    n_val = int(n * 0.10)
    n_train = n - n_val

    train = names[:n_train]
    val = names[n_train:]

    train_path = root / "split_train.txt"
    val_path = root / "split_val.txt"
    train_path.write_text("\n".join(train) + ("\n" if train else ""), encoding="utf-8")
    val_path.write_text("\n".join(val) + ("\n" if val else ""), encoding="utf-8")
    return train_path, val_path


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser()
    p.add_argument(
        "--in_dir",
        type=str,
        default="training/data/docquad_uvdoc_all_trainable",
        help="Dataset root with images/ and labels/ (default: training/data/docquad_uvdoc_all_trainable)",
    )
    args = p.parse_args(argv)

    root = Path(args.in_dir)
    report = validate_dataset(root)

    out_report_path = root / "validate_report.json"
    write_json(out_report_path, report)

    # Step 1: create split files using only valid samples
    valid_images = [
        k
        for k, v in report["samples"].items()
        if isinstance(v, dict) and v.get("status") == "ok" and isinstance(k, str) and k
    ]
    write_split_files(root, valid_images, seed=0)

    # Short CLI summary
    print(f"validate_report: {out_report_path}")
    print(f"total_samples={report['total_samples']} ok={report['ok']} invalid={report['invalid']}")
    # Top 3 error types
    top = list(report.get("errors_by_type", {}).items())[:3]
    if top:
        print("top_errors=" + ", ".join([f"{k}:{v}" for k, v in top]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
