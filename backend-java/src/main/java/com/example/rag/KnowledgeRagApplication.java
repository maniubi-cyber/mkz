package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import org.springframework.retry.annotation.EnableRetry;

/**
 * Enterprise Knowledge Base RAG Q&A System
 *
 * @author knowledge-rag团队
 */
@EnableAsync
@EnableRetry
@SpringBootApplication
public class KnowledgeRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeRagApplication.class, args);
    }
}
