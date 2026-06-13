package com.jgh.ghairouter.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.jgh.ghairouter.exception.BusinessException;
import com.jgh.ghairouter.exception.ErrorCode;
import com.jgh.ghairouter.mapper.CategoryMapper;
import com.jgh.ghairouter.mapper.CompetitionRecordMapper;
import com.jgh.ghairouter.mapper.UserMapper;
import com.jgh.ghairouter.model.dto.competition.CompetitionQueryRequest;
import com.jgh.ghairouter.model.entity.Category;
import com.jgh.ghairouter.model.entity.CompetitionRecord;
import com.jgh.ghairouter.model.entity.User;
import com.jgh.ghairouter.model.enums.ReviewStatusEnum;
import com.jgh.ghairouter.model.vo.CompetitionRecordVO;
import com.jgh.ghairouter.service.AiReviewService;
import com.jgh.ghairouter.service.CompetitionRecordService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 比赛记录 服务层实现
 */
@Slf4j
@Service
public class CompetitionRecordServiceImpl extends ServiceImpl<CompetitionRecordMapper, CompetitionRecord>
        implements CompetitionRecordService {

    @Resource
    private AiReviewService aiReviewService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private CategoryMapper categoryMapper;


    @Override
    public Long addRecord(Long userId, Long categoryId, String awardLevel,
                          String firstAuthor, List<String> authors, MultipartFile file) {
        // 校验分类是否存在
        Category category = categoryMapper.selectOneById(categoryId);
        if (category == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "比赛分类不存在");
        }

        // 比赛名称 = 所选子分类名称
        String competitionName = category.getName();

        // 读取文件并转为 base64
        String base64 = null;
        try {
            byte[] bytes = file.getBytes();
            base64 = Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取文件失败");
        }

        // 创建记录
        CompetitionRecord record = new CompetitionRecord();
        record.setUserId(userId);
        record.setCategoryId(categoryId);
        record.setCompetitionName(competitionName);
        record.setAwardLevel(awardLevel);
        record.setFirstAuthor(firstAuthor);
        if (CollUtil.isNotEmpty(authors)) {
            record.setOtherAuthors(String.join(",", authors));
        }
        record.setProofImageData(base64);
        record.setAutoReviewStatus(ReviewStatusEnum.PENDING.getValue());
        record.setAdminReviewStatus(ReviewStatusEnum.PENDING.getValue());

        //  获取文件后缀
        String fileExtension = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));

        boolean saved = this.save(record);
        if (!saved) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交比赛记录失败");
        }

        // 异步触发AI自动审核（传递 base64 数据）
        try {
            aiReviewService.autoReview(record.getId(), competitionName, base64, fileExtension);
        } catch (Exception e) {
            log.error("触发AI自动审核失败", e);
        }

        return record.getId();
    }

    @Override
    public QueryWrapper getQueryWrapper(CompetitionQueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = queryRequest.getId();
        Long userId = queryRequest.getUserId();
        Long categoryId = queryRequest.getCategoryId();
        String competitionName = queryRequest.getCompetitionName();
        String autoReviewStatus = queryRequest.getAutoReviewStatus();
        String adminReviewStatus = queryRequest.getAdminReviewStatus();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        QueryWrapper wrapper = QueryWrapper.create()
                .eq("id", id)
                .eq("user_id", userId)
                .eq("category_id", categoryId)
                .eq("auto_review_status", autoReviewStatus)
                .eq("admin_review_status", adminReviewStatus)
                .like("competition_name", competitionName);

        // 如果有指定排序字段，优先使用
        if (StrUtil.isNotBlank(sortField)) {
            wrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        // 默认按创建时间倒序
        wrapper.orderBy("create_time", false);

        return wrapper;
    }

    @Override
    public Page<CompetitionRecordVO> pageRecords(CompetitionQueryRequest queryRequest) {
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();

        Page<CompetitionRecord> recordPage = this.page(Page.of(pageNum, pageSize),
                getQueryWrapper(queryRequest));

        List<CompetitionRecordVO> voList = recordPage.getRecords().stream()
                .map(this::getRecordVO)
                .collect(Collectors.toList());

        Page<CompetitionRecordVO> voPage = new Page<>(pageNum, pageSize, recordPage.getTotalRow());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public CompetitionRecordVO getRecordVO(CompetitionRecord record) {
        if (record == null) {
            return null;
        }
        CompetitionRecordVO vo = new CompetitionRecordVO();
        BeanUtil.copyProperties(record, vo);

        // 填充用户名
        if (record.getUserId() != null) {
            User user = userMapper.selectOneById(record.getUserId());
            if (user != null) {
                vo.setUserName(user.getUserName());
            }
        }

        // 填充分类名
        if (record.getCategoryId() != null) {
            Category category = categoryMapper.selectOneById(record.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        // 填充审核管理员名
        if (record.getAdminId() != null) {
            User admin = userMapper.selectOneById(record.getAdminId());
            if (admin != null) {
                vo.setAdminName(admin.getUserName());
            }
        }

        return vo;
    }

    @Override
    public void adminReview(Long recordId, String reviewStatus, String reviewComment, Long adminId) {
        if (recordId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "记录ID不能为空");
        }
        if (StrUtil.isBlank(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为空");
        }

        ReviewStatusEnum statusEnum = ReviewStatusEnum.getEnumByValue(reviewStatus);
        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的审核状态");
        }
        if (statusEnum == ReviewStatusEnum.PENDING) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不能为待审核");
        }

        CompetitionRecord record = this.getById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "比赛记录不存在");
        }

        // 如果审核通过且未填写意见，默认填写"审核通过"
        if (statusEnum == ReviewStatusEnum.PASSED && StrUtil.isBlank(reviewComment)) {
            reviewComment = "审核通过";
        }

        record.setAdminReviewStatus(reviewStatus);
        record.setAdminReviewComment(reviewComment);
        record.setAdminId(adminId);
        record.setAdminReviewTime(LocalDateTime.now());

        boolean updated = this.updateById(record);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核失败");
        }
    }
}
