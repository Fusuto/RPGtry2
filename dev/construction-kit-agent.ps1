param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AgentArguments
)

$projectDirectory = Split-Path -Parent $PSScriptRoot
$classesDirectory = Join-Path $projectDirectory 'target/classes'
if (-not (Test-Path (Join-Path $classesDirectory 'org/main/tools/ConstructionKitAgentCli.class'))) {
    throw 'Compile first with: mvn -DskipTests compile'
}
$dependencyFile = Join-Path $projectDirectory 'target/agent-classpath.txt'
if (-not (Test-Path $dependencyFile)) {
    & mvn -q -f (Join-Path $projectDirectory 'pom.xml') dependency:build-classpath "-Dmdep.outputFile=$dependencyFile" '-Dmdep.includeScope=runtime'
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$classPath = $classesDirectory + [IO.Path]::PathSeparator + (Get-Content $dependencyFile -Raw).Trim()
& java '-Djava.awt.headless=true' '-cp' $classPath 'org.main.tools.ConstructionKitAgentCli' @AgentArguments
exit $LASTEXITCODE
