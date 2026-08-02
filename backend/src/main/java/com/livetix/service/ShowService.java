package com.livetix.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.livetix.common.Result;
import com.livetix.entity.Show;

public interface ShowService extends IService<Show> {

    Result<?> getHotShows();

    Result<?> getShowsByCategory(Long categoryId, Integer page, Integer pageSize);

    Result<?> getShowDetail(Long showId);

    void flushViewCountsToDB();

    Result<?> searchShows(String keyword, Integer page, Integer pageSize, Long categoryId);

    Result<?> listShows(Integer page, Integer pageSize, Long categoryId, Integer status,
                        String keyword, String city, String timeRange, String date, String sort);

    Result<?> saveOrUpdateShow(Show show);
}