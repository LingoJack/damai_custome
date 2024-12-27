-- 定义全局变量，从 KEYS 和 ARGV 中获取参数值
local snowflake_work_id_key = KEYS[1]                  -- Redis 中存储 worker ID 的键名
local snowflake_data_center_id_key = KEYS[2]           -- Redis 中存储 data center ID 的键名
local max_worker_id = tonumber(ARGV[1])                -- 最大的 worker ID 值
local max_data_center_id = tonumber(ARGV[2])           -- 最大的 data center ID 值

-- 初始化返回值和标志位
local return_worker_id = 0                             -- 将要返回的 worker ID
local return_data_center_id = 0                        -- 将要返回的 data center ID
local snowflake_work_id_flag = false                   -- 标记是否设置了 worker ID
local snowflake_data_center_id_flag = false            -- 标记是否设置了 data center ID

-- 初始化 JSON 格式的返回结果字符串
local json_result = string.format('{"%s": %d, "%s": %d}', 'workId', return_worker_id, 'dataCenterId', return_data_center_id)

-- 检查并初始化 worker ID 和 data center ID 键，如果不存在则设置为 0
if (redis.call('exists', snowflake_work_id_key) == 0) then
    redis.call('set', snowflake_work_id_key, 0)        -- 如果 worker ID 键不存在，则创建并设为 0
    snowflake_work_id_flag = true                      -- 设置 worker ID 已被初始化的标记
end

if (redis.call('exists', snowflake_data_center_id_key) == 0) then
    redis.call('set', snowflake_data_center_id_key, 0) -- 如果 data center ID 键不存在，则创建并设为 0
    snowflake_data_center_id_flag = true               -- 设置 data center ID 已被初始化的标记
end

-- 如果两个 ID 都是新初始化的，则直接返回初始结果
if (snowflake_work_id_flag and snowflake_data_center_id_flag) then
    return json_result                                 -- 返回包含默认值的 JSON 结果
end

-- 获取当前的 worker ID 和 data center ID
local snowflake_work_id = tonumber(redis.call('get', snowflake_work_id_key))       -- 获取当前 worker ID
local snowflake_data_center_id = tonumber(redis.call('get', snowflake_data_center_id_key)) -- 获取当前 data center ID

-- 检查是否达到最大 worker ID 和 data center ID
if (snowflake_work_id == max_worker_id) then
    if (snowflake_data_center_id == max_data_center_id) then
        redis.call('set', snowflake_work_id_key, 0)    -- 如果达到了最大值，则重置 worker ID
        redis.call('set', snowflake_data_center_id_key, 0) -- 同时重置 data center ID
    else
        return_data_center_id = redis.call('incr', snowflake_data_center_id_key)   -- 否则，增加 data center ID
    end
else
    return_worker_id = redis.call('incr', snowflake_work_id_key)                  -- 如果没有达到最大 worker ID，则增加它
end

-- 返回更新后的 worker ID 和 data center ID
return string.format('{"%s": %d, "%s": %d}', 'workId', return_worker_id, 'dataCenterId', return_data_center_id)