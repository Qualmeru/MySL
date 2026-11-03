# Builds MySL.apk (your stops) and MySL-friend.apk (no favourites) using the tools in C:\tmp\android-tools.
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
Copy-Item app\build\outputs\apk\personal\debug\app-personal-debug.apk MySL.apk -Force
Copy-Item app\build\outputs\apk\friend\debug\app-friend-debug.apk MySL-friend.apk -Force
Write-Host "Built $PSScriptRoot\MySL.apk and $PSScriptRoot\MySL-friend.apk"
