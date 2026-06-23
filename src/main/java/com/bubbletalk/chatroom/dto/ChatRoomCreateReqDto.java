package com.bubbletalk.chatroom.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatRoomCreateReqDto {
    private String name;
    private String description;
    private Boolean isPrivate;
    private Integer maxParticipants;
}
