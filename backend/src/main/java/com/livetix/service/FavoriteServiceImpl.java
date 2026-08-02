package com.livetix.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.livetix.common.Result;
import com.livetix.entity.ShowFavorite;
import com.livetix.entity.Show;
import com.livetix.mapper.ShowFavoriteMapper;
import com.livetix.mapper.ShowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<ShowFavoriteMapper, ShowFavorite> implements FavoriteService {

    private final ShowMapper showMapper;

    @Override
    public Result<?> getMyFavorites() {
        long userId = StpUtil.getLoginIdAsLong();
        var favorites = this.list(new LambdaQueryWrapper<ShowFavorite>()
                .eq(ShowFavorite::getUserId, userId)
                .orderByDesc(ShowFavorite::getCreateTime));
        List<Long> showIds = favorites.stream().map(ShowFavorite::getShowId).distinct().toList();
        if (showIds.isEmpty()) return Result.ok(List.of());
        List<Show> shows = showMapper.selectBatchIds(showIds);
        return Result.ok(shows);
    }

    @Override
    public Result<?> addFavorite(Long showId) {
        long userId = StpUtil.getLoginIdAsLong();
        long count = this.count(new LambdaQueryWrapper<ShowFavorite>()
                .eq(ShowFavorite::getUserId, userId)
                .eq(ShowFavorite::getShowId, showId));
        if (count > 0) {
            return Result.fail("已收藏该演出");
        }
        ShowFavorite fav = new ShowFavorite();
        fav.setUserId(userId);
        fav.setShowId(showId);
        this.save(fav);
        return Result.ok("收藏成功");
    }

    @Override
    public Result<?> removeFavorite(Long showId) {
        long userId = StpUtil.getLoginIdAsLong();
        this.remove(new LambdaQueryWrapper<ShowFavorite>()
                .eq(ShowFavorite::getUserId, userId)
                .eq(ShowFavorite::getShowId, showId));
        return Result.ok("已取消收藏");
    }
}