package dev.akre.protege;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

@DisplayName("ProtoCompilerProcessor Annotation Processor Tests")
public class ProtoCompilerProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should successfully compile Java source from a proto file using the processor")
    public void shouldSuccessfullyCompileProtoFile() throws IOException {
        // Setup proto directory and a sample proto file
        Path protoDir = tempDir.resolve("src/main/proto");
        Files.createDirectories(protoDir);
        Path protoFile = protoDir.resolve("helloworld.proto");
        Files.writeString(protoFile, """
                syntax = "proto3";
                package com.example;
                option java_package = "com.example.generated";
                message HelloWorld {
                  string content = 1;
                }
                """);

        // HelloWorld.java without any annotations
        JavaFileObject helloWorldSource = JavaFileObjects.forSourceString(
                "com.example.HelloWorld",
                """
                package com.example;
                public class HelloWorld {}
                """
        );

        // Compile with ProtoCompilerProcessor
        // We initialize the compilation by providing -AprotoDir pointing to our proto directory
        Compilation compilation = javac()
                .withProcessors(new ProtoCompilerProcessor())
                .withOptions("-AprotoDir=" + protoDir.toAbsolutePath())
                .compile(helloWorldSource);

        // Verification
        assertThat(compilation).succeeded();
        
        // ProtoUtils.getJavaOuterClassName for helloworld.proto -> Helloworld
        var generatedSourceFile = assertThat(compilation)
                .generatedSourceFile("com.example.generated.Helloworld");
        
        generatedSourceFile.contentsAsUtf8String().contains("public static final class HelloWorld");
        generatedSourceFile.contentsAsUtf8String().contains("public String getContent()");
    }
}