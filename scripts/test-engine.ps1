param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskClasses = Join-Path $taskProject 'build/engine-tests'
New-Item -ItemType Directory -Force -Path $taskClasses | Out-Null
$taskJavac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$taskJava = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$taskSources = @(
    (Join-Path $taskProject 'app/src/main/java/com/codetone/impro/MusicTheory.java'),
    (Join-Path $taskProject 'app/src/main/java/com/codetone/impro/AudioRenderer.java'),
    (Join-Path $taskProject 'app/src/main/java/com/codetone/impro/ProgressionEditor.java'),
    (Join-Path $taskProject 'tests/EngineTest.java')
)
& $taskJavac -encoding UTF-8 -d $taskClasses @taskSources
if ($LASTEXITCODE -ne 0) { throw 'Engine compilation failed.' }
& $taskJava -cp $taskClasses com.codetone.impro.EngineTest
if ($LASTEXITCODE -ne 0) { throw 'Engine tests failed.' }
