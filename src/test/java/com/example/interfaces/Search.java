package com.example.interfaces;

import dev.akre.protege.annotation.GenProto;
import dev.akre.protege.annotation.Field;

import java.util.List;

@GenProto(value = "search.proto", pkg = "com.example.proto", outerClass = true)
public class Search {

    public interface SearchRequest {
        @Field(1)
        String query();
        @Field(2)
        int page_number();
        @Field(3)
        int result_per_page();
        @Field(4)
        Corpus corpus();

        interface Corpus {
            @Field(1)
            Type type();

            enum Type {
                UNIVERSAL, WEB, IMAGES, LOCAL, NEWS, PRODUCTS, VIDEO
            }
        }
    }

    public interface SearchResponse {
        @Field(1)
        List<String> results();
    }
}
