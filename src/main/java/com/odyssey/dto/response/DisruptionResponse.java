package com.odyssey.dto.response;

import com.odyssey.model.NotificationMessage;
import com.odyssey.model.Trip;
import com.odyssey.model.TripImpactReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisruptionResponse {
    private Trip trip;
    private TripImpactReport impact;
    private NotificationMessage notification;
}
