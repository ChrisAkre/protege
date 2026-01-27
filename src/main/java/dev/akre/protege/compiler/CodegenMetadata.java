package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.UninterpretedOption.NamePart;
import com.google.protobuf.GeneratedMessage;
import dev.akre.protege.ProtoUtils;
import dev.akre.util.Cons;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

/**
 * Configuration metadata for the code generation process.
 * <p>
 * This record holds the file descriptor, hierarchy information, and configuration options (defaults and overrides).
 * It resolves option values by checking overrides, then the descriptor hierarchy (options in .proto files),
 * and finally defaults.
 */
public record CodegenMetadata(DescriptorProtos.FileDescriptorProto fileDescriptor, Map<Object, Cons<Object>> hierarchy,
                              Map<String, Object> defaults, Map<String, Object> overrides,
                              Map<String, Object> descriptorMap) {
    public static final Option FIELD_ANNOTATION = Option.customString("dev.akre.protege.java_field_annotation");
    public static final Option MESSAGE_ANNOTATION = Option.customString("dev.akre.protege.java_message_annotation");
    public static final Option JAVA_GENERATE_DEPRECATED = Option.customBoolean("dev.akre.protege.java_generate_deprecated");
    public static final Option PACKAGE = Option.fileOption("package", DescriptorProtos.FileDescriptorProto::getPackage);
    public static final Option JAVA_PACKAGE = Option.fileOption("java_package", f -> f.getOptions().getJavaPackage());
    public static final Option FIELD_ANNOTATIONS = Option.customStringList(FIELD_ANNOTATION.key());
    public static final Option ENHANCED_ONEOF = Option.customBoolean("dev.akre.protege.java_enhanced_oneof");
    public static final Option ONEOF_CASE = Option.customBoolean("dev.akre.protege.java_oneof_case");
    public static final Option JAVA_IMPLEMENTS = Option.customString("dev.akre.protege.java_implements");
    public static final Option JAVA_MESSAGE_SUPERCLASS = Option.customString("dev.akre.protege.message_superclass");
    public static final Option OUTER_NAME = Option.fileOption("outer_name", ProtoUtils::getJavaOuterClassName);


    public static CodegenMetadata.Builder build(DescriptorProtos.FileDescriptorProto fileDescriptor) {
        return new Builder(fileDescriptor);
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
        return key.<List<String>>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults)).orElse(List.of());
    }

    public Optional<Boolean> getBooleanDefault(String key) {
        return Optional.ofNullable((Boolean) defaults.get(key));
    }

    public Optional<Boolean> getBooleanOverride(String key) {
        return Optional.ofNullable((Boolean) overrides.get(key));
    }

    public enum Type {
        BOOLEAN(Boolean.class),
        STRING(String.class),
        STRING_LIST(List.class);

        private final Predicate<Object> check;

        Type(Class<?> cls) {
            this.check = cls::isInstance;
        }

        boolean isInstance(Object object) {
            return check.test(object);
        }
    }


    public record Option(String key, Type type, Function<Cons<Object>, Optional<?>> lookup) {
        public static Option customBoolean(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, Type.BOOLEAN, scope -> getBooleanDescriptorOption(p, scope));
        }

        public static Option customString(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, Type.STRING, scope -> getDescriptorStringOption(p, scope));
        }

        public static Option customStringList(String key) {
            var p = ProtoUtils.nameList(key);
            return new Option(key, Type.STRING_LIST, scope -> getStringListDescriptorOption(p, scope));
        }

        public static Option fileOption(String key, Function<DescriptorProtos.FileDescriptorProto, String> f) {
            return new Option(key, Type.STRING, scope -> getFileDescriptorStringOption(scope, f));
        }

        public void setValue(HashMap<String, Object> values, Object value) {
            if (!type.isInstance(value)) {
                throw new IllegalStateException("bad option type for %s: %s".formatted(key, value));
            }
            values.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> Optional<T> lookup(Cons<Object> scope) {
            return (Optional<T>) lookup.apply(scope);
        }

        @SuppressWarnings("unchecked")
        public <T> Optional<T> get(Map<String, Object> values) {
            return Optional.ofNullable((T) values.get(key));
        }
    }

    public static class Builder {
        private final DescriptorProtos.FileDescriptorProto fileDescriptor;
        private final HashMap<String, Object> defaults = new HashMap<>();
        private final HashMap<String, Object> overrides = new HashMap<>();

        public Builder(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
            JAVA_GENERATE_DEPRECATED.setValue(defaults, true);
            ENHANCED_ONEOF.setValue(defaults, false);
            ONEOF_CASE.setValue(defaults, true);
            JAVA_MESSAGE_SUPERCLASS.setValue(defaults, GeneratedMessage.class.getName());
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

        public Builder setGenerateDeprecated(boolean value) {
            JAVA_GENERATE_DEPRECATED.setValue(defaults, value);
            return this;
        }

        public Builder overrideGenerateDeprecated(boolean value) {
            JAVA_GENERATE_DEPRECATED.setValue(overrides, value);
            return this;
        }

        public Builder setPackage(String value) {
            PACKAGE.setValue(defaults, value);
            return this;
        }

        public Builder overridePackage(String value) {
            PACKAGE.setValue(overrides, value);
            return this;
        }

        public Builder setJavaPackage(String value) {
            JAVA_PACKAGE.setValue(defaults, value);
            return this;
        }

        public Builder overrideJavaPackage(String value) {
            JAVA_PACKAGE.setValue(overrides, value);
            return this;
        }

        public Builder addFieldAnnotation(String value) {
            @SuppressWarnings("unchecked") List<String> list = (List<String>) defaults.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        public Builder setFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(defaults, new ArrayList<>(values));
            return this;
        }

        public Builder addOverrideFieldAnnotation(String value) {
            @SuppressWarnings("unchecked") List<String> list = (List<String>) overrides.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        public Builder setOverrideFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(overrides, new ArrayList<>(values));
            return this;
        }

        public CodegenMetadata build() {
            return new CodegenMetadata(fileDescriptor, buildHierarchy(fileDescriptor), Map.copyOf(defaults), Map.copyOf(overrides), buildDescriptorMap(fileDescriptor));
        }
    }


}
