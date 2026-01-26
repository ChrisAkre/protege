package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.akre.protege.compiler.CodegenContext.JAVA_ENHANCED_ONEOF_OPTION;

public record OneofCodegen(
        DescriptorProtos.OneofDescriptorProto oneof,
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
        return oneof.getName();
    }

    public ClassName getEnumClassName() {
        return messageCodegen.messageClassName().nestedClass(enumName);
    }

    public ClassName getInterfaceClassName() {
        return messageCodegen.messageClassName().nestedClass(pascalName);
    }

    public static void populateOneofInterfaces(CodegenContext ctx, Map<String, List<ClassName>> oneofInterfacesByType, CodegenMetadata config) {
        oneofInterfacesByType.clear();

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            populateOneofInterfaces(message, ctx, Cons.nil(), oneofInterfacesByType, config);
        }
    }

    private static void populateOneofInterfaces(
            DescriptorProtos.DescriptorProto message,
            CodegenContext ctx,
            Cons<String> parentPath,
            Map<String, List<ClassName>> oneofInterfacesByType,
            CodegenMetadata config) {
        boolean enhancedOneof = config.isEnhancedOneof(message);

        var currentPath = parentPath.cons(message.getName());

        if (enhancedOneof) {
            for (int i = 0; i < message.getOneofDeclCount(); i++) {
                var oneof = message.getOneofDecl(i);
                var pascalName = ProtoUtils.toPascalCase(oneof.getName());

                var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).toList();
                var interfaceClassName = ClassName.get(ctx.packageName(), ctx.outerName(), capitalizedPath.toArray(new String[0])).nestedClass(pascalName);

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

        for (var nested : message.getNestedTypeList()) {
            populateOneofInterfaces(nested, ctx, currentPath, oneofInterfacesByType, config);
        }
    }
}
