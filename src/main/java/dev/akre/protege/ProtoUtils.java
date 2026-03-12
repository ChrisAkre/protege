package dev.akre.protege;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.parser.ProtobufFileDescriptorVisitor;
import dev.akre.util.Cons;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static java.util.function.Predicate.not;

public class ProtoUtils {


    public static final Map<Class<?>, DescriptorProtos.FieldDescriptorProto.Type> JAVA_TYPES = Map.ofEntries(
                entry(String.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING),
                entry(int.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32),
                entry(Integer.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32),
                entry(long.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64),
                entry(Long.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64),
                entry(float.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT),
                entry(Float.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT),
                entry(double.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE),
                entry(Double.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE),
                entry(boolean.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL),
                entry(Boolean.class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL),
                entry(byte[].class, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)
        );
    public static final Map<DescriptorProtos.FieldDescriptorProto.Type, String> PROTO_TYPES =  Map.ofEntries(
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, "string"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, "int32"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, "int64"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, "float"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, "double"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, "bool"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, "bytes")
        );

    public static DescriptorProtos.FileDescriptorProto parseProto(File file) throws IOException {
        CharStream input = CharStreams.fromPath(file.toPath());
        return parseProto(input, file.getName());
    }

    public static DescriptorProtos.FileDescriptorProto parseProto(String protoContent) {
        return parseProto(protoContent, null);
    }

    public static DescriptorProtos.FileDescriptorProto parseProto(String protoContent, String filename) {
        CharStream input = CharStreams.fromString(protoContent);
        return parseProto(input, filename);
    }

    public static String getStringLiteral(String text) {
        if (text.length() <= 2) {
            return "";
        }
        return StringEscapeUtils.unescapeJava(text.substring(1, text.length() - 1));
    }

    private static DescriptorProtos.FileDescriptorProto parseProto(CharStream input, String filename) {
        ProtobufLexer lexer = new ProtobufLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ProtobufParser parser = new ProtobufParser(tokens);
        DescriptorProtos.FileDescriptorProto.Builder builder = ProtobufFileDescriptorVisitor.parseProto(filename, parser.proto());

        if (filename != null) {
            builder.setName(filename);
        } else {
            String pkg = builder.getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                String[] parts = pkg.split("\\.");
                builder.setName(parts[parts.length - 1] + ".proto");
            }
        }
        return builder.build();
    }


    public static DescriptorProtos.FieldDescriptorProto.Type fieldTypeForName(String typeName) {
        return switch (typeName) {
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
            default -> throw new IllegalArgumentException("unknown type: " + typeName);
        };
    }

    public static String getJavaPackage(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        return fileDescriptorProto.getOptions().hasJavaPackage()
                ? fileDescriptorProto.getOptions().getJavaPackage()
                : fileDescriptorProto.getPackage();
    }

    public static String getJavaOuterClassName(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        if (fileDescriptorProto.getOptions().hasJavaOuterClassname()) {
            return fileDescriptorProto.getOptions().getJavaOuterClassname();
        }
        String fileName = fileDescriptorProto.getName();
        String baseName = (fileName.contains("/")
                ? fileName.substring(fileName.lastIndexOf("/") + 1)
                : fileName).replace(".proto", "");
        return getNames(fileDescriptorProto).anyMatch(baseName::equalsIgnoreCase)
                ? (toPascalCase(baseName) + "OuterClass")
                : toPascalCase(baseName);

    }

    private static Stream<String> getNames(Object descriptor) {
        return switch (descriptor) {
            case DescriptorProtos.FileDescriptorProto f -> Stream.of(
                    f.getMessageTypeList().stream().flatMap(ProtoUtils::getNames),
                    f.getEnumTypeList().stream().flatMap(ProtoUtils::getNames))
                    .flatMap(s -> s);
            case DescriptorProtos.DescriptorProto m -> Stream.of(
                            Stream.of(m.getName()),
                            m.getNestedTypeList().stream().flatMap(ProtoUtils::getNames),
                            m.getEnumTypeList().stream().flatMap(ProtoUtils::getNames))
                    .flatMap(s -> s);
            case DescriptorProtos.EnumDescriptorProto e -> Stream.of(e.getName());
            default -> throw new IllegalArgumentException("unexpected: " + descriptor);
        };
    }

    public static String toPascalCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.contains("_")) {
            return CaseUtils.toCamelCase(s, true, '_');
        }
        return StringUtils.capitalize(s);
    }

    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.contains("_")) {
            return CaseUtils.toCamelCase(s, false, '_');
        }
        return StringUtils.uncapitalize(s);
    }

    public static List<String> splitAndEscapeBytes(byte[] bytes) {
        List<String> result = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            switch (b) {
                case '\n' -> {
                    builder.append("\\n");
                    result.add(builder.toString());
                    builder.setLength(0);
                }
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                default -> {
                    if (b >= 32 && b <= 126) {
                        builder.append((char) b);
                    } else {
                        builder.append('\\');
                        int v = b & 0xFF;
                        builder.append((char) ('0' + ((v >> 6) & 7)));
                        builder.append((char) ('0' + ((v >> 3) & 7)));
                        builder.append((char) ('0' + (v & 7)));
                    }
                }
            }
        }
        result.add(builder.toString());
        return result;
    }

    public static String getWriteMethodName(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> "writeDouble";
            case TYPE_FLOAT -> "writeFloat";
            case TYPE_INT64 -> "writeInt64";
            case TYPE_UINT64 -> "writeUInt64";
            case TYPE_INT32 -> "writeInt32";
            case TYPE_FIXED64 -> "writeFixed64";
            case TYPE_FIXED32 -> "writeFixed32";
            case TYPE_BOOL -> "writeBool";
            case TYPE_STRING -> "writeString";
            case TYPE_MESSAGE -> "writeMessage";
            case TYPE_BYTES -> "writeBytes";
            case TYPE_UINT32 -> "writeUInt32";
            case TYPE_ENUM -> "writeEnum";
            case TYPE_SFIXED32 -> "writeSFixed32";
            case TYPE_SFIXED64 -> "writeSFixed64";
            case TYPE_SINT32 -> "writeSInt32";
            case TYPE_SINT64 -> "writeSInt64";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    public static String getReadMethodName(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> "readDouble";
            case TYPE_FLOAT -> "readFloat";
            case TYPE_INT64 -> "readInt64";
            case TYPE_UINT64 -> "readUInt64";
            case TYPE_INT32 -> "readInt32";
            case TYPE_FIXED64 -> "readFixed64";
            case TYPE_FIXED32 -> "readFixed32";
            case TYPE_BOOL -> "readBool";
            case TYPE_STRING -> "readString";
            case TYPE_MESSAGE -> "readMessage";
            case TYPE_BYTES -> "readBytes";
            case TYPE_UINT32 -> "readUInt32";
            case TYPE_ENUM -> "readEnum";
            case TYPE_SFIXED32 -> "readSFixed32";
            case TYPE_SFIXED64 -> "readSFixed64";
            case TYPE_SINT32 -> "readSInt32";
            case TYPE_SINT64 -> "readSInt64";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    public static String getComputeMethodName(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> "computeDoubleSize";
            case TYPE_FLOAT -> "computeFloatSize";
            case TYPE_INT64 -> "computeInt64Size";
            case TYPE_UINT64 -> "computeUInt64Size";
            case TYPE_INT32 -> "computeInt32Size";
            case TYPE_FIXED64 -> "computeFixed64Size";
            case TYPE_FIXED32 -> "computeFixed32Size";
            case TYPE_BOOL -> "computeBoolSize";
            case TYPE_STRING -> "computeStringSize";
            case TYPE_MESSAGE -> "computeMessageSize";
            case TYPE_BYTES -> "computeBytesSize";
            case TYPE_UINT32 -> "computeUInt32Size";
            case TYPE_ENUM -> "computeEnumSize";
            case TYPE_SFIXED32 -> "computeSFixed32Size";
            case TYPE_SFIXED64 -> "computeSFixed64Size";
            case TYPE_SINT32 -> "computeSInt32Size";
            case TYPE_SINT64 -> "computeSInt64Size";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    public static com.google.protobuf.WireFormat.FieldType getWireFormatType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_DOUBLE -> com.google.protobuf.WireFormat.FieldType.DOUBLE;
            case TYPE_FLOAT -> com.google.protobuf.WireFormat.FieldType.FLOAT;
            case TYPE_INT64 -> com.google.protobuf.WireFormat.FieldType.INT64;
            case TYPE_UINT64 -> com.google.protobuf.WireFormat.FieldType.UINT64;
            case TYPE_INT32 -> com.google.protobuf.WireFormat.FieldType.INT32;
            case TYPE_FIXED64 -> com.google.protobuf.WireFormat.FieldType.FIXED64;
            case TYPE_FIXED32 -> com.google.protobuf.WireFormat.FieldType.FIXED32;
            case TYPE_BOOL -> com.google.protobuf.WireFormat.FieldType.BOOL;
            case TYPE_STRING -> com.google.protobuf.WireFormat.FieldType.STRING;
            case TYPE_GROUP -> com.google.protobuf.WireFormat.FieldType.GROUP;
            case TYPE_MESSAGE -> com.google.protobuf.WireFormat.FieldType.MESSAGE;
            case TYPE_BYTES -> com.google.protobuf.WireFormat.FieldType.BYTES;
            case TYPE_UINT32 -> com.google.protobuf.WireFormat.FieldType.UINT32;
            case TYPE_ENUM -> com.google.protobuf.WireFormat.FieldType.ENUM;
            case TYPE_SFIXED32 -> com.google.protobuf.WireFormat.FieldType.SFIXED32;
            case TYPE_SFIXED64 -> com.google.protobuf.WireFormat.FieldType.SFIXED64;
            case TYPE_SINT32 -> com.google.protobuf.WireFormat.FieldType.SINT32;
            case TYPE_SINT64 -> com.google.protobuf.WireFormat.FieldType.SINT64;
        };
    }

    public static int getWireType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_INT32, TYPE_INT64, TYPE_UINT32, TYPE_UINT64, TYPE_SINT32, TYPE_SINT64, TYPE_BOOL, TYPE_ENUM -> 0;
            case TYPE_DOUBLE, TYPE_FIXED64, TYPE_SFIXED64 -> 1;
            case TYPE_STRING, TYPE_BYTES, TYPE_MESSAGE -> 2;
            case TYPE_FLOAT, TYPE_FIXED32, TYPE_SFIXED32 -> 5;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    public static String getDefaultReturnValue(String typeName) {
        return switch (typeName) {
            case "boolean" -> "false";
            case "byte" -> "(byte) 0";
            case "short" -> "(short) 0";
            case "int" -> "0";
            case "long" -> "0L";
            case "char" -> "'\\0'";
            case "float" -> "0.0f";
            case "double" -> "0.0d";
            default -> "null";
        };
    }

    public static String qualify(String typeName, Cons<String> scope) {
        return qualify(new StringBuilder(), scope).append('.').append(typeName).toString();
    }

    private static StringBuilder qualify(StringBuilder sb, Cons<String> scope) {
        return switch (scope) {
            case Cons<?> c when c.isEmpty() -> sb;
            case Cons(var head, var tail) -> qualify(sb, tail).append('.').append(head);
        };
    }

    public static DescriptorProtos.UninterpretedOption createUninterpretedOption(String name, String value) {
        return DescriptorProtos.UninterpretedOption.newBuilder()
                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder()
                        .setNamePart(name)
                        .setIsExtension(true)
                        .build())
                .setStringValue(com.google.protobuf.ByteString.copyFromUtf8(value))
                .build();
    }

    public static String toProtoString(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        StringBuilder sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("package ").append(fileDescriptorProto.getPackage()).append(";\n\n");
        if (fileDescriptorProto.getOptions().hasJavaPackage()) {
            sb.append("option java_package = \"").append(fileDescriptorProto.getOptions().getJavaPackage()).append("\";\n\n");
        }

        for (DescriptorProtos.EnumDescriptorProto enumType : fileDescriptorProto.getEnumTypeList()) {
            enumToString(enumType, sb, 0);
        }

        for (DescriptorProtos.DescriptorProto messageType : fileDescriptorProto.getMessageTypeList()) {
            messageToString(messageType, sb, 0);
        }

        return sb.toString();
    }

    public static String messageToString(DescriptorProtos.DescriptorProto messageType) {
        StringBuilder sb = new StringBuilder();
        messageToString(messageType, sb, 0);
        return sb.toString();
    }

    private static void messageToString(DescriptorProtos.DescriptorProto messageType, StringBuilder sb, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        sb.append(indent).append("message ").append(messageType.getName()).append(" {\n");
        if (messageType.getOptions().getMapEntry()) {
            sb.append(indent).append("  option map_entry = true;\n");
        }

        for (DescriptorProtos.EnumDescriptorProto enumType : messageType.getEnumTypeList()) {
            enumToString(enumType, sb, indentLevel + 1);
        }

        for (DescriptorProtos.DescriptorProto nestedType : messageType.getNestedTypeList()) {
            messageToString(nestedType, sb, indentLevel + 1);
        }

        for (DescriptorProtos.FieldDescriptorProto field : messageType.getFieldList().stream().sorted(Comparator.comparing(DescriptorProtos.FieldDescriptorProto::getNumber)).toList()) {
            fieldToString(messageType, field, sb, indentLevel + 1);
        }
        sb.append(indent).append("}\n\n");
    }

    public static String enumToString(DescriptorProtos.EnumDescriptorProto enumType) {
        StringBuilder sb = new StringBuilder();
        enumToString(enumType, sb, 0);
        return sb.toString();
    }

    private static void enumToString(DescriptorProtos.EnumDescriptorProto enumType, StringBuilder sb, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        sb.append(indent).append("enum ").append(enumType.getName()).append(" {\n");
        for (DescriptorProtos.EnumValueDescriptorProto value : enumType.getValueList()) {
            sb.append(indent).append("  ").append(value.getName()).append(" = ").append(value.getNumber()).append(";\n");
        }
        sb.append(indent).append("}\n\n");
    }

    public static String fieldToString(DescriptorProtos.DescriptorProto parentMessage, DescriptorProtos.FieldDescriptorProto field) {
        StringBuilder sb = new StringBuilder();
        fieldToString(parentMessage, field, sb, 0);
        return sb.toString();
    }

    private static void fieldToString(DescriptorProtos.DescriptorProto parentMessage, DescriptorProtos.FieldDescriptorProto field, StringBuilder sb, int indentLevel) {
        String indent = "  ".repeat(indentLevel);
        String typeName = field.hasTypeName() ? field.getTypeName() : PROTO_TYPES.get(field.getType());
        String label = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED ? "repeated " : "";
        if (field.hasTypeName() && field.getTypeName().endsWith("Entry")) {
            // Check if this is a map entry nested in the parent message
            Optional<DescriptorProtos.DescriptorProto> mapEntryMessage = parentMessage.getNestedTypeList().stream()
                    .filter(m -> m.getName().equals(field.getTypeName()) && m.getOptions().getMapEntry())
                    .findAny();
            if (mapEntryMessage.isPresent()) {
                DescriptorProtos.DescriptorProto m = mapEntryMessage.get();
                String keyType = m.getField(0).hasTypeName() ? m.getField(0).getTypeName() : PROTO_TYPES.get(m.getField(0).getType());
                String valueType = m.getField(1).hasTypeName() ? m.getField(1).getTypeName() : PROTO_TYPES.get(m.getField(1).getType());
                sb.append(indent).append("map<").append(keyType).append(", ").append(valueType).append("> ").append(field.getName()).append(" = ").append(field.getNumber()).append(";\n");
                return;
            }
        }

        sb.append(indent).append(label).append(typeName).append(" ").append(field.getName()).append(" = ").append(field.getNumber()).append(";\n");
    }


    public static String annotationToString(java.lang.annotation.Annotation ann) {
        Class<? extends java.lang.annotation.Annotation> type = ann.annotationType();
        String values = Arrays.stream(type.getDeclaredMethods())
                .sorted(Comparator.comparing(Method::getName))
                .filter(not(isDefaultValue(ann)))
                .map(method -> {
                    try {
                        return entry(method.getName(), formatJavaValue(method.invoke(ann)));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(Collectors.collectingAndThen(Collectors.<Map.Entry<String,String>>toList(), ProtoUtils::formatValues));
        return "@" + type.getName() + values;
    }

    private static String formatValues(List<Map.Entry<String,String>> l) {
        var values = (l.size() == 1 && l.getFirst().getKey().equals("value"))
                ? Stream.of(l.getFirst().getValue())
                : l.stream().map(e -> "%s = %s".formatted(e.getKey(), e.getValue()));
        return values.collect(() -> new StringJoiner(", ", "(", ")").setEmptyValue(""), StringJoiner::add, StringJoiner::merge).toString();
    }

    private static Predicate<? super Method> isDefaultValue(Annotation ann) {
        return method -> {
            try {
                Object defaultValue = method.getDefaultValue();
                return (defaultValue != null && Objects.deepEquals(method.invoke(ann), defaultValue));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        };
    }

    public static String formatJavaValue(Object value) {
        return switch (value) {
            case String s -> "\"" + s + "\"";
            case Character c -> "'" + c + "'";
            case Class<?> clazz -> clazz.getSimpleName() + ".class";
            case Enum<?> e -> e.name();
            case null -> "null";
            case Object o when o.getClass().isArray() -> {
                StringBuilder sb = new StringBuilder("{");
                int length = java.lang.reflect.Array.getLength(o);
                for (int i = 0; i < length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(formatJavaValue(java.lang.reflect.Array.get(o, i)));
                }
                sb.append("}");
                yield sb.toString();
            }
            default -> String.valueOf(value);
        };
    }

    public static String getQualifiedName(Class<?> clazz) {
        List<String> names = new LinkedList<>();
        Class<?> current = clazz;
        String pkg = null;
        while (current != null) {
            GenProto gp = current.getAnnotation(GenProto.class);
            if (gp != null) {
                if (pkg == null || !gp.pkg().isEmpty()) {
                    pkg = gp.pkg();
                }
                if (gp.outerClass()) {
                    break;
                }
            }
            names.add(0, current.getSimpleName());
            current = current.getDeclaringClass();
        }
        if (pkg == null || pkg.isEmpty()) {
            Class<?> top = clazz;
            while (top.getDeclaringClass() != null) top = top.getDeclaringClass();
            GenProto gp = top.getAnnotation(GenProto.class);
            pkg = (gp != null && !gp.pkg().isEmpty()) ? gp.pkg() : top.getPackageName();
        }
        return "." + pkg + (names.isEmpty() ? "" : "." + String.join(".", names));
    }

    public static String getTypeName(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        } else if (type instanceof ParameterizedType) {
            return getTypeName(((ParameterizedType) type).getRawType());
        }
        return "Object";
    }

    public static boolean isJavaGenericServicesEnabled(DescriptorProtos.FileDescriptorProto fileDescriptor) {
        if (fileDescriptor.getOptions().hasJavaGenericServices()) {
            return fileDescriptor.getOptions().getJavaGenericServices();
        }
        // Default: false for proto3, true for proto2
        return !"proto3".equals(fileDescriptor.getSyntax());
    }

    public static boolean isStringEmpty(Object value) {
        if (value instanceof String) {
            return ((String) value).isEmpty();
        }
        if (value instanceof com.google.protobuf.ByteString) {
            return ((com.google.protobuf.ByteString) value).isEmpty();
        }
        return value == null;
    }

    public static Stream<Object> descriptorStream(Object descriptor) {
        return Stream.concat(Stream.of(descriptor), descriptorChildren(descriptor));
    }

    public static <T> Stream<T> descriptorChildren(Object descriptor, Class<T> descriptorClass) {
        return descriptorChildren(descriptor).filter(descriptorClass::isInstance).map(descriptorClass::cast);
    }

    public static Stream<Object> descriptorChildren(Object descriptor) {
        return switch (descriptor) {
            case DescriptorProtos.FileDescriptorProto f -> Stream.of(
                            f.getMessageTypeList().stream(),
                            f.getEnumTypeList().stream(),
                            f.getServiceList().stream())
                    .flatMap(s -> s);
            case DescriptorProtos.DescriptorProto m -> Stream.of(
                        m.getNestedTypeList().stream(),
                        m.getEnumTypeList().stream(),
                        m.getFieldList().stream(),
                        m.getOneofDeclList().stream())
                    .flatMap(s -> s);
            case DescriptorProtos.ServiceDescriptorProto s -> s.getMethodList().stream().map(m -> (Object) m);
            case DescriptorProtos.EnumDescriptorProto ignored -> Stream.empty();
            case DescriptorProtos.FieldDescriptorProto ignored -> Stream.empty();
            case DescriptorProtos.OneofDescriptorProto ignored -> Stream.empty();
            case DescriptorProtos.MethodDescriptorProto ignored -> Stream.empty();
            default -> throw new IllegalStateException();
        };
    }

    public static Predicate<List<DescriptorProtos.UninterpretedOption.NamePart>> nameList(String key) {
        return l -> {
            String optionName = l.stream()
                    .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                    .collect(Collectors.joining("."));
            return optionName.equals(key);
        };
    }

    @SafeVarargs
    public static <T> List<T> listConcat(List<T> list, T... elements) {
        if (elements.length == 0) {
            return list;
        }
        List<T> result = new ArrayList<>(list);
        Collections.addAll(result, elements);
        return result;
    }
}
