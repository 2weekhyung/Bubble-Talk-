package com.bubbletalk.chatroom.entity;

import com.bubbletalk.base.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "chat_room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, unique = true, length = 30)
    private String roomCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "is_private", nullable = false)
    private boolean privateRoom;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Builder
    public ChatRoom(String roomCode, String name, String description, boolean privateRoom, Integer maxParticipants) {
        this.roomCode = roomCode;
        this.name = name;
        this.description = description;
        this.privateRoom = privateRoom;
        this.maxParticipants = maxParticipants != null ? maxParticipants : 10;
        this.status = RoomStatus.OPEN;
    }

    public boolean close() {
        if (this.status == RoomStatus.CLOSED) {
            return false;
        }
        this.status = RoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        return true;
    }
}
