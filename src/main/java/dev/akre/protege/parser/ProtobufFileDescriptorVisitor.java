package dev.akre.protege.parser;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.*;
import dev.akre.protege.ProtoUtils;
import dev.akre.protege.ProtobufBaseVisitor;
import dev.akre.protege.ProtobufParser;
import dev.akre.protege.parser.MemberTreeVisitor.MemberNode;
import dev.akre.util.Cons;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.akre.protege.ProtoUtils.toPascalCase;

/**
 * Visitor that creates a FileDescriptorProto.Builder from an ANTLR parse tree
 */
public class ProtobufFileDescriptorVisitor extends ProtobufBaseVisitor<Object> {

    private final MemberNode root;
    private Cons<String> scope = Cons.nil();


    // class is not threadsafe or reusable
    private ProtobufFileDescriptorVisitor(MemberNode root) {
        this.root = root;
    }

    public static FileDescriptorProto.Builder parseProto(String filename, ProtobufParser.ProtoContext ctx) {
        try {
            MemberNode root = new MemberTreeVisitor().visitProto(ctx);
            return new ProtobufFileDescriptorVisitor(root).visitProto(ctx);
        } catch (TypeNotFoundException e) {
            throw e.in(filename);
        }
    }

    public interface FileOption {
        void set(FileOptions.Builder options);

        static FileOption javaPackage(String value) {
            return options -> options.setJavaPackage(value);
        }

        static FileOption javaOuterClass(String value) {
            return options -> options.setJavaOuterClassname(value);
        }

        static FileOption javaGenericServices(boolean value) {
            return options -> options.setJavaGenericServices(value);
        }
    }
    public record Syntax(String version) {}
    public record Package(String name) {}
    public record Import(String path, boolean weak, boolean _public) {
        public void addDependency(FileDescriptorProto.Builder fileBuilder) {
            int last = fileBuilder.getDependencyCount();
            fileBuilder.addDependency(path());
            if (weak()) {
                fileBuilder.addWeakDependency(last);
            } else if (_public()) {
                fileBuilder.addPublicDependency(last);
            }
        }
    }
    public record Identifier(String name) {}
    public record MapField(FieldDescriptorProto.Builder field, DescriptorProto entry) {}
    public record Oneof(OneofDescriptorProto.Builder descriptor, List<FieldDescriptorProto.Builder> fields) {
        public void addTo(DescriptorProto.Builder messageBuilder) {
            int oneofIndex = messageBuilder.getOneofDeclCount();
            messageBuilder.addOneofDecl(descriptor);
            fields.forEach(f -> {
                f.setOneofIndex(oneofIndex);
                messageBuilder.addField(f);
            });
        }
    }

    /**
     * Generates a {@link FileDescriptorProto} by first reading file-level options and then recursively descending through the AST.
     * <p>
     * This method maintains a {@code scope} stack ({@link #scope}) which is updated during traversal to resolve the full
     * qualified name of the object being processed (e.g., nested messages or enums).
     * <p>
     * Each visit operation returns a descriptor (e.g., {@link DescriptorProto}) or a configuration record that is merged
     * into the main file descriptor builder.
     *
     * @param ctx The root context of the Protobuf file parse tree.
     * @return A builder for the {@link FileDescriptorProto} representing the parsed file.
     */
    @Override
    public FileDescriptorProto.Builder visitProto(ProtobufParser.ProtoContext ctx) {
        String packageName = ctx.packageStatement().isEmpty()
                ? ""
                : ctx.packageStatement().getFirst().name.getText();
        this.scope = Cons.of(packageName.split("\\."));
        FileDescriptorProto.Builder fileBuilder = FileDescriptorProto.newBuilder();
        var fileOptions = FileOptions.newBuilder();
        // configure scope prior to walking the tree to allow creating full type names

        ctx.children.stream().map(this::visit).forEach(ret -> {
            switch (ret) {
                case Syntax s -> fileBuilder.setSyntax(s.version());
                case Package p -> fileBuilder.setPackage(p.name());
                case FileOption o -> o.set(fileOptions);
                case Import i -> i.addDependency(fileBuilder);
                case DescriptorProto.Builder m -> fileBuilder.addMessageType(m);
                case EnumDescriptorProto.Builder e -> fileBuilder.addEnumType(e);
                case ServiceDescriptorProto.Builder s -> fileBuilder.addService(s);
                case UninterpretedOption.Builder o -> fileOptions.addUninterpretedOption(o);
                case null -> {} // EOF
                default -> throw new IllegalStateException("unexpected: " + ret);
            }
        });
        fileBuilder.setOptions(fileOptions.build());
        return fileBuilder;
    }

