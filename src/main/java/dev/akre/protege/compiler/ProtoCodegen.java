package dev.akre.protege.compiler;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.GeneratedMessage;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import dev.akre.protege.ProtoUtils;
import dev.akre.protege.compiler.CodegenMethods.Enums;
import dev.akre.protege.compiler.CodegenMethods.Services;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static dev.akre.protege.compiler.CodegenContext.JAVA_ENHANCED_ONEOF_OPTION;

public class ProtoCodegen {

    public static final ClassName OR_BUILDER_INTERFACE = ClassName.get("com.google.protobuf", "MessageOrBuilder");
    private final Filer filer;
    private final boolean generateDeprecated;
    private final ClassName messageParentClass;
    static final Map<DescriptorProtos.FieldDescriptorProto.Type, TypeName> PROTO_TYPE_TO_TYPE_NAME;
    private static final ClassName DEFAULT_MESSAGE_PARENT = ClassName.get(GeneratedMessage.class);

    private final Map<String, List<ClassName>> oneofInterfacesByType = new HashMap<>();

    public Map<String, List<ClassName>> oneofInterfacesByType() {
        return oneofInterfacesByType;
    }

    static {
        PROTO_TYPE_TO_TYPE_NAME = Map.ofEntries(
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING, ClassName.get(String.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT, TypeName.FLOAT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE, TypeName.DOUBLE),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL, TypeName.BOOLEAN),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES, ClassName.get(com.google.protobuf.ByteString.class)),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, TypeName.LONG),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32, TypeName.INT),
                Map.entry(DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64, TypeName.LONG)
        );
    }


    public ProtoCodegen(Filer filer) {
        this(filer, true);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated) {
        this(filer, generateDeprecated, DEFAULT_MESSAGE_PARENT);
    }

    public ProtoCodegen(Filer filer, boolean generateDeprecated, ClassName messageParentClass) {
        this.filer = filer;
        this.generateDeprecated = generateDeprecated;
        this.messageParentClass = messageParentClass;
    }

    public ClassName messageParentClass() {
        return messageParentClass;
    }

    public ClassName getFieldAccessorTableClass() {
        return messageParentClass.nestedClass("FieldAccessorTable");
    }

    public boolean generateDeprecated() {
        return generateDeprecated;
    }

    public JavaFile generateFile(DescriptorProtos.FileDescriptorProto fileDescriptor) throws IOException {
        var ctx = CodegenContext.create(fileDescriptor, messageParentClass, generateDeprecated);
        var outerClass = new OuterClassCodegen(ctx, this).generate();

        var javaFile = JavaFile.builder(ctx.packageName(), outerClass).build();
        javaFile.writeTo(filer);
        return javaFile;
    }

    void generateServiceTypes(TypeSpec.Builder parentBuilder, DescriptorProtos.ServiceDescriptorProto service, CodegenContext ctx) {
        var serviceName = service.getName();

        var classBuilder = TypeSpec.classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.ABSTRACT)
                .addSuperinterface(com.google.protobuf.Service.class);

        classBuilder.addMethod(Services.constructor());
        classBuilder.addType(Services.generateInterface(service, ctx));
        classBuilder.addType(Services.generateBlockingInterface(service, ctx));

        classBuilder.addMethod(Services.getDescriptor(ctx, service));
        classBuilder.addMethod(Services.getDescriptorForType());
        classBuilder.addMethod(Services.callMethod(service, ctx));
        classBuilder.addMethod(Services.getRequestPrototype(service, ctx));
        classBuilder.addMethod(Services.getResponsePrototype(service, ctx));
        classBuilder.addMethod(Services.newStub());
        classBuilder.addType(Services.generateServiceStub(service, ctx));
        classBuilder.addMethod(Services.newBlockingStub());
        classBuilder.addType(Services.generateBlockingServiceStub(service, ctx));
        classBuilder.addMethod(Services.newReflectiveService(service, serviceName, ctx));
        classBuilder.addMethod(Services.newReflectiveBlockingService(service, ctx));

        service.getMethodList().stream().map(method -> Services.rpcMethod(method, ctx)).forEach(classBuilder::addMethod);

        parentBuilder.addType(classBuilder.build());
    }

    TypeSpec generateEnumType(EnumContext ctx) {
        var enumName = ctx.enumType().getName();
        var enumBuilder = TypeSpec.enumBuilder(enumName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(com.google.protobuf.ProtocolMessageEnum.class);

        for (var value : ctx.enumType().getValueList()) {
            enumBuilder.addEnumConstant(value.getName(), TypeSpec.anonymousClassBuilder("$L", value.getNumber()).build());
        }

        enumBuilder.addEnumConstant("UNRECOGNIZED", TypeSpec.anonymousClassBuilder("-1").build());

        enumBuilder.addField(int.class, "value", Modifier.PRIVATE, Modifier.FINAL);
        enumBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(int.class, "value")
                .addStatement("this.value = value")
                .build());

        enumBuilder.addMethod(Enums.getNumber());
        enumBuilder.addMethod(Enums.forNumber(ctx));
        enumBuilder.addMethod(Enums.getDescriptor(ctx));
        enumBuilder.addMethod(Enums.getValueDescriptor());
        enumBuilder.addMethod(Enums.getDescriptorForType());
        enumBuilder.addMethod(Enums.valueOf(enumName));
        return enumBuilder.build();
    }

    public Optional<String> getJavaImplements(DescriptorProtos.DescriptorProto message) {
        return message.getOptions().getUninterpretedOptionList().stream()
                .filter(o -> o.getNameList().stream().anyMatch(n -> n.getNamePart().endsWith("java_implements")))
                .map(DescriptorProtos.UninterpretedOption::getStringValue)
                .map(com.google.protobuf.ByteString::toStringUtf8)
                .findFirst();
    }

    private boolean getBooleanOption(List<DescriptorProtos.UninterpretedOption> options, String name, boolean defaultValue) {
        for (var option : options) {
            String optionName = option.getNameList().stream()
                    .map(DescriptorProtos.UninterpretedOption.NamePart::getNamePart)
                    .collect(Collectors.joining("."));
            if (optionName.equals(name)) {
                if (option.hasIdentifierValue()) {
                    return Boolean.parseBoolean(option.getIdentifierValue());
                }
            }
        }
        return defaultValue;
    }

    public void populateOneofInterfaces(CodegenContext ctx) {
        oneofInterfacesByType.clear();

        for (var message : ctx.fileDescriptor().getMessageTypeList()) {
            populateOneofInterfaces(message, ctx, new ArrayList<>());
        }
    }

    private void populateOneofInterfaces(DescriptorProtos.DescriptorProto message, CodegenContext ctx, List<String> parentPath) {
        boolean enhancedOneof = getBooleanOption(message.getOptions().getUninterpretedOptionList(), JAVA_ENHANCED_ONEOF_OPTION, ctx.fileEnhancedOneof());

        var currentPath = new ArrayList<>(parentPath);
        currentPath.add(message.getName());

        if (enhancedOneof) {
            for (int i = 0; i < message.getOneofDeclCount(); i++) {
                var oneof = message.getOneofDecl(i);
                var pascalName = ProtoUtils.toPascalCase(oneof.getName());

                var capitalizedPath = currentPath.stream().map(ProtoUtils::capitalize).toList();
                var interfaceClassName = ClassName.get(ctx.packageName(), ctx.outerName(), capitalizedPath.toArray(new String[0])).nestedClass(pascalName);

                for (var field : message.getFieldList()) {
                    if (field.hasOneofIndex() && field.getOneofIndex() == i) {
                        if (field.getType() != DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains non-message field '" + field.getName() + "'");
                        }
                        String typeName = field.getTypeName();
                        String relativeName = typeName;
                        if (relativeName.startsWith(".")) {
                            var protoPackage = ctx.fileDescriptor().getPackage();
                            if (!protoPackage.isEmpty() && relativeName.startsWith("." + protoPackage + ".")) {
                                relativeName = relativeName.substring(protoPackage.length() + 2);
                            } else if (relativeName.startsWith(".")) {
                                relativeName = relativeName.substring(1);
                            }
                        }
                        if (!ctx.typeRegistry().containsKey(relativeName)) {
                            throw new IllegalArgumentException("Enhanced oneof '" + oneof.getName() + "' in message '" + message.getName() + "' contains field '" + field.getName() + "' with type '" + typeName + "' not defined in the current file.");
                        }

                        TypeName typeNameRes = ctx.resolveTypeName(field.getTypeName(), currentPath);
                        if (typeNameRes instanceof ClassName cn) {
                            oneofInterfacesByType.computeIfAbsent(cn.canonicalName(), k -> new ArrayList<>()).add(interfaceClassName);
                        }
                    }
                }
            }
        }

        for (var nested : message.getNestedTypeList()) {
            populateOneofInterfaces(nested, ctx, currentPath);
        }
    }
}
