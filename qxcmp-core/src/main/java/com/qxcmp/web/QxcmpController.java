package com.qxcmp.web;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.qxcmp.audit.Action;
import com.qxcmp.audit.ActionException;
import com.qxcmp.audit.ActionExecutor;
import com.qxcmp.audit.AuditLog;
import com.qxcmp.config.SiteService;
import com.qxcmp.config.SystemConfigService;
import com.qxcmp.config.UserConfigService;
import com.qxcmp.core.entity.EntityCreateEvent;
import com.qxcmp.core.entity.EntityDeleteEvent;
import com.qxcmp.core.entity.EntityService;
import com.qxcmp.core.entity.EntityUpdateEvent;
import com.qxcmp.exception.CaptchaExpiredException;
import com.qxcmp.exception.CaptchaIncorrectException;
import com.qxcmp.message.MessageService;
import com.qxcmp.user.User;
import com.qxcmp.user.UserService;
import com.qxcmp.util.Captcha;
import com.qxcmp.util.CaptchaService;
import com.qxcmp.util.IpAddressResolver;
import com.qxcmp.web.model.RestfulResponse;
import com.qxcmp.web.page.AbstractLegacyPage;
import com.qxcmp.web.view.annotation.form.Form;
import com.qxcmp.web.view.elements.grid.Col;
import com.qxcmp.web.view.elements.grid.Grid;
import com.qxcmp.web.view.elements.header.IconHeader;
import com.qxcmp.web.view.elements.html.P;
import com.qxcmp.web.view.elements.icon.Icon;
import com.qxcmp.web.view.elements.message.ErrorMessage;
import com.qxcmp.web.view.modules.form.AbstractForm;
import com.qxcmp.web.view.modules.table.EntityTable;
import com.qxcmp.web.view.modules.table.Table;
import com.qxcmp.web.view.page.QxcmpPage;
import com.qxcmp.web.view.page.QxcmpPageResolver;
import com.qxcmp.web.view.support.Alignment;
import com.qxcmp.web.view.support.Color;
import com.qxcmp.web.view.support.utils.TableHelper;
import com.qxcmp.web.view.support.utils.ViewHelper;
import com.qxcmp.web.view.views.Overview;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mobile.device.Device;
import org.springframework.mobile.device.DeviceResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.qxcmp.core.QxcmpConfiguration.QXCMP_FILE_UPLOAD_TEMP_FOLDER;

/**
 * 平台页面路由基类
 * <p>
 * 负责解析页面类型并提供视图渲染和表单提交等一些基本支持
 *
 * @author Aaric
 */
public abstract class QxcmpController {

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected ApplicationContext applicationContext;
    protected SiteService siteService;
    protected UserService userService;
    protected UserConfigService userConfigService;
    protected SystemConfigService systemConfigService;
    protected MessageService messageService;
    protected ViewHelper viewHelper;
    protected QxcmpPageResolver pageRevolveService;

    private TableHelper tableHelper;
    private CaptchaService captchaService;
    private ActionExecutor actionExecutor;
    private IpAddressResolver ipAddressResolver;
    private DeviceResolver deviceResolver;

    /**
     * 获取一个页面
     *
     * @param tClass 页面类型
     * @param models 页面数据
     * @param <T>    页面类型
     * @return 渲染后的页面
     */
    protected <T extends QxcmpPage> ModelAndView page(Class<T> tClass, Object... models) {
        T t = applicationContext.getBean(tClass, models);
        Device device = deviceResolver.resolveDevice(request);
        if (device.isMobile()) {
            t.renderToMobile();
        } else if (device.isTablet()) {
            t.renderToTablet();
        } else if (device.isNormal()) {
            t.renderToNormal();
        } else {
            t.render();
        }
        return t.build();
    }

    /**
     * 获取错误页面
     *
     * @param errors 错误信息
     * @return 错误页面
     */
    protected ModelAndView errorPage(Map<String, Object> errors) {
        return page(pageRevolveService.getErrorPage(), errors);
    }

