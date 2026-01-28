package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;

public record ServiceCodegen(
        DescriptorProtos.ServiceDescriptorProto service,
        CodegenMetadata config
) implements CodegenConfig {

    public Object descriptor() {
        return service;
    }

    public String serviceName() {
        return service.getName();
    }

    public TypeSpec generate() {
        var serviceName = service.getName();

        var classBuilder = TypeSpec.classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addSuperinterface(com.google.protobuf.Service.class);

        // protected <ServiceName>()
        classBuilder.addMethod(ServiceMessages.constructor());
        // public interface Interface
        classBuilder.addType(ServiceMessages.generateInterface(service, this));
        // public interface BlockingInterface
        classBuilder.addType(ServiceMessages.generateBlockingInterface(service, this));

        // public static final Descriptors.ServiceDescriptor getDescriptor()
        classBuilder.addMethod(ServiceMessages.getDescriptor(this, service));
        // public final Descriptors.ServiceDescriptor getDescriptorForType()
        classBuilder.addMethod(ServiceMessages.getDescriptorForType());
        // public final void callMethod(Descriptors.MethodDescriptor method, RpcController controller, Message request, RpcCallback<Message> done)
        classBuilder.addMethod(ServiceMessages.callMethod(service, this));
        // public final Message getRequestPrototype(Descriptors.MethodDescriptor method)
        classBuilder.addMethod(ServiceMessages.getRequestPrototype(service, this));
        // public final Message getResponsePrototype(Descriptors.MethodDescriptor method)
        classBuilder.addMethod(ServiceMessages.getResponsePrototype(service, this));
        // public static Stub newStub(RpcChannel channel)
        classBuilder.addMethod(ServiceMessages.newStub());
        // public static final class Stub extends <ServiceName> implements Interface
        classBuilder.addType(ServiceMessages.generateServiceStub(service, this));
        // public static BlockingInterface newBlockingStub(BlockingRpcChannel channel)
        classBuilder.addMethod(ServiceMessages.newBlockingStub());
        // public static final class BlockingStub implements BlockingInterface
        classBuilder.addType(ServiceMessages.generateBlockingServiceStub(service, this));
        // public static Service newReflectiveService(Interface impl)
        classBuilder.addMethod(ServiceMessages.newReflectiveService(service, serviceName, this));
        // public static BlockingService newReflectiveBlockingService(BlockingInterface impl)
        classBuilder.addMethod(ServiceMessages.newReflectiveBlockingService(service, this));

        // public abstract void <methodName>(RpcController controller, <InputType> request, RpcCallback<<OutputType>> done)
        service.getMethodList().stream().map(method -> ServiceMessages.rpcMethod(method, this)).forEach(classBuilder::addMethod);

        return classBuilder.build();
    }
}
