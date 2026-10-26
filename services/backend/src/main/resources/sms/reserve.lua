-- Single-node Redis. Count attempted sends, including provider failures.
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
if tonumber(redis.call('GET', KEYS[2]) or '0') >= 10 then return 0 end
if tonumber(redis.call('GET', KEYS[3]) or '0') >= 30 then return 0 end
if tonumber(redis.call('GET', KEYS[4]) or '0') >= tonumber(ARGV[1]) then return 0 end
redis.call('SET', KEYS[1], '1', 'EX', 60)
for i = 2, 4 do
    local n = redis.call('INCR', KEYS[i])
    if n == 1 then
        local ttl = 86400
        if i == 3 then ttl = 3600 end
        redis.call('EXPIRE', KEYS[i], ttl)
    end
end
return 1
