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
}
