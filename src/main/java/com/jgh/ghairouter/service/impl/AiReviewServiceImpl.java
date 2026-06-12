package com.jgh.ghairouter.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.mapper.CompetitionRecordMapper;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.service.AiReviewService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.File;

/**
 * AI自动审核服务实现（基于DashScope多模态模型）
 */
@Slf4j
@Service
public class AiReviewServiceImpl implements AiReviewService {

    @Resource
    private CompetitionRecordMapper competitionRecordMapper;

    @Resource
    private ChatModel chatModel;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final String REVIEW_PROMPT =
            "请仔细查看这张图片，判断图片中的比赛/活动/证书名称是否与\"%s\"一致或高度相关。" +
            "如果图片中的名称与给定名称一致或描述的是同一个比赛/活动，请回复'PASS'并简要说明理由；" +
            "如果不一致或无法判断，请回复'FAIL'并简要说明理由。";

    @Async
    @Override
    public void autoReview(Long recordId, String competitionName, String imageUrl) {
        log.info("开始自动审核记录: recordId={}, competitionName={}", recordId, competitionName);

        String comment;
        String status;

        try {
            // 解析本地文件路径
            String filename = imageUrl.replace("/uploads/", "");
            File imageFile = new File(uploadDir, filename);

            if (!imageFile.exists()) {
                log.error("审核图片不存在: {}", imageFile.getAbsolutePath());
                comment = "图片文件不存在，无法自动审核";
                status = ReviewStatusEnum.FAILED.getValue();
                updateReviewResult(recordId, status, comment);
                return;
            }

            // 构建多模态请求
            String prompt = String.format(REVIEW_PROMPT, competitionName);
            String response = ChatClient.create(chatModel)
                    .prompt()
                    .user(userSpec -> userSpec
                            .text(prompt)
                            .media(MimeTypeUtils.IMAGE_PNG,
                                    new FileSystemResource(imageFile))
                    )
                    .call()
                    .content();

            log.info("AI审核响应: {}", response);

            // 解析响应
            if (StrUtil.isNotBlank(response) && response.toUpperCase().contains("PASS")
                    && !response.toUpperCase().contains("FAIL")) {
                status = ReviewStatusEnum.PASSED.getValue();
                comment = response;
            } else {
                status = ReviewStatusEnum.FAILED.getValue();
                comment = StrUtil.isBlank(response) ? "AI未能给出明确判断" : response;
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
}
