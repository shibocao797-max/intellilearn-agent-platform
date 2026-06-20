package com.ruoyi.system.mapper;

import java.util.List;
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
}
