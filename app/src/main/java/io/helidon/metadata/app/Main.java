package io.helidon.metadata.app;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException, URISyntaxException {
        System.out.println("Main started");
        if (isFlattened()) {
            System.out.println("fat-jar detected!!");
        }
        Set<String> resources = resources(p -> p.startsWith("/META-INF/helidon"));
        resources.forEach(System.out::println);
    }

    static Set<String> resources(Predicate<Path> predicate) throws IOException {
        Set<Path> paths = new HashSet<>();
        for (String e : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!e.isEmpty()) {
                paths.add(Path.of(e).toAbsolutePath().normalize());
            }
        }
        for (String e : System.getProperty("jdk.module.path", "").split(File.pathSeparator)) {
            if (!e.isEmpty()) {
                paths.add(Path.of(e).toAbsolutePath().normalize());
            }
        }
        Set<String> resources = new HashSet<>();
        Deque<Path> stack = new ArrayDeque<>(paths);
        while (!stack.isEmpty()) {
            Path path = stack.pop();
            if (Files.isDirectory(path)) {
                resources.addAll(resources(path, predicate));
            } else if (path.getFileName().toString().endsWith(".jar")) {
                try (var fs = zipFileSystem(path)) {
                    for (Path root : fs.getRootDirectories()) {
                        resources.addAll(resources(root, predicate));
                        Path mf = root.resolve("META-INF/MANIFEST.MF");
                        if (Files.exists(mf)) {
                            Manifest manifest = new Manifest(Files.newInputStream(mf));
                            String cp = manifest.getMainAttributes().getValue("Class-Path");
                            if (cp != null) {
                                Path parent = path.getParent();
                                for (String e : manifest.getMainAttributes().getValue("Class-Path").split(" ")) {
                                    if (!e.isEmpty()) {
                                        Path path0 = parent.resolve(e).toAbsolutePath().normalize();
                                        if (paths.add(path0)) {
                                            stack.push(path0);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return resources;
    }

    static List<String> resources(Path path, Predicate<Path> predicate) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(p -> !Files.isDirectory(p))
                    .filter(predicate)
                    .map(Path::toString)
                    .toList();
        }
    }

    static FileSystem zipFileSystem(Path path) throws IOException {
        String uriPrefix = "jar:file:";
        if (File.pathSeparatorChar != ':') { // windows
            uriPrefix += "/";
        }
        URI uri = URI.create(uriPrefix + path.toString().replace("\\", "/"));
        return FileSystems.newFileSystem(uri, Map.of());
    }

    static String jarPath(URI uri) {
        String spec = uri.getSchemeSpecificPart();
        int index = spec.indexOf("!/");
        if (index == -1) {
            return spec;
        }
        return spec.substring(0, index);
    }

    static boolean isFlattened() throws URISyntaxException {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        URL one = cl.getResource("META-INF/helidon/io.helidon.metadata/module1/file.json");
        URL two = cl.getResource("META-INF/helidon/io.helidon.metadata/module2/file.json");
        if (one != null && two != null) {
            return jarPath(one.toURI()).equals(jarPath(two.toURI()));
        } else {
            System.err.println("Cannot detect flattening");
            return false;
        }
    }
}
