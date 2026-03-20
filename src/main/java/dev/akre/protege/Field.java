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
     * Enum corresponding to Protobuf field types.
     */
    enum ProtoFieldType {
        /** Unspecified. */
        TYPE_UNSPECIFIED(null),
        /** Double. */
        TYPE_DOUBLE(FieldDescriptorProto.Type.TYPE_DOUBLE),
        /** Float. */
        TYPE_FLOAT(FieldDescriptorProto.Type.TYPE_FLOAT),
        /** Int64. */
        TYPE_INT64(FieldDescriptorProto.Type.TYPE_INT64),
        /** UInt64. */
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        /** Int32. */
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        /** Fixed64. */
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        /** Fixed32. */
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        /** Bool. */
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        /** String. */
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        /** Group. */
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        /** Message. */
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        /** Bytes. */
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        /** UInt32. */
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        /** Enum. */
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        /** SFixed32. */
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        /** SFixed64. */
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        /** SInt32. */
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        /** SInt64. */
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        /**
         * Safely performs the given action on the underlying protobuf type if it is present.
         *
         * @param action the action to perform
         */
        public void ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        /**
         * Returns the optional underlying protobuf type.
         *
         * @return the optional protobuf type
         */
        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        /**
         * Returns the proto type number.
         *
         * @return the proto type number
         */
        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
