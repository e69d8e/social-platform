package com.li.socialplatform.handler;

import com.alibaba.fastjson2.JSON;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.pojo.entity.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class MyAuthenticationFailHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        Object json = JSON.toJSON(Result.error(MessageConstant.LOGIN_FAILED));
        response.getWriter().write(json.toString());
    }
}
