package com.example.server.thread;

import com.example.model.entity.RandomCode;
import com.example.model.mapper.RandomCodeMapper;
import com.example.server.utils.RandomUtil;

/**
 * 随机数生成唯一的订单id测试
 *
 **/
public class CodeGenerateThread implements Runnable {

    private RandomCodeMapper randomCodeMapper;

    public CodeGenerateThread(RandomCodeMapper randomCodeMapper) {
        this.randomCodeMapper = randomCodeMapper;
    }

    @Override
    public void run() {
        RandomCode entity = new RandomCode();
        entity.setCode(RandomUtil.generateOrderCode());
        randomCodeMapper.insertSelective(entity);
    }
}