package dev.akre.protege;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

@DisplayName("ProtoAnnotationProcessor Tests")
public class ProtoProcessorTest {

    @Test
    @DisplayName("Should generate a .proto file from an interface annotated with @GenProto")
    public void shouldGenerateProtoFileFromAnnotatedInterface() {
        JavaFileObject sourceFile = JavaFileObjects.forSourceLines(
                "dev.akre.protege.TestInterface",
                "package dev.akre.protege;",
                "",
                "import dev.akre.protege.GenProto;",
                "",
                "@GenProto",
                "public interface TestInterface {",
                "    String getName();",
                "    int getId();",
                "}"
        );

        Compilation compilation = javac()
                .withProcessors(new ProtoAnnotationProcessor())
                .compile(sourceFile);

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.SOURCE_OUTPUT, "dev/akre/protege/TestInterface.proto")
                .contentsAsUtf8String().isEqualTo("""
                        syntax = "proto3";
                    
                        package dev.akre.protege;
                    
                        message TestInterface {  string name = 1;
                          int32 id = 2;
                        }
                        """);
    }
}