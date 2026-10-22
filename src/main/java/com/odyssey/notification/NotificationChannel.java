package com.odyssey.notification;

import com.odyssey.model.NotificationMessage;

public interface NotificationChannel {
    String getChannelType();
    boolean isActive();
    SendResult send(NotificationMessage message);
}
