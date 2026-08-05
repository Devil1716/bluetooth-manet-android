package com.devil1716.bluetoothmanet;

import androidx.room.TypeConverter;

public class MessageStatusConverter {
    @TypeConverter
    public String fromStatus(MessageStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public MessageStatus toStatus(String value) {
        return value == null ? null : MessageStatus.valueOf(value);
    }
}
