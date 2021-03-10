package com.example.server.service;

import java.util.Map;


public interface IKillService {

    // 不使用锁，未进行数据库优化
    Boolean killItemV1(Integer killId, Integer userId) throws Exception;

    // 不使用锁，进行mysql优化
    Boolean killItemV2(Integer killId, Integer userId) throws Exception;

    // 使用redis分布式锁
    Boolean killItemV3(Integer killId, Integer userId) throws Exception;

    // 使用redisson分布式锁
    Boolean killItemV4(Integer killId, Integer userId) throws Exception;

    // 使用zookeeper分布式锁
    Boolean killItemV5(Integer killId, Integer userId) throws Exception;

    Map<String, Object> checkUserKillResult(Integer killId, Integer userId) throws Exception;
}
