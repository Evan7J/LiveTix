package com.livetix.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livetix.common.Result;
import com.livetix.entity.Banner;
import com.livetix.mapper.BannerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin: Banner/Carousel management
 */
@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerMapper bannerMapper;

    @GetMapping
    public Result<?> list() {
        List<Banner> banners = bannerMapper.selectList(
                new LambdaQueryWrapper<Banner>().orderByAsc(Banner::getSort));
        return Result.ok(banners);
    }

    @PostMapping
    public Result<?> save(@RequestBody Banner banner) {
        bannerMapper.insert(banner);
        return Result.ok("创建成功");
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Banner banner) {
        banner.setId(id);
        bannerMapper.updateById(banner);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        return Result.ok("删除成功");
    }
}
