#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import random
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import torch
import torch.nn as nn
from PIL import Image, ImageDraw

# If the script is executed directly via `python training/scripts/...py`,
# `sys.path[0]` will be the scripts directory. To make `import training.*`
# work reliably, we deterministically add the repo root to `sys.path`.
_REPO_ROOT = Path(__file__).resolve().parents[2]
if str(_REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(_REPO_ROOT))

from training.docquad_m1.letterbox import LetterboxTransform


# Smoke training for corner regression (4 points => 8 values).
#
# Important:
# - Preprocessing is LETTERBOX to 256×256 (no stretching).
# - Corner coordinates are mapped into 256-space using the same letterbox transform.
# - Targets are then normalized to [0,1] relative to 256-space (divide by 255.0).
#
# Note on rasterization/rounding:
# PIL resize operates on integer pixel grids; paste offsets are rounded to integers.
# The corner transform uses `LetterboxTransform` (float) from M1.
# This can introduce a deviation of <= ~0.5 px; for smoke training this is acceptable.


IN_SIZE = 256
NORM_DENOM = 255.0  # Spec convention: valid 256-space range is [0,255] inclusive.


def _set_determinism(seed: int = 0) -> None:
    os.environ.setdefault("PYTHONHASHSEED", str(int(seed)))
    random.seed(int(seed))
    torch.manual_seed(int(seed))
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(int(seed))

    # Enforce determinism where feasible.
    try:
        torch.use_deterministic_algorithms(True)
    except Exception:
        pass

    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True


def _auto_device() -> torch.device:
    if torch.cuda.is_available():
        return torch.device("cuda")
    # Mac: MPS
    if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def _read_split_list(path: Path) -> list[str]:
    names: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s:
            continue
        names.append(s)
    return names


def _load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


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
        if not isinstance(x, (int, float)) or not isinstance(y, (int, float)):
            raise ValueError("corner_not_numeric")
        out.append((float(x), float(y)))
    return out


def _pil_to_chw_float01(img_rgb: Image.Image) -> torch.Tensor:
    # Minimal dependencies: only Pillow + torch.
    if img_rgb.mode != "RGB":
        img_rgb = img_rgb.convert("RGB")
    w, h = img_rgb.size
    raw = img_rgb.tobytes()  # RGB interleaved
    try:
        t = torch.frombuffer(memoryview(raw), dtype=torch.uint8)
    except Exception:
        # Fallback (slower, but robust)
        t = torch.tensor(list(raw), dtype=torch.uint8)
    t = t.reshape(h, w, 3).permute(2, 0, 1).contiguous()
    return t.to(dtype=torch.float32) / 255.0


@dataclass(frozen=True)
class LoadedSample:
    name: str
    img_256: Image.Image  # RGB, 256×256, letterboxed
    x: torch.Tensor  # [3,256,256] float32 0..1
    y_norm: torch.Tensor  # [8] float32 in 0..1 (256-space normalized)
    # For metrics/visualization:
    src_w: int
    src_h: int
    corners_src: torch.Tensor  # [4,2] float32 (original pixel space)
    letterbox: LetterboxTransform


class DocQuadCornersDataset(torch.utils.data.Dataset):
    def __init__(self, base_dir: Path, names: list[str]):
        self.base_dir = base_dir
        self.images_dir = base_dir / "images"
        self.labels_dir = base_dir / "labels"
        self.names = list(names)

    def __len__(self) -> int:
        return len(self.names)

    def __getitem__(self, idx: int) -> LoadedSample:
        name = self.names[int(idx)]
        img_path = self.images_dir / name
        label_path = self.labels_dir / (Path(name).stem + ".json")

        if not img_path.exists():
            raise FileNotFoundError(f"missing image: {img_path}")
        if not label_path.exists():
            raise FileNotFoundError(f"missing label: {label_path}")

        label = _load_json(label_path)
        if label.get("image", None) != name:
            raise ValueError(f"label image field mismatch: file={name} json.image={label.get('image', None)}")

        w = int(label.get("width"))
        h = int(label.get("height"))
        corners = _extract_corners_px(label)

        with Image.open(img_path) as im0:
            im0 = im0.convert("RGB")
            if im0.size != (w, h):
                raise ValueError(f"image size mismatch for {name}: label=({w},{h}) file={im0.size}")

            letterbox = LetterboxTransform.create(w, h, IN_SIZE, IN_SIZE)
            # Resize + paste
            resized_w = int(round(w * letterbox.scale))
            resized_h = int(round(h * letterbox.scale))
            resized = im0.resize((resized_w, resized_h), resample=Image.BILINEAR)
            canvas = Image.new("RGB", (IN_SIZE, IN_SIZE), (0, 0, 0))
            paste_x = int(round(letterbox.offset_x))
            paste_y = int(round(letterbox.offset_y))
            canvas.paste(resized, (paste_x, paste_y))

        # Corners in src space
        corners_src = torch.tensor(corners, dtype=torch.float32)  # [4,2]
        # Map corners to 256-space (float)
        corners_256_np = letterbox.forward(corners_src.detach().cpu().numpy())  # float64
        corners_256 = torch.tensor(corners_256_np, dtype=torch.float32)
        corners_256 = torch.clamp(corners_256, 0.0, float(IN_SIZE - 1))

        # Normalized target in 0..1 using 256-space denom 255.
        y_norm = (corners_256.reshape(-1) / float(NORM_DENOM)).to(dtype=torch.float32)
        y_norm = torch.clamp(y_norm, 0.0, 1.0)

        x = _pil_to_chw_float01(canvas)
        if x.shape != (3, IN_SIZE, IN_SIZE):
            raise RuntimeError(f"internal error: unexpected tensor shape {tuple(x.shape)}")

        return LoadedSample(
            name=name,
            img_256=canvas,
            x=x,
            y_norm=y_norm,
            src_w=w,
            src_h=h,
            corners_src=corners_src,
            letterbox=letterbox,
        )


