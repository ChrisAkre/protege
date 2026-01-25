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

        enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
        enumBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(int.class, "value")
                .addStatement("this.value = value")
                .build());

        enumBuilder.addMethod(EnumMessages.getNumber());
        enumBuilder.addMethod(EnumMessages.forNumber(this));
        enumBuilder.addMethod(EnumMessages.getDescriptor(this));
        enumBuilder.addMethod(EnumMessages.getValueDescriptor());
        enumBuilder.addMethod(EnumMessages.getDescriptorForType());
        enumBuilder.addMethod(EnumMessages.valueOf(enumName));
        return enumBuilder.build();
    }
}
