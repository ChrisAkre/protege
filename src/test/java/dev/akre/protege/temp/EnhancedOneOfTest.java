package dev.akre.protege.temp;

import com.google.protobuf.DescriptorProtos;
import dev.akre.protege.ProtegeVersion;
import dev.akre.protege.codegen.ProtoCodegen;
import dev.akre.protege.ProtoUtils;
import dev.akre.protege.testutil.ClassAssert;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

public class EnhancedOneOfTest {

    // current protobuf compiler returns an enum to indicate which oneof field in present.

    // instead we should create a sealed interface which will be easier to use with enhanced switch syntax


    public static final String TEST_PROTO = """
            syntax = "proto3";
            package com.example.proto;
            option java_package = "com.example.test";
            option (dev.akre.protege.java_enhanced_oneof) = true;
            option (dev.akre.protege.java_oneof_case) = false;
            
            message MediaItem {
              string id = 1;
              string title = 2;
            
              oneof content {
                Video video = 3;
                Audio audio = 4;
                Image image = 5;
              }
            
              message Video {
                int32 duration_seconds = 1;
                string codec = 2;
                Resolution resolution = 3;
              }
            
              message Audio {
                int32 duration_seconds = 1;
                int32 bitrate = 2;
              }
            
              message Image {
                int32 width = 1;
                int32 height = 2;
                string format = 3;
              }
            
              message Resolution {
                int32 width = 1;
                int32 height = 2;
              }
            }
            """;

    @Test
    public void testEnhancedOneof() throws Exception {
        String protoPath = "oneof.proto";
        DescriptorProtos.FileDescriptorProto parsedProto = ProtoUtils.parseProto(TEST_PROTO, protoPath);
        String outerClassName = TestUtils.makeOuterClassName(parsedProto, protoPath.toString());
        ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler());
        Class<?> generatedClass = TestUtils.compile(outerClassName, codegen.generateFile(parsedProto).toJavaFileObject());
        ClassAssert.assertThat(generatedClass)
                .hasPublicStaticFinalStringField("PROTEGE_VERSION",ProtegeVersion.VERSION_STRING);



        Class<?> mediaItem = TestUtils.findInnerClass(generatedClass, "MediaItem").orElseThrow();
        Class<?> oneofInterface = TestUtils.findInnerClass(mediaItem, "Content").orElseThrow();

        // test that this is sealed
        ClassAssert.assertThat(oneofInterface).isInterface().isSealed();

        Class<?> video = TestUtils.findInnerClass(mediaItem, "Video").orElseThrow();
        Class<?> audio = TestUtils.findInnerClass(mediaItem, "Audio").orElseThrow();
        Class<?> image = TestUtils.findInnerClass(mediaItem, "Image").orElseThrow();

        Class<?> videoOrBuilder = TestUtils.findInnerClass(mediaItem, "VideoOrBuilder").orElseThrow();
        Class<?> audioOrBuilder = TestUtils.findInnerClass(mediaItem, "AudioOrBuilder").orElseThrow();
        Class<?> imageOrBuilder = TestUtils.findInnerClass(mediaItem, "ImageOrBuilder").orElseThrow();

        // Requirement 1: OrBuilder interfaces are non-sealed
        ClassAssert.assertThat(videoOrBuilder).isInterface().isNonSealed();
        ClassAssert.assertThat(audioOrBuilder).isInterface().isNonSealed();
        ClassAssert.assertThat(imageOrBuilder).isInterface().isNonSealed();

        // Requirement 2: oneof interface permits the associated OrBuilder interfaces
        ClassAssert.assertThat(oneofInterface).permits(videoOrBuilder, audioOrBuilder, imageOrBuilder);

        // test that member messages implement the interface (via OrBuilder)
        ClassAssert.assertThat(oneofInterface).isAssignableFrom(video).isAssignableFrom(audio).isAssignableFrom(image);

        // assert that MediaItem has getContent method returning Content
        ClassAssert.assertThat(mediaItem).hasMethod("getContent", oneofInterface);

        // assert there is no getContentCase method as we set java_generate_oneof_case = false
        ClassAssert.assertThat(mediaItem).hasNoMethod("getContentCase");

        // Also check the Builder
        Class<?> builder = TestUtils.findInnerClass(mediaItem, "Builder").orElseThrow();
        ClassAssert.assertThat(builder).hasMethod("getContent", oneofInterface);
        ClassAssert.assertThat(builder).hasNoMethod("getContentCase");

        // Test mergeFrom
        var videoMsg = mediaItem.getMethod("newBuilder").invoke(null);
        var videoBuilder = video.getMethod("newBuilder").invoke(null);
        videoBuilder.getClass().getMethod("setDurationSeconds", int.class).invoke(videoBuilder, 120);
        videoMsg.getClass().getMethod("setVideo", video).invoke(videoMsg, videoBuilder.getClass().getMethod("build").invoke(videoBuilder));
        var builtVideoMsg = videoMsg.getClass().getMethod("build").invoke(videoMsg);

        var mergeBuilder = mediaItem.getMethod("newBuilder").invoke(null);
        mergeBuilder.getClass().getMethod("mergeFrom", mediaItem).invoke(mergeBuilder, builtVideoMsg);
        var mergedMsg = mergeBuilder.getClass().getMethod("build").invoke(mergeBuilder);

        boolean hasVideo = (boolean) mergedMsg.getClass().getMethod("hasVideo").invoke(mergedMsg);
        org.junit.jupiter.api.Assertions.assertTrue(hasVideo);
    }

    @Test
    public void testEnhancedOneofWithPrimitiveThrows() throws Exception {
        String proto = """
                syntax = "proto3";
                package com.example.proto;
                option (dev.akre.protege.java_enhanced_oneof) = true;
                
                message MyMessage {
                  oneof content {
                    string text = 1;
                    int32 number = 2;
                  }
                }
                """;
        DescriptorProtos.FileDescriptorProto parsedProto = ProtoUtils.parseProto(proto, "test.proto");
        ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler());
        
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            codegen.generateFile(parsedProto);
        });
    }

    @Test
    public void testEnhancedOneofWithCrossFileMessageThrows() throws Exception {
        DescriptorProtos.DescriptorProto message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("MyMessage")
                .addOneofDecl(DescriptorProtos.OneofDescriptorProto.newBuilder().setName("content"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("other")
                        .setNumber(1)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".other.package.OtherMessage")
                        .setOneofIndex(0))
                .build();

        DescriptorProtos.FileDescriptorProto fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("com.example.proto")
                .addMessageType(message)
                .setOptions(DescriptorProtos.FileOptions.newBuilder()
                        .addUninterpretedOption(ProtoUtils.createUninterpretedOption("dev.akre.protege.java_enhanced_oneof", "true")))
                .build();

        ProtoCodegen codegen = new ProtoCodegen(new TestUtils.MockFiler());
        
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            codegen.generateFile(fileProto);
        });
    }
}
