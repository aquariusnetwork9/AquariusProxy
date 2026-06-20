# Plugin Load Failure — Context & Remediation

Location: docs/CONTEXT_ERROR.md

Summary
- Failure observed in GitHub Actions job `pluginLoadTest` (job id: 82499169857).
- Server never printed the startup marker "ZenithProxy started!" and Gradle timed out waiting for it.
- Multiple plugins failed to load with NoClassDefFoundError for com.zenith.plugin.api.ZenithProxyPlugin.

Root cause
- The plugin JARs expect the API interface com.zenith.plugin.api.ZenithProxyPlugin to be available on the parent/runtime classloader. During the pluginLoadTest run, that API class was not visible to the plugin classloader, causing NoClassDefFoundError and preventing server startup.

Reproduction steps
1. Run the integration workflow locally or in CI that executes `./gradlew pluginLoadTest`.
2. The test setup downloads third-party plugin jars into run/plugins/ and starts the server.
3. Observe logs containing repeated lines similar to:
   java.lang.NoClassDefFoundError: com/zenith/plugin/api/ZenithProxyPlugin

Key log excerpt (searchable keywords)
- "NoClassDefFoundError: com/zenith/plugin/api/ZenithProxyPlugin"
- "Timed out waiting for 'ZenithProxy started!' in application output"
- Job name: pluginLoadTest

Immediate mitigations
- Ensure the server provides the plugin API class on the runtime classpath used when launching the server for the plugin load test.
- As a temporary measure, increase the pluginLoadTest timeout to avoid spurious failures while diagnosing classpath issues.

Recommended permanent fixes (pick one)

Option A — Provide API in server runtime (recommended)
- Make sure the module or jar that contains com.zenith.plugin.api is included in the runtime classpath used by the test runner.
- Example approaches:
  - Copy the server jar and/or classes to a run/libs directory and add that to the server startup classpath before loading plugins.
  - Adjust the Gradle pluginLoadTest task to include sourceSets.main.output and configurations.runtimeClasspath in its classpath.

Example Gradle snippets (place in the appropriate build.gradle)

1) Copy jar and runtime deps to run/libs before test:

```groovy
tasks.register('preparePluginLoadRuntime') {
    dependsOn('jar')
    doLast {
        copy {
            from(tasks.named('jar').get().archiveFile)
            into("$buildDir/run/libs")
            rename { "zenith-proxy-api.jar" }
        }
        copy {
            from configurations.runtimeClasspath
            into "$buildDir/run/libs"
        }
    }
}

tasks.named('pluginLoadTest') {
    dependsOn tasks.named('preparePluginLoadRuntime')
    doFirst {
        // If pluginLoadTest launches via JavaExec, make sure the run/libs jars are in its classpath
        if (project.tasks.pluginLoadTest instanceof JavaExec) {
            project.tasks.pluginLoadTest.classpath = files("$buildDir/run/libs") + project.tasks.pluginLoadTest.classpath
        }
    }
}
```

2) Alternatively, ensure the test JavaExec includes source output and runtimeClasspath:

```groovy
tasks.named('pluginLoadTest', JavaExec) {
    classpath = files(sourceSets.main.output) + configurations.runtimeClasspath
}
```

Option B — Shade the API into plugins (alternative)
- Build plugin jars as "fat" shaded jars that include com.zenith.plugin.api so they do not rely on server to provide the interface.
- Use the Shadow plugin in plugin projects and include the API dependency. This can work short-term but may cause class duplication issues and instanceof incompatibilities.

Timeout and diagnostics improvements
- Increase the pluginLoadTest wait timeout from ~60s to 120s while fixing the classpath.
- Capture full plugin loader stack traces to a test artifact so CI logs include more details.

Suggested place for easy discovery in VS Code (Windows)
- File path: docs/CONTEXT_ERROR.md
- Search keywords that Claude or any local search can use: "ZenithProxyPlugin", "NoClassDefFoundError", "pluginLoadTest", "ZenithProxy started!"
- To find quickly in VS Code on Windows: press Ctrl+P and type docs/CONTEXT_ERROR.md or use Ctrl+Shift+F and search one of the keywords above.

Validation
1. Implement Option A or B.
2. Run locally: `./gradlew pluginLoadTest --info` and confirm no NoClassDefFoundError and see "ZenithProxy started!" in logs.
3. Re-run CI workflow.

If you want, I can add a Gradle patch to the repository to implement preparePluginLoadRuntime and adjust pluginLoadTest. Paste the pluginLoadTest task from your build.gradle if you want an exact change.
