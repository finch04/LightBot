package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.Region;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.RegionMapper;
import com.lightbot.service.RegionService;
import com.lightbot.vo.RegionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区划地区库实现
 *
 * @author finch
 * @since 2026-08-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionServiceImpl extends ServiceImpl<RegionMapper, Region> implements RegionService {

    private static final String SEED_RESOURCE = "region/china-pca.json";
    private static final int BATCH_SIZE = 500;

    private final ObjectMapper objectMapper;

    @Override
    public Region findByCode(String code) {
        String canonical = resolveCanonicalCode(code);
        if (!StringUtils.hasText(canonical)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Region>()
                .eq(Region::getCode, canonical)
                .last("LIMIT 1"));
    }

    @Override
    public List<String> listSelfAndDescendantCodes(String code) {
        Region region = findByCode(code);
        if (region == null || !StringUtils.hasText(region.getCode())) {
            return List.of();
        }
        List<String> codes = baseMapper.selectSelfAndDescendantCodes(region.getCode());
        return codes != null ? codes : List.of();
    }

    @Override
    public List<RegionVO> search(String keyword, int limit) {
        int size = limit > 0 ? Math.min(limit, 100) : 20;
        LambdaQueryWrapper<Region> q = new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getLevel)
                .orderByAsc(Region::getCode)
                .last("LIMIT " + size);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(Region::getName, kw).or().likeRight(Region::getCode, kw));
        }
        return list(q).stream().map(this::toVo).toList();
    }

    @Override
    public List<RegionVO> listChildren(String parentCode) {
        LambdaQueryWrapper<Region> q = new LambdaQueryWrapper<Region>().orderByAsc(Region::getCode);
        if (!StringUtils.hasText(parentCode)) {
            q.isNull(Region::getParentCode).eq(Region::getLevel, 1);
        } else {
            // 父级编码统一为库内 6 位（兼容传入 51 / 5101）
            String canonical = resolveCanonicalCode(parentCode);
            q.eq(Region::getParentCode, StringUtils.hasText(canonical) ? canonical : parentCode.trim());
        }
        return list(q).stream().map(this::toVo).toList();
    }

    @Override
    public long countActive() {
        return count();
    }

    @Override
    public Map<String, Long> statsBreakdown() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("count", count());
        m.put("provinces", count(new LambdaQueryWrapper<Region>().eq(Region::getLevel, 1)));
        m.put("cities", count(new LambdaQueryWrapper<Region>().eq(Region::getLevel, 2)));
        m.put("districts", count(new LambdaQueryWrapper<Region>().eq(Region::getLevel, 3)));
        return m;
    }

    @Override
    public List<RegionVO> listPath(String code) {
        Region cur = findByCode(code);
        if (cur == null) {
            return List.of();
        }
        List<Region> chain = new ArrayList<>();
        int guard = 0;
        while (cur != null && guard++ < 16) {
            chain.add(cur);
            if (!StringUtils.hasText(cur.getParentCode())) {
                break;
            }
            cur = findByCode(cur.getParentCode());
        }
        Collections.reverse(chain);
        return chain.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized int seedIfEmpty() {
        if (count() > 0) {
            return 0;
        }
        return importSeed();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized int reseed() {
        baseMapper.hardDeleteAll();
        return importSeed();
    }

    private int importSeed() {
        List<Region> rows = loadSeedRows();
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "地区种子数据为空");
        }
        int total = 0;
        List<Region> batch = new ArrayList<>(BATCH_SIZE);
        for (Region row : rows) {
            batch.add(row);
            if (batch.size() >= BATCH_SIZE) {
                saveBatch(batch, BATCH_SIZE);
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            saveBatch(batch, BATCH_SIZE);
            total += batch.size();
        }
        log.info("[Region] 导入国标省市区完成: count={}", total);
        return total;
    }

    private List<Region> loadSeedRows() {
        try {
            ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "缺少种子文件: " + SEED_RESOURCE);
            }
            try (InputStream in = resource.getInputStream()) {
                List<Map<String, Object>> provinces = objectMapper.readValue(in, new TypeReference<>() {});
                List<Region> rows = new ArrayList<>();
                if (provinces == null) {
                    return rows;
                }
                for (Map<String, Object> p : provinces) {
                    appendNode(rows, p, null, 1);
                }
                return rows;
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "解析地区种子失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void appendNode(List<Region> rows, Map<String, Object> node, String parentCode, int level) {
        if (node == null) {
            return;
        }
        Object codeObj = node.get("code");
        Object nameObj = node.get("name");
        if (codeObj == null || nameObj == null) {
            return;
        }
        String code = toStoredCode(String.valueOf(codeObj).trim());
        String name = String.valueOf(nameObj).trim();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            return;
        }
        Region region = new Region();
        region.setCode(code);
        region.setName(name);
        region.setParentCode(parentCode);
        region.setLevel(level);
        rows.add(region);

        Object children = node.get("children");
        if (children instanceof List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> m) {
                    appendNode(rows, (Map<String, Object>) m, code, level + 1);
                }
            }
        }
    }

    /**
     * 种子入库统一为国标 6 位：省 510000、市 510100、区 510104；
     * 东莞/中山等镇街 9 位编码保持原样。
     */
    private static String toStoredCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String code = raw.trim();
        if (code.matches("\\d{2}")) {
            return code + "0000";
        }
        if (code.matches("\\d{4}")) {
            return code + "00";
        }
        return code;
    }

    /**
     * 解析为库内规范编码：优先精确匹配；短码 51/5101 补零为 510000/510100。
     */
    private String resolveCanonicalCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String code = raw.trim();
        if (existsCode(code)) {
            return code;
        }
        String padded = toStoredCode(code);
        if (!padded.equals(code) && existsCode(padded)) {
            return padded;
        }
        return code;
    }

    private boolean existsCode(String code) {
        return count(new LambdaQueryWrapper<Region>().eq(Region::getCode, code)) > 0;
    }

    private RegionVO toVo(Region entity) {
        RegionVO vo = new RegionVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setParentCode(entity.getParentCode());
        vo.setLevel(entity.getLevel());
        return vo;
    }
}
