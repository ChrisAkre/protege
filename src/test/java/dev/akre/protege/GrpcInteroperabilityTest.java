package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GrpcInteroperabilityTest {

    static Stream<Arguments> grpcServices() {
        return TestProtos.GRPC_SERVICES.stream();
    }

    @ParameterizedTest(name = "GRPC Compatibility test for {6} in {4}")
    @MethodSource("grpcServices")
    public void testGrpcServiceCompatibility(
            Class<?> generatedOuterClass,
            Class<?> generatedGrpcClass,
            Class<?> expectedOuterClass,
            Class<?> expectedGrpcClass,
            Path protoPath,
            DescriptorProtos.FileDescriptorProto parsedProto,
            String serviceName) throws Exception {

        String rpcName = TestUtils.getFirstUnaryRpcName(parsedProto, serviceName);

        // Test 1: Generated Stub -> Expected Service
        runCompatibilityTest(generatedGrpcClass, expectedGrpcClass, rpcName);

        // Test 2: Expected Stub -> Generated Service
        runCompatibilityTest(expectedGrpcClass, generatedGrpcClass, rpcName);
    }

    void runCompatibilityTest(Class<?> stubGrpcClass, Class<?> serviceGrpcClass, String rpcName) throws Exception {
        ServiceDescriptor serviceDescriptor = TestUtils.getGrpcServiceDescriptor(serviceGrpcClass);
        MethodDescriptor<Object, Object> methodDescriptor = TestUtils.getMethodDescriptor(serviceDescriptor, rpcName);

        // Find the method in the ImplBase to get request/response types
        Class<?> implBase = TestUtils.getImplBase(serviceGrpcClass);
        Method serviceMethod = TestUtils.getServiceMethod(implBase, rpcName);

        Class<?> requestClass = TestUtils.getRequestClass(serviceMethod);
        Class<?> responseClass = TestUtils.getResponseClass(serviceMethod);

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
                Object stub = TestUtils.newBlockingStub(stubGrpcClass, channel);
                Method stubMethod = TestUtils.getServiceMethod(stub.getClass(), rpcName);

                // Bridge the classloader gap for the request object
                Class<?> stubRequestClass = stubMethod.getParameterTypes()[0];
                Object stubRequest = TestUtils.parseFrom(stubRequestClass, TestUtils.toByteArray(request));

                Object response = stubMethod.invoke(stub, stubRequest);
                assertNotNull(response);
                
                // Use byte array comparison for compatibility check
                byte[] expectedBytes = TestUtils.toByteArray(defaultResponse);
                byte[] actualBytes = TestUtils.toByteArray(response);
                org.assertj.core.api.Assertions.assertThat(actualBytes).isEqualTo(expectedBytes);
            } finally {
                channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}