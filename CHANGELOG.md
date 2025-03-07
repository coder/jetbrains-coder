<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# coder-gateway Changelog

## Unreleased

### Changed

Retrieve workspace directly in link handler when using wildcardSSH feature

## 2.19.0 - 2025-02-21

### Added

- Added functionality to show setup script error message to the end user.

### Fixed

- Fix bug where wildcard configs would not be written under certain conditions.

## 2.18.1 - 2025-02-14

### Changed

- Update the `pluginUntilBuild` to latest EAP

## 2.18.0 - 2025-02-04

### Changed

- Simplifies the written SSH config and avoids the need to make an API request for every workspace the filter returns.

## 2.17.0 - 2025-01-27

### Added

- Added setting "Check for IDE updates" which controls whether the plugin
  checks and prompts for available IDE backend updates.

## 2.16.0 - 2025-01-17

### Added

- Added setting "Default IDE Selection" which will look for a matching IDE 
  code/version/build number to set as the preselected IDE in the select 
  component.

## 2.15.2 - 2025-01-06

### Changed

- When starting a workspace, shell out to the Coder binary instead of making an
  API call. This reduces drift between what the plugin does and the CLI does.
- Increase workspace polling to one second on the workspace list view, to pick
  up changes made via the CLI faster. The recent connections view remains
  unchanged at five seconds.

## 2.15.1 - 2024-10-04

### Added

- Support an "owner" parameter when launching an IDE from the dashboard. This
  makes it possible to reliably connect to the right workspace in the case where
  multiple users are using the same workspace name and the workspace filter is
  configured to show multiple users' workspaces. This requires an updated
  Gateway module that includes the new "owner" parameter.

## 2.15.0 - 2024-10-04

### Added

- Add the ability to customize the workspace query filter used in the workspaces
  table view. For example, you can use this to view workspaces other than your
  own by changing the filter or making it blank (useful mainly for admins).
  Please note that currently, if many workspaces are being fetched this could
  result in long configuration times as the plugin will make queries for each
  workspace that is not running to find its agents (running workspaces already
  include agents in the initial workspaces query) and add them individually to
  the SSH config. In the future, we would like to use a wildcard host name to
  work around this issue.

  Additionally, be aware that the recents view is using the same query filter.
  This means if you connect to a workspace, then change the filter such that the
  workspace is excluded, you could cause the workspace to be deleted from the
  recent connections even if the workspace still exists in actuality, as it
  would no longer show up in the query which the plugin takes as its cue to
  delete the connection.
- Add owner column to connections view table.
- Add agent name to the recent connections view.

## 2.14.2 - 2024-09-23

### Changed

- Add support for latest 2024.3 EAP.

## 2.14.1 - 2024-09-13

### Fixed

- When a proxy command argument (such as the URL) contains `?` and `&`, escape
  it in the SSH config by using double quotes, as these characters have special
  meanings in shells.

## 2.14.0 - 2024-08-30

### Fixed

- When the `CODER_URL` environment variable is set but you connect to a
  different URL in Gateway, force the Coder CLI used in the SSH proxy command to
  use the current URL instead of `CODER_URL`. This fixes connection issues such
  as "failed to retrieve IDEs". To aply this fix, you must add the connection
  again through the "Connect to Coder" flow or by using the dashboard link (the
  recent connections do not reconfigure SSH).

### Changed

- The "Recents" view has been updated to have a new flow. Before, there were
  separate controls for managing the workspace and then you could click a link
  to launch a project (clicking a link would also start a stopped workspace
  automatically). Now, there are no workspace controls, just links which start
  the workspace automatically when needed. The links are enabled when the
  workspace is STOPPED, CANCELED, FAILED, STARTING, RUNNING. These states
  represent valid times to start a workspace and connect, or to simply connect
  to a running one or one that's already starting. We also use a spinner icon
  when workspaces are in a transition state (STARTING, CANCELING, DELETING,
  STOPPING) to give context for why a link might be disabled or a connection
  might take longer than usual to establish.

## 2.13.1 - 2024-07-19

### Changed

- Previously, the plugin would try to respawn the IDE if we fail to get a join
  link after five seconds. However, it seems sometimes we do not get a join link
  that quickly. Now the plugin will wait indefinitely for a join link as long as
  the process is still alive.  If the process never comes alive after 30 seconds
  or it dies after coming alive, the plugin will attempt to respawn the IDE.

### Added

- Extra logging around the IDE spawn to help debugging.
- Add setting to enable logging connection diagnostics from the Coder CLI for
  debugging connectivity issues.

## 2.13.0 - 2024-07-16

### Added

- When using a recent workspace connection, check if there is an update to the
  IDE and prompt to upgrade if an upgrade exists.

## 2.12.2 - 2024-07-12

### Fixed

