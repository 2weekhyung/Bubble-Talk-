package com.bubbletalk.chatroom.dto;

import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomResDto {
    private String roomCode;
    private String name;
    private String description;
    private boolean isPrivate;
    private int maxParticipants;
    private long currentParticipants;
    private RoomStatus status;
    private LocalDateTime createdAt;

    public static ChatRoomResDto from(ChatRoom room, long currentParticipants, RoomStatus effectiveStatus) {
        return ChatRoomResDto.builder()
                .roomCode(room.getRoomCode())
                .name(room.getName())
                .description(room.getDescription())
                .isPrivate(room.isPrivateRoom())
                .maxParticipants(room.getMaxParticipants())
                .currentParticipants(currentParticipants)
                .status(effectiveStatus)
                .createdAt(room.getCreatedDate())
                .build();
    }
}
