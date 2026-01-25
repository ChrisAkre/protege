package dev.akre.it;

import dev.akre.it.generated.StreamingServiceGrpc;
import dev.akre.it.generated.Streaming.HelloRequest;
import dev.akre.it.generated.Streaming.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class GrpcStreamingIntegrationTest {

    private Server server;
    private ManagedChannel channel;
    private StreamingServiceGrpc.StreamingServiceBlockingStub blockingStub;

    private static class StreamingServiceImpl extends StreamingServiceGrpc.StreamingServiceImplBase {
        @Override
        public void streamHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            for (int i = 0; i < 3; i++) {
                HelloResponse response = HelloResponse.newBuilder()
                        .setContent("Hello " + i + ", " + request.getName())
                        .build();
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new StreamingServiceImpl())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        blockingStub = StreamingServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testStreamHello() {
        HelloRequest request = HelloRequest.newBuilder()
                .setName("World")
                .build();
        
        Iterator<HelloResponse> responses = blockingStub.streamHello(request);
        List<String> contents = new ArrayList<>();
        while (responses.hasNext()) {
            contents.add(responses.next().getContent());
        }
        assertThat(contents).containsExactly(
                "Hello 0, World",
                "Hello 1, World",
                "Hello 2, World"
        );
    }
}
