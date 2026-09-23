#!/usr/bin/env python3
"""
Split SmartDoc dataset into CORE and HARD subsets based on document area fraction.

CORE: Documents where area_frac >= threshold (larger documents in frame)
HARD: Documents where area_frac < threshold (smaller documents in frame)

The script preserves the exact JSONL format from input (no fields added/removed).
"""

import argparse
import json
import logging
import shutil
import statistics
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Optional

try:
    from PIL import Image
except ImportError:
    Image = None

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)


def shoelace_area(corners: list[list[float]]) -> float:
    """
    Calculate polygon area using the Shoelace formula.
    
    Args:
        corners: List of [x, y] coordinates (4 corners for a quadrilateral)
    
    Returns:
        Absolute area of the polygon
    """
    n = len(corners)
    if n < 3:
        return 0.0
    
    area = 0.0
    for i in range(n):
        j = (i + 1) % n
        area += corners[i][0] * corners[j][1]
        area -= corners[j][0] * corners[i][1]
    
    return abs(area) / 2.0


def get_image_dimensions(image_path: Path) -> Optional[tuple[int, int]]:
    """
    Read image dimensions from file.
    
    Args:
        image_path: Path to the image file
    
    Returns:
        Tuple of (width, height) or None if unable to read
    """
    if Image is None:
        logger.warning("Pillow not installed, cannot read image dimensions")
        return None
    
    try:
        with Image.open(image_path) as img:
            return img.size  # (width, height)
    except Exception as e:
        logger.debug(f"Failed to read image {image_path}: {e}")
        return None


def validate_corners(corners: list) -> bool:
    """
    Validate that corners is a list of 4 [x, y] coordinate pairs.
    
    Args:
        corners: The corners field from JSONL
    
    Returns:
        True if valid, False otherwise
    """
    if not isinstance(corners, list) or len(corners) != 4:
        return False
    
    for corner in corners:
        if not isinstance(corner, list) or len(corner) != 2:
            return False
        if not all(isinstance(c, (int, float)) for c in corner):
            return False
    
    return True


def copy_file(src: Path, dst: Path) -> bool:
    """
    Copy a file from src to dst.
    
    Args:
        src: Source file path
        dst: Destination file path
    
    Returns:
        True if successful, False otherwise
    """
    try:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        return True
    except Exception as e:
        logger.error(f"Failed to copy {src} to {dst}: {e}")
        return False


