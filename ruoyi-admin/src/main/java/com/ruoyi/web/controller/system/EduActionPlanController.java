package com.ruoyi.web.controller.system;
import com.ruoyi.common.core.controller.BaseController; import com.ruoyi.common.core.domain.AjaxResult; import com.ruoyi.common.utils.SecurityUtils; import com.ruoyi.system.service.IEduActionPlanService; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestController @RequestMapping("/edu/action-plans") public class EduActionPlanController extends BaseController {
 @Autowired private IEduActionPlanService actionPlanService;
 @PostMapping("/generate") public AjaxResult generate(){return success(actionPlanService.generate(SecurityUtils.getUserId()));}
 @GetMapping public AjaxResult list(){return success(actionPlanService.list(SecurityUtils.getUserId()));}
 @GetMapping("/current") public AjaxResult current(){return success(actionPlanService.current(SecurityUtils.getUserId()));}
 @GetMapping("/{planId}") public AjaxResult detail(@PathVariable Long planId){return success(actionPlanService.detail(SecurityUtils.getUserId(),planId));}
 @PostMapping("/{planId}/confirm") public AjaxResult confirm(@PathVariable Long planId){return success(actionPlanService.confirm(SecurityUtils.getUserId(),planId));}
 @PostMapping("/{planId}/archive") public AjaxResult archive(@PathVariable Long planId){return success(actionPlanService.archive(SecurityUtils.getUserId(),planId));}
 @PostMapping("/{planId}/regenerate") public AjaxResult regenerate(@PathVariable Long planId){return success(actionPlanService.regenerate(SecurityUtils.getUserId(),planId));}
 @PatchMapping("/items/{itemId}") public AjaxResult updateItem(@PathVariable Long itemId,@RequestBody Map<String,String> body){return success(actionPlanService.updateItemStatus(SecurityUtils.getUserId(),itemId,body.get("status")));}
}
