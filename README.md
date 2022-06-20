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
