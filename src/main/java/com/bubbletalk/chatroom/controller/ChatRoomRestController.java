package com.bubbletalk.chatroom.controller;

import com.bubbletalk.base.dto.BaseResDto;
import com.bubbletalk.chatroom.dto.ChatRoomCreateReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomJoinReqDto;
import com.bubbletalk.chatroom.dto.ChatRoomResDto;
import com.bubbletalk.chatroom.service.ChatRoomService;
import com.bubbletalk.guest.GuestIdSupport;
import com.bubbletalk.securitylog.entity.EventType;
import com.bubbletalk.securitylog.entity.Severity;
import com.bubbletalk.securitylog.service.SecurityEventLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomRestController {

    private final ChatRoomService chatRoomService;
    private final GuestIdSupport guestIdSupport;
    private final SecurityEventLogService securityEventLogService;

    @PostMapping
    public ResponseEntity<BaseResDto> createRoom(@RequestBody ChatRoomCreateReqDto reqDto,
                                                 @RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                                 HttpServletRequest request) {
        ChatRoomResDto room = chatRoomService.createRoom(reqDto, resolveRequesterId(clientId, request));
        securityEventLogService.logEvent(
                EventType.ROOM_CREATED,
                Severity.INFO,
                room.getRoomCode(),
                resolveGuestId(request),
                request,
                "채팅방 생성"
        );
        return ResponseEntity.ok(BaseResDto.ok(room));
    }

    @GetMapping
    public ResponseEntity<BaseResDto> getPublicRooms(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 10);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return ResponseEntity.ok(BaseResDto.ok(chatRoomService.getPublicRooms(pageable)));
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
        ChatRoomResDto room = chatRoomService.leaveRoom(roomCode, resolveRequesterId(clientId, request));
        securityEventLogService.logEvent(
                EventType.ROOM_LEAVE,
                Severity.INFO,
                room.getRoomCode(),
                resolveGuestId(request),
                request,
                "사용자 채팅방 퇴장"
        );
        return ResponseEntity.ok(BaseResDto.ok(room));
    }

    private String resolveGuestId(HttpServletRequest request) {
        return guestIdSupport.resolve(request).orElse(null);
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
