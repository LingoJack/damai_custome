-- 定义计数器相关键
local counter_count_key = KEYS[1]
local counter_timestamp_key = KEYS[2]
local verify_captcha_id = KEYS[3]
-- 将验证验证码的阈值转换为数字
local verify_captcha_threshold = tonumber(ARGV[1])
-- 将当前时间（毫秒）转换为数字
local current_time_millis = tonumber(ARGV[2])
-- 将验证码ID的过期时间转换为数字
local verify_captcha_id_expire_time = tonumber(ARGV[3])
-- 将是否总是验证验证码的标志转换为数字
local always_verify_captcha = tonumber(ARGV[4])
-- 定义时间差值，用于判断是否重置计数器
local differenceValue = 1000

-- 如果总是验证验证码的标志为1，则直接设置验证码ID并设置过期时间，然后返回true
if always_verify_captcha == 1 then
    redis.call('set', verify_captcha_id, 'yes')
    redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
    return 'true'
end

-- 获取当前计数器的值，如果不存在则默认为0
local count = tonumber(redis.call('get', counter_count_key) or "0")
-- 获取上次重置计数器的时间，如果不存在则默认为0
local lastResetTime = tonumber(redis.call('get', counter_timestamp_key) or "0")

-- 如果当前时间与上次重置时间的差大于设定的时间差值，则重置计数器并更新重置时间
if current_time_millis - lastResetTime > differenceValue then
    count = 0
    redis.call('set', counter_count_key, count)
    redis.call('set', counter_timestamp_key, current_time_millis)
end

-- 计数器加1
count = count + 1

-- fixme 此处逻辑存疑，如果计数器超过阈值，但是计时器没有达到阈值，那么不应该重置计数器和计时器，否则会导致一秒内的第十二条请求被设置为不需要验证码
-- 如果计数器的值超过了验证验证码的阈值，则重置计数器，更新重置时间，设置验证码ID并设置过期时间，然后返回true
if count > verify_captcha_threshold then
    -- fixme 这里暂时最上面提出的问题进行了修复
    -- count = 0
    -- redis.call('set', counter_count_key, count)
    -- redis.call('set', counter_timestamp_key, current_time_millis)
    redis.call('set', verify_captcha_id, 'yes')
    redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
    return 'true'
end

-- 更新计数器的值
redis.call('set', counter_count_key, count)
-- 设置验证码ID为no并设置过期时间
redis.call('set', verify_captcha_id, 'no')
redis.call('expire', verify_captcha_id, verify_captcha_id_expire_time)
-- 返回false
return 'false'
