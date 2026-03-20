package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.ProtoUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GrpcCodegen(Filer filer, CodegenMetadata config) implements CodegenConfig {
    public DescriptorProtos.FileDescriptorProto descriptor() {
        return config.fileDescriptor();
    }


    public void generateFile() throws IOException {
        var fileDescriptor = config.fileDescriptor();
        var packageName = ProtoUtils.getJavaPackage(fileDescriptor);
        var outerClassName = ProtoUtils.getJavaOuterClassName(fileDescriptor);

        var typeRegistry = new HashMap<String, ClassName>();
        var isEnumMap = new HashMap<String, Boolean>();
        registerAllTypes(fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap);

        for (var service : fileDescriptor.getServiceList()) {
            generateServiceClass(service, fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap);
        }
    }

    private void generateServiceClass(DescriptorProtos.ServiceDescriptorProto service,
                                      DescriptorProtos.FileDescriptorProto fileDescriptor,
                                      String packageName,
                                      String outerClassName,
                                      Map<String, ClassName> typeRegistry,
                                      Map<String, Boolean> isEnumMap) throws IOException {
        String serviceName = service.getName();
        String className = serviceName + "Grpc";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                // private <ServiceName>Grpc()
                .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

        String fullServiceName = fileDescriptor.getPackage().isEmpty()
                ? serviceName
                : fileDescriptor.getPackage() + "." + serviceName;

        // public static final String SERVICE_NAME
        classBuilder.addField(FieldSpec.builder(String.class, "SERVICE_NAME")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", fullServiceName)
                .build());

        // public static final String PROTEGE_VERSION
        classBuilder.addField(FieldSpec.builder(String.class, "PROTEGE_VERSION")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.VERSION_STRING", ProtegeVersion.class)
                .build());

        classBuilder.addField(FieldSpec.builder(ClassName.get("io.grpc", "ServiceDescriptor"), "serviceDescriptor")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.VOLATILE)
                .build());

        // Method Descriptors
        for (var method : service.getMethodList()) {
            generateMethodDescriptor(classBuilder, method, fullServiceName, fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap);
        }

        generateGetServiceDescriptor(classBuilder, service, packageName, className);

        // Stubs
        generateStubs(classBuilder, service, fileDescriptor, packageName, className, outerClassName, typeRegistry, isEnumMap);

        // ImplBase
        generateImplBase(classBuilder, service, fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap);

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
        javaFile.writeTo(filer);
    }

    private void generateMethodDescriptor(TypeSpec.Builder classBuilder,
                                          DescriptorProtos.MethodDescriptorProto method,
                                          String fullServiceName,
                                          DescriptorProtos.FileDescriptorProto fileDescriptor,
                                          String packageName,
                                          String outerClassName,
                                          Map<String, ClassName> typeRegistry,
                                          Map<String, Boolean> isEnumMap) {
        String methodName = method.getName();
        TypeName inputType = resolveTypeName(method.getInputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
        TypeName outputType = resolveTypeName(method.getOutputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());

        ClassName methodDescriptorClass = ClassName.get("io.grpc", "MethodDescriptor");
        ParameterizedTypeName methodType = ParameterizedTypeName.get(methodDescriptorClass, inputType, outputType);

        String fieldName = "get" + methodName + "Method";
        
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(fieldName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(methodType);

        String methodTypeEnum = "UNARY";
        if (method.getClientStreaming() && method.getServerStreaming()) {
            methodTypeEnum = "BIDI_STREAMING";
        } else if (method.getClientStreaming()) {
            methodTypeEnum = "CLIENT_STREAMING";
        } else if (method.getServerStreaming()) {
            methodTypeEnum = "SERVER_STREAMING";
        }

        methodBuilder.addStatement("return $T.<$T, $T>newBuilder()\n" +
                "    .setType($T.MethodType.$L)\n" +
                "    .setFullMethodName($T.generateFullMethodName(SERVICE_NAME, $S))\n" +
                "    .setRequestMarshaller($T.marshaller($T.getDefaultInstance()))\n" +
                "    .setResponseMarshaller($T.marshaller($T.getDefaultInstance()))\n" +
                "    .build()",
                methodDescriptorClass, inputType, outputType,
                methodDescriptorClass, methodTypeEnum,
                methodDescriptorClass, methodName,
                ClassName.get("io.grpc.protobuf", "ProtoUtils"), inputType,
                ClassName.get("io.grpc.protobuf", "ProtoUtils"), outputType);

        // public static MethodDescriptor<<InputType>, <OutputType>> get<MethodName>Method()
        classBuilder.addMethod(methodBuilder.build());
    }

    private void generateGetServiceDescriptor(TypeSpec.Builder classBuilder,
                                              DescriptorProtos.ServiceDescriptorProto service,
                                              String packageName,
                                              String className) {
        MethodSpec.Builder getServiceDescriptorBuilder = MethodSpec.methodBuilder("getServiceDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get("io.grpc", "ServiceDescriptor"));

        getServiceDescriptorBuilder.addCode(
                "$T result = serviceDescriptor;\n" +
                "if (result == null) {\n" +
                "  synchronized ($T.class) {\n" +
                "    result = serviceDescriptor;\n" +
                "    if (result == null) {\n" +
                "      serviceDescriptor = result = $T.newBuilder(SERVICE_NAME)\n",
                ClassName.get("io.grpc", "ServiceDescriptor"),
                ClassName.get(packageName, className),
                ClassName.get("io.grpc", "ServiceDescriptor")
        );

        for (var method : service.getMethodList()) {
            getServiceDescriptorBuilder.addCode("          .addMethod(get$LMethod())\n", method.getName());
        }

        getServiceDescriptorBuilder.addCode(
                "          .build();\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +
                "return result;\n"
        );
        classBuilder.addMethod(getServiceDescriptorBuilder.build());
    }

    private void generateStubs(TypeSpec.Builder classBuilder,
                               DescriptorProtos.ServiceDescriptorProto service,
                               DescriptorProtos.FileDescriptorProto fileDescriptor,
                               String packageName,
                               String grpcClassName,
                               String outerClassName,
                               Map<String, ClassName> typeRegistry,
                               Map<String, Boolean> isEnumMap) {
        ClassName grpcClass = ClassName.get(packageName, grpcClassName);
        ClassName channelClass = ClassName.get("io.grpc", "Channel");

        // public static <ServiceName>Stub newStub(Channel channel)
        classBuilder.addMethod(MethodSpec.methodBuilder("newStub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(channelClass, "channel")
                .returns(grpcClass.nestedClass(service.getName() + "Stub"))
                .addStatement("return new $T(channel)", grpcClass.nestedClass(service.getName() + "Stub"))
                .build());

        // public static <ServiceName>BlockingStub newBlockingStub(Channel channel)
        classBuilder.addMethod(MethodSpec.methodBuilder("newBlockingStub")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(channelClass, "channel")
                .returns(grpcClass.nestedClass(service.getName() + "BlockingStub"))
                .addStatement("return new $T(channel)", grpcClass.nestedClass(service.getName() + "BlockingStub"))
                .build());

        // Stub classes
        generateStubClass(classBuilder, service, "Stub", "io.grpc.stub.AbstractAsyncStub", packageName, grpcClassName, fileDescriptor, outerClassName, typeRegistry, isEnumMap);
        generateStubClass(classBuilder, service, "BlockingStub", "io.grpc.stub.AbstractBlockingStub", packageName, grpcClassName, fileDescriptor, outerClassName, typeRegistry, isEnumMap);
    }

    private void generateStubClass(TypeSpec.Builder classBuilder, DescriptorProtos.ServiceDescriptorProto service, String suffix, String baseClass, String packageName, String grpcClassName, DescriptorProtos.FileDescriptorProto fileDescriptor, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap) {
        String className = service.getName() + suffix;
        ClassName stubClass = ClassName.get(packageName, grpcClassName, className);
        ClassName baseClassName = ClassName.get(baseClass.substring(0, baseClass.lastIndexOf(".")), baseClass.substring(baseClass.lastIndexOf(".") + 1));

        TypeSpec.Builder stubBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(baseClassName, stubClass));

        // private <StubName>(Channel channel)
        stubBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ClassName.get("io.grpc", "Channel"), "channel")
                .addStatement("super(channel, $T.DEFAULT)", ClassName.get("io.grpc", "CallOptions"))
                .build());

        // private <StubName>(Channel channel, CallOptions callOptions)
        stubBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ClassName.get("io.grpc", "Channel"), "channel")
                .addParameter(ClassName.get("io.grpc", "CallOptions"), "callOptions")
                .addStatement("super(channel, callOptions)")
                .build());

        // protected <StubName> build(Channel channel, CallOptions callOptions)
        stubBuilder.addMethod(MethodSpec.methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .addParameter(ClassName.get("io.grpc", "Channel"), "channel")
                .addParameter(ClassName.get("io.grpc", "CallOptions"), "callOptions")
                .returns(stubClass)
                .addStatement("return new $T(channel, callOptions)", stubClass)
                .build());

        for (var method : service.getMethodList()) {
            String methodName = StringUtils.uncapitalize(ProtoUtils.toCamelCase(method.getName()));
            TypeName inputType = resolveTypeName(method.getInputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
            TypeName outputType = resolveTypeName(method.getOutputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC);

            if (suffix.equals("BlockingStub")) {
                if (method.getServerStreaming() && !method.getClientStreaming()) {
                    methodBuilder.returns(ParameterizedTypeName.get(ClassName.get("java.util", "Iterator"), outputType))
                            .addParameter(inputType, "request")
                            .addStatement("return $T.blockingServerStreamingCall(getChannel(), get$LMethod(), getCallOptions(), request)",
                                    ClassName.get("io.grpc.stub", "ClientCalls"), method.getName());
                } else if (!method.getServerStreaming() && !method.getClientStreaming()) {
                    methodBuilder.returns(outputType)
                            .addParameter(inputType, "request")
                            .addStatement("return $T.blockingUnaryCall(getChannel(), get$LMethod(), getCallOptions(), request)",
                                    ClassName.get("io.grpc.stub", "ClientCalls"), method.getName());
                }
            } else {
                if (method.getServerStreaming() && !method.getClientStreaming()) {
                    methodBuilder.addParameter(inputType, "request")
                            .addParameter(ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType), "responseObserver")
                            .addStatement("$T.asyncServerStreamingCall(getChannel().newCall(get$LMethod(), getCallOptions()), request, responseObserver)",
                                    ClassName.get("io.grpc.stub", "ClientCalls"), method.getName());
                } else if (!method.getServerStreaming() && !method.getClientStreaming()) {
                    methodBuilder.addParameter(inputType, "request")
                            .addParameter(ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType), "responseObserver")
                            .addStatement("$T.asyncUnaryCall(getChannel().newCall(get$LMethod(), getCallOptions()), request, responseObserver)",
                                    ClassName.get("io.grpc.stub", "ClientCalls"), method.getName());
                }
            }
            // public <OutputType> <methodName>(<InputType> request)
            // or
            // public void <methodName>(<InputType> request, StreamObserver<<OutputType>> responseObserver)
            stubBuilder.addMethod(methodBuilder.build());
        }

        // public static final class <StubName> extends AbstractAsyncStub<<StubName>> (or AbstractBlockingStub)
        classBuilder.addType(stubBuilder.build());
    }

    private void generateImplBase(TypeSpec.Builder classBuilder,
                                  DescriptorProtos.ServiceDescriptorProto service,
                                  DescriptorProtos.FileDescriptorProto fileDescriptor,
                                  String packageName,
                                  String outerClassName,
                                  Map<String, ClassName> typeRegistry,
                                  Map<String, Boolean> isEnumMap) {
        String className = service.getName() + "ImplBase";
        TypeSpec.Builder implBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addSuperinterface(ClassName.get("io.grpc", "BindableService"));

        for (var method : service.getMethodList()) {
            String methodName = StringUtils.uncapitalize(ProtoUtils.toCamelCase(method.getName()));
            TypeName inputType = resolveTypeName(method.getInputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
            TypeName outputType = resolveTypeName(method.getOutputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());

            MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC);

            if (method.getClientStreaming() && method.getServerStreaming()) {
                // Bidi streaming not fully implemented yet
            } else if (method.getClientStreaming()) {
                // Client streaming not fully implemented yet
            } else if (method.getServerStreaming()) {
                methodBuilder.addParameter(inputType, "request")
                        .addParameter(ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType), "responseObserver")
                        .addStatement("$T.asyncUnimplementedUnaryCall(get$LMethod(), responseObserver)",
                                ClassName.get("io.grpc.stub", "ServerCalls"), method.getName());
            } else {
                methodBuilder.addParameter(inputType, "request")
                        .addParameter(ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType), "responseObserver")
                        .addStatement("$T.asyncUnimplementedUnaryCall(get$LMethod(), responseObserver)",
                                ClassName.get("io.grpc.stub", "ServerCalls"), method.getName());
            }
            // public void <methodName>(<InputType> request, StreamObserver<<OutputType>> responseObserver)
            implBuilder.addMethod(methodBuilder.build());
        }

        MethodSpec.Builder bindServiceBuilder = MethodSpec.methodBuilder("bindService")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(ClassName.get("io.grpc", "ServerServiceDefinition"));

        bindServiceBuilder.addStatement("$T builder = $T.builder(SERVICE_NAME)",
                ClassName.get("io.grpc", "ServerServiceDefinition", "Builder"),
                ClassName.get("io.grpc", "ServerServiceDefinition"));

        for (var method : service.getMethodList()) {
            String methodName = StringUtils.uncapitalize(ProtoUtils.toCamelCase(method.getName()));
            TypeName inputType = resolveTypeName(method.getInputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
            TypeName outputType = resolveTypeName(method.getOutputType(), fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());

            if (method.getClientStreaming() && method.getServerStreaming()) {
                // Bidi streaming not fully implemented yet
            } else if (method.getClientStreaming()) {
                // Client streaming not fully implemented yet
            } else if (method.getServerStreaming()) {
                bindServiceBuilder.addStatement("builder.addMethod(get$LMethod(), $T.asyncServerStreamingCall((request, responseObserver) -> $L(($T)request, ($T)responseObserver)))",
                        method.getName(),
                        ClassName.get("io.grpc.stub", "ServerCalls"),
                        methodName,
                        inputType,
                        ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType)
                );
            } else {
                bindServiceBuilder.addStatement("builder.addMethod(get$LMethod(), $T.asyncUnaryCall((request, responseObserver) -> $L(($T)request, ($T)responseObserver)))",
                        method.getName(),
                        ClassName.get("io.grpc.stub", "ServerCalls"),
                        methodName,
                        inputType,
                        ParameterizedTypeName.get(ClassName.get("io.grpc.stub", "StreamObserver"), outputType)
                );
            }
        }
        bindServiceBuilder.addStatement("return builder.build()");
        // public final ServerServiceDefinition bindService()
        implBuilder.addMethod(bindServiceBuilder.build());

        // public static abstract class <ServiceName>ImplBase implements BindableService
        classBuilder.addType(implBuilder.build());
    }

    private void registerAllTypes(DescriptorProtos.FileDescriptorProto fileDescriptor, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap) {
        for (var message : fileDescriptor.getMessageTypeList()) {
            registerTypes(message, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
        }
    }

    private void registerTypes(DescriptorProtos.DescriptorProto message, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap, List<String> parentNames) {
        var currentPath = new ArrayList<>(parentNames);
        currentPath.add(message.getName());

        var capitalizedPath = currentPath.stream().map(StringUtils::capitalize).toList();
        var className = ClassName.get(packageName, outerClassName, capitalizedPath.toArray(new String[0]));
        var relativeProtoName = String.join(".", currentPath);
        typeRegistry.put(relativeProtoName, className);
        isEnumMap.put(relativeProtoName, false);

        for (var nested : message.getNestedTypeList()) {
            registerTypes(nested, packageName, outerClassName, typeRegistry, isEnumMap, currentPath);
        }
    }

    private TypeName resolveTypeName(String protoTypeName, DescriptorProtos.FileDescriptorProto fileDescriptor, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap, List<String> currentScope) {
        if (protoTypeName.startsWith(".")) {
            var typeName = protoTypeName;
            var protoPackage = fileDescriptor.getPackage();
            if (!protoPackage.isEmpty() && typeName.startsWith("." + protoPackage + ".")) {
                typeName = typeName.substring(protoPackage.length() + 2);
            } else if (typeName.startsWith(".")) {
                typeName = typeName.substring(1);
            }
            if (typeRegistry.containsKey(typeName)) {
                return typeRegistry.get(typeName);
            }
            return ClassName.get(packageName, outerClassName, typeName.split("\\."));
        }

        for (int i = currentScope.size(); i >= 0; i--) {
            var scope = currentScope.subList(0, i);
            var candidateName = scope.isEmpty() ? protoTypeName : String.join(".", scope) + "." + protoTypeName;
            if (typeRegistry.containsKey(candidateName)) {
                return typeRegistry.get(candidateName);
            }
        }

        return ClassName.get(packageName, outerClassName, protoTypeName.split("\\."));
    }
}
