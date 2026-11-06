package app.birdquest.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import android.webkit.WebMessage;
import android.webkit.WebMessagePort;



import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private static final String START_URL = ORIGIN + "/assets/www/index.html";
    private static final int AUDIO = 20, LOCATION = 21, EXPORT = 22;
    private WebView webView;
    private PermissionRequest microphoneRequest;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private byte[] pendingExport;
    private boolean foreground;
    private boolean runtimePermissionPending;

    private boolean trusted(Uri uri) {
        return uri != null && TrustedOrigin.matches(uri.toString());
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.setBackgroundColor(0xfff7f8f3);
        setContentView(webView);
        // Keep controls clear of system bars, including Android 15 edge-to-edge windows.
        webView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        webView.requestApplyInsets();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (!START_URL.equals(url)) return;
                WebMessagePort[] ports = view.createWebMessageChannel();
                ports[0].setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                    @Override public void onMessage(WebMessagePort port, WebMessage message) { exportMessage(message.getData()); }
                });
                view.evaluateJavascript("window.addEventListener('message',function(e){if(e.data==='birdquest-native'&&e.ports.length===1){window.BirdquestAndroid={postMessage:function(s){e.ports[0].postMessage(s)}}}})", ignored -> view.postWebMessage(new WebMessage("birdquest-native",new WebMessagePort[]{ports[1]}),Uri.parse(ORIGIN)));
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                WebResourceResponse response = localAsset(request.getUrl());
                if (response != null) return response;
                // Never fall through to the network for an unrecognized local asset.
                if (trusted(request.getUrl())) return new WebResourceResponse("text/plain", "UTF-8",
                        404, "Not Found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (trusted(uri) && uri.getPath() != null && uri.getPath().startsWith("/assets/www/")) return false;
                if (request.isForMainFrame() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) { message("No browser is available to open this link."); }
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!foreground || !trusted(request.getOrigin()) || microphoneRequest != null || runtimePermissionPending) {
                        request.deny(); return;
                    }
                    boolean audioOnly = request.getResources().length == 1
                            && PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(request.getResources()[0]);
                    if (!audioOnly) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        microphoneRequest = request;
                        runtimePermissionPending = true;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO);
                    }
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (microphoneRequest == request) microphoneRequest = null;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!foreground || !trusted(Uri.parse(origin)) || locationCallback != null || runtimePermissionPending) {
                    callback.invoke(origin, false, false); return;
                }
                if (hasLocation()) { callback.invoke(origin, true, false); return; }
                locationOrigin = origin;
                locationCallback = callback;
                runtimePermissionPending = true;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION);
            }
            @Override public void onGeolocationPermissionsHidePrompt() {
                if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
                locationCallback = null;
            }
        });
        webView.setDownloadListener((url, agent, disposition, mimeType, length) ->
                message("Update Android System WebView to export your journal."));
        webView.loadUrl(START_URL);
    }

    private WebResourceResponse localAsset(Uri uri) {
        if (!trusted(uri)) return null;
        String path = uri.getPath();
        if (path == null || !path.startsWith("/assets/www/") || path.contains("..")) return null;
        String name = path.substring("/assets/".length());
        String mime = name.endsWith(".html") ? "text/html" : name.endsWith(".js") ? "application/javascript" : name.endsWith(".css") ? "text/css" : name.endsWith(".json") || name.endsWith(".webmanifest") ? "application/json" : name.endsWith(".svg") ? "image/svg+xml" : "application/octet-stream";
        try { return new WebResourceResponse(mime,"UTF-8",200,"OK",Collections.singletonMap("Content-Security-Policy","frame-src 'none'; object-src 'none'"),getAssets().open(name)); }
        catch (Exception missing) { return null; }
    }
    private void exportMessage(String payload) {
        try {
            JSONObject data = new JSONObject(payload);
            if (!"export-csv".equals(data.optString("type")) || pendingExport != null) return;
            pendingExport = ExportPayload.csvBytes(data.getString("csv"));
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE,"birdquest-sightings.csv");
            startActivityForResult(intent,EXPORT);
        } catch (Exception error) { pendingExport=null; message("Could not export journal."); }
    }

    private boolean hasLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        runtimePermissionPending = false;
        if (requestCode == AUDIO && microphoneRequest != null) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                microphoneRequest.deny(); microphoneRequest = null;
                message("Enable Microphone in Android Settings > Apps > Birdquest > Permissions, then try again.");
            } else if (foreground) grantPendingMicrophone();
        }
        if (requestCode == LOCATION && locationCallback != null) {
            locationCallback.invoke(locationOrigin, foreground && hasLocation(), false);
            locationCallback = null;
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT) return;
        byte[] bytes = pendingExport;
        pendingExport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
        Uri destination = data.getData();
        new Thread(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new java.io.IOException("Document is unavailable.");
                output.write(bytes);
                runOnUiThread(() -> message("Journal saved."));
            } catch (Exception error) { runOnUiThread(() -> message("Could not save the journal.")); }
        }).start();
    }
    private void grantPendingMicrophone() {
        if (microphoneRequest != null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            microphoneRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            microphoneRequest = null;
        }
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        if (webView != null) webView.onResume();
        if (!runtimePermissionPending) grantPendingMicrophone();
    }
    @Override protected void onPause() {
        // Permission dialogs can pause the Activity; do not cancel a request while Android asks the user.
        if (!runtimePermissionPending) {
            foreground = false;
            if (webView != null) webView.evaluateJavascript("window.dispatchEvent(new Event('birdquest-background'))", null);
            if (microphoneRequest != null) { microphoneRequest.deny(); microphoneRequest = null; }
            if (locationCallback != null) { locationCallback.invoke(locationOrigin, false, false); locationCallback = null; }
        }
        if (webView != null && !runtimePermissionPending) webView.onPause();
        super.onPause();
    }
    @Override protected void onStop() {
        if (runtimePermissionPending) { super.onStop(); return; }
        foreground = false;
        if (webView != null) webView.evaluateJavascript("window.dispatchEvent(new Event('birdquest-background'))", null);
        super.onStop();
    }
    @Override public void onBackPressed() {
        webView.evaluateJavascript("(()=>{let d=document.querySelector('dialog[open]');if(d){d.close();return true}return false})()",
                value -> { if (!"true".equals(value)) finish(); });
    }
    @Override protected void onDestroy() {
        if (microphoneRequest != null) microphoneRequest.deny();
        if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
