package com.livetix.common.constant;

/**
 * Redis Key constants
 */
public interface RedisKey {

    /** Hot shows cache: hot_shows */
    String HOT_SHOWS = "livetix:hot_shows";

    /** Show detail cache: show:{id} */
    String SHOW_DETAIL = "livetix:show:";

    /** Show stock cache: show:stock:{id} */
    String SHOW_STOCK = "livetix:show:stock:";

    /** Show list by category: show:category:{categoryId} */
    String SHOW_CATEGORY = "livetix:show:category:";

    /** Banners cache: banners */
    String BANNERS = "livetix:banners";

    /** Categories cache: categories */
    String CATEGORIES = "livetix:categories";

    /** Order stock lock: order:lock:{showId}:{ticketType} */
    String ORDER_LOCK = "livetix:order:lock:";

    /** User cart: cart:{userId} */
    String USER_CART = "livetix:cart:";

    /** Product detail cache: product:{id} */
    String PRODUCT_DETAIL = "livetix:product:";

    /** P0-6: 缓存击穿互斥锁前缀: mutex:{cacheKey} */
    String CACHE_MUTEX = "livetix:mutex:";

    /** P0-3: 用户下单防重锁: user:order:lock:{userId}:{showId} */
    String USER_ORDER_LOCK = "livetix:user:order:lock:";

    /** P0-3: 支付防重锁: pay:lock:{orderNo} */
    String PAY_LOCK = "livetix:pay:lock:";

    /** MQ 异步下单结果: order:result:{userId}:{requestId} → "OK:{orderId}" / "FAIL:{原因}" */
    String ORDER_RESULT = "livetix:order:result:";

    /** Method-level cache TTL (seconds) */
    long CACHE_TTL_5M = 300;
    long CACHE_TTL_30M = 1800;
    long CACHE_TTL_1H = 3600;
}
