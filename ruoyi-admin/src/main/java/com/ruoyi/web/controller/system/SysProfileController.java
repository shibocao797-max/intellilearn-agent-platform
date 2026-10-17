package com.ruoyi.web.controller.system;

import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileStorageService;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 个人信息 业务处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController
{
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;
    private static final long MAX_AVATAR_PIXELS = 16_000_000L;
    private static final String[] AVATAR_ALLOWED_EXTENSIONS = { "jpg", "jpeg", "png", "gif" };
    private static final String AVATAR_STORAGE_DIRECTORY = "avatar";

    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 个人信息
     */
    @GetMapping
    public AjaxResult profile()
    {
        LoginUser loginUser = getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success(user);
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUsername()));
        ajax.put("postGroup", userService.selectUserPostGroup(loginUser.getUsername()));
        return ajax;
    }

    /**
     * 修改用户
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user)
    {
        LoginUser loginUser = getLoginUser();
        SysUser currentUser = loginUser.getUser();
        currentUser.setNickName(user.getNickName());
        currentUser.setEmail(user.getEmail());
        currentUser.setPhonenumber(user.getPhonenumber());
        currentUser.setSex(user.getSex());
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(currentUser))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        if (userService.updateUserProfile(currentUser) > 0)
        {
            // 更新缓存用户信息
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE)
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@RequestBody Map<String, String> params)
    {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        LoginUser loginUser = getLoginUser();
        Long userId = loginUser.getUserId();
        SysUser user = userService.selectUserById(userId);
        String password = user.getPassword();
        if (!SecurityUtils.matchesPassword(oldPassword, password))
        {
            return error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(newPassword, password))
        {
            return error("新密码不能与旧密码相同");
        }
        newPassword = SecurityUtils.encryptPassword(newPassword);
        if (userService.resetUserPwd(userId, newPassword) > 0)
        {
            // 更新缓存用户密码&密码最后更新时间
            loginUser.getUser().setPwdUpdateDate(DateUtils.getNowDate());
            loginUser.getUser().setPassword(newPassword);
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改密码异常，请联系管理员");
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file) throws Exception
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择头像图片后再上传");
        }
        if (file.getSize() > MAX_AVATAR_SIZE)
        {
            return error("头像图片不能超过 5 MB");
        }
        String extension = FileUploadUtils.getExtension(file);
        if (!FileUploadUtils.isAllowedExtension(extension, AVATAR_ALLOWED_EXTENSIONS))
        {
            return error("头像仅支持 JPG、PNG 或 GIF 图片");
        }

        BufferedImage image;
        try (var input = file.getInputStream())
        {
            image = ImageIO.read(input);
        }
        if (image == null)
        {
            return error("头像文件不是有效图片");
        }
        if ((long) image.getWidth() * image.getHeight() > MAX_AVATAR_PIXELS)
        {
            return error("头像图片分辨率不能超过 1600 万像素");
        }

        byte[] safeAvatar;
        try (var output = new ByteArrayOutputStream())
        {
            if (!ImageIO.write(image, "png", output))
            {
                return error("头像图片格式无法处理");
            }
            safeAvatar = output.toByteArray();
        }

        LoginUser loginUser = getLoginUser();
        // 头像可被公开读取：先解码、再转码为 PNG，杜绝 SVG 和伪装扩展名内容。
        String avatar = fileStorageService.uploadBytes(
                AVATAR_STORAGE_DIRECTORY,
                "avatar-" + UUID.randomUUID() + ".png",
                safeAvatar,
                "image/png");
        if (userService.updateUserAvatar(loginUser.getUserId(), avatar))
        {
            String oldAvatar = loginUser.getUser().getAvatar();
            if (StringUtils.isNotEmpty(oldAvatar))
            {
                fileStorageService.delete(oldAvatar);
            }
            AjaxResult ajax = AjaxResult.success();
            ajax.put("imgUrl", avatar);
            loginUser.getUser().setAvatar(avatar);
            tokenService.setLoginUser(loginUser);
            return ajax;
        }
        return error("上传图片异常，请联系管理员");
    }
}
