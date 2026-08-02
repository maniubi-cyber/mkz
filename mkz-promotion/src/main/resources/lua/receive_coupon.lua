if(redis.call('exists', KEYS[1]) == 0) then
    return 1
end
if(tonumber(redis.call('hget', KEYS[1], 'totalNum')) <= 0) then
    return 2
end
if(tonumber(redis.call('time')[1]) > tonumber(redis.call('hget', KEYS[1], 'issueEndTime'))) then
    return 3
end
-- 先比较后自增：超限请求不得污染用户领取计数（原子脚本内读-比-增无竞态）
local userCount = tonumber(redis.call('hget', KEYS[2], ARGV[1]) or '0')
if(tonumber(redis.call('hget', KEYS[1], 'userLimit')) < userCount + 1) then
    return 4
end
redis.call('hincrby', KEYS[2], ARGV[1], 1)
redis.call('hincrby', KEYS[1], "totalNum", "-1")
return 0
