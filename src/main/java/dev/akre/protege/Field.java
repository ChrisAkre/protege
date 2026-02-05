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
     * Enumeration of Protobuf field types supported by the annotation.
     */
    enum ProtoFieldType {
        /** Type not specified. */
        TYPE_UNSPECIFIED(null),
        /** Double type. */
        TYPE_DOUBLE(FieldDescriptorProto.Type.TYPE_DOUBLE),
        /** Float type. */
        TYPE_FLOAT(FieldDescriptorProto.Type.TYPE_FLOAT),
        /** Int64 type. */
        TYPE_INT64(FieldDescriptorProto.Type.TYPE_INT64),
        /** Uint64 type. */
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        /** Int32 type. */
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        /** Fixed64 type. */
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        /** Fixed32 type. */
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        /** Boolean type. */
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        /** String type. */
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        /** Group type. */
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        /** Message type. */
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        /** Bytes type. */
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        /** Uint32 type. */
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        /** Enum type. */
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        /** Sfixed32 type. */
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        /** Sfixed64 type. */
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        /** Sint32 type. */
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        /** Sint64 type. */
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        /**
         * Executes the given action if the underlying Protobuf type is present (not null).
         *
         * @param action the consumer to execute
         */
        public void  ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        /**
         * Returns the underlying Protobuf FieldDescriptorProto.Type.
         *
         * @return an Optional containing the type, or empty if unspecified
         */
        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        /**
         * Returns the numeric value of the type as defined in the Protobuf specification.
         *
         * @return the type number
         */
        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
