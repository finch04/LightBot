package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 地区节点 VO
 *
 * @author finch
 * @since 2026-08-05
 */
@Data
@Schema(description = "地区节点")
public class RegionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String code;
    private String name;
    private String parentCode;
    private Integer level;

    private List<RegionVO> children = new ArrayList<>();
}
