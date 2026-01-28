package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * Generates Java code for a single Protobuf field.
 * <p>
 * This class abstracts the complexity of different field types (singular, repeated, map, oneof)
 * and generates the appropriate getter, setter (builder), and cleaner methods.
 */
public record FieldCodegen(
        DescriptorProtos.FieldDescriptorProto descriptor,
        TypeName fieldType,
        String fieldName,
        String pascalName,
        String internalName,
        boolean isMap,
        boolean isRepeated,
        // Map specific
        DescriptorProtos.DescriptorProto entryDescriptor,
        TypeName keyType,
        TypeName valueType,
        // Repeated specific
        TypeName genericType,
        // Context
        MessageCodegen messageCodegen,
        CodegenMetadata config
) implements CodegenConfig {

    public static FieldCodegen create(DescriptorProtos.FieldDescriptorProto field, MessageCodegen messageCodegen) {
        var ctx = messageCodegen;
        var fieldType = ctx.getFieldType(field, messageCodegen.currentScope());

        String fieldName = field.getName();
        String pascalName = ProtoUtils.toPascalCase(fieldName);
        String internalName = fieldName + "_";
        boolean isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
        boolean isMap = ctx.isMapField(field);

        DescriptorProtos.DescriptorProto entryDescriptor = null;
        TypeName keyType = null;
        TypeName valueType = null;
        TypeName genericType = null;

        if (isMap) {
            entryDescriptor = ctx.getEntryDescriptor(field);
            var keyField = entryDescriptor.getField(0);
            var valueField = entryDescriptor.getField(1);
            keyType = ctx.getFieldType(keyField, messageCodegen.currentScope());
            valueType = ctx.getFieldType(valueField, messageCodegen.currentScope());
        } else if (isRepeated) {
            genericType = CodegenUtils.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                genericType = ctx.resolveTypeName(field.getTypeName(), messageCodegen.currentScope());
            }
            if (genericType == null) {
                genericType = TypeName.get(Object.class);
            }
            genericType = genericType.box();
        }

        return new FieldCodegen(
                field,
                fieldType,
                fieldName,
                pascalName,
                internalName,
                isMap,
                isRepeated,
                entryDescriptor,
                keyType,
                valueType,
                genericType,
                messageCodegen,
                messageCodegen.config()
        );
    }

    /**
     * Generates getter methods for this field.
     *
     * @return A list of getter MethodSpecs.
     */
    public List<MethodSpec> getterMethods() {
        if (isMap) {
            return mapGetterMethods();
        }
        if (isRepeated) {
            return listGetterMethods();
        }
        return singularGetterMethods();
    }

    private List<MethodSpec> mapGetterMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MessageMethods.containsMapKey(this));
        methods.add(MessageMethods.getMapField(this, fieldType));
        methods.add(MessageMethods.getMapCount(this));
        methods.add(MessageMethods.getMapOrDefault(this));
        methods.add(MessageMethods.getMapOrThrow(this));

        if (isGenerateDeprecated(descriptor)) {
            methods.add(MessageMethods.getMapDeprecated(this, fieldType));
        }
        return methods;
    }

    private List<MethodSpec> listGetterMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        if (isString()) {
            methods.add(MessageMethods.getRepeatedListString(this));
            methods.add(MessageMethods.getRepeatedBytes(this));
        } else {
            methods.add(MessageMethods.getRepeatedList(this, fieldType));
        }
        methods.add(MessageMethods.getRepeatedCount(this));
        methods.add(MessageMethods.getRepeatedElement(this));

        if (isMessage()) {
            var orBuilderType = CodegenUtils.getOrBuilderType(genericType);
            methods.add(MessageMethods.getRepeatedOrBuilderList(this, orBuilderType));
            methods.add(MessageMethods.getRepeatedOrBuilder(this, orBuilderType));
        }

        if (isEnum()) {
            methods.add(MessageMethods.getRepeatedValueList(this));
            methods.add(MessageMethods.getRepeatedValue(this));
        }
        return methods;
    }

    private List<MethodSpec> singularGetterMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        if (isMessage() || hasOneofIndex()) {
            methods.add(MessageMethods.hasField(this, generateHasFieldCode()));
        }

        methods.add(MessageMethods.getField(this));

        if (isEnum()) {
            methods.add(MessageMethods.getFieldValue(this));
        }

        if (isMessage()) {
            methods.add(MessageMethods.getFieldOrBuilder(this, CodegenUtils.getOrBuilderType(fieldType)));
        }

        if (isString()) {
            methods.add(MessageMethods.getFieldBytes(this));
        }
        return methods;
    }

    /**
     * Generates builder methods (setters, adders, clearers) for this field.
     *
     * @return A list of builder MethodSpecs.
     */
    public List<MethodSpec> builderMethods() {
        if (isMap) {
            return mapBuilderMethods();
        }
        if (isRepeated) {
            return listBuilderMethods();
        }
        return singularBuilderMethods();
    }

    private List<MethodSpec> mapBuilderMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        ClassName builderClassName = messageCodegen.builderClassName();

        methods.add(CodegenMethods.Builder.containsMapKey(this));
        methods.add(CodegenMethods.Builder.getMapField(this, fieldType));
        methods.add(CodegenMethods.Builder.getMapCount(this));
        methods.add(CodegenMethods.Builder.getMapOrDefault(this));
        methods.add(CodegenMethods.Builder.getMapOrThrow(this));

        if (isGenerateDeprecated(descriptor)) {
            methods.add(CodegenMethods.Builder.getMapDeprecated(this, fieldType));
            methods.add(CodegenMethods.Builder.getMutableMapDeprecated(this, fieldType));
        }

        methods.add(CodegenMethods.Builder.putMap(this, builderClassName));
        if (valueType instanceof ClassName && valueFieldType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
            var valueBuilderType = ((ClassName) valueType).nestedClass("Builder");
            methods.add(CodegenMethods.Builder.putMapBuilderIfAbsent(this, valueBuilderType));
        }
        methods.add(CodegenMethods.Builder.removeMap(this, builderClassName));
        methods.add(CodegenMethods.Builder.putAllMap(this, builderClassName, fieldType));
        methods.add(CodegenMethods.Builder.clearMap(this, builderClassName));
        return methods;
    }

    private List<MethodSpec> listBuilderMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        ClassName builderClassName = messageCodegen.builderClassName();

        if (isString()) {
            methods.add(CodegenMethods.Builder.getRepeatedListString(this));
            methods.add(CodegenMethods.Builder.getRepeatedBytes(this));
            methods.add(CodegenMethods.Builder.addRepeatedBytes(this, builderClassName));
        } else {
            methods.add(CodegenMethods.Builder.getRepeatedList(this, fieldType));
        }

        methods.add(CodegenMethods.Builder.getRepeatedCount(this));
        methods.add(CodegenMethods.Builder.getRepeatedElement(this));
        methods.add(CodegenMethods.Builder.setRepeatedElement(this, builderClassName));
        methods.add(CodegenMethods.Builder.addRepeatedElement(this, builderClassName));
        methods.add(CodegenMethods.Builder.addAllRepeatedElements(this, builderClassName));
        methods.add(CodegenMethods.Builder.clearRepeatedField(this, builderClassName));

        if (isMessage()) {
            var orBuilderType = CodegenUtils.getOrBuilderType(genericType);
            var elementBuilderType = ((ClassName) genericType).nestedClass("Builder");

            methods.add(CodegenMethods.Builder.getRepeatedOrBuilderList(this, orBuilderType));
            methods.add(CodegenMethods.Builder.getRepeatedOrBuilder(this, orBuilderType));
            methods.add(CodegenMethods.Builder.getRepeatedBuilder(this, elementBuilderType));
            methods.add(CodegenMethods.Builder.addRepeatedBuilder(this, elementBuilderType));
            methods.add(CodegenMethods.Builder.addRepeatedBuilderAtIndex(this, elementBuilderType));
            methods.add(CodegenMethods.Builder.getRepeatedBuilderList(this, elementBuilderType));
            methods.add(CodegenMethods.Builder.addRepeatedElementAtIndex(this, builderClassName));
            methods.add(CodegenMethods.Builder.setRepeatedBuilder(this, builderClassName, elementBuilderType));
            methods.add(CodegenMethods.Builder.addRepeatedBuilderValue(this, builderClassName, elementBuilderType));
            methods.add(CodegenMethods.Builder.addRepeatedBuilderValueAtIndex(this, builderClassName, elementBuilderType));
            methods.add(CodegenMethods.Builder.removeRepeatedElement(this, builderClassName));
        }

        if (isEnum()) {
            methods.add(CodegenMethods.Builder.getRepeatedValueList(this));
            methods.add(CodegenMethods.Builder.getRepeatedValue(this));
            methods.add(CodegenMethods.Builder.setRepeatedValue(this, builderClassName));
            methods.add(CodegenMethods.Builder.addRepeatedValue(this, builderClassName));
            methods.add(CodegenMethods.Builder.addAllRepeatedValue(this, builderClassName));
        }

        methods.add(CodegenMethods.Builder.ensureIsMutable(this));
        return methods;
    }

    private List<MethodSpec> singularBuilderMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        ClassName builderClassName = messageCodegen.builderClassName();

        if (isMessage() || hasOneofIndex()) {
            methods.add(CodegenMethods.Builder.hasField(this, generateHasFieldCode()));
        }

        methods.add(CodegenMethods.Builder.getField(this));

        if (isEnum()) {
            methods.add(CodegenMethods.Builder.getFieldValue(this));
            methods.add(CodegenMethods.Builder.setFieldValue(this, builderClassName));
        }

        if (isString()) {
            methods.add(CodegenMethods.Builder.getFieldBytes(this));
            var clearOneof = generateClearOneofCode();
            methods.add(CodegenMethods.Builder.setFieldBytes(this, builderClassName, clearOneof));
        }

        var clearOneof = generateClearOneofCode();
        methods.add(CodegenMethods.Builder.setField(this, builderClassName, clearOneof));

        String defaultValue;
        if (descriptor.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
            defaultValue = "\"\"";
        } else {
            defaultValue = ProtoUtils.getDefaultReturnValue(fieldType.toString());
        }
        methods.add(CodegenMethods.Builder.clearField(this, builderClassName, defaultValue));

        if (isMessage()) {
            var elementBuilderType = ((ClassName) fieldType).nestedClass("Builder");
            methods.add(CodegenMethods.Builder.getFieldOrBuilder(this, CodegenUtils.getOrBuilderType(fieldType)));
            methods.add(CodegenMethods.Builder.getFieldBuilder(this, elementBuilderType));
            methods.add(CodegenMethods.Builder.setFieldBuilder(this, builderClassName, elementBuilderType));
            methods.add(CodegenMethods.Builder.mergeField(this, builderClassName, clearOneof));
        }
        return methods;
    }

    /**
     * Generates abstract getter methods for the interface.
     *
     * @return A list of abstract MethodSpecs.
     */
    public List<MethodSpec> abstractMethods() {
        if (isMap) {
            return mapAbstractMethods();
        }
        if (isRepeated) {
            return listAbstractMethods();
        }
        return singularAbstractMethods();
    }

    private List<MethodSpec> mapAbstractMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MessageMethods.abstractContainsMapKey(this));
        methods.add(MessageMethods.abstractGetMapField(this));
        methods.add(MessageMethods.abstractGetMapCount(this));
        methods.add(MessageMethods.abstractGetMapOrDefault(this));
        methods.add(MessageMethods.abstractGetMapOrThrow(this));

        if (isGenerateDeprecated(descriptor)) {
            methods.add(MessageMethods.abstractGetMapDeprecated(this, fieldType));
        }
        return methods;
    }

    private List<MethodSpec> listAbstractMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        methods.add(MessageMethods.abstractGetRepeatedList(this, fieldType));

        if (isString()) {
            methods.add(MessageMethods.abstractGetRepeatedBytes(pascalName));
        }

        methods.add(MessageMethods.abstractGetRepeatedCount(this));
        methods.add(MessageMethods.abstractGetRepeatedElement(this));

        if (isMessage()) {
            var orBuilderType = CodegenUtils.getOrBuilderType(genericType);
            methods.add(MessageMethods.abstractGetRepeatedOrBuilderList(this, orBuilderType));
            methods.add(MessageMethods.abstractGetRepeatedOrBuilder(this, orBuilderType));
        }

        if (isEnum()) {
            methods.add(MessageMethods.abstractGetRepeatedValueList(this));
            methods.add(MessageMethods.abstractGetRepeatedValue(this));
        }
        return methods;
    }

    private List<MethodSpec> singularAbstractMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        if (isMessage() || hasOneofIndex()) {
            methods.add(MessageMethods.abstractHasField(this));
        }

        methods.add(MessageMethods.abstractGetField(this));

        if (isEnum()) {
            methods.add(MessageMethods.abstractGetFieldValue(pascalName));
        }

        if (isMessage()) {
            methods.add(MessageMethods.abstractGetFieldOrBuilder(this, CodegenUtils.getOrBuilderType(fieldType)));
        }

        if (isString()) {
            methods.add(MessageMethods.abstractGetFieldBytes(pascalName));
        }
        return methods;
    }

    // Helper methods
    public int fieldNumber() {
        return descriptor.getNumber();
    }

    public DescriptorProtos.FieldDescriptorProto.Type type() {
        return descriptor.getType();
    }

    public boolean hasOneofIndex() {
        return descriptor.hasOneofIndex();
    }

    public int oneofIndex() {
        return descriptor.getOneofIndex();
    }

    public boolean isString() {
        if (isRepeated && !isMap) {
             return descriptor.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
    }

    public boolean isMessage() {
        if (isRepeated && !isMap) {
             return descriptor.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
    }

    public boolean isEnum() {
        if (isRepeated && !isMap) {
             return descriptor.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
    }

    private CodeBlock generateHasFieldCode() {
        if (descriptor.hasOneofIndex()) {
            return CodeBlock.of("return $LCase_ == $L;\n", messageCodegen.descriptor().getOneofDecl(descriptor.getOneofIndex()).getName(), descriptor.getNumber());
        }
        return CodeBlock.of("return $L_ != null;\n", descriptor.getName());
    }

    /**
     * Generates code to clear the oneof field if it is set.
     *
     * @return The CodeBlock to clear the oneof.
     */
    public CodeBlock generateClearOneofCode() {
         if (!descriptor.hasOneofIndex()) {
             return CodeBlock.of("");
         }
         return CodegenUtils.generateClearOneofCode(messageCodegen.descriptor(), descriptor.getOneofIndex());
    }

    private DescriptorProtos.FieldDescriptorProto.Type valueFieldType() {
        if (entryDescriptor == null) {
            return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32; // Default, shouldn't happen for map
        }
        return entryDescriptor.getField(1).getType();
    }
}
