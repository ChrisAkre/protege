package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.testutil.ServiceAssert;
import dev.akre.protege.testutil.TestProtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

@DisplayName("Service Interoperability Tests")
public class ServiceInteropTest {

    static Stream<Arguments> allServices() {
        return TestProtos.GENERIC_SERVICES.stream();
    }

    @ParameterizedTest(name = "test interop for {2} in {0}")
    @MethodSource("allServices")
    @DisplayName("Validate generated services interoperate with blocking stubs")
    public void testServiceInterop(Path protoPath, DescriptorProtos.FileDescriptorProto parsedProto, String serviceName, Class<?> expectedServiceClass, Class<?> generatedServiceClass) throws Exception {
        ServiceAssert.assertThat(generatedServiceClass)
                .genericServiceInteroperatesWith(generatedServiceClass.getDeclaringClass());
    }
}
