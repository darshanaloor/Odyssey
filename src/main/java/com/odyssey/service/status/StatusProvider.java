package com.odyssey.service.status;

import com.odyssey.model.Booking;
import com.odyssey.model.StatusResult;

public interface StatusProvider {
    StatusResult getStatus(Booking booking);

    default StatusResult getStatus(String tripId, Booking booking) {
        return getStatus(booking);
    }

    boolean supports(Booking booking);
}
