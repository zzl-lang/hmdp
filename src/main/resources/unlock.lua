--与当前线程标识做比对
if(ARGV[1] == redis.call('get',KEYS[1]))  then
    --如果一致则释放锁
    return redis.call('DEL',KEYS[1])
end
return 0