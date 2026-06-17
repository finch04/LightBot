package com.lightbot.util;

import com.lightbot.dto.EvalDatasetExampleVO;

import java.util.*;

/**
 * 内置示例评测集模板定义
 * <p>提供 4 个常见评测场景的示例数据集，帮助用户快速创建评测集</p>
 *
 * @author finch
 * @since 2026-06-17
 */
public final class EvalDatasetExampleTemplates {

    private EvalDatasetExampleTemplates() {}

    private static final String COLUMNS_IO = "[{\"key\":\"input\",\"label\":\"输入\",\"type\":\"text\"},{\"key\":\"reference_output\",\"label\":\"参考答案\",\"type\":\"text\"}]";

    // ========== 公开 API ==========

    /**
     * 获取所有示例列表（不含数据详情，用于前端展示）
     */
    public static List<EvalDatasetExampleVO> listExamples() {
        return List.of(
                EvalDatasetExampleVO.builder()
                        .key("prompt_quality")
                        .name("Prompt 效果评测集")
                        .description("测试 Prompt 模板的回答质量，包含多样化的输入场景和参考答案")
                        .tags(List.of("准确性", "完整性", "相关性"))
                        .itemCount(5)
                        .build(),
                EvalDatasetExampleVO.builder()
                        .key("qa_accuracy")
                        .name("问答准确性评测集")
                        .description("评测问答系统的回答准确性，适用于 RAG 知识库问答场景")
                        .tags(List.of("事实正确", "无幻觉", "引用准确"))
                        .itemCount(5)
                        .build(),
                EvalDatasetExampleVO.builder()
                        .key("text_classification")
                        .name("文本分类评测集")
                        .description("评测文本分类任务的准确率，包含正向/负向/中性三类样本")
                        .tags(List.of("分类准确", "边界清晰"))
                        .itemCount(6)
                        .build(),
                EvalDatasetExampleVO.builder()
                        .key("summarization")
                        .name("文本摘要评测集")
                        .description("评测文本摘要的质量，评估关键信息保留和语言流畅度")
                        .tags(List.of("信息保留", "语言流畅", "长度适中"))
                        .itemCount(4)
                        .build()
        );
    }

    /**
     * 根据 key 获取示例的列配置和数据项
     *
     * @param key 示例标识
     * @return 示例数据
     */
    public static ExampleDatasetData getExampleData(String key) {
        return switch (key) {
            case "prompt_quality" -> buildPromptQualityDataset();
            case "qa_accuracy" -> buildQaAccuracyDataset();
            case "text_classification" -> buildTextClassificationDataset();
            case "summarization" -> buildSummarizationDataset();
            default -> null;
        };
    }

    // ========== 数据结构 ==========

    public record ExampleDatasetData(String name, String description, String columnsConfig, List<String> dataContents) {}

    // ========== 辅助方法 ==========

    private static String jsonItem(String input, String referenceOutput) {
        return "{\"input\":\"" + input + "\",\"reference_output\":\"" + referenceOutput + "\"}";
    }

    // ========== 示例构建 ==========

    private static ExampleDatasetData buildPromptQualityDataset() {
        List<String> items = List.of(
                jsonItem("请解释什么是微服务架构", "微服务架构是一种将应用拆分为一组小型、独立部署的服务的架构风格，每个服务围绕业务能力构建，通过轻量级通信机制协作。"),
                jsonItem("写一段Python代码实现冒泡排序", "def bubble_sort(arr):\\n    n = len(arr)\\n    for i in range(n):\\n        for j in range(0, n-i-1):\\n            if arr[j] > arr[j+1]:\\n                arr[j], arr[j+1] = arr[j+1], arr[j]\\n    return arr"),
                jsonItem("帮我写一封请假邮件", "主题：请假申请\\n\\n尊敬的领导：\\n您好！因个人原因，需请假一天（X月X日），届时工作已交接给同事XX，望批准。"),
                jsonItem("用通俗语言解释量子计算", "量子计算利用量子力学的叠加和纠缠特性，让量子比特同时处于0和1的状态，从而并行处理大量计算任务，比传统计算机快指数级别。"),
                jsonItem("推荐3本编程入门书籍", "1.Python编程从入门到实践-适合零基础\\n2.Head First Java-图文并茂易理解\\n3.JavaScript高级程序设计-前端必读经典")
        );
        return new ExampleDatasetData("Prompt 效果评测集", "测试 Prompt 模板的回答质量", COLUMNS_IO, items);
    }

