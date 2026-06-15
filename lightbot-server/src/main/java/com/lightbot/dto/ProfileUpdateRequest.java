package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @Size(min = 1, max = 32, message = "昵称长度1-32")
    private String nickname;

    private String email;

    private String phone;

    private String avatarFrame;
}