    private static <T> Optional<T> exactlyOne(List<T> l, String name) {
        return Optional.of(l).map(ignored -> {
            if (l.size() == 1) {
                return l.getFirst();
            } else {
                throw new IllegalArgumentException("expected exactly one %s, but found: %s".formatted(name, l.size()));
            }
        });
    }

    @Override
    public Syntax visitSyntax(ProtobufParser.SyntaxContext ctx) {
        try {
            return new Syntax(ProtoUtils.getStringLiteral(ctx.protoVersion().getText()));
        } catch (NullPointerException e) {
            throw new InvalidProtoException("missing syntax declaration");
        }
    }

    @Override
    public Package visitPackageStatement(ProtobufParser.PackageStatementContext ctx) {
        return new Package(ctx.name.getText());
    }

    @Override
    public Import visitImportStatement(ProtobufParser.ImportStatementContext ctx) {
        return new Import(
                ProtoUtils.getStringLiteral(ctx.strLit().getText()),
                ctx.WEAK() != null,
                ctx.PUBLIC() != null
        );
    }

    @Override
    public Object visitOptionDecl(ProtobufParser.OptionDeclContext ctx) {
        return switch (ctx.option().optionName().getText()) {
            case "java_package" -> FileOption.javaPackage(ProtoUtils.getStringLiteral(ctx.option().constant().getText()));
            case "java_outer_classname" -> FileOption.javaOuterClass(ProtoUtils.getStringLiteral(ctx.option().constant().getText()));
            case "java_generic_services" -> FileOption.javaGenericServices(Boolean.parseBoolean(ctx.option().constant().getText()));
            default -> visitOption(ctx.option());
        };
    }

    @Override
    public UninterpretedOption.Builder visitOption(ProtobufParser.OptionContext ctx) {
        UninterpretedOption.Builder result = UninterpretedOption.newBuilder()
                .addAllName(visitOptionName(ctx.optionName()));

        switch (visitConstant(ctx.constant())) {
            case Long l when l >= 0 -> result.setPositiveIntValue(l);
            case Long l -> result.setNegativeIntValue(-l);
            case Double d -> result.setDoubleValue(d);
            case ByteString s -> result.setStringValue(s);
            case Identifier id -> result.setIdentifierValue(id.name());
            default -> throw new IllegalStateException();
        }

        return result;
    }

    /**
     * Pushes the message name onto the scope stack to handle nested type resolution.
     */
    @Override
    public DescriptorProto.Builder visitMessageDef(ProtobufParser.MessageDefContext ctx) {
        String messageName = ctx.name.getText();
        scope = scope.cons(messageName);

        DescriptorProto.Builder messageBuilder = DescriptorProto.newBuilder()
                .setName(messageName);
        MessageOptions.Builder messageOptions = MessageOptions.newBuilder();
        ctx.children.forEach(child -> {
            switch (child) {
                case ProtobufParser.OptionDeclContext o -> messageOptions.addUninterpretedOption(
                        visitOption(o.option()));
                case ProtobufParser.FieldContext f ->
                        messageBuilder.addField(visitField(f));
                case ProtobufParser.MessageDefContext m ->
                        messageBuilder.addNestedType(visitMessageDef(m));
                case ProtobufParser.EnumDefContext e ->
                        messageBuilder.addEnumType(visitEnumDef(e));
                case ProtobufParser.OneofContext o ->
                        visitOneof(o).addTo(messageBuilder);
                case ProtobufParser.MapFieldContext m -> {
                        MapField mf = visitMapField(m);
                        messageBuilder.addField(mf.field());
                        messageBuilder.addNestedType(mf.entry());
                }
                case ProtobufParser.ReservedContext r ->
                        visitReserved(r, messageBuilder);
                default -> {}
            }
        });
        if (messageOptions.getUninterpretedOptionCount() > 0) {
            messageBuilder.setOptions(messageOptions.build());
        }
        scope = scope.tail();
        return messageBuilder;
    }

