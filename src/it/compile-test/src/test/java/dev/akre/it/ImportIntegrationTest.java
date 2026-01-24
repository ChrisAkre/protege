package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ImportIntegrationTest {

    @Test
    public void testImportedMessage() {
        ImportTestProtos.BaseMessage base = ImportTestProtos.BaseMessage.newBuilder()
                .setBaseVal("base")
                .build();

        ImportTestProtos.DerivedMessage derived = ImportTestProtos.DerivedMessage.newBuilder()
                .setBase(base)
                .setDerivedVal("derived")
                .build();

        assertThat(derived.getBase().getBaseVal()).isEqualTo("base");
        assertThat(derived.getDerivedVal()).isEqualTo("derived");
    }
}