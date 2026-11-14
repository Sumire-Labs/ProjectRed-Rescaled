param([string[]]$GradleArguments = @(), [ValidateRange(1,600)][int]$ServerTimeoutSeconds = 180)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$javaExecutable = if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    (Get-Command java -CommandType Application -ErrorAction Stop).Source
} else {
    Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })
}
if (!(Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { throw "Java executable not found: $javaExecutable" }
function Run-Native([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Program exited with $LASTEXITCODE" }
}
function Invoke-Gradle([string]$ProjectPath, [string[]]$Arguments) {
    $wrapper = Join-Path $ProjectPath $(if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' })
    if (!$IsWindows) { Run-Native chmod @('+x', $wrapper) }
    Run-Native $wrapper (@('-p', $ProjectPath, '--console=plain', '--no-configuration-cache') + $Arguments)
}

Invoke-Gradle $repoRoot (@(':stageProductionSmoke') + $GradleArguments)
$server = Join-Path $repoRoot 'run/production-smoke'
New-Item -ItemType Directory -Path $server -Force | Out-Null
$loaderVersion = ((Get-Content (Join-Path $repoRoot 'gradle.properties') | Where-Object { $_ -match '^cleanroom_version\s*=' }) -split '=',2)[1].Trim()
$installer = Join-Path $server "cleanroom-$loaderVersion-installer.jar"
if (!(Test-Path -LiteralPath $installer)) {
    Invoke-WebRequest "https://github.com/CleanroomMC/Cleanroom/releases/download/$loaderVersion/cleanroom-$loaderVersion-installer.jar" -OutFile $installer
}
Run-Native $javaExecutable @('-jar', $installer, 'server', '--version', $loaderVersion, '--dir', $server, '--no-provision-java', '--memory', '2G', '--no-gui', '--yes')
$mods = Join-Path $server 'mods'
New-Item -ItemType Directory -Path $mods -Force | Out-Null
# Only remove jars in this fixed, isolated verification instance.
$resolvedMods = [IO.Path]::GetFullPath($mods)
if ($resolvedMods -ne [IO.Path]::GetFullPath((Join-Path $repoRoot 'run/production-smoke/mods'))) { throw 'Unexpected test instance path' }
Get-ChildItem -LiteralPath $resolvedMods -Filter '*.jar' -File | Remove-Item
Copy-Item (Join-Path $repoRoot 'build/production-smoke/mods/*.jar') $mods
[IO.File]::WriteAllText((Join-Path $server 'eula.txt'), "eula=true`n")
[IO.File]::WriteAllText((Join-Path $server 'server.properties'), "server-ip=127.0.0.1`nserver-port=0`nonline-mode=false`nlevel-type=FLAT`ngenerate-structures=false`nview-distance=3`n")
$result = Join-Path $server 'production-smoke-result.txt'
if (Test-Path -LiteralPath $result) { Remove-Item -LiteralPath $result }
$platform = if ($IsWindows) { 'win_args.txt' } else { 'unix_args.txt' }
Push-Location $server
try {
    $launch = [System.Diagnostics.ProcessStartInfo]::new()
    $launch.FileName = $javaExecutable
    $launch.WorkingDirectory = $server
    $launch.UseShellExecute = $false
    $launch.CreateNoWindow = $true
    foreach ($argument in @('-Dfml.queryResult=confirm', '-Dterminal.jline=false', "-Dprojectred.smoke.result=$result", '@user_jvm_args.txt', "@libraries/com/cleanroommc/cleanroom/$loaderVersion/$platform", 'nogui')) {
        $launch.ArgumentList.Add($argument)
    }
    $process = [System.Diagnostics.Process]::Start($launch)
    try {
        if (!$process.WaitForExit($ServerTimeoutSeconds*1000)) {
            if (!$process.HasExited) { $process.Kill($true); $process.WaitForExit() }
            throw "Production smoke server exceeded $ServerTimeoutSeconds seconds"
        }
        if ($process.ExitCode -ne 0) { throw "Production smoke server exited with $($process.ExitCode)" }
    } finally { $process.Dispose() }
} finally { Pop-Location }
if (!(Test-Path -LiteralPath $result) -or (Get-Content -LiteralPath $result -Raw).Trim() -ne 'PASS') { throw 'Installed distribution regression checks did not pass' }
