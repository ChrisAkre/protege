package dev.akre.it;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraIntegrationTest {

    @Test
    public void testPackedFields() {
        ExtraProtos.PackedMessage message = ExtraProtos.PackedMessage.newBuilder()
                .addValues(1)
                .addValues(2)
                .addNonPacked(3)
                .addNonPacked(4)
                .build();

        assertThat(message.getValuesList()).containsExactly(1, 2);
        assertThat(message.getNonPackedList()).containsExactly(3, 4);
    }

    @Test
    public void testMapKeys() {
        ExtraProtos.MapKeyValues message = ExtraProtos.MapKeyValues.newBuilder()
                .putIntToBool(10, true)
                .putBoolToString(false, "false-value")
                .build();

        assertThat(message.getIntToBoolMap().get(10)).isTrue();
        assertThat(message.getBoolToStringMap().get(false)).isEqualTo("false-value");
    }

    @Test
    public void testNestedMap() {
        ExtraProtos.NestedMap message = ExtraProtos.NestedMap.newBuilder()
                .putInnerMap("key", ExtraProtos.NestedMap.Inner.newBuilder().setValue("val").build())
                .build();

        assertThat(message.getInnerMapOrThrow("key").getValue()).isEqualTo("val");
    }
}
