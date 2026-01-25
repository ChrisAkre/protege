package dev.akre.protege.testutil;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.BlockingRpcChannel;
import com.google.protobuf.BlockingService;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import dev.akre.protege.ProtoUtils;
import io.grpc.*;
import io.grpc.stub.ServerCalls;
import org.assertj.core.api.AbstractAssert;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static com.google.testing.compile.Compiler.javac;


public class TestUtils {

    public static Descriptors.FileDescriptor getFileDescriptor(Class<?> protocClass) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method getProtocFileDescriptorMethod = protocClass.getMethod("getDescriptor");
        return (Descriptors.FileDescriptor) getProtocFileDescriptorMethod.invoke(null);
    }

    public static Descriptors.ServiceDescriptor getServiceDescriptor(Class<?> serviceClass) {
        try {
            Method getDescriptorMethod = serviceClass.getMethod("getDescriptor");
            return (Descriptors.ServiceDescriptor) getDescriptorMethod.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ServiceDescriptor getGrpcServiceDescriptor(Class<?> serviceGrpcClass) throws Exception {
        return (ServiceDescriptor) serviceGrpcClass.getMethod("getServiceDescriptor").invoke(null);
    }

    @SuppressWarnings("unchecked")
    public static MethodDescriptor<Object, Object> getMethodDescriptor(ServiceDescriptor serviceDescriptor, String rpcName) {
        return (MethodDescriptor<Object, Object>) serviceDescriptor.getMethods().stream()
                .filter(m -> m.getFullMethodName().endsWith("/" + rpcName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Method descriptor not found for RPC: " + rpcName));
    }

    public static Class<?> getRequestClass(Method serviceMethod) {
        return serviceMethod.getParameterTypes()[0];
    }

    public static Class<?> getResponseClass(Method serviceMethod) {
        return (Class<?>) ((java.lang.reflect.ParameterizedType) serviceMethod.getGenericParameterTypes()[1]).getActualTypeArguments()[0];
    }

    public static Class<?> getBlockingInterface(Class<?> serviceClass) {
        return findInnerClass(serviceClass, "BlockingInterface")
                .orElseThrow(() -> new RuntimeException("Could not find BlockingInterface in " + serviceClass.getName()));
    }

    public static BlockingService newReflectiveBlockingService(Class<?> serviceClass, Class<?> blockingInterface, Object serviceImpl) {
        try {
            Method method = serviceClass.getMethod("newReflectiveBlockingService", blockingInterface);
            return (BlockingService) method.invoke(null, serviceImpl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object newBlockingStub(Class<?> serviceClass, BlockingRpcChannel channel) {
        try {
            Method method = serviceClass.getMethod("newBlockingStub", BlockingRpcChannel.class);
            return method.invoke(null, channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object newBlockingStub(Class<?> grpcClass, Channel channel) {
        try {
            Method method = grpcClass.getMethod("newBlockingStub", Channel.class);
            return method.invoke(null, channel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getFirstUnaryRpcName(DescriptorProtos.FileDescriptorProto parsedProto, String serviceName) {
        DescriptorProtos.ServiceDescriptorProto serviceProto = parsedProto.getServiceList().stream()
                .filter(s -> s.getName().equals(serviceName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Service not found: " + serviceName));

        DescriptorProtos.MethodDescriptorProto methodProto = serviceProto.getMethodList().stream()
                .filter(m -> !m.getClientStreaming() && !m.getServerStreaming())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No unary method found in service: " + serviceName));

        return methodProto.getName();
    }

    public static String toCamelCase(String s) {
        return ProtoUtils.decapitalize(ProtoUtils.toCamelCase(s));
    }

    public static String getJavaPackage(DescriptorProtos.FileDescriptorProto fileDescriptorProto) {
        return ProtoUtils.getJavaPackage(fileDescriptorProto);
    }

    public static String makeOuterClassName(DescriptorProtos.FileDescriptorProto fileDescriptorProto, String fileName) {
        return ProtoUtils.getJavaPackage(fileDescriptorProto) + "." + ProtoUtils.getJavaOuterClassName(fileDescriptorProto);
    }

    public static Class<?> compile(String mainClassName, List<JavaFileObject> sourceFiles) throws Exception {
        ClassLoader loader = TestUtils.class.getClassLoader();

        Compilation compilation = javac()
                .withClasspath(extractClasspath(loader))
                .compile(sourceFiles);

        try {
            com.google.testing.compile.CompilationSubject.assertThat(compilation).succeeded();
        } catch (Throwable t) {
            for (JavaFileObject file : sourceFiles) {
                System.out.println("--- " + file.getName() + " ---");
                System.out.println(file.getCharContent(false));
            }
            throw t;
        }

        CompilationClassLoader classLoader = new CompilationClassLoader(loader, compilation);

        return classLoader.forceLoadClass(mainClassName);
    }

    public static Class<?> compile(String className, String source) throws Exception {
        return compile(className, List.of(JavaFileObjects.forSourceString(className, source)));
    }

    public static Class<?> compile(String outerClassName, JavaFileObject source) throws Exception {
        return compile(outerClassName, source.getCharContent(false).toString());
    }

    public static Object getDefaultInstance(Class<?> message) {
        try {
            Method getDefaultInstance = message.getMethod("getDefaultInstance");
            return getDefaultInstance.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object getRandomInstance(Class<?> messageClass) {
        try {
            Message defaultInstance = (Message) getDefaultInstance(messageClass);
            Descriptors.Descriptor descriptor = defaultInstance.getDescriptorForType();
            Message.Builder builder = com.google.protobuf.DynamicMessage.newBuilder(descriptor);
            Random random = new Random(messageClass.getName().hashCode());
            Message dynamicMessage = getRandomInstance(builder, 0, random);
            return parseFrom(messageClass, dynamicMessage.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Message getRandomInstance(Message.Builder builder, int depth, Random random) {
        if (depth <= 5) {
            builder.getDescriptorForType().getFields().forEach(field -> setRandomValue(field, builder, depth, random));
            return builder.build();
        } else {
            return builder.getDefaultInstanceForType();
        }
    }

    private static void setRandomValue(Descriptors.FieldDescriptor field, Message.Builder builder, int depth, Random random) {
        if (field.isMapField()) {
            if (depth >= 4) return;
            int count = random.nextInt(3) + 1;
            List<Message> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Message.Builder entryBuilder = builder.newBuilderForField(field);
                // Ensure map entries ALWAYS have both key and value set
                entryBuilder.getDescriptorForType().getFields().forEach(f -> setRandomValue(f, entryBuilder, depth + 1, random));
                entries.add(entryBuilder.build());
            }
            // Sort map entries by key (field number 1) to ensure deterministic order
            entries.sort((e1, e2) -> {
                Comparable k1 = (Comparable) e1.getField(e1.getDescriptorForType().findFieldByNumber(1));
                Comparable k2 = (Comparable) e2.getField(e2.getDescriptorForType().findFieldByNumber(1));
                return k1.compareTo(k2);
            });
            for (Message entry : entries) {
                builder.addRepeatedField(field, entry);
            }
        } else if (field.isRepeated()) {
            if (depth >= 4) return;
            int count = random.nextInt(3) + 1;
            for (int i = 0; i < count; i++) {
                Object value = getRandomValue(field, builder, random, depth);
                if (value != null) {
                    builder.addRepeatedField(field, value);
                }
            }
        } else {
            if (depth >= 4 && field.getJavaType() == Descriptors.FieldDescriptor.JavaType.MESSAGE) return;
            Object value = getRandomValue(field, builder, random, depth);
            if (value != null) {
                builder.setField(field, value);
            }
        }
    }

    private static Object getRandomValue(Descriptors.FieldDescriptor field, Message.Builder parentBuilder, Random random, int depth) {
        switch (field.getJavaType()) {
            case INT: return random.nextInt();
            case LONG: return random.nextLong();
            case FLOAT: return random.nextFloat();
            case DOUBLE: return random.nextDouble();
            case BOOLEAN: return random.nextBoolean();
            case STRING: return "str-" + Integer.toHexString(random.nextInt());
            case BYTE_STRING:
                byte[] bytes = new byte[8];
                random.nextBytes(bytes);
                return ByteString.copyFrom(bytes);
            case ENUM:
                List<Descriptors.EnumValueDescriptor> values = field.getEnumType().getValues();
                return values.get(random.nextInt(values.size()));
            case MESSAGE:
                Message.Builder nestedBuilder = parentBuilder.newBuilderForField(field);
                return getRandomInstance(nestedBuilder, depth + 1, random);
            default:
                throw new RuntimeException("Unsupported type: " + field.getJavaType());
        }
    }

    public static Object getBuilder(Class<?> message) {
        try {
            Method newBuilder = message.getMethod("newBuilder");
            return newBuilder.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toByteArray(Object object) {
        try {
            Method toByteArray = object.getClass().getMethod("toByteArray");
            return (byte[]) toByteArray.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object parseFrom(Class<?> message, byte[] expectedBytes) {
        try {
            Method parseFrom = message.getMethod("parseFrom", byte[].class);
            return parseFrom.invoke(null, (Object) expectedBytes);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

    }

    public static Class<?> getOrBuilderInterface(Class<?> messageClass) {
        String orBuilderName = messageClass.getSimpleName() + "OrBuilder";
        return findInnerClass(messageClass.getDeclaringClass(), orBuilderName)
                .orElseThrow(() -> new RuntimeException("Could not find OrBuilder interface " + orBuilderName + " for " + messageClass.getName()));
    }

    public static Optional<Class<?>> findInnerClass(Class<?> outer, String name) {
        if (outer == null) {
            return Optional.empty();
        }
        return Arrays.stream(outer.getDeclaredClasses())
                .filter(c -> c.getSimpleName().equals(name))
                .findFirst();
    }

    public static Class<?> getImplBase(Class<?> grpcClass) {
        return Arrays.stream(grpcClass.getDeclaredClasses())
                .filter(c -> c.getSimpleName().endsWith("ImplBase"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find ImplBase in " + grpcClass.getName()));
    }

    public static Method getServiceMethod(Class<?> serviceClass, String rpcName) {
        String javaMethodName = Character.toLowerCase(rpcName.charAt(0)) + rpcName.substring(1);
        return Arrays.stream(serviceClass.getMethods())
                .filter(m -> m.getName().equals(javaMethodName))
                .findFirst()
                .orElseGet(() -> Arrays.stream(serviceClass.getMethods())
                        .filter(m -> m.getName().equals(rpcName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Method not found: " + javaMethodName + " or " + rpcName + " in " + serviceClass.getName())));
    }


    public static ServerServiceDefinition getServiceDefinition(ServiceDescriptor serviceDescriptor, MethodDescriptor<Object, Object> methodDescriptor, Object defaultResponse) {
        return ServerServiceDefinition.builder(serviceDescriptor)
                .addMethod(methodDescriptor, ServerCalls.asyncUnaryCall((req, observer) -> {
                    observer.onNext(defaultResponse);
                    observer.onCompleted();
                }))
                .build();
    }

    private static class CompilationClassLoader extends ClassLoader {
        private final Compilation compilation;
        private final Map<String,Class<?>> cache = new HashMap<>();

        public CompilationClassLoader(ClassLoader parent, Compilation compilation) {
            super(parent);
            this.compilation = compilation;
        }


        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        c = super.loadClass(name, resolve);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";
            for (JavaFileObject file : compilation.generatedFiles()) {
                if (file.getKind() == JavaFileObject.Kind.CLASS && file.getName().endsWith(path)) {
                    try (InputStream in = file.openInputStream()) {
                        byte[] bytes = in.readAllBytes();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
            }
            return super.findClass(name);
        }

        protected Class<?> forceLoadClass(String name) throws ClassNotFoundException {
            return cache.computeIfAbsent(name, ignored -> {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
        }

    }

    public static Iterable<File> extractClasspath(ClassLoader classLoader) {
        List<File> files = new ArrayList<>();
        for (ClassLoader cl = classLoader; cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    try {
                        files.add(new File(url.toURI()));
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (files.isEmpty()) {
            return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                    .filter(path -> !path.isEmpty())
                    .map(File::new)
                    .toList();
        }

        return files;
    }

    public static class MockFiler implements Filer {
        private final Map<String, StringWriter> sources = new HashMap<>();

        @Override
        public JavaFileObject createSourceFile(CharSequence name, javax.lang.model.element.Element... originatingElements) throws IOException {
            StringWriter writer = new StringWriter();
            sources.put(name.toString(), writer);
            return new MockJavaFileObject(name.toString(), writer);
        }

        @Override
        public JavaFileObject createClassFile(CharSequence name, javax.lang.model.element.Element... originatingElements) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject createResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, javax.lang.model.element.Element... originatingElements) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
            throw new UnsupportedOperationException();
        }

        public ImmutableMap<String, String> getSources() {
            return sources.entrySet().stream()
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> e.getValue().toString()));
        }
    }

    private static class MockJavaFileObject extends SimpleJavaFileObject {

        private final StringWriter writer;

        public MockJavaFileObject(String name, StringWriter writer) {
            super(URI.create("mock:///" + name), Kind.SOURCE);
            this.writer = writer;
        }

        @Override
        public Writer openWriter() throws IOException {
            return writer;
        }
    }

    public static class MessageAssert extends AbstractAssert<MessageAssert, Object> {

        protected MessageAssert(Object actual) {
            super(actual, MessageAssert.class);
        }

        public static MessageAssert assertThatMessage(Object actual) {
            return new MessageAssert(actual);
        }
    }


}