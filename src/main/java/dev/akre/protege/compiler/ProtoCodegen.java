package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.GeneratedMessage;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.akre.protege.compiler.CodegenContext.JAVA_ENHANCED_ONEOF_OPTION;

public class ProtoCodegen {

    public static final ClassName OR_BUILDER_INTERFACE = ClassName.get("com.google.protobuf", "MessageOrBuilder");
    private final Filer filer;
    private final boolean generateDeprecated;
    private final ClassName messageParentClass;
    static final Map<DescriptorProtos.FieldDescriptorProto.Type, TypeName> PROTO_TYPE_TO_TYPE_NAME;
    private static final ClassName DEFAULT_MESSAGE_PARENT = ClassName.get(GeneratedMessage.class);

    private final Map<String, List<ClassName>> oneofInterfacesByType = new HashMap<>();

    public Map<String, List<ClassName>> oneofInterfacesByType() {
        return oneofInterfacesByType;
    }

    static {
        PROTO_TYPE_TO_TYPE_NAME = Map.ofEntries(
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, ClassName.get(String.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, TypeName.FLOAT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, TypeName.DOUBLE),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, TypeName.BOOLEAN),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, ClassName.get(com.google.protobuf.ByteString.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64, TypeName.LONG)
        );
    }


    public ProtoCodegen(Filer filer) {
        this(filer, true);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated) {
        this(filer, generateDeprecated, DEFAULT_MESSAGE_PARENT);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated, ClassName messageParentClass) {
        this.filer = filer;
        this.generateDeprecated = generateDeprecated;
        this.messageParentClass = messageParentClass;
    }

    public ClassName messageParentClass() {
        return messageParentClass;
    }

    public ClassName getFieldAccessorTableClass() {
        return messageParentClass.nestedClass("FieldAccessorTable");
    }

    public boolean generateDeprecated() {
        return generateDeprecated;
    }

    public JavaFile generateFile(DescriptorProtos.FileDescriptorProto fileDescriptor) throws IOException {
        var ctx = CodegenContext.create(fileDescriptor, messageParentClass, generateDeprecated);
        CodegenMetadata config = CodegenMetadata.build(fileDescriptor)
                .setGenerateDeprecated(generateDeprecated)
                .build();
        var outerClass = new OuterClassCodegen(ctx, this, config).generate();

        var javaFile = JavaFile.builder(ctx.packageName(), outerClass).build();
        javaFile.writeTo(filer);
        return javaFile;
    }

    public Optional<String> getJavaImplements(DescriptorProtos.DescriptorProto message) {
        return message.getOptions().getUninterpretedOptionList().stream()
                .filter(o -> o.getNameList().stream().anyMatch(n -> n.getNamePart().endsWith("java_implements")))
                .map(DescriptorProtos.UninterpretedOption::getStringValue)
                .map(com.google.protobuf.ByteString::toStringUtf8)
                .findFirst();
    }

}
