package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class DeprecatedIntegrationTest {

    @Test
    public void testDeprecatedFields() {
        DeprecatedProtos.DeprecatedMessage message = DeprecatedProtos.DeprecatedMessage.newBuilder()
                .setOldField("old")
                .setNewField("new")
                .build();

        assertThat(message.getOldField()).isEqualTo("old");
        assertThat(message.getNewField()).isEqualTo("new");
    }

    @Test
    public void testDeprecatedEnum() {
        DeprecatedProtos.DeprecatedEnum e = DeprecatedProtos.DeprecatedEnum.OLD_VALUE;
        assertThat(e).isEqualTo(DeprecatedProtos.DeprecatedEnum.OLD_VALUE);
    }
}
