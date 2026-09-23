#!/usr/bin/env python3
"""
Synthesize training images by placing receipt images on DTD backgrounds
with random perspective transformations.

This creates realistic training data for corner detection of narrow documents.
"""
import argparse
import json
import hashlib
import cv2
import numpy as np
from pathlib import Path
from typing import List, Tuple


def get_deterministic_seed(filename: str, global_seed: int) -> int:
    """Generate deterministic seed from filename and global seed."""
    combined = f"{global_seed}_{filename}"
    hash_bytes = hashlib.sha256(combined.encode()).digest()
    return int.from_bytes(hash_bytes[:4], 'big')


def collect_images(directory: Path, extensions: List[str] = None) -> List[Path]:
    """Collect all image files from directory recursively."""
    if extensions is None:
        extensions = ['.jpg', '.jpeg', '.png', '.webp', '.bmp']
    
    images = []
    for ext in extensions:
        images.extend(directory.rglob(f'*{ext}'))
        images.extend(directory.rglob(f'*{ext.upper()}'))
    
    return sorted(set(images))


def resize_cover(image: np.ndarray, target_width: int, target_height: int, rng: np.random.Generator) -> np.ndarray:
    """Resize image to cover target dimensions (may crop)."""
    h, w = image.shape[:2]
    
    scale_w = target_width / w
    scale_h = target_height / h
    scale = max(scale_w, scale_h)
    
    new_w = int(w * scale)
    new_h = int(h * scale)
    
    new_w = max(new_w, target_width)
    new_h = max(new_h, target_height)
    
    resized = cv2.resize(image, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    
    if new_w > target_width:
        x_offset = rng.integers(0, new_w - target_width + 1)
    else:
        x_offset = 0
    
    if new_h > target_height:
        y_offset = rng.integers(0, new_h - target_height + 1)
    else:
        y_offset = 0
    
    cropped = resized[y_offset:y_offset + target_height, x_offset:x_offset + target_width]
    
    if cropped.shape[0] != target_height or cropped.shape[1] != target_width:
        cropped = cv2.resize(cropped, (target_width, target_height), interpolation=cv2.INTER_LINEAR)
    
    return cropped


def generate_perspective_corners(
    doc_width: int,
    doc_height: int,
    canvas_width: int,
    canvas_height: int,
    rng: np.random.Generator,
    scale_range: Tuple[float, float] = (0.3, 0.7),
    perspective_strength: float = 0.15
) -> np.ndarray:
    """
    Generate random perspective-transformed corners for document placement.
    
    Returns corners in order: TL, TR, BR, BL
    """
    # Random scale factor
    scale = rng.uniform(scale_range[0], scale_range[1])
    
    # Calculate scaled document size
    scaled_w = int(doc_width * scale)
    scaled_h = int(doc_height * scale)
    
    # Ensure document fits in canvas with margin
    margin = 20
    max_w = canvas_width - 2 * margin
    max_h = canvas_height - 2 * margin
    
    if scaled_w > max_w or scaled_h > max_h:
        fit_scale = min(max_w / scaled_w, max_h / scaled_h)
        scaled_w = int(scaled_w * fit_scale)
        scaled_h = int(scaled_h * fit_scale)
    
    # Random position (center of document)
    cx = rng.integers(margin + scaled_w // 2, canvas_width - margin - scaled_w // 2 + 1)
    cy = rng.integers(margin + scaled_h // 2, canvas_height - margin - scaled_h // 2 + 1)
    
    # Base rectangle corners (centered at origin)
    half_w = scaled_w / 2
    half_h = scaled_h / 2
    
    corners = np.array([
        [-half_w, -half_h],  # TL
        [half_w, -half_h],   # TR
        [half_w, half_h],    # BR
        [-half_w, half_h],   # BL
    ], dtype=np.float32)
    
    # Apply random rotation
    angle = rng.uniform(-15, 15) * np.pi / 180
    cos_a, sin_a = np.cos(angle), np.sin(angle)
    rotation = np.array([[cos_a, -sin_a], [sin_a, cos_a]])
    corners = corners @ rotation.T
    
    # Apply perspective distortion
    for i in range(4):
        corners[i, 0] += rng.uniform(-perspective_strength, perspective_strength) * scaled_w
        corners[i, 1] += rng.uniform(-perspective_strength, perspective_strength) * scaled_h
    
    # Translate to canvas position
    corners[:, 0] += cx
    corners[:, 1] += cy
    
    # Clamp to canvas bounds
    corners[:, 0] = np.clip(corners[:, 0], 1, canvas_width - 2)
    corners[:, 1] = np.clip(corners[:, 1], 1, canvas_height - 2)
    
    return corners


def place_document_on_background(
    document: np.ndarray,
    background: np.ndarray,
    dst_corners: np.ndarray
) -> np.ndarray:
    """
    Place document on background using perspective transformation.
    
    Args:
        document: Source document image
        background: Background image
        dst_corners: Destination corners [TL, TR, BR, BL]
    
    Returns:
        Composited image
    """
    doc_h, doc_w = document.shape[:2]
    
    # Source corners (document image corners)
    src_corners = np.array([
        [0, 0],           # TL
        [doc_w - 1, 0],   # TR
        [doc_w - 1, doc_h - 1],  # BR
        [0, doc_h - 1],   # BL
    ], dtype=np.float32)
    
    # Compute perspective transform
    M = cv2.getPerspectiveTransform(src_corners, dst_corners.astype(np.float32))
    
    # Warp document
    canvas_h, canvas_w = background.shape[:2]
    warped = cv2.warpPerspective(document, M, (canvas_w, canvas_h))
    
    # Create mask for blending
    mask = np.ones((doc_h, doc_w), dtype=np.uint8) * 255
    warped_mask = cv2.warpPerspective(mask, M, (canvas_w, canvas_h))
    
    # Slight feathering of mask edges
    warped_mask = cv2.GaussianBlur(warped_mask, (3, 3), 0)
    
    # Composite
    mask_3ch = warped_mask[:, :, np.newaxis].astype(np.float32) / 255.0
    result = (warped.astype(np.float32) * mask_3ch + 
              background.astype(np.float32) * (1 - mask_3ch))
    
    return np.clip(result, 0, 255).astype(np.uint8)


def process_receipt(
    receipt_path: Path,
    dtd_images: List[Path],
    output_dir: Path,
    global_seed: int,
    num_variants: int,
    canvas_size: Tuple[int, int]
) -> List[dict]:
    """
    Process a single receipt and generate multiple variants.
    
    Returns list of label dicts.
    """
    receipt = cv2.imread(str(receipt_path), cv2.IMREAD_COLOR)
    if receipt is None:
        print(f'Error: Could not load {receipt_path}')
        return []
    
    doc_h, doc_w = receipt.shape[:2]
    canvas_w, canvas_h = canvas_size
    
    labels = []
    
    for variant_idx in range(num_variants):
        # Deterministic seed for this variant
        seed = get_deterministic_seed(f"{receipt_path.stem}_v{variant_idx}", global_seed)
        rng = np.random.default_rng(seed)
        
        # Select random DTD background
        dtd_idx = rng.integers(0, len(dtd_images))
        dtd_path = dtd_images[dtd_idx]
        
        background = cv2.imread(str(dtd_path), cv2.IMREAD_COLOR)
        if background is None:
            continue
        
        # Resize background to canvas size
        background = resize_cover(background, canvas_w, canvas_h, rng)
        
        # Generate random perspective corners
        corners = generate_perspective_corners(
            doc_w, doc_h, canvas_w, canvas_h, rng,
            scale_range=(0.4, 0.8),
            perspective_strength=0.12
        )
        
        # Place document on background
        result = place_document_on_background(receipt, background, corners)
        
        # Save image
        out_name = f"{receipt_path.stem}_v{variant_idx:02d}.png"
        out_path = output_dir / 'images' / out_name
        cv2.imwrite(str(out_path), result)
        
        # Create label
        label = {
            'image': out_name,
            'corners': corners.tolist()
        }
        labels.append(label)
    
    return labels


def main():
    parser = argparse.ArgumentParser(
        description='Synthesize receipt training images on DTD backgrounds'
    )
    parser.add_argument('--receipts', type=str, nargs='+', required=True,
                        help='Paths to receipt images or directories')
    parser.add_argument('--dtd-dir', type=str, required=True,
                        help='Path to DTD texture directory')
    parser.add_argument('--output-dir', type=str, required=True,
                        help='Output directory for synthesized images')
    parser.add_argument('--num-variants', type=int, default=10,
                        help='Number of variants per receipt (default: 10)')
    parser.add_argument('--canvas-width', type=int, default=1920,
                        help='Output canvas width (default: 1920)')
    parser.add_argument('--canvas-height', type=int, default=1080,
                        help='Output canvas height (default: 1080)')
    parser.add_argument('--seed', type=int, default=42,
                        help='Global seed for determinism (default: 42)')
    parser.add_argument('--min-aspect', type=float, default=0.0,
                        help='Minimum h/w aspect ratio filter (default: 0.0 = no filter)')
    args = parser.parse_args()
    
    # Collect receipt images
    receipt_paths = []
    for path_str in args.receipts:
        path = Path(path_str)
        if path.is_file():
            receipt_paths.append(path)
        elif path.is_dir():
            receipt_paths.extend(collect_images(path, ['.png', '.jpg', '.jpeg']))
    
    print(f'Found {len(receipt_paths)} receipt images')
    
    # Filter by aspect ratio if specified
    if args.min_aspect > 0:
        filtered = []
        for rp in receipt_paths:
            img = cv2.imread(str(rp))
            if img is not None:
                h, w = img.shape[:2]
                if h / w >= args.min_aspect:
                    filtered.append(rp)
        receipt_paths = filtered
        print(f'After aspect ratio filter (>= {args.min_aspect}): {len(receipt_paths)} receipts')
    
    # Collect DTD images
    dtd_dir = Path(args.dtd_dir)
    dtd_images = collect_images(dtd_dir)
    print(f'Found {len(dtd_images)} DTD texture images')
    
    if not dtd_images:
        print('Error: No DTD images found')
        return
    
    # Create output directory
    output_dir = Path(args.output_dir)
    (output_dir / 'images').mkdir(parents=True, exist_ok=True)
    
    # Process receipts
    all_labels = []
    canvas_size = (args.canvas_width, args.canvas_height)
    
    for i, receipt_path in enumerate(receipt_paths):
        print(f'Processing {i+1}/{len(receipt_paths)}: {receipt_path.name}')
        
        labels = process_receipt(
            receipt_path,
            dtd_images,
            output_dir,
            args.seed,
            args.num_variants,
            canvas_size
        )
        all_labels.extend(labels)
    
    # Write labels
    labels_path = output_dir / 'labels.jsonl'
    with open(labels_path, 'w') as f:
        for label in all_labels:
            f.write(json.dumps(label) + '\n')
    
    print()
    print(f'Done!')
    print(f'Output images: {output_dir}/images/')
    print(f'Output labels: {labels_path}')
    print(f'Total images: {len(all_labels)}')


if __name__ == '__main__':
    main()
