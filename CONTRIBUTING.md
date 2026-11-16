# Contributing

## Architecture

The Coder Gateway plugin uses Gateway APIs to SSH into the remote machine,
download the requested IDE backend, run the backend, then launches a client that
connects to that backend using a port forward over SSH. If the backend goes down
due to a crash or a workspace restart, it will restart the backend and relaunch
the client.

There are three ways to get into a workspace:

1. Dashboard link.
2. "Connect to Coder" button.
3. Using a recent connection.

Currently the first two will configure SSH but the third does not yet.

## GPG Signature Verification

The Coder Gateway plugin starting with version *2.22.0* implements a comprehensive GPG signature verification system to
ensure the authenticity and integrity of downloaded Coder CLI binaries. This security feature helps protect users from
running potentially malicious or tampered binaries.

### How It Works

1. **Binary Download**: When connecting to a Coder deployment, the plugin downloads the appropriate Coder CLI binary for
   the user's operating system and architecture from the deployment's `/bin/` endpoint.

2. **Signature Download**: After downloading the binary, the plugin attempts to download the corresponding `.v2.asc`
   signature file from the same location. The signature file is named according to the binary (e.g.,
   `coder-linux-amd64.v2.asc` for `coder-linux-amd64`).

3. **Release Digests**: Deployments released before Coder's current release signing key have no `.v2.asc` signature.
   If the signature is not available from the deployment, the plugin compares the SHA-256 digest of the binary with
   the digests of Coder's published releases, which ship with the plugin. A binary that matches the release of the
   deployment's version is accepted.

4. **Fallback Signature Sources**: If the binary matches no release digest, the plugin can optionally fall back to
   downloading signatures from `releases.coder.com`. This is controlled by the `fallbackOnCoderForSignatures`
   setting.

5. **GPG Verification**: The plugin uses the BouncyCastle library shipped with Gateway app to verify the detached GPG
   signature against the downloaded binary using Coder's trusted public key.

6. **User Interaction**: If signature verification fails or signatures are unavailable, the plugin presents security
   warnings
   to users, allowing them to accept the risk and continue or abort the operation.

### Verification Process

The verification process involves several components:

- **`GPGVerifier`**: Handles the core GPG signature verification logic using BouncyCastle
- **`ReleaseDigests`**: Compares a binary with the bundled SHA-256 digests of Coder's published releases
- **`VerificationResult`**: Represents the outcome of verification (Valid, Invalid, Failed, SignatureNotFound)
- **`CoderDownloadService`**: Manages downloading both binaries and their signatures
- **`CoderCLIManager`**: Orchestrates the download and verification workflow

### Configuration Options

Users can control signature verification behavior through plugin settings:

- **`disableSignatureVerification`**: When enabled, skips all signature verification. This is useful for clients running
  custom CLI builds, or
  customers with old deployment versions that don't have a signature published on `releases.coder.com`.
- **`fallbackOnCoderForSignatures`**: When enabled, allows downloading signatures from `releases.coder.com` if not
  available from the deployment

### Security Considerations

- The plugin embeds Coder's trusted public key in the plugin resources
- The plugin embeds the SHA-256 digests of the Coder releases that are not signed with that key
- Verification uses detached signatures, which are more secure than attached signatures
- Users are warned about security risks when verification fails
- The system gracefully handles cases where signatures are unavailable
- All verification failures are logged for debugging purposes

### Error Handling

The system handles various failure scenarios:

- **Missing signatures**: Prompts user to accept risk or abort
- **Invalid signatures**: Warns user about potential tampering and prompts user to accept risk or abort
- **Verification failures**: Prompts user to accept risk or abort

This signature verification system ensures that users can trust the Coder CLI binaries they download through the plugin,
protecting against supply chain attacks and ensuring binary integrity.

## Development

To manually install a local build:

1. Install [Jetbrains Gateway](https://www.jetbrains.com/remote-development/gateway/)
2. Run `./gradlew clean buildPlugin` to generate a zip distribution.
3. Locate the zip file in the `build/distributions` folder and follow [these
   instructions](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk)
   on how to install a plugin from disk.

Alternatively, `./gradlew clean runIde` will deploy a Gateway distribution (the
one specified in `gradle.properties` - `platformVersion`) with the latest plugin
changes deployed.

To simulate opening a workspace from the dashboard pass the Gateway link via
`--args`. For example:

```
./gradlew clean runIDE --args="jetbrains-gateway://connect#type=coder&workspace=dev&agent=coder&folder=/home/coder&url=https://dev.coder.com&token=<redacted>&ide_product_code=IU&ide_build_number=223.8836.41&ide_download_link=https://download.jetbrains.com/idea/ideaIU-2022.3.3.tar.gz"
```

Alternatively, if you have separately built the plugin and already installed it
in a Gateway distribution you can launch that distribution with the URL as the
first argument (no `--args` in this case).

If your change is something users ought to be aware of, add an entry in the
changelog.

Generally we prefer that PRs be squashed into `main` but you can rebase or merge
if it is important to keep the individual commits (make sure to clean up the
commits first if you are doing this).

## Testing

Run tests with `./gradlew test`. By default this will test against
`https://dev.coder.com` but you can set `CODER_GATEWAY_TEST_DEPLOYMENT` to a URL
of your choice or to `mock` to use mocks only.

There are two ways of using the plugin: from standalone Gateway, and from within
an IDE (`File` > `Remote Development`).  There are subtle differences so it
makes usually sense to test both.  We should also be testing both the latest
stable and latest EAP.

## Plugin compatibility

`./gradlew runPluginVerifier` can check the plugin compatibility against the specified Gateway. The integration with Github Actions is commented until [this gradle intellij plugin issue](https://github.com/JetBrains/gradle-intellij-plugin/issues/1027) is fixed.

## Releasing

Releases are cut from `main` by the `Release` workflow (`.github/workflows/release.yml`). One
person starts a release and a member of `clients` other than that person approves it.

1. Check that the changelog lists all the important changes under `Unreleased`.
2. Update `pluginVersion` in `gradle.properties` and merge that to `main`.
3. Start the release: Actions > Release > Run workflow on `main`, with the version (for example
   `v2.23.2`, matching `pluginVersion`) and the full SHA of the commit on `main` to release. Or:
   `gh workflow run release.yml -f version=v2.23.2 -f commit=<sha>`.
4. A member of `clients` other than you approves the `release-approval` deployment. The workflow
   then creates the tag, publishes the plugin to the JetBrains Marketplace, creates the GitHub
   release and opens the changelog pull request. Nobody can push release tags by hand.
5. Merge the changelog pull request.

## `main` vs `eap` branch

Sometimes there can be API incompatibilities between the latest stable version
of Gateway and EAP ones (Early Access Program).

If this happens, develop the EAP changes on the `eap` branch. Releases are only cut from `main`,
so EAP work ships once it is merged to `main`, when that Gateway version becomes stable.

## Supported Coder versions

`Coder Gateway` includes checks for compatibility with a specified version
range. A warning is raised when the Coder deployment build version is outside of
compatibility range.

At the moment the upper range is 3.0.0 so the check essentially has no effect,
but in the future we may want to keep this updated.
