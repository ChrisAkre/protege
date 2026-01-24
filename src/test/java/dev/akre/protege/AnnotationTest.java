package dev.akre.protege;

import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationTest {

    @Test
    public void testAnnotations() throws Exception {
        String protoContent = """
                syntax = "proto3";
                
                package dev.akre.protege.annotations;
                
                option java_package = "com.example.annotations";
                option java_outer_classname = "AnnotationProto";
                
                message User {
                  option (dev.akre.protege.java_message_annotation) = "@java.lang.Deprecated";
                  option (dev.akre.protege.java_message_annotation) = "@java.lang.annotation.Documented";
                
                  int64 id = 1 [
                    (dev.akre.protege.java_annotation) = "@java.lang.Deprecated(since = \\"1.0\\")",
                    (dev.akre.protege.java_annotation) = "@java.lang.annotation.Documented"
                  ];
                
                  string email = 2 [
                    (dev.akre.protege.java_annotation) = "@java.lang.Deprecated(forRemoval = true)"
                  ];
                }
                """;

        var parsedProto = ProtoUtils.parseProto(protoContent, "annotations.proto");
        var mockFiler = new TestUtils.MockFiler();
        ProtoCodegen codegen = new ProtoCodegen(mockFiler);
        String outerClassName = "com.example.annotations.AnnotationProto";
        
        var javaFileObject = codegen.generateFile(parsedProto);
        
        // We use the generated source content because the implementation is expected to fail to include these strings for now.
        String userSource = javaFileObject.getCharContent(false).toString();

        assertThat(userSource).contains("@java.lang.Deprecated");
        assertThat(userSource).contains("@java.lang.annotation.Documented");
        assertThat(userSource).contains("@java.lang.Deprecated(since = \"1.0\")");
        assertThat(userSource).contains("@java.lang.Deprecated(forRemoval = true)");
    }
}
