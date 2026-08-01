-- 优惠券库存同步脚本
-- 将Redis中的库存扣减信息同步到数据库
-- 返回当前库存信息供数据库更新使用
-- 参数：KEYS[1]=coupon:cache:{couponId}, KEYS[2]=user:coupon:{couponId}

local couponKey = KEYS[1]
local userCouponKey = KEYS[2]

-- 检查优惠券是否存在
if redis.call('exists', couponKey) == 0 then
    return -1  -- 优惠券不存在
end

-- 获取当前库存信息
local totalNum = tonumber(redis.call('hget', couponKey, 'totalNum'))
local issueNum = redis.call('hget', couponKey, 'issueNum')

-- 获取已领取用户数量
local userCount = redis.call('hlen', userCouponKey)

-- 返回库存信息（JSON格式）
return cjson.encode({
    totalNum = totalNum,
    issueNum = issueNum,
    userCount = userCount
})
