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

    /** 缓存击穿互斥锁 */
    String CACHE_MUTEX = "livetix:mutex:";

    /** 用户下单锁 */
    String USER_ORDER_LOCK = "livetix:user:order:lock:";

    /** 支付锁 */
    String PAY_LOCK = "livetix:pay:lock:";

    /** 异步下单结果 */
    String ORDER_RESULT = "livetix:order:result:";

    /** Method-level cache TTL (seconds) */
    long CACHE_TTL_5M = 300;
    long CACHE_TTL_30M = 1800;
    long CACHE_TTL_1H = 3600;
}
