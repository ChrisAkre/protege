package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;

import java.util.List;
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
}
