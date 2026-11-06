package app.birdquest.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class ExportPayloadTest {
    @Test public void preservesUnicodeAndNewlines() {
        String csv = "Species,Notes\r\nRobin,Chirp 🐦\r\n";
        assertEquals(csv, new String(ExportPayload.csvBytes(csv), StandardCharsets.UTF_8));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsEmpty() { ExportPayload.csvBytes(""); }
    @Test(expected = IllegalArgumentException.class) public void rejectsOversizedUnicode() {
        ExportPayload.csvBytes("鳥".repeat(ExportPayload.MAX_BYTES / 3 + 1));
    }
}
