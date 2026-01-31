package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility methods for generating Java code from Protobuf descriptors.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Annotation parsing and extraction</li>
 *   <li>Type registry management</li>
 *   <li>Field type resolution</li>
 *   <li>Code block generation for write conditions</li>
 * </ul>
 */
public class CodegenUtils {
    private static final String FIELD_ANNOTATION = "dev.akre.protege.java_field_annotation";
    private static final String MESSAGE_ANNOTATION = "dev.akre.protege.java_message_annotation";
    private static final String CLASS_ANNOTATION = "dev.akre.protege.java_class_annotation";

    public static final Map<DescriptorProtos.FieldDescriptorProto.Type, TypeName> PROTO_TYPE_TO_TYPE_NAME = Map.ofEntries(
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

    private CodegenUtils() {
    }

    static List<AnnotationSpec> getFieldAnnotations(DescriptorProtos.FieldOptions options) {
        return options.getUninterpretedOptionList().stream()
                .filter(o -> o.getNameList().stream()
                        .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                        .collect(Collectors.joining(".")).equals(FIELD_ANNOTATION))
                .map(o -> parseAnnotation(o.getStringValue().toStringUtf8()))
                .collect(Collectors.toList());
    }

    static List<AnnotationSpec> getClassAnnotations(DescriptorProtos.MessageOptions options) {
        return options.getUninterpretedOptionList().stream()
                .filter(o -> o.getNameList().stream()
                        .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                        .collect(Collectors.joining(".")).equals(CLASS_ANNOTATION))
                .map(o -> parseAnnotation(o.getStringValue().toStringUtf8()))
                .collect(Collectors.toList());
    }

    static List<AnnotationSpec> getMessageAnnotations(DescriptorProtos.MessageOptions options) {
        return options.getUninterpretedOptionList().stream().filter(o -> o.getNameList().stream().map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart).collect(Collectors.joining(".")).equals(MESSAGE_ANNOTATION)).map(o -> parseAnnotation(o.getStringValue().toStringUtf8())).collect(Collectors.toList());
    }

    static AnnotationSpec parseAnnotation(String annotationStr) {
        if (annotationStr.startsWith("@")) {
            annotationStr = annotationStr.substring(1);
        }
        int parenIndex = annotationStr.indexOf('(');
        if (parenIndex == -1) {
            return AnnotationSpec.builder(ClassName.bestGuess(annotationStr)).build();
        } else {
            String className = annotationStr.substring(0, parenIndex).trim();
            String content = annotationStr.substring(parenIndex + 1, annotationStr.lastIndexOf(')')).trim();
            var builder = AnnotationSpec.builder(ClassName.bestGuess(className));
            if (!content.isEmpty()) {
                // naive split by comma, but avoiding commas inside quotes
                List<String> parts = new ArrayList<>();
                boolean inQuotes = false;
                StringBuilder currentPart = new StringBuilder();
                for (int i = 0; i < content.length(); i++) {
                    char c = content.charAt(i);
                    if (c == '"') {
                        inQuotes = !inQuotes;
                    }
                    if (c == ',' && !inQuotes) {
                        parts.add(currentPart.toString());
                        currentPart = new StringBuilder();
                    } else {
                        currentPart.append(c);
                    }
                }
                parts.add(currentPart.toString());

                for (String part : parts) {
                    if (part.contains("=")) {
                        int eqIndex = part.indexOf('=');
                        String key = part.substring(0, eqIndex).trim();
                        String value = part.substring(eqIndex + 1).trim();
                        builder.addMember(key, "$L", value);
                    } else {
                        builder.addMember("value", "$L", part.trim());
                    }
                }
            }
            return builder.build();
        }
    }

