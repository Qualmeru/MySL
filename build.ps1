# Builds build\MySL.apk (no favourites) and build\MySL-preset.apk (preset stops) using the tools in C:\tmp\android-tools.
# TEMP is moved off the 8.3 short path (C:\Users\SIMONJ~1\...) because JDK 17
# fails to open its internal sockets there ("Unable to establish loopback connection").
# The low-memory JVM flags keep the build from crashing when RAM is tight.
$ErrorActionPreference = 'Stop'
$tools = 'C:\tmp\android-tools'
$env:JAVA_HOME = "$tools\jdk17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:TEMP = "$tools\tmp"; $env:TMP = $env:TEMP

Set-Location $PSScriptRoot
# These APKs get shared, so they must carry the release key or they won't update existing installs.
if (-not (Test-Path keystore.properties)) {
    throw "keystore.properties is missing, so the APKs can't be signed with the release key. See README, 'Signing'."
}
.\gradlew.bat assembleStandardRelease assemblePresetRelease --no-daemon --max-workers=1 `
    "-Dorg.gradle.jvmargs=-Xmx900m -XX:MaxMetaspaceSize=512m -XX:ReservedCodeCacheSize=96m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:+UseSerialGC -Dfile.encoding=UTF-8" `
    "-Pkotlin.compiler.execution.strategy=in-process"
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
New-Item -ItemType Directory -Force build | Out-Null
Copy-Item app\build\outputs\apk\standard\release\app-standard-release.apk build\MySL.apk -Force
Copy-Item app\build\outputs\apk\preset\release\app-preset-release.apk build\MySL-preset.apk -Force
Write-Host "Built $PSScriptRoot\build\MySL.apk and $PSScriptRoot\build\MySL-preset.apk"
