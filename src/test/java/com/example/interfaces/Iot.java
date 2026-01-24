package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.Map;

@GenProto(value = "iot.proto", pkg = "com.example.proto", outerClass = true)
public class Iot {

    public interface Device {
        @Field(1)
        String id();
        @Field(2)
        String model();
        @Field(3)
        Connectivity connectivity();
        @Field(4)
        State state();

        enum Connectivity {
            WIFI, BLUETOOTH, CELLULAR, ZIGBEE
        }

        interface State {
            @Field(1)
            Map<String, String> shadow();
            @Field(2)
            long last_seen();
            @Field(3)
            Connectivity conn();
        }
    }

    public interface Command {
        @Field(1)
        String target_device_id();

        @Field(value = 2, oneof = "payload")
        String action();

        @Field(value = 3, oneof = "payload")
        int reboot_delay();

        @Field(value = 4, oneof = "payload")
        byte[] raw_data();
    }
}
