package dev.akre.protege.parser;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.ProtobufBaseVisitor;
import dev.akre.protege.ProtobufParser;
import dev.akre.util.Cons;

import java.util.*;

/**
 * A visitor that builds a symbol table of all declared members (Messages, Enums) in the Protobuf file.
 * <p>
 * This visitor performs the first pass of the parsing process. It traverses the parse tree to construct
 * a hierarchical tree of {@link MemberNode} objects. This symbol table is then used by the
 * {@link ProtobufFileDescriptorVisitor} (the second pass) to resolve type names and dependencies.
 */
public class MemberTreeVisitor extends ProtobufBaseVisitor<MemberTreeVisitor.MemberNode> {

    public record MemberNode(String name, String fullName, DescriptorProtos.FieldDescriptorProto.Type type, Map<String, MemberNode> children) {
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
