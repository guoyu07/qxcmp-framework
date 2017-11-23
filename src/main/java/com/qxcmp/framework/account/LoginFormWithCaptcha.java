package com.qxcmp.framework.account;

import com.qxcmp.framework.web.view.annotation.form.Form;
import com.qxcmp.framework.web.view.annotation.form.ImageCaptchaField;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Aaric
 */
@Form(submitIcon = "sign in", action = "/login")
@Getter
@Setter
public class LoginFormWithCaptcha extends LoginForm {

    @ImageCaptchaField("验证码")
    private String captcha;

}
