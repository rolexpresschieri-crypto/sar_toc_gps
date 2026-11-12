param(
  [Parameter(Mandatory = $true)][string]$EnvFile
)

$ErrorActionPreference = "Stop"

function Read-EnvFile([string]$Path) {
  $map = @{}
  Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) { return }
    $eq = $line.IndexOf("=")
    if ($eq -lt 1) { return }
    $key = $line.Substring(0, $eq).Trim()
    $val = $line.Substring($eq + 1).Trim()
    if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
      $val = $val.Substring(1, $val.Length - 2)
    }
    $map[$key] = $val
  }
  return $map
}

function Invoke-Supabase {
  param(
    [string]$Method,
    [string]$Url,
    [hashtable]$Headers,
    [object]$Body = $null,
    [string]$Prefer = $null
  )
  $hdr = @{} + $Headers
  if ($Prefer) { $hdr["Prefer"] = $Prefer }
  $params = @{
    Method      = $Method
    Uri         = $Url
    Headers     = $hdr
    ContentType = "application/json"
  }
  if ($null -ne $Body) {
    $params["Body"] = ($Body | ConvertTo-Json -Compress -Depth 5)
  }
  try {
    return Invoke-RestMethod @params
  } catch {
    $resp = $_.Exception.Response
    $detail = $_.ErrorDetails.Message
    if (-not $detail -and $resp) {
      try {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $detail = $reader.ReadToEnd()
        $reader.Close()
      } catch { }
    }
    $code = "?"
    if ($resp) { $code = [int]$resp.StatusCode }
    if (-not $detail) { $detail = $_.Exception.Message }
    throw ("HTTP {0}: {1}" -f $code, $detail)
  }
}

$OrgCode = [string]$env:TOC_ORG_CODE
$OrgName = [string]$env:TOC_ORG_NAME
$AdminCode = [string]$env:TOC_ADMIN_CODE
$AdminName = [string]$env:TOC_ADMIN_NAME
$AdminPassword = [string]$env:TOC_ADMIN_PWD

if (-not $OrgCode.Trim() -or -not $AdminCode.Trim() -or -not $AdminPassword) {
  Write-Host "ERRORE: variabili TOC_ORG_CODE / TOC_ADMIN_CODE / TOC_ADMIN_PWD mancanti (lanciami da nuovo-ente.bat)."
  exit 1
}

$envMap = Read-EnvFile $EnvFile
$baseUrl = ""
$anonKey = ""
if ($envMap.ContainsKey("NEXT_PUBLIC_SUPABASE_URL")) { $baseUrl = [string]$envMap["NEXT_PUBLIC_SUPABASE_URL"] }
if ($envMap.ContainsKey("NEXT_PUBLIC_SUPABASE_ANON_KEY")) { $anonKey = [string]$envMap["NEXT_PUBLIC_SUPABASE_ANON_KEY"] }
$baseUrl = $baseUrl.Trim().TrimEnd("/")
$anonKey = $anonKey.Trim()
if (-not $baseUrl -or -not $anonKey) {
  Write-Host "ERRORE: NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY mancanti in $EnvFile"
  exit 1
}

$orgCode = $OrgCode.Trim().ToUpperInvariant()
$orgName = $OrgName.Trim()
if (-not $orgName) { $orgName = $orgCode }
$adminCode = $AdminCode.Trim().ToUpperInvariant()
$adminName = $AdminName.Trim()
if (-not $adminName) { $adminName = $adminCode }
$adminPwd = $AdminPassword

if ($orgCode -notmatch '^[A-Z0-9_]+$') {
  Write-Host "ERRORE: org_code deve essere A-Z / 0-9 / _ (senza spazi)."
  exit 1
}
if (-not $adminCode.StartsWith("ADMIN_")) {
  Write-Host "ERRORE: il codice admin deve iniziare con ADMIN_ (es. ADMIN_RT)."
  exit 1
}
if ($adminCode -notmatch '^[A-Z0-9_]+$') {
  Write-Host "ERRORE: admin_code deve essere A-Z / 0-9 / _."
  exit 1
}

$headers = @{
  "apikey"        = $anonKey
  "Authorization" = "Bearer $anonKey"
  "Accept"        = "application/json"
}

Write-Host "Ente:  $orgCode / $orgName"
Write-Host "Admin: $adminCode / $adminName"
Write-Host ""

try {
  $orgFilter = [uri]::EscapeDataString("eq.$orgCode")
  $existing = Invoke-Supabase -Method GET `
    -Url "$baseUrl/rest/v1/organizations?org_code=$orgFilter&select=id,org_code,org_name,is_enabled" `
    -Headers $headers

  $orgId = $null
  $existingList = @($existing)
  if ($existingList.Count -ge 1) {
    $orgId = [string]$existingList[0].id
    Write-Host "Ente gia presente (id $orgId) — riuso."
  } else {
    $created = Invoke-Supabase -Method POST `
      -Url "$baseUrl/rest/v1/organizations" `
      -Headers $headers `
      -Prefer "return=representation" `
      -Body @{
        org_code   = $orgCode
        org_name   = $orgName
        is_enabled = $true
      }
    $createdList = @($created)
    if ($createdList.Count -lt 1) {
      Write-Host "ERRORE: insert organizations senza risposta."
      exit 1
    }
    $orgId = [string]$createdList[0].id
    Write-Host "Ente creato (id $orgId)."
  }

  $adminFilter = [uri]::EscapeDataString("eq.$adminCode")
  $orgIdFilter = [uri]::EscapeDataString("eq.$orgId")
  $adminExisting = Invoke-Supabase -Method GET `
    -Url "$baseUrl/rest/v1/toc_admins?organization_id=$orgIdFilter&admin_code=$adminFilter&select=id,admin_code" `
    -Headers $headers

  $adminExistingList = @($adminExisting)
  if ($adminExistingList.Count -ge 1) {
    Write-Host "ERRORE: $adminCode esiste gia per questo ente (id $($adminExistingList[0].id))."
    Write-Host "        Aggiorna da TOC > Utenti oppure cambia codice."
    exit 1
  }

  $adminRow = Invoke-Supabase -Method POST `
    -Url "$baseUrl/rest/v1/toc_admins" `
    -Headers $headers `
    -Prefer "return=representation" `
    -Body @{
      admin_code      = $adminCode
      admin_name      = $adminName
      password_hash   = $adminPwd
      role            = "admin"
      is_enabled      = $true
      organization_id = $orgId
    }

  $adminList = @($adminRow)
  if ($adminList.Count -lt 1) {
    Write-Host "ERRORE: insert toc_admins senza risposta."
    exit 1
  }

  Write-Host "Admin creato (id $($adminList[0].id))."
  Write-Host ""
  Write-Host "Login TOC web:"
  Write-Host "  Ente:     $orgCode"
  Write-Host "  Codice:   $adminCode"
  Write-Host "  Password: (quella inserita)"
  exit 0
} catch {
  Write-Host "ERRORE: $($_.Exception.Message)"
  exit 1
}
