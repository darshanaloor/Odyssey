# Test error scenarios and parse endpoint

Write-Host "=== Test 1: POST /api/trips/TRIP-DEMO-001/approve when already RESOLVED (expect 409) ==="
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/approve" -Method Post -Body '{"optionId":"OPT-SAFEST-INV-TR-001"}' -ContentType "application/json"
    Write-Host "Unexpected success:" $res
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Status Code:" $statusCode
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host "Body:" $reader.ReadToEnd()
}

Write-Host "`n=== Test 2: POST /api/trips/TRIP-DEMO-001/plan with unknown optionId on DISRUPTED trip (expect 404) ==="
Invoke-RestMethod -Uri "http://localhost:8085/api/demo/reset" -Method Post | Out-Null
$dis = @{ bookingId="BKG-101"; type="CANCELLATION"; reason="test" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/disruptions" -Method Post -Body $dis -ContentType "application/json" | Out-Null
Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/recover" -Method Post | Out-Null
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/plan" -Method Post -Body '{"optionId":"OPT-DOES-NOT-EXIST"}' -ContentType "application/json"
    Write-Host "Unexpected success:" $res
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Status Code:" $statusCode
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host "Body:" $reader.ReadToEnd()
}

Write-Host "`n=== Test 3: POST /api/trips/NON-EXISTENT/plan (expect 404) ==="
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/NON-EXISTENT/plan" -Method Post -Body '{"optionId":"OPT-SAFEST-INV-TR-001"}' -ContentType "application/json"
    Write-Host "Unexpected success:" $res
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Status Code:" $statusCode
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host "Body:" $reader.ReadToEnd()
}

Write-Host "`n=== Test 4: POST /api/trips/TRIP-DEMO-001/plan with missing optionId (expect 400) ==="
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/plan" -Method Post -Body '{}' -ContentType "application/json"
    Write-Host "Unexpected success:" $res
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Status Code:" $statusCode
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host "Body:" $reader.ReadToEnd()
}

Write-Host "`n=== Test 5: POST /api/trips/TRIP-DEMO-001/parse ==="
$parseBody = @{
    text = "Flight Confirmation:`nAirline: Air India AI-671`nFrom: BLR`nTo: IXE`nDeparture: 2026-10-21 11:30`nArrival: 2026-10-21 12:45`nFare: Rs 5200.00"
} | ConvertTo-Json
$parse = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/parse" -Method Post -Body $parseBody -ContentType "application/json"
Write-Host "Parse success:" $parse.success $parse.message
Write-Host "Extracted bookings count:" $parse.data.Count
foreach ($eb in $parse.data) {
    Write-Host " - Extracted:" $eb.type $eb.provider $eb.from "->" $eb.to $eb.startTime "Cost: Rs" $eb.cost
}
