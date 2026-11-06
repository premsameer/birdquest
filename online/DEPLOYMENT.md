# Perch online recognition — pending hosting

Prepared adapter: perch-client.js. Not activated in the APK; the current APK still uses BirdNET.

Candidate server: https://github.com/arunrajiah/wildecho-api
Uses Google Perch 2.0 through ONNX. Its documented API accepts multipart audio at POST /v1/identify and exposes model_loaded at GET /v1/health.

Deployment needs an HTTPS container host with approximately 2 GB RAM, ffmpeg, and model weights (~390 MiB). No hosting credentials are configured in the current environment. Do not treat this workspace as a permanent public server.

Production configuration should cap uploads at 5 MB and clips at 15 seconds, disable feedback/audio retention, restrict CORS to https://appassets.androidplatform.net and the actual web-app origin, and configure rate limiting and authentication at the gateway. Do not embed a shared privileged server key in the APK.

Before activation:
- Verify model_loaded=true, model provenance and label alignment.
- Compare known bird recordings, silence, speech, overlapping calls and noise. The wrapper uses softmax ranking scores rather than calibrated probabilities; it does not currently apply geography filtering.
- Validate Android WebView uploads and CORS against the deployed host.
- Change capture and journal attribution to the returned provider, label values as model scores, and disclose that audio is uploaded.
- Record at least 5 seconds, retaining a 12-second maximum.
- Test against the user's region and examples that BirdNET misidentified before claiming improved accuracy.

No public server has been deployed and no recordings have been uploaded.
