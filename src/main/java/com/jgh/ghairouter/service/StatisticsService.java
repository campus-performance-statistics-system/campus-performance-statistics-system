package com.jgh.ghairouter.service;

import com.jgh.ghairouter.model.vo.UserScoreStatisticsVO;

import java.util.List;

/**
 * 统计管理服务接口
 */
public interface StatisticsService {

    /**
     * 获取所有用户的总得分统计
     *
     * @param type 统计类型：all（所有）、teacher（教师获奖）、student（指导学生科技竞赛）
     * @param sortOrder 排序方式：ascend（升序）、descend（降序，默认）
     * @return 用户得分统计列表
     */
    List<UserScoreStatisticsVO> getUserScoreStatistics(String type, String sortOrder);
}
