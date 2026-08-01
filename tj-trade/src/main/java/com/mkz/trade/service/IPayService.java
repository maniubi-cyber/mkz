package com.mkz.trade.service;

import com.mkz.trade.domain.dto.OrderDelayQueryDTO;
import com.mkz.trade.domain.dto.PayApplyFormDTO;
import com.mkz.trade.domain.vo.PayChannelVO;

import java.util.List;

public interface IPayService {
    List<PayChannelVO> queryPayChannels();

    String applyPayOrder(PayApplyFormDTO payApply);

    void queryPayResult(OrderDelayQueryDTO message);
}
