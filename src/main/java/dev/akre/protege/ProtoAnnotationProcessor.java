package dev.akre.protege;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;

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

                    GenProto gp = typeElement.getAnnotation(GenProto.class);
                    String fileName = (gp != null && !gp.value().isEmpty()) ? gp.value() : typeElement.getSimpleName().toString();

                    FileObject fileObject = processingEnv.getFiler().createResource(
                            StandardLocation.SOURCE_OUTPUT,
                            packageName,
                            fileName + ".proto"
                    );
                    try (Writer writer = fileObject.openWriter()) {
                        writer.write(protoContent);
                    }

                    // Force another round
                    JavaFileObject trigger = processingEnv.getFiler().createSourceFile(packageName + "." + fileName + "_ProtoGenTrigger", typeElement);
                    try (Writer writer = trigger.openWriter()) {
                        writer.write("package " + packageName + ";\n");
                        writer.write("public class " + fileName + "_ProtoGenTrigger {}\n");
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
        sb.append("import \"options.proto\";\n\n");

        GenProto genProto = typeElement.getAnnotation(GenProto.class);
        String messageName = (genProto != null && !genProto.value().isEmpty()) ? genProto.value() : typeElement.getSimpleName().toString();

        sb.append("message ").append(messageName).append(" {\n");

        for (AnnotationMirror mirror : typeElement.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(GenProto.class.getName())) continue;
            sb.append("  option (dev.akre.protege.java_class_annotation) = \"")
              .append(escape(annotationToString(mirror)))
              .append("\";\n");
        }

        int count = 1;
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                Field fieldAnn = method.getAnnotation(Field.class);

                if (fieldAnn != null) {
                    String name = method.getSimpleName().toString();
                    if (name.startsWith("get")) {
                        name = decapitalize(name.substring(3));
                    }
                    String type = getProtoType(method.getReturnType());
                    int number = fieldAnn.value();

                    sb.append("  ").append(type).append(" ").append(name).append(" = ").append(number);

                    StringBuilder options = new StringBuilder();
                    boolean firstOption = true;
                    for (AnnotationMirror mirror : method.getAnnotationMirrors()) {
                        if (mirror.getAnnotationType().toString().equals(Field.class.getName())) continue;

                        if (firstOption) {
                             sb.append(" [\n");
                             firstOption = false;
                        } else {
                             sb.append(",\n");
                        }
                        sb.append("    (dev.akre.protege.java_field_annotation) = \"")
                          .append(escape(annotationToString(mirror)))
                          .append("\"");
                    }
                    if (!firstOption) {
                        sb.append("\n  ]");
                    }
                    sb.append(";\n");
                }
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String annotationToString(AnnotationMirror mirror) {
        StringBuilder sb = new StringBuilder();
        sb.append("@").append(mirror.getAnnotationType().toString());
        Map<? extends ExecutableElement, ? extends AnnotationValue> values = mirror.getElementValues();

        if (!values.isEmpty()) {
            sb.append("(");
            boolean first = true;
            for (var entry : values.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey().getSimpleName()).append("=");
                sb.append(valueToString(entry.getValue()));
                first = false;
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private String valueToString(AnnotationValue av) {
        Object v = av.getValue();
        if (v instanceof VariableElement ve) {
             // Enum constant
             return ve.getEnclosingElement().toString() + "." + ve.getSimpleName();
        } else if (v instanceof List<?> list) {
             // Array
             return "{" + ((List<AnnotationValue>)list).stream().map(this::valueToString).collect(Collectors.joining(", ")) + "}";
        } else if (v instanceof String) {
             return "\"" + v + "\"";
        } else if (v instanceof TypeMirror tm) {
             return tm.toString() + ".class";
        } else if (v instanceof Character) {
             return "'" + v + "'";
        }
        return v.toString();
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

    private String decapitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
