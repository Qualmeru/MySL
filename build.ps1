# Builds build\MySL.apk (your stops) and build\MySL-friend.apk (no favourites) using the tools in C:\tmp\android-tools.
# TEMP is moved off the 8.3 short path (C:\Users\SIMONJ~1\...) because JDK 17
# fails to open its internal sockets there ("Unable to establish loopback connection").
# The low-memory JVM flags keep the build from crashing when RAM is tight.
$ErrorActionPreference = 'Stop'
$tools = 'C:\tmp\android-tools'
$env:JAVA_HOME = "$tools\jdk17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:TEMP = "$tools\tmp"; $env:TMP = $env:TEMP

Set-Location $PSScriptRoot
.\gradlew.bat assemblePersonalDebug assembleFriendDebug --no-daemon --max-workers=1 `
    "-Dorg.gradle.jvmargs=-Xmx900m -XX:MaxMetaspaceSize=320m -XX:ReservedCodeCacheSize=96m -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1 -XX:+UseSerialGC -Dfile.encoding=UTF-8" `
    "-Pkotlin.compiler.execution.strategy=in-process"
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
New-Item -ItemType Directory -Force build | Out-Null
Copy-Item app\build\outputs\apk\personal\debug\app-personal-debug.apk build\MySL.apk -Force
Copy-Item app\build\outputs\apk\friend\debug\app-friend-debug.apk build\MySL-friend.apk -Force
Write-Host "Built $PSScriptRoot\build\MySL.apk and $PSScriptRoot\build\MySL-friend.apk"
