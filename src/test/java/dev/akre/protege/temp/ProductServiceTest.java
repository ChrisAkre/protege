package dev.akre.protege.temp;

import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.TestProtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class ProductServiceTest {

    @Test
    public void testMatchingMethods() {
        Arguments inventoryArg = TestProtos.GENERIC_SERVICES.stream().filter(a -> ((Class)a.get()[3]).getSimpleName().equals("ProductService")).findAny().orElseThrow();
        Class<?> expected = ((Class)inventoryArg.get()[3]);
        Class<?> generated = ((Class)inventoryArg.get()[4]);

        ClassAssert.assertThat(generated)
                .hasPublicMethodsEqualTo(expected);
    }

}
