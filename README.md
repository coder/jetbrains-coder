# Coder Toolbox Gateway plugin

To load plugin into the Toolbox App, run `./gradlew build copyPlugin`

or put files in the following directory:

* Windows: `%LocalAppData%/JetBrains/Toolbox/cache/plugins/plugin-id`
* macOS: `~/Library/Caches/JetBrains/Toolbox/plugins/plugin-id`
* Linux: `~/.local/share/JetBrains/Toolbox/plugins/plugin-id`

Put all required .jar files (do not include any dependencies already included
with the Toolbox App to avoid possible resolution conflicts), `extensions.json`
and `icon.svg` in this directory.
