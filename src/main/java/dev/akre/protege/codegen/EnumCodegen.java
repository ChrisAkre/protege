package dev.akre.protege.codegen;

import dev.akre.protege.CodegenConfig;
import dev.akre.protege.CodegenMetadata;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import dev.akre.util.Cons;

import javax.lang.model.element.Modifier;

/**
 * Generates Java code for Protobuf enums including:
 * <ul>
 *   <li>Enum constants</li>
 *   <li>Value storage and retrieval</li>
 *   <li>Integration with ProtocolMessageEnum</li>
 * </ul>
 */
public record EnumCodegen(
        DescriptorProtos.EnumDescriptorProto descriptor,
        Cons<String> scope,
        ProtoCodegen protoCodegen,
        CodegenMetadata config
) implements CodegenConfig {
    public String getEnumName() {
        return descriptor.getName();
    }

    public ClassName outerClassName() {
        return ClassName.get(getJavaPackage(), getOuterName());
    }

    public String[] parentNames() {
        return scope.stream().toArray(String[]::new);
    }

    /**
     * Generates code for an enum
     *
     * @return a fully populated TypeSpec containing the entire enum class
     */
    public TypeSpec generate() {
        var enumName = getEnumName();
        var enumBuilder = TypeSpec.enumBuilder(enumName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(com.google.protobuf.ProtocolMessageEnum.class);

        for (var value : descriptor.getValueList()) {
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
        enumBuilder.addMethod(EnumMethods.getNumber(this));
        // public static <EnumName> forNumber(int value)
        enumBuilder.addMethod(EnumMethods.forNumber(this));
        // public static final Descriptors.EnumDescriptor getDescriptor()
        enumBuilder.addMethod(EnumMethods.getDescriptor(this));
        // public final Descriptors.EnumValueDescriptor getValueDescriptor()
        enumBuilder.addMethod(EnumMethods.getValueDescriptor(this));
        // public final Descriptors.EnumDescriptor getDescriptorForType()
        enumBuilder.addMethod(EnumMethods.getDescriptorForType(this));
        // public static <EnumName> valueOf(Descriptors.EnumValueDescriptor desc)
        enumBuilder.addMethod(EnumMethods.valueOf(this, enumName));
        return enumBuilder.build();
    }
}
