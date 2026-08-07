package com.lightbot.config;

import com.lightbot.service.RegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时若地区库为空则自动导入国标省市区种子
 *
 * @author finch
 * @since 2026-08-05
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class RegionSeedRunner implements ApplicationRunner {

    private final RegionService regionService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = regionService.seedIfEmpty();
            if (n > 0) {
                log.info("[Region] 首次自动导入地区库: count={}", n);
            } else {
                log.info("[Region] 地区库已就绪: count={}", regionService.countActive());
            }
        } catch (Exception e) {
            // 表未迁移时不阻断启动
            log.warn("[Region] 地区库初始化跳过: {}", e.getMessage());
        }
    }
}
