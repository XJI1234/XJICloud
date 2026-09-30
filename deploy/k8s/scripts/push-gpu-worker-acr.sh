#!/usr/bin/env bash
# Deprecated compatibility shim. Training images are managed outside this repository.
set -euo pipefail

if [[ -z "${XJICLOUD_ECI_MANAGER_TRAINING_IMAGE:-}" ]]; then
  echo "This script no longer builds or pushes training images." >&2
  echo "Set XJICLOUD_ECI_MANAGER_TRAINING_IMAGE to the already-uploaded 3DGS image in backend configuration." >&2
  exit 1
fi

echo "An existing 3DGS training image is configured. No image was built or pushed."
