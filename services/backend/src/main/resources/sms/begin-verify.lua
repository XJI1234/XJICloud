-- Bind the registration challenge before calling PNVS; allow only one in-flight check.
if redis.call('HGET', KEYS[1], 'phone') ~= ARGV[1] then return 0 end
if redis.call('GET', KEYS[2]) ~= ARGV[2] then return 0 end
if redis.call('HEXISTS', KEYS[1], 'verification') == 1 then return 0 end
local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if attempts > 5 then redis.call('DEL', KEYS[1]); return 0 end
redis.call('HSET', KEYS[1], 'verification', ARGV[3])
return 1
