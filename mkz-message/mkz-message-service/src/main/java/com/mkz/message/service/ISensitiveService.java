package com.mkz.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.message.domain.po.Sensitive;
import com.mkz.message.domain.query.SensitiveQuery;

import java.util.List;

public interface ISensitiveService extends IService<Sensitive> {
    PageDTO<Sensitive> getAllSensitiveWords(SensitiveQuery query);

    boolean saveSensitive(Sensitive sensitive);

    boolean updateSensitive(Sensitive sensitive);

    boolean deleteSensitive(Long id);
}
