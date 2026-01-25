package dev.akre.it;

import dev.akre.it.generated.HelloServiceGrpc;
import dev.akre.it.generated.Service.HelloRequest;
import dev.akre.it.generated.Service.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class GrpcServiceIntegrationTest {

    private Server server;
    private ManagedChannel channel;
    private HelloServiceGrpc.HelloServiceBlockingStub blockingStub;

    private static class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            HelloResponse response = HelloResponse.newBuilder()
                    .setContent("Hello, " + request.getName())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new HelloServiceImpl())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        blockingStub = HelloServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testSayHello() {
        HelloRequest request = HelloRequest.newBuilder()
                .setName("World")
                .build();
        HelloResponse response = blockingStub.sayHello(request);
        assertThat(response.getContent()).isEqualTo("Hello, World");
    }
}