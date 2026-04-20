package dev.akre.protege.temp;

import dev.akre.protege.CodegenMetadata;
import dev.akre.protege.ProtoUtils;
import dev.akre.protege.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CanaryTest {

    public static final String TEST_PROTO = """
            syntax = "proto3";
            package com.example;
            
            message Canary {
              string name = 1;
            }
            """;

    @Test
    public void testCompileOutput() throws Exception {
        var parsedProto = ProtoUtils.parseProto(TEST_PROTO, "canary.proto");
        var config = CodegenMetadata.build(parsedProto).build();
        String output = config.generate(new TestUtils.MockFiler()).toJavaFileObject().getCharContent(false).toString();
        assertThat(output).isEqualTo("""
                package com.example;
                
                import com.google.protobuf.AbstractParser;
                import com.google.protobuf.ByteString;
                import com.google.protobuf.CodedInputStream;
                import com.google.protobuf.CodedOutputStream;
                import com.google.protobuf.Descriptors;
                import com.google.protobuf.ExtensionRegistryLite;
                import com.google.protobuf.GeneratedMessage;
                import com.google.protobuf.InvalidProtocolBufferException;
                import com.google.protobuf.MapFieldReflectionAccessor;
                import com.google.protobuf.Message;
                import com.google.protobuf.MessageOrBuilder;
                import com.google.protobuf.Parser;
                import com.google.protobuf.UnknownFieldSet;
                import dev.akre.protege.ProtoUtils;
                import java.io.IOException;
                import java.io.InputStream;
                import java.lang.Object;
                import java.lang.Override;
                import java.lang.RuntimeException;
                import java.lang.String;
                import java.nio.ByteBuffer;
                
                public final class CanaryOuterClass {
                  public static final String PROTEGE_VERSION = "0.0.1-SNAPSHOT";
                
                  private static final Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(new String[] {
                    "\\n",
                    "\\014canary.proto\\022\\013com.example\\"\\026\\n",
                    "\\006Canary\\022\\014\\n",
                    "\\004name\\030\\001 \\001(\\tB\\000b\\006proto3"
                  }, new Descriptors.FileDescriptor[0]);
                
                  public static Descriptors.FileDescriptor getDescriptor() {
                    return fileDescriptor;
                  }
                
                  public interface CanaryOrBuilder extends MessageOrBuilder {
                    String getName();
                
                    ByteString getNameBytes();
                  }
                
                  public static final class Canary extends GeneratedMessage implements CanaryOuterClass.CanaryOrBuilder {
                    private static Descriptors.Descriptor descriptor;
                
                    private static final CanaryOuterClass.Canary DEFAULT_INSTANCE;
                
                    private static GeneratedMessage.FieldAccessorTable internal_fieldAccessorTable;
                
                    public static final Parser<CanaryOuterClass.Canary> PARSER = new AbstractParser<CanaryOuterClass.Canary>() {
                      @Override
                      public CanaryOuterClass.Canary parsePartialFrom(CodedInputStream input,
                          ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                        try {
                          return new CanaryOuterClass.Canary(input, extensionRegistry);
                        } catch (IOException e) {
                          throw new InvalidProtocolBufferException(e).setUnfinishedMessage(getDefaultInstance());
                        }
                      }
                    };
                
                    static {
                      descriptor = CanaryOuterClass.getDescriptor().findMessageTypeByName("Canary");
                    }
                    static {
                      DEFAULT_INSTANCE = new CanaryOuterClass.Canary();
                    }
                    static {
                      internal_fieldAccessorTable = new GeneratedMessage.FieldAccessorTable(getDescriptor(), new String[] { "Name" });
                    }
                
                    private Object name_;
                
                    private int memoizedSize = -1;
                
                    private Canary() {
                      name_ = "";
                    }
                
                    private Canary(CanaryOuterClass.Canary.Builder builder) {
                      super(builder);
                      this.name_ = builder.name_;
                    }
                
                    private Canary(CodedInputStream input, ExtensionRegistryLite extensionRegistry) throws
                        IOException {
                      this();
                      try {
                        boolean done = false;
                        while (!done) {
                          int tag = input.readTag();
                          switch (tag) {
                            case 0:
                              done = true;
                              break;
                            case 10: {
                              name_ = input.readStringRequireUtf8();
                              break;
                            }
                            default: {
                              if (!input.skipField(tag)) { done = true; };
                              break;
                            }
                          }
                        }
                      } catch (InvalidProtocolBufferException e) {
                        throw e.setUnfinishedMessage(this);
                      } catch (IOException e) {
                        throw new InvalidProtocolBufferException(e).setUnfinishedMessage(this);
                      } finally {
                      }
                    }
                
                    public static final Descriptors.Descriptor getDescriptor() {
                      return descriptor;
                    }
                
                    public static CanaryOuterClass.Canary getDefaultInstance() {
                      return DEFAULT_INSTANCE;
                    }
                
                    @Override
                    protected GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
                      return internal_fieldAccessorTable.ensureFieldAccessorsInitialized(CanaryOuterClass.Canary.class, CanaryOuterClass.Canary.Builder.class);
                    }
                
                    @Override
                    public String getName() {
                      java.lang.Object ref = name_;
                      if (ref instanceof String) { return (String) ref; };
                      ByteString bs = (ByteString) ref;
                      String s = bs.toStringUtf8();
                      name_ = s;
                      return s;
                    }
                
                    @Override
                    public ByteString getNameBytes() {
                      java.lang.Object ref = name_;
                      if (ref instanceof String) {
                        ByteString b = ByteString.copyFromUtf8((String) ref);
                        name_ = b;
                        return b;
                      } else {
                        return (ByteString) ref;
                      }
                    }
                
                    @Override
                    protected MapFieldReflectionAccessor internalGetMapFieldReflection(int fieldNumber) {
                      switch (fieldNumber) {
                        default: throw new RuntimeException("Invalid map field number: " + fieldNumber);
                      }
                    }
                
                    @Override
                    public void writeTo(CodedOutputStream output) throws IOException {
                      if (!ProtoUtils.isStringEmpty((java.lang.Object)name_)) {
                        GeneratedMessage.writeString(output, 1, name_);
                      }
                    }
                
                    @Override
                    public int getSerializedSize() {
                      int size = memoizedSize;
                      if (size != -1) {
                        return size;
                      }
                      size = 0;
                      if (!ProtoUtils.isStringEmpty((java.lang.Object)name_)) {
                        size += GeneratedMessage.computeStringSize(1, name_);
                      }
                      memoizedSize = size;
                      return size;
                    }
                
                    @Override
                    public CanaryOuterClass.Canary.Builder toBuilder() {
                      return this == DEFAULT_INSTANCE ? new CanaryOuterClass.Canary.Builder() : new CanaryOuterClass.Canary.Builder().mergeFrom(this);
                    }
                
                    @Override
                    public Parser getParserForType() {
                      return PARSER;
                    }
                
                    @Override
                    public CanaryOuterClass.Canary.Builder newBuilderForType() {
                      return newBuilder();
                    }
                
                    @Override
                    protected CanaryOuterClass.Canary.Builder newBuilderForType(
                        GeneratedMessage.BuilderParent parent) {
                      return new CanaryOuterClass.Canary.Builder(parent);
                    }
                
                    @Override
                    public CanaryOuterClass.Canary getDefaultInstanceForType() {
                      return DEFAULT_INSTANCE;
                    }
                
                    @Override
                    public UnknownFieldSet getUnknownFields() {
                      return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
                    }
                
                    @Override
                    public final boolean isInitialized() {
                      return true;
                    }
                
                    public static CanaryOuterClass.Canary.Builder newBuilder() {
                      return DEFAULT_INSTANCE.toBuilder();
                    }
                
                    public static CanaryOuterClass.Canary.Builder newBuilder(CanaryOuterClass.Canary prototype) {
                      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
                    }
                
                    public static Parser<CanaryOuterClass.Canary> parser() {
                      return PARSER;
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(ByteBuffer data) throws
                        InvalidProtocolBufferException {
                      return PARSER.parseFrom(data);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(ByteBuffer data,
                        ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                      return PARSER.parseFrom(data, extensionRegistry);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(ByteString data) throws
                        InvalidProtocolBufferException {
                      return PARSER.parseFrom(data);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(ByteString data,
                        ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                      return PARSER.parseFrom(data, extensionRegistry);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(byte[] data) throws
                        InvalidProtocolBufferException {
                      return PARSER.parseFrom(data);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(byte[] data,
                        ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                      return PARSER.parseFrom(data, extensionRegistry);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(InputStream input) throws IOException {
                      return PARSER.parseFrom(input);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(InputStream input,
                        ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                      return PARSER.parseFrom(input, extensionRegistry);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(CodedInputStream input) throws IOException {
                      return PARSER.parseFrom(input);
                    }
                
                    public static CanaryOuterClass.Canary parseFrom(CodedInputStream input,
                        ExtensionRegistryLite extensionRegistry) throws InvalidProtocolBufferException {
                      return PARSER.parseFrom(input, extensionRegistry);
                    }
                
                    public static CanaryOuterClass.Canary parseDelimitedFrom(InputStream input) throws IOException {
                      return PARSER.parseDelimitedFrom(input);
                    }
                
                    public static CanaryOuterClass.Canary parseDelimitedFrom(InputStream input,
                        ExtensionRegistryLite extensionRegistry) throws IOException {
                      return PARSER.parseDelimitedFrom(input, extensionRegistry);
                    }
                
                    public static final class Builder extends GeneratedMessage.Builder<CanaryOuterClass.Canary.Builder> implements CanaryOuterClass.CanaryOrBuilder {
                      private Object name_ = "";
                
                      private Builder() {
                        super();
                      }
                
                      private Builder(GeneratedMessage.BuilderParent parent) {
                        super(parent);
                      }
                
                      @Override
                      public String getName() {
                        java.lang.Object ref = name_;
                        if (!(ref instanceof String)) {
                          ByteString bs = (ByteString) ref;
                          String s = bs.toStringUtf8();
                          name_ = s;
                          return s;
                        }
                        return (String) ref;
                      }
                
                      @Override
                      public ByteString getNameBytes() {
                        java.lang.Object ref = name_;
                        if (ref instanceof String) {
                          ByteString b = ByteString.copyFromUtf8((String) ref);
                          name_ = b;
                          return b;
                        } else {
                          return (ByteString) ref;
                        }
                      }
                
                      public CanaryOuterClass.Canary.Builder setNameBytes(ByteString value) {
                        if (value == null) { throw new NullPointerException(); };
                        this.name_ = value;
                        onChanged();
                        return this;
                      }
                
                      public CanaryOuterClass.Canary.Builder setName(String value) {
                        this.name_ = value;
                        onChanged();
                        return this;
                      }
                
                      public CanaryOuterClass.Canary.Builder clearName() {
                        this.name_ = "";
                        onChanged();
                        return this;
                      }
                
                      @Override
                      public CanaryOuterClass.Canary build() {
                        CanaryOuterClass.Canary result = buildPartial();
                        if (!result.isInitialized()) {
                          throw newUninitializedMessageException(result);
                        }
                        return result;
                      }
                
                      @Override
                      public CanaryOuterClass.Canary buildPartial() {
                        return new CanaryOuterClass.Canary(this);
                      }
                
                      @Override
                      public CanaryOuterClass.Canary.Builder clear() {
                        super.clear();
                        name_ = "";
                        return this;
                      }
                
                      @Override
                      public CanaryOuterClass.Canary getDefaultInstanceForType() {
                        return CanaryOuterClass.Canary.getDefaultInstance();
                      }
                
                      @Override
                      public Descriptors.Descriptor getDescriptorForType() {
                        return CanaryOuterClass.Canary.getDescriptor();
                      }
                
                      public static final Descriptors.Descriptor getDescriptor() {
                        return CanaryOuterClass.Canary.getDescriptor();
                      }
                
                      @Override
                      public final boolean isInitialized() {
                        return true;
                      }
                
                      @Override
                      public CanaryOuterClass.Canary.Builder mergeFrom(CodedInputStream input,
                          ExtensionRegistryLite extensionRegistry) throws IOException {
                        return (CanaryOuterClass.Canary.Builder) super.mergeFrom(input, extensionRegistry);
                      }
                
                      @Override
                      protected GeneratedMessage.FieldAccessorTable internalGetFieldAccessorTable() {
                        return CanaryOuterClass.Canary.internal_fieldAccessorTable.ensureFieldAccessorsInitialized(CanaryOuterClass.Canary.class, CanaryOuterClass.Canary.Builder.class);
                      }
                
                      @Override
                      protected MapFieldReflectionAccessor internalGetMapFieldReflection(int fieldNumber) {
                        switch (fieldNumber) {
                          default: throw new RuntimeException("Invalid map field number: " + fieldNumber);
                        }
                      }
                
                      @Override
                      protected MapFieldReflectionAccessor internalGetMutableMapFieldReflection(int fieldNumber) {
                        switch (fieldNumber) {
                          default: throw new RuntimeException("Invalid map field number: " + fieldNumber);
                        }
                      }
                
                      @Override
                      public CanaryOuterClass.Canary.Builder mergeFrom(Message other) {
                        if (other instanceof CanaryOuterClass.Canary) {
                          return mergeFrom((CanaryOuterClass.Canary) other);
                        } else {
                          super.mergeFrom(other);
                          return this;
                        }
                      }
                
                      public CanaryOuterClass.Canary.Builder mergeFrom(CanaryOuterClass.Canary other) {
                        if (other == CanaryOuterClass.Canary.getDefaultInstance()) {
                          return this;
                        }
                        if (!ProtoUtils.isStringEmpty((java.lang.Object)other.getName())) {
                          setName(other.getName());
                        }
                        onChanged();
                        return this;
                      }
                    }
                  }
                }
                """);
    }
}
