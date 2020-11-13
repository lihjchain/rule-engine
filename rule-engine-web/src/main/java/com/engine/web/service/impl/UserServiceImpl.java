package com.engine.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.engine.web.enums.HtmlTemplatesEnum;
import com.engine.web.enums.VerifyCodeType;
import com.engine.web.interceptor.AbstractTokenInterceptor;
import com.engine.web.interceptor.AuthInterceptor;
import com.engine.web.service.UserService;
import com.engine.web.store.entity.RuleEngineUser;
import com.engine.web.store.manager.RuleEngineUserManager;
import com.engine.web.util.*;
import com.engine.web.vo.user.*;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author liqian
 * @date 2020/9/24
 */
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private RuleEngineUserManager ruleEngineUserManager;

    @Resource
    private RedissonClient redissonClient;
    @Resource
    private EmailClient emailClient;

    /**
     * 注册时验证码存入redis的前缀
     */
    private static final String REGISTER_EMAIL_CODE_PRE = "rule_engine_user_register_email_code_pre";
    /**
     * 忘记密码时验证码存入redis的前缀
     */
    private static final String FORGOT_EMAIL_CODE_PRE = "rule_engine_boot_user_forgot_email_code_pre";

    @Override
    public boolean login(LoginRequest loginRequest) {
        RuleEngineUser ruleEngineUser = ruleEngineUserManager.lambdaQuery()
                .and(a -> a.eq(RuleEngineUser::getUsername, loginRequest.getUsername())
                        .or().eq(RuleEngineUser::getEmail, loginRequest.getUsername())).one();
        if (ruleEngineUser == null) {
            throw new ValidationException("用户名/邮箱不存在!");
        }
        if (!(ruleEngineUser.getPassword().equals(MD5Utils.encrypt(loginRequest.getPassword())))) {
            throw new ValidationException("登录密码错误!");
        }
        String token = JWTUtils.genderToken(String.valueOf(ruleEngineUser.getId()), "boot", ruleEngineUser.getUsername());
        CookieUtils.set(AbstractTokenInterceptor.TOKEN, token);
        RBucket<Object> bucket = redissonClient.getBucket(token);
        //保存到redis,用户访问时获取
        bucket.set(ruleEngineUser, JWTUtils.keepTime, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public Boolean register(RegisterRequest registerRequest) {
        checkVerifyCode(registerRequest.getEmail(), registerRequest.getCode(), REGISTER_EMAIL_CODE_PRE);
        VerifyNameRequest request = new VerifyNameRequest();
        request.setUsername(registerRequest.getUsername());
        if (verifyName(request)) {
            throw new ValidationException("用户名已经存在!");
        }
        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail(registerRequest.getEmail());
        if (verifyEmail(verifyEmailRequest)) {
            throw new ValidationException("邮箱已经存在!");
        }
        RuleEngineUser ruleEngineUser = new RuleEngineUser();
        ruleEngineUser.setUsername(registerRequest.getUsername());
        ruleEngineUser.setPassword(MD5Utils.encrypt(registerRequest.getPassword()));
        ruleEngineUser.setEmail(registerRequest.getEmail());
        ruleEngineUserManager.save(ruleEngineUser);
        return true;
    }

    /**
     * 验证用户名是否重复
     *
     * @param verifyNameRequest verifyNameRequest
     * @return Boolean
     */
    @Override
    public Boolean verifyName(VerifyNameRequest verifyNameRequest) {
        return null != ruleEngineUserManager.lambdaQuery()
                .eq(RuleEngineUser::getUsername, verifyNameRequest.getUsername())
                .one();
    }

    /**
     * 忘记密码获取验证码
     *
     * @param verifyCodeByEmailRequest 邮箱/类型:注册,忘记密码
     * @return BaseResult
     */
    @Override
    public Boolean verifyCodeByEmail(GetVerifyCodeByEmailRequest verifyCodeByEmailRequest) {
        Integer type = verifyCodeByEmailRequest.getType();
        String email = verifyCodeByEmailRequest.getEmail();
        if (VerifyCodeType.FORGOT.getValue().equals(type)) {
            //忘记密码时检查此邮箱在本系统中是否存在
            VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
            verifyEmailRequest.setEmail(email);
            if (!verifyEmail(verifyEmailRequest)) {
                throw new ValidationException("你输入的邮箱账号在本系统中不存在");
            }
            //获取验证码时,把当前邮箱获取的验证码存入到redis,备用
            verifyCodeProcess(FORGOT_EMAIL_CODE_PRE, email);
        } else if (VerifyCodeType.REGISTER.getValue().equals(type)) {
            //注册获取验证码,获取验证码时,把当前邮箱获取的验证码存入到redis,备用
            verifyCodeProcess(REGISTER_EMAIL_CODE_PRE, email);
        } else {
            throw new ValidationException("不支持的类型");
        }
        return true;
    }

    /**
     * 验证邮箱是否重复
     *
     * @param verifyEmailRequest verifyEmailRequest
     * @return Boolean
     */
    @Override
    public Boolean verifyEmail(VerifyEmailRequest verifyEmailRequest) {
        return null != ruleEngineUserManager.lambdaQuery()
                .eq(RuleEngineUser::getEmail, verifyEmailRequest.getEmail())
                .one();
    }

    /**
     * 发送验证码消息
     *
     * @param pre   pre
     * @param email 邮箱
     */
    private void verifyCodeProcess(String pre, String email) {
        RBucket<Integer> rBucket = redissonClient.getBucket(pre + IPUtils.getRequestIp() + email);
        //生成验证码
        int randomCode = (int) ((Math.random() * 9 + 1) * 10000);
        //设置有效期10分钟
        rBucket.set(randomCode, 600, TimeUnit.SECONDS);
        Map<Object, Object> params = new HashMap<>(1);
        params.put("code", randomCode);
        //发送验证码邮件
        emailClient.sendSimpleMail(params, HtmlTemplatesEnum.EMAIL.getMsg(), HtmlTemplatesEnum.EMAIL.getValue(), email);
    }

    /**
     * 修改密码
     *
     * @param forgotRequest forgotRequest
     * @return Boolean
     */
    @Override
    public Boolean updatePassword(ForgotRequest forgotRequest) {
        checkVerifyCode(forgotRequest.getEmail(), forgotRequest.getCode(), FORGOT_EMAIL_CODE_PRE);
        RuleEngineUser ruleEngineUser = new RuleEngineUser();
        ruleEngineUser.setPassword(MD5Utils.encrypt(forgotRequest.getPassword()));
        return ruleEngineUserManager.lambdaUpdate()
                .eq(RuleEngineUser::getEmail, forgotRequest.getEmail())
                .update(ruleEngineUser);
    }

    @Override
    public UserResponse getUserInfo() {
        RuleEngineUser engineUser = AuthInterceptor.USER.get();
        UserResponse userResponse = new UserResponse();
        BeanUtil.copyProperties(engineUser, userResponse);
        return userResponse;
    }

    /**
     * 退出登录
     *
     * @return true
     */
    @Override
    public Boolean logout() {
        CookieUtils.delete(AbstractTokenInterceptor.TOKEN);
        return true;
    }

    /**
     * 检查验证码是否有效
     *
     * @param email 邮箱
     * @param code  验证码
     * @param pre   redis key pre
     */
    private void checkVerifyCode(String email, Integer code, String pre) {
        String userEmailCodePre = pre + IPUtils.getRequestIp() + email;
        RBucket<Integer> rBucket = redissonClient.getBucket(userEmailCodePre);
        Integer getCode = rBucket.get();
        if (getCode == null) {
            throw new ValidationException("验证码已失效!");
        }
        if (!getCode.equals(code)) {
            throw new ValidationException("验证码错误!");
        }
    }
}
