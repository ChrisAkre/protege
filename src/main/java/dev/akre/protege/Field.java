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
     * Enumeration of Protobuf field types.
     * <p>
     * Wraps standard {@link FieldDescriptorProto.Type} constants.
     */
    enum ProtoFieldType {
        /** Unspecified or inferred type. */
        TYPE_UNSPECIFIED(null),
        /** double type. */
        TYPE_DOUBLE(FieldDescriptorProto.Type.TYPE_DOUBLE),
        /** float type. */
        TYPE_FLOAT(FieldDescriptorProto.Type.TYPE_FLOAT),
        /** int64 (long) type. */
        TYPE_INT64(FieldDescriptorProto.Type.TYPE_INT64),
        /** uint64 (unsigned long) type. */
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        /** int32 (int) type. */
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        /** fixed64 type. */
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        /** fixed32 type. */
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        /** bool type. */
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        /** string type. */
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        /** group type (deprecated). */
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        /** message type. */
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        /** bytes type. */
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        /** uint32 type. */
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        /** enum type. */
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        /** sfixed32 type. */
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        /** sfixed64 type. */
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        /** sint32 type. */
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        /** sint64 type. */
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        /**
         * Executes an action if a valid Protobuf type is present.
         *
         * @param action The action to perform.
         */
        public void  ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        /**
         * Returns the underlying Protobuf {@link FieldDescriptorProto.Type}.
         *
         * @return An Optional containing the type, or empty if unspecified.
         */
        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        /**
         * Returns the numeric value of the type.
         *
         * @return The type number.
         */
        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
