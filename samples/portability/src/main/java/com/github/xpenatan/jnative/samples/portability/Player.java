package com.github.xpenatan.jnative.samples.portability;

public class Player {
    public String name;
    public int score;

    public Player(String name) {
        this.name = name;
    }

    public String describe() {
        return name + ": " + score;
    }
}
