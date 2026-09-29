package com.github.xpenatan.jnative.samples.substitution;

import vendor.Counter;
import vendor.Message;

/** Compiles exclusively against the original dependency API. */
public class SubstitutionExample {
    public static void main(String[] args) {
        Counter counter = new Counter();
        System.out.println(counter.add(3));
        System.out.println(counter.add(2));
        System.out.println(counter.unchanged());
        System.out.println(new Message().text());
    }
}
