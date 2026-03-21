package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

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
        classBuilder.addMethod(ServiceMethods.constructor(this));
        // public interface Interface
        classBuilder.addType(ServiceMethods.generateInterface(this, service));
        // public interface BlockingInterface
        classBuilder.addType(ServiceMethods.generateBlockingInterface(this, service));

        // public static final Descriptors.ServiceDescriptor getDescriptor()
        classBuilder.addMethod(ServiceMethods.getDescriptor(this, service));
        // public final Descriptors.ServiceDescriptor getDescriptorForType()
        classBuilder.addMethod(ServiceMethods.getDescriptorForType(this));
        // public final void callMethod(Descriptors.MethodDescriptor method, RpcController controller, Message request, RpcCallback<Message> done)
        classBuilder.addMethod(ServiceMethods.callMethod(this, service));
        // public final Message getRequestPrototype(Descriptors.MethodDescriptor method)
        classBuilder.addMethod(ServiceMethods.getRequestPrototype(this, service));
        // public final Message getResponsePrototype(Descriptors.MethodDescriptor method)
        classBuilder.addMethod(ServiceMethods.getResponsePrototype(this, service));
        // public static Stub newStub(RpcChannel channel)
        classBuilder.addMethod(ServiceMethods.newStub(this));
        // public static final class Stub extends <ServiceName> implements Interface
        classBuilder.addType(ServiceMethods.generateServiceStub(this, service));
        // public static BlockingInterface newBlockingStub(BlockingRpcChannel channel)
        classBuilder.addMethod(ServiceMethods.newBlockingStub(this));
        // public static final class BlockingStub implements BlockingInterface
        classBuilder.addType(ServiceMethods.generateBlockingServiceStub(this, service));
        // public static Service newReflectiveService(Interface impl)
        classBuilder.addMethod(ServiceMethods.newReflectiveService(this, service, serviceName));
        // public static BlockingService newReflectiveBlockingService(BlockingInterface impl)
        classBuilder.addMethod(ServiceMethods.newReflectiveBlockingService(this, service));

        // public abstract void <methodName>(RpcController controller, <InputType> request, RpcCallback<<OutputType>> done)
        service.getMethodList().stream().map(method -> ServiceMethods.rpcMethod(this, method)).forEach(classBuilder::addMethod);

        return classBuilder.build();
    }
}
