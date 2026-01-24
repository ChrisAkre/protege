package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.compiler.GrpcCodegen;
import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import dev.akre.protege.testutil.TestProtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.JavaFileObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

public class GrpcCodegenTest {

    static Stream<Arguments> grpcServices() {
        return TestProtos.GRPC_SERVICES.stream();
    }

    @ParameterizedTest(name = "validate gRPC service {6} in {4}")
    @MethodSource("grpcServices")
    @DisplayName("Validate generated gRPC services match expected gRPC services")
    public void testGrpcServicesFromTestProtos(Class<?> generatedMessage, Class<?> generatedService, Class<?> expectedMessage, Class<?> expectedService, Path protoPath, DescriptorProtos.FileDescriptorProto fileDescriptor, String serviceName) throws Exception {
        assertThat(generatedService).isNotNull();
        assertThat(expectedService).isNotNull();

        // Verify SERVICE_NAME
        assertThat(generatedService.getDeclaredField("SERVICE_NAME").get(null))
                .isEqualTo(expectedService.getDeclaredField("SERVICE_NAME").get(null));

        // Verify PROTEGE_VERSION
        assertThat(generatedService.getDeclaredField("PROTEGE_VERSION").get(null))
                .isEqualTo(ProtegeVersion.VERSION_STRING);

        // Verify stubs
        assertThat(generatedService.getDeclaredMethod("newStub", io.grpc.Channel.class)).isNotNull();
        assertThat(generatedService.getDeclaredMethod("newBlockingStub", io.grpc.Channel.class)).isNotNull();
    }

    @Test
    public void testGrpcCodeGeneration() throws Exception {
        String protoContent = """
                syntax = "proto3";
                package test;
                option java_package = "com.test";
                option java_outer_classname = "TestProto";
                message Request {}
                message Response {}
                service MyService {
                  rpc MyMethod(Request) returns (Response);
                }
                """;
        DescriptorProtos.FileDescriptorProto fileDescriptor = ProtoUtils.parseProto(protoContent, "test.proto");

        TestUtils.MockFiler filer = new TestUtils.MockFiler();
        ProtoCodegen protoCodegen = new ProtoCodegen(filer);
        GrpcCodegen grpcCodegen = new GrpcCodegen(filer);

        protoCodegen.generateFile(fileDescriptor);
        grpcCodegen.generateFile(fileDescriptor);

        Map<String, String> sources = filer.getSources();
        assertThat(sources).containsKey("com.test.TestProto");
        assertThat(sources).containsKey("com.test.MyServiceGrpc");

        List<JavaFileObject> javaFiles = new ArrayList<>();
        sources.forEach((name, source) -> javaFiles.add(com.google.testing.compile.JavaFileObjects.forSourceString(name, source)));

        Class<?> grpcClass = TestUtils.compile("com.test.MyServiceGrpc", javaFiles);
        assertThat(grpcClass).isNotNull();

        // Verify SERVICE_NAME
        assertThat(grpcClass.getDeclaredField("SERVICE_NAME").get(null)).isEqualTo("test.MyService");

        // Verify PROTEGE_VERSION
        assertThat(grpcClass.getDeclaredField("PROTEGE_VERSION").get(null))
                .isEqualTo(ProtegeVersion.VERSION_STRING);

        // Verify method descriptor exists
        assertThat(grpcClass.getDeclaredMethod("getMyMethodMethod")).isNotNull();

        // Verify stubs
        assertThat(grpcClass.getDeclaredMethod("newStub", io.grpc.Channel.class)).isNotNull();
        assertThat(grpcClass.getDeclaredMethod("newBlockingStub", io.grpc.Channel.class)).isNotNull();

        // Verify inner classes
        Class<?>[] innerClasses = grpcClass.getDeclaredClasses();
        List<String> innerNames = java.util.Arrays.stream(innerClasses).map(Class::getSimpleName).toList();
        assertThat(innerNames).contains("MyServiceStub", "MyServiceBlockingStub", "MyServiceImplBase");
    }

    @Test
    //@Disabled("Streaming is not yet supported in GrpcCodegen")
    public void testStreamingGrpcCodeGeneration() throws Exception {
        String protoContent = """
                syntax = "proto3";
                package test;
                option java_package = "com.test.streaming";
                option java_outer_classname = "StreamingProto";
                message Request {}
                message Response {}
                service StreamingService {
                  rpc StreamMethod(Request) returns (stream Response);
                }
                """;
        DescriptorProtos.FileDescriptorProto fileDescriptor = ProtoUtils.parseProto(protoContent, "streaming.proto");

        TestUtils.MockFiler filer = new TestUtils.MockFiler();
        ProtoCodegen protoCodegen = new ProtoCodegen(filer);
        GrpcCodegen grpcCodegen = new GrpcCodegen(filer);

        protoCodegen.generateFile(fileDescriptor);
        grpcCodegen.generateFile(fileDescriptor);

        Map<String, String> sources = filer.getSources();
        List<JavaFileObject> javaFiles = new ArrayList<>();
        sources.forEach((name, source) -> javaFiles.add(com.google.testing.compile.JavaFileObjects.forSourceString(name, source)));

        Class<?> grpcClass = TestUtils.compile("com.test.streaming.StreamingServiceGrpc", javaFiles);
        assertThat(grpcClass).isNotNull();

        // Verify method descriptor exists and is SERVER_STREAMING
        var getMethod = grpcClass.getDeclaredMethod("getStreamMethodMethod");
        assertThat(getMethod).isNotNull();
        io.grpc.MethodDescriptor<?, ?> descriptor = (io.grpc.MethodDescriptor<?, ?>) getMethod.invoke(null);
        assertThat(descriptor.getType()).isEqualTo(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING);

        // Verify BlockingStub has correct signature: Iterator<Response> streamMethod(Request)
        Class<?> blockingStubClass = java.util.Arrays.stream(grpcClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("StreamingServiceBlockingStub"))
                .findFirst()
                .orElseThrow();
        
        Class<?> requestClass = Class.forName("com.test.streaming.StreamingProto$Request", true, grpcClass.getClassLoader());
        var streamMethod = blockingStubClass.getDeclaredMethod("streamMethod", requestClass);
        assertThat(streamMethod.getReturnType()).isEqualTo(Iterator.class);

        // Verify async Stub has correct signature: void streamMethod(Request, StreamObserver<Response>)
        Class<?> asyncStubClass = java.util.Arrays.stream(grpcClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("StreamingServiceStub"))
                .findFirst()
                .orElseThrow();
        var asyncStreamMethod = asyncStubClass.getDeclaredMethod("streamMethod", requestClass, io.grpc.stub.StreamObserver.class);
        assertThat(asyncStreamMethod.getReturnType()).isEqualTo(void.class);

        // Verify ImplBase has correct signature
        Class<?> implBaseClass = java.util.Arrays.stream(grpcClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("StreamingServiceImplBase"))
                .findFirst()
                .orElseThrow();
        var implStreamMethod = implBaseClass.getDeclaredMethod("streamMethod", requestClass, io.grpc.stub.StreamObserver.class);
        assertThat(implStreamMethod.getReturnType()).isEqualTo(void.class);
    }
}
