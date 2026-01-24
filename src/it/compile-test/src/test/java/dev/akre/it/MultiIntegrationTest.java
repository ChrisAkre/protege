package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class MultiIntegrationTest {

    @Test
    public void testMultiMessages() {
        MultiProtos.MessageA a = MultiProtos.MessageA.newBuilder()
                .setA("A")
                .setInner(MultiProtos.MessageA.InnerA.newBuilder().setVal(10).build())
                .build();

        MultiProtos.MessageB b = MultiProtos.MessageB.newBuilder()
                .setB("B")
                .setA(a)
                .setColor(MultiProtos.MessageB.Color.BLUE)
                .build();

        MultiProtos.MessageC c = MultiProtos.MessageC.newBuilder()
                .setGlobal(MultiProtos.GlobalEnum.G_START)
                .setLocal(MultiProtos.MessageB.Color.RED)
                .setNested(a.getInner())
                .build();

        assertThat(c.getGlobal()).isEqualTo(MultiProtos.GlobalEnum.G_START);
        assertThat(c.getLocal()).isEqualTo(MultiProtos.MessageB.Color.RED);
        assertThat(c.getNested().getVal()).isEqualTo(10);
    }
}
