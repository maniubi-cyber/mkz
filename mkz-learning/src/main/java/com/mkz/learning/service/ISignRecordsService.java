package com.mkz.learning.service;

import com.mkz.learning.domain.vo.SignResultVO;

public interface ISignRecordsService {
    SignResultVO addSignRecords();

    Byte[] querySignRecords();
}
