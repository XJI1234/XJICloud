#!/usr/bin/env bash
# Run the training dispatch integration against an already-running real-cloud stack.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
# shellcheck disable=SC1091
source "$REPO_ROOT/eci-manager/scripts/load-env.sh"

if [[ -z "${TRAINING_TEST_DATASET_DIR:-}" ]]; then
  printf '%s\n' 'TRAINING_TEST_DATASET_DIR is required' >&2
  exit 2
fi

PRESIGN_MINUTES="${ECI_OSS_PRESIGN_EXPIRATION_MINUTES:-60}"
if [[ ! "$PRESIGN_MINUTES" =~ ^[0-9]{1,9}$ ]] || (( 10#$PRESIGN_MINUTES < 1440 )); then
  printf '%s\n' 'ECI_OSS_PRESIGN_EXPIRATION_MINUTES must be an integer of at least 1440 for cloud dispatch.' >&2
  exit 2
fi
printf '%s\n' "Local env has ECI_OSS_PRESIGN_EXPIRATION_MINUTES=${PRESIGN_MINUTES}; verify the running ECI Manager uses this same value (the effective remote setting cannot be queried by this script)." >&2

export TRAINING_TEST_ECI_BASE="${TRAINING_TEST_ECI_BASE:-${ECI_BASE:-${BASE:-http://127.0.0.1:8081}}}"
export ECI_API_KEY
exec python3 "$SCRIPT_DIR/integration_training_dispatch.py" --mode cloud
