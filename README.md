# nav-epj

TODO

## Local development

Run in development to enable hot reloading and stubbed external dependencies:

With gradle:

```bash
./gradlew runLocal
```

In IntelliJ:

In IntelliJ run configuration, first run the main function, then edit it to add `-Pio.ktor.development=true` to the VM
options, and `-config=application-local.yaml` in "program options".
