package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.ProtoUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CodegenMetadataTest {

    @Test
    public void testIsGenerateDeprecatedDefaults() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setGenerateDeprecated(true)
                .build();
        
        assertThat(metadata.isGenerateDeprecated(fileDescriptor)).isTrue();

        metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setGenerateDeprecated(false)
                .build();
        
        assertThat(metadata.isGenerateDeprecated(fileDescriptor)).isFalse();
    }

    @Test
    public void testIsGenerateDeprecatedOverride() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setGenerateDeprecated(false)
                .overrideGenerateDeprecated(true)
                .build();
        
        assertThat(metadata.isGenerateDeprecated(fileDescriptor)).isTrue();
    }

    @Test
    public void testIsGenerateDeprecatedFromFileDescriptor() {
        String protoContent = """
                syntax = "proto3";
                package test;
                option (dev.akre.protege.java_generate_deprecated) = "true";
                """;
        DescriptorProtos.FileDescriptorProto fileDescriptor = ProtoUtils.parseProto(protoContent, "test.proto");
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setGenerateDeprecated(false)
                .build();
        
        assertThat(metadata.isGenerateDeprecated(fileDescriptor)).isTrue();
    }

    @Test
    public void testIsGenerateDeprecatedFromMessageDescriptor() {
        String protoContent = """
                syntax = "proto3";
                package test;
                message MyMessage {
                    option (dev.akre.protege.java_generate_deprecated) = "true";
                }
                message OtherMessage {
                }
                """;
        DescriptorProtos.FileDescriptorProto fileDescriptor = ProtoUtils.parseProto(protoContent, "test.proto");
        DescriptorProtos.DescriptorProto myMessage = fileDescriptor.getMessageType(0);
        DescriptorProtos.DescriptorProto otherMessage = fileDescriptor.getMessageType(1);
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setGenerateDeprecated(false)
                .build();
        
        assertThat(metadata.isGenerateDeprecated(myMessage)).isTrue();
        assertThat(metadata.isGenerateDeprecated(otherMessage)).isFalse();
    }

    @Test
    public void testGetPackageDefaults() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setPackage("com.example")
                .build();
        
        assertThat(metadata.getPackage(fileDescriptor)).isEqualTo("com.example");
    }

    @Test
    public void testGetPackageOverride() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setPackage("com.example")
                .overridePackage("com.override")
                .build();
        
        assertThat(metadata.getPackage(fileDescriptor)).isEqualTo("com.override");
    }

    @Test
    public void testGetPackageFromFileDescriptor() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("com.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .build();
        
        assertThat(metadata.getPackage(fileDescriptor)).isEqualTo("com.proto");
    }

    @Test
    public void testGetPackageFromMessageDescriptor() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("com.proto")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("MyMessage").build())
                .build();
        DescriptorProtos.DescriptorProto myMessage = fileDescriptor.getMessageType(0);
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .build();
        
        assertThat(metadata.getPackage(myMessage)).isEqualTo("com.proto");
    }

    @Test
    public void testGetJavaPackageDefaults() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setJavaPackage("com.example.java")
                .build();
        
        assertThat(metadata.getJavaPackage(fileDescriptor)).isEqualTo("com.example.java");
    }

    @Test
    public void testGetJavaPackageOverride() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setJavaPackage("com.example.java")
                .overrideJavaPackage("com.override.java")
                .build();
        
        assertThat(metadata.getJavaPackage(fileDescriptor)).isEqualTo("com.override.java");
    }

    @Test
    public void testGetJavaPackageFromFileDescriptor() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setOptions(DescriptorProtos.FileOptions.newBuilder().setJavaPackage("com.proto.java").build())
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .build();
        
        assertThat(metadata.getJavaPackage(fileDescriptor)).isEqualTo("com.proto.java");
    }

    @Test
    public void testGetFieldAnnotationsDefaults() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .addFieldAnnotation("@Deprecated")
                .addFieldAnnotation("@SuppressWarnings(\"unchecked\")")
                .build();
        
        assertThat(metadata.getFieldAnnotations(fileDescriptor)).containsExactly("@Deprecated", "@SuppressWarnings(\"unchecked\")");
    }

    @Test
    public void testSetFieldAnnotations() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .setFieldAnnotations(java.util.List.of("@A", "@B"))
                .build();
        
        assertThat(metadata.getFieldAnnotations(fileDescriptor)).containsExactly("@A", "@B");
    }

    @Test
    public void testAddOverrideFieldAnnotation() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .addFieldAnnotation("@Default")
                .addOverrideFieldAnnotation("@Override")
                .build();
        
        assertThat(metadata.getFieldAnnotations(fileDescriptor)).containsExactly("@Override");
    }

    @Test
    public void testGetFieldAnnotationsOverride() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .build();
        
        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .addFieldAnnotation("@Deprecated")
                .setOverrideFieldAnnotations(java.util.List.of("@SomeAnnotation"))
                .build();
        
        assertThat(metadata.getFieldAnnotations(fileDescriptor)).containsExactly("@SomeAnnotation");
    }

    @Test
    public void testGetFieldAnnotationsFromFileDescriptor() {
        String protoContent = """
                syntax = "proto3";
                package test;
                import "options.proto";
                option (dev.akre.protege.java_field_annotation) = "@Deprecated";
                option (dev.akre.protege.java_field_annotation) = "@SuppressWarnings(\\"unchecked\\")";
                """;
        // Need options.proto for the custom option to be parsed correctly if using ProtoUtils.parseProto
        // But DescriptorProtos.FileDescriptorProto.newBuilder() might be easier if we manually add UninterpretedOptions
        
        // Let's try manually building it to avoid dependency on options.proto in this unit test
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setOptions(DescriptorProtos.FileOptions.newBuilder()
                        .addUninterpretedOption(DescriptorProtos.UninterpretedOption.newBuilder()
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("dev").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("akre").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("protege").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("java_field_annotation").setIsExtension(false).build())
                                .setStringValue(com.google.protobuf.ByteString.copyFromUtf8("@Deprecated"))
                                .build())
                        .addUninterpretedOption(DescriptorProtos.UninterpretedOption.newBuilder()
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("dev").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("akre").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("protege").setIsExtension(false).build())
                                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder().setNamePart("java_field_annotation").setIsExtension(false).build())
                                .setStringValue(com.google.protobuf.ByteString.copyFromUtf8("@SuppressWarnings(\"unchecked\")"))
                                .build())
                        .build())
                .build();

        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor)
                .build();
        
        assertThat(metadata.getFieldAnnotations(fileDescriptor)).containsExactly("@Deprecated", "@SuppressWarnings(\"unchecked\")");
    }

    @Test
    public void testMessageDescriptorMap() {
        DescriptorProtos.FileDescriptorProto fileDescriptor = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                        .setName("Parent")
                        .addNestedType(DescriptorProtos.DescriptorProto.newBuilder().setName("Child").build())
                        .build())
                .addMessageType(DescriptorProtos.DescriptorProto.newBuilder().setName("Other").build())
                .build();

        CodegenMetadata metadata = new CodegenMetadata.Builder(fileDescriptor).build();

        assertThat(metadata.messageDescriptorMap()).hasSize(3);
        assertThat(metadata.messageDescriptorMap()).containsKey("Parent");
        assertThat(metadata.messageDescriptorMap()).containsKey("Parent.Child");
        assertThat(metadata.messageDescriptorMap()).containsKey("Other");
        assertThat(metadata.messageDescriptorMap().get("Parent.Child").getName()).isEqualTo("Child");
    }
}
