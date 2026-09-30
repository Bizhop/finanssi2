# Backend: notes for Claude

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

## Running the backend and MongoDB

Avoid running them in the sandbox; build and test are enough. The MongoDB integration tests (`GameMongoIntegrationTest`) run only
with `FINANSSI_MONGODB_URI` set; with the developer's database running on the host:
`FINANSSI_MONGODB_URI=mongodb://finanssi:finanssi@host.docker.internal:27017/finanssi bash -l -c "./gradlew test --console=plain"`.
They remove only what they create. For more (real database, running API, websocket), ask the developer to
start the services locally (`docker compose up -d`, `./gradlew bootRun`) and reach them at `host.docker.internal` (e.g. port 8080);
if blocked, ask them to allow the port in the sandbox network policy. If a sandbox database is unavoidable, use `mongo:7` on a port
other than 27017 (latest `mongo` won't start on the sandbox's 6.19+ kernel) and remove it afterwards.
