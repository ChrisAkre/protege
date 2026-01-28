package dev.akre.protege;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


// TODO also look at the code and validate that not only is the .proto file generated, but the actual java implementation is generated as well.
/**
 * Marks a Java interface for Protobuf definition generation.
 * <p>
 * When an interface is annotated with {@code @GenProto}, the annotation processor
 * will generate a corresponding {@code .proto} file based on the interface's structure
 * and {@link Field} annotations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GenProto {

    // TODO this comment doesn't make sense, because you can't have a class with the same name as this interface
    /**
     * The name of the generated Protobuf message.
     * Defaults to the interface name if empty.
     *
     * @return The message name.
     */
    String value() default "";

   /**
     * Whether to wrap the generated message in an outer class (similar to {@code java_outer_classname}).
     *
     * @return {@code true} if an outer class should be generated.
     */
    boolean outerClass() default false;

    /**
     * The Protobuf package name for the generated file.
     * Defaults to the Java package name if empty.
     *
     * @return The protobuf package.
     */
    String pkg() default "";
}
