package com.example.server.controller;

import com.example.api.enums.StatusCode;
import com.example.api.response.BaseResponse;
import com.example.model.dto.KillSuccessUserInfo;
import com.example.model.mapper.ItemKillSuccessMapper;
import com.example.server.dto.KillDto;
import com.example.server.service.IKillService;
import com.example.server.service.RabbitSenderService;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * 秒杀controller
 **/
@Controller
public class KillController {

    private static final Logger log = LoggerFactory.getLogger(KillController.class);

    private static final String prefix = "kill";

    @Autowired
    private IKillService killService;

    @Autowired
    private ItemKillSuccessMapper itemKillSuccessMapper;

    /***
     * 商品秒杀核心业务逻辑
     * @param dto
     * @param result
     * @return
     */
    @RequestMapping(value = prefix + "/execute/lock", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public BaseResponse executeLock(@RequestBody @Validated KillDto dto, BindingResult result, HttpSession session) {

        if (result.hasErrors() || dto.getKillId() <= 0) {
            return new BaseResponse(StatusCode.InvalidParams);
        }
        Object uid = session.getAttribute("uid");
        if (uid == null) {
            return new BaseResponse(StatusCode.UserNotLogin);
        }
        Integer userId = (Integer) uid;

        BaseResponse response = new BaseResponse(StatusCode.Success);
        try {
            // 没有添加任何的锁
            /*Boolean res = killService.killItemV1(dto.getKillId(), userId);
            if (!res) {
                return new BaseResponse(StatusCode.Fail.getCode(), "商品已抢购完毕或不在抢购时间段!");
            }*/

            //不加分布式锁的前提，只是优化mysql查询条件
            Boolean res = killService.killItemV2(dto.getKillId(), userId);
            if (!res) {
                return new BaseResponse(StatusCode.Fail.getCode(), "不加分布式锁-哈哈~商品已抢购完毕或者不在抢购时间段哦!");
            }

            //基于Redis的分布式锁进行控制
            /*Boolean res=killService.killItemV3(dto.getKillId(),userId);
            if (!res){
                return new BaseResponse(StatusCode.Fail.getCode(),"基于Redis的分布式锁进行控制-哈哈~商品已抢购完毕或者不在抢购时间段哦!");
            }*/

            //基于Redisson的分布式锁进行控制
            /*Boolean res=killService.killItemV4(dto.getKillId(),userId);
            if (!res){
                return new BaseResponse(StatusCode.Fail.getCode(),"基于Redisson的分布式锁进行控制-哈哈~商品已抢购完毕或者不在抢购时间段哦!");
            }*/

            //基于ZooKeeper的分布式锁进行控制
            /*Boolean res = killService.killItemV5(dto.getKillId(), dto.userId);
            if (!res) {
                return new BaseResponse(StatusCode.Fail.getCode(), "基于ZooKeeper的分布式锁进行控制-哈哈~商品已抢购完毕或者不在抢购时间段哦!");
            }*/

        } catch (Exception e) {
            response = new BaseResponse(StatusCode.Fail.getCode(), e.getMessage());
        }
        return response;
    }

    /**
     * 查看订单详情
     * http://localhost:8083/kill/record/detail/343147116421722112
     *
     */
    @RequestMapping(value = prefix + "/record/detail/{orderNo}", method = RequestMethod.GET)
    public String killRecordDetail(@PathVariable String orderNo, ModelMap modelMap) {
        System.out.println("查看订单详情");
        if (StringUtils.isBlank(orderNo)) {
            return "error";
        }
        KillSuccessUserInfo info = itemKillSuccessMapper.selectByCode(orderNo);
        System.out.println("打印info信息");
        System.out.println(info);
        if (info == null) {
            return "error";
        }
        modelMap.put("info", info);
        return "killRecord";
    }

    /*
    * 抢购成功跳转页面
    *
    * */
    @RequestMapping(value = prefix + "/execute/success", method = RequestMethod.GET)
    public String executeSuccess() {
        return "executeSuccess";
    }

    /*
    * 抢购失败跳转页面
    *
    * */
    @RequestMapping(value = prefix + "/execute/fail", method = RequestMethod.GET)
    public String executeFail() {
        return "executeFail";
    }

    @Autowired
    private RabbitSenderService rabbitSenderService;

    /*
    * 商品秒杀核心业务逻辑-mq限流，将结果发送给消息队列，在进过消息队列进行处理
    *
    * */
    @RequestMapping(value = prefix + "/execute/mq", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public BaseResponse executeMq(@RequestBody @Validated KillDto dto, BindingResult result, HttpSession session) {
        if (result.hasErrors() || dto.getKillId() <= 0) {
            return new BaseResponse(StatusCode.InvalidParams);
        }
        Object uId = session.getAttribute("uid");
        if (uId == null) {
            return new BaseResponse(StatusCode.UserNotLogin);
        }
        Integer userId = (Integer) uId;

        BaseResponse response = new BaseResponse(StatusCode.Success);
        Map<String, Object> dataMap = Maps.newHashMap();
        try {
            dataMap.put("killId", dto.getKillId());
            dataMap.put("userId", userId);
            response.setData(dataMap);

            dto.setUserId(userId);
            rabbitSenderService.sendKillExecuteMqMsg(dto);
        } catch (Exception e) {
            response = new BaseResponse(StatusCode.Fail.getCode(), e.getMessage());
        }
        return response;
    }

    /*
    * 商品秒杀核心业务逻辑-mq限流-跳转至抢购结果页
    *
    * */
    @RequestMapping(value = prefix + "/execute/mq/to/result", method = RequestMethod.GET)
    public String executeToResult(@RequestParam Integer killId, HttpSession session, ModelMap modelMap) {
        Object uId = session.getAttribute("uid");
        if (uId != null) {
            Integer userId = (Integer) uId;

            modelMap.put("killId", killId);
            modelMap.put("userId", userId);
        }
        return "executeMqResult";
    }

    /*
    * 商品秒杀核心业务逻辑-mq限流-在抢购结果页中发起抢购结果的查询
    *
    * */
    @RequestMapping(value = prefix + "/execute/mq/result", method = RequestMethod.GET)
    @ResponseBody
    public BaseResponse executeResult(@RequestParam Integer killId, @RequestParam Integer userId) {
        BaseResponse response = new BaseResponse(StatusCode.Success);
        try {
            Map<String, Object> resMap = killService.checkUserKillResult(killId, userId);
            response.setData(resMap);
        } catch (Exception e) {
            response = new BaseResponse(StatusCode.Fail.getCode(), e.getMessage());
        }
        return response;
    }

    /*
    * TODO 压力测试：
    *   分别测试在没有使用消息队列和使用消息队列的情况
    *   在压力测试情况下，没有HttpSession，所以userId只能通过dto传送。
    *
    * */

    /***
     * 商品秒杀核心业务逻辑，无消息队列压力测试
     * @param dto
     * @param result
     * @return
     */
    @RequestMapping(value = prefix + "/execute", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public BaseResponse execute(@RequestBody @Validated KillDto dto, BindingResult result) {

        if (result.hasErrors() || dto.getKillId() <= 0) {
            return new BaseResponse(StatusCode.InvalidParams);
        }

        BaseResponse response = new BaseResponse(StatusCode.Success);
        try {
            // 没有添加任何的锁
            Boolean res = killService.killItemV1(dto.getKillId(), dto.getUserId());
            if (!res) {
                return new BaseResponse(StatusCode.Fail.getCode(), "商品已抢购完毕或不在抢购时间段!");
            }
        } catch (Exception e) {
            response = new BaseResponse(StatusCode.Fail.getCode(), e.getMessage());
        }
        return response;
    }

    /*
    * 商品秒杀核心业务逻辑-mq限流压测
    *
    * */
    @RequestMapping(value = prefix + "/execute/mq/lock", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    @ResponseBody
    public BaseResponse executeMqLock(@RequestBody @Validated KillDto dto, BindingResult result) {
        if (result.hasErrors() || dto.getKillId() <= 0) {
            return new BaseResponse(StatusCode.InvalidParams);
        }

        BaseResponse response = new BaseResponse(StatusCode.Success);
        Map<String, Object> dataMap = Maps.newHashMap();
        try {
            dataMap.put("killId", dto.getKillId());
            dataMap.put("userId", dto.getUserId());
            response.setData(dataMap);

            rabbitSenderService.sendKillExecuteMqMsg(dto);
        } catch (Exception e) {
            response = new BaseResponse(StatusCode.Fail.getCode(), e.getMessage());
        }
        return response;
    }
}

