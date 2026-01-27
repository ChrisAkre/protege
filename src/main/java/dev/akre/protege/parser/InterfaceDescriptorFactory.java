package dev.akre.protege.parser;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MessageOptions;
import dev.akre.protege.Field;
import dev.akre.protege.GenProto;
import dev.akre.protege.ProtoUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates Protobuf descriptors from annotated Java interfaces.
 * <p>
 * This class uses reflection to scan Java classes (typically interfaces) annotated with
 * {@link dev.akre.protege.GenProto} and {@link dev.akre.protege.Field} to build
 * corresponding {@link FileDescriptorProto} objects.
 */
public class InterfaceDescriptorFactory {

    private final Map<Type, DescriptorProtos.DescriptorProto> messages = new LinkedHashMap<>();
    private final Map<Type, DescriptorProtos.EnumDescriptorProto> enums = new LinkedHashMap<>();
    private final Set<Type> nestedTypes = new HashSet<>();
    private final Set<Type> nestedEnums = new HashSet<>();


    public InterfaceDescriptorFactory() {

    }


    /**
     * Updates an existing FileDescriptorProto with definitions from a Java class.
     *
     * @param descriptor The base descriptor to update.
     * @param cls        The Java class to inspect.
     * @return The updated FileDescriptorProto.
     */
    public FileDescriptorProto update(FileDescriptorProto descriptor, Class<?> cls) {
        FileDescriptorProto.Builder builder = descriptor.toBuilder();

        GenProto genProto = cls.getAnnotation(GenProto.class);
        boolean isOuterClass = genProto != null && genProto.outerClass();

        if (!isOuterClass) {
            builder.addMessageType(processClass(cls));
        } else {
            for (Class<?> innerClass : cls.getDeclaredClasses()) {
                if (!innerClass.isSynthetic()) {
                    if (innerClass.isEnum()) {
                        builder.addEnumType(processEnum(innerClass));
                    } else {
                        builder.addMessageType(processClass(innerClass));
                    }
                }
            }
        }

        // Add any other top-level messages/enums that were processed but not nested
        messages.forEach((type, proto) -> {
            if (!nestedTypes.contains(type) && builder.getMessageTypeList().stream().noneMatch(m -> m.getName().equals(proto.getName()))) {
                builder.addMessageType(proto);
            }
        });
        enums.forEach((type, proto) -> {
            if (!nestedEnums.contains(type) && builder.getEnumTypeList().stream().noneMatch(e -> e.getName().equals(proto.getName()))) {
                builder.addEnumType(proto);
            }
        });

        return builder.build();
    }

    public FileDescriptorProto create(Class<?> cls) {
        GenProto genProto = cls.getAnnotation(GenProto.class);
        String name = (genProto != null && !genProto.value().isEmpty())
                ? genProto.value().replace(".proto", "")
                : cls.getSimpleName();
        return create(cls, name);
    }

    public FileDescriptorProto create(Class<?> cls, String protoFileName) {
        FileDescriptorProto.Builder fileDescriptorProtoBuilder = FileDescriptorProto.newBuilder();
        fileDescriptorProtoBuilder.setName(protoFileName + ".proto");

        GenProto genProto = cls.getAnnotation(GenProto.class);
        String pkg = (genProto != null && !genProto.pkg().isEmpty()) ? genProto.pkg() : cls.getPackageName();

        fileDescriptorProtoBuilder.setPackage(pkg);
        fileDescriptorProtoBuilder.setOptions(FileOptions.newBuilder().setJavaPackage(cls.getPackageName()).build());
        return update(fileDescriptorProtoBuilder.build(), cls);
    }

