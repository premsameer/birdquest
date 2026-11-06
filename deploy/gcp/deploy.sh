#!/usr/bin/env bash
set -euo pipefail
PROJECT=birdquest-513904
REGION=us-central1
SERVICE=birdquest-recognition
REPO=birdquest
UPSTREAM=6ea2975531847926a4a6d3f03dbda0e5b09663c6
gcloud config set project "$PROJECT"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
if ! gcloud artifacts repositories describe "$REPO" --location="$REGION" >/dev/null 2>&1; then
  gcloud artifacts repositories create "$REPO" --repository-format=docker --location="$REGION"
fi
BUILD_DIR=$(mktemp -d)
trap 'rm -rf "$BUILD_DIR"' EXIT
curl --fail --location --retry 3 "https://github.com/arunrajiah/wildecho-api/archive/$UPSTREAM.tar.gz" | tar -xz --strip-components=1 -C "$BUILD_DIR"
cat >> "$BUILD_DIR/Dockerfile" <<'DOCKER'

# Bundle and checksum-verify weights once at build time.
RUN python scripts/download_model.py && chown -R wildecho:wildecho /app/models
ENV WILDECHO_FEEDBACK_ENABLED=false WILDECHO_FEEDBACK_STORE_AUDIO=false
DOCKER
IMAGE="$REGION-docker.pkg.dev/$PROJECT/$REPO/perch:$UPSTREAM"
gcloud builds submit "$BUILD_DIR" --tag "$IMAGE" --timeout=1800s
gcloud run deploy "$SERVICE" --image="$IMAGE" --region="$REGION" \
  --port=8000 --cpu=2 --memory=2Gi --concurrency=1 \
  --min-instances=0 --max-instances=1 --timeout=120s \
  --no-allow-unauthenticated \
  --set-env-vars='WILDECHO_FEEDBACK_ENABLED=false,WILDECHO_FEEDBACK_STORE_AUDIO=false,WILDECHO_MAX_DURATION_SECONDS=15,WILDECHO_MAX_UPLOAD_BYTES=5242880,WILDECHO_ONNX_INTRA_OP_THREADS=2,WILDECHO_CORS_ORIGINS=https://appassets.androidplatform.net'
URL=$(gcloud run services describe "$SERVICE" --region="$REGION" --format='value(status.url)')
printf '\nPrivate recognition service: %s\n' "$URL"
printf 'Check model readiness using: gcloud run services proxy birdquest-recognition --region=us-central1 --port=8080\nThen in another Cloud Shell terminal: curl http://localhost:8080/v1/health\n'
