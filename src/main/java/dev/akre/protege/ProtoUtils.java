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

/**
 * Utility class for parsing, analyzing, and transforming Protobuf descriptors and Java types.
 * <p>
 * This class provides a centralized collection of helper methods used throughout the Protege library
 * for tasks such as:
 * <ul>
 *   <li>Parsing {@code .proto} files into {@link DescriptorProtos.FileDescriptorProto} objects.</li>
 *   <li>Mapping between Java types and Protobuf types.</li>
 *   <li>Converting naming conventions (PascalCase, camelCase).</li>
 *   <li>Traversing descriptor hierarchies.</li>
 *   <li>Formatting Java source code (escaping strings, formatting annotations).</li>
 * </ul>
 */
public class ProtoUtils {

    /**
     * Maps standard Java classes to their corresponding Protobuf field types.
     * <p>
     * Used during the analysis of Java interfaces to determine the appropriate Protobuf field type
     * for a given method return type.
     */
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

    /**
     * Maps Protobuf field types to their string representation in a {@code .proto} file.
     * <p>
     * Used when generating {@code .proto} files from Java definitions or when debugging.
     */
    public static final Map<DescriptorProtos.FieldDescriptorProto.Type, String> PROTO_TYPES =  Map.ofEntries(
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, "string"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, "int32"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, "int64"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, "float"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, "double"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, "bool"),
                entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, "bytes")
        );

    /**
     * Parses a {@code .proto} file from the file system.
     *
     * @param file The file to parse.
     * @return The parsed {@link DescriptorProtos.FileDescriptorProto}.
     * @throws IOException If an I/O error occurs reading the file.
     */
    public static DescriptorProtos.FileDescriptorProto parseProto(File file) throws IOException {
        CharStream input = CharStreams.fromPath(file.toPath());
        return parseProto(input, file.getName());
    }

    /**
     * Parses a string containing Protobuf definition content.
     *
     * @param protoContent The content of the {@code .proto} file.
     * @return The parsed {@link DescriptorProtos.FileDescriptorProto}.
     */
    public static DescriptorProtos.FileDescriptorProto parseProto(String protoContent) {
        return parseProto(protoContent, null);
    }

    /**
     * Parses a string containing Protobuf definition content, with an associated filename.
     *
     * @param protoContent The content of the {@code .proto} file.
     * @param filename     The name of the file (used for error reporting and descriptor naming).
     * @return The parsed {@link DescriptorProtos.FileDescriptorProto}.
     */
    public static DescriptorProtos.FileDescriptorProto parseProto(String protoContent, String filename) {
        CharStream input = CharStreams.fromString(protoContent);
        return parseProto(input, filename);
    }

    /**
     * Unescapes a string literal from a Protobuf file, removing surrounding quotes.
     * <p>
     * Protobuf string literals can contain escape sequences similar to Java. This method
     * uses {@link StringEscapeUtils#unescapeJava(String)} to handle them correctly.
     *
     * @param text The raw text of the string literal (including quotes).
     * @return The unescaped string content.
     */
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


    /**
     * Converts a string representation of a type name to the corresponding Protobuf field type.
     *
     * @param typeName The name of the type (e.g., "int32", "string").
     * @return The corresponding {@link DescriptorProtos.FieldDescriptorProto.Type}.
     * @throws IllegalArgumentException If the type name is unknown.
     */
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

    /**
     * Determines the Java package name for a generated file based on the file descriptor.
     * <p>
     * Priorities:
     * 1. The {@code java_package} option in the .proto file.
     * 2. The {@code package} declaration in the .proto file.
     *
     * @param fileDescriptorProto The file descriptor.
     * @return The Java package name.
     */
    public static String getJavaPackage(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        return fileDescriptorProto.getOptions().hasJavaPackage()
                ? fileDescriptorProto.getOptions().getJavaPackage()
                : fileDescriptorProto.getPackage();
    }

    /**
     * Determines the Java outer class name for a generated file.
     * <p>
     * Priorities:
     * 1. The {@code java_outer_classname} option.
     * 2. The PascalCased file name.
     * <p>
     * Note: If the calculated outer class name conflicts with a message or enum defined in the file,
     * "OuterClass" is appended to avoid compilation errors.
     *
     * @param fileDescriptorProto The file descriptor.
     * @return The Java outer class name.
     */
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

    /**
     * Recursively streams all names defined in the descriptor to check for conflicts.
     */
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

    /**
     * Converts a string to PascalCase.
     * <p>
     * Handles underscores by camel-casing (e.g., "my_field" -> "MyField").
     * Simple strings are capitalized (e.g., "message" -> "Message").
     *
     * @param s The input string.
     * @return The PascalCase string, or null if input is null.
     */
    public static String toPascalCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.contains("_")) {
            return CaseUtils.toCamelCase(s, true, '_');
        }
        return StringUtils.capitalize(s);
    }

    /**
     * Converts a string to camelCase.
     * <p>
     * Handles underscores by camel-casing (e.g., "my_field" -> "myField").
     * Simple strings are uncapitalized (e.g., "Message" -> "message").
     *
     * @param s The input string.
     * @return The camelCase string, or null if input is null.
     */
    public static String toCamelCase(String s) {
        if (s == null) {
            return null;
        }
        if (s.contains("_")) {
            return CaseUtils.toCamelCase(s, false, '_');
        }
        return StringUtils.uncapitalize(s);
    }

    /**
     * Splits a byte array into a list of Java string literals, properly escaped.
     * <p>
     * This is used when generating code that initializes byte arrays or strings containing
     * binary data. Characters are escaped to ensure the generated Java code is valid and
     * faithful to the original bytes (e.g., escaping newlines, tabs, and non-printable characters).
     *
     * @param bytes The byte array.
     * @return A list of Java string literal contents (without surrounding quotes).
     */
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

    /**
     * Returns the name of the `CodedOutputStream` method used to write a field of the given type.
     *
     * @param type The Protobuf field type.
     * @return The method name (e.g., "writeInt32").
     */
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

    /**
     * Returns the name of the `CodedInputStream` method used to read a field of the given type.
     *
     * @param type The Protobuf field type.
     * @return The method name (e.g., "readInt32").
     */
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

    /**
     * Returns the name of the `CodedOutputStream` method used to compute the size of a field.
     *
     * @param type The Protobuf field type.
     * @return The method name (e.g., "computeInt32Size").
     */
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

    /**
     * Maps a Protobuf field type to its corresponding `WireFormat.FieldType`.
     *
     * @param type The Protobuf field type.
     * @return The WireFormat field type.
     */
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

    /**
     * Gets the wire type identifier (integer) for a given Protobuf field type.
     *
     * @param type The Protobuf field type.
     * @return The wire type integer (e.g., 0 for varint, 2 for length-delimited).
     */
    public static int getWireType(DescriptorProtos.FieldDescriptorProto.Type type) {
        return switch (type) {
            case TYPE_INT32, TYPE_INT64, TYPE_UINT32, TYPE_UINT64, TYPE_SINT32, TYPE_SINT64, TYPE_BOOL, TYPE_ENUM -> 0;
            case TYPE_DOUBLE, TYPE_FIXED64, TYPE_SFIXED64 -> 1;
            case TYPE_STRING, TYPE_BYTES, TYPE_MESSAGE -> 2;
            case TYPE_FLOAT, TYPE_FIXED32, TYPE_SFIXED32 -> 5;
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    /**
     * Returns a string representing the default Java value for a given primitive type name.
     *
     * @param typeName The Java primitive type name (e.g., "int", "boolean").
     * @return The default value string (e.g., "0", "false").
     */
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

    /**
     * Qualifies a type name with a scope represented by a {@link Cons} list.
     * <p>
     * Reconstructs the full name by appending the scope segments.
     *
     * @param typeName The simple type name.
     * @param scope    The scope hierarchy.
     * @return The fully qualified name.
     */
    public static String qualify(String typeName, Cons<String> scope) {
        return qualify(new StringBuilder(), scope).append('.').append(typeName).toString();
    }

    private static StringBuilder qualify(StringBuilder sb, Cons<String> scope) {
        if (scope.isEmpty()) {
            return sb;
        }
        return qualify(sb, scope.tail()).append('.').append(scope.head());
    }

    /**
     * Creates an uninterpreted option for a Protobuf descriptor.
     * <p>
     * Useful for programmatically adding options that are not standard (custom options).
     *
     * @param name  The name of the option.
     * @param value The value of the option.
     * @return The constructed {@link DescriptorProtos.UninterpretedOption}.
     */
    public static DescriptorProtos.UninterpretedOption createUninterpretedOption(String name, String value) {
        return DescriptorProtos.UninterpretedOption.newBuilder()
                .addName(DescriptorProtos.UninterpretedOption.NamePart.newBuilder()
                        .setNamePart(name)
                        .setIsExtension(true)
                        .build())
                .setStringValue(com.google.protobuf.ByteString.copyFromUtf8(value))
                .build();
    }

    /**
     * Generates the string content of a {@code .proto} file from a FileDescriptorProto.
     *
     * @param fileDescriptorProto The file descriptor.
     * @return The .proto file content.
     */
    public static String toProtoString(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        StringBuilder protoFileContent = new StringBuilder();
        protoFileContent.append("syntax = \"proto3\";\n\n");
        protoFileContent.append("package ").append(fileDescriptorProto.getPackage()).append(";\n\n");
        if (fileDescriptorProto.getOptions().hasJavaPackage()) {
            protoFileContent.append("option java_package = \"").append(fileDescriptorProto.getOptions().getJavaPackage()).append("\";\n\n");
        }

        for (DescriptorProtos.EnumDescriptorProto enumType : fileDescriptorProto.getEnumTypeList()) {
            protoFileContent.append(enumToString(enumType));
        }

        for (DescriptorProtos.DescriptorProto messageType : fileDescriptorProto.getMessageTypeList()) {
            protoFileContent.append(messageToString(messageType));
        }

        return protoFileContent.toString();
    }

    /**
     * Generates the string content for a message definition.
     *
     * @param messageType The message descriptor.
     * @return The string representation of the message.
     */
    public static String messageToString(DescriptorProtos.DescriptorProto messageType) {
        StringBuilder messageContent = new StringBuilder();
        messageContent.append("message ").append(messageType.getName()).append(" {\n");
        if (messageType.getOptions().getMapEntry()) {
            messageContent.append("  option map_entry = true;\n");
        }

        for (DescriptorProtos.EnumDescriptorProto enumType : messageType.getEnumTypeList()) {
            messageContent.append(indent(enumToString(enumType)));
        }

        for (DescriptorProtos.DescriptorProto nestedType : messageType.getNestedTypeList()) {
            messageContent.append(indent(messageToString(nestedType)));
        }

        for (DescriptorProtos.FieldDescriptorProto field : messageType.getFieldList().stream().sorted(Comparator.comparing(DescriptorProtos.FieldDescriptorProto::getNumber)).toList()) {
            messageContent.append("  ").append(fieldToString(messageType, field));
        }
        messageContent.append("}\n\n");
        return messageContent.toString();
    }

    /**
     * Generates the string content for an enum definition.
     *
     * @param enumType The enum descriptor.
     * @return The string representation of the enum.
     */
    public static String enumToString(DescriptorProtos.EnumDescriptorProto enumType) {
        StringBuilder enumContent = new StringBuilder();
        enumContent.append("enum ").append(enumType.getName()).append(" {\n");
        for (DescriptorProtos.EnumValueDescriptorProto value : enumType.getValueList()) {
            enumContent.append("  ").append(value.getName()).append(" = ").append(value.getNumber()).append(";\n");
        }
        enumContent.append("}\n\n");
        return enumContent.toString();
    }

    /**
     * Generates the string content for a field definition.
     *
     * @param parentMessage The parent message descriptor (used for map entry checks).
     * @param field         The field descriptor.
     * @return The string representation of the field.
     */
    public static String fieldToString(DescriptorProtos.DescriptorProto parentMessage, DescriptorProtos.FieldDescriptorProto field) {
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
                return "map<" + keyType + ", " + valueType + "> " + field.getName() + " = " + field.getNumber() + ";\n";
            }
        }

        return label + typeName + " " + field.getName() + " = " + field.getNumber() + ";\n";
    }

    // TODO optimize this by refactoring messageToString to track current indentation level and passing the string builder and indentation level to enumToString and fieldToString
    private static String indent(String s) {
        return (s == null || s.isEmpty())
                ? ""
                : s.lines()
                    .map(line -> line.isEmpty() ? line : "  " + line)
                    .collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Converts a Java annotation to its string representation (including values).
     *
     * @param ann The annotation instance.
     * @return The string representation (e.g., {@code @MyAnnotation(value="foo")}).
     */
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

    /**
     * Formats a Java object value as a valid Java source code literal.
     *
     * @param value The value (String, Character, Class, Enum, Array, etc.).
     * @return The formatted string.
     */
    public static String formatJavaValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Character) {
            return "'" + value + "'";
        } else if (value instanceof Class<?>) {
            return ((Class<?>) value).getSimpleName() + ".class";
        } else if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        } else if (value == null) {
            return "null";
        } else if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("{");
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(formatJavaValue(java.lang.reflect.Array.get(value, i)));
            }
            sb.append("}");
            return sb.toString();
        } else {
            return String.valueOf(value);
        }
    }

    /**
     * Determines the fully qualified Protobuf package name for a Java class.
     * <p>
     * Scans the class hierarchy for {@link GenProto} annotations to find the defined package.
     *
     * @param clazz The class to inspect.
     * @return The fully qualified Protobuf package name.
     */
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

    /**
     * Gets the simple name of a Java type (handling generic types).
     *
     * @param type The type.
     * @return The simple name.
     */
    public static String getTypeName(Type type) {
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        } else if (type instanceof ParameterizedType) {
            return getTypeName(((ParameterizedType) type).getRawType());
        }
        return "Object";
    }

    /**
     * Checks if generic services generation is enabled in the file descriptor.
     *
     * @param fileDescriptor The file descriptor.
     * @return True if enabled, false otherwise.
     */
    public static boolean isJavaGenericServicesEnabled(DescriptorProtos.FileDescriptorProto fileDescriptor) {
        if (fileDescriptor.getOptions().hasJavaGenericServices()) {
            return fileDescriptor.getOptions().getJavaGenericServices();
        }
        // Default: false for proto3, true for proto2
        return !"proto3".equals(fileDescriptor.getSyntax());
    }

    /**
     * Checks if a string or ByteString is empty.
     * <p>
     * Used in generated code to check for default values.
     *
     * @param value The value to check (String, ByteString, or null).
     * @return True if empty or null, false otherwise.
     */
    public static boolean isStringEmpty(Object value) {
        if (value instanceof String) {
            return ((String) value).isEmpty();
        }
        if (value instanceof com.google.protobuf.ByteString) {
            return ((com.google.protobuf.ByteString) value).isEmpty();
        }
        return value == null;
    }

    /**
     * Returns a stream of the descriptor and all its children.
     *
     * @param descriptor The root descriptor.
     * @return A stream of objects representing the descriptor hierarchy.
     */
    public static Stream<Object> descriptorStream(Object descriptor) {
        return Stream.concat(Stream.of(descriptor), descriptorChildren(descriptor));
    }

    /**
     * Returns a stream of children of a given descriptor, filtered by type.
     *
     * @param descriptor      The parent descriptor.
     * @param descriptorClass The class of children to include.
     * @param <T>             The type of children.
     * @return A stream of children.
     */
    public static <T> Stream<T> descriptorChildren(Object descriptor, Class<T> descriptorClass) {
        return descriptorChildren(descriptor).filter(descriptorClass::isInstance).map(descriptorClass::cast);
    }

    /**
     * Returns a stream of all direct children of a given descriptor.
     * <p>
     * Supports FileDescriptorProto, DescriptorProto (message), and ServiceDescriptorProto.
     *
     * @param descriptor The parent descriptor.
     * @return A stream of child objects.
     */
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

    /**
     * Creates a predicate that matches UninterpretedOption name lists against a key.
     *
     * @param key The key to match (e.g., "my.option").
     * @return A predicate for matching.
     */
    public static Predicate<List<DescriptorProtos.UninterpretedOption.NamePart>> nameList(String key) {
        return l -> {
            String optionName = l.stream()
                    .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                    .collect(Collectors.joining("."));
            return optionName.equals(key);
        };
    }

    /**
     * Concatenates elements to a list, returning a new list.
     *
     * @param list     The original list.
     * @param elements The elements to append.
     * @param <T>      The type of elements.
     * @return A new list containing the original elements plus the appended elements.
     */
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
