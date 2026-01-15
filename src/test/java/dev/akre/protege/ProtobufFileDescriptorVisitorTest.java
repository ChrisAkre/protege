package dev.akre.protege;

import com.google.protobuf.DescriptorProtos.*;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProtobufFileDescriptorVisitorTest {

    @Test
    void shouldParseBasicProtoMessage() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            package example;
            
            message Person {
                string name = 1;
                int32 id = 2;
                string email = 3;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        assertThat(fileDescriptor.getSyntax()).isEqualTo("proto3");
        assertThat(fileDescriptor.getPackage()).isEqualTo("example");
        assertThat(fileDescriptor.getMessageTypeCount()).isEqualTo(1);

        DescriptorProto message = fileDescriptor.getMessageType(0);
        assertThat(message.getName()).isEqualTo("Person");
        assertThat(message.getFieldCount()).isEqualTo(3);

        // Validate fields
        assertThat(message.getField(0))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("name");
                    assertThat(field.getNumber()).isEqualTo(1);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
                });

        assertThat(message.getField(1))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("id");
                    assertThat(field.getNumber()).isEqualTo(2);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
                });

        assertThat(message.getField(2))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("email");
                    assertThat(field.getNumber()).isEqualTo(3);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
                });
    }

    @Test
    void shouldParseComplexProtoWithNestedMessages() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            package company;
            
            message Employee {
                string name = 1;
                int32 employee_id = 2;
                repeated string phone_numbers = 3;
                
                message Address {
                    string street = 1;
                    string city = 2;
                    string country = 3;
                }
                
                Address home_address = 4;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        assertThat(fileDescriptor.getMessageTypeCount()).isEqualTo(1);

        DescriptorProto employee = fileDescriptor.getMessageType(0);
        assertThat(employee.getName()).isEqualTo("Employee");
        assertThat(employee.getFieldCount()).isEqualTo(4);

        // Check nested message
        assertThat(employee.getNestedTypeCount()).isEqualTo(1);
        DescriptorProto address = employee.getNestedType(0);
        assertThat(address.getName()).isEqualTo("Address");
        assertThat(address.getFieldCount()).isEqualTo(3);

        // Validate repeated field
        assertThat(employee.getField(2))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("phone_numbers");
                    assertThat(field.getNumber()).isEqualTo(3);
                    assertThat(field.getLabel()).isEqualTo(FieldDescriptorProto.Label.LABEL_REPEATED);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
                });

        // Validate message type field
        assertThat(employee.getField(3))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("home_address");
                    assertThat(field.getNumber()).isEqualTo(4);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
                    assertThat(field.getTypeName()).isEqualTo("Address");
                });
    }

    @Test
    void shouldParseProtoWithEnums() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            package test;
            
            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1;
                INACTIVE = 2;
                DELETED = 3;
            }
            
            message User {
                string username = 1;
                Status status = 2;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        assertThat(fileDescriptor.getEnumTypeCount()).isEqualTo(1);

        EnumDescriptorProto statusEnum = fileDescriptor.getEnumType(0);
        assertThat(statusEnum.getName()).isEqualTo("Status");
        assertThat(statusEnum.getValueCount()).isEqualTo(4);

        assertThat(statusEnum.getValue(0))
                .satisfies(value -> {
                    assertThat(value.getName()).isEqualTo("UNKNOWN");
                    assertThat(value.getNumber()).isEqualTo(0);
                });

        assertThat(statusEnum.getValue(3))
                .satisfies(value -> {
                    assertThat(value.getName()).isEqualTo("DELETED");
                    assertThat(value.getNumber()).isEqualTo(3);
                });

        // Validate message with enum field
        DescriptorProto user = fileDescriptor.getMessageType(0);
        assertThat(user.getField(1))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("status");
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
                    assertThat(field.getTypeName()).isEqualTo("Status");
                });
    }

    @Test
    void shouldParseProtoWithOneof() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            message Payment {
                string transaction_id = 1;
                
                oneof payment_method {
                    string credit_card = 2;
                    string paypal_email = 3;
                    string crypto_address = 4;
                }
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        DescriptorProto payment = fileDescriptor.getMessageType(0);
        assertThat(payment.getName()).isEqualTo("Payment");
        assertThat(payment.getFieldCount()).isEqualTo(4);
        assertThat(payment.getOneofDeclCount()).isEqualTo(1);

        OneofDescriptorProto oneof = payment.getOneofDecl(0);
        assertThat(oneof.getName()).isEqualTo("payment_method");

        // Validate oneof fields
        assertThat(payment.getField(1))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("credit_card");
                    assertThat(field.getNumber()).isEqualTo(2);
                    assertThat(field.hasOneofIndex()).isTrue();
                    assertThat(field.getOneofIndex()).isEqualTo(0);
                });

        assertThat(payment.getField(2))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("paypal_email");
                    assertThat(field.getOneofIndex()).isEqualTo(0);
                });
    }

    @Test
    void shouldParseProtoWithService() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            message GetUserRequest {
                string user_id = 1;
            }
            
            message GetUserResponse {
                string name = 1;
                string email = 2;
            }
            
            service UserService {
                rpc GetUser(GetUserRequest) returns (GetUserResponse);
                rpc StreamUsers(GetUserRequest) returns (stream GetUserResponse);
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        assertThat(fileDescriptor.getServiceCount()).isEqualTo(1);

        ServiceDescriptorProto service = fileDescriptor.getService(0);
        assertThat(service.getName()).isEqualTo("UserService");
        assertThat(service.getMethodCount()).isEqualTo(2);

        // Validate first RPC method
        assertThat(service.getMethod(0))
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("GetUser");
                    assertThat(method.getInputType()).isEqualTo("GetUserRequest");
                    assertThat(method.getOutputType()).isEqualTo("GetUserResponse");
                    assertThat(method.getClientStreaming()).isFalse();
                    assertThat(method.getServerStreaming()).isFalse();
                });

        // Validate streaming RPC method
        assertThat(service.getMethod(1))
                .satisfies(method -> {
                    assertThat(method.getName()).isEqualTo("StreamUsers");
                    assertThat(method.getServerStreaming()).isTrue();
                    assertThat(method.getClientStreaming()).isFalse();
                });
    }

    @Test
    void shouldParseProtoWithImports() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            import "google/protobuf/timestamp.proto";
            import public "common/types.proto";
            
            package test;
            
            message Event {
                string name = 1;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        assertThat(fileDescriptor.getDependencyCount()).isEqualTo(2);
        assertThat(fileDescriptor.getDependency(0)).isEqualTo("google/protobuf/timestamp.proto");
        assertThat(fileDescriptor.getDependency(1)).isEqualTo("common/types.proto");

        assertThat(fileDescriptor.getPublicDependencyCount()).isEqualTo(1);
        assertThat(fileDescriptor.getPublicDependency(0)).isEqualTo(1);
    }

    @Test
    void shouldParseProtoWithMapFields() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            message Configuration {
                map<string, string> settings = 1;
                map<int32, string> error_codes = 2;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        DescriptorProto config = fileDescriptor.getMessageType(0);
        assertThat(config.getFieldCount()).isEqualTo(2);

        // Map fields are represented as repeated message fields
        assertThat(config.getField(0))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("settings");
                    assertThat(field.getLabel()).isEqualTo(FieldDescriptorProto.Label.LABEL_REPEATED);
                    assertThat(field.getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_MESSAGE);
                    assertThat(field.getTypeName()).isEqualTo("SettingsEntry");
                });

        assertThat(config.getField(1))
                .satisfies(field -> {
                    assertThat(field.getName()).isEqualTo("error_codes");
                    assertThat(field.getTypeName()).isEqualTo("Error_codesEntry");
                });
    }

    @Test
    void shouldParseProtoWithAllFieldTypes() {
        // given
        String protoDefinition = """
            syntax = "proto3";
            
            message AllTypes {
                double double_field = 1;
                float float_field = 2;
                int32 int32_field = 3;
                int64 int64_field = 4;
                uint32 uint32_field = 5;
                uint64 uint64_field = 6;
                sint32 sint32_field = 7;
                sint64 sint64_field = 8;
                fixed32 fixed32_field = 9;
                fixed64 fixed64_field = 10;
                sfixed32 sfixed32_field = 11;
                sfixed64 sfixed64_field = 12;
                bool bool_field = 13;
                string string_field = 14;
                bytes bytes_field = 15;
            }
            """;

        // when
        FileDescriptorProto.Builder fileDescriptor = parseProto(protoDefinition);

        // then
        DescriptorProto message = fileDescriptor.getMessageType(0);
        assertThat(message.getFieldCount()).isEqualTo(15);

        assertThat(message.getField(0).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_DOUBLE);
        assertThat(message.getField(1).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_FLOAT);
        assertThat(message.getField(2).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT32);
        assertThat(message.getField(3).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_INT64);
        assertThat(message.getField(4).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_UINT32);
        assertThat(message.getField(5).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_UINT64);
        assertThat(message.getField(6).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_SINT32);
        assertThat(message.getField(7).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_SINT64);
        assertThat(message.getField(8).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_FIXED32);
        assertThat(message.getField(9).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_FIXED64);
        assertThat(message.getField(10).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_SFIXED32);
        assertThat(message.getField(11).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_SFIXED64);
        assertThat(message.getField(12).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_BOOL);
        assertThat(message.getField(13).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_STRING);
        assertThat(message.getField(14).getType()).isEqualTo(FieldDescriptorProto.Type.TYPE_BYTES);
    }

    private FileDescriptorProto.Builder parseProto(String protoContent) {
        CharStream input = CharStreams.fromString(protoContent);
        ProtobufLexer lexer = new ProtobufLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ProtobufParser parser = new ProtobufParser(tokens);

        ProtobufParser.ProtoContext tree = parser.proto();

        ProtobufFileDescriptorVisitor visitor = new ProtobufFileDescriptorVisitor();
        return visitor.visitProto(tree);
    }
}