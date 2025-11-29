# Cursor SDK for Java

```bash
./scripts/lint-protobuf.sh
./scripts/generate-protobuf-docs.sh
jwebserver -p 8000 -d "$(pwd)/docs"

./mvnw clean package
./mvnw clean install -pl examples -am && mvn exec:java -pl examples
```

## References

- https://www.jwt.io/
- https://ascopes.github.io/protobuf-maven-plugin/
- https://github.com/pseudomuto/protoc-gen-doc
- https://github.com/yoheimuta/protolint
