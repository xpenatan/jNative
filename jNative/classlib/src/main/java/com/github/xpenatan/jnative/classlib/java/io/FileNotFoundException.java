package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.FileNotFoundException")
public class FileNotFoundException extends IOException {
    public FileNotFoundException() {
    }

    public FileNotFoundException(String message) {
        super(message);
    }
}
