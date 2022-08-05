<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# coder-gateway Changelog

## [Unreleased]
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