    /**
     * 获取一个概览页面
     *
     * @param overview 概览页面
     * @return 概览页面
     */
    protected ModelAndView overviewPage(Overview overview) {
        return page(pageRevolveService.getOverviewPage(), overview);
    }

    /**
     * 执行一个操作并审计
     *
     * @param title   操作名称
     * @param action  要执行的操作
     * @param context 执行上下文
     * @return 操作结果页面
     */
    protected ModelAndView execute(String title, Action action, BiConsumer<Map<String, Object>, Overview> context) {
        String url = request.getRequestURL().toString();
        AuditLog auditLog = actionExecutor.execute(title, url, getRequestContent(request), currentUser().orElse(null), action);
        Overview overview;
        switch (auditLog.getStatus()) {
            case SUCCESS:
                overview = viewHelper.nextSuccessOverview(title, "操作成功");
                break;
            case FAILURE:
                overview = viewHelper.nextWarningOverview(title, "操作失败").addComponent(new P(auditLog.getComments()));
                break;
            default:
                overview = viewHelper.nextWarningOverview(title, "系统错误");
                break;
        }

        context.accept(auditLog.getActionContext(), overview);

        if (overview.getLinks().isEmpty()) {
            overview.addLink("返回", url);
        }
        return overviewPage(overview);
    }

    /**
     * 执行一个操作并审计
     *
     * @param title  操作名称
     * @param action 要执行的操作
     * @return 操作结果相应
     */
    protected ResponseEntity<RestfulResponse> execute(String title, Action action) {
        AuditLog auditLog = actionExecutor.execute(title, request.getRequestURL().toString(), getRequestContent(request), currentUser().orElse(null), action);

        RestfulResponse.RestfulResponseBuilder builder = RestfulResponse.builder();

        switch (auditLog.getStatus()) {
            case SUCCESS:
                builder.status(HttpStatus.OK.value());
                break;
            case FAILURE:
                builder.status(HttpStatus.BAD_GATEWAY.value());
                break;
            default:
                builder.status(HttpStatus.NOT_ACCEPTABLE.value());
                break;
        }

        builder.message(auditLog.getTitle());
        builder.developerMessage(auditLog.getComments());
        RestfulResponse response = builder.build();
        return ResponseEntity.status(response.getStatus()).body(response);

    }

    /**
     * 获取一个实体创建页面
     *
     * @param qClass        实体创建页面类型
     * @param form          创建表单
     * @param bindingResult 错误结果
     * @param <Q>           页面类型
     * @return 实体创建页面
     */
    protected <Q extends QxcmpPage> ModelAndView entityCreatePage(Class<Q> qClass, Object form, BindingResult bindingResult) {
        return page(qClass, form, bindingResult);
    }

    /**
     * 使用表单创建一个实体对象
     * <p>
     * 注意：
     * <ol>
     * <li>新建实体的url必须以 {@code /new}结尾</li>
     * </ol>
     *
     * @param entityService 实体服务
     * @param form          表单
     * @param <T>           实体类型
     * @param <ID>          实体主键类型
     * @return 操作结果页面
     */
    protected <T, ID extends Serializable> ModelAndView createEntity(EntityService<T, ID> entityService, Object form) {
        return createEntity(entityService, form, overview -> {
        });
    }

    /**
     * 使用表单创建一个实体对象
     *
     * @param entityService 实体服务
     * @param form          表单
     * @param consumer      结果页面自定义
     * @param <T>           实体类型
     * @param <ID>          实体主键类型
     * @return 操作结果页面
     */
    protected <T, ID extends Serializable> ModelAndView createEntity(EntityService<T, ID> entityService, Object form, Consumer<Overview> consumer) {
        return execute(getFormSubmitActionTitle(form), context -> {
            try {
                T t = entityService.create(() -> {
                    T next = entityService.next();
                    entityService.mergeToEntity(form, next);
                    return next;
                });
                context.put("entity-id", entityService.getEntityId(t));
                applicationContext.publishEvent(new EntityCreateEvent<>(userService.currentUser(), t));
            } catch (Exception e) {
                throw new ActionException(e.getMessage(), e);
            }
        }, (stringObjectMap, overview) -> {
            consumer.accept(overview);
            if (overview.getLinks().isEmpty()) {
                overview.addLink("继续新建", "");
                if (Objects.nonNull(stringObjectMap.get("entity-id"))) {
                    overview.addLink("重新编辑", stringObjectMap.get("entity-id").toString() + "/edit");
                }
                overview.addLink("返回", request.getRequestURL().toString().replaceAll("/new", ""));
            }
        });
    }

