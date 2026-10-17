package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.EduResource;

/**
 * 教育资源表 数据层
 *
 * @author ruoyi
 */
public interface EduResourceMapper
{
    /**
     * 查询资源详情
     *
     * @param id 资源ID
     * @return 资源信息
     */
    public EduResource selectResourceById(Long id);

    /**
     * 查询当前用户拥有的资源，供用户端详情、播放和删除操作使用。
     */
    public EduResource selectResourceByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 查询资源列表
     *
     * @param resource 资源信息
     * @return 资源集合
     */
    public List<EduResource> selectResourceList(EduResource resource);

    /**
     * 新增资源
     *
     * @param resource 资源信息
     * @return 结果
     */
    public int insertResource(EduResource resource);

    /**
     * 更新资源状态
     *
     * @param resource 资源信息（id/status/updateBy 必填）
     * @return 结果
     */
    public int updateResourceStatus(EduResource resource);

    /**
     * 更新媒体资源结果
     *
     * @param resource 资源信息
     * @return 结果
     */
    public int updateMediaResource(EduResource resource);

    /**
     * 仅删除归属于当前用户的资源，避免按裸 ID 越权删除。
     */
    public int deleteResourceByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
