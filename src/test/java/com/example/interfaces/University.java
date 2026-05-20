package com.example.interfaces;

import dev.akre.protege.annotation.GenProto;
import dev.akre.protege.annotation.Field;

import java.util.List;
import java.util.Map;

@GenProto(value = "university.proto", pkg = "com.example.proto", outerClass = true)
public class University {

    public interface Course {
        @Field(1)
        String code();
        @Field(2)
        String title();
        @Field(3)
        int credits();

        interface Grade {
            @Field(1)
            float value();
        }
    }

    public interface Student {
        @Field(1)
        String id();
        @Field(2)
        String name();
        @Field(3)
        Map<String, Course.Grade> grades();
    }

    public interface Department {
        @Field(1)
        String name();
        @Field(2)
        List<Course> courses();
        @Field(3)
        List<Student> students();
        @Field(4)
        Map<String, Department> sub_departments();
    }
}
