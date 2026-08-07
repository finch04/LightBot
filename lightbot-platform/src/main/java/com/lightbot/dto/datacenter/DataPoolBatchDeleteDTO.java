package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 数据池批量删除
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataPoolBatchDeleteDTO {

    @NotEmpty(message = "ID列表不能为空")
    private List<Long> ids;
}
