package com.odyssey.service.status;

import com.odyssey.model.Booking;
import com.odyssey.model.enums.BookingType;
import org.springframework.stereotype.Component;

@Component
public class StatusProviderSelector {

    private final LiveFlightStatusProvider liveFlightStatusProvider;
    private final SimulatedStatusProvider simulatedStatusProvider;
    private final String activeMode;

    public StatusProviderSelector(LiveFlightStatusProvider liveFlightStatusProvider,
                                  SimulatedStatusProvider simulatedStatusProvider) {
        this.liveFlightStatusProvider = liveFlightStatusProvider;
        this.simulatedStatusProvider = simulatedStatusProvider;

        String apiKey = System.getenv("FLIGHT_API_KEY");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            this.activeMode = "LIVE";
        } else {
            this.activeMode = "SIMULATED";
        }
    }

    public String getActiveMode() {
        return activeMode;
    }

    public boolean isLiveMode() {
        return "LIVE".equalsIgnoreCase(activeMode);
    }

    public StatusProvider getProvider(Booking booking) {
        if (booking == null) {
            return simulatedStatusProvider;
        }

        // Trains and buses have no live provider: they use simulated provider in both modes
        if (booking.getType() == BookingType.TRAIN || booking.getType() == BookingType.BUS) {
            return simulatedStatusProvider;
        }

        // Flights use live provider if in LIVE mode, else simulated provider
        if (booking.getType() == BookingType.FLIGHT) {
            return isLiveMode() ? liveFlightStatusProvider : simulatedStatusProvider;
        }

        return simulatedStatusProvider;
    }
}
