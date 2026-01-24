package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.List;

@GenProto(value = "addressbook.proto", pkg="com.example.proto")
public interface AddressBook {
    @Field(1)
    List<Person> people();

    interface Person {
        @Field(1)
        String name();
        @Field(2)
        int id();
        @Field(3)
        String email();
        @Field(4)
        List<PhoneNumber> phones();

        enum PhoneType {
            MOBILE, HOME, WORK
        }

        interface PhoneNumber {
            @Field(1)
            String number();
            @Field(2)
            PhoneType type();
        }
    }
}
