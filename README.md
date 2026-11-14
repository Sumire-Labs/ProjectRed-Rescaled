# ProjectRed Rescaled

ProjectRed for Minecraft 1.12.2 on Cleanroom, Java 25 and Scala 3.10.
All ten logical mods are distributed in **one ProjectRed jar**. Existing `mrtjp.projectred`
packages, registry names and module IDs are retained. Do not install the original split
ProjectRed jars alongside this distribution.

## Runtime dependencies

- Java 25 and Cleanroom 0.7.14-alpha or newer
- [MrTJPCore Rescaled](https://github.com/Sumire-Labs/MrTJPCore-Rescaled)
- [CBMultipart Rescaled](https://github.com/Sumire-Labs/CBMultipart-Rescaled)
- [CodeChickenLib CRE](https://github.com/Refitbench/CodeChickenLib) 3.3.10 or newer
- A Scala 3.10 build of Scalar

**The dependency projects are being updated to Scala 3.10 separately.** The pinned
development versions in `gradle.properties` are not all published yet. Until their
releases are available, use the isolated verification procedure below. It builds
test copies without modifying adjacent developer checkouts. These copies are
integration fixtures, not dependency releases.

## Build and verify

The build uses [CleanroomDevEnv](https://github.com/Sumire-Labs/CleanroomDevEnv),
CleanroomGradle 0.23.0 and Gradle 9.9.0. Cleanroom userdev supplies the mappings.
Set `JAVA_HOME` to JDK 25 before starting Gradle.

With released dependencies configured:

```sh
./gradlew :build
./gradlew :runClient
./gradlew :runServer
```

On Windows use `.\gradlew.bat`. Use the root `:runClient` / `:runServer` tasks;
the internal modules are not independent mod distributions or development hosts.
Versions come from Git tags: `./gradlew -q printVersion` shows the current version.

For the current Scala 3.10 integration, run PowerShell 7 (`pwsh` on Linux):

```powershell
./.github/scripts/verify.ps1 -Production
```

This pins and builds dependency sources under `run/dependency-fixtures`, publishes
them to an isolated Maven repository there, builds a Scalar 3.10 fixture, verifies
the ProjectRed distribution,
runs development server checks, then repeats those checks using SRG distribution jars
on an independently installed Cleanroom server. It needs network access and accepts
the Minecraft EULA for these isolated servers. CI runs this command without release credentials.
Verification does not overwrite the normal Maven Local repository used by other projects.

To run graphical client checks after that command:

```powershell
$fixtures = Get-Content build/verification/fixtures.json | ConvertFrom-Json
./gradlew.bat :runClient -Pclient_smoke=true -Pcg.repos.enableLocal=true `
  "-Ptjpcore_version=$($fixtures.mrtjpcore)" "-Pfmp_version=$($fixtures.cbmultipart)" `
  "-Pscalar_jar=$($fixtures.scalar)" "-Dmaven.repo.local=$($fixtures.mavenRepository)" --no-configuration-cache
```

The client check creates an isolated flat world, renders all creative item variants,
opens/resizes the IC Workbench, performs a real client/server Project Bench craft and
checks that leaving range closes its window. Screenshots are saved under
`run/smoke-client/screenshots`; reports are under `build/verification`.

## Modules and artifacts

`api`, `core`, `transmission`, `integration`, `fabrication`, `illumination`,
`transportation`, `expansion`, `relocation`, `exploration` and `compatibility`
are Gradle subprojects. Shared assets and combined metadata live in
`src/main/resources`. Only the root project is published:

- `ProjectRed-Rescaled-<version>.jar`: SRG runtime distribution
- `ProjectRed-Rescaled-<version>-mcp.jar`: development output
- `ProjectRed-Rescaled-<version>-sources.jar`: all module sources

The runtime jar includes Scala `.tasty` metadata and the Relocation access transformer.
It does not embed MrTJPCore, CBMultipart, CCL, Scalar, ComputerCraft API stubs or test classes.
Maven coordinates are `mrtjp:ProjectRed-Rescaled:<version>`, with `dev` and `sources`
classifiers. Declare mod dependencies explicitly; build-only userdev and remapping
dependencies are intentionally absent from the POM.

## Releases

`release.yml` accepts a version tag, verifies the real configured dependencies,
builds and checks the runtime distribution, then publishes to enabled destinations.
It never substitutes verification fixtures for released dependencies.

| Destination | Properties | GitHub Actions secrets |
| --- | --- | --- |
| Maven | `publish_to_maven`, `maven_name`, `maven_url` | `MAVEN_USERNAME`, `MAVEN_PASSWORD` |
| CurseForge | `curseforge_project_id` | `CURSEFORGE_TOKEN` |
| Modrinth | `modrinth_project_id` | `MODRINTH_TOKEN` |

CF/MR IDs are initially blank. Add a matching version section to `CHANGELOG.md`
before tagging. `publish-maven.yml` retries Maven publication for an existing tag.
Normal builds and the verification workflow deploy nothing.

See [migration notes](docs/MIGRATION.md) for provenance, compatibility changes,
verification coverage and remaining work. Original ProjectRed is by MrTJP,
ChickenBones, covers1624 and contributors. ProjectRed is MIT licensed; the
CleanroomDevEnv template notice is retained in `gradle/TEMPLATE-LICENSE.md`.
