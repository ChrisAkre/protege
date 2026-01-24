package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import dev.akre.protege.ProtoUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global context for code generation
 */
record CodegenContext(
        String packageName,
        String outerClassName,
        ClassName messageParentClass,
        DescriptorProtos.FileDescriptorProto fileDescriptor,
        Map<String, ClassName> typeRegistry,
        Map<String, Boolean> isEnumMap,
        Map<String, Boolean> isMapEntryMap,
        Map<String, DescriptorProtos.DescriptorProto> messageDescriptorRegistry,
        boolean generateDeprecated,
        boolean fileEnhancedOneof,
        boolean fileGenerateOneofCase
) {

    private static final String JAVA_ENHANCED_ONEOF_OPTION = "dev.akre.protege.java_enhanced_oneof";
    private static final String JAVA_GENERATE_ONEOF_CASE_OPTION = "dev.akre.protege.java_oneof_case";

    static CodegenContext create(DescriptorProtos.FileDescriptorProto fileDescriptor, ClassName messageParentClass, boolean generateDeprecated) {
        var packageName = ProtoUtils.getJavaPackage(fileDescriptor);
        var outerClassName = ProtoUtils.getJavaOuterClassName(fileDescriptor);

        var typeRegistry = new HashMap<String, ClassName>();
        var isEnumMap = new HashMap<String, Boolean>();
        var isMapEntryMap = new HashMap<String, Boolean>();
        var messageDescriptorRegistry = new HashMap<String, DescriptorProtos.DescriptorProto>();

        CodegenUtils.registerAllTypes(fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, isMapEntryMap, messageDescriptorRegistry);

        var fixedFileDescriptor = CodegenUtils.fixAllFieldTypes(fileDescriptor, isEnumMap);

        boolean fileEnhancedOneof = CodegenUtils.getBooleanOption(fixedFileDescriptor.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, false);
        boolean fileGenerateOneofCase = CodegenUtils.getBooleanOption(fixedFileDescriptor.getOptions().getUninterpretedOptionList(), JAVA_GENERATE_ONEOF_CASE_OPTION, true);

        return new CodegenContext(
                packageName,
                outerClassName,
                messageParentClass,
                fixedFileDescriptor,
                typeRegistry,
                isEnumMap,
                isMapEntryMap,
                messageDescriptorRegistry,
                generateDeprecated,
                fileEnhancedOneof,
                fileGenerateOneofCase
        );
    }

    ClassName getFieldAccessorTableClass() {
        return messageParentClass.nestedClass("FieldAccessorTable");
    }

    boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        if (field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) return false;
        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) return false;
        if (!field.hasTypeName()) return false;

        String typeName = field.getTypeName();
        if (typeName.startsWith(".")) {
            var protoPackage = fileDescriptor.getPackage();
            if (!protoPackage.isEmpty() && typeName.startsWith("." + protoPackage + ".")) {
                typeName = typeName.substring(protoPackage.length() + 2);
            } else if (typeName.startsWith(".")) {
                typeName = typeName.substring(1);
            }
        }
        return isMapEntryMap.getOrDefault(typeName, false);
    }

    TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
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

    TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        if (isMapField(field)) {
            String entryTypeName = field.getTypeName();
            if (entryTypeName.startsWith(".")) {
                var protoPackage = fileDescriptor.getPackage();
                if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                    entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                } else if (entryTypeName.startsWith(".")) {
                    entryTypeName = entryTypeName.substring(1);
                }
            }
            var entryDescriptor = messageDescriptorRegistry.get(entryTypeName);
            var keyField = entryDescriptor.getField(0);
            var valueField = entryDescriptor.getField(1);

            var keyType = getFieldType(keyField, currentScope);
            var valueType = getFieldType(valueField, currentScope);

            return ParameterizedTypeName.get(ClassName.get(java.util.Map.class), keyType.box(), valueType.box());
        }
        if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            var genericType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                genericType = resolveTypeName(field.getTypeName(), currentScope);
            }
            if (genericType == null) {
                genericType = TypeName.get(Object.class);
            }
            return ParameterizedTypeName.get(ClassName.get(java.util.List.class), genericType.box());
        } else {
            var fieldType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                fieldType = resolveTypeName(field.getTypeName(), currentScope);
            }
            if (fieldType == null) {
                fieldType = TypeName.get(Object.class);
            }
            return fieldType;
        }
    }
}

/**
 * Context for a specific message being generated
 */
