package com.example.interfaces;

import dev.akre.protege.annotation.GenProto;
import dev.akre.protege.annotation.Field;

@GenProto(value = "mediaplayer.proto", pkg = "com.example.proto")
public interface MediaItem {
    @Field(1)
    String id();
    @Field(2)
    String title();

    @Field(value = 3, oneof = "content")
    Video video();
    @Field(value = 4, oneof = "content")
    Audio audio();
    @Field(value = 5, oneof = "content")
    Image image();

    interface Video {
        @Field(1)
        int duration_seconds();
        @Field(2)
        String codec();
        @Field(3)
        Resolution resolution();
    }

    interface Audio {
        @Field(1)
        int duration_seconds();
        @Field(2)
        int bitrate();
    }

    interface Image {
        @Field(1)
        int width();
        @Field(2)
        int height();
        @Field(3)
        String format();
    }

    interface Resolution {
        @Field(1)
        int width();
        @Field(2)
        int height();
    }
}
