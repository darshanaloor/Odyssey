package com.odyssey.service.status;

import com.odyssey.model.Booking;
import com.odyssey.model.enums.BookingType;
import org.springframework.stereotype.Component;

@Component
public class StatusProviderSelector {

    private final LiveFlightStatusProvider liveFlightStatusProvider;
    private final SimulatedStatusProvider simulatedStatusProvider;
    private final MarketplaceStatusProvider marketplaceStatusProvider;
    private final String activeMode;

    public StatusProviderSelector(LiveFlightStatusProvider liveFlightStatusProvider,
                                  SimulatedStatusProvider simulatedStatusProvider,
                                  MarketplaceStatusProvider marketplaceStatusProvider) {
        this.liveFlightStatusProvider = liveFlightStatusProvider;
        this.simulatedStatusProvider = simulatedStatusProvider;
        this.marketplaceStatusProvider = marketplaceStatusProvider;

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

        // Honor explicit simulation overrides (from Transit Status & Monitoring) in SIMULATED mode,
        // including on marketplace bookings.
        if (!isLiveMode() && simulatedStatusProvider != null
                && simulatedStatusProvider.hasScriptedStatusForBooking(booking.getId())) {
            return simulatedStatusProvider;
        }

        // For marketplace bookings, read service_departures row
        if (marketplaceStatusProvider != null && (booking.getDepartureId() != null || "MARKETPLACE".equalsIgnoreCase(booking.getSource()))) {
            return marketplaceStatusProvider;
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
