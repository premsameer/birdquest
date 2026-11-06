# Google Cloud deployment

Run deploy.sh from an authenticated Google Cloud Shell with billing enabled for birdquest-513904. It enables required APIs, creates an Artifact Registry repository, builds a pinned third-party Perch service with checksum-verified model weights, and deploys a private Cloud Run service (2 CPUs, 2 GiB, concurrency 1, min 0, max 1). Builds, storage and execution may incur charges. No budget alert or hard spending cap is created.

The caller needs permissions to enable services, create repositories, run builds, deploy Cloud Run and act as its runtime service account. Cloud Build's configured service account must be allowed to write to Artifact Registry. If deployment fails, report the actual error rather than granting broad project Owner access.

This is the initial private backend, not the completed mobile integration. Verify model_loaded=true through the authenticated Cloud Run proxy and test known recordings. The APK cannot call this IAM-protected service yet. Configure mobile user authentication and a validating gateway before connecting it; do not embed a service-account key in the APK or turn off authentication as a workaround.

Source: https://github.com/arunrajiah/wildecho-api commit 6ea2975531847926a4a6d3f03dbda0e5b09663c6. Upstream licenses/notices stay in its build context. Current wrapper uses softmax ranking scores and no geographic filtering. Improved accuracy is unverified.
