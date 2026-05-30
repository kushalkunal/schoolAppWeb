param(
  [string]$TOKEN,
  [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57"
)

$h = @{Authorization="Bearer $TOKEN"}
$BASE = "http://localhost:8081/api/v1/tenants/$TENANT/timetable/periods"

$periods = @(
  @{name="Morning Assembly";startTime="08:00:00";endTime="08:20:00";sortOrder=1;breakSlot=$true},
  @{name="Period 1";startTime="08:25:00";endTime="09:10:00";sortOrder=2;breakSlot=$false},
  @{name="Period 2";startTime="09:10:00";endTime="09:55:00";sortOrder=3;breakSlot=$false},
  @{name="Period 3";startTime="09:55:00";endTime="10:40:00";sortOrder=4;breakSlot=$false},
  @{name="Snack Break";startTime="10:40:00";endTime="11:00:00";sortOrder=5;breakSlot=$true},
  @{name="Period 4";startTime="11:00:00";endTime="11:45:00";sortOrder=6;breakSlot=$false},
  @{name="Period 5";startTime="11:45:00";endTime="12:30:00";sortOrder=7;breakSlot=$false},
  @{name="Lunch Break";startTime="12:30:00";endTime="13:15:00";sortOrder=8;breakSlot=$true},
  @{name="Period 6";startTime="13:15:00";endTime="14:00:00";sortOrder=9;breakSlot=$false},
  @{name="Period 7";startTime="14:00:00";endTime="14:45:00";sortOrder=10;breakSlot=$false},
  @{name="Period 8";startTime="14:45:00";endTime="15:30:00";sortOrder=11;breakSlot=$false}
)

$createdIds = @{}
foreach ($p in $periods) {
  $body = $p | ConvertTo-Json
  try {
    $r = Invoke-WebRequest $BASE -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
    $pd = ($r.Content|ConvertFrom-Json).data
    $createdIds[$p.name] = $pd.id
    Write-Host "Created: $($pd.name) [$($pd.startTime) - $($pd.endTime)] id=$($pd.id)"
  } catch {
    $rd=[System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "ERR $($p.name): $($rd.ReadToEnd())"
  }
}
Write-Host "`nAll periods created: $($createdIds.Count)"
$createdIds | ConvertTo-Json
