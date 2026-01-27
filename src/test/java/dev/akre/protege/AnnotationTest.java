package dev.akre.protege;

import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
                  option (dev.akre.protege.java_message_annotation) = '@org.junit.jupiter.api.Tag("test")';
                
                  int64 id = 1 [
                    (dev.akre.protege.java_field_annotation) = '@java.lang.Deprecated(since = "1.0")',
                    (dev.akre.protege.java_field_annotation) = '@java.beans.BeanProperty(description = "The user id")'
                  ];
                
                  string email = 2 [
                    (dev.akre.protege.java_field_annotation) = '@java.lang.Deprecated(forRemoval = true)'
                  ];

                  repeated string tags = 3 [
                    (dev.akre.protege.java_field_annotation) = '@java.lang.Deprecated'
                  ];

                  map<string, string> attributes = 4 [
                    (dev.akre.protege.java_field_annotation) = '@java.lang.Deprecated'
                  ];
                }
                """;

        var parsedProto = ProtoUtils.parseProto(protoContent, "annotations.proto");
        var mockFiler = new TestUtils.MockFiler();
        ProtoCodegen codegen = new ProtoCodegen(mockFiler);
        
        var javaFileObject = codegen.generateFile(parsedProto);
        
        String outerClassName = "com.example.annotations.AnnotationProto";
        Class<?> outerClass = TestUtils.compile(outerClassName, javaFileObject.toJavaFileObject());
        Class<?> userClass = Arrays.stream(outerClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("User"))
                .findFirst()
                .orElseThrow();

        // Message level annotations
        assertThat(userClass.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(userClass.isAnnotationPresent(org.junit.jupiter.api.Tag.class)).isTrue();
        assertThat(userClass.getAnnotation(org.junit.jupiter.api.Tag.class).value()).isEqualTo("test");

        // OrBuilder interface
        Class<?> userOrBuilder = Arrays.stream(outerClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("UserOrBuilder"))
                .findFirst()
                .orElseThrow();
        assertThat(userOrBuilder.isAnnotationPresent(Deprecated.class)).isTrue();
        assertThat(userOrBuilder.isAnnotationPresent(org.junit.jupiter.api.Tag.class)).isTrue();
        assertThat(userOrBuilder.getAnnotation(org.junit.jupiter.api.Tag.class).value()).isEqualTo("test");

        // Field annotations on Message getters
        checkFieldAnnotation(userClass, "getId", Deprecated.class);
        checkFieldAnnotation(userClass, "getId", java.beans.BeanProperty.class);
        checkFieldAnnotation(userClass, "getEmail", Deprecated.class);
        checkFieldAnnotation(userClass, "getTagsList", Deprecated.class);
        checkFieldAnnotation(userClass, "getAttributesMap", Deprecated.class);

        // OrBuilder getters
        checkFieldAnnotation(userOrBuilder, "getId", Deprecated.class);
        checkFieldAnnotation(userOrBuilder, "getId", java.beans.BeanProperty.class);
        checkFieldAnnotation(userOrBuilder, "getEmail", Deprecated.class);
        checkFieldAnnotation(userOrBuilder, "getTagsList", Deprecated.class);
        checkFieldAnnotation(userOrBuilder, "getAttributesMap", Deprecated.class);

        // Builder getters
        Class<?> userBuilder = Arrays.stream(userClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals("Builder"))
                .findFirst()
                .orElseThrow();
        checkFieldAnnotation(userBuilder, "getId", Deprecated.class);
        checkFieldAnnotation(userBuilder, "getId", java.beans.BeanProperty.class);
        checkFieldAnnotation(userBuilder, "getEmail", Deprecated.class);
        checkFieldAnnotation(userBuilder, "getTagsList", Deprecated.class);
        checkFieldAnnotation(userBuilder, "getAttributesMap", Deprecated.class);

        // Check values on Message
        Deprecated idDeprecated = userClass.getMethod("getId").getAnnotation(Deprecated.class);
        assertThat(idDeprecated.since()).isEqualTo("1.0");

        java.beans.BeanProperty idBeanProperty = userClass.getMethod("getId").getAnnotation(java.beans.BeanProperty.class);
        assertThat(idBeanProperty.description()).isEqualTo("The user id");

        Deprecated emailDeprecated = userClass.getMethod("getEmail").getAnnotation(Deprecated.class);
        assertThat(emailDeprecated.forRemoval()).isTrue();

        // Ensure OTHER getters don't have annotations
        // Message
        assertThat(userClass.getMethod("getEmailBytes").isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userClass.getMethod("getTags", int.class).isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userClass.getMethod("getTagsCount").isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userClass.getMethod("getAttributesOrDefault", String.class, String.class).isAnnotationPresent(Deprecated.class)).isFalse();

        // Builder
        assertThat(userBuilder.getMethod("getEmailBytes").isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userBuilder.getMethod("getTags", int.class).isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userBuilder.getMethod("getTagsCount").isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(userBuilder.getMethod("getAttributesOrDefault", String.class, String.class).isAnnotationPresent(Deprecated.class)).isFalse();

        // Ensure private fields are NOT annotated
        assertThat(userClass.getDeclaredField("id_").getAnnotations()).isEmpty();
        assertThat(userClass.getDeclaredField("email_").getAnnotations()).isEmpty();
        assertThat(userBuilder.getDeclaredField("id_").getAnnotations()).isEmpty();
        assertThat(userBuilder.getDeclaredField("email_").getAnnotations()).isEmpty();
    }

    private void checkFieldAnnotation(Class<?> clazz, String methodName, Class<? extends Annotation> annotationClass) throws NoSuchMethodException {
        Method method = Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(methodName + " in " + clazz.getName()));
        assertThat(method.isAnnotationPresent(annotationClass))
                .as("Method %s in %s should have annotation %s", methodName, clazz.getSimpleName(), annotationClass.getSimpleName())
                .isTrue();
    }
}