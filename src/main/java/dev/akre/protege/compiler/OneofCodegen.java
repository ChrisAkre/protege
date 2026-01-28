package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Context for generating code for a oneof group.
 * <p>
 * This record holds the metadata required to generate the sealed interfaces, case enums,
 * and pattern matching logic used by the enhanced oneof implementation.
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
     * @param oneofIndex     The index of the oneof in the message.
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
     * Returns the name of the oneof field.
     *
     * @return The name of the oneof field.
     */
    public String oneofName() {
        return descriptor.getName();
    }

    /**
     * Returns the class name for the generated case enum.
     *
     * @return The case enum class name.
     */
    public ClassName getEnumClassName() {
        return messageCodegen.messageClassName().nestedClass(enumName);
    }

    /**
     * Returns the class name for the generated sealed interface.
     *
     * @return The sealed interface class name.
     */
    public ClassName getInterfaceClassName() {
        return messageCodegen.messageClassName().nestedClass(pascalName);
    }

    /**
     * Populates the map of oneof interfaces by type for the given context.
     * <p>
     * This method recursively traverses the descriptor tree to find all enhanced oneof fields
     * and maps the field types to their corresponding oneof interface class names.
     *
     * @param context               The outer class codegen context.
     * @param ctx                   The global codegen context.
     * @param oneofInterfacesByType The map to populate.
     * @param config                The codegen metadata.
     */
    public static void populateOneofInterfaces(OuterClassCodegen context, CodegenContext ctx, Map<String, List<ClassName>> oneofInterfacesByType, CodegenMetadata config) {
        oneofInterfacesByType.clear();

        ProtoUtils.descriptorChildren(context.descriptor(), DescriptorProtos.DescriptorProto.class)
                .forEach(message -> populateOneofInterfaces(context, message, ctx, Cons.nil(), oneofInterfacesByType));
    }

    private static void populateOneofInterfaces(
            OuterClassCodegen context,
            DescriptorProtos.DescriptorProto message,
            CodegenContext ctx,
            Cons<String> parentPath,
            Map<String, List<ClassName>> oneofInterfacesByType) {

        var currentPath = parentPath.cons(message.getName());

            for (int i = 0; i < message.getOneofDeclCount(); i++) {

                var oneof = message.getOneofDecl(i);
                if (!context.isEnhancedOneof(oneof)) {
                    continue;
                }
                var pascalName = ProtoUtils.toPascalCase(oneof.getName());

                var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).toList();
                var interfaceClassName = ClassName.get(context.getJavaPackage(), context.getOuterName(), capitalizedPath.toArray(new String[0])).nestedClass(pascalName);

                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = ctx.relativeToProtoPackage(typeName);
                        if (!ctx.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }

                        TypeName typeNameRes = ctx.resolveTypeName(field.getTypeName(), currentPath);
                        if (typeNameRes instanceof ClassName cn) {
                            oneofInterfacesByType.computeIfAbsent(cn.canonicalName(), k -> new ArrayList<>()).add(interfaceClassName);
                        }
                    }
                }
        }
    }
}
