# Verification script for Phase 4

Write-Host "=== 1. POST /api/demo/reset ==="
$reset = Invoke-RestMethod -Uri "http://localhost:8085/api/demo/reset" -Method Post
Write-Host "Reset status:" $reset.success $reset.message

Write-Host "`n=== 2. POST /api/trips/TRIP-DEMO-001/disruptions (CANCELLATION BKG-101) ==="
$disruptionBody = @{
    bookingId = "BKG-101"
    type = "CANCELLATION"
    reason = "IndiGo 6E-534 flight cancelled due to technical reasons"
} | ConvertTo-Json
$disruption = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/disruptions" -Method Post -Body $disruptionBody -ContentType "application/json"
Write-Host "Disruption status:" $disruption.success $disruption.message
Write-Host "Direct impacted count:" $disruption.data.impactedBookings.Count
Write-Host "Cascade impacted count:" $disruption.data.cascadeImpactedBookings.Count

Write-Host "`n=== 3. POST /api/trips/TRIP-DEMO-001/recover ==="
$recover = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/recover" -Method Post
Write-Host "Recover success:" $recover.success $recover.message
Write-Host "Options count:" $recover.data.options.Count
Write-Host "Rejected count:" $recover.data.rejectedOptions.Count
foreach ($opt in $recover.data.options) {
    Write-Host "Option:" $opt.id "[" + $opt.label + "]" "Score:" $opt.score "Cost:" $opt.totalCost "Arrival:" $opt.arrivalTime
}

Write-Host "`n=== 4. POST /api/trips/TRIP-DEMO-001/plan (OPT-SAFEST-INV-TR-001) ==="
$planBody = @{
    optionId = "OPT-SAFEST-INV-TR-001"
} | ConvertTo-Json
$plan = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/plan" -Method Post -Body $planBody -ContentType "application/json"
Write-Host "Plan generated:" $plan.success $plan.message
Write-Host "Plan ID:" $plan.data.id
Write-Host "Chosen Option:" $plan.data.chosenOptionId "(" $plan.data.chosenOptionLabel ")"

Write-Host "`n=== 5. POST /api/trips/TRIP-DEMO-001/approve (OPT-SAFEST-INV-TR-001) ==="
$approveBody = @{
    optionId = "OPT-SAFEST-INV-TR-001"
} | ConvertTo-Json
$approve = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001/approve" -Method Post -Body $approveBody -ContentType "application/json"
Write-Host "Approve success:" $approve.success $approve.message

Write-Host "`n=== 6. GET /api/trips/TRIP-DEMO-001 ==="
$trip = Invoke-RestMethod -Uri "http://localhost:8085/api/trips/TRIP-DEMO-001" -Method Get
Write-Host "Final Trip Status:" $trip.data.status
Write-Host "Final Bookings Count:" $trip.data.bookings.Count
foreach ($b in $trip.data.bookings) {
    Write-Host " - Booking:" $b.id "[" + $b.type + "]" $b.provider $b.status $b.startTime "->" $b.endTime "Cost: Rs" $b.cost
}

Write-Host "`n=== 7. Itinerary Diff (from approved plan) ==="
foreach ($d in $approve.data.itineraryDiff) {
    Write-Host " - Diff:" $d.bookingId "[" + $d.type + "]" $d.provider "Old:" $d.oldStatus $d.oldStart "New:" $d.newStatus $d.newStart
    Write-Host "   Description:" $d.changeDescription
}

Write-Host "`n=== 8. Cost Breakdown ==="
Write-Host "Net Trip Cost: Rs" $approve.data.costBreakdown.netTripCost
Write-Host "Max Budget: Rs" $approve.data.costBreakdown.maxBudget
Write-Host "Remaining Budget: Rs" $approve.data.costBreakdown.remainingBudget
Write-Host "Lines:"
foreach ($l in $approve.data.costBreakdown.lines) {
    Write-Host "   *" $l.label ":" $l.amount "(" $l.assumptionNote ")"
}

Write-Host "`n=== 9. Action Checklist ==="
foreach ($c in $approve.data.checklist) {
    $doneMark = if ($c.done) { "X" } else { " " }
    Write-Host "   [$doneMark]" $c.id ":" $c.text
}

Write-Host "`n=== 10. Hotel Message ==="
Write-Host $approve.data.hotelMessage

Write-Host "`n=== 11. Trade-Off Explanation ==="
Write-Host $approve.data.tradeOffExplanation

Write-Host "`n=== 12. Audit Log (Last 4 entries) ==="
$log = $trip.data.auditLog
for ($i = [Math]::Max(0, $log.Count - 4); $i -lt $log.Count; $i++) {
    Write-Host "   Entry:" $log[$i].timestamp "[" + $log[$i].actor + "]" $log[$i].action "-" $log[$i].details
}
