<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Coder Gateway Changelog

## [Unreleased]

### Added

* welcome screen
* basic Coder Workspaces wizard triggered by the Coder's welcome view. It asks the user a Coder hostname, port, email and password,
  authenticates with the Coder server and lists a collection of Workspaces created by the user.
* back button to return to the main welcome view
* basic Coder http client which authenticates, retrieves a session token and uses it to retrieve the Workspaces created by the
  user that is logged.