#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import math
import shutil
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    from PIL import Image
except ImportError:
    Image = None


REORDER_METHOD_V1 = "centroid_angle_rotate_tl_minsum_v1"


def _get_image_dimensions(image_path: Path) -> tuple[int, int] | None:
    """Read image dimensions using PIL.
    
    Returns:
        (width, height) or None if unable to read
    """
    if Image is None:
        return None
    try:
        with Image.open(image_path) as img:
            return img.size  # (width, height)
    except Exception:
        return None


def _load_jsonl_labels(jsonl_path: Path, images_dir: Path) -> list[dict[str, Any]]:
    """Load labels from JSONL file and enrich with image dimensions.
    
    JSONL format: {"image": "filename.jpg", "corners": [[x,y], ...]}
    
    Returns:
        List of label dicts with image, width, height, corners_px fields
    """
    labels = []
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            
            image_name = obj.get("image")
            corners = obj.get("corners")
            
            if not image_name or not corners:
                continue
            
            # Get image dimensions
            image_path = images_dir / image_name
            dims = _get_image_dimensions(image_path)
            if dims is None:
                continue
            
            width, height = dims
            
            labels.append({
                "image": image_name,
                "width": width,
                "height": height,
                "corners_px": corners,
            })
    
    return labels


@dataclass(frozen=True)
class Point:
    x: float
    y: float


def _is_number(x: Any) -> bool:
    # `bool` is a subclass of `int`; exclude it explicitly.
    return isinstance(x, (int, float)) and not isinstance(x, bool)


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    data = json.dumps(obj, ensure_ascii=False, indent=2)
    tmp.write_text(data + "\n", encoding="utf-8")
    tmp.replace(path)


def _ensure_empty_output_dir(out_dir: Path) -> None:
    if out_dir.exists():
        # "Do not overwrite": if the directory already contains content, abort.
        if any(out_dir.iterdir()):
            raise SystemExit(
                f"Output directory already exists and is not empty: {out_dir} (delete/rename it or choose a different --out_dir)"
            )
    else:
        out_dir.mkdir(parents=True, exist_ok=False)


def _orient(a: Point, b: Point, c: Point) -> float:
    # Cross product (b-a) x (c-a)
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)


def _on_segment(a: Point, b: Point, p: Point, eps: float = 1e-9) -> bool:
    if abs(_orient(a, b, p)) > eps:
        return False
    return (
        min(a.x, b.x) - eps <= p.x <= max(a.x, b.x) + eps
        and min(a.y, b.y) - eps <= p.y <= max(a.y, b.y) + eps
    )


def _segments_intersect(a1: Point, a2: Point, b1: Point, b2: Point, eps: float = 1e-9) -> bool:
    # Standard segment intersection including collinearity; endpoints count as intersection.
    o1 = _orient(a1, a2, b1)
    o2 = _orient(a1, a2, b2)
    o3 = _orient(b1, b2, a1)
    o4 = _orient(b1, b2, a2)

    def _sign(v: float) -> int:
        if v > eps:
            return 1
        if v < -eps:
            return -1
        return 0

    s1, s2, s3, s4 = _sign(o1), _sign(o2), _sign(o3), _sign(o4)

    if s1 == 0 and _on_segment(a1, a2, b1, eps=eps):
        return True
    if s2 == 0 and _on_segment(a1, a2, b2, eps=eps):
        return True
    if s3 == 0 and _on_segment(b1, b2, a1, eps=eps):
        return True
    if s4 == 0 and _on_segment(b1, b2, a2, eps=eps):
        return True

    return (s1 * s2 < 0) and (s3 * s4 < 0)


def _has_self_intersection_quad(pts: list[Point]) -> bool:
    # Quad edges: (0-1),(1-2),(2-3),(3-0). Only check the non-adjacent pairs.
    # Adjacent edges share endpoints and are not considered self-intersections.
    a0, a1, a2, a3 = pts
    # (0-1) with (2-3)
    if _segments_intersect(a0, a1, a2, a3):
        return True
    # (1-2) with (3-0)
    if _segments_intersect(a1, a2, a3, a0):
        return True
    return False


def _is_convex_quad(pts: list[Point], eps: float = 1e-9) -> tuple[bool, bool]:
    """(is_convex, is_degenerate).

    Degenerate: at least one angle/edge cross product is ~0.
    """

    signs: list[int] = []
    degenerate = False
    n = len(pts)
    for i in range(n):
        a = pts[i]
        b = pts[(i + 1) % n]
        c = pts[(i + 2) % n]
        cross = _orient(a, b, c)
        if abs(cross) <= eps:
            degenerate = True
            continue
        signs.append(1 if cross > 0 else -1)
    if not signs:
        return False, True
    is_convex = all(s == signs[0] for s in signs)
    return is_convex, degenerate


