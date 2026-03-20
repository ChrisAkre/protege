package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;

/**
 * Static methods for generating oneof-related code specifications
 */
public class OneofMethods {

    private OneofMethods() {
        // Utility class
    }

    static MethodSpec getOneof(OneofCodegen ctx, CodeBlock switchCode) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.getInterfaceClassName())
                .addCode(switchCode)
                .build();
    }

    static MethodSpec getOneofCase(OneofCodegen ctx, CodeBlock getCaseCode) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Case")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ctx.getEnumClassName())
                .addCode(getCaseCode)
                .build();
    }

    static MethodSpec abstractGetOneof(OneofCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName())
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ctx.getInterfaceClassName())
                .build();
    }

    static MethodSpec abstractGetOneofCase(OneofCodegen ctx) {
        return MethodSpec.methodBuilder("get" + ctx.pascalName() + "Case")
                .addModifiers(Modifier.ABSTRACT, Modifier.PUBLIC)
                .returns(ctx.getEnumClassName())
                .build();
    }

    static MethodSpec clearOneof(OneofCodegen ctx, ClassName builderClassName, CodeBlock clearCode) {
        return MethodSpec.methodBuilder("clear" + ctx.pascalName())
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addCode(clearCode)
                .addStatement("onChanged()")
                .addStatement("return this")
                .build();
    }
}
