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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("*")
@SupportedOptions("protoDir")
@AutoService(Processor.class)
public class ProtoCompilerProcessor extends AbstractProcessor {

    private final java.util.Set<Path> processedFiles = new java.util.HashSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            try {
                runProtoCompilation(resolveProtoDirs());
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, e.getMessage());
            }
        }
        return false;
    }

    private void runProtoCompilation(List<Path> directories) {
        List<Path> protoFiles = new ArrayList<>();
        for (Path directory : directories) {
            if (!Files.exists(directory)) {
                continue;
            }
            try (var stream = Files.walk(directory)) {
                List<Path> found = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".proto"))
                        .toList();
                protoFiles.addAll(found);
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to walk directory " + directory + ": " + e.getMessage());
            }
        }

        ProtoCodegen codegen = new ProtoCodegen(processingEnv.getFiler());
        for (Path protoFile : protoFiles) {
            if (processedFiles.contains(protoFile)) {
                continue;
            }
            try {
                var fileDescriptor = ProtoUtils.parseProto(protoFile.toFile());
                codegen.generateFile(fileDescriptor);

                CodegenMetadata config = CodegenMetadata.build(fileDescriptor)
                        .build();
                new GrpcCodegen(processingEnv.getFiler(), config).generateFile();
                processedFiles.add(protoFile);
            } catch (Exception e) {
                processingEnv.getMessager().printMessage(javax.tools.Diagnostic.Kind.ERROR, "Failed to compile proto " + protoFile + ": " + e.getMessage());
            }
        }
    }

    private List<Path> resolveProtoDirs() throws IOException {
        List<Path> dirs = new ArrayList<>();
        // Priority 1: Check for compiler argument -AprotoDir=...
        String argPath = processingEnv.getOptions().get("protoDir");
        if (argPath != null) {
            dirs.add(Paths.get(argPath));
        } else {
             // Fallback to searching relative to CLASS_OUTPUT
            try {
                FileObject resource = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_OUTPUT, "", "lookup-stub");
                Path outputPath = Paths.get(resource.toUri());

                // Search upwards for 'src/main/proto'
                Path current = outputPath;
                while (current != null) {
                    Path candidate = current.resolve("src/main/proto");
                    if (Files.exists(candidate)) {
                        dirs.add(candidate);
                        break;
                    }
                    current = current.getParent();
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // Include generated sources
        try {
            FileObject dummy;
            try {
                dummy = processingEnv.getFiler().getResource(
                        StandardLocation.SOURCE_OUTPUT, "", "proto-compiler-lookup-stub");
            } catch (IOException e) {
                try {
                    dummy = processingEnv.getFiler().createResource(
                            StandardLocation.SOURCE_OUTPUT, "", "proto-compiler-lookup-stub");
                } catch (Exception ex) {
                    // If creation fails (e.g. exists but getResource failed?), we might be stuck.
                    // But usually getResource works if it exists.
                    // If FilerException (already created in this run), we can't get it easily.
                    // But we are in a new round. Filer resets? No.
                    // If we created it in Round 1, getResource in Round 2 should find it.
                    throw ex;
                }
            }
            Path path = Paths.get(dummy.toUri()).getParent();
            dirs.add(path);
        } catch (Exception e) {
             // ignore
        }

        if (dirs.isEmpty()) {
             throw new IOException("Could not locate proto directory. Please provide -AprotoDir argument.");
        }

        return dirs;
    }
}