    /**
     * 获取一个实体详情页面
     *
     * @param qClass        页面类型
     * @param id            实体主键
     * @param entityService 实体服务
     * @param <Q>           页面类型
     * @param <T>           实体类型
     * @param <ID>          实体主键类型
     * @return 实体详情页面
     */
    protected <Q extends QxcmpPage, T, ID extends Serializable> ModelAndView entityDetailsPage(Class<Q> qClass, ID id, EntityService<T, ID> entityService) {
        return entityService.findOne(id).map(t -> page(qClass, t)).orElse(overviewPage(viewHelper.nextWarningOverview("资源不存在")));
    }

    /**
     * 获取一个实体编辑页面
     * <p>
     * 如果实体不在返回一个实体未找到概览页面
     *
     * @param qClass        最终的编辑页面
     * @param id            实体主键
     * @param entityService 实体服务
     * @param form          实体编辑表单
     * @param bindingResult 错误对象
     * @param <Q>           编辑页面类型
     * @param <T>           实体类型
     * @param <ID>          主键类型
     * @return 实体编辑页面
     */
    protected <Q extends QxcmpPage, T, ID extends Serializable> ModelAndView entityUpdatePage(Class<Q> qClass, ID id, EntityService<T, ID> entityService, Object form, BindingResult bindingResult) {
        return entityService.findOne(id).map(t -> {
            entityService.mergeToObject(t, form);
            return page(qClass, form, bindingResult);
        }).orElse(overviewPage(viewHelper.nextWarningOverview("资源不存在")));
    }

    /**
     * 使用表单更新一个实体对象
     * <p>
     * 注意：
     * <ol>
     * <li>更新实体的url必须以 {@code /edit} 结尾</li>
     * </ol>
     *
     * @param id            实体主键
     * @param entityService 实体服务
     * @param form          表单
     * @param <T>           实体类型
     * @param <ID>          实体主键类型
     * @return 操作结果页面
     */
    protected <T, ID extends Serializable> ModelAndView updateEntity(ID id, EntityService<T, ID> entityService, Object form) {
        return updateEntity(id, entityService, form, overview -> {
        });
    }

    protected <T, ID extends Serializable> ModelAndView updateEntity(ID id, EntityService<T, ID> entityService, Object form, Consumer<Overview> consumer) {
        return execute(getFormSubmitActionTitle(form), context -> {
            try {
                T origin = entityService.findOne(id).orElse(null);
                applicationContext.publishEvent(new EntityUpdateEvent<>(userService.currentUser(), entityService.update(id, t -> entityService.mergeToEntity(form, t)), origin));
            } catch (Exception e) {
                throw new ActionException(e.getMessage(), e);
            }
        }, (stringObjectMap, overview) -> {
            consumer.accept(overview);
            if (overview.getLinks().isEmpty()) {
                overview.addLink("重新编辑", "");
                overview.addLink("返回", StringUtils.substringBeforeLast(request.getRequestURL().toString().replaceAll("/edit", ""), "/"));
            }
        });
    }

