package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.List;

@GenProto(value = "sensordata.proto", pkg = "com.example.proto", outerClass = true)
public class SensorData {

    public interface SensorReading {
        @Field(1)
        String sensor_id();
        @Field(2)
        long timestamp();

        @Field(value = 3, oneof = "value")
        double temperature();
        @Field(value = 4, oneof = "value")
        double humidity();
        @Field(value = 5, oneof = "value")
        boolean status();
        @Field(value = 6, oneof = "value")
        String error_message();
    }

    public interface BatchSensorReading {
        @Field(1)
        List<SensorReading> readings();
    }
}
