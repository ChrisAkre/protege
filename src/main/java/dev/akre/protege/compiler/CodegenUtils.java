package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import dev.akre.protege.ProtoUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CodegenUtils {

    private CodegenUtils() {
        // Utility class
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
                    var protoPackage = fileBuilder.getPackage();
                    var name = typeName;
                    if (!protoPackage.isEmpty() && name.startsWith("." + protoPackage + ".")) {
                        name = name.substring(protoPackage.length() + 2);
                    } else if (name.startsWith(".")) {
                        name = name.substring(1);
                    }
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
}
