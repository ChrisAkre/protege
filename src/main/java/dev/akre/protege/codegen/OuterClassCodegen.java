package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;

public record OuterClassCodegen(CodegenMetadata config) implements CodegenConfig {
    public DescriptorProtos.FileDescriptorProto descriptor() {
        return config.fileDescriptor();
    }

    public TypeSpec generate() {
        var outerClassBuilder = TypeSpec.classBuilder(getOuterName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // public static final String PROTEGE_VERSION
        outerClassBuilder.addField(versionField(this));

        // private static final Descriptors.FileDescriptor fileDescriptor
        outerClassBuilder.addField(fileDescriptorField(this));
        // public static Descriptors.FileDescriptor getDescriptor()
        outerClassBuilder.addMethod(getDescriptor(this));

        for (var enumType : config.fileDescriptor().getEnumTypeList()) {
            var enumCodegen = new EnumCodegen(enumType, Cons.of(getOuterName()), config);
            // public enum <name> implements ProtocolMessageEnum
            outerClassBuilder.addType(enumCodegen.generate());
        }

        for (var message : config.fileDescriptor().getMessageTypeList()) {
            MessageCodegen messageCodegen = new MessageCodegen(message, Cons.of(getOuterName()), config);
            // public interface <name>OrBuilder extends MessageOrBuilder
            outerClassBuilder.addType(messageCodegen.generateMessageInterface());
            // public static final class <name> extends GeneratedMessageV3 implements <name>OrBuilder
            outerClassBuilder.addType(messageCodegen.generateMessageClass());
        }

        if (ProtoUtils.isJavaGenericServicesEnabled(config.fileDescriptor())) {
            for (var service : config.fileDescriptor().getServiceList()) {
                var serviceCodegen = new ServiceCodegen(service, config);
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
        var descriptorChunks =  ProtoUtils.splitAndEscapeBytes(context.config().fileDescriptor().toByteArray()).stream()
                .map(s -> CodeBlock.of("\"$L\"", s))
                .collect(CodeBlock.joining(",\n"));

        var data = CodeBlock.builder().add("new String[] {\n").indent().add(descriptorChunks).unindent().add("\n}").build();

        CodeBlock descriptorInitializer = CodeBlock.builder()
                .add("$T.internalBuildGeneratedFileFrom($L, new $T[0])",
                        Descriptors.FileDescriptor.class, data, Descriptors.FileDescriptor.class)
                .build();
        return FieldSpec.builder(Descriptors.FileDescriptor.class, "fileDescriptor", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(descriptorInitializer)
                .build();
    }
}