    private void processType(DescriptorProto.Builder messageBuilder, String messageQualifiedName, Type type,
                             FieldDescriptorProto.Builder fieldBuilder, String fieldName, Class<?> parentClass) {
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class && Map.class.isAssignableFrom((Class<?>) rawType)) {
                // Handle Map types
                Type keyType = parameterizedType.getActualTypeArguments()[0];
                Type valueType = parameterizedType.getActualTypeArguments()[1];
                String mapEntryName = ProtoUtils.toPascalCase(fieldName) + "Entry";
                String mapEntryQualifiedName = messageQualifiedName + "." + mapEntryName;

                DescriptorProto.Builder mapEntryBuilder = DescriptorProto.newBuilder();
                mapEntryBuilder.setName(mapEntryName);
                mapEntryBuilder.setOptions(MessageOptions.newBuilder().setMapEntry(true).build());

                FieldDescriptorProto.Builder keyField = FieldDescriptorProto.newBuilder().setName("key").setNumber(1);
                processType(mapEntryBuilder, mapEntryQualifiedName, keyType, keyField, "key", null);
                if (!keyField.hasLabel()) {
                    keyField.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
                }
                mapEntryBuilder.addField(keyField);

                FieldDescriptorProto.Builder valueField = FieldDescriptorProto.newBuilder().setName("value").setNumber(2);
                processType(mapEntryBuilder, mapEntryQualifiedName, valueType, valueField, "value", null);
                if (!valueField.hasLabel()) {
                    valueField.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
                }
                mapEntryBuilder.addField(valueField);

                messageBuilder.addNestedType(mapEntryBuilder.build());

                fieldBuilder.setTypeName(mapEntryQualifiedName);
                fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE);
                fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED);
            } else if (rawType instanceof Class && java.util.Collection.class.isAssignableFrom((Class<?>) rawType)) {
                // Handle Collection types
                Type elementType = parameterizedType.getActualTypeArguments()[0];
                processType(messageBuilder, messageQualifiedName, elementType, fieldBuilder, fieldName, parentClass);
                fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_REPEATED);
            }
        } else if (type instanceof Class<?> clazz) {
            if (ProtoUtils.JAVA_TYPES.containsKey(clazz) && !fieldBuilder.hasType()) {
                fieldBuilder.setType(ProtoUtils.JAVA_TYPES.get(clazz));
            } else if (clazz.isEnum()) {
                fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_ENUM);
                fieldBuilder.setTypeName(ProtoUtils.getQualifiedName(clazz));
                DescriptorProtos.EnumDescriptorProto enumProto = processEnum(clazz);
                if (parentClass != null && clazz.getDeclaringClass() == parentClass && !nestedEnums.contains(clazz)) {
                    messageBuilder.addEnumType(enumProto);
                    nestedEnums.add(clazz);
                }
            } else if (!ProtoUtils.JAVA_TYPES.containsKey(clazz)) {
                // Custom object type
                fieldBuilder.setTypeName(ProtoUtils.getQualifiedName(clazz));
                fieldBuilder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE);
                DescriptorProto msgProto = processClass(clazz);
                if (parentClass != null && clazz.getDeclaringClass() == parentClass && !nestedTypes.contains(clazz)) {
                    messageBuilder.addNestedType(msgProto);
                    nestedTypes.add(clazz);
                }
            }
        }
    }

    private DescriptorProtos.EnumDescriptorProto processEnum(Class<?> clazz) {
        if (enums.containsKey(clazz)) {
            return enums.get(clazz);
        }
        DescriptorProtos.EnumDescriptorProto.Builder enumBuilder = DescriptorProtos.EnumDescriptorProto.newBuilder();
        enumBuilder.setName(clazz.getSimpleName());
        Object[] constants = clazz.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            DescriptorProtos.EnumValueDescriptorProto.Builder valueBuilder = DescriptorProtos.EnumValueDescriptorProto.newBuilder();
            valueBuilder.setName(constants[i].toString());
            valueBuilder.setNumber(i);
            enumBuilder.addValue(valueBuilder);
        }
        DescriptorProtos.EnumDescriptorProto result = enumBuilder.build();
        enums.put(clazz, result);
        return result;
    }

    private void fillMessageBuilder(DescriptorProto.Builder messageBuilder, String qualifiedName, Class<?> cls) {
        Map<String, Integer> oneofIndices = new HashMap<>();
        AtomicInteger localFieldNumber = new AtomicInteger(1);

        MessageOptions.Builder messageOptions = messageBuilder.getOptions().toBuilder();
        String javaImplements = cls.getName();

        messageOptions.addUninterpretedOption(ProtoUtils.createUninterpretedOption("dev.akre.protege.java_implements", javaImplements));

        if (messageOptions.getUninterpretedOptionCount() > 0) {
            messageBuilder.setOptions(messageOptions);
        }

        List<Method> fieldMethods = Arrays.stream(cls.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Field.class)).sorted(Comparator.comparing(m -> m.getAnnotation(Field.class).value())).toList();

        for (Method method : fieldMethods) {
            if (method.getParameterCount() == 0 && !method.isDefault() && !method.isSynthetic()) {
                String propertyName = method.getName();
                if (propertyName.startsWith("get") && propertyName.length() > 3) {
                    propertyName = ProtoUtils.decapitalize(propertyName.substring(3));
                }
                FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder();
                fieldBuilder.setName(propertyName);

                Field fieldAnn = method.getAnnotation(Field.class);
                if (fieldAnn != null) {
                    fieldBuilder.setNumber(fieldAnn.value());
                    fieldAnn.type().ifPresent(fieldBuilder::setType);
                    if (!fieldAnn.oneof().isEmpty()) {
                        String oneofName = fieldAnn.oneof();
                        int index = oneofIndices.computeIfAbsent(oneofName, k -> {
                            int idx = messageBuilder.getOneofDeclCount();
                            messageBuilder.addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName(k));
                            return idx;
                        });
                        fieldBuilder.setOneofIndex(index);
                    }

                    DescriptorProtos.FieldOptions.Builder fieldOptions = DescriptorProtos.FieldOptions.newBuilder();
                    for (java.lang.annotation.Annotation ann : method.getAnnotations()) {
                        if (ann.annotationType().getName().equals(Field.class.getName())) {
                            continue;
                        }

                        fieldOptions.addUninterpretedOption(ProtoUtils.createUninterpretedOption("dev.akre.protege.java_annotation", ProtoUtils.annotationToString(ann)));
                    }
                    if (fieldOptions.getUninterpretedOptionCount() > 0) {
                        fieldBuilder.setOptions(fieldOptions);
                    }
                } else {
                    fieldBuilder.setNumber(localFieldNumber.getAndIncrement());
                }

                processType(messageBuilder, qualifiedName, method.getGenericReturnType(), fieldBuilder, propertyName, cls);
                if (fieldBuilder.getLabel() != FieldDescriptorProto.Label.LABEL_REPEATED) {
                    fieldBuilder.setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL);
                }
                messageBuilder.addField(fieldBuilder);
            }
        }

        // Add any remaining inner classes/enums not processed by fields
        for (Class<?> inner : cls.getDeclaredClasses()) {
            if (!inner.isSynthetic()) {
                if (inner.isEnum()) {
                    if (!nestedEnums.contains(inner)) {
                        messageBuilder.addEnumType(processEnum(inner));
                        nestedEnums.add(inner);
                    }
                } else {
                    if (!nestedTypes.contains(inner)) {
                        messageBuilder.addNestedType(processClass(inner));
                        nestedTypes.add(inner);
                    }
                }
            }
        }
    }

    private DescriptorProto processClass(Class<?> clazz) {
        if (messages.containsKey(clazz)) {
            return messages.get(clazz);
        }

        DescriptorProto.Builder messageBuilder = DescriptorProto.newBuilder();
        messageBuilder.setName(clazz.getSimpleName());
        messages.put(clazz, messageBuilder.build()); // Placeholder

        fillMessageBuilder(messageBuilder, ProtoUtils.getQualifiedName(clazz), clazz);

        DescriptorProto result = messageBuilder.build();
        messages.put(clazz, result);
        return result;
    }
}
