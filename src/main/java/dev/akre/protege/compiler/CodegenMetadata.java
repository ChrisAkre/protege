package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.UninterpretedOption.NamePart;
import dev.akre.util.Cons;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

public record CodegenMetadata(DescriptorProtos.FileDescriptorProto fileDescriptor, Map<Object, Cons<Object>> hierarchy, Map<String,Object> defaults, Map<String,Object> overrides, Map<String, DescriptorProtos.DescriptorProto> messageDescriptorMap) {
    public enum Type {
        BOOLEAN(Boolean.class), STRING(String.class), STRING_LIST(List.class);

        private final Predicate<Object> check;

        Type(Class<?> cls) {
            this.check = cls::isInstance;
        }

        boolean isInstance(Object object) {
            return check.test(object);
        }
    }
    public record Option(String key, Type type, Function<Cons<Object>,Optional<?>> lookup) {
        public static Option customBoolean(String key) {
            var p = nameList(key);
            return new Option(key, Type.BOOLEAN, scope -> getBooleanDescriptorOption(p, scope));
        }

        public static Option customString(String key) {
            var p = nameList(key);
            return new Option(key, Type.STRING, scope -> getDescriptorStringOption(p, scope));
        }

        public static Option customStringList(String key) {
            var p = nameList(key);
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
    public static final Option FIELD_ANNOTATION = Option.customString("dev.akre.protege.java_field_annotation");
    public static final Option MESSAGE_ANNOTATION = Option.customString("dev.akre.protege.java_message_annotation");
    public static final Option JAVA_GENERATE_DEPRECATED = Option.customBoolean("dev.akre.protege.java_generate_deprecated");
    public static final Option PACKAGE = Option.fileOption("package", DescriptorProtos.FileDescriptorProto::getPackage);
    public static final Option JAVA_PACKAGE = Option.fileOption("java_package", f -> f.getOptions().getJavaPackage());
    public static final Option FIELD_ANNOTATIONS = Option.customStringList(FIELD_ANNOTATION.key());


    public static class Builder {
        private final DescriptorProtos.FileDescriptorProto fileDescriptor;
        private final HashMap<String, Object> defaults = new HashMap<>();
        private final HashMap<String, Object> overrides = new HashMap<>();

        public Builder(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
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
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) defaults.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        public Builder setFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(defaults, new ArrayList<>(values));
            return this;
        }

        public Builder addOverrideFieldAnnotation(String value) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) overrides.computeIfAbsent(FIELD_ANNOTATIONS.key(), k -> new ArrayList<>());
            list.add(value);
            return this;
        }

        public Builder setOverrideFieldAnnotations(List<String> values) {
            FIELD_ANNOTATIONS.setValue(overrides, new ArrayList<>(values));
            return this;
        }

        public CodegenMetadata build() {
            return new CodegenMetadata(fileDescriptor, buildHierarchy(fileDescriptor), Map.copyOf(defaults), Map.copyOf(overrides), buildMessageDescriptorMap(fileDescriptor));
        }

        static Map<String, DescriptorProtos.DescriptorProto> buildMessageDescriptorMap(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            Map<String, DescriptorProtos.DescriptorProto> messageDescriptorMap = new HashMap<>();
            for (var message : fileDescriptor.getMessageTypeList()) {
                updateMessageDescriptorMap(message, messageDescriptorMap, Cons.nil());
            }
            return Map.copyOf(messageDescriptorMap);
        }

        static void updateMessageDescriptorMap(DescriptorProtos.DescriptorProto message, Map<String, DescriptorProtos.DescriptorProto> map, Cons<String> scope) {
            Cons<String> current = scope.cons(message.getName());
            map.put(String.join(".", current), message);
            for (var nested : message.getNestedTypeList()) {
                updateMessageDescriptorMap(nested, map, current);
            }
        }

        static Map<Object, Cons<Object>> buildHierarchy(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            return updateHierarchy(fileDescriptor, new HashMap<>(), Cons.nil());
        }

        static Map<Object, Cons<Object>> updateHierarchy(Object descriptor, Map<Object, Cons<Object>> hierarchy, Cons<Object> scope) {
            Cons<Object> current = scope.cons(descriptor);
            hierarchy.put(descriptor, current);
            descriptorChildren(descriptor).forEach(c -> updateHierarchy(c, hierarchy, current));
            return hierarchy;
        }
    }

    static Stream<Object> descriptorChildren(Object descriptor) {
        return switch (descriptor) {
            case DescriptorProtos.FileDescriptorProto f -> Stream.concat(f.getMessageTypeList().stream(), f.getEnumTypeList().stream());
            case DescriptorProtos.DescriptorProto m -> Stream.concat(m.getNestedTypeList().stream(), m.getEnumTypeList().stream());
            case DescriptorProtos.EnumDescriptorProto ignored -> Stream.empty();
            case DescriptorProtos.FieldDescriptorProto ignored -> Stream.empty();
            default -> throw new IllegalStateException();
        };
    }

    public boolean isGenerateDeprecated(Object descriptor) {
        return getBoolean(JAVA_GENERATE_DEPRECATED, descriptor);
    }

    public String getPackage(Object descriptor) {
        return getString(PACKAGE, descriptor);
    }

    public String getJavaPackage(Object descriptor) {
        return getString(JAVA_PACKAGE, descriptor);
    }

    public List<String> getFieldAnnotations(Object descriptor) {
        return getList(FIELD_ANNOTATIONS, descriptor);
    }

    public DescriptorProtos.DescriptorProto getMessageDescriptor(String name) {
        return messageDescriptorMap.get(name);
    }

    boolean getBoolean(Option key, Object descriptor) {
        return key.<Boolean>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults)).orElseThrow();

    }

    String getString(Option key, Object descriptor) {
        return key.<String>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults)).orElseThrow();
    }

    List<String> getList(Option key, Object descriptor) {
        return key.<List<String>>get(overrides).or(() -> key.lookup(hierarchy().get(descriptor))).or(() -> key.get(defaults)).orElse(List.of());
    }

    static Predicate<List<NamePart>> nameList(String key) {
        String[] parts = key.split("\\.");
        return l -> {
            if (l.size() != parts.length) {
                return false;
            }
            for (int i = 0; i < parts.length; i++) {
                if (!l.get(i).getNamePart().equals(parts[i])) {
                    return false;
                }
            }
            return true;
        };

    }

    public Optional<Boolean> getBooleanDefault(String key) {
        return Optional.ofNullable((Boolean) defaults.get(key));
    }

    public Optional<Boolean> getBooleanOverride(String key) {
        return Optional.ofNullable((Boolean) overrides.get(key));
    }

    static Optional<Boolean> getBooleanDescriptorOption(Predicate<List<NamePart>> key, Cons<Object> descriptor) {
        return getDescriptorOptions(key, descriptor).findFirst().map(o -> Boolean.valueOf(o.getStringValue().toStringUtf8()));
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
        return (switch (descriptor.head()) {
            case DescriptorProtos.FileDescriptorProto f -> f.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.DescriptorProto m -> m.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.EnumDescriptorProto e -> e.getOptions().getUninterpretedOptionList();
            case DescriptorProtos.FieldDescriptorProto f -> f.getOptions().getUninterpretedOptionList();
            default -> throw new IllegalStateException();
        }).stream().filter(o -> key.test(o.getNameList()));
    }



}