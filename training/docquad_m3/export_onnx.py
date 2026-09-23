from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import numpy as np
import torch
import torch.nn as nn

from training.docquad_m2.model import DocQuadNet256


DEFAULT_OPSET_VERSION = 17


class DocQuadNet256OnnxWrapper(nn.Module):
    """Wrapper that provides pure tensor outputs for ONNX.

    TorchScript/ONNX cannot export dataclasses as output containers.
    """

    def __init__(self, model: DocQuadNet256):
        super().__init__()
        self.model = model

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        out = self.model(x)
        # FIX: ONNX output order is part of the specification.
        # Outputs remain logits (no sigmoid/softmax here).
        return out.corner_heatmaps, out.mask_logits


@dataclass(frozen=True)
class ExportConfig:
    backbone: str = "large"
    fpn_channels: int = 96


def zero_model_parameters_(model: nn.Module) -> None:
    """Sets all parameters deterministically to 0.

    Used for stable golden samples (without large binary artifacts in the repo).
    """

    for p in model.parameters():
        if p is not None:
            p.detach().zero_()


def create_docquadnet256(
    *,
    config: ExportConfig = ExportConfig(),
    seed: int | None = 0,
    zero_init: bool = False,
) -> DocQuadNet256:
    if seed is not None:
        torch.manual_seed(int(seed))
        np.random.seed(int(seed))

    model = DocQuadNet256(backbone=config.backbone, fpn_channels=int(config.fpn_channels))
    model.eval()
    if zero_init:
        zero_model_parameters_(model)
    return model


def export_model_to_onnx(
    model: DocQuadNet256,
    onnx_path: str | Path,
) -> Path:
    onnx_path = Path(onnx_path)
    onnx_path.parent.mkdir(parents=True, exist_ok=True)

    wrapper = DocQuadNet256OnnxWrapper(model).eval()
    dummy = torch.zeros(1, 3, 256, 256, dtype=torch.float32)

    export_kwargs = dict(
        input_names=["input"],
        output_names=["corner_heatmaps", "mask_logits"],
        export_params=True,
        do_constant_folding=True,
        opset_version=DEFAULT_OPSET_VERSION,
        dynamic_axes=None,
        training=torch.onnx.TrainingMode.EVAL,
    )

    with torch.no_grad():
        # Torch 2.x: The new Dynamo exporter can internally upgrade `opset_version`
        # (e.g. 17 -> 18) and then attempts a version conversion. For deterministic
        # opset pinning tests we explicitly use the legacy exporter.
        try:
            torch.onnx.export(wrapper, dummy, str(onnx_path), dynamo=False, **export_kwargs)
        except TypeError:
            # Older Torch versions don't know `dynamo` and use the legacy path anyway.
            torch.onnx.export(wrapper, dummy, str(onnx_path), **export_kwargs)

    return onnx_path


def export_docquadnet256_to_onnx(
    onnx_path: str | Path,
    *,
    config: ExportConfig = ExportConfig(),
    seed: int | None = 0,
    zero_init: bool = False,
) -> Path:
    """Exports `DocQuadNet256` to ONNX.

    Specification (FIX):
    - Input name: `input`
    - Output names: `corner_heatmaps`, `mask_logits`
    - Input shape: [1,3,256,256] (NCHW), float32
    - Outputs: mask_logits [1,1,64,64], corner_heatmaps [1,4,64,64] (logits)

    Export fixations (M3):
    - `opset_version` is pinned to 17.
    - `do_constant_folding=True`
    - No `dynamic_axes` (fixed shapes).
    """

    onnx_path = Path(onnx_path)
    onnx_path.parent.mkdir(parents=True, exist_ok=True)

    model = create_docquadnet256(config=config, seed=seed, zero_init=zero_init)
    return export_model_to_onnx(model, onnx_path)


def create_docquadnet256_from_checkpoint(
    checkpoint_path: str | Path,
    *,
    backbone: str | None = None,
    map_location: str = "cpu",
) -> DocQuadNet256:
    """Creates a `DocQuadNet256` and loads weights from a training checkpoint.

    Expected format (from `train_docquad_heatmap.py`):
    - `torch.save({"model_state": model.state_dict(), ...}, best.pt)`

    If `backbone` is None, the backbone type is read from the checkpoint's config.
    If the checkpoint does not contain backbone info, defaults to "large".
    """

    checkpoint_path = Path(checkpoint_path)
    obj = torch.load(str(checkpoint_path), map_location=map_location)
    if not isinstance(obj, dict) or "model_state" not in obj:
        raise ValueError(
            f"unsupported checkpoint format at: {checkpoint_path} (expected dict with key 'model_state')"
        )

    cfg = obj.get("config", {})
    fpn_channels = int(cfg.get("fpn_channels", ExportConfig().fpn_channels))

    # Auto-detect backbone from checkpoint config if not explicitly provided
    if backbone is None:
        backbone = str(cfg.get("backbone", "large"))
    else:
        # Warn if CLI backbone differs from checkpoint backbone
        ckpt_backbone = cfg.get("backbone")
        if ckpt_backbone is not None and str(ckpt_backbone) != str(backbone):
            print(
                f"WARNING: --backbone={backbone} differs from checkpoint config backbone={ckpt_backbone}. "
                f"Using --backbone={backbone} as requested, but this may cause load errors if the checkpoint "
                f"was trained with a different backbone."
            )

    model = DocQuadNet256(backbone=str(backbone), fpn_channels=fpn_channels)
    model.load_state_dict(obj["model_state"], strict=True)
    model.eval()
    return model


def export_docquadnet256_checkpoint_to_onnx(
    *,
    checkpoint_path: str | Path,
    onnx_path: str | Path,
    backbone: str | None = None,
) -> Path:
    model = create_docquadnet256_from_checkpoint(checkpoint_path, backbone=backbone)
    return export_model_to_onnx(model, onnx_path)


def _main(argv: list[str]) -> int:
    import argparse

    p = argparse.ArgumentParser(description="Export DocQuadNet256 (trained checkpoint) to ONNX (opset 17, fixed I/O).")
    p.add_argument(
        "--checkpoint",
        required=True,
        help="Path to training checkpoint (e.g. training/runs/.../checkpoints/best.pt)",
    )
    p.add_argument(
        "--out",
        required=True,
        help="Output ONNX path",
    )
    p.add_argument(
        "--backbone",
        default=None,
        help="Backbone type ('large' or 'small'). If not specified, auto-detected from checkpoint config.",
    )
    args = p.parse_args(argv)

    out_path = export_docquadnet256_checkpoint_to_onnx(
        checkpoint_path=args.checkpoint,
        onnx_path=args.out,
        backbone=args.backbone,
    )
    print(f"wrote: {out_path}")
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(_main(sys.argv[1:]))


def load_np_inputs(npz_path: str | Path, keys: Iterable[str]) -> dict[str, np.ndarray]:
    npz_path = Path(npz_path)
    with np.load(npz_path) as z:
        out: dict[str, np.ndarray] = {}
        for k in keys:
            out[k] = np.asarray(z[k], dtype=np.float32)
        return out