    /**
     * 删除一个实体
     *
     * @param title         操作名称
     * @param id            实体主键
     * @param entityService 实体服务
     * @param <T>           实体类型
     * @param <ID>          实体主键类型
     * @return 删除结果
     */
    protected <T, ID extends Serializable> ResponseEntity<RestfulResponse> deleteEntity(String title, ID id, EntityService<T, ID> entityService) {
        AuditLog auditLog = actionExecutor.execute(title, request.getRequestURL().toString(), getRequestContent(request), currentUser().orElse(null), context -> {
            try {
                T entity = entityService.findOne(id).orElse(null);
                entityService.deleteById(id);
                if (Objects.nonNull(entity)) {
                    applicationContext.publishEvent(new EntityDeleteEvent<>(userService.currentUser(), entity));
                }
            } catch (Exception e) {
                throw new ActionException(e.getMessage(), e);
            }
        });

        RestfulResponse.RestfulResponseBuilder builder = RestfulResponse.builder();

        switch (auditLog.getStatus()) {
            case SUCCESS:
                builder.status(HttpStatus.OK.value());
                break;
            case FAILURE:
                builder.status(HttpStatus.BAD_REQUEST.value());
                break;
            default:
                builder.status(HttpStatus.BAD_GATEWAY.value());
                break;
        }

        builder.message(auditLog.getTitle());
        builder.developerMessage(auditLog.getComments());
        RestfulResponse restfulResponse = builder.build();

        return ResponseEntity.status(restfulResponse.getStatus()).body(restfulResponse);

    }


    /**
     * 根据请求获取一个页面
     *
     * @return 由页面解析器解析出来的页面
     * @deprecated
     */
    protected AbstractLegacyPage page() {
        return null;
    }

    /**
     * 根据请求获取一个页面并设置概览视图
     *
     * @param overview 概览组件
     * @return 概览视图页面
     * @see Overview
     * @deprecated
     */
    protected AbstractLegacyPage page(Overview overview) {
        return page().addComponent(new Grid().setTextContainer().setAlignment(Alignment.CENTER).setVerticallyPadded().addItem(new Col().addComponent(overview)));
    }

    /**
     * 获取一个重定向页面
     *
     * @param url 重定向链接
     * @return 重定向页面
     */
    protected ModelAndView redirect(String url) {
        return new ModelAndView("redirect:" + url);
    }

    /**
     * @param object
     * @return
     * @deprecated
     */
    protected AbstractForm convertToForm(Object object) {
        return viewHelper.nextForm(object);
    }

    /**
     * @param bindingResult
     * @param object
     * @return
     * @deprecated
     */
    protected ErrorMessage convertToErrorMessage(BindingResult bindingResult, Object object) {
        return viewHelper.nextFormErrorMessage(bindingResult, object);
    }

    /**
     * @param pageable
     * @param entityService
     * @return
     * @deprecated
     */
    protected EntityTable convertToTable(Pageable pageable, EntityService entityService) {
        return convertToTable("", pageable, entityService);
    }

    /**
     * @param tableName
     * @param pageable
     * @param entityService
     * @return
     * @deprecated
     */
    protected EntityTable convertToTable(String tableName, Pageable pageable, EntityService entityService) {
        return convertToTable(tableName, "", pageable, entityService);
    }

    /**
     * @param tableName
     * @param action
     * @param pageable
     * @param entityService
     * @return
     * @deprecated
     */
    @SuppressWarnings("unchecked")
    protected EntityTable convertToTable(String tableName, String action, Pageable pageable, EntityService entityService) {

        Page page;

        if (StringUtils.isNotBlank(request.getParameter("field")) && StringUtils.isNotBlank(request.getParameter("search"))) {
            String searchField = request.getParameter("field");
            String searchContent = request.getParameter("search");

            List<Field> fields = Lists.newArrayList();

            for (Field field : entityService.type().getDeclaredFields()) {
                if (StringUtils.equals(field.getName(), searchField)) {
                    fields.add(field);
                    break;
                }
            }

            page = entityService.findAll(pageable);
        } else {
            page = entityService.findAll(pageable);
        }

        return convertToTable(tableName, action, entityService.type(), page);
    }

    /**
     * @param tClass
     * @param tPage
     * @param <T>
     * @return
     * @deprecated
     */
    protected <T> EntityTable convertToTable(Class<T> tClass, Page<T> tPage) {
        return convertToTable("", tClass, tPage);
    }

