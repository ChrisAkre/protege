package dev.akre.it;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonTest {

    @Test
    void testSingleFieldMessageAnnotations() {
        Class<?> clazz = SingleFieldMessage.class;
        assertThat(clazz).hasAnnotation(JsonDeserialize.class);
        JsonDeserialize jsonDeserialize = clazz.getAnnotation(JsonDeserialize.class);
        assertThat(jsonDeserialize.builder()).isEqualTo(SingleFieldMessage.Builder.class);

        assertThat(clazz).hasAnnotation(JsonIgnoreProperties.class);
        JsonIgnoreProperties jsonIgnoreProperties = clazz.getAnnotation(JsonIgnoreProperties.class);
        assertThat(jsonIgnoreProperties.ignoreUnknown()).isTrue();

        Class<?> builderClass = SingleFieldMessage.Builder.class;
        assertThat(builderClass).hasAnnotation(JsonPOJOBuilder.class);
        JsonPOJOBuilder jsonPOJOBuilder = builderClass.getAnnotation(JsonPOJOBuilder.class);
        assertThat(jsonPOJOBuilder.withPrefix()).isEqualTo("set");
    }

    @Test
    void testFieldAnnotation() throws NoSuchMethodException {
        Method method = SingleFieldMessage.class.getMethod("getValue");
        assertThat(method).hasAnnotation(JsonValue.class);
    }

    @Test
    void testSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SingleFieldMessage message = SingleFieldMessage.newBuilder().setValue("test").build();

        // @JsonValue on getValue() means it serializes to just the string "test"
        String json = mapper.writeValueAsString(message);
        assertThat(json).isEqualTo("\"test\"");
    }

    @Test
    void testMultiFieldMessageAnnotations() throws NoSuchMethodException {
        Method method1 = MultiFieldMessage.class.getMethod("getName");
        assertThat(method1).hasAnnotation(JsonValue.class);

        Method method2 = MultiFieldMessage.class.getMethod("getId");
        assertThat(method2).hasAnnotation(JsonValue.class);

        // Serialization should fail because multiple @JsonValue
        ObjectMapper mapper = new ObjectMapper();
        MultiFieldMessage message = MultiFieldMessage.newBuilder().setName("test").setId(123).build();

        assertThatThrownBy(() -> mapper.writeValueAsString(message))
            .isInstanceOf(Exception.class);
            // Jackson throws InvalidDefinitionException or similiar
    }
}
