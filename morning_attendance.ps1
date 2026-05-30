param([string]$TOKEN, [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57")

$h = @{Authorization="Bearer $TOKEN"}
$today = "2026-05-27"

# Section -> first student to mark LATE, rest PRESENT (reverse marking: only list non-PRESENT)
# Format: sectionId, studentId to mark LATE
$sectionAttendance = @(
  @{section="032ec61c-3373-4389-9169-16ae826c357a"; name="Class 1A"; late=@("a3e7bf69-06bd-44ab-8a65-32551ce75bb7")},
  @{section="c76ef353-16d9-4d5d-9785-0b3d74e0e641"; name="Class 1B"; absent=@("187ebf94-d897-40f3-b00c-4f51b6e1d24f")},
  @{section="64a5ca42-7480-46d2-83c1-9bd44a12dcb7"; name="Class 2A"; late=@("96a9eb22-a0d1-4cda-abe5-320d95b6229a")},
  @{section="8132caf5-0eff-4ed0-a9c8-2dfd501396be"; name="Class 2B"; absent=@("3942de85-bb6c-4100-a9be-611eeaa569fb")},
  @{section="42507005-df2c-434e-9e9c-706254aa938c"; name="Class 3A"; late=@("0b2d73e4-fe22-4b93-bd9c-8d9b7f01e667")},
  @{section="52bddd2c-a6f0-406f-8faf-2af17c349965"; name="Class 3B"; absent=@("4ebe5294-0a3e-4f9e-9539-1022bad56e68")},
  @{section="5eed4526-e17f-45d5-b145-07a444ceb909"; name="Class 4A"; late=@("cb9a7665-fce7-4081-b83f-b73580445eb5")},
  @{section="936669f4-0df0-432d-9f3e-18f2a3b51a59"; name="Class 4B"; absent=@("b16d6995-6631-452e-8aa1-530d6898873c")},
  @{section="c1534e5c-c4e3-4ecf-bc37-adf00954a69a"; name="Class 5A"; late=@("9be8664b-1971-47de-9f13-9d130f2b5103")},
  @{section="145c23e6-3725-4b71-a7d4-08604d2336c3"; name="Class 5B"; absent=@("ec7c72f6-0163-4b1a-b6c3-f87dcadf536b")},
  @{section="d67338d7-1375-4fee-82cf-2da22967f237"; name="Class 6A"; present=@()},
  @{section="1786f17f-6f20-4e73-824b-0dbd1bb6c7fa"; name="Class 6B"; late=@("821681a0-35bf-456d-ac9f-cff2e335e6eb")}
)

foreach ($sa in $sectionAttendance) {
  $entries = @()
  
  # Add LATE students
  if ($sa.ContainsKey('late')) {
    foreach ($sid in $sa.late) {
      $entries += @{studentId=$sid; status="LATE"}
    }
  }
  # Add ABSENT students
  if ($sa.ContainsKey('absent')) {
    foreach ($sid in $sa.absent) {
      $entries += @{studentId=$sid; status="ABSENT"}
    }
  }
  # Everyone else is implicitly PRESENT (reverse marking model)
  
  $bodyObj = @{date=$today; entries=$entries}
  $body = $bodyObj | ConvertTo-Json -Depth 3

  $url = "http://localhost:8081/api/v1/tenants/$TENANT/sections/$($sa.section)/attendance"
  
  try {
    $r = Invoke-WebRequest $url -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
    $result = ($r.Content | ConvertFrom-Json).data
    Write-Host "$($sa.name): OK - present=$($result.presentCount) absent=$($result.absentCount) late=$($result.lateCount)"
  } catch {
    $rd = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "$($sa.name): ERR $($rd.ReadToEnd())"
  }
}

Write-Host "`nMorning attendance complete for $today"