def _collate(batch: list[LoadedSample]) -> dict[str, Any]:
    x = torch.stack([b.x for b in batch], dim=0)
    y = torch.stack([b.y_norm for b in batch], dim=0)
    return {
        "x": x,
        "y": y,
        "meta": batch,
    }


class TinyCornerRegressor(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(3, 16, kernel_size=5, stride=2, padding=2),
            nn.ReLU(inplace=True),
            nn.Conv2d(16, 32, kernel_size=3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(32, 64, kernel_size=3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.Conv2d(64, 128, kernel_size=3, stride=2, padding=1),
            nn.ReLU(inplace=True),
            nn.AdaptiveAvgPool2d((1, 1)),
            nn.Flatten(),
            nn.Linear(128, 8),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        y = self.net(x)
        # Targets are in [0,1]; sigmoid keeps outputs in a similar range (more stable smoke training).
        return torch.sigmoid(y)


@dataclass(frozen=True)
class EpochStats:
    loss: float
    mae_norm: float
    mae_px: float


def _mae_metrics(
    pred_norm: torch.Tensor,
    tgt_norm: torch.Tensor,
    meta: list[LoadedSample],
) -> tuple[float, float]:
    # MAE_norm: mean abs in normalized space (over 8 values)
    mae_norm = float(torch.mean(torch.abs(pred_norm - tgt_norm)).detach().cpu().item())

    # MAE_px: in the original image (src space), mean abs over 8 values
    pred_norm_np = pred_norm.detach().cpu()
    tgt_norm_np = tgt_norm.detach().cpu()

    abs_err_sum = 0.0
    count = 0
    for i, m in enumerate(meta):
        pred_256 = (pred_norm_np[i].reshape(4, 2) * float(NORM_DENOM)).numpy()
        tgt_256 = (tgt_norm_np[i].reshape(4, 2) * float(NORM_DENOM)).numpy()

        pred_src = m.letterbox.inverse(pred_256).astype(float)
        tgt_src = m.letterbox.inverse(tgt_256).astype(float)

        d = abs(pred_src - tgt_src).reshape(-1)
        abs_err_sum += float(d.sum())
        count += int(d.size)

    mae_px = abs_err_sum / float(max(1, count))
    return mae_norm, mae_px


def _draw_quad(draw: ImageDraw.ImageDraw, pts: list[tuple[float, float]], color: tuple[int, int, int], width: int = 2) -> None:
    if len(pts) != 4:
        return
    poly = pts + [pts[0]]
    draw.line(poly, fill=color, width=width)
    r = 3
    for (x, y) in pts:
        draw.ellipse((x - r, y - r, x + r, y + r), outline=color, width=2)


def _render_fixed_samples(
    *,
    epoch: int,
    out_dir: Path,
    model: nn.Module,
    device: torch.device,
    fixed_samples: list[LoadedSample],
) -> None:
    vis_dir = out_dir / "smoke_vis" / f"epoch_{epoch:02d}"
    vis_dir.mkdir(parents=True, exist_ok=True)

    model.eval()
    with torch.no_grad():
        for s in fixed_samples:
            x = s.x.unsqueeze(0).to(device)
            pred_norm = model(x).detach().cpu().reshape(4, 2)
            pred_256 = (pred_norm * float(NORM_DENOM)).clamp(0.0, float(IN_SIZE - 1)).numpy()

            gt_norm = s.y_norm.detach().cpu().reshape(4, 2)
            gt_256 = (gt_norm * float(NORM_DENOM)).clamp(0.0, float(IN_SIZE - 1)).numpy()

            img = s.img_256.copy()
            d = ImageDraw.Draw(img)
            _draw_quad(d, [(float(x), float(y)) for x, y in gt_256], (0, 255, 0), width=2)  # GT green
            _draw_quad(d, [(float(x), float(y)) for x, y in pred_256], (255, 0, 0), width=2)  # Pred red
            img.save(vis_dir / f"{Path(s.name).stem}.png")


def _run_epoch(
    *,
    model: nn.Module,
    loader: torch.utils.data.DataLoader,
    device: torch.device,
    opt: torch.optim.Optimizer | None,
    loss_fn: nn.Module,
) -> EpochStats:
    is_train = opt is not None
    if is_train:
        model.train()
    else:
        model.eval()

    total_loss = 0.0
    total_mae_norm = 0.0
    total_mae_px = 0.0
    n_batches = 0

    for batch in loader:
        x = batch["x"].to(device)
        y = batch["y"].to(device)
        meta = batch["meta"]

        if is_train:
            opt.zero_grad(set_to_none=True)

        with torch.set_grad_enabled(is_train):
            pred = model(x)
            loss = loss_fn(pred, y)
            if is_train:
                loss.backward()
                opt.step()

        mae_norm, mae_px = _mae_metrics(pred, y, meta)
        total_loss += float(loss.detach().cpu().item())
        total_mae_norm += mae_norm
        total_mae_px += mae_px
        n_batches += 1

    denom = float(max(1, n_batches))
    return EpochStats(
        loss=total_loss / denom,
        mae_norm=total_mae_norm / denom,
        mae_px=total_mae_px / denom,
    )


def main(argv: list[str]) -> int:
    p = argparse.ArgumentParser(description="DocQuad Smoke-Training (Corner Regression 8 Werte)")
    p.add_argument("--base_dir", type=Path, default=Path("training/data/docquad_uvdoc_all_trainable"))
    p.add_argument("--train_split", type=Path, default=None)
    p.add_argument("--val_split", type=Path, default=None)
    p.add_argument("--epochs", type=int, default=10)
    p.add_argument("--batch", type=int, default=8)
    p.add_argument("--lr", type=float, default=1e-3)
    p.add_argument("--device", type=str, default="auto", choices=["auto", "cpu", "cuda", "mps"])
    p.add_argument("--out_dir", type=Path, default=Path("training/runs/docquad_smoke"))

    args = p.parse_args(argv)

    base_dir: Path = args.base_dir
    train_split = args.train_split or (base_dir / "split_train.txt")
    val_split = args.val_split or (base_dir / "split_val.txt")

    if not base_dir.exists():
        raise FileNotFoundError(f"base_dir not found: {base_dir}")
    if not train_split.exists():
        raise FileNotFoundError(f"train_split not found: {train_split}")
    if not val_split.exists():
        raise FileNotFoundError(f"val_split not found: {val_split}")

    _set_determinism(seed=0)

    if args.device == "auto":
        device = _auto_device()
    else:
        device = torch.device(args.device)

    out_dir: Path = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "smoke_vis").mkdir(parents=True, exist_ok=True)

    train_names = _read_split_list(train_split)
    val_names = _read_split_list(val_split)
    if not train_names or not val_names:
        raise ValueError("split files must contain at least one sample name")

    ds_train = DocQuadCornersDataset(base_dir, train_names)
    ds_val = DocQuadCornersDataset(base_dir, val_names)

    gen = torch.Generator()
    gen.manual_seed(0)

    train_loader = torch.utils.data.DataLoader(
        ds_train,
        batch_size=int(args.batch),
        shuffle=True,
        num_workers=0,
        collate_fn=_collate,
        generator=gen,
        drop_last=False,
    )
    val_loader = torch.utils.data.DataLoader(
        ds_val,
        batch_size=int(args.batch),
        shuffle=False,
        num_workers=0,
        collate_fn=_collate,
        drop_last=False,
    )

    # 8 fixed val samples for visualization: deterministically the first 8 from split_val.
    fixed_names = val_names[:8]
    fixed_samples = [ds_val[val_names.index(n)] for n in fixed_names]

    model = TinyCornerRegressor().to(device)
    loss_fn = nn.SmoothL1Loss(beta=0.01)
    opt = torch.optim.AdamW(model.parameters(), lr=float(args.lr))

    log_path = out_dir / "metrics.jsonl"
    with log_path.open("w", encoding="utf-8") as f:
        for epoch in range(1, int(args.epochs) + 1):
            train_stats = _run_epoch(model=model, loader=train_loader, device=device, opt=opt, loss_fn=loss_fn)
            val_stats = _run_epoch(model=model, loader=val_loader, device=device, opt=None, loss_fn=loss_fn)

            rec = {
                "epoch": epoch,
                "train": {"loss": train_stats.loss, "mae_norm": train_stats.mae_norm, "mae_px": train_stats.mae_px},
                "val": {"loss": val_stats.loss, "mae_norm": val_stats.mae_norm, "mae_px": val_stats.mae_px},
                "device": str(device),
            }
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            f.flush()

            print(
                f"epoch {epoch:02d} | "
                f"train loss={train_stats.loss:.6f} mae_norm={train_stats.mae_norm:.6f} mae_px={train_stats.mae_px:.3f} | "
                f"val loss={val_stats.loss:.6f} mae_norm={val_stats.mae_norm:.6f} mae_px={val_stats.mae_px:.3f}",
                flush=True,
            )

            _render_fixed_samples(epoch=epoch, out_dir=out_dir, model=model, device=device, fixed_samples=fixed_samples)

    print(f"wrote: {log_path}")
    print(f"wrote: {out_dir / 'smoke_vis'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
