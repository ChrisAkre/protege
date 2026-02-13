package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.GeneratedMessage;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;

import javax.annotation.processing.Filer;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for the Protobuf-to-Java compiler.
 * <p>
 * This class orchestrates the generation of the Outer Class and all nested Message classes from a
 * {@link DescriptorProtos.FileDescriptorProto}. It delegates the actual configuration and metadata management
 * to {@link CodegenMetadata}.
 * <p>
 * <strong>Note:</strong> This class acts as a facade and is currently slated for refactoring. Future versions
 * may consolidate this logic directly into {@link CodegenMetadata} or a dedicated compiler service.
 */
// TODO Remove this class.  Probably going to rename CodegenMetadata and use that as the entry point, the builder will
//  take the file descriptor, the options
public class ProtoCodegen {

    public static final ClassName OR_BUILDER_INTERFACE = ClassName.get("com.google.protobuf", "MessageOrBuilder");
    private final Filer filer;
    private final boolean generateDeprecated;
    private final ClassName messageParentClass;
    private static final ClassName DEFAULT_MESSAGE_PARENT = ClassName.get(GeneratedMessage.class);

    private final Map<String, List<ClassName>> oneofInterfacesByType = new HashMap<>();

    public Map<String, List<ClassName>> oneofInterfacesByType() {
        return oneofInterfacesByType;
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


    public ClassName getFieldAccessorTableClass() {
        return messageParentClass.nestedClass("FieldAccessorTable");
    }


    public JavaFile generateFile(DescriptorProtos.FileDescriptorProto fileDescriptor) throws IOException {
        CodegenMetadata config = CodegenMetadata.build(fileDescriptor)
                .setGenerateDeprecated(generateDeprecated)
                .setJavaMessageSuperclass(messageParentClass.reflectionName())
                .build();
        var outerClass = new OuterClassCodegen(this, config).generate();

        var javaFile = JavaFile.builder(config.packageName(), outerClass).build();
        javaFile.writeTo(filer);
        return javaFile;
    }

}
