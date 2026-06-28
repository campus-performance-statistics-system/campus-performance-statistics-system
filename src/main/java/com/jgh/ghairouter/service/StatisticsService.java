package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;

import java.util.List;

/**
 * 统计管理服务接口
 */
public interface StatisticsService {

    /**
     * 获取用户的总得分统计
     *
     * @param type     统计类型：all（所有）、teacher（教师获奖）、student（指导学生科技竞赛）、training（指导实训）、research（科研及教材业绩）、innovation（大创业绩）、teachingReform（教改科研项目业绩）、thesis（论文业绩）、sports（体育比赛业绩）、advisor（兼职班主任）
     * @param sortOrder 排序方式：ascend（升序）、descend（降序，默认）
     * @param userName 用户名（可选，为空时返回所有用户）
     * @return 用户得分统计列表
     */
    List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder, String userName);

    /**
     * 导出所有比赛分类的附件为ZIP压缩包。
     * ZIP 根目录为"所有附件"，按比赛分类建子文件夹，
     * 文件名格式：序号-比赛名称-用户姓名.png
     */
    byte[] exportAllAttachmentsToZip();
}
