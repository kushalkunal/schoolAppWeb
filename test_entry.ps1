param([string]$TOKEN, [string]$TENANT = "926c372c-139d-460d-83b1-1a80ef92db57")

$h = @{Authorization="Bearer $TOKEN"}
$BASE = "http://localhost:8081/api/v1/tenants/$TENANT/timetable/entries"

# Test one entry: Class 1A, Period 1, Monday (dayOfWeek=1), Ananya Singh
$body = '{"sectionId":"032ec61c-3373-4389-9169-16ae826c357a","periodId":"f010685d-74f8-49f8-baac-55d9afc8f727","dayOfWeek":1,"teacherId":"e38bd555-29e6-4ab1-a725-f4d2687806ee"}'

try {
    $r = Invoke-WebRequest $BASE -Method POST -Headers $h -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host "Status: $($r.StatusCode)"
    Write-Host "OK: $($r.Content)"
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode.value__)"
    $rd = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
    Write-Host "Body: $($rd.ReadToEnd())"
}
