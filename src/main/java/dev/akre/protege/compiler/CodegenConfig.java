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

    /** Returns the underlying metadata configuration. */
    CodegenMetadata config();

    /** Returns the Protobuf descriptor associated with this configuration scope. */
    <T> T descriptor();

    /**
     * Checks if the {@code @Deprecated} annotation should be generated for the given descriptor.
     */
    default boolean isGenerateDeprecated(Object descriptor) {
        return config().getBoolean(CodegenMetadata.JAVA_GENERATE_DEPRECATED, descriptor);
    }

    /** Returns the proto package name defined in the file options. */
    default String getPackage() {
        return config().getString(CodegenMetadata.PACKAGE, descriptor()).orElse("");
    }

    /** Returns the Java package name, defaulting to the proto package if not specified. */
    default String getJavaPackage() {
        return config().getString(CodegenMetadata.JAVA_PACKAGE, descriptor()).orElseGet(this::getPackage);
    }

    /** Returns annotations to be applied to generated fields. */
    default List<AnnotationSpec> getFieldAnnotations() {
        return config().getList(CodegenMetadata.FIELD_ANNOTATIONS, descriptor()).stream()
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated class.
     *
     * @return the list of class annotations
     */
    default List<AnnotationSpec> getClassAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                config().getList(CodegenMetadata.CLASS_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated interface.
     *
     * @return the list of interface annotations
     */
    default List<AnnotationSpec> getInterfaceAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.INTERFACE_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Returns annotations to be applied to the generated builder.
     *
     * @return the list of builder annotations
     */
    default List<AnnotationSpec> getBuilderAnnotations() {
        return Stream.concat(config().getList(CodegenMetadata.MESSAGE_ANNOTATIONS, descriptor()).stream(),
                        config().getList(CodegenMetadata.BUILDER_ANNOTATIONS, descriptor()).stream())
                .map(CodegenUtils::parseAnnotation).toList();
    }

    /**
     * Retrieves the descriptor for a message by its fully qualified name.
     *
     * @param name the fully qualified message name
     * @return the message descriptor
     */
    default DescriptorProtos.DescriptorProto getMessageDescriptor(String name) {
        return (DescriptorProtos.DescriptorProto)config().descriptorMap().get(name);
    }

    /**
     * Checks if enhanced sealed-interface style code should be generated for oneofs.
     *
     * @param descriptor the object descriptor
     * @return true if enhanced oneof is enabled
     */
    default boolean isEnhancedOneof(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ENHANCED_ONEOF, descriptor);
    }

    /**
     * Checks if the legacy Case enum should be generated for oneofs.
     *
     * @param descriptor the object descriptor
     * @return true if legacy case generation is enabled
     */
    default boolean isGenerateOneofCase(Object descriptor) {
        return config().getBoolean(CodegenMetadata.ONEOF_CASE, descriptor);
    }

    /**
     * Returns the custom interface that the generated message should implement, if any.
     *
     * @return an optional containing the fully qualified interface name
     */
    default Optional<String> getJavaImplements() {
        return config().getString(CodegenMetadata.JAVA_IMPLEMENTS, descriptor());
    }

    /**
     * Returns the superclass for the generated message, defaulting to GeneratedMessage.
     *
     * @return the superclass ClassName
     */
    default ClassName getMessageSuperclass() {
        return config().getString(CodegenMetadata.JAVA_MESSAGE_SUPERCLASS, descriptor()).map(ClassName::bestGuess).orElseThrow();
    }

    /**
     * Returns the outer class name if one is defined in the file options.
     *
     * @return the outer class name
     */
    default String getOuterName() {
        return config().getString(CodegenMetadata.OUTER_NAME, descriptor()).orElse("");
    }

    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName the protobuf type name
     * @param currentScope the current scope list
     * @return the resolved TypeName
     */
    default TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName the protobuf type name
     * @param currentScope the current scope cons list
     * @return the resolved TypeName
     */
    default TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        return config().resolveTypeName(protoTypeName, currentScope);
    }

    /**
     * Determines the Java type for a given Protobuf field.
     *
     * @param field the field descriptor
     * @param currentScope the current scope list
     * @return the Java type name
     */
    default TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        return config().getFieldType(field, currentScope);
    }

    /**
     * Checks if a field is a map field.
     *
     * @param field the field descriptor
     * @return true if the field is a map field
     */
    default boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        return config().isMapField(field);
    }

    /**
     * Retrieves the descriptor for the map entry message associated with a map field.
     *
     * @param field the field descriptor
     * @return the entry descriptor
     */
    default DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        return config().getEntryDescriptor(field);
    }

    /**
     * Relativizes a type name against the current proto package.
     *
     * @param typeName the type name
     * @return the relativized type name
     */
    default String relativeToProtoPackage(String typeName) {
        return config().relativeToProtoPackage(typeName);
    }

    /**
     * Returns the class name for the FieldAccessorTable.
     *
     * @return the FieldAccessorTable ClassName
     */
    default ClassName getFieldAccessorTableClass() {
        return config().getFieldAccessorTableClass();
    }

    /**
     * Returns the registry of all known types in the file.
     *
     * @return the type registry map
     */
    default Map<String, ClassName> typeRegistry() {
        return config().typeRegistry();
    }

    /**
     * Configuration specialized for message contexts.
     */
    interface MessageConfig extends CodegenConfig {

//        default Iterable<DescriptorProtos.FieldDescriptorProto> mapFields() {
//            return () -> this.<DescriptorProtos.DescriptorProto>descriptor().getFieldList().stream().filter(f -> f.getOptions());
//        }
    }

}
