package dev.akre.protege;

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Annotates a method in a Java interface to define a Protobuf field.
 * <p>
 * This annotation is used by the Java-to-Proto generator to map Java interface methods
 * to Protobuf message fields.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Field {
    /**
     * The field number (tag) in the Protobuf message.
     *
     * @return The unique field number.
     */
    int value();

    /**
     * The Protobuf data type of the field.
     * Defaults to TYPE_UNSPECIFIED
     *
     * @return The field type.
     */
    ProtoFieldType type() default ProtoFieldType.TYPE_UNSPECIFIED;

    /**
     * The name of the 'oneof' group this field belongs to, if any.
     *
     * @return The oneof group name, or empty string if not in a oneof.
     */
    String oneof() default "";

    /**
     * Represents the underlying Protobuf field type.
     */
    enum ProtoFieldType {
        TYPE_UNSPECIFIED(null),
        TYPE_DOUBLE(FieldDescriptorProto.Type.TYPE_DOUBLE),
        TYPE_FLOAT(FieldDescriptorProto.Type.TYPE_FLOAT),
        TYPE_INT64(FieldDescriptorProto.Type.TYPE_INT64),
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        /**
         * Executes the given action if a valid underlying Protobuf field type is present.
         * @param action the action to execute
         */
        public void  ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        /**
         * Gets the underlying Protobuf field type, if specified.
         * @return an Optional containing the Protobuf field type, or empty if unspecified
         */
        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        /**
         * Gets the Protobuf tag number for this field type.
         * @return the tag number
         */
        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
