package com.ruoyi.system.mapper;
import com.ruoyi.system.domain.EduActionPlan;
import com.ruoyi.system.domain.EduActionPlanItem;
import java.util.List;
public interface EduActionPlanMapper {
 int insertPlan(EduActionPlan plan); int insertItem(EduActionPlanItem item); EduActionPlan selectPlanByIdAndUserId(Long id, Long userId); List<EduActionPlan> selectPlansByUserId(Long userId); EduActionPlan selectCurrentPlanByUserId(Long userId); List<EduActionPlanItem> selectItemsByPlanIdAndUserId(Long planId, Long userId); int activatePlan(Long id, Long userId); int archivePlan(Long id, Long userId); int archivePlanCurrent(Long userId); int updateItemStatus(Long itemId, Long userId, String status); EduActionPlanItem selectItemByIdAndUserId(Long itemId, Long userId);
}
