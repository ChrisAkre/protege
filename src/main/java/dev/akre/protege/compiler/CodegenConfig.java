package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import dev.akre.util.Cons;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Interface defining access to code generation configuration and metadata.
 * Implementing classes can delegate to {@link CodegenMetadata} for resolution.
 */
public interface CodegenConfig {
    /**
     * @return The configuration metadata.
     */
    CodegenMetadata config();

    /**
     * @return The descriptor object associated with this context.
     */
    Object descriptor();

    /**
     * @param descriptor The descriptor to check.
     * @return True if deprecated annotations should be generated.
     */
    default boolean isGenerateDeprecated(Object descriptor) {
        return config().getBoolean(CodegenMetadata.JAVA_GENERATE_DEPRECATED, descriptor);
    }

    /**
     * @return The protobuf package name.
     */
    default String getPackage() {
        return config().getString(CodegenMetadata.PACKAGE, descriptor()).orElse("");
    }

    /**
     * @return The Java package name.
     */
    default String getJavaPackage() {
        return config().getString(CodegenMetadata.JAVA_PACKAGE, descriptor()).orElseGet(this::getPackage);
    }

    /**
     * @return List of annotations for fields.
     */
    default List<AnnotationSpec> getFieldAnnotations() {
        return config().getList(CodegenMetadata.FIELD_ANNOTATIONS, descriptor()).stream()
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * @return List of annotations for the message class.
     */
    default List<AnnotationSpec> getClassAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                config().getList(CodegenMetadata.CLASS_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * @return List of annotations for the message interface.
     */
    default List<AnnotationSpec> getInterfaceAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.INTERFACE_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * @return List of annotations for the builder class.
     */
    default List<AnnotationSpec> getBuilderAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.BUILDER_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * @param name The fully qualified name of the message.
     * @return The message descriptor.
     */
    default DescriptorProtos.DescriptorProto getMessageDescriptor(String name) {
        return (DescriptorProtos.DescriptorProto)config().descriptorMap().get(name);
    }

    /**
     * @param descriptor The descriptor to check.
     * @return True if enhanced oneof support is enabled.
     */
    default boolean isEnhancedOneof(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ENHANCED_ONEOF, descriptor);
    }

    /**
     * @param descriptor The descriptor to check.
     * @return True if oneof case enum should be generated.
     */
    default boolean isGenerateOneofCase(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ONEOF_CASE, descriptor);
    }

    /**
     * @return Optional interface that the message should implement.
     */
    default Optional<String> getJavaImplements() {
        return config().getString(CodegenMetadata.JAVA_IMPLEMENTS, descriptor());
    }

    /**
     * @return The superclass for the generated message.
     */
    default ClassName getMessageSuperclass() {
        return config().getString(CodegenMetadata.JAVA_MESSAGE_SUPERCLASS, descriptor()).map(ClassName::bestGuess).orElseThrow();
    }

    /**
     * @return The outer class name if configured.
     */
    default String getOuterName() {
        return config().getString(CodegenMetadata.OUTER_NAME, descriptor()).orElse("");
    }

    /**
     * Resolves a protobuf type name to a Java TypeName.
     *
     * @param protoTypeName The protobuf type name.
     * @param currentScope The current scope.
     * @return The resolved TypeName.
     */
    default TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Resolves a protobuf type name to a Java TypeName.
     *
     * @param protoTypeName The protobuf type name.
     * @param currentScope The current scope.
     * @return The resolved TypeName.
     */
    default TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Gets the Java type for a field.
     *
     * @param field The field descriptor.
     * @param currentScope The current scope.
     * @return The TypeName.
     */
    default TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        return config().getFieldType(field, currentScope);
    }

//    default TypeName getFieldType() {
//        return config().getFieldType(descriptor());
//    }

    /**
     * @param field The field descriptor.
     * @return True if the field is a map.
     */
    default boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        return config().isMapField(field);
    }

    /**
     * @param field The field descriptor.
     * @return The descriptor for the map entry.
     */
    default DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        return config().getEntryDescriptor(field);
    }

    /**
     * @param typeName The type name.
     * @return The type name relative to the proto package.
     */
    default String relativeToProtoPackage(String typeName) {
        return config().relativeToProtoPackage(typeName);
    }

    /**
     * @return The ClassName of the field accessor table.
     */
    default ClassName getFieldAccessorTableClass() {
        return config().getFieldAccessorTableClass();
    }

    /**
     * @return The type registry.
     */
    default Map<String, ClassName> typeRegistry() {
        return config().typeRegistry();
    }
}
