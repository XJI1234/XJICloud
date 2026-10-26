local old = redis.call('GET', KEYS[1])
if old then redis.call('DEL', ARGV[4] .. old) end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
redis.call('HSET', KEYS[2], 'digest', ARGV[2], 'attempts', 0)
redis.call('EXPIRE', KEYS[2], ARGV[3])
return 1
