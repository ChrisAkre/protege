package dev.akre.protege.testutil;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Message;
import org.assertj.core.api.AbstractAssert;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DescriptorAssert extends AbstractAssert<DescriptorAssert, Object> {

    protected DescriptorAssert(Object actual) {
        super(actual, DescriptorAssert.class);
    }

    public static DescriptorAssert assertThat(DescriptorProtos.FileDescriptorProto actual) {
        return new DescriptorAssert(actual);
    }

    public static DescriptorAssert assertThat(DescriptorProtos.DescriptorProto actual) {
        return new DescriptorAssert(actual);
    }

    public DescriptorAssert compareIgnoringCustomOptions(Object expected) {
        isNotNull();
        compareRecursive((Message) actual, (Message) expected);
        return this;
    }

    private void compareRecursive(Message actual, Message expected) {
        if (actual == null && expected == null) return;
        if (actual == null) {
            actual = expected.getDefaultInstanceForType();
        } else if (expected == null) {
            expected = actual.getDefaultInstanceForType();
        }

        if (!actual.getDescriptorForType().equals(expected.getDescriptorForType())) {
            org.assertj.core.api.Assertions.fail("Expected type %s but was %s",
                    expected.getDescriptorForType().getFullName(),
                    actual.getDescriptorForType().getFullName());
        }

        Map<com.google.protobuf.Descriptors.FieldDescriptor, Object> actualFields = actual.getAllFields();
        Map<com.google.protobuf.Descriptors.FieldDescriptor, Object> expectedFields = expected.getAllFields();

        Set<com.google.protobuf.Descriptors.FieldDescriptor> allFields = new HashSet<>();
        allFields.addAll(actualFields.keySet());
        allFields.addAll(expectedFields.keySet());

        for (com.google.protobuf.Descriptors.FieldDescriptor field : allFields) {
            if (field.isExtension() && isIgnoredName(field.getFullName())) {
                continue;
            }

            Object v1 = actualFields.get(field);
            Object v2 = expectedFields.get(field);

            if (field.getName().equals("uninterpreted_option")) {
                compareUninterpretedOptions((List<DescriptorProtos.UninterpretedOption>) v1,
                        (List<DescriptorProtos.UninterpretedOption>) v2);
            } else if (field.getName().equals("extension") && field.getJavaType() == com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                compareExtensionDefinitions((List<Message>) v1,
                        (List<Message>) v2);
            } else if (field.getJavaType() == com.google.protobuf.Descriptors.FieldDescriptor.JavaType.MESSAGE) {
                if (field.isRepeated()) {
                    List<Message> l1 = (List<Message>) (v1 == null ? List.of() : v1);
                    List<Message> l2 = (List<Message>) (v2 == null ? List.of() : v2);
                    org.assertj.core.api.Assertions.assertThat(l1.size())
                            .as("Repeated field %s size in %s", field.getName(), actual.getDescriptorForType().getName()).isEqualTo(l2.size());
                    for (int i = 0; i < l1.size(); i++) {
                        compareRecursive(l1.get(i), l2.get(i));
                    }
                } else {
                    compareRecursive((Message) v1, (Message) v2);
                }
            } else if (field.isRepeated()) {
                List<?> l1 = (List<?>) (v1 == null ? List.of() : v1);
                List<?> l2 = (List<?>) (v2 == null ? List.of() : v2);
                org.assertj.core.api.Assertions.assertThat(l1)
                        .as("Repeated field %s in %s", field.getName(), actual.getDescriptorForType().getName()).isEqualTo(l2);
            } else {
                org.assertj.core.api.Assertions.assertThat(v1)
                        .as("Field %s in %s", field.getName(), actual.getDescriptorForType().getName()).isEqualTo(v2);
            }
        }
    }

    private void compareUninterpretedOptions(List<DescriptorProtos.UninterpretedOption> l1,
                                             List<DescriptorProtos.UninterpretedOption> l2) {
        List<DescriptorProtos.UninterpretedOption> f1 = l1 == null ? List.of() : l1.stream().filter(o -> !isIgnoredName(getOptionName(o))).toList();
        List<DescriptorProtos.UninterpretedOption> f2 = l2 == null ? List.of() : l2.stream().filter(o -> !isIgnoredName(getOptionName(o))).toList();
        org.assertj.core.api.Assertions.assertThat(f1).as("Uninterpreted options").isEqualTo(f2);
    }

    private void compareExtensionDefinitions(List<Message> l1,
                                             List<Message> l2) {
        List<Message> f1 = l1 == null ? List.of() : l1.stream().filter(m -> !isIgnoredDefinition(m)).toList();
        List<Message> f2 = l2 == null ? List.of() : l2.stream().filter(m -> !isIgnoredDefinition(m)).toList();
        org.assertj.core.api.Assertions.assertThat(f1.size()).as("Extension definitions size").isEqualTo(f2.size());
        for (int i = 0; i < f1.size(); i++) {
            compareRecursive(f1.get(i), f2.get(i));
        }
    }

    private boolean isIgnoredDefinition(Message m) {
        com.google.protobuf.Descriptors.FieldDescriptor nameField = m.getDescriptorForType().findFieldByName("name");
        if (nameField != null) {
            String name = (String) m.getField(nameField);
            return isIgnoredName(name);
        }
        return false;
    }

    private String getOptionName(DescriptorProtos.UninterpretedOption opt) {
        return opt.getNameList().stream()
                .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                .collect(Collectors.joining("."));
    }

    private boolean isIgnoredName(String name) {
        return name.endsWith("java_implements") || name.endsWith("java_annotation");
    }
}
