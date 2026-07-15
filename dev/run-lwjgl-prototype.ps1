$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if ($args.Count -gt 0) {
        mvn -q exec:java "-Dexec.args=$($args -join ' ')"
    } else {
        mvn -q exec:java
    }
} finally {
    Pop-Location
}
