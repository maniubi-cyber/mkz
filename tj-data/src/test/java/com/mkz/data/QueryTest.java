//package com.mkz.data;/**
// * @author fsq
// * @date 2025/6/20 10:41
// */
//
//import com.mkz.data.influxdb.domain.BusinessLog;
//import com.mkz.data.mapper.BusinessLogMapper;
//import com.mkz.data.service.IBusinessLogService;
//import com.mkz.data.utils.TimeHandlerUtils;
//import org.checkerframework.checker.units.qual.A;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import java.util.List;
//
///**
// * @Author: fsq
// * @Date: 2025/6/20 10:41
// * @Version: 1.0
// */
//@SpringBootTest
//public class QueryTest {
//
//    @Autowired
//    private BusinessLogMapper businessLogMapper;
//
//    @Test
//    public void testQuery() {
//        //统计当天所有注册用户
//        List<BusinessLog> businessLogs = businessLogMapper.login(TimeHandlerUtils.getTodayTime().getBegin(), TimeHandlerUtils.getTodayTime().getEnd());
//        for (BusinessLog businessLog : businessLogs) {
//            System.out.println(businessLog);
//        }
////        List<String> strings = businessLogMapper.allDauForUserId(TimeHandlerUtils.getTodayTime().getBegin(), TimeHandlerUtils.getTodayTime().getEnd() );
////        for (String string : strings) {
////            System.out.println(string);
////        }
//    }
//
//}