def _polygon_area_signed(pts: list[Point]) -> float:
    # Shoelace (signed)
    s = 0.0
    n = len(pts)
    for i in range(n):
        j = (i + 1) % n
        s += pts[i].x * pts[j].y - pts[j].x * pts[i].y
    return 0.5 * s


def _canonicalize_indices_v1(pts: list[Point]) -> list[int]:
    """Deterministic canonicalization (FIX per spec).

    Steps:
    1) centroid = mean(p)
    2) angle = atan2(y-cy, x-cx)
    3) sort by angle ascending (deterministic tie-break via index)
    4) rotate so TL comes first (min(x+y); deterministic tie-break via rotation index)
    """

    cx = sum(p.x for p in pts) / 4.0
    cy = sum(p.y for p in pts) / 4.0

    def key(i: int) -> tuple[float, int]:
        p = pts[i]
        ang = math.atan2(p.y - cy, p.x - cx)
        return ang, i

    ordered = sorted(range(4), key=key)

    # TL = min(x+y). For determinism: tie-break by position in `ordered`.
    sums = [pts[i].x + pts[i].y for i in ordered]
    tl_pos = min(range(4), key=lambda k: (sums[k], k))
    return ordered[tl_pos:] + ordered[:tl_pos]


def _parse_label(label_path: Path) -> tuple[dict[str, Any] | None, list[str]]:
    reasons: list[str] = []
    try:
        obj = _read_json(label_path)
    except Exception:
        return None, ["invalid_json"]

    if not isinstance(obj, dict):
        return None, ["invalid_schema"]

    for k in ("image", "width", "height", "corners_px"):
        if k not in obj:
            reasons.append(f"missing_key_{k}")

    if reasons:
        return None, reasons

    image = obj["image"]
    width = obj["width"]
    height = obj["height"]
    corners = obj["corners_px"]

    if not isinstance(image, str) or not image:
        reasons.append("image_invalid")
    if not isinstance(width, int) or width <= 0:
        reasons.append("width_invalid")
    if not isinstance(height, int) or height <= 0:
        reasons.append("height_invalid")
    if not isinstance(corners, list):
        reasons.append("corners_px_not_list")
        return None, reasons
    if len(corners) != 4:
        reasons.append("corners_not_4")
        return None, reasons

    # corners structure + numeric
    parsed: list[list[float]] = []
    for idx, p in enumerate(corners):
        if not (isinstance(p, (list, tuple)) and len(p) == 2):
            reasons.append(f"corner_{idx}_not_pair")
            return None, reasons
        x, y = p[0], p[1]
        if not (_is_number(x) and _is_number(y)):
            reasons.append(f"corner_{idx}_non_numeric")
            return None, reasons
        parsed.append([float(x), float(y)])

    out = {
        "image": image,
        "width": int(width),
        "height": int(height),
        # Raw corners (numeric) kept unchanged. No coordinate changes; only a permutation later.
        "corners_px_raw": parsed,
    }
    return out, reasons