    /**
     * @param tableName
     * @param tClass
     * @param tPage
     * @param <T>
     * @return
     * @deprecated
     */
    protected <T> EntityTable convertToTable(String tableName, Class<T> tClass, Page<T> tPage) {
        return convertToTable(tableName, "", tClass, tPage);
    }

    /**
     * @param tableName
     * @param action
     * @param tClass
     * @param tPage
     * @param <T>
     * @return
     * @deprecated
     */
    protected <T> EntityTable convertToTable(String tableName, String action, Class<T> tClass, Page<T> tPage) {
        return tableHelper.convert(tableName, action, tClass, tPage, request);
    }

    /**
     * @param dictionary
     * @return
     * @deprecated
     */
    protected Table convertToTable(Map<Object, Object> dictionary) {
        return tableHelper.convert(dictionary);
    }

    /**
     * @param consumer
     * @return
     * @deprecated
     */
    protected Table convertToTable(Consumer<Map<Object, Object>> consumer) {
        Map<Object, Object> dictionary = Maps.newLinkedHashMap();
        consumer.accept(dictionary);
        return convertToTable(dictionary);
    }

    /**
     * 获取当前请求对应的认证用户
     *
     * @return 当前认证用户
     */
    protected Optional<User> currentUser() {
        return Optional.ofNullable(userService.currentUser());
    }

    /**
     * 刷新当前用户实体
     * <p>
     * 如果当前用户已经登录，则重新读取用户数据
     */
    protected void refreshUser() {
        currentUser().ifPresent(currentUser -> userService.findOne(currentUser.getId()).ifPresent(user -> {
            Authentication authentication = new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }));
    }

    /**
     * 验证验证码是否有效，如果无效将错误信息放入 {@code BindingResult} 中
     *
     * @param captcha       用户输入的验证码
     * @param bindingResult 错误绑定
     */
    protected void verifyCaptcha(String captcha, BindingResult bindingResult) {
        if (Objects.isNull(request.getSession().getAttribute(CaptchaService.CAPTCHA_SESSION_ATTR))) {
            bindingResult.rejectValue("captcha", "Captcha.null");
        } else {
            try {
                Captcha c = (Captcha) request.getSession().getAttribute(CaptchaService.CAPTCHA_SESSION_ATTR);
                captchaService.verify(c, captcha);
            } catch (CaptchaExpiredException e) {
                bindingResult.rejectValue("captcha", "Captcha.expired");
            } catch (CaptchaIncorrectException e) {
                bindingResult.rejectValue("captcha", "Captcha.incorrect");
            }
        }
    }

    /**
     * 获取请求的IP地址
     *
     * @return 请求IP地址
     */
    protected String getRequestAddress() {
        return ipAddressResolver.resolve(request);
    }

    protected ModelAndView submitForm(Object form, Action action) {
        return submitForm(form, action, (stringObjectMap, overview) -> {
        });
    }

    @Deprecated
    protected ModelAndView submitForm(Object form, Action action, BiConsumer<Map<String, Object>, Overview> biConsumer) {
        return submitForm("", form, action, biConsumer);
    }

    /**
     * 提交一个表单并执行相应操作
     * <p>
     * 该操作会被记录到审计日志中
     *
     * @param title      操作名称
     * @param form       要提交的表单
     * @param action     要执行的操作
     * @param biConsumer 返回的结果页面
     * @return 提交后的页面
     * @deprecated
     */
    protected ModelAndView submitForm(String title, Object form, Action action, BiConsumer<Map<String, Object>, Overview> biConsumer) {
        Form annotation = form.getClass().getAnnotation(Form.class);

        if (Objects.nonNull(annotation) && StringUtils.isNotBlank(annotation.value())) {
            title = annotation.value();
        }

        if (StringUtils.isBlank(title)) {
            title = request.getRequestURL().toString();
        }

        AuditLog auditLog = actionExecutor.execute(title, request.getRequestURL().toString(), getRequestContent(request), currentUser().orElse(null), action);
        Overview overview = null;
        switch (auditLog.getStatus()) {
            case SUCCESS:
                overview = new Overview(new IconHeader(auditLog.getTitle(), new Icon("info circle")).setSubTitle("操作成功"));
                break;
            case FAILURE:
                overview = new Overview(new IconHeader(auditLog.getTitle(), new Icon("warning circle").setColor(Color.RED)).setSubTitle("操作失败")).addComponent(new P(auditLog.getComments()));
                break;
            default:
        }


        biConsumer.accept(auditLog.getActionContext(), overview);

        if (overview.getLinks().isEmpty()) {
            overview.addLink("返回", request.getRequestURL().toString());
        }

        return page(overview).build();
    }

