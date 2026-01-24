package dev.akre.protege.temp;

import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.TestProtos;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class InventoryBuilderTest {

    @Test
    public void testMatchingMethods() {
        Arguments inventoryArg = TestProtos.ALL_MESSAGES.stream().filter(a -> ((Class)a.get()[3]).getSimpleName().equals("Inventory")).findAny().orElseThrow();
        Class<?> expected = ((Class)inventoryArg.get()[3]);
        Class<?> generated = ((Class)inventoryArg.get()[4]);

        ClassAssert.assertThat(TestUtils.getBuilder(generated).getClass())
                .hasMethodsEqualTo(TestUtils.getBuilder(expected).getClass());
    }
}
