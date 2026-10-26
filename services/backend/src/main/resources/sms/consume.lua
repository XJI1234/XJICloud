local expected = redis.call('HGET', KEYS[1], 'digest')
if not expected then return 0 end
local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
if expected == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
if attempts >= 5 then redis.call('DEL', KEYS[1]) end
return 0
