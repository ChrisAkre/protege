package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.akre.protege.compiler.CodegenUtils.getBooleanOption;

/**
 * Global context for code generation
 */
record CodegenContext(
        String packageName,
        String outerName,
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

    ClassName outerClassName() {
        return ClassName.get(packageName, outerName);
    }

    public static final String JAVA_ENHANCED_ONEOF_OPTION = "dev.akre.protege.java_enhanced_oneof";
    public static final String JAVA_GENERATE_ONEOF_CASE_OPTION = "dev.akre.protege.java_oneof_case";

    static CodegenContext create(DescriptorProtos.FileDescriptorProto fileDescriptor, ClassName messageParentClass, boolean generateDeprecated) {
        var packageName = ProtoUtils.getJavaPackage(fileDescriptor);
        var outerClassName = ProtoUtils.getJavaOuterClassName(fileDescriptor);

        var typeRegistry = new HashMap<String, ClassName>();
        var isEnumMap = new HashMap<String, Boolean>();
        var isMapEntryMap = new HashMap<String, Boolean>();
        var messageDescriptorRegistry = new HashMap<String, DescriptorProtos.DescriptorProto>();

        CodegenUtils.registerAllTypes(fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, isMapEntryMap, messageDescriptorRegistry);

        var fixedFileDescriptor = CodegenUtils.fixAllFieldTypes(fileDescriptor, isEnumMap);

        boolean fileEnhancedOneof = getBooleanOption(fixedFileDescriptor.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, false);
        boolean fileGenerateOneofCase = getBooleanOption(fixedFileDescriptor.getOptions().getUninterpretedOptionList(), JAVA_GENERATE_ONEOF_CASE_OPTION, true);

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
        if (field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            return false;
        }
        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
            return false;
        }
        if (!field.hasTypeName()) {
            return false;
        }

        String typeName = relativeToProtoPackage(field.getTypeName());
        return isMapEntryMap.getOrDefault(typeName, false);
    }


    TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        if (protoTypeName.startsWith(".")) {
            var typeName = relativeToProtoPackage(protoTypeName);
            if (typeRegistry.containsKey(typeName)) {
                return typeRegistry.get(typeName);
            }
            return ClassName.get(packageName, outerName, typeName.split("\\."));
        }

        for (int i = currentScope.size(); i >= 0; i--) {
            var scope = currentScope.subList(0, i);
            var candidateName = scope.isEmpty() ? protoTypeName : String.join(".", scope) + "." + protoTypeName;
            if (typeRegistry.containsKey(candidateName)) {
                return typeRegistry.get(candidateName);
            }
        }

        return ClassName.get(packageName, outerName, protoTypeName.split("\\."));
    }

    TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        if (protoTypeName.startsWith(".")) {
            return resolveTypeName(protoTypeName, java.util.Collections.<String>emptyList());
        }

        for (var scope = currentScope; scope != null; scope = scope.tail()) {
            // Cons.stream() iterates from tail to head (root to leaf), so the order is correct.
            String scopeStr = scope.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("."));
            var candidateName = scopeStr.isEmpty() ? protoTypeName : scopeStr + "." + protoTypeName;
            if (typeRegistry.containsKey(candidateName)) {
                return typeRegistry.get(candidateName);
            }
        }

        return ClassName.get(packageName, outerName, protoTypeName.split("\\."));
    }

    TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        if (isMapField(field)) {
            String entryTypeName = relativeToProtoPackage(field.getTypeName());
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


    public DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        String typeEntryName = relativeToProtoPackage(field.getTypeName());
        return messageDescriptorRegistry().get(typeEntryName);
    }

    public String protoPackageName() {
        return fileDescriptor.getPackage();
    }

    public String relativeToProtoPackage(String typeName) {
        return CodegenUtils.relativeToProtoPackage(typeName, protoPackageName());
    }
}
    