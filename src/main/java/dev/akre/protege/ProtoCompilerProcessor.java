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

/**
 * An annotation processor that compiles {@code .proto} files into Java source code.
 * <p>
 * This processor runs once during the compilation process. It locates {@code .proto} files
 * (specified by the {@code -AprotoDir} option or inferred from the source path) and
 * generates the corresponding Java classes using {@link ProtoCodegen} and {@link GrpcCodegen}.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("protoDir")
@AutoService(Processor.class)
public class ProtoCompilerProcessor extends AbstractProcessor {

    private boolean hasRun = false;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * Processes the annotations for the current round.
     * <p>
     * This method executes the Protobuf compilation logic exactly once during the first active round.
     *
     * @param annotations the annotations to process
     * @param roundEnv    the environment for the current round
     * @return {@code false} as this processor does not claim any annotations
     */
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

    /**
     * Orchestrates the compilation of all {@code .proto} files in the specified directory.
     *
     * @param directory the directory containing {@code .proto} files
     */
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
                        .build();
                new GrpcCodegen(processingEnv.getFiler(), config).generateFile();
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to compile protos: " + e.getMessage());
        }
    }

    /**
     * Resolves the directory containing the {@code .proto} files.
     * <p>
     * It first checks for the {@code -AprotoDir} compiler option. If not present, it attempts
     * to locate a {@code src/main/proto} directory relative to the compilation output.
     *
     * @return the path to the proto directory
     * @throws IOException if the directory cannot be located
     */
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
