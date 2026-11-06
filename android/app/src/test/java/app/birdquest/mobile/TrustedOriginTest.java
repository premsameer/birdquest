package app.birdquest.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class TrustedOriginTest {
    @Test public void permitsLocalHttpsOnly() {
        assertTrue(TrustedOrigin.matches("https://appassets.androidplatform.net/assets/www/index.html"));
        assertTrue(TrustedOrigin.matches("https://appassets.androidplatform.net:443"));
    }
    @Test public void rejectsLookalikesAndNetworkOrigins() {
        for (String value : new String[]{null, "", "http://appassets.androidplatform.net", "file:///assets/www/index.html",
                "https://appassets.androidplatform.net.evil.test", "https://appassets.androidplatform.net@evil.test",
                "https://evil.test@appassets.androidplatform.net", "https://appassets.androidplatform.net:444",
                "https://example.com", "javascript:alert(1)"}) {
            assertFalse(String.valueOf(value), TrustedOrigin.matches(value));
        }
    }
}
