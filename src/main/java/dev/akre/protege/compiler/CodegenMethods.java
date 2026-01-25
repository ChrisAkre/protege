package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Static methods for generating code specifications
 */
public class CodegenMethods {

    private CodegenMethods() {
        // Utility class
    }

//    static class OuterClass {
//        public static FieldSpec versionField() {
//            return FieldSpec.builder(String.class, "PROTEGE_VERSION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
//                    .initializer("$S", ProtegeVersion.VERSION_STRING)
//                    .build();
//        }
//
//        public static MethodSpec getDescriptor() {
//            return MethodSpec.methodBuilder("getDescriptor")
//                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
//                    .returns(Descriptors.FileDescriptor.class)
//                    .addStatement("return fileDescriptor")
//                    .build();
//        }
//
//        public static FieldSpec fileDescriptorField(CodegenContext ctx) {
//            var descriptorChunks =  ProtoUtils.splitAndEscapeBytes(ctx.fileDescriptor().toByteArray()).stream()
//                    .map(s -> CodeBlock.of("\"$L\"", s))
//                    .collect(CodeBlock.joining(",\n"));
//
//            var data = CodeBlock.builder().add("new String[] {\n").indent().add(descriptorChunks).unindent().add("\n}").build();
//
//            CodeBlock descriptorInitializer = CodeBlock.builder()
//                    .addStatement("$T.internalBuildGeneratedFileFrom($L, new $T[0])",
//                            Descriptors.FileDescriptor.class, data, Descriptors.FileDescriptor.class)
//                    .build();
//            return FieldSpec.builder(Descriptors.FileDescriptor.class, "fileDescriptor", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
//                    .initializer(descriptorInitializer)
//                    .build();
//        }
//    }

    /**
     * Methods for generating enum types
     */
    static class Enums {

        static MethodSpec getNumber() {
            return MethodSpec.methodBuilder("getNumber")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(int.class)
                    .beginControlFlow("if (this == UNRECOGNIZED)")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Can't get the number of an unknown enum value.")
                    .endControlFlow()
                    .addStatement("return value")
                    .build();
        }

        static MethodSpec forNumber(EnumContext ctx) {
            var forNumberBuilder = MethodSpec.methodBuilder("forNumber")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(int.class, "value")
                    .returns(ClassName.get("", ctx.getEnumName()));

            forNumberBuilder.beginControlFlow("switch (value)");
            for (var value : ctx.enumType().getValueList()) {
                forNumberBuilder.addStatement("case $L: return $L", value.getNumber(), value.getName());
            }
            forNumberBuilder.addStatement("default: return null");
            forNumberBuilder.endControlFlow();

            return forNumberBuilder.build();
        }

        static MethodSpec getDescriptor(EnumContext ctx) {
            var cb = CodeBlock.builder();
            cb.add("return $T.getDescriptor()", ctx.outerClassName());

            if (ctx.parentNames().length > 1) {
                cb.add(".findMessageTypeByName($S)", ctx.parentNames()[1]);
                for (int i = 2; i < ctx.parentNames().length; i++) {
                    cb.add(".findNestedTypeByName($S)", ctx.parentNames()[i]);
                }
                cb.add(".findEnumTypeByName($S)", ctx.getEnumName());
            } else {
                cb.add(".findEnumTypeByName($S)", ctx.getEnumName());
            }
            cb.add(";\n");

            return MethodSpec.methodBuilder("getDescriptor")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .returns(Descriptors.EnumDescriptor.class)
                    .addCode(cb.build())
                    .build();
        }

        static MethodSpec getValueDescriptor() {
            return MethodSpec.methodBuilder("getValueDescriptor")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(Descriptors.EnumValueDescriptor.class)
                    .addStatement("return getDescriptor().getValues().get(ordinal())")
                    .build();
        }

        static MethodSpec getDescriptorForType() {
            return MethodSpec.methodBuilder("getDescriptorForType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(Descriptors.EnumDescriptor.class)
                    .addStatement("return getDescriptor()")
                    .build();
        }

        static MethodSpec valueOf(String enumName) {
            return MethodSpec.methodBuilder("valueOf")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(Descriptors.EnumValueDescriptor.class, "desc")
                    .returns(ClassName.get("", enumName))
                    .addStatement("return forNumber(desc.getNumber())")
                    .build();
        }
    }

    /**
     * Methods for generating Builder classes
     */
    static class Builder {

        static MethodSpec build(ClassName messageClassName) {
            return MethodSpec.methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(messageClassName)
                    .addStatement("$T result = buildPartial()", messageClassName)
                    .beginControlFlow("if (!result.isInitialized())")
                    .addStatement("throw newUninitializedMessageException(result)")
                    .endControlFlow()
                    .addStatement("return result")
                    .build();
        }

