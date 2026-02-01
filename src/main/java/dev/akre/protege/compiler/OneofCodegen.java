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
 * @param descriptor     The Protobuf oneof descriptor.
 * @param oneofIndex     The index of this oneof in the message.
 * @param pascalName     The PascalCase name of the oneof field.
 * @param enumName       The name of the generated Case enum.
 * @param messageCodegen The parent message codegen context.
 * @param config         The codegen configuration.
 */
public record OneofCodegen(
        DescriptorProtos.OneofDescriptorProto descriptor,
        int oneofIndex,
        String pascalName,
        String enumName,
        MessageCodegen messageCodegen,
        CodegenMetadata config
) implements CodegenConfig {
    /**
     * Creates a new OneofCodegen instance.
     *
     * @param oneof          The oneof descriptor.
     * @param oneofIndex     The index of the oneof.
     * @param messageCodegen The parent message codegen context.
     * @return A new OneofCodegen instance.
     */
    public static OneofCodegen create(
            DescriptorProtos.OneofDescriptorProto oneof,
            int oneofIndex,
            MessageCodegen messageCodegen
    ) {
        String pascalName = ProtoUtils.toPascalCase(oneof.getName());
        String enumName = pascalName + "Case";
        return new OneofCodegen(oneof, oneofIndex, pascalName, enumName, messageCodegen, messageCodegen.config());
    }

    /**
     * Returns the name of the oneof field in the proto definition.
     *
     * @return The oneof name.
     */
    public String oneofName() {
        return descriptor.getName();
    }

    /**
     * Returns the ClassName for the generated Case enum.
     *
     * @return The Case enum ClassName.
     */
    public ClassName getEnumClassName() {
        return messageCodegen.messageClassName().nestedClass(enumName);
    }

    /**
     * Returns the ClassName for the generated sealed interface.
     *
     * @return The sealed interface ClassName.
     */
    public ClassName getInterfaceClassName() {
        return messageCodegen.messageClassName().nestedClass(pascalName);
    }
}
