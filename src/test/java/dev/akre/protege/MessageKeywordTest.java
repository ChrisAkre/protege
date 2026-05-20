package dev.akre.protege;

import dev.akre.protege.ProtoUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MessageKeywordTest {
    @Test
    public void testMessageKeywordProto() throws Exception {
        String protoContent = """
                syntax = "proto2";
                
                package message_keyword;
                
                message optional {
                  string syntax = 1;
                  string import = 2;
                  repeated string weak = 3;
                  optional string public = 4;
                  string package = 5;
                  string option = 6;
                  repeated string oneof = 7;
                  optional string map = 8;
                  string reserved = 9;
                  string to = 10;
                  string max = 11;
                  optional string enum = 12;
                  string message = 13;
                  string service = 14;
                  repeated string extend = 15;
                  string extensions = 16;
                  string rpc = 17;
                  string returns = 18;
                  optional string stream = 19;
                  string inf = 20;
                  string nan = 21;
                  string true = 22;
                  string false = 23;
                  string repeated = 24;
                }
                
                message message {
                  optional string field = 1;
                }
                
                message repeated {
                  optional int32 val = 1;
                }
                
                message string {
                  optional float f1 = 1 [default = inf];
                  optional float f2 = 2 [default = -inf];
                  optional float f3 = 3 [default = nan];
                }
                
                message false {
                  optional message true = 1;
                  repeated repeated repeated = 2;
                }
                """;
        assertNotNull(ProtoUtils.parseProto(protoContent));
    }
}
