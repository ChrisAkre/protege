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
 * Configuration interface that allows codegen classes to access metadata and resolution logic.
 * <p>
 * This interface provides default methods that delegate to the underlying {@link CodegenMetadata},
 * making it easy for classes like {@code MessageCodegen} to access configuration options
 * scoped to their specific descriptor.
 */
public interface CodegenConfig {

    /**
     * Returns the underlying metadata configuration.
     *
     * @return The CodegenMetadata instance containing configuration options and type information.
     */
    CodegenMetadata config();

    /**
     * Returns the Protobuf descriptor associated with this configuration scope.
     *
     * @param <T> The specific type of the descriptor (e.g., DescriptorProto, EnumDescriptorProto).
     * @return The descriptor object.
     */
    <T> T descriptor();

    /**
     * Checks if the {@code @Deprecated} annotation should be generated for the given descriptor.
     *
     * @param descriptor The descriptor to check (Message, Field, Enum, etc.).
     * @return True if the element should be marked deprecated, false otherwise.
     */
    default boolean isGenerateDeprecated(Object descriptor) {
        return config().getBoolean(CodegenMetadata.JAVA_GENERATE_DEPRECATED, descriptor);
    }

    /**
     * Returns the proto package name defined in the file options.
     *
     * @return The package name declared in the .proto file, or an empty string if not defined.
     */
    default String getPackage() {
        return config().getString(CodegenMetadata.PACKAGE, descriptor()).orElse("");
    }

    /**
     * Returns the Java package name, defaulting to the proto package if not specified.
     *
     * @return The target Java package for generated classes.
     */
    default String getJavaPackage() {
        return config().getString(CodegenMetadata.JAVA_PACKAGE, descriptor()).orElseGet(this::getPackage);
    }

    /**
     * Returns annotations to be applied to generated fields.
     *
     * @return A list of annotation specs to apply to fields.
     */
    default List<AnnotationSpec> getFieldAnnotations() {
        return config().getList(CodegenMetadata.FIELD_ANNOTATIONS, descriptor()).stream()
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated class.
     *
     * @return A list of annotation specs to apply to the class.
     */
    default List<AnnotationSpec> getClassAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                config().getList(CodegenMetadata.CLASS_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated interface.
     *
     * @return A list of annotation specs to apply to the interface.
     */
    default List<AnnotationSpec> getInterfaceAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.INTERFACE_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated builder.
     *
     * @return A list of annotation specs to apply to the builder.
     */
    default List<AnnotationSpec> getBuilderAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.BUILDER_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Retrieves the descriptor for a message by its fully qualified name.
     *
     * @param name The fully qualified name of the message.
     * @return The descriptor for the specified message.
     */
    default DescriptorProtos.DescriptorProto getMessageDescriptor(String name) {
        return (DescriptorProtos.DescriptorProto)config().descriptorMap().get(name);
    }

    /**
     * Checks if enhanced sealed-interface style code should be generated for oneofs.
     *
     * @param descriptor The descriptor to check configuration for.
     * @return True if enhanced oneof generation is enabled.
     */
    default boolean isEnhancedOneof(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ENHANCED_ONEOF, descriptor);
    }

    /**
     * Checks if the legacy Case enum should be generated for oneofs.
     *
     * @param descriptor The descriptor to check configuration for.
     * @return True if the legacy Case enum should be generated.
     */
    default boolean isGenerateOneofCase(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ONEOF_CASE, descriptor);
    }

    /**
     * Returns the custom interface that the generated message should implement, if any.
     *
     * @return An Optional containing the fully qualified interface name, or empty if none.
     */
    default Optional<String> getJavaImplements() {
        return config().getString(CodegenMetadata.JAVA_IMPLEMENTS, descriptor());
    }

    /**
     * Returns the superclass for the generated message, defaulting to GeneratedMessage.
     *
     * @return The ClassName of the superclass.
     */
    default ClassName getMessageSuperclass() {
        return config().getString(CodegenMetadata.JAVA_MESSAGE_SUPERCLASS, descriptor()).map(ClassName::bestGuess).orElseThrow();
    }

    /**
     * Returns the outer class name if one is defined in the file options.
     *
     * @return The outer class name, or an empty string if not defined.
     */
    default String getOuterName() {
        return config().getString(CodegenMetadata.OUTER_NAME, descriptor()).orElse("");
    }

    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName The Protobuf type name (e.g., ".google.protobuf.StringValue").
     * @param currentScope  The current lexical scope (list of package/message names).
     * @return The resolved Java TypeName.
     */
    default TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName The Protobuf type name.
     * @param currentScope  The current lexical scope (Cons list).
     * @return The resolved Java TypeName.
     */
    default TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Determines the Java type for a given Protobuf field.
     *
     * @param field        The field descriptor.
     * @param currentScope The current scope.
     * @return The Java TypeName for the field.
     */
    default TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        return config().getFieldType(field, currentScope);
    }

    /**
     * Checks if a field is a map field.
     *
     * @param field The field descriptor.
     * @return True if the field is a map, false otherwise.
     */
    default boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        return config().isMapField(field);
    }

    /**
     * Retrieves the descriptor for the map entry message associated with a map field.
     *
     * @param field The field descriptor.
     * @return The descriptor for the synthetic map entry message.
     */
    default DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        return config().getEntryDescriptor(field);
    }

    /**
     * Relativizes a type name against the current proto package.
     *
     * @param typeName The fully qualified type name.
     * @return The type name relative to the current package.
     */
    default String relativeToProtoPackage(String typeName) {
        return config().relativeToProtoPackage(typeName);
    }

    /**
     * Returns the class name for the FieldAccessorTable.
     *
     * @return The ClassName of the FieldAccessorTable.
     */
    default ClassName getFieldAccessorTableClass() {
        return config().getFieldAccessorTableClass();
    }

    /**
     * Returns the registry of all known types in the file.
     *
     * @return A map mapping relative Protobuf type names to their generated Java ClassNames.
     */
    default Map<String, ClassName> typeRegistry() {
        return config().typeRegistry();
    }
}
