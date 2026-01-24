package dev.akre.protege.temp;

import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StudentTest {

    @Test
    public void testWorldMethods() throws Exception {
        Arguments arg = TestProtos.ALL_MESSAGES.stream()
                .filter(a -> a.get()[2].equals("Student"))
                .findFirst()
                .orElseThrow();
        
        Class<?> expected = (Class<?>) arg.get()[3];
        Class<?> generated = (Class<?>) arg.get()[4];

        // Check message methods
        ClassAssert.assertThat(generated)
                .hasMethodsEqualTo(expected);

        // Check builder methods
        Class<?> expectedBuilder = TestUtils.getBuilder(expected).getClass();
        Class<?> generatedBuilder = TestUtils.getBuilder(generated).getClass();

        ClassAssert.assertThat(generatedBuilder)
                .hasMethodsEqualTo(expectedBuilder);
    }
}
