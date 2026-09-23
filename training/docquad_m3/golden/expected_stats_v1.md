Dieses Golden-Reference-File (`expected_stats_v1.json`) ist **versioniert** und
legt zusätzlich zur reinen Zahl explizit fest, **wie** Kennzahlen berechnet
werden, damit Tests nicht durch spätere, implizite Änderungen „zufällig“ anders
ausfallen.

#### `mask_area` (v1)

- Basis ist `mask_logits` (ONNX Output, Logits).
- Definition:
  - `mask_prob = sigmoid(mask_logits)`
  - `mask_bin = mask_prob > 0.5` (**strict** `>`)
  - `mask_area = sum(mask_bin)` (Anzahl `True`-Pixel in `1×1×64×64`)

Hinweis: Bei einem Zero-Init-Modell ist `mask_logits == 0` → `sigmoid(0) == 0.5`
und wegen strict `>` ist `mask_bin` überall `False` → `mask_area == 0`.
