package com.github.xpenatan.jnative.samples.reflection;

import java.lang.reflect.*;

/**
 * The same source runs on the JVM and in generated native code.
 */
public final class ReflectionExample {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.github.xpenatan.jnative.samples.reflection.Player");
        Object player = type.getConstructor(String.class, int.class).newInstance("Ada", 100);
        Field health = type.getField("health");
        System.out.println("Created: " + type.getField("created").getInt(null));
        System.out.println("Initial health: " + health.getInt(player));
        health.setInt(player, 90);
        Method damage = type.getMethod("damage", int.class);
        System.out.println("After damage: " + damage.invoke(player, 15));
        System.out.println("Description: " + type.getMethod("describe").invoke(player));
        System.out.println("Runtime type matches: " + (player.getClass() == type));
        System.out.println("Volatile field: " + Modifier.isVolatile(health.getModifiers()));
        System.out.println("Method parameter: " + damage.getParameterTypes()[0].getName());
        System.gc();
        System.out.println("Health after GC: " + health.get(player));
        try {
            damage.invoke(player, -1);
        } catch(InvocationTargetException error) {
            System.out.println("Caught target error: " + error.getCause().getMessage());
        }
        try {
            type.getField("maximumHealth").setInt(player, 200);
        } catch(IllegalAccessException error) {
            System.out.println("Final field write rejected");
        }
    }
}
