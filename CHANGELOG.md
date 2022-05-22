<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Coder Gateway Changelog

## [Unreleased]

### Added

* welcome screen
* basic connector view triggered by the Coder's welcome view. It asks the user a Coder hostname, port, email and password.
* back button to return to the main welcome view
* basic Coder http client which authenticates, retrieves a session token and uses it to retrieve the Workspaces created by the
  user that is logged.