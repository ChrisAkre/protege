package dev.akre.protege;

import com.google.auto.service.AutoService;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

@SupportedAnnotationTypes("dev.akre.protege.GenProto")
@AutoService(Processor.class)
public class ProtoAnnotationProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.INTERFACE) {
                    continue;
                }
                try {
                    TypeElement typeElement = (TypeElement) element;
                    String protoContent = generateProto(typeElement);
                    String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
                    FileObject fileObject = processingEnv.getFiler().createResource(
                            StandardLocation.SOURCE_OUTPUT,
                            packageName,
                            typeElement.getSimpleName() + ".proto"
                    );
                    try (Writer writer = fileObject.openWriter()) {
                        writer.write(protoContent);
                    }
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, e.getMessage());
                }
            }
        }
        return true;
    }

    private String generateProto(TypeElement typeElement) {
        StringBuilder sb = new StringBuilder();
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("package ").append(processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName()).append(";\n\n");
        sb.append("message ").append(typeElement.getSimpleName()).append(" {");
        int count = 1;
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                String name = method.getSimpleName().toString();
                if (name.startsWith("get")) {
                    name = StringUtils.uncapitalize(name.substring(3));
                    String type = getProtoType(method.getReturnType());
                    sb.append("  ").append(type).append(" ").append(name).append(" = ").append(count++).append(";\n");
                }
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String getProtoType(javax.lang.model.type.TypeMirror type) {
        return switch (type.toString()) {
            case "java.lang.String" -> "string";
            case "int" -> "int32";
            case "long" -> "int64";
            case "boolean" -> "bool";
            case "float" -> "float";
            case "double" -> "double";
            default -> "string"; // Default
        };
    }
}