package com.ruoyi.common.constant;

/**
 * 用户类型常量
 *
 * @author ruoyi
 */
public class UserType
{
    /** 系统管理员 */
    public static final String ADMIN = "00";

    /** 普通用户 */
    public static final String USER = "01";

    // 后续扩展：
    // public static final String TEACHER = "02";
    // public static final String STUDENT = "03";

    /** 判断是否为管理员 */
    public static boolean isAdmin(String userType)
    {
        return ADMIN.equals(userType);
    }
}