        static MethodSpec buildPartial(ClassName messageClassName) {
            return MethodSpec.methodBuilder("buildPartial")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(messageClassName)
                    .addStatement("return new $T(this)", messageClassName)
                    .build();
        }

        static MethodSpec getDefaultInstanceForType(ClassName messageClassName) {
            return MethodSpec.methodBuilder("getDefaultInstanceForType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(messageClassName)
                    .addStatement("return $T.getDefaultInstance()", messageClassName)
                    .build();
        }

        static MethodSpec getDescriptorForType(ClassName messageClassName) {
            return MethodSpec.methodBuilder("getDescriptorForType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(Descriptors.Descriptor.class)
                    .addStatement("return $T.getDescriptor()", messageClassName)
                    .build();
        }

        static MethodSpec getDescriptor(ClassName messageClassName) {
            return MethodSpec.methodBuilder("getDescriptor")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .returns(Descriptors.Descriptor.class)
                    .addStatement("return $T.getDescriptor()", messageClassName)
                    .build();
        }

        static MethodSpec isInitialized() {
            return MethodSpec.methodBuilder("isInitialized")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(boolean.class)
                    .addStatement("return true")
                    .build();
        }

        static MethodSpec mergeFromCodedInput(ClassName builderClassName) {
            return MethodSpec.methodBuilder("mergeFrom")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(com.google.protobuf.CodedInputStream.class, "input")
                    .addParameter(com.google.protobuf.ExtensionRegistryLite.class, "extensionRegistry")
                    .addException(java.io.IOException.class)
                    .addStatement("return ($T) super.mergeFrom(input, extensionRegistry)", builderClassName)
                    .build();
        }

        static MethodSpec mergeFromMessage(ClassName messageClassName, ClassName builderClassName) {
            return MethodSpec.methodBuilder("mergeFrom")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(com.google.protobuf.Message.class, "other")
                    .beginControlFlow("if (other instanceof $T)", messageClassName)
                    .addStatement("return mergeFrom(($T) other)", messageClassName)
                    .nextControlFlow("else")
                    .addStatement("super.mergeFrom(other)")
                    .addStatement("return this")
                    .endControlFlow()
                    .build();
        }

        static MethodSpec internalGetFieldAccessorTable(ClassName messageClassName, ClassName fieldAccessorTableClass) {
            return MethodSpec.methodBuilder("internalGetFieldAccessorTable")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .returns(fieldAccessorTableClass)
                    .addStatement("return $T.internal_fieldAccessorTable.ensureFieldAccessorsInitialized($T.class, $T.Builder.class)", messageClassName, messageClassName, messageClassName)
                    .build();
        }

        static MethodSpec internalGetMapFieldReflection(MessageCodegen msgCodegen, CodegenContext ctx) {
            var method = MethodSpec.methodBuilder("internalGetMapFieldReflection")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .returns(com.google.protobuf.MapFieldReflectionAccessor.class)
                    .addParameter(int.class, "fieldNumber")
                    .beginControlFlow("switch (fieldNumber)");

            for (var field : msgCodegen.message().getFieldList()) {
                if (isMapField(field, ctx)) {
                    method.addCode("case " + field.getNumber() + ": return " + field.getName() + "_;\n");
                }
            }

            method.addStatement("default: throw new $T($S + fieldNumber)", RuntimeException.class, "Invalid map field number: ")
                    .endControlFlow();

            return method.build();
        }

        static MethodSpec internalGetMutableMapFieldReflection(MessageCodegen msgCodegen, CodegenContext ctx) {
            var method = MethodSpec.methodBuilder("internalGetMutableMapFieldReflection")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PROTECTED)
                    .returns(com.google.protobuf.MapFieldReflectionAccessor.class)
                    .addParameter(int.class, "fieldNumber")
                    .beginControlFlow("switch (fieldNumber)");

            for (var field : msgCodegen.message().getFieldList()) {
                if (isMapField(field, ctx)) {
                    method.addCode("case " + field.getNumber() + ": return " + field.getName() + "_;\n");
                }
            }

            method.addStatement("default: throw new $T($S + fieldNumber)", RuntimeException.class, "Invalid map field number: ")
                    .endControlFlow();

            return method.build();
        }

        private static boolean isMapField(DescriptorProtos.FieldDescriptorProto field, CodegenContext ctx) {
            if (field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                return false;
            }
            if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                return false;
            }
            if (!field.hasTypeName()) {
                return false;
            }

            String typeName = field.getTypeName();
            if (typeName.startsWith(".")) {
                var protoPackage = ctx.fileDescriptor().getPackage();
                if (!protoPackage.isEmpty() && typeName.startsWith("." + protoPackage + ".")) {
                    typeName = typeName.substring(protoPackage.length() + 2);
                } else if (typeName.startsWith(".")) {
                    typeName = typeName.substring(1);
                }
            }
            return ctx.isMapEntryMap().getOrDefault(typeName, false);
        }