record MessageContext(
        DescriptorProtos.DescriptorProto message,
        ClassName messageClassName,
        ClassName interfaceClassName,
        List<String> currentScope,
        String[] allNames,
        boolean enhancedOneof,
        boolean generateOneofCase
) {
    String getMessageName() {
        return ProtoUtils.toPascalCase(message.getName());
    }
}

/**
 * Context for a field being generated
 */
record FieldContext(
        DescriptorProtos.FieldDescriptorProto field,
        TypeName fieldType,
        String fieldName,
        String pascalName,
        String internalName,
        boolean isMap,
        boolean isRepeated
) {
    static FieldContext create(
            DescriptorProtos.FieldDescriptorProto field,
            TypeName fieldType,
            boolean isMap
    ) {
        String fieldName = field.getName();
        String pascalName = ProtoUtils.toPascalCase(fieldName);
        String internalName = fieldName + "_";
        boolean isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;

        return new FieldContext(
                field,
                fieldType,
                fieldName,
                pascalName,
                internalName,
                isMap,
                isRepeated
        );
    }
}

/**
 * Context for map field generation
 */
record MapFieldContext(
        FieldContext fieldContext,
        DescriptorProtos.DescriptorProto entryDescriptor,
        TypeName keyType,
        TypeName valueType,
        ClassName innerType
) {
    String pascalName() {
        return fieldContext.pascalName();
    }

    String internalName() {
        return fieldContext.internalName();
    }

    int fieldNumber() {
        return fieldContext.field().getNumber();
    }

    DescriptorProtos.FieldDescriptorProto.Type valueFieldType() {
        return entryDescriptor.getField(1).getType();
    }
}

/**
 * Context for repeated field generation
 */
record RepeatedFieldContext(
        FieldContext fieldContext,
        TypeName genericType
) {
    String pascalName() {
        return fieldContext.pascalName();
    }

    String internalName() {
        return fieldContext.internalName();
    }

    int fieldNumber() {
        return fieldContext.field().getNumber();
    }

    DescriptorProtos.FieldDescriptorProto.Type fieldType() {
        return fieldContext.field().getType();
    }

    boolean isString() {
        return fieldType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
    }

    boolean isMessage() {
        return fieldType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
    }

    boolean isEnum() {
        return fieldType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
    }
}

/**
 * Context for singular field generation
 */
record SingularFieldContext(
        FieldContext fieldContext,
        MessageContext messageContext
) {
    String pascalName() {
        return fieldContext.pascalName();
    }

    String internalName() {
        return fieldContext.internalName();
    }

    int fieldNumber() {
        return fieldContext.field().getNumber();
    }

    TypeName fieldType() {
        return fieldContext.fieldType();
    }

    DescriptorProtos.FieldDescriptorProto.Type type() {
        return fieldContext.field().getType();
    }

    boolean hasOneofIndex() {
        return fieldContext.field().hasOneofIndex();
    }

    int oneofIndex() {
        return fieldContext.field().getOneofIndex();
    }

    boolean isString() {
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
    }

    boolean isMessage() {
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
    }

    boolean isEnum() {
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
    }
}

/**
 * Context for enum generation
 */
record EnumContext(
        DescriptorProtos.EnumDescriptorProto enumType,
        ClassName outerClassName,
        String[] parentNames
) {
    String getEnumName() {
        return enumType.getName();
    }
}

/**
 * Context for oneof generation
 */
record OneofContext(
        DescriptorProtos.OneofDescriptorProto oneof,
        int oneofIndex,
        String pascalName,
        String enumName,
        ClassName messageClassName
) {
    static OneofContext create(
            DescriptorProtos.OneofDescriptorProto oneof,
            int oneofIndex,
            ClassName messageClassName
    ) {
        String pascalName = ProtoUtils.toPascalCase(oneof.getName());
        String enumName = pascalName + "Case";
        return new OneofContext(oneof, oneofIndex, pascalName, enumName, messageClassName);
    }

    String oneofName() {
        return oneof.getName();
    }

    ClassName getEnumClassName() {
        return messageClassName.nestedClass(enumName);
    }

    ClassName getInterfaceClassName() {
        return messageClassName.nestedClass(pascalName);
    }
}

/**
 * Context for service generation
 */
record ServiceContext(
        DescriptorProtos.ServiceDescriptorProto service,
        String packageName,
        String outerClassName,
        int serviceIndex
) {
    String getServiceName() {
        return service.getName();
    }
}