package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;

/**
 * Static methods for generating service-related code specifications
 */
public class ServiceMessages {

    private ServiceMessages() {
        // Utility class
    }

    public static MethodSpec constructor() {
        return MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PROTECTED)
                .build();
    }

    public static TypeSpec generateInterface(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var interfaceBuilder = TypeSpec.interfaceBuilder("Interface")
                .addModifiers(Modifier.PUBLIC);
        for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
            interfaceBuilder.addMethod(rpcMethod(method, ctx));
        }
        return interfaceBuilder.build();
    }

    public static TypeSpec generateBlockingInterface(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var blockingInterfaceBuilder = TypeSpec.interfaceBuilder("BlockingInterface")
                .addModifiers(Modifier.PUBLIC);
        for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
            blockingInterfaceBuilder.addMethod(blockingMethod(method, ctx));
        }
        return blockingInterfaceBuilder.build();
    }

    public static TypeSpec generateServiceStub(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var stubBuilder = TypeSpec.classBuilder("Stub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ClassName.get("", service.getName()))
                .addSuperinterface(ClassName.get("", "Interface"));

        stubBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(com.google.protobuf.RpcChannel.class, "channel")
                .addStatement("this.channel = channel")
                .build());

        stubBuilder.addField(com.google.protobuf.RpcChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);

        stubBuilder.addMethod(MethodSpec.methodBuilder("getChannel")
                .addModifiers(Modifier.PUBLIC)
                .returns(com.google.protobuf.RpcChannel.class)
                .addStatement("return channel")
                .build());

        for (var method : service.getMethodList()) {
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            stubBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(inputType, "request")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                    .addStatement("channel.callMethod(getDescriptor().getMethods().get($L), controller, request, $T.getDefaultInstance(), com.google.protobuf.RpcUtil.generalizeCallback(done, $T.class, $T.getDefaultInstance()))", service.getMethodList().indexOf(method), outputType, outputType, outputType)
                    .build());
        }

        return stubBuilder.build();
    }

    public static TypeSpec generateBlockingServiceStub(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var stubBuilder = TypeSpec.classBuilder("BlockingStub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("", "BlockingInterface"));

        stubBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(com.google.protobuf.BlockingRpcChannel.class, "channel")
                .addStatement("this.channel = channel")
                .build());

        stubBuilder.addField(com.google.protobuf.BlockingRpcChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);

        for (var method : service.getMethodList()) {
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            stubBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(inputType, "request")
                    .returns(outputType)
                    .addException(com.google.protobuf.ServiceException.class)
                    .addStatement("return ($T) channel.callBlockingMethod(getDescriptor().getMethods().get($L), controller, request, $T.getDefaultInstance())", outputType, service.getMethodList().indexOf(method), outputType)
                    .build());
        }

        return stubBuilder.build();
    }

    public static CodeBlock generateNewReflectiveService(DescriptorProtos.ServiceDescriptorProto service, String serviceName, CodegenContext ctx) {
        var builder = TypeSpec.anonymousClassBuilder("")
                .superclass(ClassName.get("", serviceName));

        for (var method : service.getMethodList()) {
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            builder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(inputType, "request")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                    .addStatement("impl.$L(controller, request, done)", methodName)
                    .build());
        }

        return CodeBlock.builder()
                .add("return $L;", builder.build())
                .build();
    }

    public static CodeBlock generateNewReflectiveBlockingService(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var builder = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(com.google.protobuf.BlockingService.class);

        builder.addMethod(MethodSpec.methodBuilder("getDescriptorForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(Descriptors.ServiceDescriptor.class)
                .addStatement("return getDescriptor()")
                .build());

        var callBlockingMethod = MethodSpec.methodBuilder("callBlockingMethod")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .addParameter(com.google.protobuf.RpcController.class, "controller")
                .addParameter(com.google.protobuf.Message.class, "request")
                .returns(com.google.protobuf.Message.class)
                .addException(com.google.protobuf.ServiceException.class);

        callBlockingMethod.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.callBlockingMethod() given method descriptor for wrong service type.")
                .endControlFlow();

        callBlockingMethod.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());

            callBlockingMethod.beginControlFlow("case $L:", i)
                    .addStatement("return impl.$L(controller, ($T)request)", methodName, inputType)
                    .endControlFlow();
        }
        callBlockingMethod.beginControlFlow("default:")
                .addStatement("throw new $T($S)", AssertionError.class, "Can't get here.")
                .endControlFlow();
        callBlockingMethod.endControlFlow();
        builder.addMethod(callBlockingMethod.build());

        var getRequestPrototype = MethodSpec.methodBuilder("getRequestPrototype")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .returns(com.google.protobuf.Message.class);

        getRequestPrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getRequestPrototype() given method descriptor for wrong service type.")
                .endControlFlow();

        getRequestPrototype.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            getRequestPrototype.addStatement("case $L: return $T.getDefaultInstance()", i, inputType);
        }
        getRequestPrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
        getRequestPrototype.endControlFlow();
        builder.addMethod(getRequestPrototype.build());

        var getResponsePrototype = MethodSpec.methodBuilder("getResponsePrototype")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .returns(com.google.protobuf.Message.class);

        getResponsePrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getResponsePrototype() given method descriptor for wrong service type.")
                .endControlFlow();

        getResponsePrototype.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());
            getResponsePrototype.addStatement("case $L: return $T.getDefaultInstance()", i, outputType);
        }
        getResponsePrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
        getResponsePrototype.endControlFlow();
        builder.addMethod(getResponsePrototype.build());

        return CodeBlock.builder()
                .add("return $L;", builder.build())
                .build();
    }

    public static MethodSpec getDescriptor(CodegenContext ctx, DescriptorProtos.ServiceDescriptorProto service) {
        return MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .returns(Descriptors.ServiceDescriptor.class)
                .addStatement("return $T.getDescriptor().getServices().get($L)", ClassName.get(ctx.packageName(), ctx.outerName()), ctx.fileDescriptor().getServiceList().indexOf(service))
                .build();
    }

    public static MethodSpec getDescriptorForType() {
        return MethodSpec.methodBuilder("getDescriptorForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(Descriptors.ServiceDescriptor.class)
                .addStatement("return getDescriptor()")
                .build();
    }

    public static MethodSpec callMethod(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var callMethod = MethodSpec.methodBuilder("callMethod")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .addParameter(com.google.protobuf.RpcController.class, "controller")
                .addParameter(com.google.protobuf.Message.class, "request")
                .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), ClassName.get(com.google.protobuf.Message.class)), "done");

        callMethod.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.callMethod() given method descriptor for wrong service type.")
                .endControlFlow();

        callMethod.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            callMethod.beginControlFlow("case $L:", i)
                    .addStatement("this.$L(controller, ($T)request, com.google.protobuf.RpcUtil.<$T>specializeCallback(done))", methodName, inputType, outputType)
                    .addStatement("return")
                    .endControlFlow();
        }
        callMethod.beginControlFlow("default:")
                .addStatement("throw new $T($S)", AssertionError.class, "Can't get here.")
                .endControlFlow();
        callMethod.endControlFlow();
        return callMethod.build();
    }

    public static MethodSpec getRequestPrototype(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var getRequestPrototype = MethodSpec.methodBuilder("getRequestPrototype")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .returns(com.google.protobuf.Message.class);

        getRequestPrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getRequestPrototype() given method descriptor for wrong service type.")
                .endControlFlow();

        getRequestPrototype.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            getRequestPrototype.addStatement("case $L: return $T.getDefaultInstance()", i, inputType);
        }
        getRequestPrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
        getRequestPrototype.endControlFlow();
        return getRequestPrototype.build();
    }

    public static MethodSpec getResponsePrototype(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var getResponsePrototype = MethodSpec.methodBuilder("getResponsePrototype")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(Descriptors.MethodDescriptor.class, "method")
                .returns(com.google.protobuf.Message.class);

        getResponsePrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getResponsePrototype() given method descriptor for wrong service type.")
                .endControlFlow();

        getResponsePrototype.beginControlFlow("switch(method.getIndex())");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());
            getResponsePrototype.addStatement("case $L: return $T.getDefaultInstance()", i, outputType);
        }
        getResponsePrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
        getResponsePrototype.endControlFlow();
        return getResponsePrototype.build();
    }

    public static MethodSpec newStub() {
        return MethodSpec.methodBuilder("newStub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(com.google.protobuf.RpcChannel.class, "channel")
                .returns(ClassName.get("", "Stub"))
                .addStatement("return new Stub(channel)")
                .build();
    }

    public static MethodSpec newBlockingStub() {
        return MethodSpec.methodBuilder("newBlockingStub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(com.google.protobuf.BlockingRpcChannel.class, "channel")
                .returns(ClassName.get("", "BlockingInterface"))
                .addStatement("return new BlockingStub(channel)")
                .build();
    }

    public static MethodSpec newReflectiveService(DescriptorProtos.ServiceDescriptorProto service, String serviceName, CodegenContext ctx) {
        return MethodSpec.methodBuilder("newReflectiveService")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.get("", "Interface"), "impl")
                .returns(com.google.protobuf.Service.class)
                .addCode(generateNewReflectiveService(service, serviceName, ctx))
                .build();
    }

    public static MethodSpec newReflectiveBlockingService(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        return MethodSpec.methodBuilder("newReflectiveBlockingService")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ClassName.get("", "BlockingInterface"), "impl")
                .returns(com.google.protobuf.BlockingService.class)
                .addCode(generateNewReflectiveBlockingService(service, ctx))
                .build();
    }

    public static MethodSpec rpcMethod(DescriptorProtos.MethodDescriptorProto method, CodegenContext ctx) {
        var methodName = ProtoUtils.toCamelCase(method.getName());
        var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
        var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(com.google.protobuf.RpcController.class, "controller")
                .addParameter(inputType, "request")
                .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                .build();
    }

    public static MethodSpec blockingMethod(DescriptorProtos.MethodDescriptorProto method, CodegenContext ctx) {
        var methodName = ProtoUtils.toCamelCase(method.getName());
        var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
        var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameter(com.google.protobuf.RpcController.class, "controller")
                .addParameter(inputType, "request")
                .returns(outputType)
                .addException(com.google.protobuf.ServiceException.class)
                .build();
    }
}
