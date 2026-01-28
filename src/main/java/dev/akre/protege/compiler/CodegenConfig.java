package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import dev.akre.util.Cons;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CodegenConfig {
    CodegenMetadata config();
    Object descriptor();

    default boolean isGenerateDeprecated(Object descriptor) {
        return config().getBoolean(CodegenMetadata.JAVA_GENERATE_DEPRECATED, descriptor);
    }

    default String getPackage() {
        return config().getString(CodegenMetadata.PACKAGE, descriptor()).orElse("");
    }

    default String getJavaPackage() {
        return config().getString(CodegenMetadata.JAVA_PACKAGE, descriptor()).orElseGet(this::getPackage);
    }

    default List<AnnotationSpec> getFieldAnnotations() {
        return config().getList(CodegenMetadata.FIELD_ANNOTATION, descriptor()).stream()
                .map(CodegenUtils::parseAnnotation).toList();
    }

    default List<AnnotationSpec> getClassAnnotations() {
        return config().getList(CodegenMetadata.FIELD_ANNOTATION, descriptor()).stream()
                .map(CodegenUtils::parseAnnotation).toList();
    }

    default DescriptorProtos.DescriptorProto getMessageDescriptor(String name) {
        return (DescriptorProtos.DescriptorProto)config().descriptorMap().get(name);
    }

    default boolean isEnhancedOneof(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ENHANCED_ONEOF, descriptor);
    }

    default boolean isGenerateOneofCase(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ONEOF_CASE, descriptor);
    }

    default Optional<String> getJavaImplements() {
        return config().getString(CodegenMetadata.JAVA_IMPLEMENTS, descriptor());
    }

    default ClassName getMessageSuperclass() {
        return config().getString(CodegenMetadata.JAVA_MESSAGE_SUPERCLASS, descriptor()).map(ClassName::bestGuess).orElseThrow();
    }

    default String getOuterName() {
        return config().getString(CodegenMetadata.OUTER_NAME, descriptor()).orElse("");
    }

    default TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    default TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    default TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        return config().getFieldType(field, currentScope);
    }

    default boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        return config().isMapField(field);
    }

    default DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        return config().getEntryDescriptor(field);
    }

    default String relativeToProtoPackage(String typeName) {
        return config().relativeToProtoPackage(typeName);
    }

    default ClassName getFieldAccessorTableClass() {
        return config().getFieldAccessorTableClass();
    }

    default Map<String, ClassName> typeRegistry() {
        return config().typeRegistry();
    }
}
