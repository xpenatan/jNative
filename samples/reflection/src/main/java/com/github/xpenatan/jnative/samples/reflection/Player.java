package com.github.xpenatan.jnative.samples.reflection;

/**
 * Retained through build registration; the example accesses this class by name.
 */
public class Player {
    public String name;
    public volatile int health;
    public static int created;
    public final int maximumHealth = 100;

    public Player(String name, int health) {
        this.name = name;
        this.health = health;
        ++created;
    }

    public synchronized int damage(int amount) {
        if(amount < 0) throw new IllegalArgumentException("damage cannot be negative");
        System.gc();
        health -= amount;
        return health;
    }

    public String describe() {
        return name + ": " + health;
    }
}
