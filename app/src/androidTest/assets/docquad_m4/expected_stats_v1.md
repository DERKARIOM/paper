Dieses Golden-Reference-File (`expected_stats_v1.json`) wird von der Android-Instrumentation-Test-Suite
für `DocQuadNet256` verwendet.

#### Definition `mask_area` (v1, FIX)
- `mask_prob = sigmoid(mask_logits)`
- `mask_bin = mask_prob > 0.5` (**strict** `>`)
- `mask_area = sum(mask_bin)` (Anzahl `True`-Pixel in `1×1×64×64`)

Hinweis: Bei einem Zero-Init-Modell ist `mask_logits == 0` → `sigmoid(0) == 0.5` und wegen strict `>`
ist `mask_bin` überall `False` → `mask_area == 0`.
