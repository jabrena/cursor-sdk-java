# Cursor SDK for Java WIP

```bash
# Lint Protobuf
./scripts/lint-protobuf.sh
# Generate Protobuf documentation
./scripts/generate-protobuf-documentation.sh
jwebserver -p 8000 -d "$(pwd)/docs"

# Build solution
./mvnw clean package
./mvnw clean install -pl examples -am && mvn exec:java -pl examples
```

## References

- https://protobuf.dev/
- https://protobuf.dev/programming-guides/proto3/
- https://www.jwt.io/
- https://ascopes.github.io/protobuf-maven-plugin/
- https://github.com/markvincze/sabledocs
- https://github.com/yoheimuta/protolint
