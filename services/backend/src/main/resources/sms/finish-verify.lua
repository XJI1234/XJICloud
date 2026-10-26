-- A resend or expiry while PNVS was checking must invalidate the old result.
if redis.call('HGET', KEYS[1], 'verification') ~= ARGV[1] then return 0 end
if redis.call('GET', KEYS[2]) ~= ARGV[3] then return 0 end
if ARGV[2] == '1' then redis.call('DEL', KEYS[1]); return 1 end
if tonumber(redis.call('HGET', KEYS[1], 'attempts') or '5') >= 5 then
    redis.call('DEL', KEYS[1])
else
    redis.call('HDEL', KEYS[1], 'verification')
end
return 0
