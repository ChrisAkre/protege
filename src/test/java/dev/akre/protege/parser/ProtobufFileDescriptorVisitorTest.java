package dev.akre.protege.parser;

import dev.akre.protege.ProtoUtils;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProtobufFileDescriptorVisitorTest {

    @Test
    public void testGetStringLiteral() {
        // Sanity check
        assertEquals("foo", ProtoUtils.getStringLiteral("\"foo\""));
        assertEquals("foo\nbar", ProtoUtils.getStringLiteral("\"foo\\nbar\""));
        assertEquals("foo\"bar", ProtoUtils.getStringLiteral("\"foo\\\"bar\""));
        assertEquals("foo'bar", ProtoUtils.getStringLiteral("'foo\\'bar'"));
        assertEquals("foo\\bar", ProtoUtils.getStringLiteral("\"foo\\\\bar\""));

        // Fix verification: \\n should become \n (backslash n), not newline.
        assertEquals("foo\\nbar", ProtoUtils.getStringLiteral("\"foo\\\\nbar\""));

        // Unicode verification
        assertEquals("A", ProtoUtils.getStringLiteral("\"\\u0041\""));
        assertEquals("☺", ProtoUtils.getStringLiteral("\"\\u263A\""));
        assertEquals("foo☺bar", ProtoUtils.getStringLiteral("\"foo\\u263Abar\""));

        // Performance loop (reduced iterations for CI)
        List<String> inputs = new ArrayList<>();
        inputs.add("\"simple\"");
        inputs.add("\"longer string with no escapes\"");
        inputs.add("\"string with \\n newline\"");
        inputs.add("\"string with \\\" quote\"");
        inputs.add("\"string with \\\\ backslash\"");
        inputs.add("\"complex \\n \\r \\t \\\" \\' \\\\ string\"");
        inputs.add("'single quoted string'");
        inputs.add("\"string with unicode \\u263A\"");

        for (int i = 0; i < 1000; i++) {
            for (String s : inputs) {
                ProtoUtils.getStringLiteral(s);
            }
        }
    }
}
