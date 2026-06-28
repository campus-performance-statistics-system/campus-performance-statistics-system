package com.jgh.ghairouter.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 体育比赛业绩记录 实体类（v9 新增）。
 * 包含运动会项目（田径等）和球类项目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sports_event_record")
public class SportsEventRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 记录类型名称（固定为"体育比赛业绩"） */
    @Column("type_name")
    private String typeName;

    /** 填报用户ID */
    @Column("user_id")
    private Long userId;

    /** 比赛项目名称 */
    @Column("event_name")
    private String eventName;

    /** 项目类型：track_field-运动会项目, ball_game-球类项目 */
    @Column("event_type")
    private String eventType;

    /** 球类项目结果：champion-冠军, placed-获奖, participated-参与 */
    @Column("event_result")
    private String eventResult;

    /** 参与教师及名次JSON数组 */
    @Column("member_data")
    private String memberData;

    /** 得分明细JSON数组 */
    @Column("score_data")
    private String scoreData;

    /** 证明图片（base64数据） */
    @Column("proof_image_data")
    private String proofImageData;

    /** 创建时间 */
    @Column("create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column("update_time")
    private LocalDateTime updateTime;

    /** 是否删除 */
    @Column(value = "is_delete", isLogicDelete = true)
    private Integer isDelete;
}