    private static ExampleDatasetData buildQaAccuracyDataset() {
        List<String> items = List.of(
                jsonItem("公司的退货政策是什么？", "自购买之日起7天内，商品未使用且包装完好的情况下可申请无理由退货，退款将在3个工作日内到账。"),
                jsonItem("如何重置账户密码？", "进入登录页点击忘记密码，输入注册邮箱，系统将发送重置链接，链接有效期30分钟。"),
                jsonItem("产品支持哪些操作系统？", "支持 Windows 10及以上、macOS 12及以上、Ubuntu 20.04及以上版本。移动端支持 iOS 15+ 和 Android 12+。"),
                jsonItem("数据备份的频率是多久？", "系统每天凌晨3点自动执行全量备份，每4小时执行增量备份，备份数据保留30天。"),
                jsonItem("API的调用频率限制是多少？", "免费版每分钟60次请求，专业版每分钟600次，企业版可自定义限流策略。超出限制返回429状态码。")
        );
        return new ExampleDatasetData("问答准确性评测集", "评测问答系统的回答准确性", COLUMNS_IO, items);
    }

    private static ExampleDatasetData buildTextClassificationDataset() {
        String columns = "[{\"key\":\"input\",\"label\":\"文本\",\"type\":\"text\"},{\"key\":\"reference_output\",\"label\":\"分类\",\"type\":\"text\"}]";
        List<String> items = List.of(
                jsonItem("这个产品太好用了，强烈推荐给大家！", "正向"),
                jsonItem("客服态度很差，等了两个小时才回复", "负向"),
                jsonItem("产品功能基本符合预期，中规中矩", "中性"),
                jsonItem("包装破损，商品有明显划痕，非常失望", "负向"),
                jsonItem("物流很快，第二天就收到了，质量也不错", "正向"),
                jsonItem("价格和描述一致，没有惊喜也没有失望", "中性")
        );
        return new ExampleDatasetData("文本分类评测集", "评测文本分类任务的准确率", columns, items);
    }

    private static ExampleDatasetData buildSummarizationDataset() {
        String columns = "[{\"key\":\"input\",\"label\":\"原文\",\"type\":\"text\"},{\"key\":\"reference_output\",\"label\":\"参考摘要\",\"type\":\"text\"}]";
        List<String> items = List.of(
                jsonItem("2025年，全球人工智能市场规模预计突破5000亿美元。其中，生成式AI增速最快，达到45%。中国AI市场占全球份额约15%，在计算机视觉和自然语言处理领域具有竞争力。主要驱动因素包括算力提升、数据积累和政策支持。", "2025年全球AI市场预计超5000亿美元，生成式AI增速45%，中国占15%份额，在视觉和NLP领域有竞争力。"),
                jsonItem("Spring Boot 3.0 带来了多项重大更新：基于 Spring Framework 6.0，最低要求 Java 17；原生支持 GraalVM 编译；引入 AOT 编译优化启动速度；Jakarta EE 9+ 迁移（javax.* 改为 jakarta.*）；以及对 Observability 的增强支持。", "Spring Boot 3.0 基于 Spring 6.0，要求 Java 17+，支持 GraalVM 和 AOT 编译，迁移至 Jakarta EE 9+，增强可观测性。"),
                jsonItem("某公司Q3财报显示：营收同比增长23%至85亿元，净利润12亿元。云业务收入占比首次超过40%，国际化收入增长58%。研发投入占比维持在18%，员工总数较上季度增加2000人。", "Q3营收85亿元同比增23%，净利12亿元，云业务占比超40%，国际化收入增58%，研发投入占18%。"),
                jsonItem("研究表明，远程办公对员工 productivity 的影响因行业而异。科技行业员工在远程环境下效率提升约15%，而制造业和服务业则下降约8%。混合办公模式（每周2-3天到岗）被证明是最优方案，员工满意度提升22%。", "远程办公效果因行业不同，科技业效率提升15%，制造业下降8%，混合办公满意度提升22%为最优。")
        );
        return new ExampleDatasetData("文本摘要评测集", "评测文本摘要的质量", columns, items);
    }
}
