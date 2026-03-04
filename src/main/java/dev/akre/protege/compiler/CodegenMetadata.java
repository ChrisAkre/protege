package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.UninterpretedOption.NamePart;
import com.google.protobuf.GeneratedMessage;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Configuration metadata for the code generation process.  This configuration allows setting default values and override
 * values through the builder, and then uses the provided FileDescriptor to resolve configuration options for each
 * Message, Field, Enum, and Service descriptor.
 * <p>
 *  Additionally, this class builds a structure mirroring all the descriptors in the file to help the code generators
 *  determine structure and type information.
 *
 * @param fileDescriptor The Protobuf file descriptor.
 * @param hierarchy A map from descriptor to its parent hierarchy.
 * @param defaults A map of default configuration values.
 * @param overrides A map of override configuration values.
 * @param descriptorMap A map from fully qualified names to descriptors.
 * @param packageName The generated Java package name.
 * @param typeRegistry A map from fully qualified names to JavaPoet ClassNames.
 * @param isEnumMap A map indicating if a fully qualified name is an enum.
 * @param isMapEntryMap A map indicating if a fully qualified name is a map entry.
 * @param oneofInterfacesByType A map from fully qualified names to oneof interfaces.
 */
public record CodegenMetadata(DescriptorProtos.FileDescriptorProto fileDescriptor, Map<Object, Cons<Object>> hierarchy,
                              Map<String, Object> defaults, Map<String, Object> overrides,
                              Map<String, Object> descriptorMap,
                              String packageName,
                              Map<String, ClassName> typeRegistry,
                              Map<String, Boolean> isEnumMap,
                              Map<String, Boolean> isMapEntryMap,
                              Map<String, List<ClassName>> oneofInterfacesByType
) {

    /** Option for specifying annotations on generated fields. */
    public static final Option FIELD_ANNOTATIONS = Option.customStringList("dev.akre.protege.java_field_annotation");
    /** Option for specifying annotations on all generated message classes. */
    public static final Option MESSAGE_ANNOTATIONS = Option.customStringList("dev.akre.protege.java_message_annotation");
    /** Option for specifying annotations on generated builder classes. */
    public static final Option BUILDER_ANNOTATIONS = Option.customStringList("dev.akre.protege.java_builder_annotation");
    /** Option for specifying annotations on generated immutable data classes. */
    public static final Option CLASS_ANNOTATIONS = Option.customStringList("dev.akre.protege.java_class_annotation");
    /** Option for specifying annotations on generated interfaces. */
    public static final Option INTERFACE_ANNOTATIONS = Option.customStringList("dev.akre.protege.java_interface_annotation");

    /** Option for controlling generation of @Deprecated annotations. */
    public static final Option JAVA_GENERATE_DEPRECATED = Option.customBoolean("dev.akre.protege.java_generate_deprecated");
    /** Option for the package name from the proto file. */
    public static final Option PACKAGE = Option.fileOption("package", DescriptorProtos.FileDescriptorProto::getPackage);
    /** Option for the Java package name. */
    public static final Option JAVA_PACKAGE = Option.fileOption("java_package", f -> f.getOptions().getJavaPackage());
    /** Option for enabling enhanced oneof generation (sealed interfaces). */
    public static final Option ENHANCED_ONEOF = Option.customBoolean("dev.akre.protege.java_enhanced_oneof");
    /** Option for enabling generation of the legacy oneof Case enum. */
    public static final Option ONEOF_CASE = Option.customBoolean("dev.akre.protege.java_oneof_case");
    /** Option for specifying an interface the generated message should implement. */
    public static final Option JAVA_IMPLEMENTS = Option.customString("dev.akre.protege.java_implements");
    /** Option for specifying a custom superclass for generated messages. */
    public static final Option JAVA_MESSAGE_SUPERCLASS = Option.customString("dev.akre.protege.message_superclass");
    /** Option for specifying the outer class name. */
    public static final Option OUTER_NAME = Option.fileOption("outer_name", ProtoUtils::getJavaOuterClassName);
    /** Option for enabling Jackson annotations. */
    public static final Option JACKSON_ANNOTATIONS = Option.customBoolean("dev.akre.protege.java_jackson_annotations");


    /**
     * Creates a new Builder for the given file descriptor.
     *
     * @param fileDescriptor The Protobuf file descriptor to process.
     * @return A new Builder instance.
     */
    public static CodegenMetadata.Builder build(DescriptorProtos.FileDescriptorProto fileDescriptor) {
        return new Builder(fileDescriptor);
    }

    /**
     * Returns the class name for the FieldAccessorTable nested class.
     *
     * @return The ClassName of the FieldAccessorTable.
     */
    public ClassName getFieldAccessorTableClass() {
        return getMessageSuperclass().nestedClass("FieldAccessorTable");
    }

    /**
     * Returns the configured superclass for generated messages.
     *
     * @return The ClassName of the message superclass.
     */
    public ClassName getMessageSuperclass() {
        return getString(JAVA_MESSAGE_SUPERCLASS, fileDescriptor).map(ClassName::bestGuess).orElseThrow();
    }

    /**
     * Checks if a field is a map field.
     *
     * @param field The field descriptor.
     * @return True if the field is a map, false otherwise.
     */
    public boolean isMapField(DescriptorProtos.FieldDescriptorProto field) {
        if (field.getLabel() != DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            return false;
        }
        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
            return false;
        }
        if (!field.hasTypeName()) {
            return false;
        }

        String typeName = relativeToProtoPackage(field.getTypeName());
        return isMapEntryMap.getOrDefault(typeName, false);
    }


    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName The Protobuf type name.
     * @param currentScope  The current scope as a list of names.
     * @return The resolved TypeName.
     */
    public TypeName resolveTypeName(String protoTypeName, List<String> currentScope) {
        String outerClassName = getString(OUTER_NAME, fileDescriptor).orElse("");
        if (protoTypeName.startsWith(".")) {
            var typeName = relativeToProtoPackage(protoTypeName);
            if (typeRegistry.containsKey(typeName)) {
                return typeRegistry.get(typeName);
            }
            return ClassName.get(packageName, outerClassName, typeName.split("\\."));
        }

        for (int i = currentScope.size(); i >= 0; i--) {
            var scope = currentScope.subList(0, i);
            var candidateName = scope.isEmpty() ? protoTypeName : String.join(".", scope) + "." + protoTypeName;
            if (typeRegistry.containsKey(candidateName)) {
                return typeRegistry.get(candidateName);
            }
        }

        return ClassName.get(packageName, outerClassName, protoTypeName.split("\\."));
    }

    /**
     * Resolves a Protobuf type name to a JavaPoet TypeName within the current scope.
     *
     * @param protoTypeName The Protobuf type name.
     * @param currentScope  The current scope as a Cons list of names.
     * @return The resolved TypeName.
     */
    public TypeName resolveTypeName(String protoTypeName, Cons<String> currentScope) {
        if (protoTypeName.startsWith(".")) {
            return resolveTypeName(protoTypeName, java.util.Collections.<String>emptyList());
        }

        for (var scope = currentScope; scope != null; scope = scope.tail()) {
            // Cons.stream() iterates from tail to head (root to leaf), so the order is correct.
            String scopeStr = scope.stream().map(Object::toString).collect(java.util.stream.Collectors.joining("."));
            var candidateName = scopeStr.isEmpty() ? protoTypeName : scopeStr + "." + protoTypeName;
            if (typeRegistry.containsKey(candidateName)) {
                return typeRegistry.get(candidateName);
            }
        }

        String outerClassName = getString(OUTER_NAME, fileDescriptor).orElse("");
        return ClassName.get(packageName, outerClassName, protoTypeName.split("\\."));
    }

    /**
     * Determines the Java type for a given Protobuf field.
     *
     * @param field        The field descriptor.
     * @param currentScope The current scope.
     * @return The Java TypeName for the field.
     */
    public TypeName getFieldType(DescriptorProtos.FieldDescriptorProto field, List<String> currentScope) {
        if (isMapField(field)) {
            var entryDescriptor = getEntryDescriptor(field);
            var keyField = entryDescriptor.getField(0);
            var valueField = entryDescriptor.getField(1);

            var keyType = getFieldType(keyField, currentScope);
            var valueType = getFieldType(valueField, currentScope);

            return ParameterizedTypeName.get(ClassName.get(java.util.Map.class), keyType.box(), valueType.box());
        }
        if (field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            var genericType = CodegenUtils.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                genericType = resolveTypeName(field.getTypeName(), currentScope);
            }
            if (genericType == null) {
                genericType = TypeName.get(Object.class);
            }
            return ParameterizedTypeName.get(ClassName.get(java.util.List.class), genericType.box());
        } else {
            var fieldType = CodegenUtils.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                fieldType = resolveTypeName(field.getTypeName(), currentScope);
            }
            if (fieldType == null) {
                fieldType = TypeName.get(Object.class);
            }
            return fieldType;
        }
    }


    /**
     * Retrieves the descriptor for the map entry message associated with a map field.
     *
     * @param field The field descriptor.
     * @return The DescriptorProto for the map entry.
     */
    public DescriptorProtos.DescriptorProto getEntryDescriptor(DescriptorProtos.FieldDescriptorProto field) {
        String typeEntryName = relativeToProtoPackage(field.getTypeName());
        return (DescriptorProtos.DescriptorProto) descriptorMap.get(typeEntryName);
    }

    /**
     * Returns the package name defined in the proto file.
     *
     * @return The package name.
     */
    public String protoPackageName() {
        return fileDescriptor.getPackage();
    }

    /**
     * Relativizes a type name against the current proto package.
     *
     * @param typeName The full type name.
     * @return The relative type name.
     */
    public String relativeToProtoPackage(String typeName) {
        return CodegenUtils.relativeToProtoPackage(typeName, protoPackageName());
    }

    static Optional<Boolean> getBooleanDescriptorOption(Predicate<List<NamePart>> key, Cons<Object> descriptor) {
        return getDescriptorOptions(key, descriptor).findFirst().map(o -> {
            if (o.hasIdentifierValue()) {
                return Boolean.valueOf(o.getIdentifierValue());
            }
            return Boolean.valueOf(o.getStringValue().toStringUtf8());
        });
    }

    static Optional<String> getDescriptorStringOption(Predicate<List<NamePart>> key, Cons<Object> descriptor) {
        return getDescriptorOptions(key, descriptor).findFirst().map(o -> o.getStringValue().toStringUtf8());
    }

    static Optional<List<String>> getStringListDescriptorOption(Predicate<List<NamePart>> key, Cons<Object> descriptor) {
        List<String> list = getDescriptorOptions(key, descriptor).map(o -> o.getStringValue().toStringUtf8()).toList();
        return list.isEmpty() ? Optional.empty() : Optional.of(list);
    }

    static Optional<String> getFileDescriptorStringOption(Cons<Object> descriptor, Function<DescriptorProtos.FileDescriptorProto, String> f) {
        return switch (descriptor.head()) {
            case DescriptorProtos.FileDescriptorProto d -> Optional.of(f.apply(d)).filter(not(String::isEmpty));
            case null -> throw new IllegalStateException();
            default -> getFileDescriptorStringOption(descriptor.tail(), f);
        };
    }

    static Stream<DescriptorProtos.UninterpretedOption> getDescriptorOptions(Predicate<List<NamePart>> key, Cons<Object> descriptor) {
        Stream<DescriptorProtos.UninterpretedOption> options = (switch (descriptor.head()) {
            case DescriptorProtos.FileDescriptorProto f -> f.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.DescriptorProto m -> m.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.EnumDescriptorProto e -> e.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.FieldDescriptorProto f -> f.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.OneofDescriptorProto o -> o.getOptions().getUninterpretedOptionList();
            default -> throw new IllegalStateException();
        }).stream().filter(o -> key.test(o.getNameList()));

        if (!descriptor.tail().isEmpty()) {
            options = Stream.concat(options, getDescriptorOptions(key, descriptor.tail()));
        }
        return options;
    }

    boolean getBoolean(Option key, Object descriptor) {
        return key.<Boolean>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults)).orElseThrow();
    }

    Optional<String> getString(Option key, Object descriptor) {
        return key.<String>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults));
    }

    List<String> getList(Option key, Object descriptor) {
        try {
            return key.<List<String>>get(overrides)
                    .or(() -> Optional.ofNullable(this.hierarchy().get(descriptor)).flatMap(key::lookup))
                    .or(() -> key.get(defaults))
                    .orElse(List.of());
        } catch (ClassCastException e) {
            throw e;
        }
    }

    /**
     * Gets a boolean default value by key.
     *
     * @param key The option key.
     * @return The optional boolean value.
     */
    public Optional<Boolean> getBooleanDefault(String key) {
        return Optional.ofNullable((Boolean) defaults.get(key));
    }

    /**
     * Gets a boolean override value by key.
     *
     * @param key The option key.
     * @return The optional boolean value.
     */
    public Optional<Boolean> getBooleanOverride(String key) {
        return Optional.ofNullable((Boolean) overrides.get(key));
    }

    /**
     * Represents the data type of a configuration option, used to validate values and ensure type safety
     * when retrieving options.
     */
    public enum ValueType {
        /** Boolean type. */
        BOOLEAN(Boolean.class),
        /** String type. */
        STRING(String.class),
        /** List of Strings type. */
        STRING_LIST(List.class);

        private final Predicate<Object> check;

        ValueType(Class<?> cls) {
            this.check = cls::isInstance;
        }

        boolean isInstance(Object object) {
            return check.test(object);
        }
    }


    /**
     * Defines a configuration option with a unique key, a specific {@link ValueType}, and a lookup function
     * to resolve its value from a descriptor hierarchy.
     *
     * @param key The option key.
     * @param type The type of the option.
     * @param lookup The lookup function to resolve the option value.
     */
    public record Option(String key, ValueType type, Function<Cons<Object>, Optional<?>> lookup) {
        /**
         * Creates a custom boolean option.
         * @param key The option key.
         * @return The Option.
         */
        public static Option customBoolean(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, ValueType.BOOLEAN, scope -> getBooleanDescriptorOption(p, scope));
        }

        /**
         * Creates a custom string option.
         * @param key The option key.
         * @return The Option.
         */
        public static Option customString(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, ValueType.STRING, scope -> getDescriptorStringOption(p, scope));
        }

        /**
         * Creates a custom string list option.
         * @param key The option key.
         * @return The Option.
         */
        public static Option customStringList(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, ValueType.STRING_LIST, scope -> getStringListDescriptorOption(p, scope));
        }

        /**
         * Creates a file-level option.
         * @param key The option key.
         * @param f Function to extract the value from the file descriptor.
         * @return The Option.
         */
        public static Option fileOption(String key, Function<DescriptorProtos.FileDescriptorProto, String> f) {
            return new Option(key, ValueType.STRING, scope -> getFileDescriptorStringOption(scope, f));
        }

        /**
         * Sets a value in the provided map, ensuring type safety.
         * @param values The map to set the value in.
         * @param value The value to set.
         */
        public void setValue(HashMap<String, Object> values, Object value) {
            if (!type.isInstance(value)) {
                throw new IllegalStateException("bad option type for %s: %s".formatted(key, value));
            }
            values.put(key, value);
        }

        /**
         * Looks up the option value in the given scope.
         * @param scope The scope chain.
         * @param <T> The return type.
         * @return The optional value.
         */
        @SuppressWarnings("unchecked")
        public <T> Optional<T> lookup(Cons<Object> scope) {
            return (Optional<T>) lookup.apply(scope);
        }

        /**
         * Gets the option value from the provided map.
         * @param values The map of values.
         * @param <T> The return type.
         * @return The optional value.
         */
        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(Map<String, Object> values) {
            return Optional.ofNullable((T) values.get(key));
        }
    }

    /**
     * A builder for creating and configuring {@link CodegenMetadata} instances. It allows setting
     * default values and specific overrides that interact with values set in the .proto file.
     */
    public static class Builder {
        private final DescriptorProtos.FileDescriptorProto fileDescriptor;
        private final HashMap<String, Object> defaults = new HashMap<>();
        private final HashMap<String, Object> overrides = new HashMap<>();

        /**
         * Creates a new Builder.
         * @param fileDescriptor The file descriptor.
         */
        public Builder(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
            JAVA_GENERATE_DEPRECATED.setValue(defaults, true);
            ENHANCED_ONEOF.setValue(defaults, false);
            ONEOF_CASE.setValue(defaults, true);
            JAVA_MESSAGE_SUPERCLASS.setValue(defaults, GeneratedMessage.class.getName());
            JACKSON_ANNOTATIONS.setValue(defaults, false);
        }



        static Map<String, Object> buildDescriptorMap(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            return Map.copyOf(updateDescriptorMap(fileDescriptor, new HashMap<>(), Cons.nil()));
        }

        static Map<String, Object> updateDescriptorMap(Object descriptor, Map<String, Object> map, Cons<String> scope) {
            switch (descriptor) {
                case DescriptorProtos.FileDescriptorProto f -> {
                    map.put(".", f);
                    f.getMessageTypeList().forEach(c -> updateDescriptorMap(c, map, scope));
                    f.getEnumTypeList().forEach(c -> updateDescriptorMap(c, map, scope));
                }
                case DescriptorProtos.DescriptorProto m -> {
                    var current = scope.cons(m.getName());
                    map.put(String.join(".", current), m);
                    m.getNestedTypeList().forEach(c -> updateDescriptorMap(c, map, current));
                    m.getEnumTypeList().forEach(c -> updateDescriptorMap(c, map, scope));
                }
                case DescriptorProtos.EnumDescriptorProto e -> map.put(String.join(".", scope.cons(e.getName())), e);

                default -> throw new IllegalStateException();
            }
            return map;
        }

        static Map<Object, Cons<Object>> buildHierarchy(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            return updateHierarchy(fileDescriptor, new HashMap<>(), Cons.nil());
        }

        static Map<Object, Cons<Object>> updateHierarchy(Object descriptor, Map<Object, Cons<Object>> hierarchy, Cons<Object> scope) {
            Cons<Object> current = scope.cons(descriptor);
            hierarchy.put(descriptor, current);
            ProtoUtils.descriptorChildren(descriptor).forEach(c -> updateHierarchy(c, hierarchy, current));
            return hierarchy;
        }

        /**
         * Sets whether to generate @Deprecated annotations by default.
         * @param value True to generate, false otherwise.
         * @return This builder.
         */
        public Builder setGenerateDeprecated(boolean value) {
            JAVA_GENERATE_DEPRECATED.setValue(defaults, value);
            return this;
        }

        /**
         * Overrides whether to generate @Deprecated annotations.
         * @param value True to generate, false otherwise.
         * @return This builder.
         */
        public Builder overrideGenerateDeprecated(boolean value) {
            JAVA_GENERATE_DEPRECATED.setValue(overrides, value);
            return this;
        }

        /**
         * Sets the default package name.
         * @param value The package name.
         * @return This builder.
         */
        public Builder setPackage(String value) {
            PACKAGE.setValue(defaults, value);
            return this;
        }

        /**
         * Overrides the package name.
         * @param value The package name.
         * @return This builder.
         */
        public Builder overridePackage(String value) {
            PACKAGE.setValue(overrides, value);
            return this;
        }

        /**
         * Sets the default Java package name.
         * @param value The Java package name.
         * @return This builder.
         */
        public Builder setJavaPackage(String value) {
            JAVA_PACKAGE.setValue(defaults, value);
            return this;
        }

        /**
         * Overrides the Java package name.
         * @param value The Java package name.
         * @return This builder.
         */
        public Builder overrideJavaPackage(String value) {
            JAVA_PACKAGE.setValue(overrides, value);
            return this;
        }

        /**
         * Adds a default field annotation.
         * @param value The annotation string.
         * @return This builder.
         */
        public Builder addFieldAnnotation(String value) {
            @SuppressWarnings("unchecked") List<String> list = (List<String>) defaults.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        /**
         * Sets the default field annotations.
         * @param values The list of annotation strings.
         * @return This builder.
         */
        public Builder setFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(defaults, new ArrayList<>(values));
            return this;
        }

        /**
         * Adds an override field annotation.
         * @param value The annotation string.
         * @return This builder.
         */
        public Builder addOverrideFieldAnnotation(String value) {
            @SuppressWarnings("unchecked") List<String> list = (List<String>) overrides.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        /**
         * Sets the override field annotations.
         * @param values The list of annotation strings.
         * @return This builder.
         */
        public Builder setOverrideFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(overrides, new ArrayList<>(values));
            return this;
        }

        /**
         * Sets the default message superclass.
         * @param value The superclass name.
         * @return This builder.
         */
        public Builder setJavaMessageSuperclass(String value) {
            JAVA_MESSAGE_SUPERCLASS.setValue(defaults, value);
            return this;
        }

        /**
         * Builds the CodegenMetadata instance.
         * @return The configured CodegenMetadata.
         */
        public CodegenMetadata build() {
            var hierarchy = buildHierarchy(fileDescriptor);
            var descriptorMap = buildDescriptorMap(fileDescriptor);

            // Temporary metadata for resolving options before full initialization
            var tempMetadata = new CodegenMetadata(fileDescriptor, hierarchy, Map.copyOf(defaults), Map.copyOf(overrides), descriptorMap,
                    null, null, null, null, null);

            String packageName = tempMetadata.getString(JAVA_PACKAGE, fileDescriptor).orElseGet(fileDescriptor::getPackage);
            String outerClassName = tempMetadata.getString(OUTER_NAME, fileDescriptor).orElseGet(() -> ProtoUtils.getJavaOuterClassName(fileDescriptor));

            var typeRegistry = new HashMap<String, ClassName>();
            var isEnumMap = new HashMap<String, Boolean>();
            var isMapEntryMap = new HashMap<String, Boolean>();
            var messageDescriptorRegistry = new HashMap<String, DescriptorProtos.DescriptorProto>();

            CodegenUtils.registerAllTypes(fileDescriptor, packageName, outerClassName, typeRegistry, isEnumMap, isMapEntryMap, messageDescriptorRegistry);

             var metadata = new CodegenMetadata(fileDescriptor, hierarchy, Map.copyOf(defaults), Map.copyOf(overrides), descriptorMap,
                     packageName, typeRegistry, isEnumMap, isMapEntryMap, null);

             var oneofInterfacesByType = new HashMap<String, List<ClassName>>();
             populateOneofInterfaces(metadata, oneofInterfacesByType);

             return new CodegenMetadata(fileDescriptor, hierarchy, Map.copyOf(defaults), Map.copyOf(overrides), descriptorMap,
                     packageName, typeRegistry, isEnumMap, isMapEntryMap, oneofInterfacesByType);
        }

        private void populateOneofInterfaces(CodegenMetadata metadata, Map<String, List<ClassName>> oneofInterfacesByType) {
            ProtoUtils.descriptorChildren(metadata.fileDescriptor(), DescriptorProtos.DescriptorProto.class)
                    .forEach(message -> populateOneofInterfaces(metadata, message, Cons.nil(), oneofInterfacesByType));
        }

        private void populateOneofInterfaces(
                CodegenMetadata metadata,
                DescriptorProtos.DescriptorProto message,
                Cons<String> parentPath,
                Map<String, List<ClassName>> oneofInterfacesByType) {

            var currentPath = parentPath.cons(message.getName());

            for (int i = 0; i < message.getOneofDeclCount(); i++) {

                var oneof = message.getOneofDecl(i);
                if (!metadata.getBoolean(ENHANCED_ONEOF, oneof)) {
                    continue;
                }
                var pascalName = ProtoUtils.toPascalCase(oneof.getName());
                var outerName = metadata.getString(OUTER_NAME, metadata.fileDescriptor()).orElse("");

                var capitalizedPath = currentPath.stream().map(StringUtils::capitalize).toList();
                var interfaceClassName = ClassName.get(metadata.packageName(), outerName, capitalizedPath.toArray(new String[0])).nestedClass(pascalName);

                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = metadata.relativeToProtoPackage(typeName);
                        if (!metadata.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }

                        TypeName typeNameRes = metadata.resolveTypeName(field.getTypeName(), currentPath);
                        if (typeNameRes instanceof ClassName cn) {
                            oneofInterfacesByType.computeIfAbsent(cn.canonicalName(), k -> new ArrayList<>()).add(interfaceClassName);
                        }
                    }
                }
            }
            // Recurse
            for (var nested : message.getNestedTypeList()) {
                populateOneofInterfaces(metadata, nested, currentPath, oneofInterfacesByType);
            }
        }
    }
}
