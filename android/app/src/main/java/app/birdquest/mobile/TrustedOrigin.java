package app.birdquest.mobile;

import java.net.URI;

final class TrustedOrigin {
    static boolean matches(String value) {
        if (value == null) return false;
        try {
            URI uri = new URI(value);
            return "https".equals(uri.getScheme())
                    && "appassets.androidplatform.net".equals(uri.getHost())
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443);
        } catch (Exception invalid) { return false; }
    }
}