    /**
     * 执行一个操作并记录到审计日志中
     *
     * @param title  操作名称
     * @param action 要执行的操作
     * @return 操作结果实体
     * @deprecated
     */
    protected RestfulResponse audit(String title, Action action) {
        AuditLog auditLog = actionExecutor.execute(title, request.getRequestURL().toString(), getRequestContent(request), currentUser().orElse(null), action);

        switch (auditLog.getStatus()) {
            case SUCCESS:
                return RestfulResponse.builder().status(HttpStatus.OK.value()).message(auditLog.getTitle()).developerMessage(auditLog.getComments()).build();
            case FAILURE:
                return RestfulResponse.builder().status(HttpStatus.BAD_GATEWAY.value()).message(auditLog.getTitle()).developerMessage(auditLog.getComments()).build();
            default:
        }

        return RestfulResponse.builder().status(HttpStatus.NOT_ACCEPTABLE.value()).message(auditLog.getTitle()).developerMessage(auditLog.getComments()).build();
    }

    /**
     * 获取上传后的文件
     *
     * @param keys 临时文件标识
     * @return 文件列表
     */
    protected List<File> getUploadFiles(List<String> keys) {
        List<File> files = Lists.newArrayList();

        keys.forEach(s -> files.addAll(FileUtils.listFiles(new File(QXCMP_FILE_UPLOAD_TEMP_FOLDER + s), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)));

        return files;
    }

    /**
     * 获取单个上传后的文件
     *
     * @param key 临时文件标识
     * @return 单个文件
     */
    protected File getUploadFile(String key) {
        return FileUtils.listFiles(new File(QXCMP_FILE_UPLOAD_TEMP_FOLDER + key), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).stream().findAny().orElse(null);
    }

    private String getRequestContent(HttpServletRequest request) {
        if (request.getMethod().equalsIgnoreCase("get")) {
            return request.getQueryString();
        } else if (request.getMethod().equalsIgnoreCase("post")) {
            return new Gson().toJson(request.getParameterMap());
        } else {
            return "Unknown request method: " + request.toString();
        }
    }

    /**
     * 获取表单提交操作标题
     *
     * @param form 表单
     * @return 表单标题
     */
    private String getFormSubmitActionTitle(Object form) {
        Form annotation = form.getClass().getAnnotation(Form.class);
        if (Objects.nonNull(annotation) && StringUtils.isNotBlank(annotation.value())) {
            return annotation.value();
        }
        return request.getRequestURL().toString();
    }

    @Autowired
    public void setActionExecutor(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    @Autowired
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Autowired
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setSiteService(SiteService siteService) {
        this.siteService = siteService;
    }

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    @Autowired
    public void setUserConfigService(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    @Autowired
    public void setSystemConfigService(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Autowired
    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }

    @Autowired
    public void setViewHelper(ViewHelper viewHelper) {
        this.viewHelper = viewHelper;
    }

    @Autowired
    public void setTableHelper(TableHelper tableHelper) {
        this.tableHelper = tableHelper;
    }

    @Autowired
    public void setCaptchaService(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Autowired
    public void setIpAddressResolver(IpAddressResolver ipAddressResolver) {
        this.ipAddressResolver = ipAddressResolver;
    }

    @Autowired
    public void setDeviceResolver(DeviceResolver deviceResolver) {
        this.deviceResolver = deviceResolver;
    }

    @Autowired
    public void setPageRevolveService(QxcmpPageResolver pageRevolveService) {
        this.pageRevolveService = pageRevolveService;
    }
}
