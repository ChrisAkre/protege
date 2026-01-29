package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import dev.akre.protege.ProtoUtils;

/**
 * Context for generating code for a oneof group.
 * <p>
 * This record holds the metadata required to generate the sealed interfaces, case enums,
 * and pattern matching logic used by the enhanced oneof implementation.
 *
 * @param descriptor The oneof descriptor.
 * @param oneofIndex The index of the oneof in the message.
 * @param pascalName The PascalCase name of the oneof.
 * @param enumName The name of the case enum.
 * @param messageCodegen The parent message codegen context.
 * @param config The configuration metadata.
 */
public record OneofCodegen(
        DescriptorProtos.OneofDescriptorProto descriptor,
        int oneofIndex,
        String pascalName,
        String enumName,
        MessageCodegen messageCodegen,
        CodegenMetadata config
) implements CodegenConfig {
    public static OneofCodegen create(
            DescriptorProtos.OneofDescriptorProto oneof,
            int oneofIndex,
            MessageCodegen messageCodegen
    ) {
        String pascalName = ProtoUtils.toPascalCase(oneof.getName());
        String enumName = pascalName + "Case";
        return new OneofCodegen(oneof, oneofIndex, pascalName, enumName, messageCodegen, messageCodegen.config());
    }

    public String oneofName() {
        return descriptor.getName();
    }

    public ClassName getEnumClassName() {
        return messageCodegen.messageClassName().nestedClass(enumName);
    }

    public ClassName getInterfaceClassName() {
        return messageCodegen.messageClassName().nestedClass(pascalName);
    }
}
