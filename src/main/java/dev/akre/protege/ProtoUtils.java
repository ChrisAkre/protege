package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;

import java.util.HashMap;
import java.util.Map;

public class ProtoUtils {



    public static DescriptorProtos.FieldDescriptorProto.Type getFieldType(ProtobufParser.Type_Context ctx) {
        return switch (ctx.getText()) {
            case "double" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE;
            case "float" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT;
            case "int32" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32;
            case "int64" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64;
            case "uint32" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32;
            case "uint64" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64;
            case "sint32" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32;
            case "sint64" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64;
            case "fixed32" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32;
            case "fixed64" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64;
            case "sfixed32" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32;
            case "sfixed64" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64;
            case "bool" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL;
            case "string" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
            case "bytes" -> DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES;
            default -> ctx.messageType() != null
                    ? DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE
                    : DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
        };
    }
}
