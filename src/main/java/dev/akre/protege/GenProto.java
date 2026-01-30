package dev.akre.protege;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


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

    /**
     * The name of the generated Protobuf message.
     * Defaults to the interface name if empty.
     * <p>
     * <b>Note:</b> If the generated Protobuf message has the same name as this interface
     * and is generated in the same package, it will cause a name conflict during compilation.
     * To avoid this, consider using a different name for the message or generating the
     * Protobuf code into a different package (using {@link #pkg()}).
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
