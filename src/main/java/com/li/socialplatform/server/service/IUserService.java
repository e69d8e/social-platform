package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.LoginDTO;
import com.li.socialplatform.pojo.dto.RefreshDTO;
import com.li.socialplatform.pojo.dto.UserDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface IUserService extends IService<User> {
    Result login(LoginDTO loginDTO);

    Result refresh(RefreshDTO refreshDTO, HttpServletRequest request);

    Result register(UserDTO userDTO);

    Result getUserProfile(Long id);

    Result updateUserProfile(UserDTO userDTO);

    Result updatePassword(UserDTO userDTO, HttpServletRequest request);

    Result signIn();

    Result signInCount();

    /**
     * 搜索帖子
     *
     * @param record 是否把关键词写入搜索历史。顶栏输入框的实时联想必须传 false，
     *               否则每敲一个字都会产生一条历史记录（历史被无意义前缀刷屏）。
     */
    Result listPost(String keyword, Integer pageNum, Integer pageSize, Integer categoryId, boolean record);

    /**
     * 搜索用户
     *
     * @param record 是否把关键词写入搜索历史（同 {@link #listPost}）
     */
    Result listUser(String keyword, Integer pageNum, Integer pageSize, boolean record);

    Result logout(HttpServletRequest request);
}
