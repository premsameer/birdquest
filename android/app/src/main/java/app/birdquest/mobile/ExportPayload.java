package app.birdquest.mobile;

import java.nio.charset.StandardCharsets;

/** Restrict the bridge to a bounded CSV document; no filesystem paths from JavaScript. */
final class ExportPayload {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static byte[] csvBytes(String csv) {
        if (csv == null || csv.isEmpty() || csv.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Journal is empty or too large to export.");
        }
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Journal exceeds 5 MB.");
        return bytes;
    }
}
