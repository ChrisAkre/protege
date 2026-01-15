package dev.akre.protege;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static dev.akre.protege.ProtoUtils.getFieldType;

public class ProtobufFileDescriptorVisitor extends ProtobufBaseVisitor<Object> {

    private FileDescriptorProto.Builder fileBuilder;

    public FileDescriptorProto.Builder getFileDescriptorBuilder() {
        return fileBuilder;
    }

    public record Syntax(String version) {}

    public record Package(String name) {}

    public record Import(String path, boolean weak, boolean pub) {
        public void addDependency(FileDescriptorProto.Builder fileBuilder) {
            int last = fileBuilder.getDependencyCount();
            fileBuilder.addDependency(path());
            if (weak()) {
                fileBuilder.addWeakDependency(last);
            }
            if (pub()) {
                fileBuilder.addPublicDependency(last);
            }
        }
    }

    public record Identifier(String name) {}

    @Override
    public FileDescriptorProto.Builder visitProto(ProtobufParser.ProtoContext ctx) {
        fileBuilder = FileDescriptorProto.newBuilder();
        ctx.children.forEach(c -> {
            switch (visit(c)) {
                case Syntax s -> fileBuilder.setSyntax(s.version());
                case Package p -> fileBuilder.setPackage(p.name());
                case Import i -> i.addDependency(fileBuilder);
                case DescriptorProto.Builder m -> fileBuilder.addMessageType(m);
                case EnumDescriptorProto.Builder e -> fileBuilder.addEnumType(e);
                case null -> {} // EOF
                default -> throw new IllegalStateException("unexpected: " + c);
            }
        });
        return fileBuilder;
    }

    @Override
    public Syntax visitSyntax(ProtobufParser.SyntaxContext ctx) {
        return new Syntax(getStringLiteral(ctx.protoVersion().getText()));
    }

    @Override
    public Object visitPackageStatement(ProtobufParser.PackageStatementContext ctx) {
        return new Package(ctx.fullIdent().getText());
    }

    @Override
    public Object visitImportStatement(ProtobufParser.ImportStatementContext ctx) {
        return new Import(getStringLiteral(ctx.strLit().getText()), ctx.WEAK() != null, ctx.PUBLIC() != null);
    }

    @Override
    public UninterpretedOption visitOption(ProtobufParser.OptionContext ctx) {
        var result = UninterpretedOption.newBuilder()
                .addAllName(visitOptionName(ctx.optionName()));

        switch (visitConstant(ctx.constant())) {
            case Long l when l >= 0 -> result.setPositiveIntValue(l);
            case Long l -> result.setNegativeIntValue(-l);
            case Double d -> result.setDoubleValue(d);
            case ByteString s -> result.setStringValue(s);
            case Identifier id -> result.setIdentifierValue(id.name());
            default -> throw new IllegalStateException();
        }

        return result.build();
    }

    @Override
    public DescriptorProto.Builder visitMessageDef(ProtobufParser.MessageDefContext ctx) {
        DescriptorProto.Builder messageBuilder = DescriptorProto.newBuilder()
                .setName(ctx.messageName().getText());

        visitMessageBody(ctx.messageBody(), messageBuilder);
        return messageBuilder;
    }

