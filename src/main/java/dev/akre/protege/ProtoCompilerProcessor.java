package dev.akre.protege;

import com.google.auto.service.AutoService;
import dev.akre.protege.compiler.CodegenMetadata;
import dev.akre.protege.compiler.GrpcCodegen;
import dev.akre.protege.compiler.ProtoCodegen;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedOptions("protoDir")
@AutoService(Processor.class)
public class ProtoCompilerProcessor extends AbstractProcessor {

    private boolean hasRun = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Execute exactly once during the first active round
        if (!hasRun && !roundEnv.processingOver()) {
            hasRun = true;
            try {
                runProtoCompilation(resolveProtoDir());
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, e.getMessage());
            }
        }
        return false;
    }

    private void runProtoCompilation(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            var protoFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".proto"))
                    .toList();

            ProtoCodegen codegen = new ProtoCodegen(processingEnv.getFiler());
            for (Path protoFile : protoFiles) {
                var fileDescriptor = ProtoUtils.parseProto(protoFile.toFile());
                codegen.generateFile(fileDescriptor);

                CodegenMetadata config = CodegenMetadata.build(fileDescriptor)
                        .setGenerateDeprecated(codegen.generateDeprecated())
                        .build();
                new GrpcCodegen(processingEnv.getFiler(), config).generateFile();
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to compile protos: " + e.getMessage());
        }
    }

    private Path resolveProtoDir() throws IOException {
        // Priority 1: Check for compiler argument -AprotoDir=...
        String argPath = processingEnv.getOptions().get("protoDir");
        if (argPath != null) {
            return Paths.get(argPath);
        }

        // Priority 2: Fallback to searching relative to CLASS_OUTPUT
        // We create a temporary resource to locate the filesystem context
        FileObject resource = processingEnv.getFiler().getResource(
                StandardLocation.CLASS_OUTPUT, "", "lookup-stub");
        Path outputPath = Paths.get(resource.toUri());

        // Search upwards for 'src/main/proto'
        Path current = outputPath;
        while (current != null) {
            Path candidate = current.resolve("src/main/proto");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        throw new IOException("Could not locate proto directory. Please provide -AprotoDir argument.");
    }
}
