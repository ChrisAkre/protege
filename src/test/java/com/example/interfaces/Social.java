package com.example.interfaces;

import dev.akre.protege.GenProto;
import dev.akre.protege.Field;

import java.util.List;
import java.util.Map;

@GenProto(value = "social.proto", pkg = "com.example.proto", outerClass = true)
public class Social {

    public interface User {
        @Field(1)
        String id();
        @Field(2)
        String display_name();
        @Field(3)
        List<String> friend_ids();
    }

    public interface Post {
        @Field(1)
        String id();
        @Field(2)
        String author_id();
        @Field(3)
        String content();
        @Field(4)
        long timestamp();
        @Field(5)
        List<Comment> comments();
        @Field(6)
        Map<String, Boolean> reactions();

        interface Comment {
            @Field(1)
            String user_id();
            @Field(2)
            String text();
            @Field(3)
            long timestamp();
        }
    }
}