        // Field setters/getters
        static MethodSpec hasField(FieldCodegen ctx, CodeBlock hasCode) {
            return MethodSpec.methodBuilder("has" + ctx.pascalName())
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class)
                    .addCode(hasCode)
                    .build();
        }

        static MethodSpec getField(FieldCodegen ctx) {
            var builder = MethodSpec.methodBuilder("get" + ctx.pascalName())
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotations(ctx.getterAnnotations())
                    .returns(ctx.fieldType());

            if (ctx.isString()) {
                builder.addStatement("java.lang.Object ref = $L", ctx.internalName());
                builder.beginControlFlow("if (!(ref instanceof $T))", String.class);
                builder.addStatement("$T bs = ($T) ref", com.google.protobuf.ByteString.class, com.google.protobuf.ByteString.class);
                builder.addStatement("$T s = bs.toStringUtf8()", String.class);
                builder.addStatement("$L = s", ctx.internalName());
                builder.addStatement("return s");
                builder.endControlFlow();
                builder.addStatement("return ($T) ref", String.class);
            } else {
                builder.addStatement("return $L", ctx.internalName());
            }

            return builder.build();
        }

        static MethodSpec setField(FieldCodegen ctx, ClassName builderClassName, CodeBlock clearOneofCode) {
            var builder = MethodSpec.methodBuilder("set" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ctx.fieldType(), "value");

            if (ctx.hasOneofIndex()) {
                builder.addCode(clearOneofCode);
                builder.addStatement("$LCase_ = $L",
                        ctx.messageCodegen().message().getOneofDecl(ctx.oneofIndex()).getName(),
                        ctx.fieldNumber());
            }

            builder.addStatement("this.$L = value", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this");

            return builder.build();
        }

        static MethodSpec clearField(FieldCodegen ctx, ClassName builderClassName, String defaultValue) {
            return MethodSpec.methodBuilder("clear" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addStatement("this.$L = $L", ctx.internalName(), defaultValue)
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getFieldValue(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Value")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(int.class)
                    .addStatement("return $L == null ? 0 : $L.getNumber()", ctx.internalName(), ctx.internalName())
                    .build();
        }

        static MethodSpec setFieldValue(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("set" + ctx.pascalName() + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "value")
                    .addStatement("this.$L = $T.forNumber(value)", ctx.internalName(), ctx.fieldType())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getFieldBytes(FieldCodegen ctx) {
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

        static MethodSpec setFieldBytes(FieldCodegen ctx, ClassName builderClassName, CodeBlock clearOneofCode) {
            var builder = MethodSpec.methodBuilder("set" + ctx.pascalName() + "Bytes")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ClassName.get("com.google.protobuf", "ByteString"), "value")
                    .addStatement("if (value == null) { throw new NullPointerException(); }");

            if (ctx.hasOneofIndex()) {
                builder.addCode(clearOneofCode);
                builder.addStatement("$LCase_ = $L",
                        ctx.messageCodegen().message().getOneofDecl(ctx.oneofIndex()).getName(),
                        ctx.fieldNumber());
            }

            builder.addStatement("this.$L = value", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this");

            return builder.build();
        }

        static MethodSpec getFieldOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(orBuilderType)
                    .addStatement("return get$L()", ctx.pascalName())
                    .build();
        }

        static MethodSpec getFieldBuilder(FieldCodegen ctx, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Builder")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementBuilderType)
                    .addStatement("onChanged()")
                    .beginControlFlow("if ($L == null)", ctx.internalName())
                    .addStatement("$L = $T.getDefaultInstance()", ctx.internalName(), ctx.fieldType())
                    .endControlFlow()
                    .addStatement("return $T.newBuilder(($T)$L)", ctx.fieldType(), ctx.fieldType(), ctx.internalName())
                    .build();
        }

        static MethodSpec setFieldBuilder(FieldCodegen ctx, ClassName builderClassName, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("set" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(elementBuilderType, "builderForValue")
                    .addStatement("return set$L(builderForValue.build())", ctx.pascalName())
                    .build();
        }

        static MethodSpec mergeField(FieldCodegen ctx, ClassName builderClassName, CodeBlock clearOneofCode) {
            var builder = MethodSpec.methodBuilder("merge" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ctx.fieldType(), "value")
                    .beginControlFlow("if ($L != null)", ctx.internalName())
                    .addStatement("$L = $T.newBuilder(($T)$L).mergeFrom(value).buildPartial()",
                            ctx.internalName(), ctx.fieldType(), ctx.fieldType(), ctx.internalName())
                    .nextControlFlow("else");

            if (ctx.hasOneofIndex()) {
                builder.addCode(clearOneofCode);
                builder.addStatement("$LCase_ = $L",
                        ctx.messageCodegen().message().getOneofDecl(ctx.oneofIndex()).getName(),
                        ctx.fieldNumber());
            }

            builder.addStatement("$L = value", ctx.internalName())
                    .endControlFlow()
                    .addStatement("onChanged()")
                    .addStatement("return this");

            return builder.build();
        }

        // Oneof methods
        static MethodSpec getOneof(OneofContext ctx, CodeBlock switchCode) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName())
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ctx.getInterfaceClassName())
                    .addCode(switchCode)
                    .build();
        }

        static MethodSpec getOneofCase(OneofContext ctx, CodeBlock getCaseCode) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Case")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ctx.getEnumClassName())
                    .addCode(getCaseCode)
                    .build();
        }

        static MethodSpec clearOneof(OneofContext ctx, ClassName builderClassName, CodeBlock clearCode) {
            return MethodSpec.methodBuilder("clear" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addCode(clearCode)
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        // Map field methods
        static MethodSpec containsMapKey(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("contains" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(boolean.class)
                    .addParameter(ctx.keyType(), "key")
                    .addStatement("return $L.getMap().containsKey(key)", ctx.internalName())
                    .build();
        }

        static MethodSpec getMapField(FieldCodegen ctx, TypeName fieldType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Map")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotations(ctx.getterAnnotations())
                    .returns(fieldType)
                    .addStatement("return $L.getMap()", ctx.internalName())
                    .build();
        }

        static MethodSpec getMapCount(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(int.class)
                    .addStatement("return $L.getMap().size()", ctx.internalName())
                    .build();
        }

        static MethodSpec getMapOrDefault(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrDefault")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ctx.valueType())
                    .addParameter(ctx.keyType(), "key")
                    .addParameter(ctx.valueType(), "defaultValue")
                    .addStatement("return $L.getMap().getOrDefault(key, defaultValue)", ctx.internalName())
                    .build();
        }

        static MethodSpec getMapOrThrow(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrThrow")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ctx.valueType())
                    .addParameter(ctx.keyType(), "key")
                    .beginControlFlow("if (!$L.getMap().containsKey(key))", ctx.internalName())
                    .addStatement("throw new $T()", IllegalArgumentException.class)
                    .endControlFlow()
                    .addStatement("return $L.getMap().get(key)", ctx.internalName())
                    .build();
        }

        static MethodSpec getMapDeprecated(FieldCodegen ctx, TypeName fieldType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName())
                    .addAnnotation(Deprecated.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(fieldType)
                    .addStatement("return get$LMap()", ctx.pascalName())
                    .build();
        }

        static MethodSpec putMap(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("put" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ctx.keyType(), "key")
                    .addParameter(ctx.valueType(), "value")
                    .addStatement("$L.getMutableMap().put(key, value)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec putMapBuilderIfAbsent(FieldCodegen ctx, ClassName valueBuilderType) {
            return MethodSpec.methodBuilder("put" + ctx.pascalName() + "BuilderIfAbsent")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(valueBuilderType)
                    .addParameter(ctx.keyType(), "key")
                    .addStatement("$T map = $L.getMutableMap()", ParameterizedTypeName.get(ClassName.get(Map.class), ctx.keyType().box(), ctx.valueType().box()), ctx.internalName())
                    .beginControlFlow("if (!map.containsKey(key))")
                    .addStatement("map.put(key, $T.getDefaultInstance())", ctx.valueType())
                    .endControlFlow()
                    .addStatement("onChanged()")
                    .addStatement("return map.get(key).toBuilder()")
                    .build();
        }

        static MethodSpec removeMap(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("remove" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ctx.keyType(), "key")
                    .addStatement("$L.getMutableMap().remove(key)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getMutableMapDeprecated(FieldCodegen ctx, TypeName fieldType) {
            return MethodSpec.methodBuilder("getMutable" + ctx.pascalName())
                    .addAnnotation(Deprecated.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(fieldType)
                    .addStatement("onChanged()")
                    .addStatement("return $L.getMutableMap()", ctx.internalName())
                    .build();
        }

        static MethodSpec putAllMap(FieldCodegen ctx, ClassName builderClassName, TypeName fieldType) {
            return MethodSpec.methodBuilder("putAll" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(fieldType, "values")
                    .addStatement("$L.getMutableMap().putAll(values)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec clearMap(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("clear" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addStatement("$L.getMutableMap().clear()", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        // Repeated field methods
        static MethodSpec getRepeatedListString(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "List")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotations(ctx.getterAnnotations())
                    .returns(ClassName.get("com.google.protobuf", "ProtocolStringList"))
                    .addStatement("return $L.getUnmodifiableView()", ctx.internalName())
                    .build();
        }

        static MethodSpec getRepeatedBytes(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Bytes")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get("com.google.protobuf", "ByteString"))
                    .addParameter(int.class, "index")
                    .addStatement("return $L.getByteString(index)", ctx.internalName())
                    .build();
        }

        static MethodSpec addRepeatedBytes(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName() + "Bytes")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ClassName.get("com.google.protobuf", "ByteString"), "value")
                    .addStatement("if (value == null) { throw new NullPointerException(); }")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add(value)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getRepeatedList(FieldCodegen ctx, TypeName listType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "List")
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotations(ctx.getterAnnotations())
                    .returns(listType)
                    .addStatement("return $T.unmodifiableList($L)", java.util.Collections.class, ctx.internalName())
                    .build();
        }

        static MethodSpec getRepeatedCount(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Count")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(int.class)
                    .addStatement("return $L.size()", ctx.internalName())
                    .build();
        }

        static MethodSpec getRepeatedElement(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ctx.genericType())
                    .addParameter(int.class, "index")
                    .addStatement("return $L.get(index)", ctx.internalName())
                    .build();
        }

        static MethodSpec setRepeatedElement(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("set" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addParameter(ctx.genericType(), "value")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.set(index, value)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec addRepeatedElement(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ctx.genericType(), "value")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add(value)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec addAllRepeatedElements(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("addAll" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ParameterizedTypeName.get(ClassName.get(Iterable.class), WildcardTypeName.subtypeOf(ctx.genericType())), "values")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$T.addAll(values, $L)", com.google.protobuf.AbstractMessageLite.Builder.class, ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec clearRepeatedField(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("clear" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addStatement("$L = $T.$L", ctx.internalName(),
                            ctx.isString() ? com.google.protobuf.LazyStringArrayList.class : java.util.Collections.class,
                            ctx.isString() ? "EMPTY" : "emptyList()")
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getRepeatedOrBuilderList(FieldCodegen ctx, TypeName orBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilderList")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(orBuilderType)))
                    .addStatement("return (java.util.List) $L", ctx.internalName())
                    .build();
        }

        static MethodSpec getRepeatedOrBuilder(FieldCodegen ctx, TypeName orBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "OrBuilder")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(orBuilderType)
                    .addParameter(int.class, "index")
                    .addStatement("return $L.get(index)", ctx.internalName())
                    .build();
        }

        static MethodSpec getRepeatedBuilder(FieldCodegen ctx, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Builder")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementBuilderType)
                    .addParameter(int.class, "index")
                    .addStatement("return $L.get(index).toBuilder()", ctx.internalName())
                    .build();
        }

        static MethodSpec addRepeatedBuilder(FieldCodegen ctx, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName() + "Builder")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(elementBuilderType)
                    .addStatement("$T builder = $T.newBuilder()", elementBuilderType, ctx.genericType())
                    .addStatement("add$L(builder.buildPartial())", ctx.pascalName())
                    .addStatement("return builder")
                    .build();
        }

        static MethodSpec addRepeatedBuilderAtIndex(FieldCodegen ctx, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName() + "Builder")
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, "index")
                    .returns(elementBuilderType)
                    .addStatement("$T builder = $T.newBuilder()", elementBuilderType, ctx.genericType())
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add(index, builder.buildPartial())", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return builder")
                    .build();
        }

        static MethodSpec getRepeatedBuilderList(FieldCodegen ctx, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "BuilderList")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), elementBuilderType))
                    .addStatement("return $L.stream().map(m -> m.toBuilder()).collect($T.toList())", ctx.internalName(), java.util.stream.Collectors.class)
                    .build();
        }

