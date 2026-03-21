package com.example.interfaces;

import dev.akre.protege.annotation.Field;
import dev.akre.protege.annotation.GenProto;
import jdk.jfr.Enabled;

import javax.annotation.Resource;
import java.beans.BeanProperty;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@GenProto(value = "options.proto", pkg = "dev.akre.protege")
public interface MyMessage {
    @Resource
    @Field(1)
    String getId();

    @BeanProperty(description = "foo")
    @Field(2)
    String getName();
}
