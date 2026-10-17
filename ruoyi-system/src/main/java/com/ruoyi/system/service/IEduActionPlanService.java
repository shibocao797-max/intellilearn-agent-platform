package com.ruoyi.system.service;
import java.util.List; import java.util.Map;
public interface IEduActionPlanService { Map<String,Object> generate(Long userId); List<Map<String,Object>> list(Long userId); Map<String,Object> current(Long userId); Map<String,Object> detail(Long userId,Long planId); Map<String,Object> confirm(Long userId,Long planId); Map<String,Object> archive(Long userId,Long planId); Map<String,Object> regenerate(Long userId,Long planId); Map<String,Object> updateItemStatus(Long userId,Long itemId,String status); }
