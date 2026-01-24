package dev.akre.protege;

import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

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

                  repeated string tags = 3 [
                    (dev.akre.protege.java_annotation) = "@java.lang.SafeVarargs"
                  ];

                  map<string, string> attributes = 4 [
                    (dev.akre.protege.java_annotation) = "@java.lang.Deprecated"
                  ];
                }
                """;

        var parsedProto = ProtoUtils.parseProto(protoContent, "annotations.proto");
        var mockFiler = new TestUtils.MockFiler();
        ProtoCodegen codegen = new ProtoCodegen(mockFiler);
        
        var javaFileObject = codegen.generateFile(parsedProto);
        
        // We use the generated source content because the implementation is expected to fail to include these strings for now.
        String userSource = javaFileObject.getCharContent(false).toString();

        assertThat(userSource).contains("@Deprecated");
        assertThat(userSource).contains("@Documented");
        assertThat(userSource).contains("since = \"1.0\"");
        assertThat(userSource).contains("forRemoval = true");
        assertThat(userSource).contains("@SafeVarargs");
    }
}
