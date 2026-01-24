package dev.akre.protege.parser;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.ProtobufBaseVisitor;
import dev.akre.protege.ProtobufParser;
import dev.akre.util.Cons;

import java.util.*;

class MemberTreeVisitor extends ProtobufBaseVisitor<MemberTreeVisitor.MemberNode> {

    public record MemberNode(String name, String fullName, DescriptorProtos.FieldDescriptorProto.Type type, Map<String, MemberNode> children) {
        public Optional<MemberNode> resolve(String typeName, Cons<String> scope) {
            String[] typeParts = typeName.startsWith(".")
                    ? typeName.substring(1).split("\\.")
                    : typeName.split("\\.");

            if (typeName.startsWith(".")) {
                return resolveRelative(typeParts);
            }

            List<String> scopeList = new ArrayList<>();
            scope.forEach(scopeList::add);
            Collections.reverse(scopeList);

            return resolveInScope(scopeList, 0, typeParts);
        }

        private Optional<MemberNode> resolveInScope(List<String> scopeParts, int index, String[] typeParts) {
            if (index < scopeParts.size()) {
                MemberNode next = children.get(scopeParts.get(index));
                if (next != null) {
                    Optional<MemberNode> result = next.resolveInScope(scopeParts, index + 1, typeParts);
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
                if (current == null) return Optional.empty();
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
