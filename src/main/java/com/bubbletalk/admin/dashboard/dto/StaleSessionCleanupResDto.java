package com.bubbletalk.admin.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaleSessionCleanupResDto {

    private int scannedSessions;
    private int removedSessions;
    private int scannedRooms;
    private int affectedRooms;
    private String message;
}
