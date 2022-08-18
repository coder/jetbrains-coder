<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# coder-gateway Changelog

## [Unreleased]

## [2.1.0]
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

## [2.0.2]
### Added
- support for displaying working and non-working workspaces
- better support for Light and Dark themes in the "Status" column

### Fixed
- left panel is no longer visible when a new connection is triggered from Coder's "Recent Workspaces" panel.
  This provides consistency with other plugins compatible with Gateway
- the "Select IDE and Project" button in the "Coder Workspaces" view is now disabled when no workspace is selected

### Changed
- the authentication view is now merged with the "Coder Workspaces" view allowing users to quickly change the host

## [2.0.1]
### Fixed
- `Recent Coder Workspaces` label overlaps with the search bar in the `Connections` view
- working workspaces are now listed when there are issues with resolving agents
- list only workspaces owned by the logged user

### Changed
- links to documentation now point to the latest Coder OSS
- simplified main action link text from `Connect to Coder Workspaces` to `Connect to Coder`
- minimum supported Gateway build is now 222.3739.24

## [2.0.0]
### Added
- support for Gateway 2022.2

### Changed
- Java 17 is now required to run the plugin
- adapted the code to the new SSH API provided by Gateway

## [1.0.0]
### Added
- initial scaffold for Gateway plugin
- browser based authentication on Coder environments
- REST client for Coder V2 public API
- coder-cli orchestration for setting up the SSH configurations for Coder Workspaces
- basic panel to display live Coder Workspaces
- support for multi-agent Workspaces
- Gateway SSH connection to a Coder Workspace