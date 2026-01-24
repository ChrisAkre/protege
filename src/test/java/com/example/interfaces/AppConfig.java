package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.Map;

@GenProto(value = "config.proto", pkg = "com.example.proto")
public interface AppConfig {
    @Field(1)
    String environment();
    @Field(2)
    Database db();
    @Field(3)
    Logging logging();
    @Field(4)
    Map<String, String> features();

    interface Database {
        @Field(1)
        String host();
        @Field(2)
        int port();
        @Field(3)
        Map<String, String> options();
    }

    interface Logging {
        @Field(1)
        Level level();
        @Field(2)
        String output_path();

        enum Level {
            INFO, DEBUG, WARN, ERROR
        }
    }
}