    static void registerAllTypes(DescriptorProtos.FileDescriptorProto fileDescriptor, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap, Map<String, Boolean> isMapEntryMap, Map<String, DescriptorProtos.DescriptorProto> messageDescriptorRegistry) {
        for (var message : fileDescriptor.getMessageTypeList()) {
            registerTypes(message, packageName, outerClassName, typeRegistry, isEnumMap, isMapEntryMap, messageDescriptorRegistry, new ArrayList<>());
        }
        for (var enumType : fileDescriptor.getEnumTypeList()) {
            registerEnumTypes(enumType, packageName, outerClassName, typeRegistry, isEnumMap, new ArrayList<>());
        }
    }

    private static void registerTypes(DescriptorProtos.DescriptorProto message, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap, Map<String, Boolean> isMapEntryMap, Map<String, DescriptorProtos.DescriptorProto> messageDescriptorRegistry, List<String> parentNames) {
        var currentPath = new ArrayList<>(parentNames);
        currentPath.add(message.getName());

        var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).collect(Collectors.toList());
        var className = ClassName.get(packageName, outerClassName, capitalizedPath.toArray(new String[0]));
        var relativeProtoName = String.join(".", currentPath);
        typeRegistry.put(relativeProtoName, className);
        isEnumMap.put(relativeProtoName, false);
        isMapEntryMap.put(relativeProtoName, message.getOptions().getMapEntry());
        messageDescriptorRegistry.put(relativeProtoName, message);

