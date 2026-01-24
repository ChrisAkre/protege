package dev.akre.protege.testutil;

import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.BlockingService;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.ServiceException;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceAssert extends ClassAssert {

    protected ServiceAssert(Class<?> actual) {
        super(actual);
    }

    public static ServiceAssert assertThat(Class<?> actual) {
        return new ServiceAssert(actual);
    }

    public ServiceAssert genericServiceInteroperatesWith(Class<?> outerClass) {
        isNotNull();
        Class<?> serviceClass = actual;

        try {
            Class<?> blockingInterface = TestUtils.getBlockingInterface(serviceClass);
            Descriptors.ServiceDescriptor serviceDescriptor = TestUtils.getServiceDescriptor(serviceClass);

            Map<String, Message> methodResponses = new HashMap<>();
            Object serviceImpl = createServiceImplProxy(blockingInterface, serviceDescriptor, methodResponses);

            BlockingService service = TestUtils.newReflectiveBlockingService(serviceClass, blockingInterface, serviceImpl);
            BlockingRpcChannel channel = createDirectChannel(service);
            Object stub = TestUtils.newBlockingStub(serviceClass, channel);

            serviceDescriptor.getMethods().forEach(methodDescriptor -> {
                try {
                    testMethod(stub, blockingInterface, methodDescriptor, methodResponses);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            failWithMessage("Service interoperability test failed for <%s>: %s", serviceClass.getName(), e.getMessage());
        }

        return this;
    }

    private static Object createServiceImplProxy(
            Class<?> blockingInterface,
            Descriptors.ServiceDescriptor serviceDescriptor,
            Map<String, Message> methodResponses) {
        return Proxy.newProxyInstance(
                blockingInterface.getClassLoader(),
                new Class<?>[]{blockingInterface},
                (proxy, method, args) -> {
                    Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(method.getName());
                    if (methodDescriptor == null) {
                        String capitalized = method.getName().substring(0, 1).toUpperCase() + method.getName().substring(1);
                        methodDescriptor = serviceDescriptor.findMethodByName(capitalized);
                    }

                    if (methodDescriptor == null) {
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("toString")) return "ProxyBlockingInterface";
                        throw new UnsupportedOperationException("Method not supported: " + method.getName());
                    }
                    return methodResponses.get(methodDescriptor.getName());
                }
        );
    }

    private static BlockingRpcChannel createDirectChannel(BlockingService service) {
        return (methodDescriptor, controller, request, responsePrototype) -> {
            try {
                return service.callBlockingMethod(methodDescriptor, controller, request);
            } catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void testMethod(
            Object stub,
            Class<?> blockingInterface,
            Descriptors.MethodDescriptor methodDescriptor,
            Map<String, Message> methodResponses) throws Exception {
        String methodName = methodDescriptor.getName();
        String camelCaseName = TestUtils.toCamelCase(methodName);

        Method stubMethod = Arrays.stream(blockingInterface.getMethods())
                .filter(m -> m.getName().equals(camelCaseName))
                .findFirst()
                .orElse(null);

        if (stubMethod == null) {
            failWithMessage("Method <%s> (expected as <%s>) not found in <%s>", methodName, camelCaseName, blockingInterface.getName());
            return;
        }

        Class<?> requestClass = stubMethod.getParameterTypes()[1];
        Class<?> responseClass = stubMethod.getReturnType();

        Message randomRequest = (Message) TestUtils.getRandomInstance(requestClass);
        Message randomResponse = (Message) TestUtils.getRandomInstance(responseClass);

        methodResponses.put(methodName, randomResponse);

        Object actualResponse = stubMethod.invoke(stub, null, randomRequest);
        if (!randomResponse.equals(actualResponse)) {
            failWithMessage("Expected response <%s> but was <%s> for method <%s>", randomResponse, actualResponse, methodName);
        }
    }
}