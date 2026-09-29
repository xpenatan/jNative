package java.io;

import java.nio.file.Files;
import java.nio.file.Path;

public class File implements Comparable<File>, Serializable {
    public static final String separator = System.getProperty("file.separator");
    public static final char separatorChar = separator.charAt(0);
    public static final String pathSeparator = System.getProperty("path.separator");
    public static final char pathSeparatorChar = pathSeparator.charAt(0);
    private final String path;

    public File(String path) {
        if(path == null) throw new NullPointerException();
        this.path = path;
    }

    public File(File parent, String child) {
        this(parent == null ? child : parent.toPath().resolve(child).toString());
    }

    public File(String parent, String child) {
        this(parent == null ? null : new File(parent), child);
    }

    public String getPath() {
        return path;
    }

    public Path toPath() {
        return Path.of(path);
    }

    public String getAbsolutePath() {
        return NativeFiles.pathText(toPath(), 0);
    }

    public File getAbsoluteFile() {
        return new File(getAbsolutePath());
    }

    public String getName() {
        return NativeFiles.pathText(toPath(), 1);
    }

    public boolean exists() {
        return Files.exists(toPath());
    }

    public boolean isFile() {
        return (NativeFiles.status(toPath()) & 1) != 0;
    }

    public boolean isDirectory() {
        return (NativeFiles.status(toPath()) & 2) != 0;
    }

    public long length() {
        return NativeFiles.length(toPath());
    }

    public boolean mkdirs() {
        return NativeFiles.mkdirs(toPath());
    }

    public File getParentFile() {
        String parent = NativeFiles.pathText(toPath(), 2);
        return parent == null ? null : new File(parent);
    }

    public String getParent() {
        File parent = getParentFile();
        return parent == null ? null : parent.getPath();
    }

    public boolean delete() {
        try {
            return Files.deleteIfExists(toPath());
        } catch(IOException ignored) {
            return false;
        }
    }

    public int compareTo(File other) {
        return toPath().compareTo(other.toPath());
    }

    public boolean equals(Object other) {
        return other instanceof File && compareTo((File)other) == 0;
    }

    public int hashCode() {
        return toPath().hashCode();
    }

    public String toString() {
        return path;
    }
}
