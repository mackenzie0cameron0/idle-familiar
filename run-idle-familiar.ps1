# Quick launch for the Idle Familiar plugin — runs RuneLite in developer mode
# with the plugin loaded, WITHOUT Gradle (compiles with javac + cached jars).
# Use when `gradlew run` misbehaves. Run from anywhere:
#   powershell -ExecutionPolicy Bypass -File run-idle-familiar.ps1
$ErrorActionPreference = "Stop"

$Proj   = $PSScriptRoot
$Cache  = Join-Path $env:USERPROFILE ".gradle\caches\modules-2"
$Out    = Join-Path $Proj "build\launch"
$Res    = Join-Path $Proj "src\main\resources"

# --- JDK (need 17+ for the cached RuneLite; the gradle-managed Adoptium 21 fits) ---
$JdkHome = $env:JAVA_HOME
if (-not $JdkHome -or -not (Test-Path (Join-Path $JdkHome "bin\javac.exe"))) {
    $JdkHome = "C:\Users\BWAmackenzie\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
}
$Javac = Join-Path $JdkHome "bin\javac.exe"
$Java  = Join-Path $JdkHome "bin\java.exe"
if (-not (Test-Path $Javac)) { throw "javac not found. Set JAVA_HOME to a JDK 17+ install." }

# --- Classpath: every cached dependency jar (prefer 1.12.28 / newer dupes) ---
$Jars = Get-ChildItem -Path $Cache -Recurse -Filter *.jar |
    Where-Object {
        $_.Name -notlike "*-sources.jar" -and
        $_.Name -notlike "*-javadoc.jar" -and
        $_.FullName -notlike "*1.12.27*" -and
        $_.Name -ne "gson-2.8.5.jar" -and
        $_.Name -ne "guava-23.2-jre.jar" -and
        $_.Name -ne "slf4j-api-1.7.25.jar"
    } | Select-Object -ExpandProperty FullName
if (-not $Jars) { throw "No RuneLite dependency jars in $Cache. Run gradlew once to populate the cache." }
$Cp = ($Jars -join ";")

# --- Compile main + test sources (test holds the launcher main class) ---
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Sources = Get-ChildItem -Path (Join-Path $Proj "src") -Recurse -Filter *.java |
    Select-Object -ExpandProperty FullName

Write-Host "Compiling $($Sources.Count) sources with JDK at $JdkHome ..."
$env:CLASSPATH = $Cp
& $Javac -encoding UTF-8 --release 11 -proc:none -d $Out @Sources
if ($LASTEXITCODE -ne 0) { throw "Compile failed." }

# --- Launch RuneLite (developer mode) with the plugin as a builtin ---
Write-Host "Launching RuneLite + Idle Familiar (developer mode) ..."
$env:CLASSPATH = "$Res;$Out;$Cp"
& $Java -ea com.idlefamiliar.IdleFamiliarPluginTest --developer-mode --debug
