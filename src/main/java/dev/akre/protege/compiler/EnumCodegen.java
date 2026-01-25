package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;

public record EnumCodegen(
        DescriptorProtos.EnumDescriptorProto enumType,
        Cons<String> scope,
        CodegenContext ctx,
        ProtoCodegen protoCodegen
) {
    public String getEnumName() {
        return enumType.getName();
    }

    public ClassName outerClassName() {
        return ClassName.get(ctx.packageName(), ctx.outerName());
    }

    public String[] parentNames() {
        return scope.stream().toArray(String[]::new);
    }

    public TypeSpec generate() {
        var enumName = getEnumName();
        var enumBuilder = TypeSpec.enumBuilder(enumName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(com.google.protobuf.ProtocolMessageEnum.class);

        for (var value : enumType.getValueList()) {
            enumBuilder.addEnumConstant(value.getName(), TypeSpec.anonymousClassBuilder("$L", value.getNumber()).build());
        }

        enumBuilder.addEnumConstant("UNRECOGNIZED", TypeSpec.anonymousClassBuilder("-1").build());

        // private final int value
        enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
        // private <EnumName>(int value)
        enumBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(int.class, "value")
                .addStatement("this.value = value")
                .build());

        // public final int getNumber()
        enumBuilder.addMethod(EnumMessages.getNumber());
        // public static <EnumName> forNumber(int value)
        enumBuilder.addMethod(EnumMessages.forNumber(this));
        // public static final Descriptors.EnumDescriptor getDescriptor()
        enumBuilder.addMethod(EnumMessages.getDescriptor(this));
        // public final Descriptors.EnumValueDescriptor getValueDescriptor()
        enumBuilder.addMethod(EnumMessages.getValueDescriptor());
        // public final Descriptors.EnumDescriptor getDescriptorForType()
        enumBuilder.addMethod(EnumMessages.getDescriptorForType());
        // public static <EnumName> valueOf(Descriptors.EnumValueDescriptor desc)
        enumBuilder.addMethod(EnumMessages.valueOf(enumName));
        return enumBuilder.build();
    }
}
