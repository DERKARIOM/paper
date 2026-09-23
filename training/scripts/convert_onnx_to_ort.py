#!/usr/bin/env python3
"""Convert an ONNX model to ORT format with reduced operator configuration.

Produces:
  1. An optimized `.ort` model (size-optimized, Android-ready)
  2. A reduced operator configuration file for `--minimal_build extended`

Requirements:
  pip install onnxruntime==1.24.1

Usage:
  python3 training/scripts/convert_onnx_to_ort.py \
    --model app/src/main/assets/docquad/docquadnet256_trained_opset17.onnx \
    --out_dir /tmp/ort_output

  # With custom optimization level:
  python3 training/scripts/convert_onnx_to_ort.py \
    --model app/src/main/assets/docquad/docquadnet256_trained_opset17.onnx \
    --out_dir /tmp/ort_output \
    --optimization_level extended

Output files:
  <out_dir>/<model_name>.ort                  — ORT-format model
  <out_dir>/<model_name>.required_operators.config — reduced op config
  <out_dir>/<model_name>.optimized.onnx       — intermediate optimized ONNX (for debugging)
"""

import argparse
import hashlib
import os
import shutil
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def check_onnxruntime_version():
    """Verify onnxruntime is installed and print version."""
    try:
        import onnxruntime as ort
    except ImportError:
        print("ERROR: onnxruntime is not installed.", file=sys.stderr)
        print("  pip install onnxruntime==1.24.1", file=sys.stderr)
        sys.exit(1)

    version = ort.__version__
    print(f"onnxruntime version: {version}")
    if not version.startswith("1.24."):
        print(
            f"WARNING: Expected onnxruntime 1.24.x, got {version}. "
            "Model may not be compatible with the Android runtime.",
            file=sys.stderr,
        )
    return ort