def make_trainable(*, in_labels: Path, in_images: Path, out_dir: Path) -> dict[str, Any]:
    _ensure_empty_output_dir(out_dir)
    out_images = out_dir / "images"
    out_labels = out_dir / "labels"
    out_images.mkdir(parents=True, exist_ok=True)
    out_labels.mkdir(parents=True, exist_ok=True)

    report: dict[str, Any] = {
        "total_labels": 0,
        "exported": 0,
        "invalid": 0,
        "invalid_by_reason": {},
        "reordered": 0,
        "samples": {},
    }

    label_paths = sorted(p for p in in_labels.glob("*.json") if p.is_file())
    for lp in label_paths:
        report["total_labels"] += 1

        parsed, parse_reasons = _parse_label(lp)
        if parsed is None:
            # No reliable image name available; use the label filename as the key.
            key = lp.name
            sample = {
                "status": "invalid",
                "reorder_applied": False,
                "reasons": list(parse_reasons),
                "width": None,
                "height": None,
            }
            report["samples"][key] = sample
            for r in parse_reasons:
                report["invalid_by_reason"][r] = int(report["invalid_by_reason"].get(r, 0)) + 1
            report["invalid"] += 1
            continue

        image = str(parsed["image"])
        width = int(parsed["width"])
        height = int(parsed["height"])
        raw = parsed["corners_px_raw"]
        pts = [Point(x=float(p[0]), y=float(p[1])) for p in raw]

        reasons: list[str] = []

        # Image existence
        img_src = in_images / image
        if not img_src.is_file():
            reasons.append("missing_image_file")

        # Bounds check
        for i, p in enumerate(pts):
            if not (0.0 <= p.x <= float(width - 1) and 0.0 <= p.y <= float(height - 1)):
                reasons.append("bounds_error")
                break

        # Canonicalize (permutation)
        canon_idx = _canonicalize_indices_v1(pts)
        canon_pts = [pts[i] for i in canon_idx]

        # Geometry validation
        area = _polygon_area_signed(canon_pts)
        if not (area > 0.0):
            reasons.append("area_non_positive")
        else:
            area_frac = area / (float(width) * float(height))
            if area_frac < 0.05:
                reasons.append("area_fraction_lt_0.05")

        if _has_self_intersection_quad(canon_pts):
            reasons.append("self_intersection")

        is_convex, is_degenerate = _is_convex_quad(canon_pts)
        if is_degenerate:
            reasons.append("degenerate")
        if not is_convex:
            reasons.append("not_convex")

        reorder_applied = canon_idx != [0, 1, 2, 3]

        if reasons:
            report["samples"][image] = {
                "status": "invalid",
                "reorder_applied": bool(reorder_applied),
                "reasons": list(dict.fromkeys(reasons)),
                "width": width,
                "height": height,
            }
            for r in dict.fromkeys(reasons):
                report["invalid_by_reason"][r] = int(report["invalid_by_reason"].get(r, 0)) + 1
            report["invalid"] += 1
            continue

        # Export (copy + label)
        img_dst = out_images / image
        shutil.copy2(img_src, img_dst)

        out_label = {
            "image": image,
            "width": width,
            "height": height,
            "corners_px_raw": raw,
            "corners_px": [[canon_pts[i].x, canon_pts[i].y] for i in range(4)],
            "reorder_applied": bool(reorder_applied),
            "reorder_method": REORDER_METHOD_V1,
        }
        out_label_path = out_labels / (Path(image).stem + ".json")
        _write_json(out_label_path, out_label)

        report["samples"][image] = {
            "status": "exported",
            "reorder_applied": bool(reorder_applied),
            "reasons": [],
            "width": width,
            "height": height,
        }
        report["exported"] += 1
        if reorder_applied:
            report["reordered"] += 1

    # Report schreiben
    _write_json(out_dir / "report.json", report)
    return report


def make_trainable_from_jsonl(*, in_labels_jsonl: Path, in_images: Path, out_dir: Path) -> dict[str, Any]:
    """Make trainable dataset from JSONL labels file.
    
    JSONL format: {"image": "filename.jpg", "corners": [[x,y], ...]}
    Image dimensions are read from the actual image files.
    """
    if Image is None:
        raise SystemExit("Pillow is required for JSONL mode. Install with: pip install Pillow")
    
    _ensure_empty_output_dir(out_dir)
    out_images = out_dir / "images"
    out_labels = out_dir / "labels"
    out_images.mkdir(parents=True, exist_ok=True)
    out_labels.mkdir(parents=True, exist_ok=True)

    report: dict[str, Any] = {
        "total_labels": 0,
        "exported": 0,
        "invalid": 0,
        "invalid_by_reason": {},
        "reordered": 0,
        "samples": {},
    }

    # Load JSONL labels
    labels = _load_jsonl_labels(in_labels_jsonl, in_images)
    
    for label_data in labels:
        report["total_labels"] += 1
        
        image = str(label_data["image"])
        width = int(label_data["width"])
        height = int(label_data["height"])
        raw = label_data["corners_px"]
        pts = [Point(x=float(p[0]), y=float(p[1])) for p in raw]

        reasons: list[str] = []

        # Image existence
        img_src = in_images / image
        if not img_src.is_file():
            reasons.append("missing_image_file")

        # Bounds check
        for i, p in enumerate(pts):
            if not (0.0 <= p.x <= float(width - 1) and 0.0 <= p.y <= float(height - 1)):
                reasons.append("bounds_error")
                break

        # Canonicalize (permutation)
        canon_idx = _canonicalize_indices_v1(pts)
        canon_pts = [pts[i] for i in canon_idx]

        # Geometry validation
        area = _polygon_area_signed(canon_pts)
        if not (area > 0.0):
            reasons.append("area_non_positive")
        else:
            area_frac = area / (float(width) * float(height))
            if area_frac < 0.05:
                reasons.append("area_fraction_lt_0.05")

        if _has_self_intersection_quad(canon_pts):
            reasons.append("self_intersection")

        is_convex, is_degenerate = _is_convex_quad(canon_pts)
        if is_degenerate:
            reasons.append("degenerate")
        if not is_convex:
            reasons.append("not_convex")

        reorder_applied = canon_idx != [0, 1, 2, 3]

        if reasons:
            report["samples"][image] = {
                "status": "invalid",
                "reorder_applied": bool(reorder_applied),
                "reasons": list(dict.fromkeys(reasons)),
                "width": width,
                "height": height,
            }
            for r in dict.fromkeys(reasons):
                report["invalid_by_reason"][r] = int(report["invalid_by_reason"].get(r, 0)) + 1
            report["invalid"] += 1
            continue

        # Export (copy + label)
        img_dst = out_images / image
        shutil.copy2(img_src, img_dst)

        out_label = {
            "image": image,
            "width": width,
            "height": height,
            "corners_px_raw": raw,
            "corners_px": [[canon_pts[i].x, canon_pts[i].y] for i in range(4)],
            "reorder_applied": bool(reorder_applied),
            "reorder_method": REORDER_METHOD_V1,
        }
        out_label_path = out_labels / (Path(image).stem + ".json")
        _write_json(out_label_path, out_label)

        report["samples"][image] = {
            "status": "exported",
            "reorder_applied": bool(reorder_applied),
            "reasons": [],
            "width": width,
            "height": height,
        }
        report["exported"] += 1
        if reorder_applied:
            report["reordered"] += 1

    # Report schreiben
    _write_json(out_dir / "report.json", report)
    return report


