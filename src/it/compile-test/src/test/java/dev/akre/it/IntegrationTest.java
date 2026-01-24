package dev.akre.it;

import dev.akre.it.IntegrationProtos;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

public class IntegrationTest {

    @Test
    public void testGeneratedMessage() {
        IntegrationProtos.SampleMessage message = IntegrationProtos.SampleMessage.newBuilder()
                .setId("ID-123")
                .setName("Integration Test")
                .setStatus(IntegrationProtos.Status.ACTIVE)
                .addTags("test")
                .addTags("integration")
                .putMetadata("key1", "value1")
                .setCreatedAt(IntegrationProtos.Timestamp.newBuilder()
                        .setSeconds(123456789L)
                        .setNanos(456)
                        .build())
                .build();

        assertThat(message.getId()).isEqualTo("ID-123");
        assertThat(message.getName()).isEqualTo("Integration Test");
        assertThat(message.getStatus()).isEqualTo(IntegrationProtos.Status.ACTIVE);
        assertThat(message.getTagsList()).containsExactly("test", "integration");
        assertThat(message.getMetadataCount()).isEqualTo(1);
        assertThat(message.getMetadataMap().get("key1")).isEqualTo("value1");
        assertThat(message.getCreatedAt().getSeconds()).isEqualTo(123456789L);
        assertThat(message.getCreatedAt().getNanos()).isEqualTo(456);

        // Test serialization/deserialization
        byte[] data = message.toByteArray();
        try {
            IntegrationProtos.SampleMessage parsed = IntegrationProtos.SampleMessage.parseFrom(data);
            assertThat(parsed.getName()).isEqualTo(message.getName());
            assertThat(parsed.getStatus()).isEqualTo(message.getStatus());
            assertThat(parsed.getTagsList()).isEqualTo(message.getTagsList());
            assertThat(parsed.getCreatedAt().getSeconds()).isEqualTo(message.getCreatedAt().getSeconds());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