        for (var nested : message.getNestedTypeList()) {
            registerTypes(nested, packageName, outerClassName, typeRegistry, isEnumMap, isMapEntryMap, messageDescriptorRegistry, currentPath);
        }
        for (var nestedEnum : message.getEnumTypeList()) {
            registerEnumTypes(nestedEnum, packageName, outerClassName, typeRegistry, isEnumMap, currentPath);
        }
    }

    private static void registerEnumTypes(DescriptorProtos.EnumDescriptorProto enumType, String packageName, String outerClassName, Map<String, ClassName> typeRegistry, Map<String, Boolean> isEnumMap, List<String> parentNames) {
        var currentPath = new ArrayList<>(parentNames);
        currentPath.add(enumType.getName());

        var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).collect(Collectors.toList());
        var className = ClassName.get(packageName, outerClassName, capitalizedPath.toArray(new String[0]));
        var relativeProtoName = String.join(".", currentPath);
        typeRegistry.put(relativeProtoName, className);
        isEnumMap.put(relativeProtoName, true);
    }

    static DescriptorProtos.FileDescriptorProto fixAllFieldTypes(DescriptorProtos.FileDescriptorProto fileDescriptor, Map<String, Boolean> isEnumMap) {
        var fileBuilder = fileDescriptor.toBuilder();
        for (var messageBuilder : fileBuilder.getMessageTypeBuilderList()) {
            fixFieldTypes(messageBuilder, fileBuilder, isEnumMap, new ArrayList<>());
        }
        return fileBuilder.build();
    }

    private static void fixFieldTypes(DescriptorProtos.DescriptorProto.Builder messageBuilder, DescriptorProtos.FileDescriptorProto.Builder fileBuilder, Map<String, Boolean> isEnumMap, List<String> currentScope) {
        var nextScope = new ArrayList<>(currentScope);
        nextScope.add(messageBuilder.getName());

        for (var fieldBuilder : messageBuilder.getFieldBuilderList()) {
            if (fieldBuilder.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE && fieldBuilder.hasTypeName()) {
                var typeName = fieldBuilder.getTypeName();
                var isEnum = false;
                if (typeName.startsWith(".")) {
                    var name = relativeToProtoPackage(typeName, fileBuilder.getPackage());
                    if (isEnumMap.getOrDefault(name, false)) {
                        isEnum = true;
                    }
                } else {
                    for (int i = nextScope.size(); i >= 0; i--) {
                        var scope = nextScope.subList(0, i);
                        var candidateName = scope.isEmpty() ? typeName : String.join(".", scope) + "." + typeName;
                        if (isEnumMap.getOrDefault(candidateName, false)) {
                            isEnum = true;
                            break;
                        }
                    }
                }
                if (isEnum) {
                    fieldBuilder.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM);
                }
            }
        }

        for (var nested : messageBuilder.getNestedTypeBuilderList()) {
            fixFieldTypes(nested, fileBuilder, isEnumMap, nextScope);
        }
    }

    static boolean getBooleanOption(List<DescriptorProtos.UninterpretedOption> options, String name, boolean defaultValue) {
        for (var option : options) {
            String optionName = option.getNameList().stream().map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart).collect(Collectors.joining("."));
            if (optionName.equals(name)) {
                if (option.hasIdentifierValue()) {
                    return Boolean.parseBoolean(option.getIdentifierValue());
                }
            }
        }
        return defaultValue;
    }

    /**
     * Generates the condition to check if a field has a non-default value (proto3 semantics) and should be written to the output stream.
     */
    static CodeBlock getWriteCondition(DescriptorProtos.FieldDescriptorProto.Type type, String fieldName, MessageCodegen context) {
        if (context.descriptor().getOptions().getMapEntry()) {
            return null;
        }
        return switch (type) {
            case TYPE_STRING -> CodeBlock.of("!$T.isStringEmpty((java.lang.Object)$L)", ProtoUtils.class, fieldName);
            case TYPE_INT32, TYPE_UINT32, TYPE_SINT32, TYPE_FIXED32, TYPE_SFIXED32 ->
                    CodeBlock.of("$L != 0", fieldName);
            case TYPE_ENUM -> CodeBlock.of("$L.getNumber() != 0", fieldName);
            case TYPE_MESSAGE -> CodeBlock.of("$L != null", fieldName);
            case TYPE_INT64, TYPE_UINT64, TYPE_SINT64, TYPE_FIXED64, TYPE_SFIXED64 ->
                    CodeBlock.of("$L != 0L", fieldName);
            case TYPE_FLOAT -> CodeBlock.of("java.lang.Float.floatToRawIntBits($L) != 0", fieldName);
            case TYPE_DOUBLE -> CodeBlock.of("java.lang.Double.doubleToRawLongBits($L) != 0", fieldName);
            case TYPE_BOOL -> CodeBlock.of("$L", fieldName);
            case TYPE_BYTES -> CodeBlock.of("!$L.isEmpty()", fieldName);
            default -> null;
        };
    }

    /**
     * Converts a message type name to its corresponding *OrBuilder interface type.
     * <p>
     * Used when generating method signatures that accept both the concrete message class
     * and its builder (via the interface).
     *
     * @param typeName The concrete message type (e.g., {@code MyMessage})
     * @return The *OrBuilder type (e.g., {@code MyMessageOrBuilder}), or the original type if not a ClassName.
     */
    public static TypeName getOrBuilderType(TypeName typeName) {
        if (typeName instanceof ClassName) {
            ClassName cn = (ClassName) typeName;
            return cn.peerClass(cn.simpleName() + "OrBuilder");
        }
        throw new IllegalArgumentException("unexpected typename: " + typeName);
    }

    /**
     * Relativizes a Protobuf type name by trimming a matching package name from the start. If the package name is not
     * a prefix, the name is unchanged.
     * <p>
     * Removes the package prefix from a type name if it matches the given proto package.
     */
    public static String relativeToProtoPackage(String typeName, String protoPackage) {
        if (typeName.startsWith(".")) {
            if (!protoPackage.isEmpty() && typeName.startsWith("." + protoPackage + ".")) {
                return typeName.substring(protoPackage.length() + 2);
            } else if (typeName.startsWith(".")) {
                return typeName.substring(1);
            }
        }
        return typeName;
    }

    /**
     * Generates code to clear fields associated with a specific oneof group.
     */
    public static CodeBlock generateClearOneofCode(DescriptorProtos.DescriptorProto message, int oneofIndex) {
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
}
