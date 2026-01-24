package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Field {
    int value();
    DescriptorProtos.FieldDescriptorProto.Type type() default DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32;
    String oneof() default "";
}
