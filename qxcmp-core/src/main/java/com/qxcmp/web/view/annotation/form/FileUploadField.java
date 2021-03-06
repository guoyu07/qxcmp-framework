package com.qxcmp.web.view.annotation.form;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileUploadField {

    /**
     * 标签名称
     */
    String value();

    /**
     * 字段所属组
     */
    String section() default "";

    /**
     * 字段说明文本
     */
    String tooltip() default "";

    /**
     * 是否为必选项
     * <p>
     * 会在标签上加上红星
     */
    boolean required() default false;

    /**
     * 最大图片大小(KB)
     */
    long maxSize() default 20480;

    /**
     * 支持的文件最大数量
     */
    int maxCount() default 1;

    /**
     * 控件文本
     */
    String text() default "上传文件";
}
