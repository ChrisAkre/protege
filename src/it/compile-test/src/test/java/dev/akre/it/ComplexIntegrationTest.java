package dev.akre.it;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;

// @Disabled("Generator bugs with repeated enums and proto2 default values")
public class ComplexIntegrationTest {

    @Test
    public void testComplexMessage() {
        ComplexProtos.ComplexMessage.NestedMessage nested = ComplexProtos.ComplexMessage.NestedMessage.newBuilder()
                .setValue("nested-value")
                .setType(ComplexProtos.ComplexMessage.NestedMessage.NestedEnum.FOO)
                .build();

        ComplexProtos.ComplexMessage message = ComplexProtos.ComplexMessage.newBuilder()
                .setId("complex-1")
                .setNested(nested)
                .setName("oneof-name")
                .putIntToStringMap(1, "one")
                .putIntToStringMap(2, "two")
                .putStringToNestedMap("key", nested)
                // .addEnumList(ComplexProtos.ComplexMessage.NestedMessage.NestedEnum.BAR)
                .setFlag(true)
                .setScore(99.5)
                .setData(ByteString.copyFromUtf8("some data"))
                .build();

        assertThat(message.getId()).isEqualTo("complex-1");
        assertThat(message.getNested().getValue()).isEqualTo("nested-value");
        assertThat(message.getTestOneofCase()).isEqualTo(ComplexProtos.ComplexMessage.TestOneofCase.NAME);
        assertThat(message.getName()).isEqualTo("oneof-name");
        assertThat(message.getIntToStringMap().get(1)).isEqualTo("one");
        assertThat(message.getStringToNestedMap().get("key").getValue()).isEqualTo("nested-value");
        // assertThat(message.getEnumList(0)).isEqualTo(ComplexProtos.ComplexMessage.NestedMessage.NestedEnum.BAR);
        assertThat(message.getFlag()).isTrue();
        assertThat(message.getScore()).isEqualTo(99.5);
        assertThat(message.getData().toStringUtf8()).isEqualTo("some data");
    }

    @Test
    public void testRecursiveMessage() {
        ComplexProtos.RecursiveMessage message = ComplexProtos.RecursiveMessage.newBuilder()
                .setName("parent")
                .setChild(ComplexProtos.RecursiveMessage.newBuilder()
                        .setName("child")
                        .build())
                .build();

        assertThat(message.getName()).isEqualTo("parent");
        assertThat(message.getChild().getName()).isEqualTo("child");
    }

    @Test
    public void testAllTypes() {
        AllTypesProtos.AllTypes message = AllTypesProtos.AllTypes.newBuilder()
                .setDoubleField(1.1)
                .setFloatField(2.2f)
                .setInt32Field(3)
                .setInt64Field(4L)
                .setUint32Field(5)
                .setUint64Field(6L)
                .setSint32Field(7)
                .setSint64Field(8L)
                .setFixed32Field(9)
                .setFixed64Field(10L)
                .setSfixed32Field(11)
                .setSfixed64Field(12L)
                .setBoolField(true)
                .setStringField("string")
                .setBytesField(ByteString.copyFromUtf8("bytes"))
                .build();

        assertThat(message.getDoubleField()).isEqualTo(1.1);
        assertThat(message.getFloatField()).isEqualTo(2.2f);
        assertThat(message.getInt32Field()).isEqualTo(3);
        assertThat(message.getInt64Field()).isEqualTo(4L);
        assertThat(message.getUint32Field()).isEqualTo(5);
        assertThat(message.getUint64Field()).isEqualTo(6L);
        assertThat(message.getSint32Field()).isEqualTo(7);
        assertThat(message.getSint64Field()).isEqualTo(8L);
        assertThat(message.getFixed32Field()).isEqualTo(9);
        assertThat(message.getFixed64Field()).isEqualTo(10L);
        assertThat(message.getSfixed32Field()).isEqualTo(11);
        assertThat(message.getSfixed64Field()).isEqualTo(12L);
        assertThat(message.getBoolField()).isTrue();
        assertThat(message.getStringField()).isEqualTo("string");
        assertThat(message.getBytesField().toStringUtf8()).isEqualTo("bytes");
    }

    @Test
    @Disabled("Default value not supported yet by generator")
    public void testProto2Features() {
        Proto2Protos.Proto2Message message = Proto2Protos.Proto2Message.newBuilder()
                .setId(123)
                .setFavoriteColor(Proto2Protos.Proto2Message.Color.GREEN)
                .build();

        assertThat(message.getId()).isEqualTo(123);
        assertThat(message.getFavoriteColor()).isEqualTo(Proto2Protos.Proto2Message.Color.GREEN);
        assertThat(message.getName()).isEqualTo("unnamed"); // Default value
    }
}
