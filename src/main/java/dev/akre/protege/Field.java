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
     * Enumeration of supported Protobuf field types.
     * <p>
     * These correspond directly to {@link FieldDescriptorProto.Type}.
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
        /** UInt64 type. */
        TYPE_UINT64(FieldDescriptorProto.Type.TYPE_UINT64),
        /** Int32 type. */
        TYPE_INT32(FieldDescriptorProto.Type.TYPE_INT32),
        /** Fixed64 type. */
        TYPE_FIXED64(FieldDescriptorProto.Type.TYPE_FIXED64),
        /** Fixed32 type. */
        TYPE_FIXED32(FieldDescriptorProto.Type.TYPE_FIXED32),
        /** Bool type. */
        TYPE_BOOL(FieldDescriptorProto.Type.TYPE_BOOL),
        /** String type. */
        TYPE_STRING(FieldDescriptorProto.Type.TYPE_STRING),
        /** Group type (deprecated). */
        TYPE_GROUP(FieldDescriptorProto.Type.TYPE_GROUP),
        /** Message type. */
        TYPE_MESSAGE(FieldDescriptorProto.Type.TYPE_MESSAGE),
        /** Bytes type. */
        TYPE_BYTES(FieldDescriptorProto.Type.TYPE_BYTES),
        /** UInt32 type. */
        TYPE_UINT32(FieldDescriptorProto.Type.TYPE_UINT32),
        /** Enum type. */
        TYPE_ENUM(FieldDescriptorProto.Type.TYPE_ENUM),
        /** SFixed32 type. */
        TYPE_SFIXED32(FieldDescriptorProto.Type.TYPE_SFIXED32),
        /** SFixed64 type. */
        TYPE_SFIXED64(FieldDescriptorProto.Type.TYPE_SFIXED64),
        /** SInt32 type. */
        TYPE_SINT32(FieldDescriptorProto.Type.TYPE_SINT32),
        /** SInt64 type. */
        TYPE_SINT64(FieldDescriptorProto.Type.TYPE_SINT64);

        private final FieldDescriptorProto.Type protoType;

        ProtoFieldType(FieldDescriptorProto.Type protoType) {
            this.protoType = protoType;
        }

        public void  ifPresent(Consumer<FieldDescriptorProto.Type> action) {
            getProtoType().ifPresent(action);
        }

        public Optional<FieldDescriptorProto.Type> getProtoType() {
            return Optional.ofNullable(this.protoType);
        }

        public int getNumber() {
            return this.protoType.getNumber();
        }
    }
}
