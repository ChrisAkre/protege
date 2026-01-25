package dev.akre.protege.testutil;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import dev.akre.protege.GenProto;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.compiler.ProtoCodegen;
import dev.akre.protege.ProtoUtils;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.params.provider.Arguments;

import dev.akre.protege.compiler.GrpcCodegen;
import com.google.testing.compile.JavaFileObjects;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TestProtos {
    public static final List<Arguments> DESCRIPTORS;
    static {
        Path protoDir = Paths.get("src/test/proto");
        try (var files = Files.list(protoDir)) {
            DESCRIPTORS = files.filter(p -> p.toString().endsWith(".proto"))
                    .map(protoPath -> {
                        try {
                            if (protoPath.endsWith("extra_features.proto")) {
                                System.out.println("hi");
                            }
                            DescriptorProtos.FileDescriptorProto parsedProto = ProtoUtils.parseProto(protoPath.toFile());
                            String outerClassName = TestUtils.makeOuterClassName(parsedProto, protoPath.getFileName().toString());
                            Class<?> expectedClass;
                            try {
                                expectedClass = Class.forName(outerClassName);
                            } catch (ClassNotFoundException e) {
                                if (!parsedProto.hasSyntax()) {
                                    // probably a commented out file
                                    return null;
                                }
                                throw e;
                            }
                            ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler());
                            Class<?> generatedClass = TestUtils.compile(outerClassName, codegen.generateFile(parsedProto).toJavaFileObject());
                            ClassAssert.assertThat(generatedClass)
                                    .hasPublicStaticFinalStringField("PROTEGE_VERSION", ProtegeVersion.VERSION_STRING);
                            return Arguments.of(protoPath, parsedProto, expectedClass, generatedClass);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to prepare parameters for " + protoPath, e);
                        }
                    }).filter(Objects::nonNull).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static final List<Arguments> INTERFACE_DESCRIPTORS;
    static {
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages("com.example.interfaces")
                .scan()) {
            INTERFACE_DESCRIPTORS = scanResult.getAllClasses()
                    .filter(classInfo -> !classInfo.isInnerClass())
                    .filter(classInfo -> classInfo.hasAnnotation(GenProto.class.getName()))
                    .loadClasses()
                    .stream()
                    .flatMap(c -> {
                        GenProto genProto = c.getAnnotation(GenProto.class);
                        String protoFile = genProto.value();

                        Arguments descriptorArgs = DESCRIPTORS.stream()
                                .filter(args -> ((Path) args.get()[0]).getFileName().toString().equals(protoFile))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("No descriptor found for " + protoFile));

                        Class<?> expectedClass = (Class<?>) descriptorArgs.get()[2];

                        Stream<Class<?>> classesToProcess = genProto.outerClass()
                                ? Stream.of(c.getDeclaredClasses()).filter(inner -> !inner.isSynthetic())
                                : Stream.of(c);

                        return classesToProcess.map(cls -> {
                            try {
                                String msgName = cls.getSimpleName();
                                Descriptors.Descriptor descriptor = (Descriptors.Descriptor) TestUtils.findInnerClass(expectedClass, msgName)
                                        .orElseThrow(() -> new RuntimeException("Message " + msgName + " not found in " + expectedClass.getName()))
                                        .getMethod("getDescriptor").invoke(null);
                                return Arguments.of(cls.getName(), protoFile, c, descriptor);
                            } catch (Exception e) {
                                throw new RuntimeException("Error processing class " + cls.getName() + " with proto " + protoFile, e);
                            }
                        });
                    })
                    .toList();
        }
    }
    public static final List<Arguments> TOP_LEVEL_MESSAGES;
    static {
        TOP_LEVEL_MESSAGES = DESCRIPTORS.stream().map(Arguments::get).flatMap(args -> {
            Path protoPath = (Path) args[0];
            DescriptorProtos.FileDescriptorProto parsedProto = (DescriptorProtos.FileDescriptorProto) args[1];
            Class<?> expectedClass = (Class<?>) args[2];
            Class<?> generatedClass = (Class<?>) args[3];

            return parsedProto.getMessageTypeList().stream()
                    .filter(Predicate.not(m -> m.getOptions().getMapEntry()))
                    .map(message -> {
                        String messageName = message.getName();
                        Class<?> expectedMessage = TestUtils.findInnerClass(expectedClass, messageName).orElseThrow();
                        Class<?> generatedMessage = TestUtils.findInnerClass(generatedClass, messageName).orElseThrow();
                        return Arguments.of(protoPath, parsedProto, messageName, expectedMessage, generatedMessage);
                    });
        }).toList();
    }

    public static final List<Arguments> ALL_MESSAGES;
    static {
        ALL_MESSAGES = DESCRIPTORS.stream().map(Arguments::get).flatMap(args -> {
            Path protoPath = (Path) args[0];
            DescriptorProtos.FileDescriptorProto parsedProto = (DescriptorProtos.FileDescriptorProto) args[1];
            Class<?> expectedClass = (Class<?>) args[2];
            Class<?> generatedClass = (Class<?>) args[3];

            Stream.Builder<Arguments> builder = Stream.builder();
            for (DescriptorProtos.DescriptorProto message : parsedProto.getMessageTypeList()) {
                collectMessages(builder, protoPath, parsedProto, message, expectedClass, generatedClass);
            }
            return builder.build();
        }).toList();
    }

    public static final List<Arguments> GENERIC_SERVICES;
    static {
        GENERIC_SERVICES = DESCRIPTORS.stream().map(Arguments::get).flatMap(args -> {
            Path protoPath = (Path) args[0];
            DescriptorProtos.FileDescriptorProto parsedProto = (DescriptorProtos.FileDescriptorProto) args[1];
            Class<?> expectedClass = (Class<?>) args[2];
            Class<?> generatedClass = (Class<?>) args[3];

            if (!parsedProto.getOptions().getJavaGenericServices()) {
                return Stream.empty();
            }

            return parsedProto.getServiceList().stream()
                    .map(service -> {
                        String serviceName = service.getName();
                        Class<?> expectedService = TestUtils.findInnerClass(expectedClass, serviceName).orElseThrow();
                        Class<?> generatedService = TestUtils.findInnerClass(generatedClass, serviceName).orElseThrow();
                        return Arguments.of(protoPath, parsedProto, serviceName, expectedService, generatedService);
                    });
        }).toList();
    }

    public static final List<Arguments> GRPC_SERVICES;
    static {
        GRPC_SERVICES = DESCRIPTORS.stream().map(Arguments::get).flatMap(args -> {
            Path protoPath = (Path) args[0];
            DescriptorProtos.FileDescriptorProto parsedProto = (DescriptorProtos.FileDescriptorProto) args[1];
            Class<?> expectedClass = (Class<?>) args[2];

            if (parsedProto.getServiceList().isEmpty()) {
                return Stream.empty();
            }

            try {
                TestUtils.MockFiler filer = new TestUtils.MockFiler();
                ProtoCodegen protoCodegen = new ProtoCodegen(filer);
                GrpcCodegen grpcCodegen = new GrpcCodegen(filer);

                protoCodegen.generateFile(parsedProto);
                grpcCodegen.generateFile(parsedProto);

                List<JavaFileObject> sourceFiles = filer.getSources().entrySet().stream()
                        .map(e -> JavaFileObjects.forSourceString(e.getKey(), e.getValue()))
                        .toList();

                String outerClassName = ProtoUtils.getJavaPackage(parsedProto) + "." + ProtoUtils.getJavaOuterClassName(parsedProto);
                Class<?> firstGenerated = TestUtils.compile(outerClassName, sourceFiles);
                ClassLoader loader = firstGenerated.getClassLoader();

                return parsedProto.getServiceList().stream().map(service -> {
                    try {
                        String serviceName = service.getName();
                        String packageName = ProtoUtils.getJavaPackage(parsedProto);
                        String fullGrpcClassName = packageName + "." + serviceName + "Grpc";

                        Class<?> generatedService = loader.loadClass(fullGrpcClassName);
                        Class<?> generatedMessage = loader.loadClass(outerClassName);
                        Class<?> expectedService = Class.forName(fullGrpcClassName);

                        ClassAssert.assertThat(generatedMessage)
                                .hasPublicStaticFinalStringField("PROTEGE_VERSION", ProtegeVersion.VERSION_STRING);

                        ClassAssert.assertThat(generatedService)
                                .hasPublicStaticFinalStringField("PROTEGE_VERSION", ProtegeVersion.VERSION_STRING);

                        return Arguments.of(generatedMessage, generatedService, expectedClass, expectedService, protoPath, parsedProto, serviceName);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
    }

    private static void collectMessages(Stream.Builder<Arguments> builder, Path protoPath, DescriptorProtos.FileDescriptorProto parsedProto, DescriptorProtos.DescriptorProto message, Class<?> expectedParent, Class<?> generatedParent) {
        if (message.getOptions().getMapEntry()) {
            return;
        }

        String messageName = message.getName();
        Class<?> expectedMessage = TestUtils.findInnerClass(expectedParent, messageName).orElseThrow();
        Class<?> generatedMessage = TestUtils.findInnerClass(generatedParent, messageName).orElseThrow();
        builder.add(Arguments.of(protoPath, parsedProto, messageName, expectedMessage, generatedMessage));

        for (DescriptorProtos.DescriptorProto nestedMessage : message.getNestedTypeList()) {
            collectMessages(builder, protoPath, parsedProto, nestedMessage, expectedMessage, generatedMessage);
        }
    }
}
