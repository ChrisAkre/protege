package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.Map;

@GenProto(value = "ecommerce.proto", pkg = "com.example.proto", outerClass = true)
public class Ecommerce {

    public interface Product {
        @Field(1)
        String id();
        @Field(2)
        String name();
        @Field(3)
        double price();
        @Field(4)
        Category category();

        enum Category {
            ELECTRONICS, CLOTHING, HOME, BOOKS
        }
    }

    public interface Order {
        @Field(1)
        String order_id();
        @Field(2)
        Map<String, Integer> items();
        @Field(3)
        float total_amount();
        @Field(4)
        Status status();

        enum Status {
            PENDING, SHIPPED, DELIVERED, CANCELLED
        }
    }
}
