#!/usr/bin/env bash
cd "$(dirname "$0")/apps/web2"
pnpm exec vite --host 0.0.0.0 --port 5176
