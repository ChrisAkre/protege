package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

@GenProto(value = "person.proto", pkg = "com.example.proto")
public interface Person {
    @Field(1)
    String name();
    @Field(2)
    int id();
}
