package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_KEY + "TP";
        //1.查询Redis中是否有缓存
        String TypeList = stringRedisTemplate.opsForValue().get(key);
        //2.如果存在
        if(StringUtils.isNotBlank(TypeList)){
            //将String类型转为java对象
            List<ShopType> shopTypeList = JSONUtil.toList(TypeList, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //3.Redis中不存在，从数据库中查询
        List<ShopType> list = list();
        //4.如果数据库中也没有，返回错误
        if(list==null){
            return Result.fail("404");
        }
        //4.将数据写回Redis缓存
        String jsonStr = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
        //5.返回List
        return Result.ok(list);
    }
}
