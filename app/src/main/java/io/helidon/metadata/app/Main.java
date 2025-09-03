package io.helidon.metadata.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws IOException, URISyntaxException {
        System.out.println("## Helidon Metadata App\n");
        System.out.println("Flattened: " + isFlattened() + "\n");
        System.out.println("### Discovered resources\n");
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
        Set<String> result = new HashSet<>();
        Deque<Path> stack = new ArrayDeque<>(paths);
        while (!stack.isEmpty()) {
            Path path = stack.pop();
            if (Files.isDirectory(path)) {
                result.addAll(resources(path, predicate));
            } else if (isZipFile(path)) {
                try (var fs = zipFileSystem(path)) {
                    for (Path root : fs.getRootDirectories()) {
                        result.addAll(resources(root, predicate));
                        Path mf = root.resolve("META-INF/MANIFEST.MF");
                        if (Files.exists(mf)) {
                            Manifest manifest = new Manifest(Files.newInputStream(mf));
                            String cp = manifest.getMainAttributes().getValue("Class-Path");
                            if (cp != null) {
                                Path parent = path.getParent();
                                for (String e : cp.split("\\s")) {
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
        return result;
    }

    static List<String> resources(Path path, Predicate<Path> predicate) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            return stream.filter(p -> !Files.isDirectory(p))
                    .filter(predicate)
                    .map(Path::toString) // TODO relativize
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

    static boolean isZipFile(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            return is.read() == 0x50 && is.read() == 0x4b; // magic number
        }
    }

    static boolean isFlattened() throws URISyntaxException {
        return Objects.equals(
                entry("META-INF/helidon/io.helidon.metadata/module1/file.json"),
                entry("META-INF/helidon/io.helidon.metadata/module2/file.json"));
    }

    static String entry(String path) throws URISyntaxException {
        URL url = ClassLoader.getSystemClassLoader().getResource(path);
        String entry = null;
        if (url != null) {
            String spec = url.toURI().getSchemeSpecificPart();
            entry = spec.substring(0, spec.length() - (path.length() + 1));
            if (entry.charAt(entry.length() - 1) == '!') {
                entry = entry.substring(0, entry.length() - 1);
            }
        }
        return entry;
    }
}
