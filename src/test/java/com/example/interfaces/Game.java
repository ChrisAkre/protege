package com.example.interfaces;

import dev.akre.protege.annotation.GenProto;
import dev.akre.protege.annotation.Field;

import java.util.List;
import java.util.Map;

@GenProto(value = "game.proto", pkg = "com.example.proto", outerClass = true)
public class Game {

    public interface Player {
        @Field(1)
        String id();
        @Field(2)
        String username();
        @Field(3)
        int level();
        @Field(4)
        Inventory inventory();
        @Field(5)
        Stats stats();

        interface Inventory {
            @Field(1)
            Map<String, Integer> items();
            @Field(2)
            List<String> equipped_gear();
        }

        interface Stats {
            @Field(1)
            int health();
            @Field(2)
            int mana();
            @Field(3)
            Map<String, Float> attributes();
        }
    }

    public interface World {
        @Field(1)
        List<Player> players();
        @Field(2)
        Map<Integer, String> zones();
    }
}
