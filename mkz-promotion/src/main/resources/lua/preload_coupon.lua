-- 优惠券库存预热脚本
-- 将数据库中的优惠券库存信息加载到Redis
-- 参数：KEYS[1]=coupon:cache:{couponId}, ARGV[1]=totalNum, ARGV[2]=userLimit, ARGV[3]=issueEndTime

local couponKey = KEYS[1]

-- 检查是否已缓存
if redis.call('exists', couponKey) == 1 then
    return 1  -- 已存在，无需重复加载
end

-- 初始化优惠券缓存
redis.call('hmset', couponKey,
    'totalNum', ARGV[1],
    'userLimit', ARGV[2],
    'issueEndTime', ARGV[3],
    'issueNum', '0'
)

-- 设置过期时间（7天）
redis.call('expire', couponKey, 604800)

return 0
