package com.ruoyi.system.service;

import java.util.Map;

public interface IEduProfileConfirmationService
{
    Map<String, Object> confirmDimension(Long userId, String dimensionKey, Integer profileVersion,
                                         String status, String rejectReason);
}
