# Contributing

## Architecture

The Coder Toolbox Gateway plugins provides some login pages, after which
it configures SSH and gives Toolbox a list of environments with their
host names. Toolbox then handles everything else.

There are two ways to get into a workspace:

1. Dashboard link.
2. Through Toolbox.

## Development

You can get the latest build of Toolbox with Gateway support from our shared
Slack channel with JetBrains. Make sure you download the right version (check
[./gradle/libs.versions.toml](./gradle/libs.versions.toml)).

To load the plugin into Toolbox, close Toolbox, run `./gradlew build copyPlugin`,
then launch Toolbox again. If you are not seeing your changes, try copying the
plugin into Toolbox's `cache/plugins` directory instead of `plugins`.

To simulate opening a workspace from the dashboard you can use something like
`xdg-open` to launch a URL in this format:

```
jetbrains://gateway/com.coder.gateway/connect?workspace=dev&agent=coder&url=https://dev.coder.com&token=<redacted>
```

If your change is something users ought to be aware of, add an entry in the
changelog.

Generally we prefer that PRs be squashed into `main` but you can rebase or merge
if it is important to keep the individual commits (make sure to clean up the
commits first if you are doing this).

We are using `ktlint` to format but this is not currently enforced.

## Testing

Run tests with `./gradlew test`. By default this will test against
`https://dev.coder.com` but you can set `CODER_GATEWAY_TEST_DEPLOYMENT` to a URL
of your choice or to `mock` to use mocks only.

Some investigation is needed to see what options we have for testing code
directly tied to the UI, as currently that code is untested.

## Releasing

We do not yet have a release workflow yet, but it will look like:

1. Check that the changelog lists all the important changes.
2. Update the extension.json version and changelog header.
3. Tag the commit made from the second step with the version.
4. Publish the resulting draft release after validating it.
