param([switch]$Production)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$fixtureRoot = Join-Path $repoRoot 'run/dependency-fixtures'
$verificationMaven = Join-Path $fixtureRoot 'maven'
New-Item -ItemType Directory -Path $fixtureRoot -Force | Out-Null

function Run-Native([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Program exited with $LASTEXITCODE" }
}
function Checkout-Fixture([string]$Name, [string]$Repository, [string]$Commit) {
    $target = Join-Path $fixtureRoot $Name
    if (!(Test-Path -LiteralPath (Join-Path $target '.git'))) {
        Run-Native git @('clone', '--filter=blob:none', '--no-checkout', "https://github.com/$Repository.git", $target)
        Run-Native git @('-C', $target, 'checkout', '--detach', $Commit)
    }
    $actual = (& git -C $target rev-parse HEAD).Trim()
    if ($actual -ne $Commit) { throw "Unexpected revision in fixture $target; expected $Commit" }
    return $target
}
function Invoke-Gradle([string]$ProjectPath, [string[]]$Arguments) {
    $wrapper = Join-Path $ProjectPath $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    if (!$IsWindows) { Run-Native chmod @('+x', $wrapper) }
    Run-Native $wrapper (@('-p', $ProjectPath, '--console=plain', '--no-configuration-cache', "-Dmaven.repo.local=$verificationMaven") + $Arguments)
}
function Get-Version([string]$ProjectPath) {
    $wrapper = Join-Path $ProjectPath $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    $lines = & $wrapper -p $ProjectPath -q printVersion --no-configuration-cache "-Dmaven.repo.local=$verificationMaven"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read fixture version' }
    return ($lines | Select-Object -Last 1).Trim()
}

# Pinned sources make CI independent of unreleased Scala 3.10 dependency publications.
# These copies are fixtures only; no changes are made to adjacent developer checkouts.
$core = Checkout-Fixture 'mrtjpcore' 'Sumire-Labs/MrTJPCore-Rescaled' '9563657719ed3deb9c72895517902caf84ec9244'
$multipart = Checkout-Fixture 'cbmultipart' 'Sumire-Labs/CBMultipart-Rescaled' '84bf6b7b1a56801ff093425cde5bdf051ed1e9a6'
$scalar = Checkout-Fixture 'scalar' 'CleanroomMC/Scalar' 'd5f7c8fad448d7a3d0ce6674e236038ea4eab7ef'
$multipartMod = Join-Path $multipart 'src/main/scala/codechicken/multipart/handler/MultipartMod.scala'
$multipartText = [IO.File]::ReadAllText($multipartMod)
if (!$multipartText.Contains('scalar@[3.9.0,3.10.0)') -and !$multipartText.Contains('scalar@[3.10.0,3.11.0)')) {
    throw 'The fixture dependency declaration changed; review its Scala compatibility before proceeding.'
}
[IO.File]::WriteAllText($multipartMod, $multipartText.Replace('scalar@[3.9.0,3.10.0)', 'scalar@[3.10.0,3.11.0)'))
Invoke-Gradle $core @('publishToMavenLocal', '-Ppublish_to_local_maven=true', '-Pscala_version=3.10.0')
Invoke-Gradle $multipart @('publishToMavenLocal', '-Ppublish_to_local_maven=true', '-Pscala_version=3.10.0')
$coreVersion = Get-Version $core
$multipartVersion = Get-Version $multipart
$properties = @('-Pcg.repos.enableLocal=true', "-Dmaven.repo.local=$verificationMaven", "-Ptjpcore_version=$coreVersion", "-Pfmp_version=$multipartVersion")
Invoke-Gradle $repoRoot (@(':scalarFixture', "-Pverification_scalar_source=$scalar") + $properties)
$scalarJar = Join-Path $repoRoot 'build/verification/scalar/scalar-3.10.0-fixture.jar'
$properties += "-Pscalar_jar=$scalarJar"
$reportDirectory = Join-Path $repoRoot 'build/verification'
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
@{ mrtjpcore=$coreVersion; cbmultipart=$multipartVersion; scalar=$scalarJar; mavenRepository=$verificationMaven; testDependenciesOnly=$true } |
    ConvertTo-Json | Set-Content (Join-Path $reportDirectory 'fixtures.json')
Invoke-Gradle $repoRoot (@(':build', ':runServer', '-Psmoke_test=true') + $properties)

if ($Production) { & (Join-Path $PSScriptRoot 'production-smoke.ps1') -GradleArguments $properties }
