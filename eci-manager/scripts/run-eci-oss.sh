#!/usr/bin/env bash
# Run Python with the eci-oss conda env (Miniconda).
set -euo pipefail

CONDA_PY="${HOME}/miniconda3/envs/eci-oss/bin/python"
if [[ ! -x "${CONDA_PY}" ]]; then
  echo "eci-oss env not found. Install Miniconda and run:" >&2
  echo "  conda create -n eci-oss python=3.12 -y && conda activate eci-oss && pip install oss2" >&2
  exit 1
fi

exec "${CONDA_PY}" "$@"
