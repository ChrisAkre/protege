package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProtoUtils Documentation Formatting Tests")
public class ProtoUtilsDocTest {

    @Test
    @DisplayName("Should correctly format a complex proto file with nested structures")
    void shouldFormatComplexProto() {
        // Build a complex FileDescriptorProto
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setSyntax("proto3")
                .setPackage("dev.akre.test")
                .setOptions(DescriptorProtos.FileOptions.newBuilder()
                        .setJavaPackage("dev.akre.test.java")
                        .build())
                .addEnumType(DescriptorProtos.EnumDescriptorProto.newBuilder()
                        .setName("GlobalEnum")
                        .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("ZERO").setNumber(0).build())
                        .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("ONE").setNumber(1).build())
                        .build())
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("OuterMessage")
                        .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                .setName("field_one")
                                .setNumber(1)
                                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)
                                .build())
                        .addNestedType(DescriptorProtos.DescriptorProto.newBuilder()
                                .setName("InnerMessage")
                                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                                        .setName("inner_field")
                                        .setNumber(1)
                                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                                        .build())
                                .build())
                        .addEnumType(DescriptorProtos.EnumDescriptorProto.newBuilder()
                                .setName("NestedEnum")
                                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("A").setNumber(0).build())
                                .build())
                        .build())
                .build();

        String expectedOutput = """
                syntax = "proto3";

                package dev.akre.test;

                option java_package = "dev.akre.test.java";

                enum GlobalEnum {
                  ZERO = 0;
                  ONE = 1;
                }

                message OuterMessage {
                  enum NestedEnum {
                    A = 0;
                  }

                  message InnerMessage {
                    int32 inner_field = 1;
                  }

                  string field_one = 1;
                }

                """;

        // Normalize line endings to avoid platform-specific issues
        String actualOutput = ProtoUtils.toProtoString(fileDescriptor).replace("\r\n", "\n");

        // Compare with expected output
        assertThat(actualOutput).isEqualTo(expectedOutput);
    }
}
