package com.qxcmp.article.controller;

import com.qxcmp.article.page.AdminNewsPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import static com.qxcmp.article.NewsModule.ADMIN_NEWS_URL;

/**
 * @author Aaric
 */
@Controller
@RequestMapping(ADMIN_NEWS_URL)
@RequiredArgsConstructor
public class AdminNewsPageController extends AbstractNewsPageController {

    @GetMapping("")
    public ModelAndView newsPage() {
        return page(AdminNewsPage.class, articleService, channelService);
    }

}
