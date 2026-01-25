package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;

/**
 * Generates the Service class and its inner interfaces and stubs.
 *
 * @param service The service descriptor.
 * @param ctx     The codegen context.
 */
public record ServiceCodegen(
        DescriptorProtos.ServiceDescriptorProto service,
        CodegenContext ctx
) {

    public String serviceName() {
        return service.getName();
    }

    /**
     * Generates the service class.
     *
     * @return The TypeSpec for the service class.
     */
    public TypeSpec generate() {
        var serviceName = service.getName();

        var classBuilder = TypeSpec.classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addSuperinterface(com.google.protobuf.Service.class);

        classBuilder.addMethod(ServiceMessages.constructor());
        classBuilder.addType(ServiceMessages.generateInterface(service, ctx));
        classBuilder.addType(ServiceMessages.generateBlockingInterface(service, ctx));

        classBuilder.addMethod(ServiceMessages.getDescriptor(ctx, service));
        classBuilder.addMethod(ServiceMessages.getDescriptorForType());
        classBuilder.addMethod(ServiceMessages.callMethod(service, ctx));
        classBuilder.addMethod(ServiceMessages.getRequestPrototype(service, ctx));
        classBuilder.addMethod(ServiceMessages.getResponsePrototype(service, ctx));
        classBuilder.addMethod(ServiceMessages.newStub());
        classBuilder.addType(ServiceMessages.generateServiceStub(service, ctx));
        classBuilder.addMethod(ServiceMessages.newBlockingStub());
        classBuilder.addType(ServiceMessages.generateBlockingServiceStub(service, ctx));
        classBuilder.addMethod(ServiceMessages.newReflectiveService(service, serviceName, ctx));
        classBuilder.addMethod(ServiceMessages.newReflectiveBlockingService(service, ctx));

        service.getMethodList().stream().map(method -> ServiceMessages.rpcMethod(method, ctx)).forEach(classBuilder::addMethod);

        return classBuilder.build();
    }
}
