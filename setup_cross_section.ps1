param([string]$TOKEN, [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57")

$h = @{Authorization="Bearer $TOKEN"}
$base = "http://localhost:8081/api/v1/tenants/$TENANT/timetable/entries"

# Cross-section teacher assignments
# Scenario: A class teacher ALSO teaches in another section during free periods
# Ananya Singh (class teacher Class 1A) → teaches Class 3B, Period 4 (Mon-Fri)
# Deepa Nair   (class teacher Class 2A) → teaches Class 4A, Period 4 (Mon-Fri)
# Rohan Mehta  (class teacher Class 1B) → teaches Class 5A, Period 4 (Mon-Fri)

$ANANYA  = "e38bd555-29e6-4ab1-a725-f4d2687806ee"
$DEEPA   = "bea298a5-8f19-40ca-868b-5d5544c1e9bc"
$ROHAN   = "555e88ef-7ffc-4973-8590-4ee9c01e13b7"

$SEC_3B  = "52bddd2c-a6f0-406f-8faf-2af17c349965"  # Class 3B
$SEC_4A  = "5eed4526-e17f-45d5-b145-07a444ceb909"  # Class 4A
$SEC_5A  = "c1534e5c-c4e3-4ecf-bc37-adf00954a69a"  # Class 5A

$P4      = "56088684-b11d-4739-ba97-45a6991253f5"  # Period 4 (11:00-11:45)

$entries = @(
  # Ananya → Class 3B P4 Mon-Fri
  @{sectionId=$SEC_3B; periodId=$P4; dayOfWeek=1; teacherId=$ANANYA; label="Ananya→3B P4 Mon"},
  @{sectionId=$SEC_3B; periodId=$P4; dayOfWeek=2; teacherId=$ANANYA; label="Ananya→3B P4 Tue"},
  @{sectionId=$SEC_3B; periodId=$P4; dayOfWeek=3; teacherId=$ANANYA; label="Ananya→3B P4 Wed"},
  @{sectionId=$SEC_3B; periodId=$P4; dayOfWeek=4; teacherId=$ANANYA; label="Ananya→3B P4 Thu"},
  @{sectionId=$SEC_3B; periodId=$P4; dayOfWeek=5; teacherId=$ANANYA; label="Ananya→3B P4 Fri"},

  # Deepa → Class 4A P4 Mon-Fri
  @{sectionId=$SEC_4A; periodId=$P4; dayOfWeek=1; teacherId=$DEEPA; label="Deepa→4A P4 Mon"},
  @{sectionId=$SEC_4A; periodId=$P4; dayOfWeek=2; teacherId=$DEEPA; label="Deepa→4A P4 Tue"},
  @{sectionId=$SEC_4A; periodId=$P4; dayOfWeek=3; teacherId=$DEEPA; label="Deepa→4A P4 Wed"},
  @{sectionId=$SEC_4A; periodId=$P4; dayOfWeek=4; teacherId=$DEEPA; label="Deepa→4A P4 Thu"},
  @{sectionId=$SEC_4A; periodId=$P4; dayOfWeek=5; teacherId=$DEEPA; label="Deepa→4A P4 Fri"},

  # Rohan → Class 5A P4 Mon-Fri
  @{sectionId=$SEC_5A; periodId=$P4; dayOfWeek=1; teacherId=$ROHAN; label="Rohan→5A P4 Mon"},
  @{sectionId=$SEC_5A; periodId=$P4; dayOfWeek=2; teacherId=$ROHAN; label="Rohan→5A P4 Tue"},
  @{sectionId=$SEC_5A; periodId=$P4; dayOfWeek=3; teacherId=$ROHAN; label="Rohan→5A P4 Wed"},
  @{sectionId=$SEC_5A; periodId=$P4; dayOfWeek=4; teacherId=$ROHAN; label="Rohan→5A P4 Thu"},
  @{sectionId=$SEC_5A; periodId=$P4; dayOfWeek=5; teacherId=$ROHAN; label="Rohan→5A P4 Fri"}
)

$ok = 0; $conflict = 0; $err = 0

foreach ($e in $entries) {
  $body = @{sectionId=$e.sectionId; periodId=$e.periodId; dayOfWeek=$e.dayOfWeek; teacherId=$e.teacherId} | ConvertTo-Json
  try {
    $r = Invoke-WebRequest $base -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
    $ok++
    Write-Host "OK   $($e.label)"
  } catch {
    $rd = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    $msg = $rd.ReadToEnd() | ConvertFrom-Json
    if ($msg.errorCode -eq "VALIDATION_ERROR") {
      $conflict++
      Write-Host "CONF $($e.label) — $($msg.message)"
    } else {
      $err++
      Write-Host "ERR  $($e.label) — $($msg.message)"
    }
  }
}

Write-Host "`nCreated: $ok  Conflicts: $conflict  Errors: $err"

# Now verify conflict detection: try to assign Ananya to Class 2A Period 1 Mon
# (She already teaches Class 1A Period 1 Mon)
Write-Host "`n--- Conflict Detection Test ---"
$conflictBody = @{
  sectionId="64a5ca42-7480-46d2-83c1-9bd44a12dcb7"  # Class 2A
  periodId="f010685d-74f8-49f8-baac-55d9afc8f727"    # Period 1
  dayOfWeek=1
  teacherId=$ANANYA
} | ConvertTo-Json

try {
  $r2 = Invoke-WebRequest $base -Method POST -Headers $h -ContentType "application/json" -Body $conflictBody -UseBasicParsing
  Write-Host "UNEXPECTED OK - conflict was not detected!"
} catch {
  $rd2 = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
  $msg2 = $rd2.ReadToEnd() | ConvertFrom-Json
  Write-Host "CONFLICT CAUGHT: $($msg2.errorCode) - $($msg2.message)"
}
