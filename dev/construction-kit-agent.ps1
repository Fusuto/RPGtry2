param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AgentArguments
)

$projectDirectory = Split-Path -Parent $PSScriptRoot
$classesDirectory = Join-Path $projectDirectory 'target/classes'
if (-not (Test-Path (Join-Path $classesDirectory 'org/main/tools/ConstructionKitAgentCli.class'))) {
    throw 'Compile first with: mvn -DskipTests compile'
}
& java '-Djava.awt.headless=true' '-cp' $classesDirectory 'org.main.tools.ConstructionKitAgentCli' @AgentArguments
exit $LASTEXITCODE
