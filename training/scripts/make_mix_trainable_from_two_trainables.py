#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import random
import shutil
from pathlib import Path
from typing import Any


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, obj: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    data = json.dumps(obj, ensure_ascii=False, indent=2)
    tmp.write_text(data + "\n", encoding="utf-8")
    tmp.replace(path)


def _ensure_empty_output_dir(out_dir: Path) -> None:
    if out_dir.exists():
        if any(out_dir.iterdir()):
            raise SystemExit(
                f"Output directory already exists and is not empty: {out_dir} (delete/rename it or choose a different --out_dir)"
            )
    else:
        out_dir.mkdir(parents=True, exist_ok=False)


def _require_trainable_layout(root: Path) -> tuple[Path, Path]:
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
    out: list[Path] = []
    for p in images_dir.iterdir():
        if not p.is_file():
            continue
        # MacOS / tooling: dotfiles ignorieren (keine stillen Dataset-Samples).
        if p.name.startswith("."):
            continue
        out.append(p)
    return sorted(out, key=lambda x: x.name)


def _list_label_files(labels_dir: Path) -> list[Path]:
    return sorted(labels_dir.glob("*.json"), key=lambda p: p.name)


def _assert_strict_pairing(*, images_dir: Path, labels_dir: Path, dataset_name: str) -> list[tuple[str, Path]]:
    images = _list_image_files(images_dir)
    labels = _list_label_files(labels_dir)

    image_by_stem: dict[str, str] = {}
    for ip in images:
        stem = ip.stem
        if stem in image_by_stem:
            raise SystemExit(
                f"FATAL: {dataset_name}: duplicate image stem '{stem}' (at least: {image_by_stem[stem]!r} and {ip.name!r})"
            )
        image_by_stem[stem] = ip.name

    label_by_stem: dict[str, Path] = {}
    for lp in labels:
        stem = lp.stem
        if stem in label_by_stem:
            raise SystemExit(
                f"FATAL: {dataset_name}: duplicate label stem '{stem}' (at least: {label_by_stem[stem].name!r} and {lp.name!r})"
            )
        label_by_stem[stem] = lp

    missing_labels = sorted([s for s in image_by_stem.keys() if s not in label_by_stem])
    missing_images = sorted([s for s in label_by_stem.keys() if s not in image_by_stem])
    if missing_labels or missing_images:
        raise SystemExit(
            "FATAL: strict pairing violation in {ds}: "
            "images_without_label={mw} labels_without_image={lw}".format(
                ds=dataset_name, mw=missing_labels, lw=missing_images
            )
        )

    # Additionally: the label must internally point to exactly this image.
    pairs: list[tuple[str, Path]] = []
    for stem in sorted(image_by_stem.keys()):
        image_name = image_by_stem[stem]
        lp = label_by_stem[stem]
        label = _read_json(lp)
        img_field = label.get("image", None)
        if img_field != image_name:
            raise SystemExit(
                f"FATAL: {dataset_name}: label-image mismatch: {lp} has image={img_field!r}, expected {image_name!r}"
            )
        pairs.append((image_name, lp))
    return pairs


def _copy_with_prefix(
    *,
    src_root: Path,
    prefix: str,
    out_images: Path,
    out_labels: Path,
    dataset_name: str,
) -> list[str]:
    images_dir, labels_dir = _require_trainable_layout(src_root)
    pairs = _assert_strict_pairing(images_dir=images_dir, labels_dir=labels_dir, dataset_name=dataset_name)

    out_names: list[str] = []
    for image_name, label_path in pairs:
        src_img = images_dir / image_name
        if not src_img.is_file():
            # This should already be prevented by the pairing check.
            raise SystemExit(f"FATAL: {dataset_name}: image missing unexpectedly: {src_img}")

        dst_img_name = f"{prefix}{image_name}"
        dst_label_name = f"{prefix}{label_path.name}"

        if Path(dst_img_name).stem != Path(dst_label_name).stem:
            raise SystemExit(
                f"FATAL: internal mismatch after prefixing: image={dst_img_name!r} label={dst_label_name!r}"
            )

        dst_img_path = out_images / dst_img_name
        dst_label_path = out_labels / dst_label_name
        if dst_img_path.exists() or dst_label_path.exists():
            raise SystemExit(
                f"FATAL: output collision for {dataset_name}: {dst_img_path.name} / {dst_label_path.name} already exists"
            )

        shutil.copy2(src_img, dst_img_path)

        label = _read_json(label_path)
        label["image"] = dst_img_name
        _write_json(dst_label_path, label)
        out_names.append(dst_img_name)

    return out_names


def _write_splits(*, out_dir: Path, image_names: list[str], val_ratio: float) -> tuple[int, int]:
    names = sorted(list(image_names))
    random.Random(0).shuffle(names)

    total = len(names)
    if total == 0:
        val_count = 0
    else:
        # Round to nearest int to keep ratios stable across dataset growth.
        val_count = int(round(float(total) * float(val_ratio)))
        val_count = max(1, min(total, val_count))

    val = names[:val_count]
    train = names[val_count:]

    (out_dir / "split_val.txt").write_text("".join([n + "\n" for n in val]), encoding="utf-8")
    (out_dir / "split_train.txt").write_text("".join([n + "\n" for n in train]), encoding="utf-8")
    return len(val), len(train)


def _main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(
        description="Merge two DocQuad trainable datasets deterministically (prefix images+labels, strict pairing, deterministic split)."
    )
    p.add_argument("--a_dir", type=Path, required=True, help="Trainable dataset A (e.g. docquad_uvdoc_all_trainable)")
    p.add_argument("--b_dir", type=Path, required=True, help="Trainable dataset B (e.g. docquad_my_data_trainable)")
    p.add_argument("--out_dir", type=Path, required=True, help="Output trainable dataset directory")
    p.add_argument(
        "--val_ratio",
        type=float,
        default=0.10,
        help="Validation split ratio (default: 0.10). Deterministic shuffle with seed=0.",
    )
    args = p.parse_args(argv)

    if not (0.0 < float(args.val_ratio) < 1.0):
        raise SystemExit(f"FATAL: --val_ratio must be between 0 and 1 (exclusive), got: {args.val_ratio}")

    out_dir: Path = args.out_dir
    _ensure_empty_output_dir(out_dir)
    out_images = out_dir / "images"
    out_labels = out_dir / "labels"
    out_images.mkdir(parents=True, exist_ok=True)
    out_labels.mkdir(parents=True, exist_ok=True)

    created: list[str] = []
    created += _copy_with_prefix(
        src_root=args.a_dir,
        prefix="a_",
        out_images=out_images,
        out_labels=out_labels,
        dataset_name="A",
    )
    created += _copy_with_prefix(
        src_root=args.b_dir,
        prefix="b_",
        out_images=out_images,
        out_labels=out_labels,
        dataset_name="B",
    )

    # Final pairing check on output (should always pass).
    _assert_strict_pairing(images_dir=out_images, labels_dir=out_labels, dataset_name="OUT")

    val_count, train_count = _write_splits(out_dir=out_dir, image_names=created, val_ratio=float(args.val_ratio))

    print(f"[docquad] wrote mix trainable: {out_dir}")
    print(
        f"[docquad] samples: {len(created)} | val_ratio={float(args.val_ratio):.6f} | val={val_count} | train={train_count} | seed=0",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(_main(sys.argv[1:]))
