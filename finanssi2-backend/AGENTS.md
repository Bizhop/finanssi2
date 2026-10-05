# Backend: notes for coding agents

## Building in the sandbox

The working tree is shared with the host, where IntelliJ builds it with Gradle. Sharing `build/` and `.gradle/` made sandbox builds fail
(`Unable to delete directory build/test-results/...`) and report host test runs as `UP-TO-DATE`. So the sandbox keeps its own build
state outside the tree, via its own Gradle user home. Don't use `clean` as a fix, and don't touch the project's `build/` or `.gradle/`.

Before building, check these exist (a recreated sandbox loses them), and recreate if missing:

```bash
mkdir -p ~/.gradle/init.d
cat > ~/.gradle/init.d/sandbox-build-dir.gradle <<'EOF'
// Sandbox only: keep build outputs out of the working tree shared with the host (IntelliJ)
allprojects {
    layout.buildDirectory = file("${System.getProperty('user.home')}/.cache/gradle-build/${rootProject.name}${path == ':' ? '' : path.replace(':', '/')}")
}
EOF
echo 'org.gradle.projectcachedir=/home/agent/.cache/gradle-project-cache/finanssi2' >> ~/.gradle/gradle.properties
```

- Run Gradle via a login shell (Java on PATH): `bash -l -c "./gradlew test --console=plain"`; incremental builds are safe
- Test results: `~/.cache/gradle-build/finanssi2/test-results/test/`

## Running the backend and PostgreSQL

PostgreSQL persistence integration tests are tagged for manual runs. `databaseTest` uses a PostgreSQL instance at
`host.docker.internal:5432` (override with `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD`); `containerTest` starts an
isolated Testcontainer. For local development, start PostgreSQL with `docker compose up -d`, then run the backend with
`./gradlew bootRun`. Hibernate updates the schema from the JPA entities; use `docker compose down -v` to wipe the local database
when an incompatible update requires a clean start.

## Java conventions

- prefer streaming API and immutable colletions
- prefer guard clauses to avoid deep if-else blocks
- prefer exhaustive switch expressions when dealing with enums
- prefer simple records over POJOs