    @Override
    public MapField visitMapField(ProtobufParser.MapFieldContext ctx) {
        String entryName = toPascalCase(ctx.mapName().getText()) + "Entry";

        // Key type
        FieldDescriptorProto.Type keyType = ProtoUtils.fieldTypeForName(ctx.keyType().getText());
        FieldDescriptorProto.Builder keyField = FieldDescriptorProto.newBuilder()
                .setName("key")
                .setNumber(1)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .setType(keyType);

        // Value type
        FieldDescriptorProto.Builder valueField = FieldDescriptorProto.newBuilder()
                .setName("value")
                .setNumber(2)
                .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
        setFieldTypeName(ctx.type_(), valueField);


        DescriptorProto entry = DescriptorProto.newBuilder()
                .setName(entryName)
                .setOptions(MessageOptions.newBuilder().setMapEntry(true))
                .addField(keyField)
                .addField(valueField)
                .build();

        FieldDescriptorProto.Builder field = FieldDescriptorProto.newBuilder()
                .setName(ctx.mapName().getText())
                .setNumber(Integer.parseInt(ctx.fieldNumber().getText()))
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(ProtoUtils.qualify(entryName, scope));

        if (ctx.fieldOptions() != null) {
            var options = FieldOptions.newBuilder();
            ctx.fieldOptions().option().stream()
                    .map(this::visitOption)
                    .forEach(options::addUninterpretedOption);
            field.setOptions(options);
        }

        return new MapField(field, entry);
    }

    @Override
    public Oneof visitOneof(ProtobufParser.OneofContext ctx) {
        OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder()
                .setName(ctx.oneofName().getText());

        List<FieldDescriptorProto.Builder> fields = ctx.oneofField().stream().map(fieldCtx -> {
            FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                    .setName(fieldCtx.fieldName().getText())
                    .setNumber(visitIntLit(fieldCtx.fieldNumber().intLit()))
                    .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
            setFieldTypeName(fieldCtx.type_(), fieldBuilder);
            return fieldBuilder;
        }).toList();

        return new Oneof(oneofBuilder, fields);
    }


    @Override
    public Integer visitIntLit(ProtobufParser.IntLitContext ctx) {
        return Integer.parseInt(ctx.getText());
    }

    @Override
    public FieldDescriptorProto.Builder visitField(ProtobufParser.FieldContext ctx) {
        FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                .setName(ctx.fieldName().getText())
                .setNumber(Integer.parseInt(ctx.fieldNumber().getText()))
                .setLabel(visitLabel(ctx.label()));

        setFieldTypeName(ctx.type_(), fieldBuilder);

        if (ctx.fieldOptions() != null) {
            var options = FieldOptions.newBuilder();
            ctx.fieldOptions().option().stream()
                    .map(this::visitOption)
                    .forEach(options::addUninterpretedOption);
            fieldBuilder.setOptions(options);
        }

        return fieldBuilder;
    }


    @Override
    public FieldDescriptorProto.Label visitLabel(ProtobufParser.LabelContext ctx) {
        if (ctx.REQUIRED() != null) {
            return FieldDescriptorProto.Label.LABEL_REQUIRED;
        } else if (ctx.REPEATED() != null) {
            return FieldDescriptorProto.Label.LABEL_REPEATED;
        } else {
            // default is OPTIONAL
            return FieldDescriptorProto.Label.LABEL_OPTIONAL;
        }
    }

    @Override
    public EnumDescriptorProto.Builder visitEnumDef(ProtobufParser.EnumDefContext ctx) {
        EnumDescriptorProto.Builder enumBuilder = EnumDescriptorProto.newBuilder()
                .setName(ctx.name.getText());

        ctx.enumBody().enumField().forEach(fieldCtx -> {
            int number = visitIntLit(fieldCtx.intLit());

            enumBuilder.addValue(
                    EnumValueDescriptorProto.newBuilder()
                            .setName(fieldCtx.ident().getText())
                            .setNumber(number)
            );
        });

        return enumBuilder;
    }

    @Override
    public ServiceDescriptorProto.Builder visitServiceDef(ProtobufParser.ServiceDefContext ctx) {
        ServiceDescriptorProto.Builder serviceBuilder = ServiceDescriptorProto.newBuilder()
                .setName(ctx.serviceName().getText());
        ctx.rpc().stream().map(this::visitRpc).forEach(serviceBuilder::addMethod);
        return serviceBuilder;
    }

