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
     * Enumerates the available Protobuf field types.
     * This maps directly to the types defined in the Google Protocol Buffers specification.
     */
    enum ProtoFieldType {
        /** Unspecified type. Used as a default to signify that the type should be inferred. */
        TYPE_UNSPECIFIED(null),
        /** Double precision floating point type. */
        TYPE_DOUBLE(FieldDescriptorProto.Type.TYPE_DOUBLE),
        /** Single precision floating point type. */
        TYPE_FLOAT(FieldDescriptorProto.Type.TYPE_FLOAT),
        /** 64-bit integer type. */
        TYPE_INT64(FieldDescriptorProto.Type.TYPE_INT64),
        /** 64-bit unsigned integer type. */
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        /** 32-bit integer type. */
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        /** Fixed-length 64-bit integer type. */
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        /** Fixed-length 32-bit integer type. */
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        /** Boolean type. */
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        /** String type. */
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        /** Group type (deprecated in Protobuf but included for completeness). */
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        /** Message type (for nested messages). */
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        /** Bytes type. */
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        /** 32-bit unsigned integer type. */
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        /** Enumeration type. */
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        /** Fixed-length 32-bit integer type with sign bit. */
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        /** Fixed-length 64-bit integer type with sign bit. */
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        /** Signed 32-bit integer type. */
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        /** Signed 64-bit integer type. */
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        /**
         * Executes the given action if the underlying Protobuf type is present.
         *
         * @param action the action to execute
         */
        public void  ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        /**
         * Returns an Optional containing the underlying Protobuf type, or an empty Optional if unspecified.
         *
         * @return an Optional of the Protobuf type
         */
        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        /**
         * Returns the integer value representing the underlying Protobuf type.
         *
         * @return the integer value of the underlying type
         */
        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
