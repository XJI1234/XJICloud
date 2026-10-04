# Deprecated

`services/gpu-worker/` is retained only for the legacy Redis worker path. ECI training uses the existing uploaded 3DGS image configured with `XJICLOUD_ECI_MANAGER_TRAINING_IMAGE`; do not build or push an ECI training image from this directory.

The existing `worker_agent.py` and backend Worker API remain available for optional legacy Redis worker use. Enable that path only when ECI dispatch is disabled.
