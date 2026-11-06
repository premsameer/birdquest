#!/usr/bin/env bash
set -euo pipefail
PROJECT=birdquest-513904
REGION=us-central1
ACCOUNT=birdquest-gateway
SA="$ACCOUNT@$PROJECT.iam.gserviceaccount.com"
gcloud config set project "$PROJECT"
if ! gcloud iam service-accounts describe "$SA" >/dev/null 2>&1; then
 gcloud iam service-accounts create "$ACCOUNT" --display-name='Birdquest authenticated gateway'
fi
gcloud run services add-iam-policy-binding birdquest-recognition --region="$REGION" --member="serviceAccount:$SA" --role=roles/run.invoker
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
IMAGE="$REGION-docker.pkg.dev/$PROJECT/birdquest/gateway:v1"
gcloud builds submit "$ROOT/server/gateway" --tag="$IMAGE"
gcloud run deploy birdquest-gateway --image="$IMAGE" --region="$REGION" --service-account="$SA" --allow-unauthenticated --cpu=1 --memory=512Mi --concurrency=1 --min-instances=0 --max-instances=1 --timeout=120s --set-env-vars='GOOGLE_CLOUD_PROJECT=birdquest-513904,RECOGNITION_URL=https://birdquest-recognition-2cop3owhka-uc.a.run.app,ALLOWED_EMAILS=samsun24@gmail.com'
gcloud run services describe birdquest-gateway --region="$REGION" --format='value(status.url)'
