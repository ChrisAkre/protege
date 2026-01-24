package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled
public class SimpleGrpcTest {

    static Stream<Arguments> grpcServices() {
        return TestProtos.GRPC_SERVICES.stream();
    }

    @ParameterizedTest(name = "Compatibility test for {6}")
    @MethodSource("grpcServices")
    public void testGrpcServiceCompatibility(
            Class<?> generatedOuterClass,
            Class<?> generatedGrpcClass,
            Class<?> expectedOuterClass,
            Class<?> expectedGrpcClass,
            Path protoPath,
            DescriptorProtos.FileDescriptorProto parsedProto,
            String serviceName) throws Exception {

        // Get the first unary method from the service
        DescriptorProtos.ServiceDescriptorProto serviceProto = parsedProto.getServiceList().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElseThrow();

        DescriptorProtos.MethodDescriptorProto methodProto = serviceProto.getMethodList().stream()
                .filter(m -> !m.getClientStreaming() && !m.getServerStreaming())
                .findFirst()
                .orElseThrow();

        String rpcName = methodProto.getName();

        // Test 1: Generated Stub -> Expected Service
        runCompatibilityTest(generatedGrpcClass, expectedGrpcClass, rpcName);

        // Test 2: Expected Stub -> Generated Service
        runCompatibilityTest(expectedGrpcClass, generatedGrpcClass, rpcName);
    }

    @SuppressWarnings("unchecked")
    private void runCompatibilityTest(Class<?> stubGrpcClass, Class<?> serviceGrpcClass, String rpcName) throws Exception {
        ServiceDescriptor serviceDescriptor = TestUtils.getGrpcServiceDescriptor(serviceGrpcClass);
        MethodDescriptor<Object, Object> methodDescriptor = (MethodDescriptor<Object, Object>) serviceDescriptor.getMethods().stream()
                .filter(m -> m.getFullMethodName().endsWith("/" + rpcName))
                .findFirst()
                .orElseThrow();

        // Find the method in the ImplBase to get request/response types
        Class<?> implBase = TestUtils.getImplBase(serviceGrpcClass);
        Method serviceMethod = TestUtils.getServiceMethod(implBase, rpcName);

        Class<?> requestClass = serviceMethod.getParameterTypes()[0];
        Class<?> responseClass = (Class<?>) ((java.lang.reflect.ParameterizedType) serviceMethod.getGenericParameterTypes()[1]).getActualTypeArguments()[0];

        Object defaultResponse = TestUtils.getDefaultInstance(responseClass);
        Object request = TestUtils.getDefaultInstance(requestClass);

        ServerServiceDefinition serviceDef = TestUtils.getServiceDefinition(serviceDescriptor, methodDescriptor, defaultResponse);

        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(serviceDef)
                .build()
                .start();

        try {
            ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
            try {
                Object stub = stubGrpcClass.getMethod("newBlockingStub", Channel.class).invoke(null, channel);
                Method stubMethod = stub.getClass().getMethod(serviceMethod.getName(), requestClass);
                Object response = stubMethod.invoke(stub, request);
                assertNotNull(response);
                assertEquals(defaultResponse, response);
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}