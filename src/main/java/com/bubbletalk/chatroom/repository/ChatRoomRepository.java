package com.bubbletalk.chatroom.repository;

import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    boolean existsByRoomCode(String roomCode);

    Optional<ChatRoom> findByRoomCode(String roomCode);

    List<ChatRoom> findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus status);
}
