package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.jgh.ghairouter.mapper.CompetitionRecordMapper;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.service.AiReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * AI自动审核服务实现（使用 DashScope OpenAI 兼容 API 直接调用视觉模型）
 */
@Slf4j
@Service
public class AiReviewServiceImpl implements AiReviewService {

    @Resource
    private CompetitionRecordMapper competitionRecordMapper;

    /**
     * 使用的视觉模型
     */
    @Value("${spring.ai.dashscope.chat.options.model:qwen-vl-plus}")
    private String model;

    /**
     * DashScope API Key
     */
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    /**
     * DashScope OpenAI 兼容端点
     */
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /**
     * 审核提示词
     */
    private static final String REVIEW_PROMPT = """
            你是比赛记录审核助手，仅执行图片文字和用户输入名称的比对审核，严格遵守全部规则，只返回标准JSON字符串，禁止输出任何额外文字、注释、说明、思考过程、换行、markdown标记。
            
            ## 审核判断标准
            1. 图片识别出的赛事/证书/活动名称 和 用户提供名称完全一致 → isPass: true
            2. 图片名称是目标名称的标准简称、官方全称、合理同义变体 → isPass: true
            3. 图片名称和目标名称差异明显、不属于同义变体 → isPass: false
            4. 图片模糊、文字残缺、无法识别完整名称、无法确认匹配度 → isPass: false
            5. 存在任何不确定、模棱两可的场景，统一判定为不通过，不得折中
            
            ## 强制输出规范
            1. 唯一输出格式（固定JSON结构，字段不可增删改名）：{"isPass": 布尔值, "content": "具体判断依据文本"}
            2. 布尔值仅允许 true / false，不加引号；content必须填写具象、客观的理由，禁止模糊描述。
            3. 全程只输出一行纯净JSON字符串，不能添加：```、json、前置说明、思考过程、换行、#、解释文字、多余空格、分段描述。（required）
            4. 输出前强制自检：①是否只有JSON ②字段名称正确 ③布尔值格式无误 ④理由具体客观，不符合则重新生成。
            
            ## 正确输出样例参考
            {"isPass":true,"content":"图片证书文字为2026大学生程序设计大赛，用户输入全称一致，匹配通过"}
            {"isPass":false,"content":"图片显示活动名称校园歌手赛，用户输入为全国机器人大赛，名称完全不相关，判定不通过"}
            """;

    /**
     * HTTP 客户端
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Async
    @Override
    public void autoReview(Long recordId, String competitionName, String imageBase64, String mimeType) {
        log.info("开始自动审核记录: recordId={}, competitionName={}", recordId, competitionName);

        String comment;
        String status;

        try {
            if (StrUtil.isBlank(imageBase64)) {
                throw new RuntimeException("图片数据为空");
            }

            // 解码 base64 → 调用视觉模型识别图片
            byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(REVIEW_PROMPT);
            stringBuilder.append("\n");
            stringBuilder.append("用户输入：");
            stringBuilder.append("比赛名称：" + competitionName);
            String aiResponse = recognizeImage(imageBytes, mimeType, stringBuilder.toString());

            log.info("AI审核响应: {}", aiResponse);

            // 解析 JSON 响应：{"isPass": true/false, "content": "理由"}
            try {
                JSONObject result = JSONUtil.parseObj(aiResponse);
                Boolean isPass = result.getBool("isPass", false);
                String content = result.getStr("content", aiResponse);
                if (Boolean.TRUE.equals(isPass)) {
                    status = ReviewStatusEnum.PASSED.getValue();
                } else {
                    status = ReviewStatusEnum.FAILED.getValue();
                }
                comment = StrUtil.isBlank(content) ? aiResponse : content;
            } catch (Exception e) {
                // JSON 解析失败，视为审核不通过
                log.warn("AI返回格式异常，按FAILED处理: {}", aiResponse);
                status = ReviewStatusEnum.FAILED.getValue();
                comment = aiResponse;
            }
        } catch (Exception e) {
            log.error("AI自动审核异常", e);
            status = ReviewStatusEnum.FAILED.getValue();
            comment = "AI自动审核服务异常: " + e.getMessage();
        }

        updateReviewResult(recordId, status, comment);
    }

    private void updateReviewResult(Long recordId, String status, String comment) {
        CompetitionRecord record = competitionRecordMapper.selectOneById(recordId);
        if (record != null) {
            record.setAutoReviewStatus(status);
            record.setAutoReviewComment(comment);
            competitionRecordMapper.update(record);
            log.info("自动审核完成: recordId={}, status={}", recordId, status);
        }
    }

    /**
     * 调用视觉模型识别图片
     * 使用 DashScope OpenAI 兼容 API 直接调用通义千问视觉模型
     *
     * @param imageBytes 图片字节数据
     * @param mimeType   图片 MIME 类型（如 image/png）
     * @param prompt     提示词
     * @return 模型返回的文本内容
     */
    private String recognizeImage(byte[] imageBytes, String mimeType, String prompt) {
        try {
            // 将图片转换为 Base64 data URL
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String imageUrl = "data:" + mimeType + ";base64," + base64Image;

            // 构建请求体（OpenAI 兼容格式）
            JSONObject requestBody = new JSONObject();
            requestBody.set("model", model);

            // 构建用户消息（多模态：图片 + 文本）
            JSONObject userMessage = new JSONObject();
            userMessage.set("role", "user");

            JSONArray contentArray = new JSONArray();

            // 图片部分
            JSONObject imageContent = new JSONObject();
            imageContent.set("type", "image_url");
            JSONObject imageUrlObj = new JSONObject();
            imageUrlObj.set("url", imageUrl);
            imageContent.set("image_url", imageUrlObj);
            contentArray.add(imageContent);

            // 文本部分
            JSONObject textContent = new JSONObject();
            textContent.set("type", "text");
            textContent.set("text", prompt);
            contentArray.add(textContent);

            userMessage.set("content", contentArray);

            JSONArray messagesArray = new JSONArray();
            messagesArray.add(userMessage);
            requestBody.set("messages", messagesArray);

            // 发送 HTTP 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("视觉模型调用失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
                throw new RuntimeException("视觉模型调用失败，状态码: " + response.statusCode());
            }

            // 解析 OpenAI 格式响应
            JSONObject responseJson = JSONUtil.parseObj(response.body());
            JSONArray choices = responseJson.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                if (message != null) {
                    return message.getStr("content", "无法识别图片内容");
                }
            }

            throw new RuntimeException("模型返回结果格式错误");
        } catch (Exception e) {
            log.error("调用视觉模型失败", e);
            throw new RuntimeException("调用视觉模型失败: " + e.getMessage());
        }
    }
}
