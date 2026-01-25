package dev.akre.protege;

import com.example.proto.generated.AddressBookServiceGrpc;
import com.example.proto.generated.Addressbook;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;
import dev.akre.protege.testutil.ServiceAssert;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class AddressBookRpcTest {

    @Test
    public void testRpcCallWithStandardGrpc() throws Exception {
        // 1. Implement the service logic using standard gRPC base class
        AddressBookServiceGrpc.AddressBookServiceImplBase serviceImpl = new AddressBookServiceGrpc.AddressBookServiceImplBase() {
            @Override
            public void getAddressBook(Addressbook.GetAddressBookRequest request, StreamObserver<Addressbook.AddressBook> responseObserver) {
                Addressbook.AddressBook response = Addressbook.AddressBook.newBuilder()
                        .addPeople(Addressbook.AddressBook.Person.newBuilder()
                                .setName("John Doe")
                                .setId(123)
                                .setEmail("john.doe@example.com")
                                .build())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };

        String serverName = InProcessServerBuilder.generateName();

        // 2. Start the in-process server
        Server server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(serviceImpl)
                .build()
                .start();

        // 3. Create the in-process channel
        ManagedChannel managedChannel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();

        try {
            // 4. Create a standard blocking stub
            AddressBookServiceGrpc.AddressBookServiceBlockingStub stub = AddressBookServiceGrpc.newBlockingStub(managedChannel);

            // 5. Make the call
            Addressbook.GetAddressBookRequest request = Addressbook.GetAddressBookRequest.newBuilder()
                    .setId("test-123")
                    .build();

            Addressbook.AddressBook response = stub.getAddressBook(request);

            // 6. Verify the response
            assertThat(response).isNotNull();
            assertThat(response.getPeopleCount()).isEqualTo(1);
            assertThat(response.getPeople(0).getName()).isEqualTo("John Doe");

        } finally {
            // 7. Cleanly shutdown
            managedChannel.shutdownNow();
            managedChannel.awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testRpcCallWithGenericService() throws Exception {
        // 1. Implement the service logic using the generic interface
        Addressbook.AddressBookService.Interface serviceImpl = this::returnPerson;

        // 2. Wrap it in a Service object
        com.google.protobuf.Service service = Addressbook.AddressBookService.newReflectiveService(serviceImpl);

        // 3. Create a simple RpcChannel that calls the service directly
        com.google.protobuf.RpcChannel channel = new com.google.protobuf.RpcChannel() {
            @Override
            public void callMethod(com.google.protobuf.Descriptors.MethodDescriptor method,
                                   RpcController controller,
                                   com.google.protobuf.Message request,
                                   com.google.protobuf.Message responsePrototype,
                                   RpcCallback<com.google.protobuf.Message> done) {
                service.callMethod(method, controller, request, done);
            }
        };

        // 4. Create a stub and make the call
        Addressbook.AddressBookService.Stub stub = Addressbook.AddressBookService.newStub(channel);
        Addressbook.GetAddressBookRequest request = Addressbook.GetAddressBookRequest.newBuilder()
                .setId("test-456")
                .build();

        AtomicReference<Addressbook.AddressBook> responseRef = new AtomicReference<>();
        stub.getAddressBook(null, request, responseRef::set);

        // 5. Verify the response
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getPeopleCount()).isEqualTo(1);
        assertThat(responseRef.get().getPeople(0).getName()).isEqualTo("Jane Doe");
    }

    private void returnPerson(RpcController rpcController, Addressbook.GetAddressBookRequest getAddressBookRequest, RpcCallback<Addressbook.AddressBook> addressBookRpcCallback) {
        Addressbook.AddressBook response = Addressbook.AddressBook.newBuilder()
                .addPeople(Addressbook.AddressBook.Person.newBuilder()
                        .setName("Jane Doe")
                        .setId(456)
                        .setEmail("jane.doe@example.com")
                        .build())
                .build();
        addressBookRpcCallback.run(response);
    }


    @Test
    public void testRpcCallWithGenericBlockingService() throws Exception {
        ServiceAssert.assertThat(Addressbook.AddressBookService.class)
                .genericServiceInteroperatesWith(Addressbook.class);
    }
}