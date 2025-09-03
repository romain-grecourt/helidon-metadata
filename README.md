## Helidon Metadata

An experiment to investigate ways to provide Helidon metadata at unique location that does not require merging.

E.g.

```
/META-INF/helidon/{groupId}/{artifactId}/service-registry.json
/META-INF/helidon/{groupId}/{artifactId}/config-metadata.json
/META-INF/helidon/{groupId}/{artifactId}/feature-registry.json
```

### Resource manifest

A mergeable text file at a static location that lists all the resources paths under `/META-INF/helidon`.

E.g. `/META-INF/helidon/resources`
```
/META-INF/helidon/io.helidon.common/helidon-common/service-registry.json
/META-INF/helidon/io.helidon.webserver/helidon-webserver/service-registry.json
/META-INF/helidon/io.helidon.webserver/helidon-webserver/config-metadata.json
```

This file can be simply concatenated.

### Fallback

If we detect "flattening", and the manifest is "incomplete", we can implement a fallback to find all the unique resource paths.
Such fallback can be implemented by traversing the class-path and module-path entries and listing all their contents.

This fallback should be disabled for native-image.

### Native Image

All the resources must be added to the native-image, I.e., all JSON resources and `/META-INF/helidon/manifest`.

### Merging Resources

Are the maven-assembly-plugin and maven-shade-plugin extensions still relevant ?

### Demo

```shell
mvn clean package
```

Thin jar:
```shell
java -jar app/target/app.jar
```

Fat jar:
```shell
java -jar app/target/app-fat.jar
```

Module-Path:
```shell
java -p app/target/app.jar:module1/target/module1.jar:module2/target/module2.jar \
  -m io.helidon.metadata.app/io.helidon.metadata.app.Main
```

Class-Path:
```shell
java -cp app/target/classes:module1/target/module1.jar:module2/target/module2.jar io.helidon.metadata.app.Main 
```
