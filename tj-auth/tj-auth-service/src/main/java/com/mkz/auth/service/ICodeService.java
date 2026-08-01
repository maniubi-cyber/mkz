package com.mkz.auth.service;

import com.mkz.common.exceptions.BadRequestException;

/**
 * @author fsq
 * @date 2025/5/20 10:05
 */
public interface ICodeService {

    void sendVerifyCode(String cellPhone);

    void verifyCode(String phone, String code) ;
}
