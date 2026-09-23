from __future__ import annotations

from dataclasses import dataclass

import torch
from torch.utils.data import DataLoader

from training.docquad_m2.losses import corners_bce_logits_loss, mask_bce_logits_loss
from training.docquad_m2.model import DocQuadNet256


@dataclass(frozen=True)
class TrainStats:
    loss_start: float
    loss_end: float


def overfit_sanity(
    model: DocQuadNet256,
    loader: DataLoader,
    phase: int,
    steps: int = 25,
    lr: float = 1e-3,
    device: str = "cpu",
) -> TrainStats:
    """Kurzer CPU-Overfit-Sanity-Run.

    Phase:
    - 1: nur Corner-Loss
    - 2: Corner + Mask
    """
    if phase not in (1, 2):
        raise ValueError("phase must be 1 or 2")
    if steps <= 0:
        raise ValueError("steps must be > 0")

    model = model.to(device)
    model.train()
    opt = torch.optim.AdamW(model.parameters(), lr=lr)

    it = iter(loader)
    loss_start = None
    loss_last = None

    for step in range(int(steps)):
        try:
            batch = next(it)
        except StopIteration:
            it = iter(loader)
            batch = next(it)

        x = batch["input"].to(device)
        tgt_corner = batch["corner_heatmaps"].to(device)
        tgt_mask = batch["mask"].to(device)

        out = model(x)
        loss = corners_bce_logits_loss(out.corner_heatmaps, tgt_corner)
        if phase == 2:
            loss = loss + mask_bce_logits_loss(out.mask_logits, tgt_mask)

        opt.zero_grad(set_to_none=True)
        loss.backward()
        opt.step()

        loss_last = float(loss.detach().cpu().item())
        if loss_start is None:
            loss_start = loss_last

    assert loss_start is not None and loss_last is not None
    return TrainStats(loss_start=loss_start, loss_end=loss_last)
