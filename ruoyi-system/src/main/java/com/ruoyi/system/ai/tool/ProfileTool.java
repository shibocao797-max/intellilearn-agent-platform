package com.ruoyi.system.ai.tool;

import com.ruoyi.system.domain.LearnerProfile;
import com.ruoyi.system.mapper.LearnerProfileMapper;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 画像工具 — 学习画像的加载、保存、合并
 *
 * @author edu-agent
 */
@Component
public class ProfileTool {

    private static final Logger log = LoggerFactory.getLogger(ProfileTool.class);

    @Autowired
    private LearnerProfileMapper learnerProfileMapper;

    /**
     * 加载用户的学习画像（从 MySQL 查询）
     */
    public String loadProfile(Long userId) {
        LearnerProfile lp = learnerProfileMapper.selectProfileByUserId(userId);
        if (lp == null || lp.getProfileJson() == null) {
            return "{}";
        }
        log.debug("画像已加载: userId={}, version={}", userId, lp.getVersion());
        return lp.getProfileJson();
    }

    /**
     * 保存学习画像到 MySQL
     */
    public void saveProfile(Long userId, String profileJson) {
        if (profileJson == null || profileJson.isBlank()) {
            log.warn("尝试保存空白画像，跳过: userId={}", userId);
            return;
        }
        try {
            int version = 0;
            int dimensionCount = 0;
            try {
                JSONObject json = JSON.parseObject(profileJson);
                version = json.getIntValue("version", 0);
                dimensionCount = json.getIntValue("dimensionCount", 0);
            } catch (Exception ignore) {
                // 解析失败仍存储
            }

            LearnerProfile lp = new LearnerProfile();
            lp.setUserId(userId);
            lp.setProfileJson(profileJson);
            lp.setVersion(version);
            lp.setDimensionCount(dimensionCount);
            lp.setLastExtractAt(new Date());

            if (learnerProfileMapper.existsByUserId(userId)) {
                learnerProfileMapper.updateProfile(lp);
                log.info("画像已更新: userId={}, version={}, dimensions={}",
                        userId, version, dimensionCount);
            } else {
                learnerProfileMapper.insertProfile(lp);
                log.info("画像已创建: userId={}, version={}, dimensions={}",
                        userId, version, dimensionCount);
            }
        } catch (Exception e) {
            log.error("画像保存失败: userId={}, error={}", userId, e.getMessage(), e);
        }
    }

    /**
     * 合并画像 — 新特征覆盖旧特征，不丢失旧信息
     */
    public String mergeProfile(String oldProfile, String newFeatures) {
        if (newFeatures == null || newFeatures.isBlank() || "{}".equals(newFeatures.trim())) {
            return oldProfile;
        }
        try {
            var oldJson = JSON.parseObject(oldProfile);
            var newJson = JSON.parseObject(newFeatures);

            // 合并 dimensions
            if (newJson.containsKey("dimensions")) {
                var oldDims = oldJson.getJSONObject("dimensions");
                var newDims = newJson.getJSONObject("dimensions");
                if (oldDims == null) {
                    oldJson.put("dimensions", newDims);
                } else {
                    for (String key : newDims.keySet()) {
                        oldDims.put(key, newDims.get(key));
                    }
                }
            }

            // 更新版本号
            int version = oldJson.getIntValue("version", 0) + 1;
            oldJson.put("version", version);
            oldJson.put("lastUpdated", LocalDateTime.now().toString());

            // 计算维度数量
            var dims = oldJson.getJSONObject("dimensions");
            if (dims != null) {
                oldJson.put("dimensionCount", dims.size());
            }

            log.info("画像合并完成: version={}, dimensions={}", version,
                    dims != null ? dims.size() : 0);
            return oldJson.toJSONString();
        } catch (Exception e) {
            log.error("画像合并失败: {}", e.getMessage());
            return oldProfile;
        }
    }

    /**
     * 检查画像是否在冷却期内（避免高频 LLM 调用更新画像）
     */
    public boolean isInCooldown(String profileJson, int cooldownMinutes) {
        if (profileJson == null || profileJson.equals("{}")) {
            return false;
        }
        try {
            var json = JSON.parseObject(profileJson);
            String lastUpdateStr = json.getString("lastUpdated");
            if (lastUpdateStr == null || lastUpdateStr.isBlank()) return false;
            var lastUpdate = LocalDateTime.parse(lastUpdateStr);
            var cooldownThreshold = LocalDateTime.now().minusMinutes(cooldownMinutes);
            return lastUpdate.isAfter(cooldownThreshold);
        } catch (Exception e) {
            log.debug("冷却期检查失败（默认不冷却）: {}", e.getMessage());
            return false;
        }
    }
}
