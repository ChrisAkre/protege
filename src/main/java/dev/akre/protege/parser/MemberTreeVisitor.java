package dev.akre.protege.parser;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.ProtobufBaseVisitor;
import dev.akre.protege.ProtobufParser;
import dev.akre.util.Cons;

import java.util.*;

/**
 * Visitor that builds a tree of members (messages, enums) from a Protobuf parse tree.
 * <p>
 * This is the first pass of the parsing process. It constructs a symbol table allowing
 * for type resolution in the subsequent {@link ProtobufFileDescriptorVisitor} pass.
 */
public class MemberTreeVisitor extends ProtobufBaseVisitor<MemberTreeVisitor.MemberNode> {

    /**
     * Represents a node in the member tree (e.g., a Message or Enum).
     *
     * @param name     the simple name of the member
     * @param fullName the fully qualified name of the member
     * @param type     the type of the member (MESSAGE or ENUM)
     * @param children map of child members
     */
    public record MemberNode(String name, String fullName, DescriptorProtos.FieldDescriptorProto.Type type, Map<String, MemberNode> children) {
        /**
         * Resolves a type name against the current scope.
         *
         * @param typeName the type name to resolve
         * @param scope    the current scope
         * @return the resolved member node, if found
         */
        public Optional<MemberNode> resolve(String typeName, Cons<String> scope) {
            String[] typeParts = typeName.startsWith(".")
                    ? typeName.substring(1).split("\\.")
                    : typeName.split("\\.");

            if (typeName.startsWith(".")) {
                return resolveRelative(typeParts);
            }

            return resolveInScope(Cons.copyOf(scope.reversed()), typeParts);
        }

        private Optional<MemberNode> resolveInScope(Cons<String> scopeParts, String[] typeParts) {
            if (!scopeParts.isEmpty()) {
                MemberNode next = children.get(scopeParts.head());
                if (next != null) {
                    Optional<MemberNode> result = next.resolveInScope(scopeParts.tail(), typeParts);
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
            return resolveRelative(typeParts);
        }

        private Optional<MemberNode> resolveRelative(String[] parts) {
            MemberNode current = this;
            for (String part : parts) {
                current = current.children().get(part);
                if (current == null) {
                    return Optional.empty();
                }
            }
            return Optional.of(current);
        }
    }

    @Override
    public MemberNode visitProto(ProtobufParser.ProtoContext ctx) {
        String packageName = ctx.packageStatement().isEmpty() ? "" : ctx.packageStatement().getFirst().name.getText();
        MemberNode root = new MemberNode("", "", null, new HashMap<>());
        MemberNode current = root;
        if (!packageName.isEmpty()) {
            for (String part : packageName.split("\\.")) {
                MemberNode next = new MemberNode(part, current.fullName().isEmpty() ? "." + part : current.fullName() + "." + part, null, new HashMap<>());
                current.children().put(part, next);
                current = next;
            }
        }
        final MemberNode packageNode = current;
        ctx.children.forEach(c -> {
            if (c instanceof ProtobufParser.MessageDefContext m) {
                addMessage(m, packageNode);
            } else if (c instanceof ProtobufParser.EnumDefContext e) {
                addEnum(e, packageNode);
            }
        });
        return root;
    }

    private void addMessage(ProtobufParser.MessageDefContext ctx, MemberNode parent) {
        String name = ctx.name.getText();
        String fullName = parent.fullName().isEmpty() ? "." + name : parent.fullName() + "." + name;
        MemberNode node = new MemberNode(name, fullName, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE, new HashMap<>());
        parent.children().put(name, node);
        ctx.children.forEach(c -> {
            if (c instanceof ProtobufParser.MessageDefContext m) {
                addMessage(m, node);
            } else if (c instanceof ProtobufParser.EnumDefContext e) {
                addEnum(e, node);
            }
        });
    }

    private void addEnum(ProtobufParser.EnumDefContext ctx, MemberNode parent) {
        String name = ctx.name.getText();
        String fullName = parent.fullName().isEmpty() ? "." + name : parent.fullName() + "." + name;
        MemberNode node = new MemberNode(name, fullName, DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM, new HashMap<>());
        parent.children().put(name, node);
    }
}
