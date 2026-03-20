package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

import com.google.protobuf.Descriptors;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;

import javax.lang.model.element.Modifier;

/**
 * Static methods for generating enum types
 */
public class EnumMessages {

    private EnumMessages() {
        // Utility class
    }

    static MethodSpec getNumber() {
        return MethodSpec.methodBuilder("getNumber")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(int.class)
                .beginControlFlow("if (this == UNRECOGNIZED)")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "Can't get the number of an unknown enum value.")
                .endControlFlow()
                .addStatement("return value")
                .build();
    }

    static MethodSpec forNumber(EnumCodegen ctx) {
        var forNumberBuilder = MethodSpec.methodBuilder("forNumber")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(int.class, "value")
                .returns(ClassName.get("", ctx.getEnumName()));

        forNumberBuilder.beginControlFlow("switch (value)");
        for (var value : ctx.descriptor().getValueList()) {
            forNumberBuilder.addStatement("case $L: return $L", value.getNumber(), value.getName());
        }
        forNumberBuilder.addStatement("default: return null");
        forNumberBuilder.endControlFlow();

        return forNumberBuilder.build();
    }

    static MethodSpec getDescriptor(EnumCodegen ctx) {
        var cb = CodeBlock.builder();
        cb.add("return $T.getDescriptor()", ctx.outerClassName());

        var parentNames = ctx.parentNames();
        if (parentNames.length > 1) {
            cb.add(".findMessageTypeByName($S)", parentNames[1]);
            for (int i = 2; i < parentNames.length; i++) {
                cb.add(".findNestedTypeByName($S)", parentNames[i]);
            }
            cb.add(".findEnumTypeByName($S)", ctx.getEnumName());
        } else {
            cb.add(".findEnumTypeByName($S)", ctx.getEnumName());
        }
        cb.add(";\n");

        return MethodSpec.methodBuilder("getDescriptor")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .returns(Descriptors.EnumDescriptor.class)
                .addCode(cb.build())
                .build();
    }

    static MethodSpec getValueDescriptor() {
        return MethodSpec.methodBuilder("getValueDescriptor")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(Descriptors.EnumValueDescriptor.class)
                .addStatement("return getDescriptor().getValues().get(ordinal())")
                .build();
    }

    static MethodSpec getDescriptorForType() {
        return MethodSpec.methodBuilder("getDescriptorForType")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .returns(Descriptors.EnumDescriptor.class)
                .addStatement("return getDescriptor()")
                .build();
    }

    static MethodSpec valueOf(String enumName) {
        return MethodSpec.methodBuilder("valueOf")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Descriptors.EnumValueDescriptor.class, "desc")
                .returns(ClassName.get("", enumName))
                .addStatement("return forNumber(desc.getNumber())")
                .build();
    }
}