    private void visitMessageBody(ProtobufParser.MessageBodyContext ctx,
                                  DescriptorProto.Builder messageBuilder) {
        for (ParseTree child : ctx.children) {
            switch (child) {
                case ProtobufParser.FieldContext fieldCtx -> {
                    FieldDescriptorProto.Builder fieldBuilder = (FieldDescriptorProto.Builder) visit(fieldCtx);
                    messageBuilder.addField(fieldBuilder);
                }
                case ProtobufParser.MessageDefContext nestedCtx -> {
                    DescriptorProto.Builder nestedBuilder = DescriptorProto.newBuilder()
                            .setName(nestedCtx.messageName().getText());
                    visitMessageBody(nestedCtx.messageBody(), nestedBuilder);
                    messageBuilder.addNestedType(nestedBuilder);
                }
                case ProtobufParser.EnumDefContext enumCtx -> {
                    EnumDescriptorProto.Builder enumBuilder = (EnumDescriptorProto.Builder) visit(enumCtx);
                    messageBuilder.addEnumType(enumBuilder);
                }
                case ProtobufParser.OneofContext oneofCtx ->
                        processOneof(oneofCtx, messageBuilder);
                case ProtobufParser.MapFieldContext mapCtx -> {
                    FieldDescriptorProto.Builder fieldBuilder = (FieldDescriptorProto.Builder) visit(mapCtx);
                    messageBuilder.addField(fieldBuilder);
                }
                case ProtobufParser.ReservedContext reservedCtx ->
                        visitReserved(reservedCtx, messageBuilder);
                default -> {}
            }
        }
    }

    private void processOneof(ProtobufParser.OneofContext oneofCtx,
                              DescriptorProto.Builder messageBuilder) {
        int oneofIndex = messageBuilder.getOneofDeclCount();

        OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder()
                .setName(oneofCtx.oneofName().getText());
        messageBuilder.addOneofDecl(oneofBuilder);

        // Process oneof fields
        for (ProtobufParser.OneofFieldContext fieldCtx : oneofCtx.oneofField()) {
            FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                    .setName(fieldCtx.fieldName().getText())
                    .setNumber(Integer.parseInt(fieldCtx.fieldNumber().getText()))
                    .setType(getFieldType(fieldCtx.type_()))
                    .setOneofIndex(oneofIndex);

            if (isMessageType(fieldCtx.type_())) {
                fieldBuilder.setTypeName(fieldCtx.type_().getText());
            }

            messageBuilder.addField(fieldBuilder);
        }
    }

    @Override
    public FieldDescriptorProto.Builder visitField(ProtobufParser.FieldContext ctx) {
        FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder()
                .setName(ctx.fieldName().getText())
                .setNumber(Integer.parseInt(ctx.fieldNumber().getText()))
                .setType(getFieldType(ctx.type_()));

        if (isMessageType(ctx.type_())) {
            fieldBuilder.setTypeName(ctx.type_().getText());
        }

        // Set field label using enhanced switch
        FieldDescriptorProto.Label label = switch (ctx) {
            case ProtobufParser.FieldContext c when c.REQUIRED() != null -> FieldDescriptorProto.Label.LABEL_REQUIRED;
            case ProtobufParser.FieldContext c when c.OPTIONAL() != null -> FieldDescriptorProto.Label.LABEL_OPTIONAL;
            case ProtobufParser.FieldContext c when c.REPEATED() != null -> FieldDescriptorProto.Label.LABEL_REPEATED;
            default -> FieldDescriptorProto.Label.LABEL_OPTIONAL; // Proto3 default
        };
        fieldBuilder.setLabel(label);

        // Handle field options
        if (ctx.fieldOptions() != null) {
            fieldBuilder.setOptions(FieldOptions.newBuilder()
                    .addAllUninterpretedOption(ctx.fieldOptions().option().stream().map(this::visitOption).toList()));
        }

        return fieldBuilder;
    }

    @Override
    public FieldDescriptorProto.Builder visitMapField(ProtobufParser.MapFieldContext ctx) {
        // Map fields are represented as repeated message fields with special options
        String mapEntryName = capitalize(ctx.mapName().getText()) + "Entry";

        return FieldDescriptorProto.newBuilder()
                .setName(ctx.mapName().getText())
                .setNumber(Integer.parseInt(ctx.fieldNumber().getText()))
                .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                .setTypeName(mapEntryName);
    }

