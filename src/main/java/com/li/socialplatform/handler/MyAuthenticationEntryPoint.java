package com.li.socialplatform.handler;

import com.alibaba.fastjson2.JSON;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.pojo.entity.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class MyAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        String message = request.getAttribute("TOKEN_BLACKLISTED") != null
                ? "登录凭证已失效，请重新登录"
                : MessageConstant.USER_NOT_LOGIN;
        Object json = JSON.toJSON(Result.error(message));
        response.getWriter().write(json.toString());
    }
}