def convert_onnx_to_ort(
    model_path: Path,
    out_dir: Path,
    optimization_level: str = "basic",
) -> dict:
    """Convert ONNX model to ORT format.

    Args:
        model_path: Path to the input .onnx model.
        out_dir: Output directory for generated files.
        optimization_level: One of 'basic', 'extended', 'all'.
            'basic' is safest for mobile; 'extended' enables more fusions.

    Returns:
        Dict with paths to generated files and metadata.
    """
    ort = check_onnxruntime_version()

    if not model_path.exists():
        print(f"ERROR: Model not found: {model_path}", file=sys.stderr)
        sys.exit(1)

    out_dir.mkdir(parents=True, exist_ok=True)
    stem = model_path.stem

    # --- Step 1: Optimize the ONNX model and save as ORT ---
    opt_level_map = {
        "basic": ort.GraphOptimizationLevel.ORT_ENABLE_BASIC,
        "extended": ort.GraphOptimizationLevel.ORT_ENABLE_EXTENDED,
        "all": ort.GraphOptimizationLevel.ORT_ENABLE_ALL,
    }
    if optimization_level not in opt_level_map:
        print(
            f"ERROR: Invalid optimization_level '{optimization_level}'. "
            f"Choose from: {list(opt_level_map.keys())}",
            file=sys.stderr,
        )
        sys.exit(1)

    optimized_onnx_path = out_dir / f"{stem}.optimized.onnx"
    ort_path = out_dir / f"{stem}.ort"

    print(f"Input model:        {model_path}")
    print(f"Input size:         {model_path.stat().st_size:,} bytes")
    print(f"Input SHA-256:      {sha256(model_path)}")
    print(f"Optimization level: {optimization_level}")
    print()

    # Create session options for optimization
    so = ort.SessionOptions()
    so.graph_optimization_level = opt_level_map[optimization_level]
    so.optimized_model_filepath = str(optimized_onnx_path)

    # Run a session to trigger optimization and save optimized ONNX
    print("Step 1: Optimizing ONNX model...")
    _ = ort.InferenceSession(str(model_path), so, providers=["CPUExecutionProvider"])
    print(f"  Optimized ONNX saved: {optimized_onnx_path}")
    print(f"  Optimized size:       {optimized_onnx_path.stat().st_size:,} bytes")

    # --- Step 2: Convert optimized ONNX to ORT format ---
    print()
    print("Step 2: Converting to ORT format...")

    # Use the onnxruntime tools module for ORT conversion
    try:
        from onnxruntime.tools import convert_onnx_models_to_ort
    except ImportError:
        print(
            "ERROR: onnxruntime.tools.convert_onnx_models_to_ort not available.",
            file=sys.stderr,
        )
        print("  Ensure onnxruntime >= 1.24.0 is installed.", file=sys.stderr)
        sys.exit(1)

    # convert_onnx_models_to_ort works on a directory; copy model there
    convert_dir = out_dir / "_convert_tmp"
    convert_dir.mkdir(exist_ok=True)
    tmp_model = convert_dir / model_path.name
    shutil.copy2(model_path, tmp_model)

    # Run conversion — this produces .ort file + required_operators.config
    # Note: optimization_level (basic/extended/all) was already applied in Step 1
    # via SessionOptions. The convert_onnx_models_to_ort tool uses
    # OptimizationStyle (Fixed vs Runtime) which controls whether optimizations
    # are baked in (Fixed) or applied at runtime (Runtime).
    # We use Fixed since we already optimized the model.
    from onnxruntime.tools.convert_onnx_models_to_ort import OptimizationStyle

    convert_onnx_models_to_ort.convert_onnx_models_to_ort(
        model_path_or_dir=convert_dir,
        output_dir=None,  # in-place
        optimization_styles=[OptimizationStyle.Fixed],
        custom_op_library_path=None,
    )

    # Find generated files
    generated_ort = convert_dir / f"{model_path.stem}.ort"
    generated_config = convert_dir / f"{model_path.stem}.required_operators.config"

    if not generated_ort.exists():
        # Some versions put .ort next to .onnx with same stem
        candidates = list(convert_dir.glob("*.ort"))
        if candidates:
            generated_ort = candidates[0]

    if generated_ort.exists():
        final_ort = out_dir / f"{stem}.ort"
        shutil.move(str(generated_ort), str(final_ort))
        ort_path = final_ort
        print(f"  ORT model saved:  {ort_path}")
        print(f"  ORT size:         {ort_path.stat().st_size:,} bytes")
    else:
        print("WARNING: .ort file was not generated.", file=sys.stderr)
        ort_path = None

    config_path = None
    config_candidates = list(convert_dir.glob("*.config")) + list(
        convert_dir.glob("*.required_operators*")
    )
    if config_candidates:
        final_config = out_dir / f"{stem}.required_operators.config"
        shutil.move(str(config_candidates[0]), str(final_config))
        config_path = final_config
        print(f"  Op config saved:  {config_path}")

    # Cleanup temp dir
    shutil.rmtree(convert_dir, ignore_errors=True)

    # --- Step 3: Summary ---
    print()
    print("=" * 60)
    print("SUMMARY")
    print("=" * 60)
    input_size = model_path.stat().st_size
    if ort_path and ort_path.exists():
        ort_size = ort_path.stat().st_size
        reduction = (1 - ort_size / input_size) * 100
        print(f"  Input:     {input_size:>12,} bytes  {model_path.name}")
        print(f"  ORT:       {ort_size:>12,} bytes  {ort_path.name}")
        print(f"  Reduction: {reduction:>11.1f}%")
        print(f"  ORT SHA-256: {sha256(ort_path)}")
    if config_path and config_path.exists():
        print(f"  Op config: {config_path}")
    print()

    # --- Step 4: Validate ORT model loads ---
    if ort_path and ort_path.exists():
        print("Step 3: Validating ORT model loads correctly...")
        try:
            so_val = ort.SessionOptions()
            sess = ort.InferenceSession(
                str(ort_path), so_val, providers=["CPUExecutionProvider"]
            )
            inputs = sess.get_inputs()
            outputs = sess.get_outputs()
            print(f"  Inputs:  {[(i.name, i.shape, i.type) for i in inputs]}")
            print(f"  Outputs: {[(o.name, o.shape, o.type) for o in outputs]}")
            print("  ✓ ORT model loads and is valid.")
        except Exception as e:
            print(f"  ✗ ORT model validation failed: {e}", file=sys.stderr)

    return {
        "ort_path": ort_path,
        "config_path": config_path,
        "optimized_onnx_path": optimized_onnx_path,
    }


