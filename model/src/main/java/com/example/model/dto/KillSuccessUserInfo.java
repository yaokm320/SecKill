package com.example.model.dto;

import com.example.model.entity.ItemKillSuccess;
import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀成功用户信息对象
 **/
@Data
public class KillSuccessUserInfo extends ItemKillSuccess implements Serializable {

    private String userName;

    private String phone;

    private String email;

    private String itemName;

    @Override
    public String toString() {
        return super.toString() + "\nKillSuccessUserInfo{" +
                "userName='" + userName + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", itemName='" + itemName + '\'' +
                '}';
    }
}