        static MethodSpec addRepeatedElementAtIndex(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addParameter(ctx.genericType(), "value")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add(index, value)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec setRepeatedBuilder(FieldCodegen ctx, ClassName builderClassName, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("set" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addParameter(elementBuilderType, "builderForValue")
                    .addStatement("return set$L(index, builderForValue.build())", ctx.pascalName())
                    .build();
        }

        static MethodSpec addRepeatedBuilderValue(FieldCodegen ctx, ClassName builderClassName, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(elementBuilderType, "builderForValue")
                    .addStatement("return add$L(builderForValue.build())", ctx.pascalName())
                    .build();
        }

        static MethodSpec addRepeatedBuilderValueAtIndex(FieldCodegen ctx, ClassName builderClassName, ClassName elementBuilderType) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addParameter(elementBuilderType, "builderForValue")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add(index, builderForValue.build())", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec removeRepeatedElement(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("remove" + ctx.pascalName())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.remove(index)", ctx.internalName())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec getRepeatedValueList(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "ValueList")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(List.class, Integer.class))
                    .addStatement("return $L.stream().map(e -> e.getNumber()).collect($T.toList())", ctx.internalName(), Collectors.class)
                    .build();
        }

        static MethodSpec getRepeatedValue(FieldCodegen ctx) {
            return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(int.class)
                    .addParameter(int.class, "index")
                    .addStatement("return $L.get(index).getNumber()", ctx.internalName())
                    .build();
        }

        static MethodSpec setRepeatedValue(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("set" + ctx.pascalName() + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "index")
                    .addParameter(int.class, "value")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.set(index, $T.forNumber(value))", ctx.internalName(), ctx.genericType().box())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec addRepeatedValue(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("add" + ctx.pascalName() + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(int.class, "value")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .addStatement("$L.add($T.forNumber(value))", ctx.internalName(), ctx.genericType().box())
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec addAllRepeatedValue(FieldCodegen ctx, ClassName builderClassName) {
            return MethodSpec.methodBuilder("addAll" + ctx.pascalName() + "Value")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(builderClassName)
                    .addParameter(ParameterizedTypeName.get(ClassName.get(Iterable.class), ClassName.get(Integer.class)), "values")
                    .addStatement("ensure$LIsMutable()", ctx.pascalName())
                    .beginControlFlow("for (Integer value : values)")
                    .addStatement("$L.add($T.forNumber(value))", ctx.internalName(), ctx.genericType().box())
                    .endControlFlow()
                    .addStatement("onChanged()")
                    .addStatement("return this")
                    .build();
        }

        static MethodSpec ensureIsMutable(FieldCodegen ctx) {
            var ensureIsMutable = MethodSpec.methodBuilder("ensure" + ctx.pascalName() + "IsMutable")
                    .addModifiers(Modifier.PRIVATE);
            if (ctx.isString()) {
                ensureIsMutable.beginControlFlow("if (!(($L instanceof $T)))", ctx.internalName(), com.google.protobuf.LazyStringArrayList.class)
                        .addStatement("$L = new $T($L)", ctx.internalName(), com.google.protobuf.LazyStringArrayList.class, ctx.internalName())
                        .endControlFlow();
            } else {
                ensureIsMutable.beginControlFlow("if (!(($L instanceof $T)))", ctx.internalName(), java.util.ArrayList.class)
                        .addStatement("$L = new $T<>($L)", ctx.internalName(), java.util.ArrayList.class, ctx.internalName())
                        .endControlFlow();
            }
            return ensureIsMutable.build();
        }
    }

    /**
     * Methods for generating Service classes
     */
    static class Services {
        static MethodSpec constructor() {
            return MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PROTECTED)
                    .build();
        }
        static TypeSpec generateInterface(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var interfaceBuilder = TypeSpec.interfaceBuilder("Interface")
                    .addModifiers(Modifier.PUBLIC);
            for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
                interfaceBuilder.addMethod(Services.rpcMethod(method, ctx));
            }
            return interfaceBuilder.build();
        }

        public static TypeSpec generateBlockingInterface(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var blockingInterfaceBuilder = TypeSpec.interfaceBuilder("BlockingInterface")
                    .addModifiers(Modifier.PUBLIC);
            for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
                blockingInterfaceBuilder.addMethod(Services.blockingMethod(method, ctx));
            }
            return blockingInterfaceBuilder.build();
        }


        static TypeSpec generateServiceStub(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var stubBuilder = TypeSpec.classBuilder("Stub")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .superclass(ClassName.get("", service.getName()))
                    .addSuperinterface(ClassName.get("", "Interface"));

            stubBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(com.google.protobuf.RpcChannel.class, "channel")
                    .addStatement("this.channel = channel")
                    .build());

            stubBuilder.addField(com.google.protobuf.RpcChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);

            stubBuilder.addMethod(MethodSpec.methodBuilder("getChannel")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(com.google.protobuf.RpcChannel.class)
                    .addStatement("return channel")
                    .build());

            for (var method : service.getMethodList()) {
                var methodName = ProtoUtils.toCamelCase(method.getName());
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

                stubBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(com.google.protobuf.RpcController.class, "controller")
                        .addParameter(inputType, "request")
                        .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                        .addStatement("channel.callMethod(getDescriptor().getMethods().get($L), controller, request, $T.getDefaultInstance(), com.google.protobuf.RpcUtil.generalizeCallback(done, $T.class, $T.getDefaultInstance()))", service.getMethodList().indexOf(method), outputType, outputType, outputType)
                        .build());
            }

            return stubBuilder.build();
        }

        static TypeSpec generateBlockingServiceStub(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var stubBuilder = TypeSpec.classBuilder("BlockingStub")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .addSuperinterface(ClassName.get("", "BlockingInterface"));

            stubBuilder.addMethod(MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(com.google.protobuf.BlockingRpcChannel.class, "channel")
                    .addStatement("this.channel = channel")
                    .build());

            stubBuilder.addField(com.google.protobuf.BlockingRpcChannel.class, "channel", Modifier.PRIVATE, Modifier.FINAL);

            for (var method : service.getMethodList()) {
                var methodName = ProtoUtils.toCamelCase(method.getName());
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

                stubBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(com.google.protobuf.RpcController.class, "controller")
                        .addParameter(inputType, "request")
                        .returns(outputType)
                        .addException(com.google.protobuf.ServiceException.class)
                        .addStatement("return ($T) channel.callBlockingMethod(getDescriptor().getMethods().get($L), controller, request, $T.getDefaultInstance())", outputType, service.getMethodList().indexOf(method), outputType)
                        .build());
            }

            return stubBuilder.build();
        }

        static CodeBlock generateNewReflectiveService(DescriptorProtos.ServiceDescriptorProto service, String serviceName, CodegenContext ctx) {
            var builder = TypeSpec.anonymousClassBuilder("")
                    .superclass(ClassName.get("", serviceName));

            for (var method : service.getMethodList()) {
                var methodName = ProtoUtils.toCamelCase(method.getName());
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

                builder.addMethod(MethodSpec.methodBuilder(methodName)
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(com.google.protobuf.RpcController.class, "controller")
                        .addParameter(inputType, "request")
                        .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                        .addStatement("impl.$L(controller, request, done)", methodName)
                        .build());
            }

            return CodeBlock.builder()
                    .add("return $L;", builder.build())
                    .build();
        }

        static CodeBlock generateNewReflectiveBlockingService(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var builder = TypeSpec.anonymousClassBuilder("")
                    .addSuperinterface(com.google.protobuf.BlockingService.class);

            builder.addMethod(MethodSpec.methodBuilder("getDescriptorForType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(Descriptors.ServiceDescriptor.class)
                    .addStatement("return getDescriptor()")
                    .build());

            var callBlockingMethod = MethodSpec.methodBuilder("callBlockingMethod")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(com.google.protobuf.Message.class, "request")
                    .returns(com.google.protobuf.Message.class)
                    .addException(com.google.protobuf.ServiceException.class);

            callBlockingMethod.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.callBlockingMethod() given method descriptor for wrong service type.")
                    .endControlFlow();

            callBlockingMethod.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var methodName = ProtoUtils.toCamelCase(method.getName());
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());

                callBlockingMethod.beginControlFlow("case $L:", i)
                        .addStatement("return impl.$L(controller, ($T)request)", methodName, inputType)
                        .endControlFlow();
            }
            callBlockingMethod.beginControlFlow("default:")
                    .addStatement("throw new $T($S)", AssertionError.class, "Can't get here.")
                    .endControlFlow();
            callBlockingMethod.endControlFlow();
            builder.addMethod(callBlockingMethod.build());

            var getRequestPrototype = MethodSpec.methodBuilder("getRequestPrototype")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .returns(com.google.protobuf.Message.class);

            getRequestPrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getRequestPrototype() given method descriptor for wrong service type.")
                    .endControlFlow();

            getRequestPrototype.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                getRequestPrototype.addStatement("case $L: return $T.getDefaultInstance()", i, inputType);
            }
            getRequestPrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
            getRequestPrototype.endControlFlow();
            builder.addMethod(getRequestPrototype.build());

            var getResponsePrototype = MethodSpec.methodBuilder("getResponsePrototype")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .returns(com.google.protobuf.Message.class);

            getResponsePrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getResponsePrototype() given method descriptor for wrong service type.")
                    .endControlFlow();

            getResponsePrototype.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());
                getResponsePrototype.addStatement("case $L: return $T.getDefaultInstance()", i, outputType);
            }
            getResponsePrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
            getResponsePrototype.endControlFlow();
            builder.addMethod(getResponsePrototype.build());

            return CodeBlock.builder()
                    .add("return $L;", builder.build())
                    .build();
        }

        static MethodSpec getDescriptor(CodegenContext ctx, DescriptorProtos.ServiceDescriptorProto service) {
            return MethodSpec.methodBuilder("getDescriptor")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .returns(Descriptors.ServiceDescriptor.class)
                    .addStatement("return $T.getDescriptor().getServices().get($L)", ClassName.get(ctx.packageName(), ctx.outerName()), ctx.fileDescriptor().getServiceList().indexOf(service))
                    .build();
        }

        static MethodSpec getDescriptorForType() {
            return MethodSpec.methodBuilder("getDescriptorForType")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .returns(Descriptors.ServiceDescriptor.class)
                    .addStatement("return getDescriptor()")
                    .build();
        }

        static MethodSpec callMethod(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var callMethod = MethodSpec.methodBuilder("callMethod")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(com.google.protobuf.Message.class, "request")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), ClassName.get(com.google.protobuf.Message.class)), "done");

            callMethod.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.callMethod() given method descriptor for wrong service type.")
                    .endControlFlow();

            callMethod.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var methodName = ProtoUtils.toCamelCase(method.getName());
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

                callMethod.beginControlFlow("case $L:", i)
                        .addStatement("this.$L(controller, ($T)request, com.google.protobuf.RpcUtil.<$T>specializeCallback(done))", methodName, inputType, outputType)
                        .addStatement("return")
                        .endControlFlow();
            }
            callMethod.beginControlFlow("default:")
                    .addStatement("throw new $T($S)", AssertionError.class, "Can't get here.")
                    .endControlFlow();
            callMethod.endControlFlow();
            return callMethod.build();
        }

        static MethodSpec getRequestPrototype(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var getRequestPrototype = MethodSpec.methodBuilder("getRequestPrototype")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .returns(com.google.protobuf.Message.class);

            getRequestPrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getRequestPrototype() given method descriptor for wrong service type.")
                    .endControlFlow();

            getRequestPrototype.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
                getRequestPrototype.addStatement("case $L: return $T.getDefaultInstance()", i, inputType);
            }
            getRequestPrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
            getRequestPrototype.endControlFlow();
            return getRequestPrototype.build();
        }

        static MethodSpec getResponsePrototype(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            var getResponsePrototype = MethodSpec.methodBuilder("getResponsePrototype")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                    .addParameter(Descriptors.MethodDescriptor.class, "method")
                    .returns(com.google.protobuf.Message.class);

            getResponsePrototype.beginControlFlow("if (method.getService() != getDescriptor())")
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Service.getResponsePrototype() given method descriptor for wrong service type.")
                    .endControlFlow();

            getResponsePrototype.beginControlFlow("switch(method.getIndex())");
            for (int i = 0; i < service.getMethodCount(); i++) {
                var method = service.getMethod(i);
                var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());
                getResponsePrototype.addStatement("case $L: return $T.getDefaultInstance()", i, outputType);
            }
            getResponsePrototype.addStatement("default: throw new $T($S)", AssertionError.class, "Can't get here.");
            getResponsePrototype.endControlFlow();
            return getResponsePrototype.build();
        }

        static MethodSpec newStub() {
            return MethodSpec.methodBuilder("newStub")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(com.google.protobuf.RpcChannel.class, "channel")
                    .returns(ClassName.get("", "Stub"))
                    .addStatement("return new Stub(channel)")
                    .build();
        }

        static MethodSpec newBlockingStub() {
            return MethodSpec.methodBuilder("newBlockingStub")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(com.google.protobuf.BlockingRpcChannel.class, "channel")
                    .returns(ClassName.get("", "BlockingInterface"))
                    .addStatement("return new BlockingStub(channel)")
                    .build();
        }

        static MethodSpec newReflectiveService(DescriptorProtos.ServiceDescriptorProto service, String serviceName, CodegenContext ctx) {
            return MethodSpec.methodBuilder("newReflectiveService")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(ClassName.get("", "Interface"), "impl")
                    .returns(com.google.protobuf.Service.class)
                    .addCode(generateNewReflectiveService(service, serviceName, ctx))
                    .build();
        }

        static MethodSpec newReflectiveBlockingService(DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
            return MethodSpec.methodBuilder("newReflectiveBlockingService")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(ClassName.get("", "BlockingInterface"), "impl")
                    .returns(com.google.protobuf.BlockingService.class)
                    .addCode(generateNewReflectiveBlockingService(service, ctx))
                    .build();
        }

        static MethodSpec rpcMethod(DescriptorProtos.MethodDescriptorProto method, CodegenContext ctx) {
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            return MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(inputType, "request")
                    .addParameter(ParameterizedTypeName.get(ClassName.get(com.google.protobuf.RpcCallback.class), outputType), "done")
                    .build();
        }

        static MethodSpec blockingMethod(DescriptorProtos.MethodDescriptorProto method, CodegenContext ctx) {
            var methodName = ProtoUtils.toCamelCase(method.getName());
            var inputType = ctx.resolveTypeName(method.getInputType(), new java.util.ArrayList<>());
            var outputType = ctx.resolveTypeName(method.getOutputType(), new java.util.ArrayList<>());

            return MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(com.google.protobuf.RpcController.class, "controller")
                    .addParameter(inputType, "request")
                    .returns(outputType)
                    .addException(com.google.protobuf.ServiceException.class)
                    .build();
        }

    }


}