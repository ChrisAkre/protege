package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProtoUtils Utility Tests")
public class ProtoUtilsTest {

    @Test
    @DisplayName("Should parse proto content with a given filename")
    void shouldParseProtoWithFilename() {
        String protoContent = "syntax = \"proto3\"; package dev.akre.test; message Test {}";
        String filename = "custom.proto";
        DescriptorProtos.FileDescriptorProto descriptor = ProtoUtils.parseProto(protoContent, filename);
        assertThat(descriptor.getName()).isEqualTo(filename);
    }

    @Test
    @DisplayName("Should parse proto content from a File object")
    void shouldParseProtoFromFile(@TempDir Path tempDir) throws IOException {
        String protoContent = "syntax = \"proto3\"; package dev.akre.test; message Test {}";
        Path protoFile = tempDir.resolve("test.proto");
        Files.writeString(protoFile, protoContent);

        DescriptorProtos.FileDescriptorProto descriptor = ProtoUtils.parseProto(protoFile.toFile());
        assertThat(descriptor.getName()).isEqualTo("test.proto");
    }

    @Test
    @DisplayName("Should fallback to package name when filename is not provided")
    void shouldFallbackToPackageNameWhenFilenameIsNull() {
        String protoContent = "syntax = \"proto3\"; package dev.akre.test; message Test {}";
        DescriptorProtos.FileDescriptorProto descriptor = ProtoUtils.parseProto(protoContent);
        assertThat(descriptor.getName()).isEqualTo("test.proto");
    }

    @Test
    @DisplayName("Should correctly convert strings to PascalCase")
    void shouldConvertToPascalCase() {
        assertThat(ProtoUtils.toPascalCase("test")).isEqualTo("Test");
        assertThat(ProtoUtils.toPascalCase("test_name")).isEqualTo("TestName");
        assertThat(ProtoUtils.toPascalCase("test_name_long")).isEqualTo("TestNameLong");
        assertThat(ProtoUtils.toPascalCase("TestName")).isEqualTo("TestName");
        assertThat(ProtoUtils.toPascalCase("")).isEqualTo("");
        assertThat(ProtoUtils.toPascalCase(null)).isNull();
    }

    @Test
    @DisplayName("Should correctly capitalize the first letter of a string")
    void shouldCapitalizeFirstLetter() {
        assertThat(ProtoUtils.capitalize("test")).isEqualTo("Test");
        assertThat(ProtoUtils.capitalize("test_name")).isEqualTo("Test_name");
        assertThat(ProtoUtils.capitalize("TestName")).isEqualTo("TestName");
        assertThat(ProtoUtils.capitalize("")).isEqualTo("");
        assertThat(ProtoUtils.capitalize(null)).isNull();
    }

    @Test
    public void testIsJavaGenericServicesEnabled() {
        DescriptorProtos.FileDescriptorProto proto3 = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setSyntax("proto3")
                .build();
        assertThat(ProtoUtils.isJavaGenericServicesEnabled(proto3)).isFalse();

        DescriptorProtos.FileDescriptorProto proto3WithOption = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setSyntax("proto3")
                .setOptions(DescriptorProtos.FileOptions.newBuilder().setJavaGenericServices(true).build())
                .build();
        assertThat(ProtoUtils.isJavaGenericServicesEnabled(proto3WithOption)).isTrue();

        DescriptorProtos.FileDescriptorProto proto2 = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setSyntax("proto2")
                .build();
        assertThat(ProtoUtils.isJavaGenericServicesEnabled(proto2)).isTrue();

        DescriptorProtos.FileDescriptorProto protoDefault = DescriptorProtos.FileDescriptorProto.newBuilder()
                .build();
        assertThat(ProtoUtils.isJavaGenericServicesEnabled(protoDefault)).isTrue();

        DescriptorProtos.FileDescriptorProto proto2WithOption = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setSyntax("proto2")
                .setOptions(DescriptorProtos.FileOptions.newBuilder().setJavaGenericServices(false).build())
                .build();
        assertThat(ProtoUtils.isJavaGenericServicesEnabled(proto2WithOption)).isFalse();
    }
}
