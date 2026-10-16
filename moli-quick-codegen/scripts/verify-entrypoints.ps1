param(
    [Parameter(Mandatory = $true)][string]$Config,
    [Parameter(Mandatory = $true)][string]$CliJar,
    [string]$WebBaseUri = 'http://127.0.0.1:8088',
    [string]$Version = '1.0.13-bate'
)
$ErrorActionPreference = 'Stop'
$configPath = (Resolve-Path -LiteralPath $Config).Path
$jarPath = (Resolve-Path -LiteralPath $CliJar).Path
$resultDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ('moli-codegen-entries-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $resultDirectory | Out-Null
$api = $WebBaseUri.TrimEnd('/') + '/moli-codegen/api'
$settings = Invoke-RestMethod -Uri ($api + '/config/import') -Method Post -ContentType 'text/plain; charset=utf-8' -Body ([System.IO.File]::ReadAllText($configPath))
$json = $settings | ConvertTo-Json -Depth 50 -Compress
$webZip = Join-Path $resultDirectory 'web.zip'
$cliZip = Join-Path $resultDirectory 'cli.zip'
$mavenZip = Join-Path $resultDirectory 'maven.zip'
Invoke-WebRequest -Uri ($api + '/export') -Method Post -ContentType 'application/json; charset=utf-8' -Body $json -OutFile $webZip | Out-Null
& java -jar $jarPath generate --config $configPath --zip $cliZip
if ($LASTEXITCODE -ne 0) { throw 'CLI generation failed' }
& mvn -B -ntp -N "com.bingchunmoli:moli-quick-codegen-maven-plugin:${Version}:generate" "-Dcodegen.config=$configPath" "-Dcodegen.zip=$mavenZip"
if ($LASTEXITCODE -ne 0) { throw 'Maven generation failed' }
$hashes = @($webZip, $cliZip, $mavenZip) | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }
if (($hashes | Select-Object -Unique).Count -ne 1) { throw "Entry points produced different ZIP content; inspect $resultDirectory" }
Write-Output "Web, CLI and Maven ZIP outputs match. Results: $resultDirectory"
