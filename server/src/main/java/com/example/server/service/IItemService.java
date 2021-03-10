package com.example.server.service;

import com.example.model.entity.ItemKill;

import java.util.List;


public interface IItemService {

    List<ItemKill> getKillItems() throws Exception;

    ItemKill getKillDetail(Integer id) throws Exception;
}
