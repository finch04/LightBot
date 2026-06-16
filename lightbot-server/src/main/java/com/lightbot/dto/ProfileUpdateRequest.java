package com.lightbot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @Size(min = 1, max = 8, message = "昵称长度1-8")
    private String nickname;

    private String email;

    private String phone;

    private String avatarFrame;

    @Min(value = 0, message = "等级最小为0")
    @Max(value = 6, message = "等级最大为6")
    private Integer level;
}
