package dev.akre.protege.testutil;

import org.assertj.core.api.AbstractAssert;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class ClassAssert extends AbstractAssert<ClassAssert, Class<?>> {

    protected ClassAssert(Class<?> actual) {
        super(actual, ClassAssert.class);
    }

    public static ClassAssert assertThat(Class<?> actual) {
        return new ClassAssert(actual);
    }

    @SuppressWarnings("UnusedReturnValue")
    public ClassAssert hasPublicStaticFinalStringField(String fieldName, String expectedValue) {
        isNotNull();

        try {
            Field field = actual.getDeclaredField(fieldName);
            int modifiers = field.getModifiers();

            // Verify modifiers: public static final
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                failWithMessage("Expected field <%s> to be public static final, but it was not.", fieldName);
            }

            // Verify type is String
            if (!field.getType().equals(String.class)) {
                failWithMessage("Expected field <%s> to be of type String, but was <%s>.",
                        fieldName, field.getType().getName());
            }

            // Verify value
            Object value = field.get(null); // Passing null because it's a static field
            if (!expectedValue.equals(value)) {
                failWithMessage("Expected field <%s> to have value <%s>, but was <%s>.",
                        fieldName, expectedValue, value);
            }

        } catch (NoSuchFieldException e) {
            failWithMessage("Expected class <%s> to have field <%s>, but it was not found.",
                    actual.getName(), fieldName);
        } catch (IllegalAccessException e) {
            failWithMessage("Could not access field <%s> on class <%s>.", fieldName, actual.getName());
        }

        return this;
    }

    public ClassAssert hasMethodsEqualTo(Class<?> expectedClass) {
        isNotNull();

        var expectedMethods = getSignatures(expectedClass);
        var actualMethods = getSignatures(actual);

        if (actual.getName().contains("OptionsOuterClass")) {
            System.out.println("Comparing methods for: " + actual.getName());
            System.out.println("Interfaces for actual: " + Arrays.toString(actual.getInterfaces()));
            System.out.println("Interfaces for expected: " + Arrays.toString(expectedClass.getInterfaces()));
            System.out.println("Actual methods: " + actualMethods);
            System.out.println("Expected methods: " + expectedMethods);
        }

        org.assertj.core.api.Assertions.assertThat(actualMethods)
                .describedAs("Methods in %s should match methods in %s", actual.getName(), expectedClass.getName())
                .containsExactlyInAnyOrderElementsOf(expectedMethods);

        return this;
    }

    public ClassAssert hasPublicMethodsEqualTo(Class<?> expectedClass) {
        isNotNull();

        var expectedMethods = getSignatures(expectedClass, true);
        var actualMethods = getSignatures(actual, true);

        if (actual.getName().contains("OptionsOuterClass")) {
            System.out.println("Comparing methods for: " + actual.getName());
            System.out.println("Interfaces for actual: " + Arrays.toString(actual.getInterfaces()));
            System.out.println("Interfaces for expected: " + Arrays.toString(expectedClass.getInterfaces()));
            System.out.println("Actual methods: " + actualMethods);
            System.out.println("Expected methods: " + expectedMethods);
        }

        org.assertj.core.api.Assertions.assertThat(actualMethods)
                .describedAs("Methods in %s should match methods in %s", actual.getName(), expectedClass.getName())
                .containsExactlyInAnyOrderElementsOf(expectedMethods);

        return this;
    }


    public ClassAssert hasMethod(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        isNotNull();
        try {
            Method method = actual.getMethod(methodName, parameterTypes);
            if (!returnType.isAssignableFrom(method.getReturnType())) {
                failWithMessage("Expected method <%s> on class <%s> to have return type assignable to <%s>, but was <%s>.",
                        methodName, actual.getName(), returnType.getName(), method.getReturnType().getName());
            }
        } catch (NoSuchMethodException e) {
            failWithMessage("Expected class <%s> to have method <%s> with parameter types %s, but it was not found.",
                    actual.getName(), methodName, Arrays.toString(parameterTypes));
        }
        return this;
    }

    public ClassAssert hasDeprecatedMethod(String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        isNotNull();
        try {
            Method method = actual.getMethod(methodName, parameterTypes);
            if (!returnType.isAssignableFrom(method.getReturnType())) {
                failWithMessage("Expected method <%s> on class <%s> to have return type assignable to <%s>, but was <%s>.",
                        methodName, actual.getName(), returnType.getName(), method.getReturnType().getName());
            }
            if (!method.isAnnotationPresent(Deprecated.class)) {
                failWithMessage("Expected method <%s> on class <%s> to have return type assignable to <%s>, but was <%s>.",
                        methodName, actual.getName(), returnType.getName(), method.getReturnType().getName());
            }
        } catch (NoSuchMethodException e) {
            failWithMessage("Expected class <%s> to have method <%s> with parameter types %s, but it was not found.",
                    actual.getName(), methodName, Arrays.toString(parameterTypes));
        }
        return this;
    }

    public ClassAssert hasNoMethod(String methodName, Class<?>... parameterTypes) {
        isNotNull();
        try {
            actual.getMethod(methodName, parameterTypes);
            failWithMessage("Expected class <%s> to NOT have method <%s> with parameter types %s, but it was found.",
                    actual.getName(), methodName, Arrays.toString(parameterTypes));
        } catch (NoSuchMethodException e) {
            // Success
        }
        return this;
    }

    public ClassAssert isMessageClass() {
        isNotNull();
        if (!com.google.protobuf.Message.class.isAssignableFrom(actual)) {
            failWithMessage("Expected class <%s> to be a Protobuf Message, but it was not.", actual.getName());
        }
        return this;
    }

    public ClassAssert isAssignableFrom(Class<?> other) {
        isNotNull();
        if (!actual.isAssignableFrom(other)) {
            failWithMessage("Expected class <%s> to be assignable from <%s>, but it was not.",
                    actual.getName(), other.getName());
        }
        return this;
    }

    public ClassAssert isInterface() {
        isNotNull();
        if (!actual.isInterface()) {
            failWithMessage("Expected class <%s> to be an interface, but it was not.", actual.getName());
        }
        return this;
    }

    public ClassAssert isSealed() {
        isNotNull();
        if (!actual.isSealed()) {
            failWithMessage("Expected class <%s> to be sealed, but it was not.", actual.getName());
        }
        return this;
    }

    public ClassAssert isNonSealed() {
        isNotNull();
        if (actual.isSealed()) {
            failWithMessage("Expected class <%s> to be non-sealed, but it was sealed.", actual.getName());
        }
        return this;
    }

    public ClassAssert permits(Class<?>... expectedSubclasses) {
        isSealed();
        List<Class<?>> permitted = Arrays.asList(actual.getPermittedSubclasses());
        for (Class<?> expected : expectedSubclasses) {
            if (!permitted.contains(expected)) {
                failWithMessage("Expected class <%s> to permit <%s>, but it only permits %s.",
                        actual.getName(), expected.getName(), permitted);
            }
        }
        return this;
    }

    public ClassAssert hasDefaultInstanceEqualTo(Class<?> expectedClass) {
        isNotNull();
        Object actualDefault = TestUtils.getDefaultInstance(actual);
        Object expectedDefault = TestUtils.getDefaultInstance(expectedClass);

        byte[] actualBytes = TestUtils.toByteArray(actualDefault);
        byte[] expectedBytes = TestUtils.toByteArray(expectedDefault);

        org.assertj.core.api.Assertions.assertThat(actualBytes)
                .describedAs("Default instance of %s should have same bytes as %s", actual.getName(), expectedClass.getName())
                .isEqualTo(expectedBytes);

        return this;
    }

    public ClassAssert hasRandomInstanceEqualTo(Class<?> expectedClass) {
        isNotNull();
        Object expectedInstance = TestUtils.getRandomInstance(expectedClass);
        byte[] expectedBytes = TestUtils.toByteArray(expectedInstance);

        Object actualInstance = TestUtils.parseFrom(actual, expectedBytes);
        byte[] actualBytes = TestUtils.toByteArray(actualInstance);

        org.assertj.core.api.Assertions.assertThat(actualBytes)
                .describedAs("Serialization of random instance of %s should be stable", actual.getName())
                .isEqualTo(expectedBytes);

        // Also test the other way around if possible
        Object actualRandom = TestUtils.getRandomInstance(actual);
        byte[] actualRandomBytes = TestUtils.toByteArray(actualRandom);
        Object expectedFromActual = TestUtils.parseFrom(expectedClass, actualRandomBytes);
        byte[] expectedFromActualBytes = TestUtils.toByteArray(expectedFromActual);

        org.assertj.core.api.Assertions.assertThat(expectedFromActualBytes)
                .describedAs("Serialization of random instance from %s should be compatible with %s", actual.getName(), expectedClass.getName())
                .isEqualTo(actualRandomBytes);

        return this;
    }

    private List<MethodSignature> getSignatures(Class<?> clazz) {
        return getSignatures(clazz, false);
    }

    private List<MethodSignature> getSignatures(Class<?> cls, boolean publicOnly) {
        return Arrays.stream(cls.getMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) || !publicOnly)
                .map(MethodSignature::of)
                .toList();
    }

    private record MethodSignature(String name, String modifiers, String returnType, List<String> parameterTypes, boolean isDeprecated) {
        static MethodSignature of(Method m) {
            return new MethodSignature(
                    m.getName(),
                    Modifier.toString(m.getModifiers() & Modifier.methodModifiers()),
                    m.getReturnType().getName(),
                    Arrays.stream(m.getParameterTypes()).map(Class::getName).toList(),
                    m.isAnnotationPresent(Deprecated.class)
            );
        }

        @Override
        public String toString() {
            return (isDeprecated ? "@Deprecated " : "") + modifiers + " " + returnType + " " + name + "(" +
                    String.join(", ", parameterTypes) + ")";
        }
    }


}
