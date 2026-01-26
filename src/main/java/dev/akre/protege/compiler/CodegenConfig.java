package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import java.util.List;

public interface CodegenConfig {
    CodegenMetadata config();

    default boolean isGenerateDeprecated(Object descriptor) {
        return config().getBoolean(CodegenMetadata.JAVA_GENERATE_DEPRECATED, descriptor);
    }

    default String getPackage(Object descriptor) {
        return config().getString(CodegenMetadata.PACKAGE, descriptor);
    }

    default String getJavaPackage(Object descriptor) {
        return config().getString(CodegenMetadata.JAVA_PACKAGE, descriptor);
    }

    default List<String> getFieldAnnotations(Object descriptor) {
        return config().getList(CodegenMetadata.FIELD_ANNOTATIONS, descriptor);
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
}
