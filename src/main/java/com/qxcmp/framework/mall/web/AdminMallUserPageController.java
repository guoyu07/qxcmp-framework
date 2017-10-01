package com.qxcmp.framework.mall.web;

import com.qxcmp.framework.audit.ActionException;
import com.qxcmp.framework.mall.Commodity;
import com.qxcmp.framework.mall.CommodityService;
import com.qxcmp.framework.mall.Store;
import com.qxcmp.framework.mall.StoreService;
import com.qxcmp.framework.user.User;
import com.qxcmp.framework.web.QXCMPBackendController;
import com.qxcmp.framework.web.view.Component;
import com.qxcmp.framework.web.view.elements.container.TextContainer;
import com.qxcmp.framework.web.view.elements.header.HeaderType;
import com.qxcmp.framework.web.view.elements.header.IconHeader;
import com.qxcmp.framework.web.view.elements.header.PageHeader;
import com.qxcmp.framework.web.view.elements.icon.Icon;
import com.qxcmp.framework.web.view.elements.label.BasicLabel;
import com.qxcmp.framework.web.view.elements.segment.Segment;
import com.qxcmp.framework.web.view.support.utils.TableHelper;
import com.qxcmp.framework.web.view.views.Overview;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.qxcmp.framework.core.QXCMPConfiguration.QXCMP_BACKEND_URL;
import static com.qxcmp.framework.core.QXCMPNavigationConfiguration.NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT;
import static com.qxcmp.framework.core.QXCMPNavigationConfiguration.NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT_COMMODITY;

@Controller
@RequestMapping(QXCMP_BACKEND_URL + "/mall/user/store")
@RequiredArgsConstructor
public class AdminMallUserPageController extends QXCMPBackendController {

    /**
     * 用户所选店铺偏好
     */
    private final String USER_CONFIG_STORE_SELECTION = "admin.mall.user.store.selection";

    private final StoreService storeService;

    private final CommodityService commodityService;

    private final TableHelper tableHelper;

