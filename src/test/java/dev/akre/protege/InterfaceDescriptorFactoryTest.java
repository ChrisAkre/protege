package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.akre.protege.parser.InterfaceDescriptorFactory;
import dev.akre.protege.testutil.DescriptorAssert;
import dev.akre.protege.testutil.TestProtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.beans.BeanProperty;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterfaceDescriptorFactory Tests")
class InterfaceDescriptorFactoryTest {

    private InterfaceDescriptorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new InterfaceDescriptorFactory();
    }

    @Test
    @DisplayName("Should create a valid FileDescriptorProto for a simple interface message")
    public void shouldCreateFileDescriptorForSimpleInterface() {
        @GenProto(pkg="com.example")
        interface MyMessage {
            @Field(0)
            @BeanProperty(bound = false, description = "foo", enumerationValues = {"a", "b", "c"})
            int getId();
        }

        String expectedProtoDescription = """
                name: "MyMessage.proto"
                package: "com.example"
                message_type {
                  name: "MyMessage"
                  field {
                    name: "id"
                    number: 0
                    label: LABEL_OPTIONAL
                    type: TYPE_INT32
                    options {
                      uninterpreted_option {
                        name {
                          name_part: "dev.akre.protege.java_annotation"
                          is_extension: true
                        }
                        string_value: "@java.beans.BeanProperty(bound = false, description = \\"foo\\", enumerationValues = {\\"a\\", \\"b\\", \\"c\\"})"
                      }
                    }
                  }
                  options {
                    uninterpreted_option {
                      name {
                        name_part: "dev.akre.protege.java_implements"
                        is_extension: true
                      }
                      string_value: "dev.akre.protege.InterfaceDescriptorFactoryTest$1MyMessage"
                    }
                  }
                }
                options {
                  java_package: "dev.akre.protege"
                }
                """;

        assertThat(factory.create(MyMessage.class).toString()).isEqualTo(expectedProtoDescription);
    }

    @ParameterizedTest(name = "Compare descriptor from {0} to descriptor in {1}")
    @MethodSource("provideTestData")
    @DisplayName("Generated descriptors should match the ones compiled by protoc")
    void testDescriptorMatches(String className, String protoPath, Class<?> interfaceClass, Descriptors.Descriptor expectedDescriptor) {
        DescriptorProtos.FileDescriptorProto generatedProto = factory.create(interfaceClass);
        String expectedMessageName = expectedDescriptor.getName();
        
        DescriptorProtos.DescriptorProto actualMessageDescriptor = generatedProto.getMessageTypeList().stream()
                .filter(message -> message.getName().equals(expectedMessageName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Message " + expectedMessageName + " not found in generated proto: " +
                        generatedProto.getMessageTypeList().stream().map(DescriptorProtos.DescriptorProto::getName).toList()));




        DescriptorProtos.DescriptorProto expectedMessageDescriptor = expectedDescriptor.toProto();
        try {
            DescriptorAssert.assertThat(actualMessageDescriptor).compareIgnoringCustomOptions(expectedMessageDescriptor);
        } catch (Throwable ignored) {
            assertThat(actualMessageDescriptor).isEqualTo(expectedMessageDescriptor);
        }
    }

    private static Stream<Arguments> provideTestData() {
        return TestProtos.INTERFACE_DESCRIPTORS.stream();
    }
}
