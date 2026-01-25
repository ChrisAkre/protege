package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.palantir.javapoet.*;
import dev.akre.protege.ProtoUtils;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public record FieldCodegen(
        DescriptorProtos.FieldDescriptorProto field,
        TypeName fieldType,
        String fieldName,
        String pascalName,
        String internalName,
        boolean isMap,
        boolean isRepeated,
        List<AnnotationSpec> getterAnnotations,
        // Map specific
        DescriptorProtos.DescriptorProto entryDescriptor,
        TypeName keyType,
        TypeName valueType,
        // Repeated specific
        TypeName genericType,
        // Context
        MessageCodegen messageCodegen
) {

    public static FieldCodegen create(DescriptorProtos.FieldDescriptorProto field, MessageCodegen messageCodegen) {
        var ctx = messageCodegen.ctx();
        var fieldType = ctx.getFieldType(field, messageCodegen.currentScope());

        String fieldName = field.getName();
        String pascalName = ProtoUtils.toPascalCase(fieldName);
        String internalName = fieldName + "_";
        boolean isRepeated = field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED;
        boolean isMap = ctx.isMapField(field);
        List<AnnotationSpec> getterAnnotations = CodegenUtils.getGetterAnnotations(field.getOptions());

        DescriptorProtos.DescriptorProto entryDescriptor = null;
        TypeName keyType = null;
        TypeName valueType = null;
        TypeName genericType = null;

        if (isMap) {
            entryDescriptor = ctx.getEntryDescriptor(field);
            var keyField = entryDescriptor.getField(0);
            var valueField = entryDescriptor.getField(1);
            keyType = ctx.getFieldType(keyField, messageCodegen.currentScope());
            valueType = ctx.getFieldType(valueField, messageCodegen.currentScope());
        } else if (isRepeated) {
            genericType = ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(field.getType());
            if (field.hasTypeName()) {
                genericType = ctx.resolveTypeName(field.getTypeName(), messageCodegen.currentScope());
            }
            if (genericType == null) {
                genericType = TypeName.get(Object.class);
            }
            genericType = genericType.box();
        }

        return new FieldCodegen(
                field,
                fieldType,
                fieldName,
                pascalName,
                internalName,
                isMap,
                isRepeated,
                getterAnnotations,
                entryDescriptor,
                keyType,
                valueType,
                genericType,
                messageCodegen
        );
    }

    public List<MethodSpec> getterMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        if (isMap) {
            methods.add(MessageMethods.containsMapKey(this));
            methods.add(MessageMethods.getMapField(this, fieldType));
            methods.add(MessageMethods.getMapCount(this));
            methods.add(MessageMethods.getMapOrDefault(this));
            methods.add(MessageMethods.getMapOrThrow(this));

            if (messageCodegen.protoCodegen().generateDeprecated()) {
                methods.add(MessageMethods.getMapDeprecated(this, fieldType));
            }
        } else if (isRepeated) {
            if (isString()) {
                methods.add(MessageMethods.getRepeatedListString(this));
                methods.add(MessageMethods.getRepeatedBytes(this));
            } else {
                methods.add(MessageMethods.getRepeatedList(this, fieldType));
            }
            methods.add(MessageMethods.getRepeatedCount(this));
            methods.add(MessageMethods.getRepeatedElement(this));

            if (isMessage()) {
                 var orBuilderType = MessageCodegen.getOrBuilderType(genericType);
                 methods.add(MessageMethods.getRepeatedOrBuilderList(this, orBuilderType));
                 methods.add(MessageMethods.getRepeatedOrBuilder(this, orBuilderType));
            }

            if (isEnum()) {
                methods.add(MessageMethods.getRepeatedValueList(this));
                methods.add(MessageMethods.getRepeatedValue(this));
            }
        } else {
            // Singular
            if (isMessage() || hasOneofIndex()) {
                methods.add(MessageMethods.hasField(this, generateHasFieldCode()));
            }

            methods.add(MessageMethods.getField(this));

            if (isEnum()) {
                methods.add(MessageMethods.getFieldValue(this));
            }

            if (isMessage()) {
                methods.add(MessageMethods.getFieldOrBuilder(this, MessageCodegen.getOrBuilderType(fieldType)));
            }

            if (isString()) {
                methods.add(MessageMethods.getFieldBytes(this));
            }
        }
        return methods;
    }

    public List<MethodSpec> builderMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        ClassName builderClassName = messageCodegen.builderClassName();

        if (isMap) {
             methods.add(CodegenMethods.Builder.containsMapKey(this));
             methods.add(CodegenMethods.Builder.getMapField(this, fieldType));
             methods.add(CodegenMethods.Builder.getMapCount(this));
             methods.add(CodegenMethods.Builder.getMapOrDefault(this));
             methods.add(CodegenMethods.Builder.getMapOrThrow(this));

             if (messageCodegen.protoCodegen().generateDeprecated()) {
                 methods.add(CodegenMethods.Builder.getMapDeprecated(this, fieldType));
                 methods.add(CodegenMethods.Builder.getMutableMapDeprecated(this, fieldType));
             }

             methods.add(CodegenMethods.Builder.putMap(this, builderClassName));
             if (valueType instanceof ClassName && valueFieldType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                 var valueBuilderType = ((ClassName) valueType).nestedClass("Builder");
                 methods.add(CodegenMethods.Builder.putMapBuilderIfAbsent(this, valueBuilderType));
             }
             methods.add(CodegenMethods.Builder.removeMap(this, builderClassName));
             methods.add(CodegenMethods.Builder.putAllMap(this, builderClassName, fieldType));
             methods.add(CodegenMethods.Builder.clearMap(this, builderClassName));
        } else if (isRepeated) {
             if (isString()) {
                 methods.add(CodegenMethods.Builder.getRepeatedListString(this));
                 methods.add(CodegenMethods.Builder.getRepeatedBytes(this));
                 methods.add(CodegenMethods.Builder.addRepeatedBytes(this, builderClassName));
             } else {
                 methods.add(CodegenMethods.Builder.getRepeatedList(this, fieldType));
             }

             methods.add(CodegenMethods.Builder.getRepeatedCount(this));
             methods.add(CodegenMethods.Builder.getRepeatedElement(this));
             methods.add(CodegenMethods.Builder.setRepeatedElement(this, builderClassName));
             methods.add(CodegenMethods.Builder.addRepeatedElement(this, builderClassName));
             methods.add(CodegenMethods.Builder.addAllRepeatedElements(this, builderClassName));
             methods.add(CodegenMethods.Builder.clearRepeatedField(this, builderClassName));

             if (isMessage()) {
                 var orBuilderType = MessageCodegen.getOrBuilderType(genericType);
                 var elementBuilderType = ((ClassName) genericType).nestedClass("Builder");

                 methods.add(CodegenMethods.Builder.getRepeatedOrBuilderList(this, orBuilderType));
                 methods.add(CodegenMethods.Builder.getRepeatedOrBuilder(this, orBuilderType));
                 methods.add(CodegenMethods.Builder.getRepeatedBuilder(this, elementBuilderType));
                 methods.add(CodegenMethods.Builder.addRepeatedBuilder(this, elementBuilderType));
                 methods.add(CodegenMethods.Builder.addRepeatedBuilderAtIndex(this, elementBuilderType));
                 methods.add(CodegenMethods.Builder.getRepeatedBuilderList(this, elementBuilderType));
                 methods.add(CodegenMethods.Builder.addRepeatedElementAtIndex(this, builderClassName));
                 methods.add(CodegenMethods.Builder.setRepeatedBuilder(this, builderClassName, elementBuilderType));
                 methods.add(CodegenMethods.Builder.addRepeatedBuilderValue(this, builderClassName, elementBuilderType));
                 methods.add(CodegenMethods.Builder.addRepeatedBuilderValueAtIndex(this, builderClassName, elementBuilderType));
                 methods.add(CodegenMethods.Builder.removeRepeatedElement(this, builderClassName));
             }

             if (isEnum()) {
                 methods.add(CodegenMethods.Builder.getRepeatedValueList(this));
                 methods.add(CodegenMethods.Builder.getRepeatedValue(this));
                 methods.add(CodegenMethods.Builder.setRepeatedValue(this, builderClassName));
                 methods.add(CodegenMethods.Builder.addRepeatedValue(this, builderClassName));
                 methods.add(CodegenMethods.Builder.addAllRepeatedValue(this, builderClassName));
             }

             methods.add(CodegenMethods.Builder.ensureIsMutable(this));

        } else {
             // Singular
             if (isMessage() || hasOneofIndex()) {
                 methods.add(CodegenMethods.Builder.hasField(this, generateHasFieldCode()));
             }

             methods.add(CodegenMethods.Builder.getField(this));

             if (isEnum()) {
                 methods.add(CodegenMethods.Builder.getFieldValue(this));
                 methods.add(CodegenMethods.Builder.setFieldValue(this, builderClassName));
             }

             if (isString()) {
                 methods.add(CodegenMethods.Builder.getFieldBytes(this));
                 var clearOneof = generateClearOneofCode();
                 methods.add(CodegenMethods.Builder.setFieldBytes(this, builderClassName, clearOneof));
             }

             var clearOneof = generateClearOneofCode();
             methods.add(CodegenMethods.Builder.setField(this, builderClassName, clearOneof));

             String defaultValue;
             if (field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                 defaultValue = "\"\"";
             } else {
                 defaultValue = ProtoUtils.getDefaultReturnValue(fieldType.toString());
             }
             methods.add(CodegenMethods.Builder.clearField(this, builderClassName, defaultValue));

             if (isMessage()) {
                 var elementBuilderType = ((ClassName) fieldType).nestedClass("Builder");
                 methods.add(CodegenMethods.Builder.getFieldOrBuilder(this, MessageCodegen.getOrBuilderType(fieldType)));
                 methods.add(CodegenMethods.Builder.getFieldBuilder(this, elementBuilderType));
                 methods.add(CodegenMethods.Builder.setFieldBuilder(this, builderClassName, elementBuilderType));
                 methods.add(CodegenMethods.Builder.mergeField(this, builderClassName, clearOneof));
             }
        }
        return methods;
    }

    public List<MethodSpec> abstractMethods() {
        List<MethodSpec> methods = new ArrayList<>();
        if (isMap) {
            methods.add(MessageMethods.abstractContainsMapKey(this));
            methods.add(MessageMethods.abstractGetMapField(this));
            methods.add(MessageMethods.abstractGetMapCount(this));
            methods.add(MessageMethods.abstractGetMapOrDefault(this));
            methods.add(MessageMethods.abstractGetMapOrThrow(this));

            if (messageCodegen.protoCodegen().generateDeprecated()) {
                methods.add(MessageMethods.abstractGetMapDeprecated(this, fieldType));
            }
        } else if (isRepeated) {
            methods.add(MessageMethods.abstractGetRepeatedList(this, fieldType));

            if (isString()) {
                methods.add(MessageMethods.abstractGetRepeatedBytes(pascalName));
            }

            methods.add(MessageMethods.abstractGetRepeatedCount(this));
            methods.add(MessageMethods.abstractGetRepeatedElement(this));

            if (isMessage()) {
                 var orBuilderType = MessageCodegen.getOrBuilderType(genericType);
                 methods.add(MessageMethods.abstractGetRepeatedOrBuilderList(this, orBuilderType));
                 methods.add(MessageMethods.abstractGetRepeatedOrBuilder(this, orBuilderType));
            }

            if (isEnum()) {
                methods.add(MessageMethods.abstractGetRepeatedValueList(this));
                methods.add(MessageMethods.abstractGetRepeatedValue(this));
            }
        } else {
            // Singular
            if (isMessage() || hasOneofIndex()) {
                methods.add(MessageMethods.abstractHasField(this));
            }

            methods.add(MessageMethods.abstractGetField(this));

            if (isEnum()) {
                methods.add(MessageMethods.abstractGetFieldValue(pascalName));
            }

            if (isMessage()) {
                methods.add(MessageMethods.abstractGetFieldOrBuilder(this, MessageCodegen.getOrBuilderType(fieldType)));
            }

            if (isString()) {
                methods.add(MessageMethods.abstractGetFieldBytes(pascalName));
            }
        }
        return methods;
    }

    // Helper methods
    public int fieldNumber() {
        return field.getNumber();
    }

    public DescriptorProtos.FieldDescriptorProto.Type type() {
        return field.getType();
    }

    public boolean hasOneofIndex() {
        return field.hasOneofIndex();
    }

    public int oneofIndex() {
        return field.getOneofIndex();
    }

    public boolean isString() {
        if (isRepeated && !isMap) {
             return field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING;
    }

    public boolean isMessage() {
        if (isRepeated && !isMap) {
             return field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE;
    }

    public boolean isEnum() {
        if (isRepeated && !isMap) {
             return field.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
        }
        return type() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM;
    }

    private CodeBlock generateHasFieldCode() {
        if (field.hasOneofIndex()) {
            return CodeBlock.of("return $LCase_ == $L;\n", messageCodegen.message().getOneofDecl(field.getOneofIndex()).getName(), field.getNumber());
        }
        return CodeBlock.of("return $L_ != null;\n", field.getName());
    }

    public CodeBlock generateClearOneofCode() {
         if (!field.hasOneofIndex()) return CodeBlock.of("");
         int oneofIndex = field.getOneofIndex();
         var message = messageCodegen.message();
         var cb = CodeBlock.builder();
         cb.addStatement("$LCase_ = 0", message.getOneofDecl(oneofIndex).getName());
         for (var f : message.getFieldList()) {
             if (f.hasOneofIndex() && f.getOneofIndex() == oneofIndex) {
                 var fieldName = f.getName() + "_";
                 if (f.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                     cb.addStatement("$L = $T.emptyList()", fieldName, java.util.Collections.class);
                 } else if (f.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                     cb.addStatement("$L = null", fieldName);
                 } else if (f.getType() == DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING) {
                     cb.addStatement("$L = \"\"", fieldName);
                 } else {
                     cb.addStatement("$L = $L", fieldName, ProtoUtils.getDefaultReturnValue(ProtoCodegen.PROTO_TYPE_TO_TYPE_NAME.get(f.getType()).toString()));
                 }
             }
         }
         return cb.build();
    }

    private DescriptorProtos.FieldDescriptorProto.Type valueFieldType() {
        if (entryDescriptor == null) return DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32; // Default, shouldn't happen for map
        return entryDescriptor.getField(1).getType();
    }
}
