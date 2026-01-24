package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.akre.protege.compiler.CodegenContext.JAVA_ENHANCED_ONEOF_OPTION;
import static dev.akre.protege.compiler.CodegenContext.JAVA_GENERATE_ONEOF_CASE_OPTION;
import static dev.akre.protege.compiler.CodegenUtils.getBooleanOption;

public record MessageCodegen(
        DescriptorProtos.DescriptorProto message,
        String messageName,
        Cons<String> scope,
        CodegenContext ctx,
        ProtoCodegen protoCodegen) {

    MessageCodegen(DescriptorProtos.DescriptorProto message, CodegenContext ctx, Cons<String> scope, ProtoCodegen protoCodegen) {
        this(message, ProtoUtils.toPascalCase(message.getName()), scope, ctx, protoCodegen);
    }

    public Cons<String> allNames() {
        return scope.cons(messageName());
    }

    public ClassName messageClassName() {
        return className(allNames());
    }

    static ClassName className(Cons<String> scope) {
        return switch (scope) {
            case Cons<String> c when c.tail().tail().isEmpty() -> ClassName.get(c.tail().head(), c.head());
            case Cons<String> c -> className(c.tail()).nestedClass(c.head());
        };
    }

    boolean enhancedOneof() {
        return getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, ctx.fileEnhancedOneof());
    }

    boolean generateOneofCase() {
        return getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_GENERATE_ONEOF_CASE_OPTION, ctx.fileGenerateOneofCase());
    }

    ClassName interfaceClassName() {
        return className(scope.cons(interfaceName()));
    }

    String getMessageName() {
        return ProtoUtils.toPascalCase(message.getName());
    }

    public String interfaceName() {
        return getMessageName() + "OrBuilder";
    }

    public String[] allNamesArray() {
        return allNames().stream().toArray(String[]::new);
    }

    public List<String> currentScope() {
        return scope().stream().toList();
    }

    public String canonicalMessageName() {
        return ctx().packageName() + "." + allNames().stream().collect(Collectors.joining("."));
    }

    public TypeSpec generateMessageClass() {
        var classBuilder = TypeSpec.classBuilder(messageName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(protoCodegen.messageParentClass())
                .addSuperinterface(interfaceClassName());

        classBuilder.addAnnotations(CodegenUtils.getMessageAnnotations(message.getOptions()));

        for (var nestedMessage : message.getNestedTypeList()) {
            var nestedMsgCodegen = new MessageCodegen(nestedMessage, ctx, allNames(), protoCodegen);
            classBuilder.addType(protoCodegen.generateOrBuilderType(nestedMsgCodegen));
            classBuilder.addType(nestedMsgCodegen.generateMessageClass());
        }

        for (var nestedEnum : message.getEnumTypeList()) {
            classBuilder.addType(protoCodegen.generateEnumType(new EnumContext(nestedEnum, ctx, allNamesArray())));
        }

        generateDescriptor(classBuilder);
        generateMapFieldPrototypes(classBuilder);
        generateDefaultInstance(classBuilder);
        generateInternalFieldAccessorTable(classBuilder);
        generateFieldsAndGetters(classBuilder);

        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            classBuilder.addField(oneofCaseField(this, i));
        }
        generateOneofs(classBuilder);

        classBuilder.addField(MessageMethods.memoizedSizeField());

        classBuilder.addMethod(internalGetMapFieldReflection());

        classBuilder.addMethod(MessageMethods.writeTo(this));
        generateGetSerializedSize(classBuilder);
        generateRequiredAbstractMethods(classBuilder);
        generateNewBuilderMethods(classBuilder);
        generateNoArgConstructor(classBuilder);
        generateBuilderConstructorToMessage(classBuilder);
        generateParserField(classBuilder);
        generateParseFromMethods(classBuilder);
        generateParserConstructor(classBuilder);

        var builderClass = generateBuilderClass(messageClassName(), interfaceClassName());
        classBuilder.addType(builderClass);

        return classBuilder.build();
    }

    static FieldSpec oneofCaseField(MessageCodegen context, int i) {
        return FieldSpec.builder(int.class, context.message().getOneofDecl(i).getName() + "Case_", Modifier.PRIVATE).initializer("0").build();
    }

    private void generateDescriptor(TypeSpec.Builder classBuilder) {
        classBuilder.addField(descriptorField(classBuilder));
        classBuilder.addMethod(MessageMethods.getDescriptor());
    }

    private FieldSpec descriptorField(TypeSpec.Builder classBuilder) {
        var allNames = allNamesArray();
        var cb = CodeBlock.builder();
        cb.add("descriptor = $T.getDescriptor().findMessageTypeByName($S)", ctx.outerClassName(), allNames[1]);
        for (int i = 2; i < allNames.length; i++) {
            cb.add(".findNestedTypeByName($S)", allNames[i]);
        }
        cb.add(";\n");

        classBuilder.addStaticBlock(cb.build());

        return FieldSpec.builder(Descriptors.Descriptor.class, "descriptor", Modifier.PRIVATE, Modifier.STATIC).build();
    }

    private void generateDefaultInstance(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
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

    private void generateInternalFieldAccessorTable(TypeSpec.Builder classBuilder) {
        classBuilder.addField(FieldSpec.builder(protoCodegen.getFieldAccessorTableClass(), "internal_fieldAccessorTable", Modifier.PRIVATE, Modifier.STATIC).build());

        String allNames = java.util.stream.Stream.concat(
                        message.getFieldList().stream().map(f -> ProtoUtils.toPascalCase(f.getName())),
                        message.getOneofDeclList().stream().map(o -> ProtoUtils.toPascalCase(o.getName())))
                .map(n -> "\"" + n + "\"")
                .collect(Collectors.joining(", "));

        classBuilder.addStaticBlock(CodeBlock.builder()
                .addStatement("internal_fieldAccessorTable = new $T(getDescriptor(), new String[] { $L })",
                        protoCodegen.getFieldAccessorTableClass(),
                        allNames)
                .build());

        classBuilder.addMethod(MessageMethods.internalGetFieldAccessorTable(messageClassName(), protoCodegen.getFieldAccessorTableClass()));
    }

    private void generateRequiredAbstractMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var builderClassName = messageClassName.nestedClass("Builder");
        classBuilder.addMethod(MessageMethods.toBuilder(messageClassName, builderClassName));
        classBuilder.addMethod(MessageMethods.getParserForType());
        classBuilder.addMethod(MessageMethods.newBuilderForType(builderClassName));
        classBuilder.addMethod(MessageMethods.newBuilderForTypeWithParent(protoCodegen.messageParentClass(), builderClassName));
        classBuilder.addMethod(MessageMethods.getDefaultInstanceForType(messageClassName));
        classBuilder.addMethod(MessageMethods.getUnknownFields());
        classBuilder.addMethod(MessageMethods.isInitialized());
    }

    private void generateNewBuilderMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var builderClassName = messageClassName.nestedClass("Builder");
        classBuilder.addMethod(MessageMethods.newBuilder(messageClassName, builderClassName));
        classBuilder.addMethod(MessageMethods.newBuilderWithPrototype(messageClassName, builderClassName));
    }

    private void generateParserField(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
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

        classBuilder.addMethod(MessageMethods.parser(messageClassName));
    }

    private void generateParseFromMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var byteString = ClassName.get("com.google.protobuf", "ByteString");
        var inputStream = ClassName.get("java.io", "InputStream");
        var byteBuffer = ClassName.get("java.nio", "ByteBuffer");
        var codedInputStream = ClassName.get("com.google.protobuf", "CodedInputStream");

        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteBuffer, "data", false));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteBuffer, "data", true));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteString, "data", false));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteString, "data", true));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, TypeName.get(byte[].class), "data", false));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, TypeName.get(byte[].class), "data", true));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, inputStream, "input", false));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, inputStream, "input", true));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, codedInputStream, "input", false));
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, codedInputStream, "input", true));
        classBuilder.addMethod(MessageMethods.parseDelimitedFrom(messageClassName, false));
        classBuilder.addMethod(MessageMethods.parseDelimitedFrom(messageClassName, true));
    }

    public MethodSpec internalGetMapFieldReflection() {
        return MethodSpec.methodBuilder("internalGetMapFieldReflection")
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
                .build();
    }

    void generateMapFieldPrototypes(TypeSpec.Builder classBuilder) {
        for (var field : message.getFieldList()) {
            if (ctx.isMapField(field)) {
                var entryDescriptor = ctx.getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());

                var entryClassName = ParameterizedTypeName.get(ClassName.get(com.google.protobuf.MapEntry.class), keyType.box(), valueType.box());

                classBuilder.addField(FieldSpec.builder(entryClassName, field.getName() + "_DefaultEntry", Modifier.PRIVATE, Modifier.STATIC).build());

                classBuilder.addStaticBlock(CodeBlock.builder()
                        .addStatement("$L_DefaultEntry = $T.newDefaultInstance(getDescriptor().getNestedTypes().stream().filter(t -> t.getName().equals($S)).findFirst().get(), $T.$L, $L, $T.$L, $L)",
                                field.getName(),
                                com.google.protobuf.MapEntry.class,
                                entryDescriptor.getName(),
                                com.google.protobuf.WireFormat.FieldType.class, getWireFormatType(keyField.getType()), getDefaultValue(keyField),
                                com.google.protobuf.WireFormat.FieldType.class, getWireFormatType(valueField.getType()), getDefaultValue(valueField))
                        .build());
            }
        }
    }


    private CodeBlock getDefaultValue(DescriptorProtos.FieldDescriptorProto field) {
        return switch (field.getType()) {
            case TYPE_DOUBLE -> CodeBlock.of("0.0d");
            case TYPE_FLOAT -> CodeBlock.of("0.0f");
            case TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 -> CodeBlock.of("0L");
            case TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 -> CodeBlock.of("0");
            case TYPE_BOOL -> CodeBlock.of("false");
            case TYPE_STRING -> CodeBlock.of("$S", "");
            case TYPE_BYTES -> CodeBlock.of("$T.EMPTY", com.google.protobuf.ByteString.class);
            case TYPE_ENUM -> {
                var type = ctx.getFieldType(field, currentScope());
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

    void generateNoArgConstructor(TypeSpec.Builder classBuilder) {
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
                var fieldType = ctx.getFieldType(field, currentScope());
                constructor.addStatement("$L = $T.forNumber(0)", fieldName, fieldType);
            }
        }
        classBuilder.addMethod(constructor.build());
    }

    void generateFieldsAndGetters(TypeSpec.Builder classBuilder) {
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, currentScope());
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
                var entryDescriptor = ctx().getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());
                privateFieldType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "MapField"), keyType.box(), valueType.box());
            }

            var fieldSpec = FieldSpec.builder(privateFieldType, fieldCtx.internalName(), Modifier.PRIVATE).build();
            classBuilder.addField(fieldSpec);

            if (fieldCtx.isMap()) {
                var entryDescriptor = ctx.getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());

                var mapCtx = new MapFieldContext(fieldCtx, entryDescriptor, keyType, valueType, null);

                classBuilder.addMethod(MessageMethods.containsMapKey(mapCtx));
                classBuilder.addMethod(MessageMethods.getMapField(mapCtx, fieldType));
                classBuilder.addMethod(MessageMethods.getMapCount(mapCtx));
                classBuilder.addMethod(MessageMethods.getMapOrDefault(mapCtx));
                classBuilder.addMethod(MessageMethods.getMapOrThrow(mapCtx));

                if (protoCodegen.generateDeprecated()) {
                    classBuilder.addMethod(MessageMethods.getMapDeprecated(mapCtx, fieldType));
                }
            } else if (fieldCtx.isRepeated()) {
                var genericType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    genericType = ctx.resolveTypeName(field.getTypeName(), currentScope());
                }
                if (genericType == null) genericType = TypeName.get(Object.class);
                genericType = genericType.box();

                var repeatedCtx = new RepeatedFieldContext(fieldCtx, genericType);

                if (repeatedCtx.isString()) {
                    classBuilder.addMethod(MessageMethods.getRepeatedListString(repeatedCtx));
                    classBuilder.addMethod(MessageMethods.getRepeatedBytes(repeatedCtx));
                } else {
                    classBuilder.addMethod(MessageMethods.getRepeatedList(repeatedCtx, fieldType));
                }

                classBuilder.addMethod(MessageMethods.getRepeatedCount(repeatedCtx));
                classBuilder.addMethod(MessageMethods.getRepeatedElement(repeatedCtx));

                if (repeatedCtx.isMessage()) {
                    var orBuilderType = getOrBuilderType(genericType);
                    classBuilder.addMethod(MessageMethods.getRepeatedOrBuilderList(repeatedCtx, orBuilderType));
                    classBuilder.addMethod(MessageMethods.getRepeatedOrBuilder(repeatedCtx, orBuilderType));
                }

                if (repeatedCtx.isEnum()) {
                    classBuilder.addMethod(MessageMethods.getRepeatedValueList(repeatedCtx));
                    classBuilder.addMethod(MessageMethods.getRepeatedValue(repeatedCtx));
                }
            } else {
                var singularCtx = new SingularFieldContext(fieldCtx, this);

                if (singularCtx.isMessage() || singularCtx.hasOneofIndex()) {
                    classBuilder.addMethod(MessageMethods.hasField(singularCtx, generateHasFieldCode(message, field)));
                }

                classBuilder.addMethod(MessageMethods.getField(singularCtx));

                if (singularCtx.isEnum()) {
                    classBuilder.addMethod(MessageMethods.getFieldValue(singularCtx));
                }

                if (singularCtx.isMessage()) {
                    classBuilder.addMethod(MessageMethods.getFieldOrBuilder(singularCtx, getOrBuilderType(fieldType)));
                }

                if (singularCtx.isString()) {
                    classBuilder.addMethod(MessageMethods.getFieldBytes(singularCtx));
                }
            }
        }
    }

    private CodeBlock generateHasFieldCode(DescriptorProtos.DescriptorProto message, DescriptorProtos.FieldDescriptorProto field) {
        if (field.hasOneofIndex()) {
            return CodeBlock.of("return $LCase_ == $L;\n", message.getOneofDecl(field.getOneofIndex()).getName(), field.getNumber());
        }
        return CodeBlock.of("return $L_ != null;\n", field.getName());
    }

    void generateGetSerializedSize(TypeSpec.Builder classBuilder) {
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
                var innerType = ctx.resolveTypeName(field.getTypeName(), currentScope());
                var entryDescriptor = ctx.getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());

                getSerializedSizeBuilder.beginControlFlow("for ($T<?, ?> entry : $L.getMap().entrySet())", Map.Entry.class, fieldName);
                getSerializedSizeBuilder.addStatement("$T entryMsg = $T.newBuilder().setKey(($T)entry.getKey()).setValue(($T)entry.getValue()).build()", innerType, innerType, keyType.box(), valueType.box());
                getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.computeMessageSize($L, entryMsg)", number);
                getSerializedSizeBuilder.endControlFlow();
            } else if (isRepeated) {
                getSerializedSizeBuilder.beginControlFlow("for (int i = 0; i < $L.size(); i++)", fieldName);
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L.get(i))", protoCodegen.messageParentClass(), number, fieldName);
                } else if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i).getNumber())", computeMethod, number, fieldName);
                } else {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i))", computeMethod, number, fieldName);
                }
                getSerializedSizeBuilder.endControlFlow();
            } else {
                var condition = CodegenUtils.getWriteCondition(type, fieldName, this);
                if (condition != null) {
                    getSerializedSizeBuilder.beginControlFlow("if ($L)", condition);
                }
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L)", protoCodegen.messageParentClass(), number, fieldName);
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



    void generateBuilderConstructorToMessage(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
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

    void generateParserConstructor(TypeSpec.Builder classBuilder) {
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
                var innerType = ctx.resolveTypeName(field.getTypeName(), currentScope());
                constructor.addStatement("  $T entry = input.readMessage($T.PARSER, extensionRegistry)", innerType, innerType);
                constructor.addStatement("  $L.getMutableMap().put(entry.getKey(), entry.getValue())", fieldName);
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                var innerType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    innerType = ctx.resolveTypeName(field.getTypeName(), currentScope());
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
                    var fieldType = ctx.getFieldType(field, currentScope());
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
                    var fieldType = ctx.getFieldType(field, currentScope());
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

    private int getWireType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_INT32, TYPE_INT64, TYPE_UINT32, TYPE_UINT64, TYPE_SINT32, TYPE_SINT64, TYPE_BOOL, TYPE_ENUM -> 0;
            case TYPE_DOUBLE, TYPE_FIXED64, TYPE_SFIXED64 -> 1;
            case TYPE_STRING, TYPE_BYTES, TYPE_MESSAGE -> 2;
            case TYPE_FLOAT, TYPE_FIXED32, TYPE_SFIXED32 -> 5;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
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
                    cb.addStatement("$L = $L", fieldName, ProtoUtils.getDefaultReturnValue(ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType()).toString()));
                }
            }
        }
        return cb.build();
    }

    public ClassName builderClassName() {
        return messageClassName().nestedClass("Builder");
    }

    TypeSpec generateBuilderClass(ClassName messageClassName, ClassName interfaceClassName) {
        var builderClassBuilder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(protoCodegen.messageParentClass().nestedClass("Builder"), builderClassName()))
                .addSuperinterface(interfaceClassName);

        // Add fields to builder
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            var privateFieldType = fieldType;
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                    privateFieldType = ClassName.get("com.google.protobuf", "LazyStringList");
                } else {
                    privateFieldType = TypeName.get(Object.class);
                }
            } else if (fieldCtx.isMap()) {
                var entryDescriptor = ctx.getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());
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
            builderClassBuilder.addField(oneofCaseField(this, i));
        }

        // Constructors
        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super()")
                .build());

        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(protoCodegen.messageParentClass().nestedClass("BuilderParent"), "parent")
                .addStatement("super(parent)")
                .build());

        // Methods for each field
        generateBuilderMethods(builderClassBuilder, builderClassName());

        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            var oneof = message.getOneofDecl(i);
            var oneofCtx = OneofContext.create(oneof, i, messageClassName);

            if (enhancedOneof()) {
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

            if (generateOneofCase()) {
                builderClassBuilder.addMethod(CodegenMethods.Builder.getOneofCase(oneofCtx, generateGetCaseCode(message, i, oneof.getName(), oneofCtx.enumName())));
            }

            builderClassBuilder.addMethod(CodegenMethods.Builder.clearOneof(oneofCtx, builderClassName(), generateClearOneofCode(message, i)));
        }

        builderClassBuilder.addMethod(CodegenMethods.Builder.build(messageClassName));
        builderClassBuilder.addMethod(CodegenMethods.Builder.buildPartial(messageClassName));

        // clear()
        var clearMethod = MethodSpec.methodBuilder("clear")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName())
                .addStatement("super.clear()");
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, currentScope());
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
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromCodedInput(builderClassName()));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetFieldAccessorTable(messageClassName, protoCodegen.getFieldAccessorTableClass()));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMapFieldReflection(this, ctx));
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMutableMapFieldReflection(this, ctx));
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromMessage(messageClassName, builderClassName()));

        builderClassBuilder.addMethod(MessageMethods.mergeFromOther(this));

        return builderClassBuilder.build();
    }

    private void generateBuilderMethods(TypeSpec.Builder builderClassBuilder, ClassName builderClassName) {
        for (var field : message.getFieldList()) {
            var fieldType = ctx.getFieldType(field, currentScope());
            var fieldCtx = FieldContext.create(field, fieldType, ctx.isMapField(field));

            if (fieldCtx.isMap()) {
                var entryDescriptor = ctx.getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = ctx.getFieldType(keyField, currentScope());
                var valueType = ctx.getFieldType(valueField, currentScope());

                var mapCtx = new MapFieldContext(fieldCtx, entryDescriptor, keyType, valueType, null);

                builderClassBuilder.addMethod(CodegenMethods.Builder.containsMapKey(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapField(mapCtx, fieldType));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapCount(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapOrDefault(mapCtx));
                builderClassBuilder.addMethod(CodegenMethods.Builder.getMapOrThrow(mapCtx));

                if (protoCodegen.generateDeprecated()) {
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
                var genericType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    genericType = ctx.resolveTypeName(field.getTypeName(), currentScope());
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
                var singularCtx = new SingularFieldContext(fieldCtx, this);

                if (singularCtx.isMessage() || singularCtx.hasOneofIndex()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.hasField(singularCtx, generateHasFieldCode(message, field)));
                }

                builderClassBuilder.addMethod(CodegenMethods.Builder.getField(singularCtx));

                if (singularCtx.isEnum()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldValue(singularCtx));
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setFieldValue(singularCtx, builderClassName));
                }

                if (singularCtx.isString()) {
                    builderClassBuilder.addMethod(CodegenMethods.Builder.getFieldBytes(singularCtx));
                    var clearOneof = singularCtx.hasOneofIndex() ? generateClearOneofCode(message, singularCtx.oneofIndex()) : CodeBlock.of("");
                    builderClassBuilder.addMethod(CodegenMethods.Builder.setFieldBytes(singularCtx, builderClassName, clearOneof));
                }

                var clearOneof = singularCtx.hasOneofIndex() ? generateClearOneofCode(message, singularCtx.oneofIndex()) : CodeBlock.of("");
                builderClassBuilder.addMethod(CodegenMethods.Builder.setField(singularCtx, builderClassName, clearOneof));

                String defaultValue;
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    defaultValue = "\"\"";
                } else {
                    defaultValue = ProtoUtils.getDefaultReturnValue(fieldType.toString());
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

    void generateOneofs(TypeSpec.Builder classBuilder) {
        for (int i = 0; i < message.getOneofDeclCount(); i++) {
            var oneof = message.getOneofDecl(i);
            var oneofCtx = OneofContext.create(oneof, i, messageClassName());

            if (enhancedOneof()) {
                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + message.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = ctx.relativeToProtoPackage(field.getTypeName());
                        if (!ctx.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + message.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }
                    }
                }

                var interfaceBuilder = TypeSpec.interfaceBuilder(oneofCtx.pascalName())
                        .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        TypeName typeName = ctx.resolveTypeName(field.getTypeName(), currentScope());
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

            if (generateOneofCase()) {
                var enumBuilder = TypeSpec.enumBuilder(oneofCtx.enumName())
                        .addModifiers(Modifier.PUBLIC)
                        .addSuperinterface(com.google.protobuf.Internal.EnumLite.class);

                for (var field : message.getFieldList()) {
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
                        .returns(messageClassName().nestedClass(oneofCtx.enumName()));

                forNumberBuilder.beginControlFlow("switch (value)");
                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        forNumberBuilder.addStatement("case $L: return $L", field.getNumber(), field.getName().toUpperCase());
                    }
                }
                forNumberBuilder.addStatement("case 0: return $L", oneofCtx.oneofName().toUpperCase() + "_NOT_SET");
                forNumberBuilder.addStatement("default: return null");
                forNumberBuilder.endControlFlow();
                enumBuilder.addMethod(forNumberBuilder.build());

                classBuilder.addType(enumBuilder.build());

                classBuilder.addMethod(MessageMethods.getOneofCase(oneofCtx, generateGetCaseCode(message, i, oneofCtx.oneofName(), oneofCtx.enumName())));
            }

            if (enhancedOneof()) {
                var switchCode = CodeBlock.builder();
                switchCode.beginControlFlow("switch ($LCase_)", oneofCtx.oneofName());
                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        switchCode.addStatement("case $L: return get$L()", field.getNumber(), ProtoUtils.toPascalCase(field.getName()));
                    }
                }
                switchCode.addStatement("default: return null");
                switchCode.endControlFlow();

                classBuilder.addMethod(MessageMethods.getOneof(oneofCtx, this, switchCode.build()));
            }
        }
    }

    private CodeBlock generateGetCaseCode(DescriptorProtos.DescriptorProto message, int oneofIndex, String oneofName, String enumName) {
        return CodeBlock.of("return $L.forNumber($LCase_);\n", enumName, oneofName);
    }

    public static TypeName getOrBuilderType(TypeName typeName) {

        if (typeName instanceof ClassName) {
            ClassName cn = (ClassName) typeName;
            List<String> simpleNames = cn.simpleNames();
            String last = simpleNames.getLast();
            if (simpleNames.size() == 1) {
                return ClassName.get(cn.packageName(), last + "OrBuilder");
            } else {
                return ClassName.get(cn.packageName(), simpleNames.getFirst(),
                        java.util.stream.Stream.concat(
                                simpleNames.subList(1, simpleNames.size() - 1).stream(),
                                java.util.stream.Stream.of(last + "OrBuilder")
                        ).toArray(String[]::new));
            }
        }
        return typeName;
    }
}
