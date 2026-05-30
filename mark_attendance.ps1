param([string]$TOKEN, [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57")

# Refresh token if needed
if (-not $TOKEN) {
  $resp = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/auth/password/login" -Method POST -ContentType "application/json" -Body '{"email":"teacher@vms.school","password":"Test@1234"}' -UseBasicParsing
  $TOKEN = ($resp.Content | ConvertFrom-Json).data.accessToken
}

$h = @{Authorization="Bearer $TOKEN"}
$BASE = "http://localhost:8081/api/v1/tenants/$TENANT"
$today = (Get-Date -Format "yyyy-MM-dd")

# Section IDs for Classes 1-6 A sections (all the A sections with students)
$sections = @(
  @{id="032ec61c-3373-4389-9169-16ae826c357a"; name="Class 1A"},
  @{id="c76ef353-16d9-4d5d-9785-0b3d74e0e641"; name="Class 1B"},
  @{id="64a5ca42-7480-46d2-83c1-9bd44a12dcb7"; name="Class 2A"},
  @{id="8132caf5-0eff-4ed0-a9c8-2dfd501396be"; name="Class 2B"},
  @{id="42507005-df2c-434e-9e9c-706254aa938c"; name="Class 3A"},
  @{id="52bddd2c-a6f0-406f-8faf-2af17c349965"; name="Class 3B"},
  @{id="5eed4526-e17f-45d5-b145-07a444ceb909"; name="Class 4A"},
  @{id="936669f4-0df0-432d-9f3e-18f2a3b51a59"; name="Class 4B"},
  @{id="c1534e5c-c4e3-4ecf-bc37-adf00954a69a"; name="Class 5A"},
  @{id="145c23e6-3725-4b71-a7d4-08604d2336c3"; name="Class 5B"},
  @{id="d67338d7-1375-4fee-82cf-2da22967f237"; name="Class 6A"},
  @{id="1786f17f-6f20-4e73-824b-0dbd1bb6c7fa"; name="Class 6B"}
)

foreach ($section in $sections) {
  # Get students in this section
  $studentsResp = Invoke-WebRequest "$BASE/attendance/$($section.id)/students?date=$today" -Headers $h -UseBasicParsing
  $students = ($studentsResp.Content | ConvertFrom-Json).data
  Write-Host "$($section.name): $($students.Count) students"
  
  if ($students.Count -eq 0) {
    Write-Host "  (no students, skipping)"
    continue
  }
  
  # Build attendance records: mark 1st student ABSENT, rest PRESENT
  $records = @()
  $i = 0
  foreach ($stu in $students) {
    $status = if ($i -eq 0) { "ABSENT" } else { "PRESENT" }
    $records += @{ studentId = $stu.studentId; status = $status }
    $i++
  }
  
  $body = @{ date = $today; records = $records } | ConvertTo-Json -Depth 3
  
  try {
    $r = Invoke-WebRequest "$BASE/attendance/$($section.id)" -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host "  OK Submitted: $($records.Count) records (1 absent, $($records.Count - 1) present)"
  } catch {
    $rd = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "  ERR: $($rd.ReadToEnd())"
  }
}

Write-Host "`nAll attendance submitted for $today"