def main() -> None:
    p = argparse.ArgumentParser(
        description="Make trainable DocQuadNet-256 dataset from converted labels",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # From per-image JSON labels (UVDoc-style):
  python3 make_trainable_from_converted.py \\
      --in_labels training/data/docquad_uvdoc_all_converted/labels \\
      --in_images training/data/docquad_uvdoc_all_converted/images \\
      --out_dir training/data/docquad_uvdoc_all_trainable

  # From JSONL labels (SmartDoc-style):
  python3 make_trainable_from_converted.py \\
      --in_labels_jsonl training/labels/smartdoc_train_sub.jsonl \\
      --in_images training/data/smartdoc \\
      --out_dir training/data/smartdoc_train_sub_trainable
""",
    )
    p.add_argument(
        "--in_labels",
        default=None,
        help="Input labels directory (converted per-image JSON labels)",
    )
    p.add_argument(
        "--in_labels_jsonl",
        default=None,
        help="Input JSONL labels file (SmartDoc-style: {image, corners})",
    )
    p.add_argument(
        "--in_images",
        default=None,
        help="Input images directory",
    )
    p.add_argument(
        "--out_dir",
        default=None,
        help="Output directory to create (must not exist or must be empty)",
    )
    args = p.parse_args()

    # Validate arguments
    if args.in_labels_jsonl and args.in_labels:
        raise SystemExit("Cannot specify both --in_labels and --in_labels_jsonl")
    
    if not args.in_labels_jsonl and not args.in_labels:
        raise SystemExit("Must specify either --in_labels (directory) or --in_labels_jsonl (file)")
    
    if not args.in_images:
        raise SystemExit("Must specify --in_images")
    
    if not args.out_dir:
        raise SystemExit("Must specify --out_dir")

    in_images = Path(args.in_images)
    out_dir = Path(args.out_dir)

    if not in_images.is_dir():
        raise SystemExit(f"in_images is not a directory: {in_images}")

    if args.in_labels_jsonl:
        # JSONL mode (SmartDoc-style)
        in_labels_jsonl = Path(args.in_labels_jsonl)
        if not in_labels_jsonl.is_file():
            raise SystemExit(f"in_labels_jsonl is not a file: {in_labels_jsonl}")
        report = make_trainable_from_jsonl(in_labels_jsonl=in_labels_jsonl, in_images=in_images, out_dir=out_dir)
    else:
        # Per-image JSON mode (UVDoc-style)
        in_labels = Path(args.in_labels)
        if not in_labels.is_dir():
            raise SystemExit(f"in_labels is not a directory: {in_labels}")
        report = make_trainable(in_labels=in_labels, in_images=in_images, out_dir=out_dir)

    # Kurze CLI-Zusammenfassung (deterministisch):
    inv = int(report["invalid"])
    exp = int(report["exported"])
    total = int(report["total_labels"])
    print(f"total_labels={total} exported={exp} invalid={inv} reordered={int(report['reordered'])}")

    # Top-3 invalid reasons
    inv_by = report.get("invalid_by_reason", {})
    top = sorted(inv_by.items(), key=lambda kv: (-int(kv[1]), kv[0]))[:3]
    if top:
        print("top_invalid_reasons=" + ", ".join([f"{k}:{v}" for k, v in top]))


if __name__ == "__main__":
    main()
