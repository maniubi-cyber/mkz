package com.mkz.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.learning.domain.po.PointsMallItems;
import com.mkz.learning.domain.query.PointsItemsPageQuery;

public interface IPointsMallItemsService extends IService<PointsMallItems> {

    PageDTO<PointsMallItems> queryPointsItemsByPage(PointsItemsPageQuery query);
}