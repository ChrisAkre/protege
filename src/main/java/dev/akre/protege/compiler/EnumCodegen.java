package dev.akre.protege.compiler;

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
 *
 * @param descriptor The EnumDescriptorProto describing the enum.
 * @param scope The scope (package/nesting) of the enum.
 * @param protoCodegen The main codegen instance.
 * @param config The shared configuration metadata.
 */
public record EnumCodegen(
        DescriptorProtos.EnumDescriptorProto descriptor,
        Cons<String> scope,
        ProtoCodegen protoCodegen,
        CodegenMetadata config
) implements CodegenConfig {
    /**
     * Returns the name of the enum.
     *
     * @return The simple name of the enum.
     */
    public String getEnumName() {
        return descriptor.getName();
    }

    /**
     * Returns the ClassName of the outer class containing this enum.
     *
     * @return The ClassName of the outer class.
     */
    public ClassName outerClassName() {
        return ClassName.get(getJavaPackage(), getOuterName());
    }

    /**
     * Returns the parent names in the scope as an array.
     *
     * @return An array of parent names.
     */
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
