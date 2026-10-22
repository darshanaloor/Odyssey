package com.odyssey.notification;

import com.odyssey.model.NotificationMessage;
import com.odyssey.util.NotificationMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class SimulatedChannel implements NotificationChannel {

    @Override
    public String getChannelType() {
        return "SIMULATED";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public SendResult send(NotificationMessage message) {
        String maskedRecipient = message.getRecipient() != null && message.getRecipient().contains("@")
                ? NotificationMasker.maskEmail(message.getRecipient())
                : NotificationMasker.maskPhone(message.getRecipient());

        log.info("SimulatedChannel: Recording simulated alert for channel {} to recipient {}",
                message.getChannel(), maskedRecipient);

        return SendResult.builder()
                .status(SendStatus.SIMULATED)
                .error("Simulated, not sent to a real device")
                .sentAt(LocalDateTime.now())
                .build();
    }
}
