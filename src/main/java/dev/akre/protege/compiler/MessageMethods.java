package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MessageMethods {

    public static MethodSpec getDescriptor() {
        return MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .returns(Descriptors.Descriptor.class)
                .addStatement("return descriptor")
                .build();
    }

    public static FieldSpec memoizedSizeField() {
        return FieldSpec.builder(int.class, "memoizedSize", Modifier.PRIVATE)
                .initializer("-1")
                .build();
    }

    public static MethodSpec toBuilder(ClassName messageClassName, ClassName builderClassName) {
        return MethodSpec.methodBuilder("toBuilder")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addStatement("return this == DEFAULT_INSTANCE ? new $T() : new $T().mergeFrom(this)", builderClassName, builderClassName)
                .build();
    }

    public static MethodSpec getParserForType() {
        return MethodSpec.methodBuilder("getParserForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(com.google.protobuf.Parser.class)
                .addStatement("return PARSER")
                .build();
    }

    public static MethodSpec newBuilderForType(ClassName builderClassName) {
        return MethodSpec.methodBuilder("newBuilderForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addStatement("return newBuilder()")
                .build();
    }

    public static MethodSpec newBuilderForTypeWithParent(MessageCodegen context) {
        return MethodSpec.methodBuilder("newBuilderForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .returns(context.builderClassName())
                .addParameter(context.getMessageSuperclass().nestedClass("BuilderParent"), "parent")
                .addStatement("return new $T(parent)", context.builderClassName())
                .build();
    }

    public static MethodSpec getDefaultInstanceForType(ClassName messageClassName) {
        return MethodSpec.methodBuilder("getDefaultInstanceForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(messageClassName)
                .addStatement("return DEFAULT_INSTANCE")
                .build();
    }

    public static MethodSpec getUnknownFields() {
        return MethodSpec.methodBuilder("getUnknownFields")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(com.google.protobuf.UnknownFieldSet.class)
                .addStatement("return com.google.protobuf.UnknownFieldSet.getDefaultInstance()")
                .build();
    }

    public static MethodSpec isInitialized() {
        return MethodSpec.methodBuilder("isInitialized")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(boolean.class)
                .addStatement("return true")
                .build();
    }

    public static MethodSpec newBuilder(ClassName messageClassName, ClassName builderClassName) {
        return MethodSpec.methodBuilder("newBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return DEFAULT_INSTANCE.toBuilder()")
                .build();
    }

    public static MethodSpec newBuilderWithPrototype(ClassName messageClassName, ClassName builderClassName) {
        return MethodSpec.methodBuilder("newBuilder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(messageClassName, "prototype")
                .returns(builderClassName)
                .addStatement("return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype)")
                .build();
    }

    public static MethodSpec parser(ClassName messageClassName) {
        return MethodSpec.methodBuilder("parser")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ParameterizedTypeName.get(ClassName.get("com.google.protobuf", "Parser"), messageClassName))
                .addStatement("return PARSER")
                .build();
    }

    public static MethodSpec internalGetFieldAccessorTable(ClassName messageClassName, ClassName fieldAccessorTableClass) {
        return MethodSpec.methodBuilder("internalGetFieldAccessorTable")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PROTECTED)
                .returns(fieldAccessorTableClass)
                .addStatement("return internal_fieldAccessorTable.ensureFieldAccessorsInitialized($T.class, $T.Builder.class)", messageClassName, messageClassName)
                .build();
    }

    // Field getters
    public static MethodSpec hasField(FieldCodegen ctx, CodeBlock hasCode) {
        return MethodSpec.methodBuilder("has" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addCode(hasCode)
                .build();
    }

    public static MethodSpec getField(FieldCodegen ctx) {
        var getterBuilder = MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(ctx.fieldType());

        if (ctx.isString()) {
            getterBuilder.addStatement("java.lang.Object ref = $L", ctx.internalName());
            getterBuilder.addStatement("if (ref instanceof $T) { return ($T) ref; }", String.class, String.class);
            getterBuilder.addStatement("$T bs = ($T) ref", com.google.protobuf.ByteString.class, com.google.protobuf.ByteString.class);
            getterBuilder.addStatement("$T s = bs.toStringUtf8()", String.class);
            getterBuilder.addStatement("$L = s", ctx.internalName());
            getterBuilder.addStatement("return s");
        } else if (ctx.isMessage()) {
            getterBuilder.addStatement("return $L != null ? $L : $T.getDefaultInstance()", ctx.internalName(), ctx.internalName(), ctx.fieldType());
        } else {
            getterBuilder.addStatement("return ($T)$L", ctx.fieldType(), ctx.internalName());
        }

        return getterBuilder.build();
    }

    public static MethodSpec getFieldValue(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Value")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L == null ? 0 : $L.getNumber()", ctx.internalName(), ctx.internalName())
                .build();
    }

    public static MethodSpec getFieldOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(orBuilderType)
                .addStatement("return get$L()", ctx.pascalName())
                .build();
    }

    public static MethodSpec getFieldBytes(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Bytes")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("com.google.protobuf", "ByteString"))
                .addStatement("java.lang.Object ref = $L", ctx.internalName())
                .beginControlFlow("if (ref instanceof String)")
                .addStatement("$T b = $T.copyFromUtf8(($T) ref)", com.google.protobuf.ByteString.class, com.google.protobuf.ByteString.class, String.class)
                .addStatement("$L = b", ctx.internalName())
                .addStatement("return b")
                .nextControlFlow("else")
                .addStatement("return ($T) ref", com.google.protobuf.ByteString.class)
                .endControlFlow()
                .build();
    }

    // Map field getters
    public static MethodSpec containsMapKey(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("contains" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(ctx.keyType(), "key")
                .addStatement("return $L.getMap().containsKey(key)", ctx.internalName())
                .build();
    }

    public static MethodSpec getMapField(FieldCodegen ctx, TypeName fieldType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Map")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(fieldType)
                .addStatement("return $L.getMap()", ctx.internalName())
                .build();
    }

    public static MethodSpec getMapCount(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L.getMap().size()", ctx.internalName())
                .build();
    }

    public static MethodSpec getMapOrDefault(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrDefault")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.valueType())
                .addParameter(ctx.keyType(), "key")
                .addParameter(ctx.valueType(), "defaultValue")
                .addStatement("return $L.getMap().getOrDefault(key, defaultValue)", ctx.internalName())
                .build();
    }

    public static MethodSpec getMapOrThrow(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrThrow")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.valueType())
                .addParameter(ctx.keyType(), "key")
                .beginControlFlow("if (!$L.getMap().containsKey(key))", ctx.internalName())
                .addStatement("throw new $T()", IllegalArgumentException.class)
                .endControlFlow()
                .addStatement("return $L.getMap().get(key)", ctx.internalName())
                .build();
    }

    public static MethodSpec getMapDeprecated(FieldCodegen ctx, TypeName fieldType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addAnnotation(Deprecated.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(fieldType)
                .addStatement("return get$LMap()", ctx.pascalName())
                .build();
    }

    // Repeated field getters
    public static MethodSpec getRepeatedListString(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "List")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(ClassName.get("com.google.protobuf", "ProtocolStringList"))
                .addStatement("return new $T($L)", com.google.protobuf.UnmodifiableLazyStringList.class, ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedList(FieldCodegen ctx, TypeName fieldType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "List")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(fieldType)
                .addStatement("return $L", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedCount(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L.size()", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedElement(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.genericType())
                .addParameter(int.class, "index")
                .addStatement("return $L.get(index)", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedOrBuilderList(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilderList")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(orBuilderType)))
                .addStatement("return (java.util.List) $L", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(orBuilderType)
                .addParameter(int.class, "index")
                .addStatement("return $L.get(index)", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedValueList(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "ValueList")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, Integer.class))
                .addStatement("return $L.stream().map(e -> e.getNumber()).collect($T.toList())", ctx.internalName(), Collectors.class)
                .build();
    }

    public static MethodSpec getRepeatedValue(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Value")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(int.class, "index")
                .addStatement("return $L.get(index).getNumber()", ctx.internalName())
                .build();
    }

    public static MethodSpec getRepeatedBytes(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Bytes")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get("com.google.protobuf", "ByteString"))
                .addParameter(int.class, "index")
                .addStatement("return $L.getByteString(index)", ctx.internalName())
                .build();
    }

    // Parse methods
    public static MethodSpec parseFrom(ClassName messageClassName, TypeName paramType, String paramName, boolean hasRegistry) {
        var builder = MethodSpec.methodBuilder("parseFrom")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(messageClassName)
                .addParameter(paramType, paramName);

        if (hasRegistry) {
            builder.addParameter(ClassName.get("com.google.protobuf", "ExtensionRegistryLite"), "extensionRegistry");
            builder.addException(ClassName.get("com.google.protobuf", "InvalidProtocolBufferException"));
            builder.addStatement("return PARSER.parseFrom($L, extensionRegistry)", paramName);
        } else {
            builder.addException(paramType.toString().contains("InputStream") ?
                    ClassName.get("java.io", "IOException") :
                    ClassName.get("com.google.protobuf", "InvalidProtocolBufferException"));
            builder.addStatement("return PARSER.parseFrom($L)", paramName);
        }

        return builder.build();
    }

    public static MethodSpec parseDelimitedFrom(ClassName messageClassName, boolean hasRegistry) {
        var builder = MethodSpec.methodBuilder("parseDelimitedFrom")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(messageClassName)
                .addParameter(ClassName.get("java.io", "InputStream"), "input")
                .addException(ClassName.get("java.io", "IOException"));

        if (hasRegistry) {
            builder.addParameter(ClassName.get("com.google.protobuf", "ExtensionRegistryLite"), "extensionRegistry");
            builder.addStatement("return PARSER.parseDelimitedFrom(input, extensionRegistry)");
        } else {
            builder.addStatement("return PARSER.parseDelimitedFrom(input)");
        }

        return builder.build();
    }

    static MethodSpec writeTo(MessageCodegen context) {
        var writeToBuilder = MethodSpec.methodBuilder("writeTo")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get("com.google.protobuf", "CodedOutputStream"), "output")
                .addException(IOException.class);

        for (var field : context.descriptor().getFieldList()) {
            var fieldName = field.getName() + "_";
            var number = field.getNumber();
            var isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
            var methodName = ProtoUtils.getWriteMethodName(field.getType());

            if (context.ctx().isMapField(field)) {
                writeToBuilder.beginControlFlow("");
                var innerType = context.ctx().resolveTypeName(field.getTypeName(), context.currentScope());
                String entryTypeName = field.getTypeName();
                if (entryTypeName.startsWith(".")) {
                    var protoPackage = context.ctx().fileDescriptor().getPackage();
                    if (!protoPackage.isEmpty() && entryTypeName.startsWith("." + protoPackage + ".")) {
                        entryTypeName = entryTypeName.substring(protoPackage.length() + 2);
                    } else if (entryTypeName.startsWith(".")) {
                        entryTypeName = entryTypeName.substring(1);
                    }
                }
                var entryDescriptor = context.ctx().messageDescriptorRegistry().get(entryTypeName);
                var keyField = entryDescriptor.getField(0);
                var valueField = entryDescriptor.getField(1);
                var keyType = context.ctx().getFieldType(keyField, context.currentScope());
                var valueType = context.ctx().getFieldType(valueField, context.currentScope());

                writeToBuilder.addStatement("$T<$T, $T> sortedMap = new $T<>($L.getMap())",
                        Map.class, keyType.box(), valueType.box(), java.util.TreeMap.class, fieldName);
                writeToBuilder.beginControlFlow("for ($T<$T, $T> entry : sortedMap.entrySet())",
                        Map.Entry.class, keyType.box(), valueType.box());

                writeToBuilder.addStatement("$T entryMsg = $T.newBuilder().setKey(($T)entry.getKey()).setValue(($T)entry.getValue()).build()", innerType, innerType, keyType.box(), valueType.box());
                writeToBuilder.addStatement("output.writeMessage($L, entryMsg)", number);
                writeToBuilder.endControlFlow();
                writeToBuilder.endControlFlow();
            } else if (isRepeated) {
                writeToBuilder.beginControlFlow("for (int i = 0; i < $L.size(); i++)", fieldName);
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    writeToBuilder.addStatement("$T.writeString(output, $L, $L.get(i))", context.getMessageSuperclass(), number, fieldName);
                } else if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) {
                    writeToBuilder.addStatement("output.$N($L, $L.get(i).getNumber())", methodName, number, fieldName);
                } else {
                    writeToBuilder.addStatement("output.$N($L, $L.get(i))", methodName, number, fieldName);
                }
                writeToBuilder.endControlFlow();
            } else {
                CodeBlock condition;
                if (field.hasOneofIndex()) {
                    var oneofName = context.descriptor().getOneofDecl(field.getOneofIndex()).getName();
                    condition = CodeBlock.of("$LCase_ == $L", oneofName, number);
                } else {
                    condition = CodegenUtils.getWriteCondition(field.getType(), fieldName, context);
                }
                if (condition != null) {
                    writeToBuilder.beginControlFlow("if ($L)", condition);
                }
                if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                    writeToBuilder.addStatement("$T.writeString(output, $L, $L)", context.getMessageSuperclass(), number, fieldName);
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
        return writeToBuilder.build();
    }

    static MethodSpec mergeFromOther(MessageCodegen context) {
        var mergeFromSpecificMethod = MethodSpec.methodBuilder("mergeFrom")
                .addModifiers(Modifier.PUBLIC)
                .returns(context.builderClassName())
                .addParameter(context.messageClassName(), "other")
                .beginControlFlow("if (other == $T.getDefaultInstance())", context.messageClassName())
                .addStatement("return this")
                .endControlFlow();

        for (var field : context.descriptor().getFieldList()) {
            var fieldName = field.getName();
            var pascalName = ProtoUtils.toPascalCase(fieldName);
            if (context.ctx().isMapField(field)) {
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
                CodeBlock condition;
                if (field.hasOneofIndex()) {
                    condition = CodeBlock.of("other.has$L()", pascalName);
                } else {
                    condition = CodegenUtils.getWriteCondition(field.getType(), "other.get" + pascalName + "()", context);
                }
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
        return mergeFromSpecificMethod.build();
    }


    static MethodSpec abstractHasField(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("has" + ctx.pascalName())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(boolean.class)
                .build();
    }

    static MethodSpec abstractGetField(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(ctx.fieldType())
                .build();
    }

    static MethodSpec abstractGetFieldOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(orBuilderType)
                .build();
    }

    static MethodSpec abstractGetFieldValue(String pascalName) {
        return MethodSpec.methodBuilder("get" + pascalName + "Value")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(int.class)
                .build();
    }

    static MethodSpec abstractGetFieldBytes(String pascalName) {
        return MethodSpec.methodBuilder("get" + pascalName + "Bytes")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ClassName.get("com.google.protobuf", "ByteString"))
                .build();
    }

    // Map field methods
    static MethodSpec abstractContainsMapKey(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("contains" + ctx.pascalName())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(ctx.keyType(), "key")
                .build();
    }

    static MethodSpec abstractGetMapField(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Map")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(ParameterizedTypeName.get(ClassName.get(Map.class), ctx.keyType().box(), ctx.valueType().box()))
                .build();
    }

    static MethodSpec abstractGetMapCount(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(int.class)
                .build();
    }

    static MethodSpec abstractGetMapOrDefault(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrDefault")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ctx.valueType())
                .addParameter(ctx.keyType(), "key")
                .addParameter(ctx.valueType(), "defaultValue")
                .build();
    }

    static MethodSpec abstractGetMapOrThrow(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrThrow")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ctx.valueType())
                .addParameter(ctx.keyType(), "key")
                .build();
    }

    static MethodSpec abstractGetMapDeprecated(FieldCodegen ctx, TypeName fieldType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addAnnotation(Deprecated.class)
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(fieldType)
                .build();
    }

    // Repeated field methods
    static MethodSpec abstractGetRepeatedList(FieldCodegen ctx, TypeName listType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "List")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .addAnnotations(ctx.getterAnnotations())
                .returns(listType)
                .build();
    }

    static MethodSpec abstractGetRepeatedCount(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(int.class)
                .build();
    }

    static MethodSpec abstractGetRepeatedElement(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ctx.genericType())
                .addParameter(int.class, "index")
                .build();
    }

    static MethodSpec abstractGetRepeatedOrBuilderList(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilderList")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(orBuilderType)))
                .build();
    }

    static MethodSpec abstractGetRepeatedOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(orBuilderType)
                .addParameter(int.class, "index")
                .build();
    }

    static MethodSpec abstractGetRepeatedValueList(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "ValueList")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, Integer.class))
                .build();
    }

    static MethodSpec abstractGetRepeatedValue(FieldCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Value")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(int.class, "index")
                .build();
    }

    static MethodSpec abstractGetRepeatedBytes(String pascalName) {
        return MethodSpec.methodBuilder("get" + pascalName + "Bytes")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ClassName.get("com.google.protobuf", "ByteString"))
                .addParameter(int.class, "index")
                .build();
    }
}