    @Override
    public MethodDescriptorProto.Builder visitRpc(ProtobufParser.RpcContext rpcCtx) {
        MethodDescriptorProto.Builder methodBuilder = MethodDescriptorProto.newBuilder()
                .setName(rpcCtx.rpcName().getText());

        if (rpcCtx.clientStream != null) {
            methodBuilder.setClientStreaming(true);
        }

        if (rpcCtx.serverStream != null) {
            methodBuilder.setServerStreaming(true);
        }

        methodBuilder.setInputType(root.resolve(rpcCtx.clientType.getText(), scope)
                .map(MemberNode::fullName)
                .orElse(rpcCtx.clientType.getText()));

        methodBuilder.setOutputType(root.resolve(rpcCtx.serverType.getText(), scope)
                .map(MemberNode::fullName)
                .orElse(rpcCtx.serverType.getText()));

        return methodBuilder;
    }

    private void visitReserved(ProtobufParser.ReservedContext ctx,
                               DescriptorProto.Builder messageBuilder) {
        switch (ctx) {
            case ProtobufParser.ReservedContext c when c.ranges() != null ->
                    c.ranges().range().forEach(rangeCtx -> {
                        int start = visitIntLit(rangeCtx.intLit(0));
                        int end = switch (rangeCtx) {
                            case ProtobufParser.RangeContext r when r.TO() == null -> start + 1;
                            case ProtobufParser.RangeContext r when r.MAX() != null -> 536870911;
                            default -> visitIntLit(rangeCtx.intLit(1));
                        };

                        messageBuilder.addReservedRange(
                                DescriptorProto.ReservedRange.newBuilder()
                                        .setStart(start)
                                        .setEnd(end)
                        );
                    });
            case ProtobufParser.ReservedContext c when c.fieldNames() != null ->
                    c.fieldNames().strLit().forEach(strCtx ->
                            messageBuilder.addReservedName(ProtoUtils.getStringLiteral(strCtx.getText()))
                    );
            default -> {} // EOF
        }
    }

    private boolean isMessageType(ProtobufParser.Type_Context ctx) {
        return ctx.messageType() != null;
    }

    @Override
    public List<UninterpretedOption.NamePart> visitOptionName(ProtobufParser.OptionNameContext ctx) {
        Stream<UninterpretedOption.NamePart.Builder> custom = ctx.custom.stream()
                .map(ProtobufParser.IdentContext::getText)
                .map(name -> UninterpretedOption.NamePart.newBuilder()
                        .setIsExtension(true)
                        .setNamePart(name));

        Stream<UninterpretedOption.NamePart.Builder> parts = ctx.plain.stream()
                .map(ProtobufParser.IdentContext::getText)
                .map(name -> UninterpretedOption.NamePart.newBuilder()
                        .setIsExtension(false)
                        .setNamePart(name));

        return Stream.concat(custom, parts)
                .map(UninterpretedOption.NamePart.Builder::build)
                .toList();
    }

    @Override
    public Object visitConstant(ProtobufParser.ConstantContext ctx) {
        if (ctx.fullIdent() != null) {
            return new Identifier(ctx.fullIdent().getText());
        } else if (ctx.boolLit() != null) {
            return new Identifier(ctx.boolLit().getText());
        } else if (ctx.intLit() != null) {
            return Long.parseLong(ctx.intLit().getText());
        } else if (ctx.floatLit() != null) {
            String text = ctx.floatLit().getText();
            return switch (text) {
                case "inf", "+inf" -> Double.POSITIVE_INFINITY;
                case "-inf" -> Double.NEGATIVE_INFINITY;
                case "nan" -> Double.NaN;
                default -> Double.parseDouble(text);
            };
        } else if (ctx.strLit() != null) {
            return ByteString.copyFromUtf8(ProtoUtils.getStringLiteral(ctx.strLit().getText()));
        } else {
            throw new IllegalStateException();
        }
    }

    static String getStringConstant(ProtobufParser.OptionContext ctx) {
        return ProtoUtils.getStringLiteral(ctx.constant().getText());
    }

    private void setFieldTypeName(ProtobufParser.Type_Context ctx, FieldDescriptorProto.Builder fieldBuilder) {
        String typeName = ctx.getText();
        if (ctx.messageType() == null) {
            fieldBuilder.setType(ProtoUtils.fieldTypeForName(typeName));
        } else {
            MemberNode node = root.resolve(typeName, scope).orElseThrow(() -> new TypeNotFoundException(typeName));
            fieldBuilder.setTypeName(node.fullName());
            fieldBuilder.setType(node.type());
        }
    }

}
