#!/usr/bin/env bash
# Self-signed development certificate for the optional TLS front door. Never commit the output.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p certs
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days 30 \
  -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
  -keyout certs/relay.key -out certs/relay.crt
echo "wrote docker/nginx/certs/relay.{crt,key}"
