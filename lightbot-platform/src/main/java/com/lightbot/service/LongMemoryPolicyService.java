package com.lightbot.service;

import com.lightbot.dto.LongMemoryKeyConfigUpdateDTO;
import com.lightbot.dto.LongMemoryPolicyUpdateDTO;
import com.lightbot.vo.LongMemoryKeyConfigVO;
import com.lightbot.vo.LongMemoryPolicyVO;

/**
 * 企业长期记忆策略服务
 *
 * @author finch
 * @since 2026-08-01
 */
public interface LongMemoryPolicyService {

    /**
     * 获取企业默认策略
     *
     * @return 企业默认
     */
    LongMemoryPolicyVO getEnterprisePolicy();

    /**
     * 更新企业默认策略
     *
     * @param request 更新请求
     * @return 更新后的企业默认
     */
    LongMemoryPolicyVO updateEnterprisePolicy(LongMemoryPolicyUpdateDTO request);

    /**
     * 解析生效策略：apiKeyId 为空为控制台调试；非空为该 Key（含覆盖合并）
     *
     * @param apiKeyId 企业 API Key ID，可为 null
     * @return 生效策略
     */
    LongMemoryPolicyVO resolveEffective(Long apiKeyId);

    /**
     * 查询某 Key 的策略配置与生效结果
     *
     * @param apiKeyId API Key ID
     * @return Key 策略视图
     */
    LongMemoryKeyConfigVO getKeyConfig(Long apiKeyId);

    /**
     * 更新某 Key 的策略覆盖
     *
     * @param apiKeyId API Key ID
     * @param request  覆盖配置
     * @return 更新后的 Key 策略视图
     */
    LongMemoryKeyConfigVO updateKeyConfig(Long apiKeyId, LongMemoryKeyConfigUpdateDTO request);
}
