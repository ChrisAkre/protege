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

public record OuterClassCodegen(CodegenContext ctx, ProtoCodegen temp) {
    public TypeSpec generate() {
        temp.populateOneofInterfaces(ctx);

        var outerClassBuilder = TypeSpec.classBuilder(ctx.outerName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        outerClassBuilder.addField(versionField());

        outerClassBuilder.addField(fileDescriptorField());
        outerClassBuilder.addMethod(getDescriptor());

        for (var enumType : ctx.fileDescriptor().getEnumTypeList()) {
            outerClassBuilder.addType(temp.generateEnumType(new EnumContext(enumType, ctx)));
        }

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            MessageCodegen messageCodegen = new MessageCodegen(message, ctx, Cons.of(ctx.outerName()), temp);
            outerClassBuilder.addType(temp.generateOrBuilderType(messageCodegen));
            outerClassBuilder.addType(messageCodegen.generateMessageClass());
        }

        if (ProtoUtils.isJavaGenericServicesEnabled(ctx.fileDescriptor())) {
            for (var service : ctx.fileDescriptor().getServiceList()) {
                temp.generateServiceTypes(outerClassBuilder, service, ctx);
            }
        }
        return outerClassBuilder.build();
    }

    FieldSpec versionField() {
        return FieldSpec.builder(String.class, "PROTEGE_VERSION", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", ProtegeVersion.VERSION_STRING)
                .build();
    }

    MethodSpec getDescriptor() {
        return MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(Descriptors.FileDescriptor.class)
                .addStatement("return fileDescriptor")
                .build();
    }

    FieldSpec fileDescriptorField() {
        var descriptorChunks =  ProtoUtils.splitAndEscapeBytes(ctx.fileDescriptor().toByteArray()).stream()
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
