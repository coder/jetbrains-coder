<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# coder-gateway Changelog

## Unreleased

## 2.9.0-eap.0 - 2023-10-27

### Added

- Configuration options for mTLS.
- Configuration options for adding a CA cert to the trust store and an alternate
  hostname.
- Agent ID can be used in place of the name when using the Gateway link. If
  both are present the name will be ignored.

### Fixed

- Configuring SSH will include all agents even on workspaces that are off.

## 2.8.0 - 2023-10-03

### Added

- Add a setting for a command to run to get headers that will be set on all
  requests to the Coder deployment.
- Support for Gateway 2023.3.

## 2.6.0 - 2023-09-06

### Added

- Initial support for Gateway links (jetbrains-gateway://). See the readme for
  the expected parameters.
- Support for Gateway 232.9921.

## 2.5.2 - 2023-08-06

### Fixed

- Inability to connect to a workspace after going back to the workspaces view.
- Remove version warning for 2.x release.

### Changed

- Add a message to distinguish between connecting to the worker and querying for
  IDEs.

## 2.5.1 - 2023-07-07

### Fixed

- Inability to download new editors in older versions of Gateway.

## 2.5.0 - 2023-06-29

### Added

- Support for Gateway 2023.2.

## 2.4.0 - 2023-06-02

### Added

- Allow configuring the binary directory separately from data.
- Add status and start/stop buttons to the recent connections view.

### Changed

- Check binary version with `version --output json` (if available) since this is
  faster than waiting for the round trip checking etags. It also covers cases
  where the binary is hosted somewhere that does not support etags.
- Move the template link from the row to a dedicated button on the toolbar.

## 2.3.0 - 2023-05-03

### Added

- Support connecting to multiple deployments (existing connections will still be
  using the old method; please re-add them if you connect to multiple
  deployments)
- Settings page for configuring both the source and destination of the CLI
- Listing editors and connecting will retry automatically on failure
- Surface various errors in the UI to make them more immediately visible

### Changed

- A token dialog and browser will not be launched when automatically connecting
  to the last known deployment; these actions will only take place when you
  explicitly interact by pressing "connect"
- Token dialog has been widened so the entire token can be seen at once

### Fixed

- The help text under the IDE dropdown now takes into account whether the IDE is
  already installed
- Various minor alignment issues
- Workspaces table now updates when the agent status changes
- Connecting when the directory contains a tilde
- Selection getting lost when a workspace starts or stops
- Wait for the agent to become fully ready before connecting
- Avoid populating the token dialog with the last known token if it was for a
  different deployment

## 2.2.1 - 2023-03-23

### Fixed

- Reading an existing config would sometimes use the wrong directory on Linux
- Two separate SSH sessions would spawn when connecting to a workspace through
  the main flow

## 2.2.0 - 2023-03-08

### Added

- Support for Gateway 2023

### Fixed

- The "Select IDE and Project" button is no longer disabled for a time after
  going back a step

### Changed

- Initial authentication is now asynchronous which means no hang on the main
  screen while that happens and it shows in the progress bar

## 2.1.7 - 2023-02-28

### Fixed

- Terminal link is now correct when host ends in `/`
- Improved resiliency and error handling when trying to open the last successful connection

## 2.1.6-eap.0 - 2023-02-02

### Fixed

- Improved resiliency and error handling when resolving installed IDE's

## 2.1.6 - 2023-02-01

### Fixed

- Improved resiliency and error handling when resolving installed IDE's

## 2.1.5-eap.0 - 2023-01-24

### Fixed

- Support for `Remote Development` in the Jetbrains IDE's

## 2.1.5 - 2023-01-24

### Fixed

- Support for `Remote Development` in the Jetbrains IDE's

## 2.1.4-eap.0 - 2022-12-23

Bug fixes and enhancements included in `2.1.4` release:

### Added

- Ability to open a template in the Dashboard
- Ability to sort by workspace name, or by template name or by workspace status
- A new token is requested when the one persisted is expired
- Support for re-using already installed IDE backends

### Changed

- Renamed the plugin from `Coder Gateway` to `Gateway`
- Workspaces and agents are now resolved and displayed progressively

### Fixed

- Icon rendering on `macOS`
- `darwin` agents are now recognized as `macOS`
- Unsupported OS warning is displayed only for running workspaces

## 2.1.4 - 2022-12-23

### Added

- Ability to open a template in the Dashboard
- Ability to sort by workspace name, or by template name or by workspace status
- A new token is requested when the one persisted is expired
- Support for re-using already installed IDE backends

### Changed

- Renamed the plugin from `Coder Gateway` to `Gateway`
- Workspaces and agents are now resolved and displayed progressively

### Fixed

- Icon rendering on `macOS`
- `darwin` agents are now recognized as `macOS`
- Unsupported OS warning is displayed only for running workspaces

## 2.1.3-eap.0 - 2022-12-12

Bug fixes and enhancements included in `2.1.3` release:

### Added

- Warning system when plugin might not be compatible with Coder REST API
- A `Create workspace` button which links to Coder's templates page
- Workspace icons
- Quick toolbar action to open Coder Dashboard in the browser
- Custom user agent for the HTTP client

### Changed

- Redesigned the information&warning banner. Messages can now include hyperlinks

### Removed

- Connection handle window is no longer displayed

### Fixed

- Outdated Coder CLI binaries are cleaned up
- Workspace status color style: running workspaces are green, failed ones should be red, everything else is gray
- Typos in plugin description

## 2.1.3 - 2022-12-09

### Added

- Warning system when plugin might not be compatible with Coder REST API
- A `Create workspace` button which links to Coder's templates page
- Workspace icons
- Quick toolbar action to open Coder Dashboard in the browser
- Custom user agent for the HTTP client

### Changed

- Redesigned the information&warning banner. Messages can now include hyperlinks

### Removed

- Connection handle window is no longer displayed

### Fixed

- Outdated Coder CLI binaries are cleaned up
- Workspace status color style: running workspaces are green, failed ones should be red, everything else is gray
- Typos in plugin description

## 2.1.2-eap.0 - 2022-11-29

### Added

- Support for Gateway 2022.3 RC
- Upgraded support for the latest Coder REST API
- Support for latest Gateway 2022.2.x builds

### Fixed

- Authentication flow is now done using HTTP headers

## 2.1.2 - 2022-11-23

### Added

- Upgraded support for the latest Coder REST API
- Support for latest Gateway 2022.2.x builds

### Fixed

- Authentication flow is now done using HTTP headers

## 2.1.1

### Added

- Support for remembering last opened Coder session

### Changed

- Minimum supported Gateway build is now 222.3739.54
- Some dialog titles

## 2.1.0

### Added

- Support for displaying workspace version
- Support for managing the lifecycle of a workspace, i.e. start and stop and update workspace to the latest template version

### Changed

- Workspace panel is now updated every 5 seconds
- Combinations of workspace names and agent names are now listed even when a workspace is down
- Minimum supported Gateway build is now 222.3739.40

### Fixed

- Terminal link for workspaces with a single agent
- No longer allow users to open a connection to a Windows or macOS workspace. It's not yet supported by Gateway

## 2.0.2

### Added

- Support for displaying working and non-working workspaces
- Better support for Light and Dark themes in the "Status" column

### Fixed

- Left panel is no longer visible when a new connection is triggered from Coder's "Recent Workspaces" panel.
  This provides consistency with other plugins compatible with Gateway
- The "Select IDE and Project" button in the "Coder Workspaces" view is now disabled when no workspace is selected

### Changed

- The authentication view is now merged with the "Coder Workspaces" view allowing users to quickly change the host

## 2.0.1

### Fixed

- `Recent Coder Workspaces` label overlaps with the search bar in the `Connections` view
- Working workspaces are now listed when there are issues with resolving agents
- List only workspaces owned by the logged user

### Changed

- Links to documentation now point to the latest Coder OSS
- Simplified main action link text from `Connect to Coder Workspaces` to `Connect to Coder`
- Minimum supported Gateway build is now 222.3739.24

## 2.0.0

### Added

- Support for Gateway 2022.2

### Changed

- Java 17 is now required to run the plugin
- Adapted the code to the new SSH API provided by Gateway

## 1.0.0

### Added

- Initial scaffold for Gateway plugin
- Browser based authentication on Coder environments
- REST client for Coder V2 public API
- coder-cli orchestration for setting up the SSH configurations for Coder Workspaces
- Basic panel to display live Coder Workspaces
- Support for multi-agent Workspaces
- Gateway SSH connection to a Coder Workspace