- On Windows, expand the home directory when paths use `/` separators (for
  example `~/foo/bar` or `$HOME/foo/bar`). This results in something like
  `c:\users\coder/foo/bar`, but Windows appears to be fine with the mixed
  separators. As before, you can still use `\` separators (for example
  `~\foo\bar` or `$HOME\foo\bar`.

## 2.12.1 - 2024-07-09

### Changed

- Allow connecting when the agent state is "connected" but the lifecycle state
  is "created". This may resolve issues when trying to connect to an updated
  workspace where the agent has restarted but lifecycle scripts have not been
  ran again.

## 2.12.0 - 2024-07-02

### Added

- Set `--usage-app` on the proxy command if the Coder CLI supports it
  (>=2.13.0). To make use of this, you must add the connection again through the
  "Connect to Coder" flow or by using the dashboard link (the recents
  connections do not reconfigure SSH).

### Changed

- Add support for latest Gateway 242.* EAP.

### Fixed

- The version column now displays "Up to date" or "Outdated" instead of
  duplicating the status column.

## 2.11.7 - 2024-05-22

### Fixed

- Polling and workspace action buttons when running from File > Remote
  Development within a local IDE.

## 2.11.6 - 2024-05-08

### Fixed

- Multiple clients being launched when a backend was already running.

## 2.11.5 - 2024-05-06

### Added

- Automatically restart and reconnect to the IDE backend when it disappears.

## 2.11.4 - 2024-05-01

### Fixed

- All recent connections show their status now, not just the first.

## 2.11.3 - 2024-04-30

### Fixed

- Default URL setting was showing the help text for the setup command instead of
  its own description.
- Exception when there is no default or last used URL.

## 2.11.2 - 2024-04-30

### Fixed

- Sort IDEs by version (latest first).
- Recent connections window will try to recover after encountering an error.
  There is still a known issue where if a token expires there is no way to enter
  a new one except to go back through the "Connect to Coder" flow.
- Header command ignores stderr and does not error if nothing is output.  It
  will still error if any blank lines are output.
- Remove "from jetbrains.com" from the download text since the download source
  can be configured.

### Changed

- If using a certificate and key, it is assumed that token authentication is not
  required, all token prompts are skipped, and the token header is not sent.
- Recent connections to deleted workspaces are automatically deleted.
- Display workspace name instead of the generated host name in the recents
  window.
- Add deployment URL, IDE product, and build to the recents window.
- Display status and error in the recents window under the workspace name
  instead of hiding them in tooltips.
- Truncate the path in the recents window if it is too long to prevent
  needing to scroll to press the workspace actions.
- If there is no default URL, coder.example.com will no longer be used. The
  field will just be blank, to remove the need to first delete the example URL.

### Added

- New setting for a setup command that will run in the directory of the IDE
  before connecting to it.  By default if this command fails the plugin will
  display the command's exit code and output then abort the connection, but
  there is an additional setting to ignore failures.
- New setting for extra SSH options.  This is arbitrary text and is not
  validated in any way.  If this setting is left empty, the environment variable
  CODER_SSH_CONFIG_OPTIONS will be used if set.
- New setting for the default URL. If this setting is left empty, the
  environment variable CODER_URL will be used. If CODER_URL is also empty, the
  URL in the global CLI config directory will be used, if it exists.

## 2.10.0 - 2024-03-12

### Changed

- If IDE details or the folder are missing from a Gateway link, the plugin will
  now show the IDE selection screen to allow filling in these details.

### Fixed

- Fix matching on the wrong workspace/agent name. If a Gateway link was failing,
  this could be why.
- Make errors when starting/stopping/updating a workspace visible.

## 2.9.4 - 2024-02-26

### Changed

- Disable autostarting workspaces by default on macOS to prevent an issue where
  it wakes periodically and keeps the workspace on. This can be toggled via the
  "Disable autostart" setting.
- CLI configuration is now reported in the progress indicator. Before it
  happened in the background so it made the "Select IDE and project" button
  appear to hang for a short time while it completed.

### Fixed

- Prevent environment variables being expanded too early in the header
  command. This will make header commands like `auth --url=$CODER_URL` work.
- Stop workspaces before updating them. This is necessary in some cases where
  the update changes parameters and the old template needs to be stopped with
  the existing parameter values first or where the template author was not
  diligent about making sure the agent gets restarted with the new ID and token
  when doing two build starts in a row.
- Errors from API requests are now read and reported rather than only reporting
  the HTTP status code.
- Data and binary directories are expanded so things like `~` can be used now.

## 2.9.3 - 2024-02-10

### Fixed

- Plugin will now use proxy authorization settings.

## 2.9.2 - 2023-12-19

### Fixed

- Listing IDEs when using the plugin from the File > Remote Development option
  within a local IDE should now work.
- Recent connections are now preserved.

## 2.9.1 - 2023-11-06

### Fixed

- Set the `CODER_HEADER_COMMAND` environment variable when executing the CLI with the setting value.

## 2.9.0 - 2023-10-27

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
