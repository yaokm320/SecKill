package com.example.server.service.impl;

import com.example.model.dto.KillSuccessUserInfo;
import com.example.model.entity.ItemKill;
import com.example.model.entity.ItemKillSuccess;
import com.example.model.mapper.ItemKillMapper;
import com.example.model.mapper.ItemKillSuccessMapper;
import com.example.server.enums.SysConstant;
import com.example.server.service.IKillService;
import com.example.server.service.RabbitSenderService;
import com.example.server.utils.RandomUtil;
import com.example.server.utils.SnowFlake;
import com.google.common.collect.Maps;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.joda.time.DateTime;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class KillService implements IKillService {

    private static final Logger log = LoggerFactory.getLogger(KillService.class);

    private SnowFlake snowFlake = new SnowFlake(2, 3);

    @Autowired
    private ItemKillSuccessMapper itemKillSuccessMapper;

    @Autowired
    private ItemKillMapper itemKillMapper;

    @Autowired
    private RabbitSenderService rabbitSenderService;

    @Autowired
    private Environment env;

    /**
     * 商品秒杀核心业务逻辑的处理
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV1(Integer killId, Integer userId) throws Exception {

        Boolean result = false;

        //TODO: 1、判断当前用户是否已经抢购了当前商品
        if (itemKillSuccessMapper.countByKillUserId(killId, userId) <= 0) {
            // 查询秒杀商品详情
            ItemKill itemKill = itemKillMapper.selectById(killId);
            //TODO: 2、判断当前代抢购的商品库存是否充足、以及是否出在可抢的时间段内
            if (itemKill != null && 1 == itemKill.getCanKill()) {
                //TODO: 扣减库存
                int res = itemKillMapper.updateKillItem(killId);
                //TODO: 判断是否扣减成功?是-生成秒杀成功的订单、同时通知用户秒杀已经成功
                if (res > 0) {
                    this.commonRecordKillSuccessInfo(itemKill, userId);
                    result = true;
                }
            }
        } else {
            throw new Exception("您已经抢购过该商品了！");
        }
        return result;
    }

    /**
     * 通用的方法-记录用户秒杀成功后生成的订单
     * 并进行异步邮件消息的通知
     *
     * @param itemKill
     * @param userId
     * @throws Exception
     */
    private void commonRecordKillSuccessInfo(ItemKill itemKill, Integer userId) throws Exception {
        //TODO:记录抢购成功后生成的秒杀订单记录
        ItemKillSuccess entity = new ItemKillSuccess();

        //entity.setCode(RandomUtil.generateOrderCode());     //传统时间戳+N位随机数，设置订单号
        String orderNo = String.valueOf(snowFlake.nextId());  //雪花算法设置订单号

        entity.setCode(orderNo);
        entity.setItemId(itemKill.getItemId());
        entity.setKillId(itemKill.getId());
        entity.setUserId(userId.toString());
        entity.setStatus(SysConstant.OrderStatus.SuccessNotPayed.getCode().byteValue());
        entity.setCreateTime(DateTime.now().toDate());

        // 插入一条秒杀成功的记录并且分别将信息发送给邮件消息队列和订单死信队列
        // TODO:仿照单例模式的双重检验锁写法，再次判断，只有当前用户没有秒杀过当前商品才可以插入数据，还是会存在一人多单的问题
        if (itemKillSuccessMapper.countByKillUserId(itemKill.getId(), userId) <= 0) {
            // 秒杀成功，插入一条成功的记录
            int res = itemKillSuccessMapper.insertSelective(entity);
            if (res > 0) {
                //TODO:进行异步邮件消息的通知=rabbitmq+mail
                rabbitSenderService.sendKillSuccessEmailMsg(orderNo);

                //TODO:入死信队列，用于 “失效” 超过指定的TTL时间时仍然未支付的订单
                rabbitSenderService.sendKillSuccessOrderExpireMsg(orderNo);
            }
        }
    }

    /**
     * 商品秒杀核心业务逻辑的处理-mysql层面的优化，在高并发情况下还是会有问题
     * 优化1： 查询item的数量要保证待秒杀的数量大于0
     * 优化2： 扣件库存，库存数量大于0才可以扣减
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV2(Integer killId, Integer userId) throws Exception {
        Boolean result = false;

        // TODO: 1、判断当前用户是否已经抢购过当前商品
        if (itemKillSuccessMapper.countByKillUserId(killId, userId) <= 0) {
            //TODO: A.查询待秒杀商品详情，主要是在SQL里面加了判断数量，要求待秒杀的数量应该大于0；
            ItemKill itemKill = itemKillMapper.selectByIdV2(killId);
            if (itemKill != null && 1 == itemKill.getCanKill() && itemKill.getTotal() > 0) {
                //TODO: B.扣减库存,库存数量大于0才可以扣减
                int res = itemKillMapper.updateKillItemV2(killId);
                //TODO: 判断扣减是否成功?是-生成秒杀成功的订单，同时通知用户秒杀成功的消息
                if (res > 0) {
                    commonRecordKillSuccessInfo(itemKill, userId);
                    result = true;
                }
            }
        } else {
            throw new Exception("您已经抢购过该商品了!");
        }
        return result;
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 商品秒杀核心业务逻辑的处理-redis的分布式锁
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV3(Integer killId, Integer userId) throws Exception {
        Boolean result = false;

        // 判断用户没有购买过
        if (itemKillSuccessMapper.countByKillUserId(killId, userId) <= 0) {
            //TODO:借助Redis的原子操作实现分布式锁-对共享操作-资源进行控制
            ValueOperations valueOperations = stringRedisTemplate.opsForValue();
            final String key = new StringBuffer().append(killId).append(userId).append("-RedisLock").toString();
            final String value = RandomUtil.generateOrderCode();
            // setnx+expire，防止redis宕机导致的锁无法释放
            Boolean cacheRes = valueOperations.setIfAbsent(key, value); //lua脚本提供“分布式锁服务”，就可以写在一起
            // TODO: redis部署节点宕机了
            if (cacheRes) {
                // 到时候自动释放锁
                stringRedisTemplate.expire(key, 30, TimeUnit.SECONDS);
                try {
                    // 查询
                    ItemKill itemKill = itemKillMapper.selectByIdV2(killId);
                    // 判断
                    if (itemKill != null && 1 == itemKill.getCanKill() && itemKill.getTotal() > 0) {
                        // 更新，减库存
                        int res = itemKillMapper.updateKillItemV2(killId);
                        if (res > 0) {
                            // 生成订单
                            commonRecordKillSuccessInfo(itemKill, userId);
                            result = true;
                        }
                    }
                } catch (Exception e) {
                    throw new Exception("还没到抢购日期、已过了抢购时间或已被抢购完毕！");
                } finally {
                    if (value.equals(valueOperations.get(key).toString())) {
                        stringRedisTemplate.delete(key);
                    }
                }
            }
        } else {
            throw new Exception("Redis-您已经抢购过该商品了!");
        }
        return result;
    }

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 商品秒杀核心业务逻辑的处理-redisson的分布式锁
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV4(Integer killId, Integer userId) throws Exception {
        Boolean result = false;

        final String lockKey = new StringBuffer().append(killId).append(userId).append("-RedissonLock").toString();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            //TODO:第一个参数30s=表示尝试获取分布式锁，并且最大的等待获取锁的时间为30s
            //TODO:第二个参数10s=表示上锁之后，10s内操作完毕将自动释放锁
            Boolean cacheRes = lock.tryLock(30, 10, TimeUnit.SECONDS);
            if (cacheRes) {
                //TODO:核心业务逻辑的处理，没有变化
                if (itemKillSuccessMapper.countByKillUserId(killId, userId) <= 0) {
                    ItemKill itemKill = itemKillMapper.selectByIdV2(killId);
                    if (itemKill != null && 1 == itemKill.getCanKill() && itemKill.getTotal() > 0) {
                        int res = itemKillMapper.updateKillItemV2(killId);
                        if (res > 0) {
                            commonRecordKillSuccessInfo(itemKill, userId);

                            result = true;
                        }
                    }
                } else {
                    //throw new Exception("redisson-您已经抢购过该商品了!");
                    log.error("redisson-您已经抢购过该商品了!");
                }
            }
        } finally {
            //TODO:释放锁
            lock.unlock();
            // 强制释放锁
            //lock.forceUnlock();
        }
        return result;
    }

    @Autowired
    private CuratorFramework curatorFramework;

    private static final String pathPrefix = "/kill/zkLock/";

    /**
     * 商品秒杀核心业务逻辑的处理-基于ZooKeeper的分布式锁
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV5(Integer killId, Integer userId) throws Exception {
        Boolean result = false;

        InterProcessMutex mutex = new InterProcessMutex(curatorFramework, pathPrefix + killId + userId + "-lock");
        try {
            if (mutex.acquire(10L, TimeUnit.SECONDS)) {

                //TODO:核心业务逻辑
                if (itemKillSuccessMapper.countByKillUserId(killId, userId) <= 0) {
                    ItemKill itemKill = itemKillMapper.selectByIdV2(killId);
                    if (itemKill != null && 1 == itemKill.getCanKill() && itemKill.getTotal() > 0) {
                        int res = itemKillMapper.updateKillItemV2(killId);
                        if (res > 0) {
                            commonRecordKillSuccessInfo(itemKill, userId);
                            result = true;
                        }
                    }
                } else {
                    throw new Exception("zookeeper-您已经抢购过该商品了!");
                }
            }
        } catch (Exception e) {
            throw new Exception("还没到抢购日期、已过了抢购时间或已被抢购完毕！");
        } finally {
            if (mutex != null) {
                mutex.release();
            }
        }
        return result;
    }

    /**
     * 检查用户的秒杀结果
     *
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Map<String, Object> checkUserKillResult(Integer killId, Integer userId) throws Exception {
        Map<String, Object> dataMap = Maps.newHashMap();
        KillSuccessUserInfo info = itemKillSuccessMapper.selectByKillIdUserId(killId, userId);
        if (info != null) {
            dataMap.put("executeResult", String.format(env.getProperty("notice.kill.item.success.content"), info.getItemName()));
            dataMap.put("info", info);
        } else {
            throw new Exception(env.getProperty("notice.kill.item.fail.content"));
        }
        return dataMap;
    }

}