def verify_operators(config_path: Path):
    """Print the operators listed in the reduced config file."""
    if not config_path or not config_path.exists():
        print("No operator config file to display.")
        return

    print()
    print("Required operators (from config):")
    print("-" * 40)
    with open(config_path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                print(f"  {line}")
    print("-" * 40)


def verify_inference_equivalence(
    onnx_path: Path, ort_path: Path, atol: float = 1e-4
):
    """Compare inference outputs between ONNX and ORT models."""
    import numpy as np

    ort = check_onnxruntime_version()

    print()
    print("Verifying inference equivalence...")

    so = ort.SessionOptions()
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_DISABLE_ALL

    sess_onnx = ort.InferenceSession(
        str(onnx_path), so, providers=["CPUExecutionProvider"]
    )
    sess_ort = ort.InferenceSession(
        str(ort_path), ort.SessionOptions(), providers=["CPUExecutionProvider"]
    )

    # Create dummy input matching model spec
    inputs = sess_onnx.get_inputs()
    feed = {}
    for inp in inputs:
        shape = [d if isinstance(d, int) else 1 for d in inp.shape]
        dtype = np.float16 if "float16" in inp.type.lower() else np.float32
        feed[inp.name] = np.random.randn(*shape).astype(dtype)

    out_onnx = sess_onnx.run(None, feed)
    out_ort = sess_ort.run(None, feed)

    all_close = True
    for i, (a, b) in enumerate(zip(out_onnx, out_ort)):
        a = np.array(a, dtype=np.float32)
        b = np.array(b, dtype=np.float32)
        max_diff = np.max(np.abs(a - b))
        close = max_diff <= atol
        status = "✓" if close else "✗"
        print(f"  Output[{i}]: max_diff={max_diff:.6e} {status}")
        if not close:
            all_close = False

    if all_close:
        print("  ✓ All outputs match within tolerance.")
    else:
        print(f"  ✗ Some outputs differ by more than atol={atol}")


def main():
    p = argparse.ArgumentParser(
        description="Convert ONNX model to ORT format (size-optimized, Android-ready).",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    p.add_argument(
        "--model",
        required=True,
        help="Path to input .onnx model",
    )
    p.add_argument(
        "--out_dir",
        default="/tmp/ort_output",
        help="Output directory (default: /tmp/ort_output)",
    )
    p.add_argument(
        "--optimization_level",
        choices=["basic", "extended", "all"],
        default="basic",
        help="Graph optimization level (default: basic)",
    )
    p.add_argument(
        "--verify",
        action="store_true",
        help="Run inference equivalence check after conversion",
    )
    p.add_argument(
        "--show_ops",
        action="store_true",
        help="Print required operators from config",
    )
    args = p.parse_args()

    model_path = Path(args.model).resolve()
    out_dir = Path(args.out_dir).resolve()

    result = convert_onnx_to_ort(
        model_path=model_path,
        out_dir=out_dir,
        optimization_level=args.optimization_level,
    )

    if args.show_ops:
        verify_operators(result["config_path"])

    if args.verify:
        if result["ort_path"] and result["ort_path"].exists():
            verify_inference_equivalence(model_path, result["ort_path"])
        else:
            print("Cannot verify: ORT model not available.")

    # Android integration notes
    print()
    print("=" * 60)
    print("ANDROID INTEGRATION")
    print("=" * 60)
    print("""
To use the .ort model in the Android app:

1. Copy the .ort file to the assets directory:
     cp {ort} app/src/main/assets/docquad/

2. Update DocQuadDetector.java to reference the .ort file:
     public static final String DEFAULT_MODEL_ASSET_PATH =
         "docquad/{ort_name}";

3. To build ONNX Runtime with minimal operators (optional):
     python3 tools/ci_build/build.py \\
       --build_dir build/android_minimal \\
       --config Release \\
       --android \\
       --android_sdk_path $ANDROID_SDK_ROOT \\
       --android_ndk_path $ANDROID_NDK_HOME \\
       --android_abi arm64-v8a \\
       --android_api 29 \\
       --minimal_build extended \\
       --include_ops_by_config {config} \\
       --disable_ml_ops \\
       --skip_tests \\
       --use_xnnpack

   This produces a smaller libonnxruntime.so with only the
   operators required by your model.
""".format(
        ort=result["ort_path"] or "<ort_path>",
        ort_name=result["ort_path"].name if result["ort_path"] else "<model>.ort",
        config=result["config_path"] or "<config_path>",
    ))


if __name__ == "__main__":
    main()
