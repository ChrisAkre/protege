package dev.akre.protege.compiler;

import com.google.protobuf.Descriptors;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;

public record OuterClassCodegen(CodegenContext ctx, ProtoCodegen protoCodegen, CodegenMetadata config) implements CodegenMetadata.Config {
    public TypeSpec generate() {
        protoCodegen.populateOneofInterfaces(ctx);

        var outerClassBuilder = TypeSpec.classBuilder(ctx.outerName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // public static final String PROTEGE_VERSION
        outerClassBuilder.addField(versionField(this));

        // private static final Descriptors.FileDescriptor fileDescriptor
        outerClassBuilder.addField(fileDescriptorField(this));
        // public static Descriptors.FileDescriptor getDescriptor()
        outerClassBuilder.addMethod(getDescriptor(this));

        for (var enumType : ctx.fileDescriptor().getEnumTypeList()) {
            var enumCodegen = new EnumCodegen(enumType, Cons.of(ctx.outerName()), ctx, protoCodegen);
            // public enum <name> implements ProtocolMessageEnum
            outerClassBuilder.addType(enumCodegen.generate());
        }

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            MessageCodegen messageCodegen = new MessageCodegen(message, ctx, Cons.of(ctx.outerName()), protoCodegen);
            // public interface <name>OrBuilder extends MessageOrBuilder
            outerClassBuilder.addType(messageCodegen.generateMessageInterface());
            // public static final class <name> extends GeneratedMessageV3 implements <name>OrBuilder
            outerClassBuilder.addType(messageCodegen.generateMessageClass());
        }

        if (ProtoUtils.isJavaGenericServicesEnabled(ctx.fileDescriptor())) {
            for (var service : ctx.fileDescriptor().getServiceList()) {
                var serviceCodegen = new ServiceCodegen(service, ctx);
                // public static abstract class <name> implements Service
                outerClassBuilder.addType(serviceCodegen.generate());
            }
        }
        return outerClassBuilder.build();
    }

    static FieldSpec versionField(OuterClassCodegen context) {
        return FieldSpec.builder(String.class, "PROTEGE_VERSION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", ProtegeVersion.VERSION_STRING)
                .build();
    }

    static MethodSpec getDescriptor(OuterClassCodegen context) {
        return MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(Descriptors.FileDescriptor.class)
                .addStatement("return fileDescriptor")
                .build();
    }

    static FieldSpec fileDescriptorField(OuterClassCodegen context) {
        var descriptorChunks =  ProtoUtils.splitAndEscapeBytes(context.ctx().fileDescriptor().toByteArray()).stream()
                .map(s -> CodeBlock.of("\"$L\"", s))
                .collect(CodeBlock.joining(",\n"));

        var data = CodeBlock.builder().add("new String[] {\n").indent().add(descriptorChunks).unindent().add("\n}").build();

        CodeBlock descriptorInitializer = CodeBlock.builder()
                .addStatement("$T.internalBuildGeneratedFileFrom($L, new $T[0])",
                        Descriptors.FileDescriptor.class, data, Descriptors.FileDescriptor.class)
                .build();
        return FieldSpec.builder(Descriptors.FileDescriptor.class, "fileDescriptor", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(descriptorInitializer)
                .build();
    }
}
