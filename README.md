# Coder Gateway Plugin 

[!["Join us on
Discord"](https://img.shields.io/badge/join-us%20on%20Discord-gray.svg?longCache=true&logo=discord&colorB=purple)](https://discord.gg/coder)
[![Twitter
Follow](https://img.shields.io/twitter/follow/CoderHQ?label=%40CoderHQ&style=social)](https://twitter.com/coderhq)
[![Coder Gateway Plugin Build](https://github.com/coder/coder-jetbrains/actions/workflows/build.yml/badge.svg)](https://github.com/coder/coder-jetbrains/actions/workflows/build.yml)

<!-- Plugin description -->
**Coder Gateway** connects your Jetbrains IDE to your [Coder Workspaces](https://coder.com/docs/coder/latest/workspaces) so that you can develop from anywhere.

**Manage less**

- Ensure your entire team is using the same tools and resources
- Keep your source code and data behind your firewall

**Code more**

- Build and test faster
    - Leveraging cloud CPUs, RAM, network speeds, etc.
- Access your environment from any place
- Onboard instantly then stay up to date continuously

<!-- Plugin description end -->

## Getting Started

To manually install a local build:

1. Install [Jetbrains Gateway](https://www.jetbrains.com/help/phpstorm/remote-development-a.html#gateway)
2. run `./gradlew clean buildPlugin` to generate a zip distribution
3. locate the zip file in the `build/distributions` folder and follow [these instructions](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk) on how to install a plugin from disk.

Alternatively, `./gradlew clean runIde` will deploy a Gateway distribution (the one specified in `gradle.properties` - `platformVersion`) with the latest plugin changes deployed.

### Plugin Structure

```
.
├── .github/                GitHub Actions workflows and Dependabot configuration files
├── gradle
│   └── wrapper/            Gradle Wrapper
├── build/                  Output build directory
├── src                     Plugin sources
│   └── main
│       ├── kotlin/         Kotlin production sources
│       └── resources/      Resources - plugin.xml, icons, i8n
│   └── test
│       ├── kotlin/         Kotlin test sources
├── .gitignore              Git ignoring rules
├── build.gradle.kts        Gradle configuration
├── CHANGELOG.md            Full change history
├── gradle.properties       Gradle configuration properties
├── gradlew                 *nix Gradle Wrapper script
├── gradlew.bat             Windows Gradle Wrapper script
├── qodana.yml              Qodana profile configuration file
├── README.md               README
└── settings.gradle.kts     Gradle project settings
```

`src` directory is the most important part of the project, the Coder Gateway  implementation and the manifest for the plugin – [plugin.xml][file:plugin.xml].

### Gradle Configuration Properties

The project-specific configuration file [gradle.properties][file:gradle.properties] contains:

| Property name               | Description                                                                                                   |
| --------------------------- |---------------------------------------------------------------------------------------------------------------|
| `pluginGroup`               | Package name, set to `com.coder.gateway`.                                                                     |
| `pluginName`                | Zip filename.                                                                                                 |
| `pluginVersion`             | The current version of the plugin in [SemVer](https://semver.org/) format.                                    |
| `pluginSinceBuild`          | The `since-build` attribute of the `<idea-version>` tag. The minimum Gateway build supported by the plugin    |
| `pluginUntilBuild`          | The `until-build` attribute of the `<idea-version>` tag. Supported Gateway builds, until & not inclusive      |
| `platformType`              | The type of IDE distribution, in this GW.                                                                     |
| `platformVersion`           | The version of the Gateway used to build&run the plugin.                                                      |
| `platformDownloadSources`   | Gateway sources downloaded while initializing the Gradle build. Note: Gateway does not have open sources      |
| `platformPlugins`           | Comma-separated list of dependencies to the bundled Gateway plugins and plugins from the Plugin Repositories. |
| `javaVersion`               | Java language level used to compile sources and generate the files for - Java 11 is required since 2020.3.    |
| `gradleVersion`             | Version of Gradle used for plugin development.                                                                |

The properties listed define the plugin itself or configure the [gradle-intellij-plugin][gh:gradle-intellij-plugin] – check its documentation for more details.

### Testing

No functional or UI tests are available yet.

### Code Monitoring

Code quality is monitored with the help of [Qodana](https://www.jetbrains.com/qodana/)

Qodana inspections are accessible within the project on two levels:

- using the [Qodana IntelliJ GitHub Action][docs:qodana-github-action], run automatically within the [Build](.github/workflows/build.yml) workflow,
- with the [Gradle Qodana Plugin][gh:gradle-qodana-plugin], so you can use it on the local environment or any CI other than GitHub Actions.

Qodana inspection is configured with the `qodana { ... }` section in the [Gradle build file][file:build.gradle.kts] and [`qodana.yml`][file:qodana.yml] YAML configuration file.

> **NOTE:** Qodana requires Docker to be installed and available in your environment.

To run inspections, you can use a predefined *Run Qodana* configuration, which will provide a full report on `http://localhost:8080`, or invoke the Gradle task directly with the `./gradlew runInspections` command.

A final report is available in the `./build/reports/inspections/` directory.

![Qodana][file:qodana.png]