    /**
     * 用户先进行店铺选择，然后进行相关店铺的处理
     *
     * @return 店铺选择页面
     */
    @GetMapping("")
    public ModelAndView userStorePage() {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (stores.isEmpty()) {
            return overviewPage(new Overview(new IconHeader("你还没有管理任何店铺", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        Store selectedStore = getUserSelectedStore(user);

        if (Objects.isNull(selectedStore) || !stores.contains(selectedStore)) {
            return redirect(QXCMP_BACKEND_URL + "/mall/user/store/select");
        } else {
            return page().addComponent(new Segment()
                    .addComponent(getUserStorePageHeader(selectedStore)))
                    .setBreadcrumb("控制台", "", "商城管理", "mall", "我的店铺")
                    .setVerticalNavigation(NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT, "")
                    .build();
        }
    }

    @GetMapping("/select")
    public ModelAndView userStoreSelectPage(final AdminMallUserStoreSelectForm form) {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (stores.isEmpty()) {
            return overviewPage(new Overview(new IconHeader("你还没有管理任何店铺", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        form.setStore(stores.get(0));

        return page().addComponent(new TextContainer().addComponent(new Segment()
                .addComponent(new PageHeader(HeaderType.H4, "请选择店铺"))
                .addComponent(convertToForm(form))))
                .setBreadcrumb("控制台", "", "商城管理", "mall", "我的店铺", "mall/user/store", "选择店铺")
                .addObject("selection_items_store", stores)
                .build();
    }

    @PostMapping("/select")
    public ModelAndView userStoreSelectPage(@Valid final AdminMallUserStoreSelectForm form, BindingResult bindingResult) {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (bindingResult.hasErrors() || !stores.contains(form.getStore())) {
            return overviewPage(new Overview(new IconHeader("店铺不存在", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        userConfigService.save(user.getId(), USER_CONFIG_STORE_SELECTION, form.getStore().getId());

        return redirect(QXCMP_BACKEND_URL + "/mall/user/store");
    }

    @GetMapping("/commodity")
    public ModelAndView userCommodityPage(Pageable pageable) {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (stores.isEmpty()) {
            return overviewPage(new Overview(new IconHeader("你还没有管理任何店铺", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        Store selectedStore = getUserSelectedStore(user);

        Page<Commodity> commodities = commodityService.findByStore(selectedStore, pageable);

        return page().addComponent(new Segment().addComponent(getUserStorePageHeader(selectedStore)).addComponent(tableHelper.convert("userStoreCommodity", Commodity.class, commodities)))
                .setBreadcrumb("控制台", "", "商城管理", "mall", "我的店铺", "mall/user/store", "商品管理")
                .setVerticalNavigation(NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT, NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT_COMMODITY)
                .build();
    }

    @GetMapping("/commodity/new")
    public ModelAndView userCommodityNewPage(final AdminMallUserStoreCommodityNewForm form) {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (stores.isEmpty()) {
            return overviewPage(new Overview(new IconHeader("你还没有管理任何店铺", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        Store selectedStore = getUserSelectedStore(user);

        return page().addComponent(new Segment().addComponent(getUserStorePageHeader(selectedStore)).addComponent(convertToForm(form)))
                .setBreadcrumb("控制台", "", "商城管理", "mall", "我的店铺", "mall/user/store", "商品管理", "mall/user/store/commodity", "添加商品")
                .setVerticalNavigation(NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT, NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT_COMMODITY)
                .build();
    }

    @PostMapping("/commodity/new")
    public ModelAndView userCommodityNewPage(@Valid final AdminMallUserStoreCommodityNewForm form, BindingResult bindingResult) {

        User user = currentUser().orElseThrow(RuntimeException::new);

        List<Store> stores = storeService.findByUser(user);

        if (stores.isEmpty()) {
            return overviewPage(new Overview(new IconHeader("你还没有管理任何店铺", new Icon("warning circle"))).addLink("返回", QXCMP_BACKEND_URL + "/mall")).build();
        }

        Store selectedStore = getUserSelectedStore(user);

        if (bindingResult.hasErrors()) {
            return page().addComponent(new Segment().addComponent(getUserStorePageHeader(selectedStore)).addComponent(convertToForm(form).setErrorMessage(convertToErrorMessage(bindingResult, form))))
                    .setBreadcrumb("控制台", "", "商城管理", "mall", "我的店铺", "mall/user/store", "商品管理", "mall/user/store/commodity", "添加商品")
                    .setVerticalNavigation(NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT, NAVIGATION_ADMIN_MALL_USER_STORE_MANAGEMENT_COMMODITY)
                    .build();
        }

        return submitForm(form, context -> {
            try {
                commodityService.create(() -> {
                    Commodity commodity = commodityService.next();
                    commodity.setCover(form.getCover());
                    commodity.setAlbums(form.getAlbums());
                    commodity.setDetails(form.getDetails());
                    commodity.setTitle(form.getTitle());
                    commodity.setSubTitle(form.getSubTitle());
                    commodity.setOriginPrice(form.getOriginPrice());
                    commodity.setSellPrice(form.getSellPrice());
                    commodity.setInventory(form.getInventory());
                    commodity.setDisabled(form.isDisabled());

                    commodity.setStore(selectedStore);
                    commodity.setUserModified(user);
                    commodity.setDateCreated(new Date());
                    commodity.setDateModified(new Date());
                    return commodity;
                });
            } catch (Exception e) {
                throw new ActionException(e.getMessage(), e);
            }
        }, (context, overview) -> overview.addLink("返回", QXCMP_BACKEND_URL + "/mall/user/store/commodity").addLink("继续添加商品", QXCMP_BACKEND_URL + "/mall/user/store/commodity/new"));
    }

    private Store getUserSelectedStore(User user) {
        return storeService.findOne(userConfigService.getString(user.getId(), USER_CONFIG_STORE_SELECTION).orElse("")).orElse(null);
    }

    private Component getUserStorePageHeader(Store selectedStore) {
        return new BasicLabel(selectedStore.getName()).setIcon(new Icon("marker")).setUrl(QXCMP_BACKEND_URL + "/mall/user/store/select");
    }
}
