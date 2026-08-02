package com.livetix.service;

public interface StockPreloadService {

    int preloadStock(Long showId);

    int preloadAllOnSale();

    int getPreloadStock(Long showId);

    void clearPreloadStock(Long showId);

    long restorePreloadStock(Long showId, int quantity);

    void calibrateStock();
}