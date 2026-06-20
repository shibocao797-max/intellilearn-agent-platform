package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.LearnerProfile;

/**
 * 学习者画像表 Mapper 接口
 *
 * 支持：按用户ID查询、插入、全量覆盖更新、存在性检查。
 *
 * @author edu-agent
 */
public interface LearnerProfileMapper
{
    /**
     * 根据用户 ID 查询画像（user_id 有唯一索引，至多一条）
     *
     * @param userId 用户ID
     * @return 画像实体，不存在则返回 null
     */
    public LearnerProfile selectProfileByUserId(Long userId);

    /**
     * 插入新画像（首次建立画像时调用）
     *
     * @param profile 画像实体
     * @return 插入行数
     */
    public int insertProfile(LearnerProfile profile);

    /**
     * 全量覆盖更新画像（每次画像抽取后调用）
     *
     * @param profile 画像实体（含新的 profileJson / version / dimensionCount）
     * @return 更新行数
     */
    public int updateProfile(LearnerProfile profile);

    /**
     * 判断指定用户是否已有画像记录
     *
     * @param userId 用户ID
     * @return true=已存在
     */
    public boolean existsByUserId(Long userId);

    /**
     * 查询画像历史版本列表（最近20条，供"学习时间机器"使用）
     */
    public java.util.List<LearnerProfile> selectHistoryByUserId(Long userId);
}
