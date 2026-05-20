package dev.akre.protege.parser;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InvalidProtoExceptionTest {

    @Test
    void testInvalidProtoExceptionMessageOnly() {
        InvalidProtoException exception = new InvalidProtoException("error message");
        assertThat(exception.getMessage()).isEqualTo("error message");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void testInvalidProtoExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("cause");
        InvalidProtoException exception = new InvalidProtoException("error message", cause);
        assertThat(exception.getMessage()).isEqualTo("error message");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void testInvalidProtoExceptionFullConstructor() {
        Throwable cause = new RuntimeException("cause");
        InvalidProtoException exception = new InvalidProtoException("error message", "file.proto", cause);
        assertThat(exception.getMessage()).isEqualTo("'error message' when processing file.proto");
        // This is expected to fail until fixed
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void testInvalidProtoExceptionInMethod() {
        InvalidProtoException original = new InvalidProtoException("error message");
        InvalidProtoException exception = original.in("file.proto");

        assertThat(exception.getMessage()).isEqualTo("'error message' when processing file.proto");
        assertThat(exception.getCause()).isEqualTo(original);
        assertThat(exception.getStackTrace()).isEqualTo(original.getStackTrace());
    }

    @Test
    void testTypeNotFoundExceptionSimple() {
        TypeNotFoundException exception = new TypeNotFoundException("MyType");
        assertThat(exception.getMessage()).isEqualTo("Type 'MyType' not found");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void testTypeNotFoundExceptionFull() {
        Throwable cause = new RuntimeException("cause");
        TypeNotFoundException exception = new TypeNotFoundException("MyType", "file.proto", cause);
        assertThat(exception.getMessage()).isEqualTo("Type 'MyType' not found in 'file.proto'");
        assertThat(exception.getCause()).isEqualTo(cause);
    }
}
