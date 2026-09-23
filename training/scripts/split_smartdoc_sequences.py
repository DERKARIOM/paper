#!/usr/bin/env python3
"""
Split SmartDoc dataset into train/val/test with sequence-disjoint splitting.

A "sequence" is defined as (bg_name, model_name) extracted from the filename.
All frames from a sequence belong to exactly one split (train OR val OR test).

After splitting, per-sequence subsampling is applied to reduce redundancy.

Usage:
    python split_smartdoc_sequences.py \
        --labels-file training/labels/smartdoc_labels.jsonl \
        --out-dir training/labels \
        --train-ratio 0.8 --val-ratio 0.1 --test-ratio 0.1 \
        --k-train 20 --k-val 5 --k-test 5 \
        --seed 42
"""

import argparse
import json
import random
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Optional


def parse_sequence_id(image_name: str) -> Optional[str]:
    """
    Extract sequence_id from SmartDoc image filename.
    
    Format: background01_datasheet001_frame_0001.jpeg
    Sequence ID: background01_datasheet001
    
    Args:
        image_name: The image filename
    
    Returns:
        Sequence ID string or None if parsing fails
    """
    # Pattern: backgroundXX_modelYYY_frame_ZZZZ.jpeg
    match = re.match(r'^(background\d+_[a-zA-Z]+\d+)_frame_\d+\.\w+$', image_name)
    if match:
        return match.group(1)
    return None


