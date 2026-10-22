package com.odyssey.service;

import com.odyssey.config.RecoveryProperties;
import com.odyssey.dto.response.ChannelStatusResponse;
import com.odyssey.dto.response.NotificationHistoryResponse;
import com.odyssey.entity.NotificationEntity;
import com.odyssey.entity.TripEntity;
import com.odyssey.model.*;
import com.odyssey.model.enums.*;
import com.odyssey.notification.*;
import com.odyssey.repository.TripRepository;
import com.odyssey.repository.jpa.NotificationJpaRepository;
import com.odyssey.repository.jpa.TripJpaRepository;
import com.odyssey.util.NotificationMasker;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealNotificationServiceTest {

    @Mock
    private JavaMailSender mockMailSender;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    @Mock
    private NotificationJpaRepository notificationJpaRepository;

    @Mock
    private TripJpaRepository tripJpaRepository;

    @Mock
    private TripRepository tripRepository;

    private EmailChannel emailChannel;
    private SmsChannel smsChannel;
    private SimulatedChannel simulatedChannel;
    private RecoveryProperties recoveryProperties;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        recoveryProperties = new RecoveryProperties();

        // 1. Setup EmailChannel (Active with mock mail sender)
        emailChannel = new EmailChannel("smtp.example.com", 587, "user@example.com", "dummy_pass", "odyssey@example.com");
        emailChannel.setMailSenderForTesting(mockMailSender);

        // 2. Setup SmsChannel (Active with mock HTTP client)
        smsChannel = new SmsChannel("AC_TEST_SID", "AUTH_TOKEN_TEST", "+1234567890", "+1987654321");
        smsChannel.setHttpClientForTesting(mockHttpClient);

        // 3. Setup SimulatedChannel
        simulatedChannel = new SimulatedChannel();

        // 4. Setup NotificationService
        notificationService = new NotificationService(
                emailChannel,
                smsChannel,
                simulatedChannel,
                notificationJpaRepository,
                tripJpaRepository,
                tripRepository,
                recoveryProperties
        );

        lenient().when(notificationJpaRepository.save(any(NotificationEntity.class))).thenAnswer(inv -> {
            NotificationEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId("NOTIF-" + UUID.randomUUID().toString().substring(0, 8));
            }
            return e;
        });
    }

    @Test
    @DisplayName("Email Channel: Dispatches successfully when SMTP credentials and mail sender are active")
    void testEmailChannelSentWhenActive() {
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        NotificationMessage message = NotificationMessage.builder()
                .channel("EMAIL")
                .recipient("traveller@example.com")
                .subject("Test Alert")
                .message("Disruption detected on flight AI-101")
                .build();

        SendResult result = emailChannel.send(message);

        assertEquals(SendStatus.SENT, result.getStatus());
        assertNotNull(result.getProviderMessageId());
        verify(mockMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Email Channel: Returns SIMULATED when SMTP is not configured")
    void testEmailChannelSimulatedWhenInactive() {
        EmailChannel inactiveEmailChannel = new EmailChannel(null, 587, null, null, null);

        NotificationMessage message = NotificationMessage.builder()
                .channel("EMAIL")
                .recipient("traveller@example.com")
                .subject("Test Alert")
                .message("Disruption detected")
                .build();

        SendResult result = inactiveEmailChannel.send(message);

        assertEquals(SendStatus.SIMULATED, result.getStatus());
        assertTrue(result.getError().contains("simulated"));
    }

    @Test
    @DisplayName("Email Channel: Returns SKIPPED when recipient format is invalid")
    void testEmailChannelSkippedOnInvalidEmail() {
        NotificationMessage message = NotificationMessage.builder()
                .channel("EMAIL")
                .recipient("invalid-email-address")
                .subject("Test Alert")
                .message("Disruption detected")
                .build();

        SendResult result = emailChannel.send(message);

        assertEquals(SendStatus.SKIPPED, result.getStatus());
        assertTrue(result.getError().contains("invalid"));
        verify(mockMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMS Channel: Dispatches SMS successfully via Twilio REST API")
    void testSmsChannelSentWhenActive() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(201);
        when(mockHttpResponse.body()).thenReturn("{\"sid\":\"SM_TEST_12345\"}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        NotificationMessage message = NotificationMessage.builder()
                .channel("SMS")
                .recipient("9876543210")
                .subject("SMS Disruption Alert")
                .message("Flight AI-101 cancelled")
                .build();

        SendResult result = smsChannel.send(message);

        assertEquals(SendStatus.SENT, result.getStatus());
        assertEquals("SM_TEST_12345", result.getProviderMessageId());
        verify(mockHttpClient, times(1)).send(any(HttpRequest.class), any());
    }

    @Test
    @DisplayName("SMS Channel: Returns SIMULATED when Twilio credentials are not set")
    void testSmsChannelSimulatedWhenInactive() {
        SmsChannel inactiveSms = new SmsChannel(null, null, null, null);

        NotificationMessage message = NotificationMessage.builder()
                .channel("SMS")
                .recipient("9876543210")
                .message("Flight cancelled")
                .build();

        SendResult result = inactiveSms.send(message);

        assertEquals(SendStatus.SIMULATED, result.getStatus());
    }

    @Test
    @DisplayName("SMS Channel: Returns SKIPPED when recipient phone number is invalid")
    void testSmsChannelSkippedOnInvalidPhone() {
        NotificationMessage message = NotificationMessage.builder()
                .channel("SMS")
                .recipient("1234") // too short
                .message("Flight cancelled")
                .build();

        SendResult result = smsChannel.send(message);

        assertEquals(SendStatus.SKIPPED, result.getStatus());
    }

    @Test
    @DisplayName("WhatsApp Channel: Sends formatted WhatsApp message when TWILIO_WHATSAPP_FROM is set")
    void testWhatsAppChannelActiveAndSent() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(201);
        when(mockHttpResponse.body()).thenReturn("{\"sid\":\"WA_TEST_67890\"}");
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());

        NotificationMessage message = NotificationMessage.builder()
                .channel("WHATSAPP")
                .recipient("+919876543210")
                .message("Flight AI-101 cancelled")
                .build();

        SendResult result = smsChannel.send(message);

        assertEquals(SendStatus.SENT, result.getStatus());
        assertEquals("WA_TEST_67890", result.getProviderMessageId());
    }

    @Test
    @DisplayName("De-duplication: Does not dispatch duplicate disruption alerts")
    void testDeDuplicationPreventsDuplicateAlerts() {
        Trip trip = createSampleTrip("TRIP-DUP-001");
        Disruption disruption = Disruption.builder()
                .id("DIS-DUP-1")
                .bookingId("BKG-1")
                .type(DisruptionType.CANCELLATION)
                .reason("Weather delay")
                .build();

        String expectedKey = "TRIP-DUP-001:DISRUPTION_ALERT:DIS-DUP-1";
        when(notificationJpaRepository.existsByTripIdAndDedupKey("TRIP-DUP-001", expectedKey)).thenReturn(true);

        notificationService.sendDisruptionAlert(trip, trip.getBookings().get(0), disruption, TripImpactReport.builder().build());

        verify(notificationJpaRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("Secrets Masking: Recipient phone and email are masked before saving to history")
    void testSecretsMaskedInLogsAndHistory() {
        Trip trip = createSampleTrip("TRIP-MASK-001");
        trip.setTravellerPhone("+919876543210");
        trip.setTravellerEmail("johndoe@example.com");

        TripEntity tripEntity = TripEntity.builder().id("TRIP-MASK-001").build();
        when(tripJpaRepository.findById("TRIP-MASK-001")).thenReturn(Optional.of(tripEntity));
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        try {
            doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), any());
            when(mockHttpResponse.statusCode()).thenReturn(201);
            when(mockHttpResponse.body()).thenReturn("{\"sid\":\"SM_MASK_001\"}");
        } catch (Exception ignored) {}

        Disruption disruption = Disruption.builder()
                .id("DIS-MASK-1")
                .bookingId("BKG-1")
                .type(DisruptionType.CANCELLATION)
                .reason("Maintenance")
                .build();

        notificationService.sendDisruptionAlert(trip, trip.getBookings().get(0), disruption, TripImpactReport.builder().build());

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationJpaRepository, atLeastOnce()).save(captor.capture());

        for (NotificationEntity saved : captor.getAllValues()) {
            if ("EMAIL".equals(saved.getChannel())) {
                assertEquals("j***@example.com", saved.getRecipientMasked());
                assertFalse(saved.getRecipientMasked().contains("johndoe"));
            } else if ("SMS".equals(saved.getChannel())) {
                assertEquals("+91*****3210", saved.getRecipientMasked());
                assertFalse(saved.getRecipientMasked().contains("987654"));
            }
        }
    }

    @Test
    @DisplayName("Preferences Off: Channels disabled in preferences are not dispatched")
    void testPreferencesOffMeansNoSend() {
        Trip trip = createSampleTrip("TRIP-PREF-OFF");
        trip.setNotifyEmail(false);
        trip.setNotifySms(false);
        trip.setNotifyWhatsapp(false);

        Disruption disruption = Disruption.builder()
                .id("DIS-PREF-1")
                .bookingId("BKG-1")
                .type(DisruptionType.CANCELLATION)
                .reason("Strike")
                .build();

        notificationService.sendDisruptionAlert(trip, trip.getBookings().get(0), disruption, TripImpactReport.builder().build());

        verify(mockMailSender, never()).send(any(MimeMessage.class));
        verify(notificationJpaRepository, never()).save(any(NotificationEntity.class));
    }

    @Test
    @DisplayName("Test Alert Rate Limiting: Throws 429 after 3 test alerts within an hour")
    void testTestEndpointRateLimit() {
        Trip trip = createSampleTrip("TRIP-RATE-001");

        when(notificationJpaRepository.countByTripIdAndEventTypeAndCreatedAtAfter(
                eq("TRIP-RATE-001"), eq("TEST_ALERT"), any(LocalDateTime.class)))
                .thenReturn(3L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.sendTestAlert(trip)
        );

        assertTrue(ex.getMessage().contains("Rate limit"));
    }

    @Test
    @DisplayName("Hotel Email Path: Dispatches hotel update when hotel booking and email exist")
    void testHotelEmailPathAndMailtoFallback() {
        Trip trip = createSampleTrip("TRIP-HOTEL-001");
        Booking hotelBooking = Booking.builder()
                .id("BKG-HOTEL-99")
                .type(BookingType.HOTEL)
                .provider("Grand Palace Hotel")
                .from("Mumbai")
                .to("Mumbai")
                .startTime(LocalDateTime.of(2026, 10, 21, 14, 0))
                .endTime(LocalDateTime.of(2026, 10, 22, 11, 0))
                .cost(new BigDecimal("4500"))
                .hotelContactEmail("reservations@grandpalace.com")
                .build();
        trip.getBookings().add(hotelBooking);

        TripEntity tripEntity = TripEntity.builder().id("TRIP-HOTEL-001").build();
        when(tripJpaRepository.findById("TRIP-HOTEL-001")).thenReturn(Optional.of(tripEntity));
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        RecoveryPlan plan = RecoveryPlan.builder()
                .hotelMessage("Dear Hotel, Guest Rohit Sharma check-in delayed to 20:00.")
                .build();

        NotificationHistoryResponse res = notificationService.sendHotelEmail(trip, plan);

        assertEquals("SENT", res.getStatus());
        assertEquals("r***@grandpalace.com", res.getRecipientMasked());
        verify(mockMailSender, times(1)).send(any(MimeMessage.class));
        verify(tripRepository, times(1)).save(trip);
    }

    @Test
    @DisplayName("Hotel Email Path: Fails with 400 when trip has no hotel contact email")
    void testHotelEmailFailsWhenNoContactEmail() {
        Trip trip = createSampleTrip("TRIP-HOTEL-NO-EMAIL");
        Booking hotelBooking = Booking.builder()
                .id("BKG-HOTEL-01")
                .type(BookingType.HOTEL)
                .provider("Beachside Stay")
                .from("Goa")
                .to("Goa")
                .startTime(LocalDateTime.of(2026, 10, 21, 14, 0))
                .endTime(LocalDateTime.of(2026, 10, 22, 11, 0))
                .cost(new BigDecimal("3000"))
                .hotelContactEmail(null) // missing
                .build();
        trip.getBookings().add(hotelBooking);

        RecoveryPlan plan = RecoveryPlan.builder().hotelMessage("Test message").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                notificationService.sendHotelEmail(trip, plan)
        );

        assertTrue(ex.getMessage().contains("contact email"));
    }

    @Test
    @DisplayName("Demo String Leak Prevention: Non-demo trip alerts contain no demo strings")
    void testNoDemoStringsInNonDemoTripAlerts() {
        Trip nonDemoTrip = Trip.builder()
                .id("TRIP-PUNE-999")
                .title("Pune to Nagpur Business Trip")
                .travellerName("Vikramaditya Roy")
                .travellerPhone("+919123456789")
                .travellerEmail("vikram@enterprise.in")
                .bookings(List.of(
                        Booking.builder()
                                .id("BKG-V1")
                                .type(BookingType.FLIGHT)
                                .provider("Indigo 6E-202")
                                .from("Pune (PNQ)")
                                .to("Nagpur (NAG)")
                                .startTime(LocalDateTime.of(2026, 11, 10, 8, 0))
                                .endTime(LocalDateTime.of(2026, 11, 10, 9, 30))
                                .cost(new BigDecimal("4200"))
                                .build()
                ))
                .build();

        Disruption disruption = Disruption.builder()
                .id("DIS-PUNE-1")
                .bookingId("BKG-V1")
                .type(DisruptionType.FLIGHT_DELAY)
                .delayMinutes(60)
                .reason("ATC congestion")
                .build();

        NotificationMessage alert = notificationService.generateAlert(
                nonDemoTrip,
                nonDemoTrip.getBookings().get(0),
                disruption,
                TripImpactReport.builder().summary("Indigo delayed by 60 mins.").build()
        );

        String msg = alert.getMessage();
        assertFalse(msg.contains("Mangaluru"), "Must not leak Mangaluru");
        assertFalse(msg.contains("Ananya"), "Must not leak Ananya");
        assertFalse(msg.contains("Gateway"), "Must not leak Gateway");
        assertFalse(msg.contains("Panaji"), "Must not leak Panaji");
        assertFalse(msg.contains("TRIP-DEMO"), "Must not leak TRIP-DEMO");
        assertTrue(msg.contains("Pune to Nagpur Business Trip"), "Must contain real trip title");
        assertTrue(msg.contains("Indigo 6E-202"), "Must contain real booking provider");
    }

    @Test
    @DisplayName("Channel Status: Returns accurate active and simulated status flags")
    void testChannelStatus() {
        ChannelStatusResponse status = notificationService.getChannelStatus();
        assertEquals("Active", status.getEmail());
        assertEquals("Active", status.getSms());
        assertEquals("Active", status.getWhatsapp());
    }

    @Test
    @DisplayName("Quiet Hours: Correctly identifies quiet hours window")
    void testQuietHours() {
        recoveryProperties.setQuietHoursStart(LocalTime.of(22, 0));
        recoveryProperties.setQuietHoursEnd(LocalTime.of(7, 0));

        assertTrue(notificationService.isQuietHours(LocalTime.of(23, 0)));
        assertTrue(notificationService.isQuietHours(LocalTime.of(5, 30)));
        assertFalse(notificationService.isQuietHours(LocalTime.of(14, 0)));
    }

    private Trip createSampleTrip(String tripId) {
        return Trip.builder()
                .id(tripId)
                .title("Bengaluru to Mangaluru Travel")
                .travellerName("Ramesh Hegde")
                .travellerPhone("+919876543210")
                .travellerEmail("ramesh@example.com")
                .notifyEmail(true)
                .notifySms(true)
                .notifyWhatsapp(false)
                .bookings(new ArrayList<>(List.of(
                        Booking.builder()
                                .id("BKG-1")
                                .type(BookingType.FLIGHT)
                                .provider("Air India AI-101")
                                .from("BLR")
                                .to("IXE")
                                .startTime(LocalDateTime.of(2026, 10, 21, 10, 0))
                                .endTime(LocalDateTime.of(2026, 10, 21, 11, 15))
                                .cost(new BigDecimal("3500"))
                                .build()
                )))
                .activeDisruptions(new ArrayList<>())
                .auditLog(new ArrayList<>())
                .build();
    }
}
