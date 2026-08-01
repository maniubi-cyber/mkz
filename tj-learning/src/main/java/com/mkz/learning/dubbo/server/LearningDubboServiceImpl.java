//package com.mkz.learning.dubbo.server;
//
//import com.mkz.api.interfaces.learning.LearningDubboService;
//import com.mkz.learning.service.ILearningLessonService;
//import org.apache.dubbo.config.annotation.DubboService;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.util.Arrays;
//
//@DubboService(version = "1.0.0", interfaceClass = LearningDubboService.class)
//public class LearningDubboServiceImpl implements LearningDubboService {
//
//    @Autowired
//    private ILearningLessonService learningLessonService;
//
//    @Override
//    public String sayHello(String name) {
//        int i = 1 / 0;
//        return "Hello  + name + ";
//    }
//
//    @Override
//    public void testSeata() {
//        learningLessonService.addUserLesson(129L, Arrays.asList(1549025085494521857L));
//    }
//}
