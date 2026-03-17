package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.WireFormat.FieldType;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Java code for a Protobuf message handling:
 * <ul>
 *   <li>Message class definition (POJO/GeneratedMessageV3)</li>
 *   <li>Builder class generation</li>
 *   <li>Interface definition (*OrBuilder)</li>
 *   <li>Nested types (messages and enums)</li>
 *   <li>Serialization/Deserialization logic</li>
 * </ul>
 *
 * @param descriptor The message descriptor.
 * @param scope The current scope.
 * @param protoCodegen The ProtoCodegen instance.
 * @param config The configuration metadata.
 */
public record MessageCodegen(
        DescriptorProtos.DescriptorProto descriptor,
        Cons<String> scope,
        ProtoCodegen protoCodegen,
        CodegenMetadata config
) implements CodegenConfig.MessageConfig {

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

    ClassName interfaceClassName() {
        return className(scope.cons(interfaceName()));
    }

    String messageName() {
        return descriptor.getName();
    }

    public String interfaceName() {
        return messageName() + "OrBuilder";
    }

    public String[] allNamesArray() {
        return allNames().stream().toArray(String[]::new);
    }

    public List<String> currentScope() {
        return scope().stream().toList();
    }

    public String canonicalMessageName() {
        return config.packageName() + "." + String.join(".", allNames());
    }

    public TypeSpec generateMessageClass() {
        var classBuilder = TypeSpec.classBuilder(messageName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(getMessageSuperclass())
                .addSuperinterface(interfaceClassName());

        classBuilder.addAnnotations(CodegenUtils.getClassAnnotations(descriptor.getOptions()));
        for (String annotation : config.getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor)) {
            classBuilder.addAnnotation(CodegenUtils.parseAnnotation(annotation));
        }


        for (var nestedMessage : descriptor.getNestedTypeList()) {
            var nestedMsgCodegen = new MessageCodegen(nestedMessage, allNames(), protoCodegen, config);
            // public interface <MessageName>OrBuilder extends MessageOrBuilder
            classBuilder.addType(nestedMsgCodegen.generateMessageInterface());
            // public static final class <MessageName> extends GeneratedMessageV3 implements <MessageName>OrBuilder
            classBuilder.addType(nestedMsgCodegen.generateMessageClass());
        }

        for (var nestedEnum : descriptor.getEnumTypeList()) {
            var enumCodegen = new EnumCodegen(nestedEnum, allNames(), protoCodegen, config);
            // public enum <EnumName> implements ProtocolMessageEnum
            classBuilder.addType(enumCodegen.generate());
        }

        generateDescriptor(classBuilder);
        generateMapFieldPrototypes(classBuilder);
        generateDefaultInstance(classBuilder);
        generateInternalFieldAccessorTable(classBuilder);
        generateFieldsAndGetters(classBuilder);

        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            // private int <oneofName>Case_
            classBuilder.addField(oneofCaseField(this, i));
        }
        generateOneofs(classBuilder);

        // private int memoizedSize
        classBuilder.addField(MessageMethods.memoizedSizeField());

        // protected MapFieldReflectionAccessor internalGetMapFieldReflection(int fieldNumber)
        classBuilder.addMethod(internalGetMapFieldReflection());

        // public void writeTo(CodedOutputStream output)
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
        // public static final class Builder extends GeneratedMessageV3.Builder<Builder> implements <MessageName>OrBuilder
        classBuilder.addType(builderClass);

        return classBuilder.build();
    }

    static FieldSpec oneofCaseField(MessageCodegen context, int i) {
        return FieldSpec.builder(int.class, context.descriptor().getOneofDecl(i).getName() + "Case_", Modifier.PRIVATE).initializer("0").build();
    }

    private void generateDescriptor(TypeSpec.Builder classBuilder) {
        // private static Descriptors.Descriptor descriptor
        classBuilder.addField(descriptorField(classBuilder));
        // public static final Descriptors.Descriptor getDescriptor()
        classBuilder.addMethod(MessageMethods.getDescriptor());
    }

        private FieldSpec descriptorField(TypeSpec.Builder classBuilder) {
            var allNames = allNamesArray();
            var cb = CodeBlock.builder();
            cb.add("descriptor = $T.getDescriptor().findMessageTypeByName($S)", ClassName.get(getJavaPackage(), getOuterName()), allNames[1]);
            for (int i = 2; i < allNames.length; i++) {
                cb.add(".findNestedTypeByName($S)", allNames[i]);
            }
            cb.add(";\n");
    
            classBuilder.addStaticBlock(cb.build());
    
            return FieldSpec.builder(Descriptors.Descriptor.class, "descriptor", Modifier.PRIVATE, Modifier.STATIC).build();
        }
    
    private void generateDefaultInstance(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        // private static final <MessageName> DEFAULT_INSTANCE
        var defaultInstanceField = FieldSpec.builder(messageClassName, "DEFAULT_INSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .build();
        classBuilder.addField(defaultInstanceField);
        classBuilder.addStaticBlock(CodeBlock.of("DEFAULT_INSTANCE = new $T();\n", messageClassName));

        // public static <MessageName> getDefaultInstance()
        classBuilder.addMethod(MethodSpec.methodBuilder("getDefaultInstance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(messageClassName)
                .addStatement("return DEFAULT_INSTANCE")
                .build());
    }

    private void generateInternalFieldAccessorTable(TypeSpec.Builder classBuilder) {
        // private static GeneratedMessageV3.FieldAccessorTable internal_fieldAccessorTable
        classBuilder.addField(FieldSpec.builder(getFieldAccessorTableClass(), "internal_fieldAccessorTable", Modifier.PRIVATE, Modifier.STATIC).build());

        String allNames = java.util.stream.Stream.concat(
                        descriptor.getFieldList().stream().map(f -> ProtoUtils.toPascalCase(f.getName())),
                        descriptor.getOneofDeclList().stream().map(o -> ProtoUtils.toPascalCase(o.getName())))
                .map(n -> "\"" + n + "\"")
                .collect(Collectors.joining(", "));

        classBuilder.addStaticBlock(CodeBlock.builder()
                .addStatement("internal_fieldAccessorTable = new $T(getDescriptor(), new String[] { $L })",
                        getFieldAccessorTableClass(),
                        allNames)
                .build());

        // protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable()
        classBuilder.addMethod(MessageMethods.internalGetFieldAccessorTable(messageClassName(), getFieldAccessorTableClass()));
    }

    private void generateRequiredAbstractMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var builderClassName = messageClassName.nestedClass("Builder");
        // public Builder toBuilder()
        classBuilder.addMethod(MessageMethods.toBuilder(messageClassName, builderClassName));
        // public Parser<<MessageName>> getParserForType()
        classBuilder.addMethod(MessageMethods.getParserForType());
        // public Builder newBuilderForType()
        classBuilder.addMethod(MessageMethods.newBuilderForType(builderClassName));
        // protected Builder newBuilderForType(GeneratedMessageV3.BuilderParent parent)
        classBuilder.addMethod(MessageMethods.newBuilderForTypeWithParent(this));
        // public <MessageName> getDefaultInstanceForType()
        classBuilder.addMethod(MessageMethods.getDefaultInstanceForType(messageClassName));
        // public final UnknownFieldSet getUnknownFields()
        classBuilder.addMethod(MessageMethods.getUnknownFields());
        // public final boolean isInitialized()
        classBuilder.addMethod(MessageMethods.isInitialized());
    }

    private void generateNewBuilderMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var builderClassName = messageClassName.nestedClass("Builder");
        // public static Builder newBuilder()
        classBuilder.addMethod(MessageMethods.newBuilder(messageClassName, builderClassName));
        // public static Builder newBuilder(<MessageName> prototype)
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

        // public static final Parser<<MessageName>> PARSER
        classBuilder.addField(FieldSpec.builder(parserType, "PARSER", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", parserInitializer)
                .build());

        // public Parser<<MessageName>> parser()
        classBuilder.addMethod(MessageMethods.parser(messageClassName));
    }

    private void generateParseFromMethods(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var byteString = ClassName.get("com.google.protobuf", "ByteString");
        var inputStream = ClassName.get("java.io", "InputStream");
        var byteBuffer = ClassName.get("java.nio", "ByteBuffer");
        var codedInputStream = ClassName.get("com.google.protobuf", "CodedInputStream");

        // public static <MessageName> parseFrom(ByteBuffer data)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteBuffer, "data", false));
        // public static <MessageName> parseFrom(ByteBuffer data, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteBuffer, "data", true));
        // public static <MessageName> parseFrom(ByteString data)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteString, "data", false));
        // public static <MessageName> parseFrom(ByteString data, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, byteString, "data", true));
        // public static <MessageName> parseFrom(byte[] data)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, TypeName.get(byte[].class), "data", false));
        // public static <MessageName> parseFrom(byte[] data, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, TypeName.get(byte[].class), "data", true));
        // public static <MessageName> parseFrom(InputStream input)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, inputStream, "input", false));
        // public static <MessageName> parseFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, inputStream, "input", true));
        // public static <MessageName> parseFrom(CodedInputStream input)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, codedInputStream, "input", false));
        // public static <MessageName> parseFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseFrom(messageClassName, codedInputStream, "input", true));
        // public static <MessageName> parseDelimitedFrom(InputStream input)
        classBuilder.addMethod(MessageMethods.parseDelimitedFrom(messageClassName, false));
        // public static <MessageName> parseDelimitedFrom(InputStream input, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(MessageMethods.parseDelimitedFrom(messageClassName, true));
    }

    public MethodSpec internalGetMapFieldReflection() {
        return MethodSpec.methodBuilder("internalGetMapFieldReflection")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .returns(com.google.protobuf.MapFieldReflectionAccessor.class)
                .addParameter(int.class, "fieldNumber")
                .beginControlFlow("switch (fieldNumber)")
                .addCode(descriptor.getFieldList().stream()
                        .filter(this::isMapField)
                        .map(f -> "case " + f.getNumber() + ": return " + f.getName() + "_;\n")
                        .collect(Collectors.joining()))
                .addStatement("default: throw new $T($S + fieldNumber)", RuntimeException.class, "Invalid map field number: ")
                .endControlFlow()
                .build();
    }

    void generateMapFieldPrototypes(TypeSpec.Builder classBuilder) {
        for (var field : descriptor.getFieldList()) {
            if (isMapField(field)) {
                var entryDescriptor = getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);

                var keyType = getFieldType(keyField, currentScope());
                var valueType = getFieldType(valueField, currentScope());

                var entryClassName = ParameterizedTypeName.get(ClassName.get(com.google.protobuf.MapEntry.class), keyType.box(), valueType.box());

                // private static MapEntry<<KeyType>, <ValueType>> <fieldName>_DefaultEntry
                classBuilder.addField(FieldSpec.builder(entryClassName, field.getName() + "_DefaultEntry", Modifier.PRIVATE, Modifier.STATIC).build());

                classBuilder.addStaticBlock(CodeBlock.builder()
                        .addStatement("$L_DefaultEntry = $T.newDefaultInstance(getDescriptor().getNestedTypes().stream().filter(t -> t.getName().equals($S)).findFirst().get(), $T.$L, $L, $T.$L, $L)",
                                field.getName(),
                                com.google.protobuf.MapEntry.class,
                                entryDescriptor.getName(),
                                FieldType.class, ProtoUtils.getWireFormatType(keyField.getType()), getDefaultValue(keyField),
                                FieldType.class, ProtoUtils.getWireFormatType(valueField.getType()), getDefaultValue(valueField))
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
                var type = getFieldType(field, currentScope());
                yield CodeBlock.of("$T.forNumber(0)", type);
            }
            default -> CodeBlock.of("null");
        };
    }

    void generateNoArgConstructor(TypeSpec.Builder classBuilder) {
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE);

        for (var field : descriptor.getFieldList()) {
            var fieldName = field.getName() + "_";
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING
                    && field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                constructor.addStatement("$L = \"\"", fieldName);
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES
                    && field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                constructor.addStatement("$L = $T.EMPTY", fieldName, com.google.protobuf.ByteString.class);
            } else if (isMapField(field)) {
                constructor.addStatement("$L = $T.emptyMapField($L_DefaultEntry)", fieldName, com.google.protobuf.MapField.class, field.getName());
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    constructor.addStatement("$L = $T.EMPTY", fieldName, com.google.protobuf.LazyStringArrayList.class);
                } else {
                    constructor.addStatement("$L = $T.emptyList()", fieldName, java.util.Collections.class);
                }
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                var fieldType = getFieldType(field, currentScope());
                constructor.addStatement("$L = $T.forNumber(0)", fieldName, fieldType);
            }
        }
        // private <MessageName>()
        classBuilder.addMethod(constructor.build());
    }

    void generateFieldsAndGetters(TypeSpec.Builder classBuilder) {
        for (var field : descriptor.getFieldList()) {
            var fieldCodegen = FieldCodegen.create(field, this);

            // Generate private field
            var privateFieldType = fieldCodegen.fieldType();
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                if (fieldCodegen.isRepeated() && !fieldCodegen.isMap()) {
                    privateFieldType = ClassName.get("com.google.protobuf", "LazyStringList");
                } else {
                    privateFieldType = TypeName.get(Object.class);
                }
            } else if (fieldCodegen.isMap()) {
                var keyType = fieldCodegen.keyType();
                var valueType = fieldCodegen.valueType();
                privateFieldType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "MapField"), keyType.box(), valueType.box());
            }

            var fieldSpec = FieldSpec.builder(privateFieldType, fieldCodegen.internalName(), Modifier.PRIVATE)
                    .build();
            // private <FieldType> <fieldName>_
            classBuilder.addField(fieldSpec);

            // public <FieldType> get<FieldName>()
            // public boolean has<FieldName>()
            // ...
            fieldCodegen.getterMethods().forEach(classBuilder::addMethod);
        }
    }

    void generateGetSerializedSize(TypeSpec.Builder classBuilder) {
        var getSerializedSizeBuilder = MethodSpec.methodBuilder("getSerializedSize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("int size = memoizedSize")
                .beginControlFlow("if (size != -1)")
                .addStatement("return size")
                .endControlFlow();
        getSerializedSizeBuilder.addStatement("size = 0");

        for (var field : descriptor.getFieldList()) {
            var fieldName = field.getName() + "_";
            var number = field.getNumber();
            var isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
            var type = field.getType();

            if (isMapField(field)) {
                getSerializedSizeBuilder.beginControlFlow("");
                var innerType = resolveTypeName(field.getTypeName(), currentScope());
                var entryDescriptor = getEntryDescriptor(field);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = getFieldType(keyField, currentScope());
                var valueType = getFieldType(valueField, currentScope());

                getSerializedSizeBuilder.addStatement("$T<$T, $T> sortedMap = new $T<>($L.getMap())",
                        Map.class, keyType.box(), valueType.box(), java.util.TreeMap.class, fieldName);
                getSerializedSizeBuilder.beginControlFlow("for ($T<$T, $T> entry : sortedMap.entrySet())", Map.Entry.class, keyType.box(), valueType.box());
                getSerializedSizeBuilder.addStatement("$T entryMsg = $T.newBuilder().setKey(($T)entry.getKey()).setValue(($T)entry.getValue()).build()", innerType, innerType, keyType.box(), valueType.box());
                getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.computeMessageSize($L, entryMsg)", number);
                getSerializedSizeBuilder.endControlFlow();
                getSerializedSizeBuilder.endControlFlow();
            } else if (isRepeated) {
                getSerializedSizeBuilder.beginControlFlow("for (int i = 0; i < $L.size(); i++)", fieldName);
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L.get(i))", getMessageSuperclass(), number, fieldName);
                } else if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i).getNumber())", computeMethod, number, fieldName);
                } else {
                    var computeMethod = ProtoUtils.getComputeMethodName(type);
                    getSerializedSizeBuilder.addStatement("size += com.google.protobuf.CodedOutputStream.$L($L, $L.get(i))", computeMethod, number, fieldName);
                }
                getSerializedSizeBuilder.endControlFlow();
            } else {
                CodeBlock condition;
                if (field.hasOneofIndex()) {
                    var oneofName = descriptor.getOneofDecl(field.getOneofIndex()).getName();
                    condition = CodeBlock.of("$LCase_ == $L", oneofName, number);
                } else {
                    condition = CodegenUtils.getWriteCondition(type, fieldName, this);
                }
                if (condition != null) {
                    getSerializedSizeBuilder.beginControlFlow("if ($L)", condition);
                }
                if (type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    getSerializedSizeBuilder.addStatement("size += $T.computeStringSize($L, $L)", getMessageSuperclass(), number, fieldName);
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
        // public int getSerializedSize()
        classBuilder.addMethod(getSerializedSizeBuilder.build());
    }



    void generateBuilderConstructorToMessage(TypeSpec.Builder classBuilder) {
        var messageClassName = messageClassName();
        var builderClassName = messageClassName.nestedClass("Builder");
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(builderClassName, "builder")
                .addStatement("super(builder)");

        for (var field : descriptor.getFieldList()) {
            var fieldName = field.getName() + "_";
            if (isMapField(field)) {
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
        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            var oneofName = descriptor.getOneofDecl(i).getName() + "Case_";
            constructor.addStatement("this.$L = builder.$L", oneofName, oneofName);
        }
        // private <MessageName>(Builder builder)
        classBuilder.addMethod(constructor.build());
    }

    void generateParserConstructor(TypeSpec.Builder classBuilder) {
        var constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(ClassName.get("com.google.protobuf", "CodedInputStream"), "input")
                .addParameter(ClassName.get("com.google.protobuf", "ExtensionRegistryLite"), "extensionRegistry")
                .addException(IOException.class);

        constructor.addStatement("this()");
        for (var field : descriptor.getFieldList()) {
            if (isMapField(field)) {
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

        for (var field : descriptor.getFieldList()) {
            int wireType = ProtoUtils.getWireType(field.getType());
            int tag = (field.getNumber() << 3) | wireType;
            constructor.addCode("case $L: {\n", tag);

            var fieldName = field.getName() + "_";
            var readMethod = ProtoUtils.getReadMethodName(field.getType());

            if (isMapField(field)) {
                var innerType = resolveTypeName(field.getTypeName(), currentScope());
                constructor.addStatement("  $T entry = input.readMessage($T.PARSER, extensionRegistry)", innerType, innerType);
                constructor.addStatement("  $L.getMutableMap().put(entry.getKey(), entry.getValue())", fieldName);
            } else if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                var innerType = CodegenUtils.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
                if (field.hasTypeName()) {
                    innerType = resolveTypeName(field.getTypeName(), currentScope());
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
                    constructor.addCode(CodegenUtils.generateClearOneofCode(this, field.getOneofIndex()));
                    constructor.addStatement("$LCase_ = $L", descriptor.getOneofDecl(field.getOneofIndex()).getName(), field.getNumber());
                }
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                    var fieldType = getFieldType(field, currentScope());
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
                    var fieldType = getFieldType(field, currentScope());
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
        for (var field : descriptor.getFieldList()) {
            if (isMapField(field)) {
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

        // private <MessageName>(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
        classBuilder.addMethod(constructor.build());
    }



    public ClassName builderClassName() {
        return messageClassName().nestedClass("Builder");
    }

    TypeSpec generateBuilderClass(ClassName messageClassName, ClassName interfaceClassName) {
        var builderClassBuilder = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .superclass(ParameterizedTypeName.get(getMessageSuperclass().nestedClass("Builder"), builderClassName()))
                .addSuperinterface(interfaceClassName);

        for (String annotation : config.getList(CodegenMetadata.BUILDER_ANNOTATIONS, descriptor)) {
            builderClassBuilder.addAnnotation(CodegenUtils.parseAnnotation(annotation));
        }

        // Add fields to builder
        for (var field : descriptor.getFieldList()) {
            var fieldCodegen = FieldCodegen.create(field, this);
            var fieldType = fieldCodegen.fieldType();

            var privateFieldType = fieldType;
            if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                if (fieldCodegen.isRepeated() && !fieldCodegen.isMap()) {
                    privateFieldType = ClassName.get("com.google.protobuf", "LazyStringList");
                } else {
                    privateFieldType = TypeName.get(Object.class);
                }
            } else if (fieldCodegen.isMap()) {
                var keyType = fieldCodegen.keyType();
                var valueType = fieldCodegen.valueType();
                privateFieldType = ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "MapField"), keyType.box(), valueType.box());
            }

            var fieldBuilder = FieldSpec.builder(privateFieldType, fieldCodegen.internalName(), Modifier.PRIVATE);
            if (fieldCodegen.isMap()) {
                fieldBuilder.initializer("$T.newMapField($L_DefaultEntry)", com.google.protobuf.MapField.class, field.getName());
            } else if (fieldCodegen.isRepeated()) {
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
            // private <FieldType> <fieldName>_
            builderClassBuilder.addField(fieldBuilder.build());
        }

        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            // private int <oneofName>Case_
            builderClassBuilder.addField(oneofCaseField(this, i));
        }

        // Constructors
        // private Builder()
        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addStatement("super()")
                .build());

        // private Builder(GeneratedMessageV3.BuilderParent parent)
        builderClassBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(getMessageSuperclass().nestedClass("BuilderParent"), "parent")
                .addStatement("super(parent)")
                .build());

        // Methods for each field
        generateBuilderMethods(builderClassBuilder, builderClassName());

        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            var oneof = descriptor.getOneofDecl(i);
            var oneofCtx = OneofCodegen.create(oneof, i, this);

            if (isEnhancedOneof(descriptor)) {
                var switchCode = CodeBlock.builder();
                switchCode.beginControlFlow("switch ($LCase_)", oneofCtx.oneofName());
                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        switchCode.addStatement("case $L: return get$L()", field.getNumber(), ProtoUtils.toPascalCase(field.getName()));
                    }
                }
                switchCode.addStatement("default: return null");
                switchCode.endControlFlow();
                // public <OneofName> get<OneofName>()
                builderClassBuilder.addMethod(OneofMessages.getOneof(oneofCtx, switchCode.build()));
            }

            if (isGenerateOneofCase(descriptor)) {
                // public <OneofName>Case get<OneofName>Case()
                builderClassBuilder.addMethod(OneofMessages.getOneofCase(oneofCtx, generateGetCaseCode(descriptor, i, oneof.getName(), oneofCtx.enumName())));
            }

            // public Builder clear<OneofName>()
            builderClassBuilder.addMethod(OneofMessages.clearOneof(oneofCtx, builderClassName(), CodegenUtils.generateClearOneofCode(this, i)));
        }

        // public <MessageName> build()
        builderClassBuilder.addMethod(CodegenMethods.Builder.build(messageClassName));
        // public <MessageName> buildPartial()
        builderClassBuilder.addMethod(CodegenMethods.Builder.buildPartial(messageClassName));

        // clear()
        var clearMethod = MethodSpec.methodBuilder("clear")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName())
                .addStatement("super.clear()");
        for (var field : descriptor.getFieldList()) {
            var fieldCodegen = FieldCodegen.create(field, this);
            var fieldType = fieldCodegen.fieldType();

            if (fieldCodegen.isMap()) {
                clearMethod.addStatement("$L.getMutableMap().clear()", fieldCodegen.internalName());
            } else if (fieldCodegen.isRepeated()) {
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    clearMethod.addStatement("$L = new $T()", fieldCodegen.internalName(), com.google.protobuf.LazyStringArrayList.class);
                } else {
                    clearMethod.addStatement("$L = new $T<>()", fieldCodegen.internalName(), java.util.ArrayList.class);
                }
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                clearMethod.addStatement("$L = $S", fieldCodegen.internalName(), "");
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                clearMethod.addStatement("$L = null", fieldCodegen.internalName());
            } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                clearMethod.addStatement("$L = $T.forNumber(0)", fieldCodegen.internalName(), fieldType);
            } else {
                clearMethod.addStatement("$L = $L", fieldCodegen.internalName(), ProtoUtils.getDefaultReturnValue(fieldType.toString()));
            }
        }
        clearMethod.addStatement("return this");
        // public Builder clear()
        builderClassBuilder.addMethod(clearMethod.build());

        // public <MessageName> getDefaultInstanceForType()
        builderClassBuilder.addMethod(CodegenMethods.Builder.getDefaultInstanceForType(messageClassName));
        // public static final Descriptors.Descriptor getDescriptor()
        builderClassBuilder.addMethod(CodegenMethods.Builder.getDescriptorForType(messageClassName));
        // public Descriptors.Descriptor getDescriptorForType()
        builderClassBuilder.addMethod(CodegenMethods.Builder.getDescriptor(messageClassName));
        // public final boolean isInitialized()
        builderClassBuilder.addMethod(CodegenMethods.Builder.isInitialized());
        // public Builder mergeFrom(CodedInputStream input, ExtensionRegistryLite extensionRegistry)
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromCodedInput(builderClassName()));
        // protected GeneratedMessageV3.FieldAccessorTable internalGetFieldAccessorTable()
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetFieldAccessorTable(messageClassName, getFieldAccessorTableClass()));
        // protected MapFieldReflectionAccessor internalGetMapFieldReflection(int fieldNumber)
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMapFieldReflection(this, this));
        // protected MapFieldReflectionAccessor internalGetMutableMapFieldReflection(int fieldNumber)
        builderClassBuilder.addMethod(CodegenMethods.Builder.internalGetMutableMapFieldReflection(this, this));
        // public Builder mergeFrom(Message other)
        builderClassBuilder.addMethod(CodegenMethods.Builder.mergeFromMessage(messageClassName, builderClassName()));

        // public Builder mergeFrom(<MessageName> other)
        builderClassBuilder.addMethod(MessageMethods.mergeFromOther(this));

        return builderClassBuilder.build();
    }


    private void generateBuilderMethods(TypeSpec.Builder builderClassBuilder, ClassName builderClassName) {
        for (var field : descriptor.getFieldList()) {
            var fieldCodegen = FieldCodegen.create(field, this);
            // public Builder set<FieldName>(<FieldType> value)
            // public Builder clear<FieldName>()
            // ...
            fieldCodegen.builderMethods().forEach(builderClassBuilder::addMethod);
        }
    }

    void generateOneofs(TypeSpec.Builder classBuilder) {
        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            var oneof = descriptor.getOneofDecl(i);
            var oneofCtx = OneofCodegen.create(oneof, i, this);

            if (isEnhancedOneof(descriptor)) {
                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + descriptor.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = relativeToProtoPackage(field.getTypeName());
                        if (!typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneofCtx.oneofName() + "' in message '" + descriptor.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }
                    }
                }

                var interfaceBuilder = TypeSpec.interfaceBuilder(oneofCtx.pascalName())
                        .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        TypeName typeName = resolveTypeName(field.getTypeName(), currentScope());
                        if (typeName instanceof ClassName cn) {
                            TypeName orBuilderType = CodegenUtils.getOrBuilderType(cn);
                            if (orBuilderType instanceof ClassName orBuilderCn) {
                                interfaceBuilder.addPermittedSubclass(orBuilderCn);
                            }
                        }
                    }
                }
                // public sealed interface <OneofName>
                classBuilder.addType(interfaceBuilder.build());
            }

            if (isGenerateOneofCase(descriptor)) {
                var enumBuilder = TypeSpec.enumBuilder(oneofCtx.enumName())
                        .addModifiers(Modifier.PUBLIC)
                        .addSuperinterface(com.google.protobuf.Internal.EnumLite.class);

                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        enumBuilder.addEnumConstant(field.getName().toUpperCase(),
                                TypeSpec.anonymousClassBuilder("$L", field.getNumber()).build());
                    }
                }
                enumBuilder.addEnumConstant(oneofCtx.oneofName().toUpperCase() + "_NOT_SET",
                        TypeSpec.anonymousClassBuilder("0").build());

                // private final int value
                enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
                // private <OneofName>Case(int value)
                enumBuilder.addMethod(MethodSpec.constructorBuilder()
                        .addParameter(int.class, "value")
                        .addStatement("this.value = value")
                        .build());

                // public int getNumber()
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
                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        forNumberBuilder.addStatement("case $L: return $L", field.getNumber(), field.getName().toUpperCase());
                    }
                }
                forNumberBuilder.addStatement("case 0: return $L", oneofCtx.oneofName().toUpperCase() + "_NOT_SET");
                forNumberBuilder.addStatement("default: return null");
                forNumberBuilder.endControlFlow();
                // public static <OneofName>Case forNumber(int value)
                enumBuilder.addMethod(forNumberBuilder.build());

                // public enum <OneofName>Case implements Internal.EnumLite
                classBuilder.addType(enumBuilder.build());

                // public <OneofName>Case get<OneofName>Case()
                classBuilder.addMethod(OneofMessages.getOneofCase(oneofCtx, generateGetCaseCode(descriptor, i, oneofCtx.oneofName(), oneofCtx.enumName())));
            }

            if (isEnhancedOneof(descriptor)) {
                var switchCode = CodeBlock.builder();
                switchCode.beginControlFlow("switch ($LCase_)", oneofCtx.oneofName());
                for (var field : descriptor.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        switchCode.addStatement("case $L: return get$L()", field.getNumber(), ProtoUtils.toPascalCase(field.getName()));
                    }
                }
                switchCode.addStatement("default: return null");
                switchCode.endControlFlow();

                // public <OneofName> get<OneofName>()
                classBuilder.addMethod(OneofMessages.getOneof(oneofCtx, switchCode.build()));
            }
        }
    }

    private CodeBlock generateGetCaseCode(DescriptorProtos.DescriptorProto message, int oneofIndex, String oneofName, String enumName) {
        return CodeBlock.of("return $L.forNumber($LCase_);\n", enumName, oneofName);
    }


    TypeSpec generateMessageInterface() {
        var interfaceBuilder = TypeSpec.interfaceBuilder(interfaceClassName())
                .addAnnotations(CodegenUtils.getMessageAnnotations(descriptor.getOptions()))
                .addSuperinterface(ProtoCodegen.OR_BUILDER_INTERFACE)
                .addModifiers(Modifier.PUBLIC);

        getJavaImplements().ifPresent(s -> interfaceBuilder.addSuperinterface(ClassName.bestGuess(s)));

        String canonicalName = canonicalMessageName();
        var oneofInterfaces = config.oneofInterfacesByType().getOrDefault(canonicalName, java.util.Collections.emptyList());
        if (!oneofInterfaces.isEmpty()) {
            interfaceBuilder.addModifiers(Modifier.NON_SEALED);
            for (var iface : oneofInterfaces) {
                interfaceBuilder.addSuperinterface(iface);
            }
        }

        for (var field : descriptor.getFieldList()) {
            var fieldCodegen = FieldCodegen.create(field, this);
            // <FieldType> get<FieldName>()
            // boolean has<FieldName>()
            // ...
            fieldCodegen.abstractMethods().forEach(interfaceBuilder::addMethod);
        }

        for (int i = 0; i < descriptor.getOneofDeclCount(); i++) {
            var oneof = descriptor.getOneofDecl(i);
            var oneofCtx = OneofCodegen.create(oneof, i, this);

            if (isEnhancedOneof(descriptor)) {
                // <OneofName> get<OneofName>()
                interfaceBuilder.addMethod(OneofMessages.abstractGetOneof(oneofCtx));
            }

            if (isGenerateOneofCase(descriptor)) {
                // <OneofName>Case get<OneofName>Case()
                interfaceBuilder.addMethod(OneofMessages.abstractGetOneofCase(oneofCtx));
            }
        }
        // public interface <MessageName>OrBuilder extends MessageOrBuilder
        return interfaceBuilder.build();
    }
}
