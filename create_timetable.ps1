param(
  [string]$TOKEN,
  [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57"
)

$h = @{Authorization="Bearer $TOKEN"}
$BASE = "http://localhost:8081/api/v1/tenants/$TENANT/timetable/entries"

# Section ID -> Teacher ID mapping
$assignments = @{
  "032ec61c-3373-4389-9169-16ae826c357a" = "e38bd555-29e6-4ab1-a725-f4d2687806ee"  # Class 1A -> Ananya
  "c76ef353-16d9-4d5d-9785-0b3d74e0e641" = "555e88ef-7ffc-4973-8590-4ee9c01e13b7"  # Class 1B -> Rohan
  "64a5ca42-7480-46d2-83c1-9bd44a12dcb7" = "bea298a5-8f19-40ca-868b-5d5544c1e9bc"  # Class 2A -> Deepa
  "8132caf5-0eff-4ed0-a9c8-2dfd501396be" = "10754f04-b8a2-43c2-93e5-e6eda59fa7c5"  # Class 2B -> Suresh
  "42507005-df2c-434e-9e9c-706254aa938c" = "eda534f4-9aa1-4621-a9a4-bedf709947e4"  # Class 3A -> Meera
  "52bddd2c-a6f0-406f-8faf-2af17c349965" = "c280903c-9d05-429a-a5a8-0e03e6f8a2cb"  # Class 3B -> Arjun
  "5eed4526-e17f-45d5-b145-07a444ceb909" = "0b8b1b81-523e-4745-a08f-cd2dbb3902e9"  # Class 4A -> Lakshmi
  "936669f4-0df0-432d-9f3e-18f2a3b51a59" = "619ed001-494f-4aee-be10-fedaee0325e7"  # Class 4B -> Priya
  "c1534e5c-c4e3-4ecf-bc37-adf00954a69a" = "e38bd555-29e6-4ab1-a725-f4d2687806ee"  # Class 5A -> Ananya
  "145c23e6-3725-4b71-a7d4-08604d2336c3" = "555e88ef-7ffc-4973-8590-4ee9c01e13b7"  # Class 5B -> Rohan
  "d67338d7-1375-4fee-82cf-2da22967f237" = "619ed001-494f-4aee-be10-fedaee0325e7"  # Class 6A -> Priya
  "1786f17f-6f20-4e73-824b-0dbd1bb6c7fa" = "eda534f4-9aa1-4621-a9a4-bedf709947e4"  # Class 6B -> Meera
}

# Period IDs for class periods (not breaks)
$periodIds = @{
  "Period 1" = "f010685d-74f8-49f8-baac-55d9afc8f727"
  "Period 2" = "9b2432d8-41e2-4d77-8a89-1e48b09a0622"
  "Period 3" = "16ae2d6f-c942-489d-b28b-d5326aa6bd43"
}

# Subject names for variety
# dayOfWeek: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri
$days = @(1,2,3,4,5)

$created = 0
$errors = 0

foreach ($sectionId in $assignments.Keys) {
  $teacherId = $assignments[$sectionId]
  
  foreach ($day in $days) {
    foreach ($periodName in @("Period 1","Period 2","Period 3")) {
      $periodId = $periodIds[$periodName]
      
      $body = "{`"sectionId`":`"$sectionId`",`"periodId`":`"$periodId`",`"dayOfWeek`":$day,`"teacherId`":`"$teacherId`"}"
      
      try {
        $r = Invoke-WebRequest $BASE -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
        $created++
      } catch {
        $rd = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $errMsg = $rd.ReadToEnd()
        # Skip conflict errors (teacher in two sections) - expected for shared teachers
        if ($errMsg -notmatch "already assigned") {
          Write-Host "ERR $sectionId day=$day $periodName : $errMsg"
          $errors++
        }
      }
    }
  }
}

Write-Host "Timetable entries created: $created (errors: $errors)"
