package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.DescriptorAssert;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

@DisplayName("Proto Code Generation Tests")
public class ProtoCodegenTest {

    static Stream<Arguments> descriptors() {
        return TestProtos.DESCRIPTORS.stream();
    }

    static Stream<Arguments> topLevelMessages() {
        return TestProtos.TOP_LEVEL_MESSAGES.stream();
    }

    static Stream<Arguments> allMessages() {
        return TestProtos.ALL_MESSAGES.stream();
    }

    static Stream<Arguments> allServices() {
        return TestProtos.GENERIC_SERVICES.stream();
    }

    @ParameterizedTest(name = "validate descriptor parsed from {0}")
    @MethodSource("descriptors")
    @DisplayName("Validate parsed descriptors match protoc descriptors")
    public void validateParsedDescriptor(Path protoPath, DescriptorProtos.FileDescriptorProto parsedProto, Class<?> expectedClass, Class<?> ignoredGeneratedClass) throws Exception {
        DescriptorProtos.FileDescriptorProto expected = TestUtils.getFileDescriptor(expectedClass).toProto();
        DescriptorAssert.assertThat(parsedProto).compareIgnoringCustomOptions(expected);
    }

    @ParameterizedTest(name = "validate descriptor generated from {0}")
    @MethodSource("descriptors")
    @DisplayName("Validate generated descriptors match protoc descriptors")
    public void validateGeneratedDescriptor(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, Class<?> expectedClass, Class<?> generatedClass) throws Exception {
        DescriptorProtos.FileDescriptorProto expected = TestUtils.getFileDescriptor(expectedClass).toProto();
        DescriptorProtos.FileDescriptorProto generatedProto = TestUtils.getFileDescriptor(generatedClass).toProto();
        DescriptorAssert.assertThat(generatedProto).compareIgnoringCustomOptions(expected);
    }

    @ParameterizedTest(name = "test mutual serialization for {2} in {0}")
    @MethodSource("allMessages")
    @DisplayName("Validate parsed proto is byte-level compatible with generated proto")
    public void testMessageSerialization(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, String messageName, Class<?> expectedMessageClass, Class<?> generatedMessageClass) {
        ClassAssert.assertThat(generatedMessageClass)
                .isMessageClass()
                .hasDefaultInstanceEqualTo(expectedMessageClass)
                .hasRandomInstanceEqualTo(expectedMessageClass);
    }

    @ParameterizedTest(name = "validate OrBuilder interface for {2} in {0}")
    @MethodSource("allMessages")
    @DisplayName("Validate generated OrBuilder interfaces are identical to protoc generated interfaces")
    public void validateMessageOrBuilder(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, String messageName, Class<?> expectedMessageClass, Class<?> generatedMessageClass) {
        ClassAssert.assertThat(TestUtils.getOrBuilderInterface(generatedMessageClass))
                .matches(Class::isInterface)
                .hasMethodsEqualTo(TestUtils.getOrBuilderInterface(expectedMessageClass));
    }

    @ParameterizedTest(name = "validate message methods for {2} in {0}")
    @MethodSource("allMessages")
    @DisplayName("Validate generated messages are identical to protoc generated messages")
    public void validateMessage(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, String messageName, Class<?> expectedMessageClass, Class<?> generatedMessageClass) {
        ClassAssert.assertThat(generatedMessageClass)
                .hasMethodsEqualTo(expectedMessageClass);
    }

    @ParameterizedTest(name = "validate builder methods for {2} in {0}")
    @MethodSource("allMessages")
    @DisplayName("Validate generated builders are identical to protoc generated builders")
    public void validateBuilder(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, String messageName, Class<?> expectedMessageClass, Class<?> generatedMessageClass) {
        ClassAssert.assertThat(TestUtils.getBuilder(generatedMessageClass).getClass())
                .hasMethodsEqualTo(TestUtils.getBuilder(expectedMessageClass).getClass());
    }

    @ParameterizedTest(name = "validate service methods for {2} in {0}")
    @MethodSource("allServices")
    @DisplayName("Validate generated services are identical to protoc generated services")
    public void validateService(Path protoPath, DescriptorProtos.FileDescriptorProto ignoredParsedProto, String serviceName, Class<?> expectedServiceClass, Class<?> generatedServiceClass) {
        ClassAssert.assertThat(generatedServiceClass)
                .hasMethodsEqualTo(expectedServiceClass);
    }
}

    