package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result Result(Long id) {
        // 解决缓存穿透问题
        //return queryWithPassThrough(id);
        // 利用互斥锁解决缓存击穿问题
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("商品信息不存在");
        }
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.在Redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StringUtils.isNotBlank(shopJson)){
            // 3.存在，String转为Json
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否是空值,空字符串
        if(shopJson != null){
            return null;
        }
        //4.利用互斥锁解决缓存击穿
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            //4.1 未命中，尝试获取互斥锁
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否获取锁
            if(!isLock){
                //4.3 获取失败，休眠
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 获取成功，根据id查询数据库
            //5.从数据库中查询
            shop = getById(id);
            //6.不存在，返回错误
            if(shop==null){
                //Redis中存入空对象
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //7.存在，将数据存入Redis   设置缓存时间为30分钟
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //8.释放互斥锁
           unLock(lockKey);
        }
        //9.返回数据
        return shop;
    }

    public Result queryWithPassThrough(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.在Redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断Redis中的数据中是否存在实值
        if(StringUtils.isNotBlank(shopJson)){
            // 存在，String转为Json
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //判断命中的是否是空字符串
        if(shopJson != null){
            return Result.fail("商品信息不存在");
        }
        //3.不存在,根据id查询
        //4.从数据库中查询
        Shop shop = getById(id);
        //5.不存在，返回错误
        if(shop==null){
            //Redis中存入空对象
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);

            return Result.fail("商家信息不存在");
        }
        //6.存在，将数据存入Redis   设置缓存时间为30分钟
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shop);
    }

    //尝试获取锁的方法
    private boolean tryLock(String key){
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isLock);
    }
    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("信息为空");
        }
        //1. 更新数据库
        updateById(shop);
        //2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok("更新成功");
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1. 判断是否需要根据坐标进行查询
        if(x == null || y == null){
            // 不需要根据坐标查询，根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.  计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        //3. 查询Redis，按照距离排序、分页
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().
                radius(key, new Circle(new Point(x, y), 5000),
                        RedisGeoCommands.
                                GeoRadiusCommandArgs.
                                newGeoRadiusArgs().includeCoordinates().limit(end)
                );
        //4. 解析出id
        if(results == null){
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        // 4.1 截取from - end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result ->{
            String shopIdStr = result.getContent().getName();
            Distance distance = result.getDistance();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr,distance);
        });
        //5. 根据id查询店铺
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        //6. 返回
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }

    public void saveShop2Redis(Long id,Long expireSeconds){
        //1. 查询店铺数据
        Shop shop = getById(id);
        //2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.在Redis中查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StringUtils.isBlank(shopJson)){
            // 3.未命中，直接返回null
            return null;
        }
        //4. 命中，需要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //5.1 未过期，直接返回店铺信息
            return shop;
        }
        //5.2 已过期，需要缓存重建
        // 6.缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);;
                }
            });
        }
        //6.4 返回过期的商铺信息
        return shop;
    }
}
