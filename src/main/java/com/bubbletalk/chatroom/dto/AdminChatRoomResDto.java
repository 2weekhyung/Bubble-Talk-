package com.bubbletalk.chatroom.dto;

import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminChatRoomResDto {

    private String roomCode;
    private String name;
    private boolean privateRoom;
    private RoomStatus status;
    private long currentParticipants;
    private int maxParticipants;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    public static AdminChatRoomResDto from(
            ChatRoom room,
            long currentParticipants,
            RoomStatus effectiveStatus
    ) {
        return AdminChatRoomResDto.builder()
                .roomCode(room.getRoomCode())
                .name(room.getName())
                .privateRoom(room.isPrivateRoom())
                .status(effectiveStatus)
                .currentParticipants(currentParticipants)
                .maxParticipants(room.getMaxParticipants())
                .createdAt(room.getCreatedDate())
                .closedAt(room.getClosedAt())
                .build();
    }
}
