package com.bubbletalk.admin.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardSummaryResDto {

    private long totalRooms;
    private long publicRooms;
    private long privateRooms;
    private long openRooms;
    private long fullRooms;
    private long closedRooms;
    private long activeSessions;
    private Long activeGuests;
    private long todayMenuCount;
    private long todayVoteCount;
    private boolean redisAvailable;
}
