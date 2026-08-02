package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.ShowFavorite;

public interface FavoriteService extends IService<ShowFavorite> {

    Result<?> getMyFavorites();

    Result<?> addFavorite(Long showId);

    Result<?> removeFavorite(Long showId);
}