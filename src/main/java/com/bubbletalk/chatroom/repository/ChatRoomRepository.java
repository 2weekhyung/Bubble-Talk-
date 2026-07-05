package com.bubbletalk.chatroom.repository;

import com.bubbletalk.chatroom.entity.ChatRoom;
import com.bubbletalk.chatroom.entity.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long>, JpaSpecificationExecutor<ChatRoom> {
    boolean existsByRoomCode(String roomCode);

    Optional<ChatRoom> findByRoomCode(String roomCode);

    List<ChatRoom> findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus status);

    Page<ChatRoom> findByPrivateRoomFalseAndStatusNotOrderByCreatedDateDesc(RoomStatus status, Pageable pageable);

    List<ChatRoom> findAllByOrderByCreatedDateDesc();
}
