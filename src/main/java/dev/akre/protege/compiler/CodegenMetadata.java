package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.UninterpretedOption.NamePart;
import dev.akre.util.Cons;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public record CodegenMetadata(DescriptorProtos.FileDescriptorProto fileDescriptor, Map<Object, Cons<Object>> hierarchy, Map<String,Object> defaults, Map<String,Object> overrides) {
    record Option(String key, Function<Cons<Object>,Object> getDescriptorOptionValue) {
        public Option customBoolean(String key) {
            var p = nameList(key);
            return new Option(key, scope -> getBooleanDescriptorOption(p, scope));
        }
    }
    public static final String JAVA_ANNOTATION_OPTION = "dev.akre.protege.java_annotation";
    public static final String JAVA_MESSAGE_ANNOTATION_OPTION = "dev.akre.protege.java_message_annotation";
    public static final String JAVA_GENERATE_DEPRECATED = "dev.akre.protege.java_generate_deprecated";


    public static class Builder {
        private final DescriptorProtos.FileDescriptorProto fileDescriptor;
        private final HashMap<String, Object> defaults = new HashMap<>();
        private final HashMap<String, Object> overrides = new HashMap<>();

        public Builder(DescriptorProtos.FileDescriptorProto fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
        }

        public Builder setGenerateDeprecated(boolean value) {
            return setBoolean(JAVA_GENERATE_DEPRECATED, value);
        }

        public Builder overrideGenerateDeprecated(boolean value) {
            return overrideBoolean(JAVA_GENERATE_DEPRECATED, value);
        }

        private Builder setBoolean(String key, boolean value) {
            defaults.put(key, value);
            return this;
        }

        private Builder overrideBoolean(String key, boolean value) {
            overrides.put(key, value);
            return this;
        }

        public CodegenMetadata build() {
            return new CodegenMetadata(fileDescriptor, buildHierarchy(fileDescriptor), Map.copyOf(defaults), Map.copyOf(overrides));
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
        return getBooolean(JAVA_GENERATE_DEPRECATED, descriptor);
    }

    boolean getBooolean(String key, Object descriptor) {
        return getBooleanOverride(key).or(() -> getBooleanDescriptorOption(nameList(key), hierarchy().get(descriptor))).or(() -> getBooleanDefault(key)).orElseThrow();

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