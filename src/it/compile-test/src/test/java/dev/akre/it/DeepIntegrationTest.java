package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class DeepIntegrationTest {

    @Test
    public void testDeepNesting() {
        DeepProtos.Level1.Level2.Level3.Level4 l4 = DeepProtos.Level1.Level2.Level3.Level4.newBuilder()
                .setL4("four")
                .build();

        DeepProtos.Level1.Level2.Level3 l3 = DeepProtos.Level1.Level2.Level3.newBuilder()
                .setL3("three")
                .setLevel4(l4)
                .build();

        DeepProtos.Level1.Level2 l2 = DeepProtos.Level1.Level2.newBuilder()
                .setL2("two")
                .setLevel3(l3)
                .build();

        DeepProtos.Level1 l1 = DeepProtos.Level1.newBuilder()
                .setL1("one")
                .setLevel2(l2)
                .build();

        assertThat(l1.getLevel2().getLevel3().getLevel4().getL4()).isEqualTo("four");
    }
}
