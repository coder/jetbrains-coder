<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# coder-gateway Changelog

## Unreleased

### Added

- warning system when plugin might not be compatible with Coder REST API
- a `Create workspace` button which links to Coder's templates page
- workspace icons
- quick toolbar action to open Coder Dashboard in the browser
- custom user agent for the HTTP client

### Changed

- redesigned the information&warning banner. Messages can now include hyperlinks

### Removed

- connection handle window is no longer displayed

### Fixed

- outdated Coder CLI binaries are cleaned up
- workspace status color style: running workspaces are green, failed ones should be red, everything else is gray
- typos in plugin description

## 2.1.2 - 2022-11-23

### Added

- upgraded support for the latest Coder REST API
- support for latest Gateway 2022.2.x builds

### Fixed
- authentication flow is now done using HTTP headers

## 2.1.1

### Added
- support for remembering last opened Coder session

### Changed
- minimum supported Gateway build is now 222.3739.54
- some dialog titles

## 2.1.0

### Added
- support for displaying workspace version
- support for managing the lifecycle of a workspace, i.e. start and stop and update workspace to the latest template version

### Changed
- workspace panel is now updated every 5 seconds
- combinations of workspace names and agent names are now listed even when a workspace is down
- minimum supported Gateway build is now 222.3739.40

### Fixed
- terminal link for workspaces with a single agent
- no longer allow users to open a connection to a Windows or macOS workspace. It's not yet supported by Gateway

## 2.0.2

### Added
- support for displaying working and non-working workspaces
- better support for Light and Dark themes in the "Status" column

### Fixed
- left panel is no longer visible when a new connection is triggered from Coder's "Recent Workspaces" panel.
  This provides consistency with other plugins compatible with Gateway
- the "Select IDE and Project" button in the "Coder Workspaces" view is now disabled when no workspace is selected

### Changed
- the authentication view is now merged with the "Coder Workspaces" view allowing users to quickly change the host

## 2.0.1

### Fixed
- `Recent Coder Workspaces` label overlaps with the search bar in the `Connections` view
- working workspaces are now listed when there are issues with resolving agents
- list only workspaces owned by the logged user

### Changed
- links to documentation now point to the latest Coder OSS
- simplified main action link text from `Connect to Coder Workspaces` to `Connect to Coder`
- minimum supported Gateway build is now 222.3739.24

## 2.0.0

### Added
- support for Gateway 2022.2

### Changed
- Java 17 is now required to run the plugin
- adapted the code to the new SSH API provided by Gateway

## 1.0.0

### Added
- initial scaffold for Gateway plugin
- browser based authentication on Coder environments
- REST client for Coder V2 public API
- coder-cli orchestration for setting up the SSH configurations for Coder Workspaces
- basic panel to display live Coder Workspaces
- support for multi-agent Workspaces
- Gateway SSH connection to a Coder Workspace
