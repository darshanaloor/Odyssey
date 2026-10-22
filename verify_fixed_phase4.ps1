# Comprehensive verification script for Phases 3 and 4 fixes

Write-Host "=========================================================="
Write-Host "1. RESET DEMO TRIP: POST /api/demo/reset"
Write-Host "=========================================================="
$reset = Invoke-RestMethod -Uri "http://localhost:8085/api/demo/reset" -Method Post
Write-Host "Reset success:" $reset.success "-" $reset.message
Write-Host "Trip traveller:" $reset.data.travellerName "(" $reset.data.travellerPhone ")"

Write-Host "`n=========================================================="
Write-Host "2. INJECT CANCELLATION: POST /api/trips/TRIP-DEMO-001/disruptions"
Write-Host "=========================================================="
$disBody = @{
    bookingId = "BKG-101"
    type = "CANCELLATION"
    reason = "IndiGo 6E-534 flight cancelled due to technical reasons"
    # disruptionTime omitted so it defaults to recovery.demoDisruptionTime (2026-10-21T09:00:00)
} | ConvertTo-Json
$disruption = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/disruptions" -Method Post -Body $disBody -ContentType "application/json"
Write-Host "Disruption recorded:" $disruption.success "-" $disruption.message
Write-Host "Notification Recipient:" $disruption.data.notification.recipient
Write-Host "Notification Message Sample:"
Write-Host $disruption.data.notification.message

Write-Host "`n=========================================================="
Write-Host "3. CALL /recover TWICE (IDEMPOTENCY CHECK)"
Write-Host "=========================================================="
$recover1 = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/recover" -Method Post
$recover2 = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/recover" -Method Post

$dataJson1 = $recover1.data | ConvertTo-Json -Depth 6
$dataJson2 = $recover2.data | ConvertTo-Json -Depth 6
$isDataIdentical = ($dataJson1 -eq $dataJson2)
Write-Host "Are the two /recover data payloads identical (idempotent)?" $isDataIdentical
Write-Host "(Outer envelope timestamp differs by design: $($recover1.timestamp) vs $($recover2.timestamp))"

Write-Host "`n=========================================================="
Write-Host "4. OPTIONS TABLE"
Write-Host "=========================================================="
Write-Host ("{0,-10} | {1,-35} | {2,-16} | {3,-16} | {4,-10} | {5,-6}" -f "Label", "Provider", "Departure", "Arrival", "Cost", "Score")
Write-Host ("-" * 105)
foreach ($opt in $recover1.data.options) {
    $leg = $opt.legs[0]
    Write-Host ("{0,-10} | {1,-35} | {2,-16} | {3,-16} | Rs {4,-7} | {5,-6}" -f `
        $opt.label, $leg.provider, $leg.startTime, $opt.arrivalTime, $opt.totalCost, $opt.score)
}

Write-Host "`n=========================================================="
Write-Host "5. REJECTED OPTIONS WITH REASONS"
Write-Host "=========================================================="
Write-Host ("{0,-12} | {1,-8} | {2,-38} | {3}" -f "ID", "Mode", "Provider", "Reason")
Write-Host ("-" * 125)
foreach ($rej in $recover1.data.rejectedOptions) {
    Write-Host ("{0,-12} | {1,-8} | {2,-38} | {3}" -f $rej.id, $rej.mode, $rej.provider, $rej.reason)
}

Write-Host "`n=========================================================="
Write-Host "6. PLAN WITH SAFEST OPTION: POST /api/trips/TRIP-DEMO-001/plan"
Write-Host "=========================================================="
$safestOpt = ($recover1.data.options | Where-Object { $_.label -eq "SAFEST" })[0]
$planBody = @{
    optionId = $safestOpt.id
} | ConvertTo-Json
$plan = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/plan" -Method Post -Body $planBody -ContentType "application/json"
Write-Host "Plan generated for:" $safestOpt.id "(" $safestOpt.label ")"

Write-Host "`n--- Itinerary Diff in Plan ---"
foreach ($d in $plan.data.itineraryDiff) {
    Write-Host ("Booking {0} [{1}] {2}: {3} {4} -> {5} {6}" -f $d.bookingId, $d.type, $d.provider, $d.oldStatus, $d.oldStart, $d.newStatus, $d.newStart)
    Write-Host ("   Details: {0}" -f $d.changeDescription)
}

Write-Host "`n--- Action Checklist Order in Plan ---"
for ($i = 0; $i -lt $plan.data.checklist.Count; $i++) {
    $c = $plan.data.checklist[$i]
    $mark = if ($c.done) { "[X]" } else { "[ ]" }
    Write-Host ("   {0}. {1} {2,-22} : {3}" -f ($i + 1), $mark, $c.id, $c.text)
}

Write-Host "`n--- Hotel Message in Plan ---"
Write-Host $plan.data.hotelMessage

Write-Host "`n--- Trade-off Explanation in Plan ---"
Write-Host $plan.data.tradeOffExplanation

Write-Host "`n=========================================================="
Write-Host "7. APPROVE SAFEST OPTION: POST /api/trips/TRIP-DEMO-001/approve"
Write-Host "=========================================================="
$approveBody = @{
    optionId = $safestOpt.id
} | ConvertTo-Json
$approve = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/approve" -Method Post -Body $approveBody -ContentType "application/json"
Write-Host "Approve response:" $approve.success "-" $approve.message

Write-Host "`n=========================================================="
Write-Host "8. FINAL TRIP STATE: GET /api/trips/TRIP-DEMO-001"
Write-Host "=========================================================="
$trip = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001" -Method Get
Write-Host "Final Trip Status:" $trip.data.status
Write-Host "Traveller:" $trip.data.travellerName "(" $trip.data.travellerPhone ")"

Write-Host "`n--- Final Booking Statuses ---"
foreach ($b in $trip.data.bookings) {
    Write-Host (" - {0,-32} | {1,-8} | {2,-10} | {3} to {4} | Cost: Rs {5}" -f `
        $b.id, $b.type, $b.status, $b.startTime, $b.endTime, $b.cost)
}

Write-Host "`n--- Full Audit Log ---"
for ($i = 0; $i -lt $trip.data.auditLog.Count; $i++) {
    $a = $trip.data.auditLog[$i]
    Write-Host (" [{0}] {1} [{2,-6}] {3,-26} : {4}" -f ($i + 1), $a.timestamp, $a.actor, $a.action, $a.details)
}
