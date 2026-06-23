package com.bubbletalk.chatroom.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.chatroom.dto.ChatRoomCreateReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomJoinReqDto;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.guest.GuestIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomRestController {

    private final ChatRoomService chatRoomService;
    private final GuestIdSupport guestIdSupport;

    @PostMapping
    public ResponseEntity<BaseResDto> createRoom(@RequestBody ChatRoomCreateReqDto reqDto) {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.createRoom(reqDto)));
    }

    @GetMapping
    public ResponseEntity<BaseResDto> getPublicRooms() {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.getPublicRooms()));
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<BaseResDto> getRoom(@PathVariable String roomCode) {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.getRoom(roomCode)));
    }

    @PostMapping("/{roomCode}/join")
    public ResponseEntity<BaseResDto> joinRoom(@PathVariable String roomCode,
                                               @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                               HttpServletRequest request) {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.joinRoom(roomCode, resolveRequesterId(clientId, request))));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<BaseResDto> joinRoomByCode(@RequestBody ChatRoomJoinReqDto reqDto,
                                                     @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                                     HttpServletRequest request) {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.joinRoom(reqDto.getRoomCode(), resolveRequesterId(clientId, request))));
    }

    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<BaseResDto> leaveRoom(@PathVariable String roomCode,
                                                @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                                HttpServletRequest request) {
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.leaveRoom(roomCode, resolveRequesterId(clientId, request))));
    }

    private String resolveRequesterId(String clientId, HttpServletRequest request) {
        return guestIdSupport.resolve(request)
                .map(guestId -> "guest:" + guestId)
                .orElseGet(() -> resolveLegacyRequesterId(clientId, request));
    }

    private String resolveLegacyRequesterId(String clientId, HttpServletRequest request) {
        if (clientId != null && !clientId.isBlank()) {
            return "client:" + clientId;
        }
        return "ip:" + request.getRemoteAddr();
    }
}
