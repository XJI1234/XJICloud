#!/usr/bin/env bash
# Submit through an already-running Mock stack. The script stops after
# backend accepts the uploaded dataset and returns QUEUED.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
source "$REPO_ROOT/eci-manager/scripts/load-env.sh"

if [[ -z "${TRAINING_TEST_DATASET_DIR:-}" ]]; then
  printf '%s\n' 'TRAINING_TEST_DATASET_DIR is required' >&2
  exit 2
fi

export TRAINING_TEST_ECI_BASE="${TRAINING_TEST_ECI_BASE:-${ECI_BASE:-${BASE:-http://127.0.0.1:8081}}}"
export ECI_API_KEY
exec python3 "$SCRIPT_DIR/integration_training_dispatch.py" --mode mock