    @Override
    public EnumDescriptorProto.Builder visitEnumDef(ProtobufParser.EnumDefContext ctx) {
        EnumDescriptorProto.Builder enumBuilder = EnumDescriptorProto.newBuilder()
                .setName(ctx.enumName().getText());

        // Visit enum fields
        for (ProtobufParser.EnumFieldContext fieldCtx : ctx.enumBody().enumField()) {
            String numberText = fieldCtx.intLit().getText();
            int number = fieldCtx.MINUS() != null
                    ? -Integer.parseInt(numberText)
                    : Integer.parseInt(numberText);

            EnumValueDescriptorProto.Builder valueBuilder = EnumValueDescriptorProto.newBuilder()
                    .setName(fieldCtx.ident().getText())
                    .setNumber(number);

            enumBuilder.addValue(valueBuilder);
        }

        return enumBuilder;
    }

    @Override
    public ServiceDescriptorProto.Builder visitServiceDef(ProtobufParser.ServiceDefContext ctx) {
        ServiceDescriptorProto.Builder serviceBuilder = ServiceDescriptorProto.newBuilder()
                .setName(ctx.serviceName().getText());

        // Visit RPC methods
        for (ProtobufParser.RpcContext rpcCtx : ctx.rpc()) {
            MethodDescriptorProto.Builder methodBuilder = MethodDescriptorProto.newBuilder()
                    .setName(rpcCtx.rpcName().getText())
                    .setInputType(rpcCtx.messageType(0).getText())
                    .setOutputType(rpcCtx.messageType(1).getText());

            if (rpcCtx.STREAM(0) != null) {
                methodBuilder.setClientStreaming(true);
            }
            if (rpcCtx.STREAM(1) != null) {
                methodBuilder.setServerStreaming(true);
            }

            serviceBuilder.addMethod(methodBuilder);
        }

        return serviceBuilder;
    }

    private void visitReserved(ProtobufParser.ReservedContext ctx,
                               DescriptorProto.Builder messageBuilder) {
        switch (ctx) {
            case ProtobufParser.ReservedContext c when c.ranges() != null -> {
                for (ProtobufParser.RangeContext rangeCtx : c.ranges().range()) {
                    int start = Integer.parseInt(rangeCtx.intLit(0).getText());
                    int end = switch (rangeCtx) {
                        case ProtobufParser.RangeContext r when r.TO() == null -> start + 1;
                        case ProtobufParser.RangeContext r when r.MAX() != null -> 536870911; // Max field number
                        default -> Integer.parseInt(rangeCtx.intLit(1).getText());
                    };

                    DescriptorProto.ReservedRange.Builder rangeBuilder = DescriptorProto.ReservedRange.newBuilder()
                            .setStart(start)
                            .setEnd(end);
                    messageBuilder.addReservedRange(rangeBuilder);
                }
            }
            case ProtobufParser.ReservedContext c when c.fieldNames() != null -> {
                for (ProtobufParser.StrLitContext strCtx : c.fieldNames().strLit()) {
                    messageBuilder.addReservedName(getStringLiteral(strCtx.getText()));
                }
            }
            default -> {}
        }
    }



    private boolean isMessageType(ProtobufParser.Type_Context ctx) {
        return ctx.messageType() != null;
    }

    @Override
    public List<UninterpretedOption.NamePart> visitOptionName(ProtobufParser.OptionNameContext ctx) {
        var custom = Stream.of(ctx.custom)
                .filter(Objects::nonNull)
                .map(ProtobufParser.IdentContext::getText)
                .map(UninterpretedOption.NamePart.newBuilder().setIsExtension(true)::setNamePart);
        var parts = ctx.ident().stream()
                .map(ProtobufParser.IdentContext::getText)
                .map(UninterpretedOption.NamePart.newBuilder()::setNamePart);
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
            return Double.parseDouble(ctx.floatLit().getText());
        } else if (ctx.strLit() != null) {
            return ByteString.copyFromUtf8(getStringLiteral(ctx.strLit().getText()));
        } else {
            throw new IllegalStateException();
        }
    }

    static String getStringLiteral(String text) {
        // Remove surrounding quotes and handle escape sequences
        return text.substring(1, text.length() - 1)
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }



    private String capitalize(String str) {
        return (str == null || str.isEmpty())
                ? str
                : str.substring(0, 1).toUpperCase() + str.substring(1);
    }

}