def process_dataset(
    images_root: Path,
    labels_jsonl: Path,
    out_images_root: Path,
    out_core_labels: Path,
    out_hard_labels: Path,
    area_frac_thresh: float,
    copy_workers: int,
    overwrite: bool,
) -> None:
    """
    Process the SmartDoc dataset and split into CORE and HARD subsets.
    """
    # Validate inputs
    if not images_root.is_dir():
        logger.error(f"Images root directory does not exist: {images_root}")
        sys.exit(1)
    
    if not labels_jsonl.is_file():
        logger.error(f"Labels JSONL file does not exist: {labels_jsonl}")
        sys.exit(1)
    
    # Check output directories
    core_images_dir = out_images_root / "core" / "images"
    hard_images_dir = out_images_root / "hard" / "images"
    
    for out_dir in [core_images_dir, hard_images_dir]:
        if out_dir.exists() and not overwrite:
            logger.error(f"Output directory exists: {out_dir}. Use --overwrite to replace.")
            sys.exit(1)
    
    for out_file in [out_core_labels, out_hard_labels]:
        if out_file.exists() and not overwrite:
            logger.error(f"Output file exists: {out_file}. Use --overwrite to replace.")
            sys.exit(1)
    
    # Clean output directories if overwrite
    if overwrite:
        for out_dir in [core_images_dir, hard_images_dir]:
            if out_dir.exists():
                shutil.rmtree(out_dir)
        for out_file in [out_core_labels, out_hard_labels]:
            if out_file.exists():
                out_file.unlink()
    
    # Create output directories
    core_images_dir.mkdir(parents=True, exist_ok=True)
    hard_images_dir.mkdir(parents=True, exist_ok=True)
    out_core_labels.parent.mkdir(parents=True, exist_ok=True)
    out_hard_labels.parent.mkdir(parents=True, exist_ok=True)
    
    # Read and parse JSONL
    logger.info(f"Reading labels from: {labels_jsonl}")
    samples = []
    with open(labels_jsonl, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                sample = json.loads(line)
                sample["_line_num"] = line_num
                sample["_raw_line"] = line
                samples.append(sample)
            except json.JSONDecodeError as e:
                logger.warning(f"Line {line_num}: Invalid JSON - {e}")
    
    logger.info(f"Total samples read: {len(samples)}")
    
    # Sort by image name for determinism
    samples.sort(key=lambda s: s.get("image", ""))
    
    # Process samples
    core_samples = []
    hard_samples = []
    core_area_fracs = []
    hard_area_fracs = []
    
    skipped = {
        "missing_image_field": 0,
        "missing_corners_field": 0,
        "invalid_corners": 0,
        "image_not_found": 0,
        "cannot_determine_dimensions": 0,
        "zero_image_area": 0,
        "copy_failed": 0,
    }
    
    copy_tasks_core = []
    copy_tasks_hard = []
    
    for sample in samples:
        # Check required fields
        if "image" not in sample:
            skipped["missing_image_field"] += 1
            logger.debug(f"Line {sample.get('_line_num')}: Missing 'image' field")
            continue
        
        if "corners" not in sample:
            skipped["missing_corners_field"] += 1
            logger.debug(f"Line {sample.get('_line_num')}: Missing 'corners' field")
            continue
        
        image_name = sample["image"]
        corners = sample["corners"]
        
        # Validate corners
        if not validate_corners(corners):
            skipped["invalid_corners"] += 1
            logger.debug(f"Line {sample.get('_line_num')}: Invalid corners format")
            continue
        
        # Check image exists
        image_path = images_root / image_name
        if not image_path.is_file():
            skipped["image_not_found"] += 1
            logger.debug(f"Line {sample.get('_line_num')}: Image not found: {image_path}")
            continue
        
        # Get image dimensions
        # First check if width/height are in the label (they are not in this dataset)
        width = sample.get("width")
        height = sample.get("height")
        
        if width is None or height is None:
            dims = get_image_dimensions(image_path)
            if dims is None:
                skipped["cannot_determine_dimensions"] += 1
                logger.debug(f"Line {sample.get('_line_num')}: Cannot determine image dimensions")
                continue
            width, height = dims
        
        # Calculate areas
        image_area = width * height
        if image_area <= 0:
            skipped["zero_image_area"] += 1
            logger.debug(f"Line {sample.get('_line_num')}: Zero image area")
            continue
        
        doc_area = shoelace_area(corners)
        area_frac = doc_area / image_area
        
        # Determine split
        if area_frac < area_frac_thresh:
            # HARD
            hard_samples.append(sample)
            hard_area_fracs.append(area_frac)
            copy_tasks_hard.append((image_path, hard_images_dir / image_name))
        else:
            # CORE
            core_samples.append(sample)
            core_area_fracs.append(area_frac)
            copy_tasks_core.append((image_path, core_images_dir / image_name))
    
    # Copy images
    logger.info(f"Copying {len(copy_tasks_core)} CORE images...")
    logger.info(f"Copying {len(copy_tasks_hard)} HARD images...")
    
    all_copy_tasks = copy_tasks_core + copy_tasks_hard
    copy_failures = 0
    
    if copy_workers == 1:
        # Sequential copy for determinism
        for src, dst in all_copy_tasks:
            if not copy_file(src, dst):
                copy_failures += 1
    else:
        # Parallel copy
        with ThreadPoolExecutor(max_workers=copy_workers) as executor:
            results = list(executor.map(lambda t: copy_file(t[0], t[1]), all_copy_tasks))
            copy_failures = sum(1 for r in results if not r)
    
    if copy_failures > 0:
        skipped["copy_failed"] = copy_failures
        logger.warning(f"Failed to copy {copy_failures} images")
    
    # Write output JSONL files (preserving exact format)
    logger.info(f"Writing CORE labels to: {out_core_labels}")
    with open(out_core_labels, "w", encoding="utf-8") as f:
        for sample in core_samples:
            # Write the original JSON structure without internal fields
            output_sample = {k: v for k, v in sample.items() if not k.startswith("_")}
            f.write(json.dumps(output_sample, ensure_ascii=False) + "\n")
    
    logger.info(f"Writing HARD labels to: {out_hard_labels}")
    with open(out_hard_labels, "w", encoding="utf-8") as f:
        for sample in hard_samples:
            # Write the original JSON structure without internal fields
            output_sample = {k: v for k, v in sample.items() if not k.startswith("_")}
            f.write(json.dumps(output_sample, ensure_ascii=False) + "\n")
    
    # Statistics
    total_processed = len(core_samples) + len(hard_samples)
    total_skipped = sum(skipped.values())
    
    logger.info("=" * 60)
    logger.info("SUMMARY")
    logger.info("=" * 60)
    logger.info(f"Total samples in input:    {len(samples)}")
    logger.info(f"Total processed:           {total_processed}")
    logger.info(f"Total skipped:             {total_skipped}")
    
    if total_skipped > 0:
        logger.info("Skip reasons:")
        for reason, count in skipped.items():
            if count > 0:
                logger.info(f"  - {reason}: {count}")
    
    logger.info("-" * 60)
    logger.info(f"CORE samples:              {len(core_samples)}")
    logger.info(f"HARD samples:              {len(hard_samples)}")
    logger.info(f"Area fraction threshold:   {area_frac_thresh}")
    
    if core_area_fracs:
        logger.info("-" * 60)
        logger.info("CORE area_frac statistics:")
        logger.info(f"  min:    {min(core_area_fracs):.4f}")
        logger.info(f"  median: {statistics.median(core_area_fracs):.4f}")
        logger.info(f"  max:    {max(core_area_fracs):.4f}")
    
    if hard_area_fracs:
        logger.info("-" * 60)
        logger.info("HARD area_frac statistics:")
        logger.info(f"  min:    {min(hard_area_fracs):.4f}")
        logger.info(f"  median: {statistics.median(hard_area_fracs):.4f}")
        logger.info(f"  max:    {max(hard_area_fracs):.4f}")
    
    logger.info("=" * 60)
    logger.info("Done.")


def main():
    parser = argparse.ArgumentParser(
        description="Split SmartDoc dataset into CORE and HARD subsets based on document area fraction.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Example usage:
  python3 training/scripts/split_smartdoc_by_area.py \\
      --images_root training/data/smartdoc \\
      --labels_jsonl training/labels/smartdoc_labels.jsonl \\
      --out_images_root training/data/smartdoc_splitt \\
      --out_core_labels training/labels/smartdoc_core_labels.jsonl \\
      --out_hard_labels training/labels/smartdoc_hard_labels.jsonl \\
      --area_frac_thresh 0.15 \\
      --copy_workers 1 \\
      --overwrite
""",
    )
    
    parser.add_argument(
        "--images_root",
        type=Path,
        required=True,
        help="Root directory containing SmartDoc images",
    )
    parser.add_argument(
        "--labels_jsonl",
        type=Path,
        required=True,
        help="Path to input JSONL labels file",
    )
    parser.add_argument(
        "--out_images_root",
        type=Path,
        required=True,
        help="Root directory for output images (will contain core/images and hard/images)",
    )
    parser.add_argument(
        "--out_core_labels",
        type=Path,
        required=True,
        help="Path to output CORE labels JSONL file",
    )
    parser.add_argument(
        "--out_hard_labels",
        type=Path,
        required=True,
        help="Path to output HARD labels JSONL file",
    )
    parser.add_argument(
        "--area_frac_thresh",
        type=float,
        default=0.15,
        help="Area fraction threshold (default: 0.15). Samples with area_frac < threshold go to HARD.",
    )
    parser.add_argument(
        "--copy_workers",
        type=int,
        default=1,
        help="Number of workers for copying images (default: 1 for determinism)",
    )
    parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Overwrite existing output directories and files",
    )
    
    args = parser.parse_args()
    
    # Validate threshold
    if not 0.0 < args.area_frac_thresh < 1.0:
        parser.error("--area_frac_thresh must be between 0 and 1 (exclusive)")
    
    # Validate workers
    if args.copy_workers < 1:
        parser.error("--copy_workers must be at least 1")
    
    process_dataset(
        images_root=args.images_root,
        labels_jsonl=args.labels_jsonl,
        out_images_root=args.out_images_root,
        out_core_labels=args.out_core_labels,
        out_hard_labels=args.out_hard_labels,
        area_frac_thresh=args.area_frac_thresh,
        copy_workers=args.copy_workers,
        overwrite=args.overwrite,
    )


if __name__ == "__main__":
    main()