def load_labels(labels_file: Path) -> list[dict]:
    """Load labels from JSONL file."""
    labels = []
    with open(labels_file, 'r', encoding='utf-8') as f:
        for line_num, line in enumerate(f, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                label = json.loads(line)
                labels.append(label)
            except json.JSONDecodeError as e:
                print(f"Warning: Line {line_num}: Invalid JSON - {e}", file=sys.stderr)
    return labels


def save_labels(labels: list[dict], output_file: Path) -> None:
    """Save labels to JSONL file."""
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with open(output_file, 'w', encoding='utf-8') as f:
        for label in labels:
            f.write(json.dumps(label, ensure_ascii=False) + '\n')


def group_by_sequence(labels: list[dict]) -> dict[str, list[dict]]:
    """Group labels by sequence_id."""
    sequences = defaultdict(list)
    unknown_count = 0
    
    for label in labels:
        image_name = label.get('image', '')
        seq_id = parse_sequence_id(image_name)
        
        if seq_id is None:
            unknown_count += 1
            # Use filename stem as fallback sequence
            seq_id = Path(image_name).stem
        
        sequences[seq_id].append(label)
    
    if unknown_count > 0:
        print(f"Warning: {unknown_count} images with unparseable sequence ID", file=sys.stderr)
    
    return dict(sequences)


def split_sequences(
    sequence_ids: list[str],
    train_ratio: float,
    val_ratio: float,
    test_ratio: float,
    seed: int
) -> tuple[list[str], list[str], list[str]]:
    """
    Split sequence IDs into train/val/test sets.
    
    Args:
        sequence_ids: List of sequence IDs
        train_ratio: Fraction for training
        val_ratio: Fraction for validation
        test_ratio: Fraction for testing
        seed: Random seed for reproducibility
    
    Returns:
        Tuple of (train_ids, val_ids, test_ids)
    """
    # Validate ratios
    total = train_ratio + val_ratio + test_ratio
    if abs(total - 1.0) > 0.001:
        print(f"Warning: Ratios sum to {total}, normalizing...", file=sys.stderr)
        train_ratio /= total
        val_ratio /= total
        test_ratio /= total
    
    # Sort for determinism, then shuffle with seed
    sorted_ids = sorted(sequence_ids)
    rng = random.Random(seed)
    rng.shuffle(sorted_ids)
    
    n = len(sorted_ids)
    n_train = int(n * train_ratio)
    n_val = int(n * val_ratio)
    # Rest goes to test
    
    train_ids = sorted_ids[:n_train]
    val_ids = sorted_ids[n_train:n_train + n_val]
    test_ids = sorted_ids[n_train + n_val:]
    
    return train_ids, val_ids, test_ids


def subsample_sequence(
    labels: list[dict],
    k: int,
    seed: int,
    sequence_id: str
) -> list[dict]:
    """
    Subsample k frames from a sequence.
    
    Uses uniform spacing over sorted frame indices for better coverage.
    
    Args:
        labels: List of labels for this sequence
        k: Number of frames to sample
        seed: Base seed (combined with sequence_id for determinism)
        sequence_id: Sequence identifier for seed derivation
    
    Returns:
        Subsampled list of labels
    """
    if k <= 0 or len(labels) == 0:
        return []
    
    if k >= len(labels):
        return labels
    
    # Sort by image name (which includes frame index)
    sorted_labels = sorted(labels, key=lambda x: x.get('image', ''))
    
    # Uniform spacing: select evenly distributed indices
    n = len(sorted_labels)
    step = n / k
    indices = [int(i * step) for i in range(k)]
    
    # Ensure indices are unique and within bounds
    indices = sorted(set(min(idx, n - 1) for idx in indices))
    
    # If we got fewer indices due to rounding, add more
    if len(indices) < k:
        # Derive deterministic seed from sequence_id
        seq_seed = seed + hash(sequence_id) % (2**31)
        rng = random.Random(seq_seed)
        
        remaining = [i for i in range(n) if i not in indices]
        rng.shuffle(remaining)
        indices.extend(remaining[:k - len(indices)])
        indices = sorted(indices)
    
    return [sorted_labels[i] for i in indices[:k]]


def main():
    parser = argparse.ArgumentParser(
        description='Split SmartDoc dataset with sequence-disjoint splitting and subsampling'
    )
    parser.add_argument(
        '--labels-file',
        type=str,
        required=True,
        help='Path to input SmartDoc labels JSONL file'
    )
    parser.add_argument(
        '--out-dir',
        type=str,
        required=True,
        help='Output directory for split label files'
    )
    parser.add_argument(
        '--train-ratio',
        type=float,
        default=0.8,
        help='Fraction of sequences for training (default: 0.8)'
    )
    parser.add_argument(
        '--val-ratio',
        type=float,
        default=0.1,
        help='Fraction of sequences for validation (default: 0.1)'
    )
    parser.add_argument(
        '--test-ratio',
        type=float,
        default=0.1,
        help='Fraction of sequences for testing (default: 0.1)'
    )
    parser.add_argument(
        '--k-train',
        type=int,
        default=20,
        help='Max frames per sequence for training (default: 20, 0=no limit)'
    )
    parser.add_argument(
        '--k-val',
        type=int,
        default=5,
        help='Max frames per sequence for validation (default: 5, 0=no limit)'
    )
    parser.add_argument(
        '--k-test',
        type=int,
        default=5,
        help='Max frames per sequence for testing (default: 5, 0=no limit)'
    )
    parser.add_argument(
        '--seed',
        type=int,
        default=42,
        help='Random seed for reproducibility (default: 42)'
    )
    parser.add_argument(
        '--prefix',
        type=str,
        default='smartdoc',
        help='Output filename prefix (default: smartdoc)'
    )
    
    args = parser.parse_args()
    
    labels_file = Path(args.labels_file)
    out_dir = Path(args.out_dir)
    
    if not labels_file.exists():
        print(f"Error: Labels file not found: {labels_file}", file=sys.stderr)
        sys.exit(1)
    
    print(f"Input labels: {labels_file}")
    print(f"Output directory: {out_dir}")
    print(f"Split ratios: train={args.train_ratio}, val={args.val_ratio}, test={args.test_ratio}")
    print(f"Subsampling: k_train={args.k_train}, k_val={args.k_val}, k_test={args.k_test}")
    print(f"Seed: {args.seed}")
    print()
    
    # Load labels
    print("Loading labels...")
    labels = load_labels(labels_file)
    print(f"Loaded {len(labels)} labels")
    
    # Group by sequence
    print("Grouping by sequence...")
    sequences = group_by_sequence(labels)
    print(f"Found {len(sequences)} unique sequences")
    
    # Print sequence statistics
    seq_sizes = [len(v) for v in sequences.values()]
    print(f"Frames per sequence: min={min(seq_sizes)}, max={max(seq_sizes)}, avg={sum(seq_sizes)/len(seq_sizes):.1f}")
    print()
    
    # Split sequences
    print("Splitting sequences...")
    train_seqs, val_seqs, test_seqs = split_sequences(
        list(sequences.keys()),
        args.train_ratio,
        args.val_ratio,
        args.test_ratio,
        args.seed
    )
    print(f"Sequences: train={len(train_seqs)}, val={len(val_seqs)}, test={len(test_seqs)}")
    
    # Collect and subsample labels for each split
    def collect_split(seq_ids: list[str], k: int, split_name: str) -> list[dict]:
        result = []
        for seq_id in sorted(seq_ids):
            seq_labels = sequences[seq_id]
            if k > 0:
                sampled = subsample_sequence(seq_labels, k, args.seed, seq_id)
            else:
                sampled = seq_labels
            result.extend(sampled)
        print(f"  {split_name}: {len(result)} samples from {len(seq_ids)} sequences")
        return result
    
    print("\nCollecting and subsampling...")
    train_labels = collect_split(train_seqs, args.k_train, "train")
    val_labels = collect_split(val_seqs, args.k_val, "val")
    test_labels = collect_split(test_seqs, args.k_test, "test")
    
    # Sort labels by image name for determinism
    train_labels.sort(key=lambda x: x.get('image', ''))
    val_labels.sort(key=lambda x: x.get('image', ''))
    test_labels.sort(key=lambda x: x.get('image', ''))
    
    # Save output files
    print("\nSaving output files...")
    
    train_file = out_dir / f"{args.prefix}_train_sub.jsonl"
    val_file = out_dir / f"{args.prefix}_val_sub.jsonl"
    test_file = out_dir / f"{args.prefix}_test_sub.jsonl"
    
    save_labels(train_labels, train_file)
    print(f"  {train_file}: {len(train_labels)} samples")
    
    save_labels(val_labels, val_file)
    print(f"  {val_file}: {len(val_labels)} samples")
    
    save_labels(test_labels, test_file)
    print(f"  {test_file}: {len(test_labels)} samples")
    
    # Also save full (non-subsampled) split files
    print("\nSaving full (non-subsampled) split files...")
    
    train_full = []
    for seq_id in sorted(train_seqs):
        train_full.extend(sequences[seq_id])
    train_full.sort(key=lambda x: x.get('image', ''))
    
    val_full = []
    for seq_id in sorted(val_seqs):
        val_full.extend(sequences[seq_id])
    val_full.sort(key=lambda x: x.get('image', ''))
    
    test_full = []
    for seq_id in sorted(test_seqs):
        test_full.extend(sequences[seq_id])
    test_full.sort(key=lambda x: x.get('image', ''))
    
    train_full_file = out_dir / f"{args.prefix}_train.jsonl"
    val_full_file = out_dir / f"{args.prefix}_val.jsonl"
    test_full_file = out_dir / f"{args.prefix}_test.jsonl"
    
    save_labels(train_full, train_full_file)
    print(f"  {train_full_file}: {len(train_full)} samples")
    
    save_labels(val_full, val_full_file)
    print(f"  {val_full_file}: {len(val_full)} samples")
    
    save_labels(test_full, test_full_file)
    print(f"  {test_full_file}: {len(test_full)} samples")
    
    # Summary
    print("\n" + "="*60)
    print("SUMMARY")
    print("="*60)
    print(f"Total input samples: {len(labels)}")
    print(f"Total sequences: {len(sequences)}")
    print()
    print("Sequence-disjoint splits:")
    print(f"  Train: {len(train_seqs)} sequences, {len(train_full)} total frames, {len(train_labels)} subsampled")
    print(f"  Val:   {len(val_seqs)} sequences, {len(val_full)} total frames, {len(val_labels)} subsampled")
    print(f"  Test:  {len(test_seqs)} sequences, {len(test_full)} total frames, {len(test_labels)} subsampled")
    print()
    print("Output files:")
    print(f"  Subsampled: {args.prefix}_{{train,val,test}}_sub.jsonl")
    print(f"  Full:       {args.prefix}_{{train,val,test}}.jsonl")
    print("\nDone!")


if __name__ == '__main__':
    main()
