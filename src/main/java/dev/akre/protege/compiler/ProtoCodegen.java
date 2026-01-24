package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.ProtoUtils;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("deprecation")
public class ProtoCodegen {

    private final Filer filer;
    private final boolean generateDeprecated;
    private final ClassName messageParentClass;
    static final Map<DescriptorProtos.FieldDescriptorProto.Type, TypeName> PROTO_TYPE_TO_TYPE_NAME;
    private static final ClassName DEFAULT_MESSAGE_PARENT = ClassName.get(GeneratedMessage.class);

    private static final String JAVA_ENHANCED_ONEOF_OPTION = "dev.akre.protege.java_enhanced_oneof";
    private static final String JAVA_GENERATE_ONEOF_CASE_OPTION = "dev.akre.protege.java_oneof_case";

    private final Map<String, List<ClassName>> oneofInterfacesByType = new HashMap<>();

    static {
        PROTO_TYPE_TO_TYPE_NAME = Map.ofEntries(
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, ClassName.get(String.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, TypeName.FLOAT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, TypeName.DOUBLE),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, TypeName.BOOLEAN),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, ClassName.get(com.google.protobuf.ByteString.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64, TypeName.LONG)
        );
    }


    public ProtoCodegen(Filer filer) {
        this(filer, true);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated) {
        this(filer, generateDeprecated, DEFAULT_MESSAGE_PARENT);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated, ClassName messageParentClass) {
        this.filer = filer;
        this.generateDeprecated = generateDeprecated;
        this.messageParentClass = messageParentClass;
    }

    private ClassName getFieldAccessorTableClass() {
        return messageParentClass.nestedClass("FieldAccessorTable");
    }

    public JavaFileObject generateFile(DescriptorProtos.FileDescriptorProto fileDescriptor) throws IOException {
        var ctx = CodegenContext.create(fileDescriptor, messageParentClass, generateDeprecated);
        populateOneofInterfaces(ctx);

        var outerClassBuilder = TypeSpec.classBuilder(ctx.outerClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        outerClassBuilder.addField(FieldSpec.builder(String.class, "PROTEGE_VERSION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", ProtegeVersion.VERSION_STRING)
                .build());

        generateFileDescriptor(outerClassBuilder, ctx.fileDescriptor());

        for (var enumType : ctx.fileDescriptor().getEnumTypeList()) {
            generateEnumTypes(outerClassBuilder, enumType, ClassName.get(ctx.packageName(), ctx.outerClassName()), new String[]{ctx.outerClassName()});
        }

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            generateMessageTypes(outerClassBuilder, ctx, message, ctx.outerClassName());
        }

        if (ProtoUtils.isJavaGenericServicesEnabled(ctx.fileDescriptor())) {
            for (var service : ctx.fileDescriptor().getServiceList()) {
                generateServiceTypes(outerClassBuilder, service, ctx);
            }
        }

        var javaFile = JavaFile.builder(ctx.packageName(), outerClassBuilder.build()).build();
        var lastFile = javaFile.toJavaFileObject();
        javaFile.writeTo(filer);
        return lastFile;
    }

    private void generateServiceTypes(TypeSpec.Builder parentBuilder, DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var serviceName = service.getName();
        var classBuilder = TypeSpec.classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addSuperinterface(com.google.protobuf.Service.class);

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PROTECTED)
                .build());

        var interfaceBuilder = TypeSpec.interfaceBuilder("Interface")
                .addModifiers(Modifier.PUBLIC);

        var blockingInterfaceBuilder = TypeSpec.interfaceBuilder("BlockingInterface")
                .addModifiers(Modifier.PUBLIC);

        for (var method : service.getMethodList()) {
            classBuilder.addMethod(CodegenMethods.Service.rpcMethod(method, ctx));
            interfaceBuilder.addMethod(CodegenMethods.Service.rpcMethod(method, ctx));
            blockingInterfaceBuilder.addMethod(CodegenMethods.Service.blockingMethod(method, ctx));
        }

        classBuilder.addMethod(CodegenMethods.Service.getDescriptor(ctx, service));
        classBuilder.addMethod(CodegenMethods.Service.getDescriptorForType());
        classBuilder.addMethod(CodegenMethods.Service.callMethod(service, ctx));
        classBuilder.addMethod(CodegenMethods.Service.getRequestPrototype(service, ctx));
        classBuilder.addMethod(CodegenMethods.Service.getResponsePrototype(service, ctx));
        classBuilder.addMethod(CodegenMethods.Service.newStub());
        classBuilder.addType(CodegenMethods.Service.generateServiceStub(service, ctx));
        classBuilder.addMethod(CodegenMethods.Service.newBlockingStub());
        classBuilder.addType(CodegenMethods.Service.generateBlockingServiceStub(service, ctx));
        classBuilder.addMethod(CodegenMethods.Service.newReflectiveService(service, serviceName, ctx));
        classBuilder.addMethod(CodegenMethods.Service.newReflectiveBlockingService(service, ctx));

        classBuilder.addType(interfaceBuilder.build());
        classBuilder.addType(blockingInterfaceBuilder.build());

        parentBuilder.addType(classBuilder.build());
    }

    private void generateOneofs(TypeSpec.Builder classBuilder, MessageContext msgCtx, CodegenContext ctx) {
        for (int i = 0; i < msgCtx.message().getOneofDeclCount(); i++) {
            var oneof = msgCtx.message().getOneofDecl(i);
            var oneofCtx = OneofContext.create(oneof, i, msgCtx.messageClassName());

            if (msgCtx.enhancedOneof()) {
                for (var field : msgCtx.message().getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + msgCtx.message().getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = typeName;
                        if (relativeName.startsWith(".")) {
                            var protoPackage = ctx.fileDescriptor().getPackage();
                            if (!protoPackage.isEmpty() && relativeName.startsWith("." + protoPackage + ".")) {
                                relativeName = relativeName.substring(protoPackage.length() + 2);
                            } else if (relativeName.startsWith(".")) {
                                relativeName = relativeName.substring(1);
                            }
                        }
                        if (!ctx.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + msgCtx.message().getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }
                    }
                }

                var interfaceBuilder = TypeSpec.interfaceBuilder(oneofCtx.pascalName())
                        .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

                for (var field : msgCtx.message().getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        TypeName typeName = ctx.resolveTypeName(field.getTypeName(), msgCtx.currentScope());
                        if (typeName instanceof ClassName cn) {
                            TypeName orBuilderType = getOrBuilderType(cn);
                            if (orBuilderType instanceof ClassName orBuilderCn) {
                                interfaceBuilder.addPermittedSubclass(orBuilderCn);
                            }
                        }
                    }
                }
                classBuilder.addType(interfaceBuilder.build());
            }

            if (msgCtx.generateOneofCase()) {
                var enumBuilder = TypeSpec.enumBuilder(oneofCtx.enumName())
                        .addModifiers(Modifier.PUBLIC)
                        .addSuperinterface(com.google.protobuf.Internal.EnumLite.class);

                for (var field : msgCtx.message().getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        enumBuilder.addEnumConstant(field.getName().toUpperCase(),
                                TypeSpec.anonymousClassBuilder("$L", field.getNumber()).build());
                    }
                }
                enumBuilder.addEnumConstant(oneofCtx.oneofName().toUpperCase() + "_NOT_SET",
                        TypeSpec.anonymousClassBuilder("0").build());

                enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
                enumBuilder.addMethod(MethodSpec.constructorBuilder()
                        .addParameter(int.class, "value")
                        .addStatement("this.value = value")
                        .build());

                enumBuilder.addMethod(MethodSpec.methodBuilder("getNumber")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int.class)
                        .addStatement("return value")
                        .build());

                var forNumberBuilder = MethodSpec.methodBuilder("forNumber")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameter(int.class, "value")
                        .returns(msgCtx.messageClassName().nestedClass(oneofCtx.enumName()));

                forNumberBuilder.beginControlFlow("switch (value)");
                for (var field : msgCtx.message().getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        forNumberBuilder.addStatement("case $L: return $L", field.getNumber(), field.getName().toUpperCase());
                    }
                }
                forNumberBuilder.addStatement("case 0: return $L", oneofCtx.oneofName().toUpperCase() + "_NOT_SET");
                forNumberBuilder.addStatement("default: return null");
                forNumberBuilder.endControlFlow();
                enumBuilder.addMethod(forNumberBuilder.build());

                classBuilder.addType(enumBuilder.build());

                classBuilder.addMethod(CodegenMethods.Message.getOneofCase(oneofCtx, generateGetCaseCode(msgCtx.message(), i, oneofCtx.oneofName(), oneofCtx.enumName())));
            }

            if (msgCtx.enhancedOneof()) {
                var switchCode = CodeBlock.builder();
                switchCode.beginControlFlow("switch ($LCase_)", oneofCtx.oneofName());
                for (var field : msgCtx.message().getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        switchCode.addStatement("case $L: return get$L()", field.getNumber(), ProtoUtils.toPascalCase(field.getName()));
                    }
                }
                switchCode.addStatement("default: return null");
                switchCode.endControlFlow();

                classBuilder.addMethod(CodegenMethods.Message.getOneof(oneofCtx, msgCtx, switchCode.build()));
            }
        }
    }

    private CodeBlock generateGetCaseCode(DescriptorProtos.DescriptorProto message, int oneofIndex, String oneofName, String enumName) {
        return CodeBlock.of("return $L.forNumber($LCase_);\n", enumName, oneofName);
    }

    private CodeBlock generateClearOneofCode(DescriptorProtos.DescriptorProto message, int oneofIndex) {
        var cb = CodeBlock.builder();
        cb.addStatement("$LCase_ = 0", message.getOneofDecl(oneofIndex).getName());
        for (var field : message.getFieldList()) {
            if (field.hasOneofIndex() && field.getOneofIndex() == oneofIndex) {
                var fieldName = field.getName() + "_";
                if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                     // Should not happen in oneof according to protobuf spec, but just in case
                     cb.addStatement("$L = $T.emptyList()", fieldName, java.util.Collections.class);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    cb.addStatement("$L = null", fieldName);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    cb.addStatement("$L = \"\"", fieldName);
                } else {
                    // Primitive types. This is a bit hacky without full type mapping here,
                    // but we can use common defaults.
                    cb.addStatement("$L = $L", fieldName, ProtoUtils.getDefaultReturnValue(PROTO_TYPE_TO_TYPE_NAME.get(field.getType()).toString()));
                }
            }
        }
        return cb.build();
    }

    private CodeBlock generateHasFieldCode(DescriptorProtos.DescriptorProto message, DescriptorProtos.FieldDescriptorProto field) {
        if (field.hasOneofIndex()) {
            return CodeBlock.of("return $LCase_ == $L;\n", message.getOneofDecl(field.getOneofIndex()).getName(), field.getNumber());
        }
        return CodeBlock.of("return $L_ != null;\n", field.getName());
    }

    private void generateEnumTypes(TypeSpec.Builder parentBuilder, DescriptorProtos.EnumDescriptorProto enumType, ClassName outerClassName, String[] parentNames) {
        var enumContext = new EnumContext(enumType, outerClassName, parentNames);
        var enumName = enumType.getName();
        var enumBuilder = TypeSpec.enumBuilder(enumName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(com.google.protobuf.ProtocolMessageEnum.class);

        for (var value : enumType.getValueList()) {
            enumBuilder.addEnumConstant(value.getName(), TypeSpec.anonymousClassBuilder("$L", value.getNumber()).build());
        }

        enumBuilder.addEnumConstant("UNRECOGNIZED", TypeSpec.anonymousClassBuilder("-1").build());

        enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
        enumBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(int.class, "value")
                .addStatement("this.value = value")
                .build());

        enumBuilder.addMethod(CodegenMethods.Enum.getNumber());
        enumBuilder.addMethod(CodegenMethods.Enum.forNumber(enumContext));
        enumBuilder.addMethod(CodegenMethods.Enum.getDescriptor(enumContext));
        enumBuilder.addMethod(CodegenMethods.Enum.getValueDescriptor());
        enumBuilder.addMethod(CodegenMethods.Enum.getDescriptorForType());
        enumBuilder.addMethod(CodegenMethods.Enum.valueOf(enumName));

        parentBuilder.addType(enumBuilder.build());
    }


    private void generateMapFieldPrototypes(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, CodegenContext ctx, List<String> currentScope) {
        for (var field : message.getFieldList()) {
            if (ctx.isMapField(field)) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = ctx.getFieldType(keyField, currentScope);
                var valueType = ctx.getFieldType(valueField, currentScope);

                var entryClassName = ParameterizedTypeName.get(ClassName.get(com.google.protobuf.MapEntry.class), keyType.box(), valueType.box());

                classBuilder.addField(FieldSpec.builder(entryClassName, field.getName() + "_DefaultEntry", Modifier.PRIVATE, Modifier.STATIC).build());

                classBuilder.addStaticBlock(CodeBlock.builder()
                        .addStatement("$L_DefaultEntry = $T.newDefaultInstance(getDescriptor().getNestedTypes().stream().filter(t -> t.getName().equals($S)).findFirst().get(), $T.$L, $L, $T.$L, $L)",
                                field.getName(),
                                com.google.protobuf.MapEntry.class,
                                entryDescriptor.getName(),
                                com.google.protobuf.WireFormat.FieldType.class, getWireFormatType(keyField.getType()), getDefaultValue(keyField, ctx, currentScope),
                                com.google.protobuf.WireFormat.FieldType.class, getWireFormatType(valueField.getType()), getDefaultValue(valueField, ctx, currentScope))
                        .build());
            }
        }
    }

    private CodeBlock getDefaultValue(DescriptorProtos.FieldDescriptorProto field, CodegenContext ctx, List<String> currentScope) {
        return switch (field.getType()) {
            case TYPE_DOUBLE -> CodeBlock.of("0.0d");
            case TYPE_FLOAT -> CodeBlock.of("0.0f");
            case TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 -> CodeBlock.of("0L");
            case TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 -> CodeBlock.of("0");
            case TYPE_BOOL -> CodeBlock.of("false");
            case TYPE_STRING -> CodeBlock.of("$S", "");
            case TYPE_BYTES -> CodeBlock.of("$T.EMPTY", com.google.protobuf.ByteString.class);
            case TYPE_ENUM -> {
                var type = ctx.getFieldType(field, currentScope);
                yield CodeBlock.of("$T.forNumber(0)", type);
            }
            default -> CodeBlock.of("null");
        };
    }

    private com.google.protobuf.WireFormat.FieldType getWireFormatType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> com.google.protobuf.WireFormat.FieldType.DOUBLE;
            case TYPE_FLOAT -> com.google.protobuf.WireFormat.FieldType.FLOAT;
            case TYPE_INT64 -> com.google.protobuf.WireFormat.FieldType.INT64;
            case TYPE_UINT64 -> com.google.protobuf.WireFormat.FieldType.UINT64;
            case TYPE_INT32 -> com.google.protobuf.WireFormat.FieldType.INT32;
            case TYPE_FIXED64 -> com.google.protobuf.WireFormat.FieldType.FIXED64;
            case TYPE_FIXED32 -> com.google.protobuf.WireFormat.FieldType.FIXED32;
            case TYPE_BOOL -> com.google.protobuf.WireFormat.FieldType.BOOL;
            case TYPE_STRING -> com.google.protobuf.WireFormat.FieldType.STRING;
            case TYPE_GROUP -> com.google.protobuf.WireFormat.FieldType.GROUP;
            case TYPE_MESSAGE -> com.google.protobuf.WireFormat.FieldType.MESSAGE;
            case TYPE_BYTES -> com.google.protobuf.WireFormat.FieldType.BYTES;
            case TYPE_UINT32 -> com.google.protobuf.WireFormat.FieldType.UINT32;
            case TYPE_ENUM -> com.google.protobuf.WireFormat.FieldType.ENUM;
            case TYPE_SFIXED32 -> com.google.protobuf.WireFormat.FieldType.SFIXED32;
            case TYPE_SFIXED64 -> com.google.protobuf.WireFormat.FieldType.SFIXED64;
            case TYPE_SINT32 -> com.google.protobuf.WireFormat.FieldType.SINT32;
            case TYPE_SINT64 -> com.google.protobuf.WireFormat.FieldType.SINT64;
        };
    }


    private void generateMessageTypes(TypeSpec.Builder parentBuilder, CodegenContext ctx, DescriptorProtos.DescriptorProto message, String... parentNames) {
        var messageName = ProtoUtils.toPascalCase(message.getName());
        var interfaceName = messageName + "OrBuilder";

        var allNames = java.util.Arrays.copyOf(parentNames, parentNames.length + 1);
        allNames[parentNames.length] = messageName;

        var currentScope = java.util.Arrays.asList(java.util.Arrays.copyOfRange(allNames, 1, allNames.length));

        var messageClassName = ClassName.get(ctx.packageName(), allNames[0],
                java.util.Arrays.copyOfRange(allNames, 1, allNames.length));

        var javaImplements = getJavaImplements(message);
        boolean enhancedOneof = getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, ctx.fileEnhancedOneof());
        boolean generateOneofCase = getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_GENERATE_ONEOF_CASE_OPTION, ctx.fileGenerateOneofCase());

        var interfaceClassName = ClassName.get(ctx.packageName(), allNames[0],
                java.util.stream.Stream.concat(
                        java.util.Arrays.stream(java.util.Arrays.copyOfRange(allNames, 1, allNames.length - 1)),
                        java.util.stream.Stream.of(interfaceName)
                ).toArray(String[]::new));

        var msgCtx = new MessageContext(
                message,
                messageClassName,
                interfaceClassName,
                currentScope,
                allNames,
                enhancedOneof,
                generateOneofCase
        );

        generateOrBuilderInterface(parentBuilder, msgCtx, ctx, interfaceName, javaImplements);

        var classBuilder = TypeSpec.classBuilder(messageName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(messageParentClass)
                .addSuperinterface(interfaceClassName);

        // Nested types
        for (var nestedMessage : message.getNestedTypeList()) {
            generateMessageTypes(classBuilder, ctx, nestedMessage, allNames);
        }
        for (var nestedEnum : message.getEnumTypeList()) {
            generateEnumTypes(classBuilder, nestedEnum, ClassName.get(ctx.packageName(), ctx.outerClassName()), allNames);
        }

        generateDescriptor(classBuilder, message, ClassName.get(ctx.packageName(), ctx.outerClassName()), allNames);
        generateMapFieldPrototypes(classBuilder, message, ctx, currentScope);
        generateDefaultInstance(classBuilder, messageClassName);
        generateInternalFieldAccessorTable(classBuilder, message, messageClassName);
        generateFieldsAndGetters(classBuilder, msgCtx, ctx);
        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            classBuilder.addField(FieldSpec.builder(int.class, message.getOneofDecl(i).getName() + "Case_", Modifier.PRIVATE).initializer("0").build());
        }
        generateOneofs(classBuilder, msgCtx, ctx);

        classBuilder.addField(FieldSpec.builder(int.class, "memoizedSize", Modifier.PRIVATE)
                .initializer("-1")
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder("internalGetMapFieldReflection")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .returns(com.google.protobuf.MapFieldReflectionAccessor.class)
                .addParameter(int.class, "fieldNumber")
                .beginControlFlow("switch (fieldNumber)")
                .addCode(message.getFieldList().stream()
                        .filter(ctx::isMapField)
                        .map(f -> "case " + f.getNumber() + ": return " + f.getName() + "_;\n")
                        .collect(Collectors.joining()))
                .addStatement("default: throw new $T($S + fieldNumber)", RuntimeException.class, "Invalid map field number: ")
                .endControlFlow()
                .build());

        generateWriteToMethod(classBuilder, message, ctx, currentScope);
        generateGetSerializedSize(classBuilder, message, ctx, currentScope);
        generateRequiredAbstractMethods(classBuilder, messageClassName);
        generateNewBuilderMethods(classBuilder, messageClassName);
        generateNoArgConstructor(classBuilder, message, ctx, currentScope);
        generateBuilderConstructorToMessage(classBuilder, message, messageClassName, ctx);
        generateParserField(classBuilder, messageClassName);
        generateParseFromMethods(classBuilder, messageClassName);
        generateParserConstructor(classBuilder, message, messageClassName, ctx, currentScope);

        var builderClass = generateBuilderClass(message, messageClassName, interfaceClassName, ctx, msgCtx);
        classBuilder.addType(builderClass);

        parentBuilder.addType(classBuilder.build());
    }

    private void generateNoArgConstructor(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, CodegenContext ctx, java.util.List<String> currentScope) {
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE);

        for (var field : message.getFieldList()) {
            var fieldName = field.getName() + "_";
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
                    && field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                constructor.addStatement("$L = \"\"", fieldName);
            } else if (ctx.isMapField(field)) {
                constructor.addStatement("$L = $T.emptyMapField($L_DefaultEntry)", fieldName, com.google.protobuf.MapField.class, field.getName());
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("$L = $T.EMPTY", fieldName, com.google.protobuf.LazyStringArrayList.class);
                } else {
                    constructor.addStatement("$L = $T.emptyList()", fieldName, java.util.Collections.class);
                }
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                var fieldType = ctx.getFieldType(field, currentScope);
                constructor.addStatement("$L = $T.forNumber(0)", fieldName, fieldType);
            }
        }
        classBuilder.addMethod(constructor.build());
    }


    private void generateDefaultInstance(TypeSpec.Builder classBuilder, ClassName messageClassName) {
        var defaultInstanceField = FieldSpec.builder(messageClassName, "DEFAULT_INSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .build();
        classBuilder.addField(defaultInstanceField);
        classBuilder.addStaticBlock(CodeBlock.of("DEFAULT_INSTANCE = new $T();\n", messageClassName));

        classBuilder.addMethod(MethodSpec.methodBuilder("getDefaultInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(messageClassName)
                .addStatement("return DEFAULT_INSTANCE")
                .build());
    }

    private TypeName getOrBuilderType(TypeName typeName) {
        if (typeName instanceof ClassName) {
            ClassName cn = (ClassName) typeName;
            List<String> simpleNames = cn.simpleNames();
            String last = simpleNames.get(simpleNames.size() - 1);
            if (simpleNames.size() == 1) {
                return ClassName.get(cn.packageName(), last + "OrBuilder");
            } else {
                return ClassName.get(cn.packageName(), simpleNames.get(0),
                        Stream.concat(
                                simpleNames.subList(1, simpleNames.size() - 1).stream(),
                                Stream.of(last + "OrBuilder")
                        ).toArray(String[]::new));
            }
        }
        return typeName;
    }

    private void generateOrBuilderInterface(TypeSpec.Builder parentBuilder, MessageContext msgCtx, CodegenContext ctx, String interfaceName, Optional<String> javaImplements) {
        var interfaceBuilder = TypeSpec.interfaceBuilder(interfaceName)
                .addModifiers(Modifier.PUBLIC);

        if (javaImplements.isPresent()) {
            interfaceBuilder.addSuperinterface(ClassName.bestGuess(javaImplements.get()));
        }

        interfaceBuilder.addSuperinterface(ClassName.get("com.google.protobuf", "MessageOrBuilder"));

        String canonicalName = msgCtx.messageClassName().canonicalName();
        var oneofInterfaces = oneofInterfacesByType.getOrDefault(canonicalName, Collections.emptyList());
        if (!oneofInterfaces.isEmpty()) {
            interfaceBuilder.addModifiers(Modifier.NON_SEALED);
            for (var iface : oneofInterfaces) {
                interfaceBuilder.addSuperinterface(iface);
            }
        }

        for (var field : msgCtx.message().getFieldList()) {
            var fieldType = ctx.getFieldType(field, msgCtx.currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            if (fieldCtx.isMap()) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = ctx.getFieldType(keyField, msgCtx.currentScope());
                var valueType = ctx.getFieldType(valueField, msgCtx.currentScope());

                var mapCtx = new MapFieldContext(fieldCtx, entryDescriptor, keyType, valueType, null);

                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.containsMapKey(mapCtx));
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getMapField(mapCtx));
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getMapCount(mapCtx));
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getMapOrDefault(mapCtx));
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getMapOrThrow(mapCtx));

                if (generateDeprecated) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getMapDeprecated(mapCtx, fieldType));
                }
            } else if (fieldCtx.isRepeated()) {
                var genericType = PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    genericType = ctx.resolveTypeName(field.getTypeName(), msgCtx.currentScope());
                }
                if (genericType == null) genericType = TypeName.get(Object.class);
                genericType = genericType.box();

                var repeatedCtx = new RepeatedFieldContext(fieldCtx, genericType);

                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedList(repeatedCtx, fieldType));

                if (repeatedCtx.isString()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedBytes(fieldCtx.pascalName()));
                }

                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedCount(repeatedCtx));
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedElement(repeatedCtx));

                if (repeatedCtx.isMessage()) {
                    var orBuilderType = getOrBuilderType(genericType);
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedOrBuilderList(repeatedCtx, orBuilderType));
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedOrBuilder(repeatedCtx, orBuilderType));
                }

                if (repeatedCtx.isEnum()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedValueList(repeatedCtx));
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getRepeatedValue(repeatedCtx));
                }
            } else {
                var singularCtx = new SingularFieldContext(fieldCtx, msgCtx);

                if (singularCtx.isMessage() || singularCtx.hasOneofIndex()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.hasField(singularCtx));
                }

                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getField(singularCtx));

                if (singularCtx.isEnum()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getFieldValue(fieldCtx.pascalName()));
                }

                if (singularCtx.isMessage()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getFieldOrBuilder(singularCtx, getOrBuilderType(fieldType)));
                }

                if (singularCtx.isString()) {
                    interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getFieldBytes(fieldCtx.pascalName()));
                }
            }
        }

        for (int i = 0; i < msgCtx.message().getOneofDeclCount(); i++) {
            var oneof = msgCtx.message().getOneofDecl(i);
            var oneofCtx = OneofContext.create(oneof, i, msgCtx.messageClassName());

            if (msgCtx.enhancedOneof()) {
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getOneof(oneofCtx));
            }

            if (msgCtx.generateOneofCase()) {
                interfaceBuilder.addMethod(CodegenMethods.OrBuilder.getOneofCase(oneofCtx));
            }
        }
        parentBuilder.addType(interfaceBuilder.build());
    }

    private void generateInternalFieldAccessorTable(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, ClassName messageClassName) {
        classBuilder.addField(FieldSpec.builder(getFieldAccessorTableClass(), "internal_fieldAccessorTable", Modifier.PRIVATE, Modifier.STATIC).build());

        String allNames = Stream.concat(
                        message.getFieldList().stream().map(f -> ProtoUtils.toPascalCase(f.getName())),
                        message.getOneofDeclList().stream().map(o -> ProtoUtils.toPascalCase(o.getName())))
                .map(n -> "\"" + n + "\"")
                .collect(Collectors.joining(", "));

        classBuilder.addStaticBlock(CodeBlock.builder()
                .addStatement("internal_fieldAccessorTable = new $T(getDescriptor(), new String[] { $L })",
                        getFieldAccessorTableClass(),
                        allNames)
                .build());

        classBuilder.addMethod(CodegenMethods.Message.internalGetFieldAccessorTable(messageClassName, getFieldAccessorTableClass()));
    }

    private void generateFieldsAndGetters(TypeSpec.Builder classBuilder, MessageContext msgCtx, CodegenContext ctx) {
        for (var field : msgCtx.message().getFieldList()) {
            var fieldType = ctx.getFieldType(field, msgCtx.currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            // Generate private field
            var privateFieldType = fieldType;
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                if (fieldCtx.isRepeated()) {
                    privateFieldType = ClassName.get("com.google.protobuf", "LazyStringList");
                } else {
                    privateFieldType = TypeName.get(Object.class);
                }
            } else if (fieldCtx.isMap()) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, msgCtx.currentScope());
                var valueType = ctx.getFieldType(valueField, msgCtx.currentScope());
                privateFieldType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "MapField"), keyType.box(), valueType.box());
            }

            var fieldSpec = FieldSpec.builder(privateFieldType, fieldCtx.internalName(), Modifier.PRIVATE).build();
            classBuilder.addField(fieldSpec);

            if (fieldCtx.isMap()) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = ctx.getFieldType(keyField, msgCtx.currentScope());
                var valueType = ctx.getFieldType(valueField, msgCtx.currentScope());

                var mapCtx = new MapFieldContext(fieldCtx, entryDescriptor, keyType, valueType, null);

                classBuilder.addMethod(CodegenMethods.Message.containsMapKey(mapCtx));
                classBuilder.addMethod(CodegenMethods.Message.getMapField(mapCtx, fieldType));
                classBuilder.addMethod(CodegenMethods.Message.getMapCount(mapCtx));
                classBuilder.addMethod(CodegenMethods.Message.getMapOrDefault(mapCtx));
                classBuilder.addMethod(CodegenMethods.Message.getMapOrThrow(mapCtx));

                if (generateDeprecated) {
                    classBuilder.addMethod(CodegenMethods.Message.getMapDeprecated(mapCtx, fieldType));
                }
            } else if (fieldCtx.isRepeated()) {
                var genericType = PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    genericType = ctx.resolveTypeName(field.getTypeName(), msgCtx.currentScope());
                }
                if (genericType == null) genericType = TypeName.get(Object.class);
                genericType = genericType.box();

                var repeatedCtx = new RepeatedFieldContext(fieldCtx, genericType);

                if (repeatedCtx.isString()) {
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedListString(repeatedCtx));
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedBytes(repeatedCtx));
                } else {
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedList(repeatedCtx, fieldType));
                }

                classBuilder.addMethod(CodegenMethods.Message.getRepeatedCount(repeatedCtx));
                classBuilder.addMethod(CodegenMethods.Message.getRepeatedElement(repeatedCtx));

                if (repeatedCtx.isMessage()) {
                    var orBuilderType = getOrBuilderType(genericType);
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedOrBuilderList(repeatedCtx, orBuilderType));
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedOrBuilder(repeatedCtx, orBuilderType));
                }

                if (repeatedCtx.isEnum()) {
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedValueList(repeatedCtx));
                    classBuilder.addMethod(CodegenMethods.Message.getRepeatedValue(repeatedCtx));
                }
            } else {
                var singularCtx = new SingularFieldContext(fieldCtx, msgCtx);

                if (singularCtx.isMessage() || singularCtx.hasOneofIndex()) {
                    classBuilder.addMethod(CodegenMethods.Message.hasField(singularCtx, generateHasFieldCode(msgCtx.message(), field)));
                }

                classBuilder.addMethod(CodegenMethods.Message.getField(singularCtx));

                if (singularCtx.isEnum()) {
                    classBuilder.addMethod(CodegenMethods.Message.getFieldValue(singularCtx));
                }

                if (singularCtx.isMessage()) {
                    classBuilder.addMethod(CodegenMethods.Message.getFieldOrBuilder(singularCtx, getOrBuilderType(fieldType)));
                }

                if (singularCtx.isString()) {
                    classBuilder.addMethod(CodegenMethods.Message.getFieldBytes(singularCtx));
                }
            }
        }
    }

    private void generateRequiredAbstractMethods(TypeSpec.Builder classBuilder, ClassName messageClassName) {
        var builderClassName = messageClassName.nestedClass("Builder");
        classBuilder.addMethod(CodegenMethods.Message.toBuilder(messageClassName, builderClassName));
        classBuilder.addMethod(CodegenMethods.Message.getParserForType());
        classBuilder.addMethod(CodegenMethods.Message.newBuilderForType(builderClassName));
        classBuilder.addMethod(CodegenMethods.Message.newBuilderForTypeWithParent(messageParentClass, builderClassName));
        classBuilder.addMethod(CodegenMethods.Message.getDefaultInstanceForType(messageClassName));
        classBuilder.addMethod(CodegenMethods.Message.getUnknownFields());
        classBuilder.addMethod(CodegenMethods.Message.isInitialized());
    }

    private void generateNewBuilderMethods(TypeSpec.Builder classBuilder, ClassName messageClassName) {
        var builderClassName = messageClassName.nestedClass("Builder");
        classBuilder.addMethod(CodegenMethods.Message.newBuilder(messageClassName, builderClassName));
        classBuilder.addMethod(CodegenMethods.Message.newBuilderWithPrototype(messageClassName, builderClassName));
    }

    private void generateBuilderConstructorToMessage(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, ClassName messageClassName, CodegenContext ctx) {
        var builderClassName = messageClassName.nestedClass("Builder");
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(builderClassName, "builder")
                .addStatement("super(builder)");

        for (var field : message.getFieldList()) {
            var fieldName = field.getName() + "_";
            if (ctx.isMapField(field)) {
                constructor.addStatement("this.$L = builder.$L.copy()", fieldName, fieldName);
                constructor.addStatement("this.$L.makeImmutable()", fieldName);
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("this.$L = builder.$L.getUnmodifiableView()", fieldName, fieldName);
                } else {
                    constructor.addStatement("this.$L = $T.unmodifiableList(builder.$L)", fieldName, java.util.Collections.class, fieldName);
                }
            } else {
                constructor.addStatement("this.$L = builder.$L", fieldName, fieldName);
            }
        }
        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            var oneofName = message.getOneofDecl(i).getName() + "Case_";
            constructor.addStatement("this.$L = builder.$L", oneofName, oneofName);
        }
        classBuilder.addMethod(constructor.build());
    }

    private void generateParserField(TypeSpec.Builder classBuilder, ClassName messageClassName) {
        var parserType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "Parser"), messageClassName);
        var abstractParser = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "AbstractParser"), messageClassName);

        var parsePartialFrom = MethodSpec.methodBuilder("parsePartialFrom")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(messageClassName)
                .addParameter(ClassName.get("com.google.protobuf", "CodedInputStream"), "input")
                .addParameter(ClassName.get("com.google.protobuf", "ExtensionRegistryLite"), "extensionRegistry")
                .addException(com.google.protobuf.InvalidProtocolBufferException.class)
                .beginControlFlow("try")
                .addStatement("return new $T(input, extensionRegistry)", messageClassName)
                .nextControlFlow("catch ($T e)", java.io.IOException.class)
                .addStatement("throw new $T(e).setUnfinishedMessage(getDefaultInstance())", com.google.protobuf.InvalidProtocolBufferException.class)
                .endControlFlow()
                .build();

        var parserInitializer = TypeSpec.anonymousClassBuilder("")
                .superclass(abstractParser)
                .addMethod(parsePartialFrom)
                .build();

        classBuilder.addField(FieldSpec.builder(parserType, "PARSER", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", parserInitializer)
                .build());

        classBuilder.addMethod(CodegenMethods.Message.parser(messageClassName));
    }

    private void generateParseFromMethods(TypeSpec.Builder classBuilder, ClassName messageClassName) {
        var byteString = ClassName.get("com.google.protobuf", "ByteString");
        var inputStream = ClassName.get("java.io", "InputStream");
        var byteBuffer = ClassName.get("java.nio", "ByteBuffer");
        var codedInputStream = ClassName.get("com.google.protobuf", "CodedInputStream");

        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, byteBuffer, "data", false));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, byteBuffer, "data", true));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, byteString, "data", false));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, byteString, "data", true));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, TypeName.get(byte[].class), "data", false));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, TypeName.get(byte[].class), "data", true));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, inputStream, "input", false));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, inputStream, "input", true));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, codedInputStream, "input", false));
        classBuilder.addMethod(CodegenMethods.Message.parseFrom(messageClassName, codedInputStream, "input", true));
        classBuilder.addMethod(CodegenMethods.Message.parseDelimitedFrom(messageClassName, false));
        classBuilder.addMethod(CodegenMethods.Message.parseDelimitedFrom(messageClassName, true));
    }

    private void generateParserConstructor(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, ClassName messageClassName, CodegenContext ctx, java.util.List<String> currentScope) {
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ClassName.get("com.google.protobuf", "CodedInputStream"), "input")
                .addParameter(ClassName.get("com.google.protobuf", "ExtensionRegistryLite"), "extensionRegistry")
                .addException(IOException.class);

        constructor.addStatement("this()");

        for (var field : message.getFieldList()) {
            if (ctx.isMapField(field)) {
                constructor.addStatement("this.$L_ = $T.newMapField($L_DefaultEntry)", field.getName(), com.google.protobuf.MapField.class, field.getName());
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("this.$L_ = new $T()", field.getName(), com.google.protobuf.LazyStringArrayList.class);
                } else {
                    constructor.addStatement("this.$L_ = new $T<>()", field.getName(), java.util.ArrayList.class);
                }
            }
        }

        constructor.beginControlFlow("try");
        constructor.addStatement("boolean done = false");
        constructor.beginControlFlow("while (!done)");
        constructor.addStatement("int tag = input.readTag()");
        constructor.beginControlFlow("switch (tag)");
        constructor.addCode("case 0:\n  done = true;\n  break;\n");

        for (var field : message.getFieldList()) {
            int wireType = getWireType(field.getType());
            int tag = (field.getNumber() << 3) | wireType;
            constructor.addCode("case $L: {\n", tag);

            var fieldName = field.getName() + "_";
            var readMethod = ProtoUtils.getReadMethodName(field.getType());

            if (ctx.isMapField(field)) {
                var innerType = ctx.resolveTypeName(field.getTypeName(), currentScope);
                constructor.addStatement("  $T entry = input.readMessage($T.PARSER, extensionRegistry)", innerType, innerType);
                constructor.addStatement("  $L.getMutableMap().put(entry.getKey(), entry.getValue())", fieldName);
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                var innerType = PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    innerType = ctx.resolveTypeName(field.getTypeName(), currentScope);
                }
                if (innerType == null) {
                    innerType = TypeName.get(Object.class);
                }
                innerType = innerType.box();

                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    constructor.addStatement("  $L.add(input.readMessage($T.PARSER, extensionRegistry))", fieldName, innerType);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    constructor.addStatement("  $L.add($T.forNumber(input.readEnum()))", fieldName, innerType);
                } else {
                    constructor.addStatement("  $L.add(input.$L())", fieldName, readMethod);
                }
            } else {
                if (field.hasOneofIndex()) {
                    constructor.addCode(generateClearOneofCode(message, field.getOneofIndex()));
                    constructor.addStatement("$LCase_ = $L", message.getOneofDecl(field.getOneofIndex()).getName(), field.getNumber());
                }
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    var fieldType = ctx.getFieldType(field, currentScope);
                    constructor.beginControlFlow("  if ($L != null)", fieldName);
                    constructor.addStatement("var builder = $L.toBuilder()", fieldName);
                    constructor.addStatement("input.readMessage(builder, extensionRegistry)");
                    constructor.addStatement("$L = ($T) builder.buildPartial()", fieldName, fieldType);
                    constructor.nextControlFlow("else");
                    constructor.addStatement("$L = input.readMessage($T.PARSER, extensionRegistry)", fieldName, fieldType);
                    constructor.endControlFlow();
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("  $L = input.readStringRequireUtf8()", fieldName);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    var fieldType = ctx.getFieldType(field, currentScope);
                    constructor.addStatement("  $L = $T.forNumber(input.readEnum())", fieldName, fieldType);
                } else {
                    constructor.addStatement("  $L = input.$L()", fieldName, readMethod);
                }
            }
            constructor.addStatement("  break");
            constructor.addCode("}\n");
        }

        constructor.addCode("default: {\n");
        constructor.addStatement("  if (!input.skipField(tag)) { done = true; }");
        constructor.addStatement("  break");
        constructor.addCode("}\n");
        constructor.endControlFlow(); // switch
        constructor.endControlFlow(); // while
        constructor.nextControlFlow("catch ($T e)", com.google.protobuf.InvalidProtocolBufferException.class);
        constructor.addStatement("throw e.setUnfinishedMessage(this)");
        constructor.nextControlFlow("catch ($T e)", IOException.class);
        constructor.addStatement("throw new $T(e).setUnfinishedMessage(this)", com.google.protobuf.InvalidProtocolBufferException.class);
        constructor.nextControlFlow("finally");
        for (var field : message.getFieldList()) {
            if (ctx.isMapField(field)) {
                constructor.addStatement("this.$L_.makeImmutable()", field.getName());
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("this.$L_ = $L_.getUnmodifiableView()", field.getName(), field.getName());
                } else {
                    constructor.addStatement("this.$L_ = $T.unmodifiableList($L_)", field.getName(), java.util.Collections.class, field.getName());
                }
            }
        }
        constructor.endControlFlow();

        classBuilder.addMethod(constructor.build());
    }

    private static int getWireType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_INT32, TYPE_INT64, TYPE_UINT32, TYPE_UINT64, TYPE_SINT32, TYPE_SINT64, TYPE_BOOL, TYPE_ENUM -> 0;
            case TYPE_DOUBLE, TYPE_FIXED64, TYPE_SFIXED64 -> 1;
            case TYPE_STRING, TYPE_BYTES, TYPE_MESSAGE -> 2;
            case TYPE_FLOAT, TYPE_FIXED32, TYPE_SFIXED32 -> 5;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    private TypeSpec generateBuilderClass(DescriptorProtos.DescriptorProto message, ClassName messageClassName, ClassName interfaceClassName, CodegenContext ctx, MessageContext msgCtx) {
        var builderClassName = messageClassName.nestedClass("Builder");
        var builderClassBuilder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(messageParentClass.nestedClass("Builder"), builderClassName))
                .addSuperinterface(interfaceClassName);

        // Add fields to builder
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, msgCtx.currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            var privateFieldType = fieldType;
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                    privateFieldType = ClassName.get("com.google.protobuf", "LazyStringList");
                } else {
                    privateFieldType = TypeName.get(Object.class);
                }
            } else if (fieldCtx.isMap()) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, msgCtx.currentScope());
                var valueType = ctx.getFieldType(valueField, msgCtx.currentScope());
                privateFieldType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "MapField"), keyType.box(), valueType.box());
            }

            var fieldBuilder = FieldSpec.builder(privateFieldType, fieldCtx.internalName(), Modifier.PRIVATE);
            if (fieldCtx.isMap()) {
                fieldBuilder.initializer("$T.newMapField($L_DefaultEntry)", com.google.protobuf.MapField.class, field.getName());
            } else if (fieldCtx.isRepeated()) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    fieldBuilder.initializer("new $T()", com.google.protobuf.LazyStringArrayList.class);
                } else {
                    fieldBuilder.initializer("$T.emptyList()", java.util.Collections.class);
                }
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                fieldBuilder.initializer("$S", "");
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                // null by default
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                fieldBuilder.initializer("$T.forNumber(0)", fieldType);
            } else {
                fieldBuilder.initializer("$L", ProtoUtils.getDefaultReturnValue(fieldType.toString()));
            }
            builderClassBuilder.addField(fieldBuilder.build());
        }

        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            builderClassBuilder.addField(FieldSpec.builder(int.class, message.getOneofDecl(i).getName() + "Case_", Modifier.PRIVATE).initializer("0").build());
        }

        // Constructors
        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super()")
                .build());

        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(messageParentClass.nestedClass("BuilderParent"), "parent")
                .addStatement("super(parent)")
                .build());

        // Methods for each field
        generateBuilderMethods(builderClassBuilder, msgCtx, ctx, builderClassName);

        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            var oneof = message.getOneofDecl(i);
            var oneofCtx = OneofContext.create(oneof, i, messageClassName);

            if (msgCtx.enhancedOneof()) {
                var switchCode = CodeBlock.builder();
                switchCode.beginControlFlow("switch ($LCase_)", oneofCtx.oneofName());
                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        switchCode.addStatement("case $L: return get$L()", field.getNumber(), ProtoUtils.toPascalCase(field.getName()));
                    }
                }
                switchCode.addStatement("default: return null");
                switchCode.endControlFlow();
                builderClassBuilder.addMethod(CodegenMethods.Builder.getOneof(oneofCtx, switchCode.build()));
            }

            if (msgCtx.generateOneofCase()) {
                builderClassBuilder.addMethod(CodegenMethods.Builder.getOneofCase(oneofCtx, generateGetCaseCode(message, i, oneof.getName(), oneofCtx.enumName())));
            }

            builderClassBuilder.addMethod(CodegenMethods.Builder.clearOneof(oneofCtx, builderClassName, generateClearOneofCode(message, i)));
        }

        builderClassBuilder.addMethod(CodegenMethods.Builder.build(messageClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.buildPartial(messageClassName));

        // clear()
        var clearMethod = MethodSpec.methodBuilder("clear")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addStatement("super.clear()");
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, msgCtx.currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            if (fieldCtx.isMap()) {
                clearMethod.addStatement("$L.getMutableMap().clear()", fieldCtx.internalName());
            } else if (fieldCtx.isRepeated()) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    clearMethod.addStatement("$L = new $T()", fieldCtx.internalName(), com.google.protobuf.LazyStringArrayList.class);
                } else {
                    clearMethod.addStatement("$L = new $T<>()", fieldCtx.internalName(), java.util.ArrayList.class);
                }
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                clearMethod.addStatement("$L = $S", fieldCtx.internalName(), "");
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                clearMethod.addStatement("$L = null", fieldCtx.internalName());
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                clearMethod.addStatement("$L = $T.forNumber(0)", fieldCtx.internalName(), fieldType);
            } else {
                clearMethod.addStatement("$L = $L", fieldCtx.internalName(), ProtoUtils.getDefaultReturnValue(fieldType.toString()));
            }
        }
        clearMethod.addStatement("return this");
        builderClassBuilder.addMethod(clearMethod.build());

        builderClassBuilder.addMethod(CodegenMethods.Builder.getDefaultInstanceForType(messageClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.getDescriptorForType(messageClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.getDescriptor(messageClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.isInitialized());
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromCodedInput(builderClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetFieldAccessorTable(messageClassName, getFieldAccessorTableClass()));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMapFieldReflection(msgCtx, ctx));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMutableMapFieldReflection(msgCtx, ctx));
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromMessage(messageClassName, builderClassName));

        // mergeFrom(SpecificMessage other)
        var mergeFromSpecificMethod = MethodSpec.methodBuilder("mergeFrom")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(messageClassName, "other")
                .addStatement("if (other == $T.getDefaultInstance()) return this", messageClassName);

        for (var field : message.getFieldList()) {
            var fieldName = field.getName();
            var pascalName = ProtoUtils.toPascalCase(fieldName);
            if (ctx.isMapField(field)) {
                mergeFromSpecificMethod.beginControlFlow("if (!other.get$LMap().isEmpty())", pascalName)
                        .addStatement("$L_.getMutableMap().putAll(other.get$LMap())", fieldName, pascalName)
                        .addStatement("onChanged()")
                        .endControlFlow();
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                mergeFromSpecificMethod.beginControlFlow("if (!other.$L_.isEmpty())", fieldName)
                        .addStatement("ensure$LIsMutable()", pascalName)
                        .addStatement("$L_.addAll(other.$L_)", fieldName, fieldName)
                        .addStatement("onChanged()")
                        .endControlFlow();
            } else {
                var condition = getWriteCondition(field.getType(), "other.get" + pascalName + "()");
                if (condition != null) {
                    mergeFromSpecificMethod.beginControlFlow("if ($L)", condition);
                }
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    mergeFromSpecificMethod.addStatement("merge$L(other.get$L())", pascalName, pascalName);
                } else {
                    mergeFromSpecificMethod.addStatement("set$L(other.get$L())", pascalName, pascalName);
                }
                if (condition != null) {
                    mergeFromSpecificMethod.endControlFlow();
                }
            }
        }
        mergeFromSpecificMethod.addStatement("onChanged()")
                .addStatement("return this");
        builderClassBuilder.addMethod(mergeFromSpecificMethod.build());

        return builderClassBuilder.build();
    }

    private void generateDescriptor(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, ClassName outerClassName, String[] allNames) {
        var cb = CodeBlock.builder();
        cb.add("descriptor = $T.getDescriptor().findMessageTypeByName($S)", outerClassName, allNames[1]);
        for (int i = 2; i < allNames.length; i++) {
            cb.add(".findNestedTypeByName($S)", allNames[i]);
        }
        cb.add(";\n");

        classBuilder.addStaticBlock(cb.build());

        var descriptor = FieldSpec.builder(Descriptors.Descriptor.class, "descriptor", Modifier.PRIVATE, Modifier.STATIC).build();
        classBuilder.addField(descriptor);
        classBuilder.addMethod(MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .returns(Descriptors.Descriptor.class)
                .addStatement("return descriptor")
                .build());
    }

    private void generateFileDescriptor(TypeSpec.Builder outerClassBuilder, DescriptorProtos.FileDescriptorProto fileDescriptor) {
        var descriptorDataChunks = ProtoUtils.splitAndEscapeBytes(fileDescriptor.toByteArray());

        var arrayBuilder = CodeBlock.builder().add("{\n").indent();
        for (int i = 0; i < descriptorDataChunks.length; i++) {
            arrayBuilder.add("\"$L\"", descriptorDataChunks[i]);
            if (i < descriptorDataChunks.length - 1) {
                arrayBuilder.add(",\n");
            }
        }
        arrayBuilder.unindent().add("\n}");

        outerClassBuilder.addField(FieldSpec.builder(String[].class, "descriptorData", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(arrayBuilder.build())
                .build());

        outerClassBuilder.addField(FieldSpec.builder(Descriptors.FileDescriptor.class, "fileDescriptor", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .build());

        outerClassBuilder.addStaticBlock(CodeBlock.builder()
                .addStatement("fileDescriptor = $T.internalBuildGeneratedFileFrom(descriptorData, new $T[0])",
                        Descriptors.FileDescriptor.class, Descriptors.FileDescriptor.class)
                .build());

        outerClassBuilder.addMethod(MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(Descriptors.FileDescriptor.class)
                .addStatement("return fileDescriptor")
                .build());
    }

    private void generateWriteToMethod(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, CodegenContext ctx, List<String> currentScope) {
        var writeToBuilder = MethodSpec.methodBuilder("writeTo")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("com.google.protobuf", "CodedOutputStream"), "output")
                .addException(IOException.class);

        for (var field : message.getFieldList()) {
            var fieldName = field.getName() + "_";
            var number = field.getNumber();
            var isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
            var methodName = ProtoUtils.getWriteMethodName(field.getType());

            if (ctx.isMapField(field)) {
                writeToBuilder.beginControlFlow("for ($T<?, ?> entry : $L.getMap().entrySet())",
                        Map.Entry.class, fieldName);

                var innerType = ctx.resolveTypeName(field.getTypeName(), currentScope);
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope);
                var valueType = ctx.getFieldType(valueField, currentScope);

                writeToBuilder.addStatement("$T entryMsg = $T.newBuilder().setKey(($T)entry.getKey()).setValue(($T)entry.getValue()).build()", innerType, innerType, keyType.box(), valueType.box());
                writeToBuilder.addStatement("output.writeMessage($L, entryMsg)", number);
                writeToBuilder.endControlFlow();
            } else if (isRepeated) {
                writeToBuilder.beginControlFlow("for (int i = 0; i < $L.size(); i++)", fieldName);
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    writeToBuilder.addStatement("$T.writeString(output, $L, $L.get(i))", messageParentClass, number, fieldName);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    writeToBuilder.addStatement("output.$N($L, $L.get(i).getNumber())", methodName, number, fieldName);
                } else {
                    writeToBuilder.addStatement("output.$N($L, $L.get(i))", methodName, number, fieldName);
                }
                writeToBuilder.endControlFlow();
            } else {
                var condition = getWriteCondition(field.getType(), fieldName);
                if (condition != null) {
                    writeToBuilder.beginControlFlow("if ($L)", condition);
                }
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    writeToBuilder.addStatement("$T.writeString(output, $L, $L)", messageParentClass, number, fieldName);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    writeToBuilder.addStatement("output.$N($L, $L.getNumber())", methodName, number, fieldName);
                } else {
                    writeToBuilder.addStatement("output.$N($L, $L)", methodName, number, fieldName);
                }
                if (condition != null) {
                    writeToBuilder.endControlFlow();
                }
            }
        }
        classBuilder.addMethod(writeToBuilder.build());
    }

    private void generateGetSerializedSize(TypeSpec.Builder classBuilder, DescriptorProtos.DescriptorProto message, CodegenContext ctx, List<String> currentScope) {
        var getSerializedSizeBuilder = MethodSpec.methodBuilder("getSerializedSize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class);

        getSerializedSizeBuilder.addStatement("int size = memoizedSize");
        getSerializedSizeBuilder.addStatement("if (size != -1) return size");
        getSerializedSizeBuilder.addStatement("size = 0");

        for (var field : message.getFieldList()) {
            var fieldName = field.getName() + "_";
            var number = field.getNumber();
            var isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
            var type = field.getType();

            if (ctx.isMapField(field)) {
                var innerType = ctx.resolveTypeName(field.getTypeName(), currentScope);
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope);
                var valueType = ctx.getFieldType(valueField, currentScope);

                getSerializedSizeBuilder.beginControlFlow("for ($T<?, ?> entry : $L.getMap().entrySet())", Map.Entry.class, fieldName);
                getSerializedSizeBuilder.addStatement("$T entryMsg = $T.newBuilder().setKey(($T)entry.getKey()).setValue(($T)entry.getValue()).build()", innerType, innerType, keyType.box(), valueType.box());
                getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.computeMessageSize($L, entryMsg)", number);
                getSerializedSizeBuilder.endControlFlow();
            } else if (isRepeated) {
                getSerializedSizeBuilder.beginControlFlow("for (int i = 0; i < $L.size(); i++)", fieldName);
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L.get(i))", messageParentClass, number, fieldName);
                } else if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i).getNumber())", computeMethod, number, fieldName);
                } else {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i))", computeMethod, number, fieldName);
                }
                getSerializedSizeBuilder.endControlFlow();
            } else {
                var condition = getWriteCondition(type, fieldName);
                if (condition != null) {
                    getSerializedSizeBuilder.beginControlFlow("if ($L)", condition);
                }
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L)", messageParentClass, number, fieldName);
                } else if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.getNumber())", computeMethod, number, fieldName);
                } else {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L)", computeMethod, number, fieldName);
                }
                if (condition != null) {
                    getSerializedSizeBuilder.endControlFlow();
                }
            }
        }

        getSerializedSizeBuilder.addStatement("memoizedSize = size");
        getSerializedSizeBuilder.addStatement("return size");
        classBuilder.addMethod(getSerializedSizeBuilder.build());
    }

    private CodeBlock getWriteCondition(DescriptorProtos.FieldDescriptorProto.Type type, String fieldName) {
        return switch (type) {
            case TYPE_STRING -> CodeBlock.of("!$T.isStringEmpty($L)", messageParentClass, fieldName);
            case TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 -> CodeBlock.of("$L != 0", fieldName);
            case TYPE_ENUM -> CodeBlock.of("$L != null", fieldName);
            case TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 -> CodeBlock.of("$L != 0L", fieldName);
            case TYPE_FLOAT -> CodeBlock.of("java.lang.Float.floatToRawIntBits($L) != 0", fieldName);
            case TYPE_DOUBLE -> CodeBlock.of("java.lang.Double.doubleToRawLongBits($L) != 0", fieldName);
            case TYPE_BOOL -> CodeBlock.of("$L", fieldName);
            case TYPE_BYTES -> CodeBlock.of("!$L.isEmpty()", fieldName);
            case TYPE_MESSAGE -> CodeBlock.of("$L != null", fieldName);
            default -> null;
        };
    }



    private void generateBuilderMethods(TypeSpec.Builder builderClassBuilder, MessageContext msgCtx, CodegenContext ctx, ClassName builderClassName) {
        for (var field : msgCtx.message().getFieldList()) {
            var fieldType = ctx.getFieldType(field, msgCtx.currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            if (fieldCtx.isMap()) {
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = ctx.fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = ctx.messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, msgCtx.currentScope());
                var valueType = ctx.getFieldType(valueField, msgCtx.currentScope());

                var mapCtx = new MapFieldContext(fieldCtx, entryDescriptor, keyType, valueType, null);

                builderClassBuilder.addMethod(CodegenMethods.Builder.containsMapKey(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapField(mapCtx, fieldType));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapCount(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapOrDefault(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapOrThrow(mapCtx));

                if (generateDeprecated) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getMapDeprecated(mapCtx, fieldType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getMutableMapDeprecated(mapCtx, fieldType));
                }

                builderClassBuilder.addMethod(CodegenMethods.Builder.putMap(mapCtx, builderClassName));
                if (valueField.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                     var valueBuilderType = ((ClassName) valueType).nestedClass("Builder");
                     builderClassBuilder.addMethod(CodegenMethods.Builder.putMapBuilderIfAbsent(mapCtx, valueBuilderType));
                }
                builderClassBuilder.addMethod(CodegenMethods.Builder.removeMap(mapCtx, builderClassName));
                builderClassBuilder.addMethod(CodegenMethods.Builder.putAllMap(mapCtx, builderClassName, fieldType));
                builderClassBuilder.addMethod(CodegenMethods.Builder.clearMap(mapCtx, builderClassName));

            } else if (fieldCtx.isRepeated()) {
                var genericType = PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    genericType = ctx.resolveTypeName(field.getTypeName(), msgCtx.currentScope());
                }
                if (genericType == null) genericType = TypeName.get(Object.class);
                genericType = genericType.box();

                var repeatedCtx = new RepeatedFieldContext(fieldCtx, genericType);

                if (repeatedCtx.isString()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedListString(repeatedCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedBytes(repeatedCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedBytes(repeatedCtx, builderClassName));
                } else {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedList(repeatedCtx, fieldType));
                }

                builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedCount(repeatedCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedElement(repeatedCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.setRepeatedElement(repeatedCtx, builderClassName));
                builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedElement(repeatedCtx, builderClassName));
                builderClassBuilder.addMethod(CodegenMethods.Builder.addAllRepeatedElements(repeatedCtx, builderClassName));
                builderClassBuilder.addMethod(CodegenMethods.Builder.clearRepeatedField(repeatedCtx, builderClassName));

                if (repeatedCtx.isMessage()) {
                    var orBuilderType = getOrBuilderType(genericType);
                    var elementBuilderType = ((ClassName) genericType).nestedClass("Builder");

                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedOrBuilderList(repeatedCtx, orBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedOrBuilder(repeatedCtx, orBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedBuilder(repeatedCtx, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedBuilder(repeatedCtx, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedBuilderAtIndex(repeatedCtx, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedBuilderList(repeatedCtx, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedElementAtIndex(repeatedCtx, builderClassName));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setRepeatedBuilder(repeatedCtx, builderClassName, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedBuilderValue(repeatedCtx, builderClassName, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedBuilderValueAtIndex(repeatedCtx, builderClassName, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.removeRepeatedElement(repeatedCtx, builderClassName));
                }

                if (repeatedCtx.isEnum()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedValueList(repeatedCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getRepeatedValue(repeatedCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setRepeatedValue(repeatedCtx, builderClassName));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addRepeatedValue(repeatedCtx, builderClassName));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.addAllRepeatedValue(repeatedCtx, builderClassName));
                }

                builderClassBuilder.addMethod(CodegenMethods.Builder.ensureIsMutable(repeatedCtx));

            } else {
                var singularCtx = new SingularFieldContext(fieldCtx, msgCtx);

                if (singularCtx.isMessage() || singularCtx.hasOneofIndex()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.hasField(singularCtx, generateHasFieldCode(msgCtx.message(), field)));
                }

                builderClassBuilder.addMethod(CodegenMethods.Builder.getField(singularCtx));

                if (singularCtx.isEnum()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldValue(singularCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setFieldValue(singularCtx, builderClassName));
                }

                if (singularCtx.isString()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldBytes(singularCtx));
                    var clearOneof = singularCtx.hasOneofIndex() ? generateClearOneofCode(msgCtx.message(), singularCtx.oneofIndex()) : CodeBlock.of("");
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setFieldBytes(singularCtx, builderClassName, clearOneof));
                }

                var clearOneof = singularCtx.hasOneofIndex() ? generateClearOneofCode(msgCtx.message(), singularCtx.oneofIndex()) : CodeBlock.of("");
                builderClassBuilder.addMethod(CodegenMethods.Builder.setField(singularCtx, builderClassName, clearOneof));

                String defaultValue;
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    defaultValue = "\"\"";
                } else {
                    defaultValue = ProtoUtils.getDefaultReturnValue(fieldType.toString()).toString();
                }
                builderClassBuilder.addMethod(CodegenMethods.Builder.clearField(singularCtx, builderClassName, defaultValue));

                if (singularCtx.isMessage()) {
                    var elementBuilderType = ((ClassName) fieldType).nestedClass("Builder");
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldOrBuilder(singularCtx, getOrBuilderType(fieldType)));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldBuilder(singularCtx, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setFieldBuilder(singularCtx, builderClassName, elementBuilderType));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.mergeField(singularCtx, builderClassName, clearOneof));
                }
            }
        }
    }

        private Optional<String> getJavaImplements(DescriptorProtos.DescriptorProto message) {
            return message.getOptions().getUninterpretedOptionList().stream()
                    .filter(o -> o.getNameList().stream().anyMatch(n -> n.getNamePart().endsWith("java_implements")))
                    .map(DescriptorProtos.UninterpretedOption::getStringValue)
                    .map(com.google.protobuf.ByteString::toStringUtf8)
                    .findFirst();
        }

        private boolean getBooleanOption(List<DescriptorProtos.UninterpretedOption> options, String name, boolean defaultValue) {
            for (var option : options) {
                String optionName = option.getNameList().stream()
                        .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                        .collect(Collectors.joining("."));
                if (optionName.equals(name)) {
                    if (option.hasIdentifierValue()) {
                        return Boolean.parseBoolean(option.getIdentifierValue());
                    }
                }
            }
            return defaultValue;
        }

    private void populateOneofInterfaces(CodegenContext ctx) {
        oneofInterfacesByType.clear();

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            populateOneofInterfaces(message, ctx, new ArrayList<>());
        }
    }

    private void populateOneofInterfaces(DescriptorProtos.DescriptorProto message, CodegenContext ctx, List<String> parentPath) {
        boolean enhancedOneof = getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, ctx.fileEnhancedOneof());

        var currentPath = new ArrayList<>(parentPath);
        currentPath.add(message.getName());

        if (enhancedOneof) {
            for (int i = 0; i < message.getOneofDeclCount(); i++) {
                var oneof = message.getOneofDecl(i);
                var pascalName = ProtoUtils.toPascalCase(oneof.getName());

                var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).collect(Collectors.toList());
                var interfaceClassName = ClassName.get(ctx.packageName(), ctx.outerClassName(), capitalizedPath.toArray(new String[0])).nestedClass(pascalName);

                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = typeName;
                        if (relativeName.startsWith(".")) {
                            var protoPackage = ctx.fileDescriptor().getPackage();
                            if (!protoPackage.isEmpty() && relativeName.startsWith("." + protoPackage + ".")) {
                                relativeName = relativeName.substring(protoPackage.length() + 2);
                            } else if (relativeName.startsWith(".")) {
                                relativeName = relativeName.substring(1);
                            }
                        }
                        if (!ctx.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }

                        TypeName typeNameRes = ctx.resolveTypeName(field.getTypeName(), currentPath);
                        if (typeNameRes instanceof ClassName cn) {
                            oneofInterfacesByType.computeIfAbsent(cn.canonicalName(), k -> new ArrayList<>()).add(interfaceClassName);
                        }
                    }
                }
            }
        }

        for (var nested : message.getNestedTypeList()) {
            populateOneofInterfaces(nested, ctx, currentPath);
        }
    }
    }
