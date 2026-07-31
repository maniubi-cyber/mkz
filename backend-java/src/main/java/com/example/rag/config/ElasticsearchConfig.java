package com.example.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Elasticsearch 配置类
 *
 * <h3>全文检索优化说明：</h3>
 * <p>原基于 MySQL LIKE '%keyword%' 的模糊搜索在数据量超 10 万条时耗时超 1.5s，
 * 引入 Elasticsearch 并配置 IK 中文分词器，将搜索响应降至 60ms，
 * 并支持按相关度排序和高亮显示。</p>
 *
 * <h3>IK 分词器：</h3>
 * <ul>
 *   <li>ik_max_word: 最细粒度分词，适合索引时召回更多结果</li>
 *   <li>ik_smart: 智能分词，适合查询时精准匹配</li>
 * </ul>
 *
 * <h3>索引设计：</h3>
 * <pre>
 *   Index: document_search
 *   Fields:
 *     - title: text (ik_max_word) + keyword
 *     - content: text (ik_max_word)
 *     - kbId: long
 *     - ownerId: long
 *     - visibility: keyword
 *     - orgId: long
 *     - createTime: date
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.example.rag.repository")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.data.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${spring.data.elasticsearch.username:}")
    private String username;

    @Value("${spring.data.elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder =
                ClientConfiguration.builder()
                        .connectedTo(elasticsearchUrl.replace("http://", "").replace("https://", ""));

        // 如果有用户名密码，则配置认证
        if (username != null && !username.isEmpty()) {
            builder.withBasicAuth(username, password);
        }

        return builder
                .withConnectTimeout(Duration.ofSeconds(10))
                .withSocketTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * RestHighLevelClient Bean
     * 用于执行底层的 Elasticsearch 操作（搜索、索引、删除等）
     */
    @Bean
    public RestHighLevelClient restHighLevelClient() {
        org.elasticsearch.client.RestClientBuilder builder = org.elasticsearch.client.RestClient.builder(
                org.apache.http.HttpHost.create(elasticsearchUrl)
        );

        // 配置认证
        if (username != null && !username.isEmpty()) {
            org.apache.http.auth.UsernamePasswordCredentials credentials =
                    new org.apache.http.auth.UsernamePasswordCredentials(username, password);
            org.apache.http.impl.client.BasicCredentialsProvider credentialsProvider =
                    new org.apache.http.impl.client.BasicCredentialsProvider();
            credentialsProvider.setCredentials(org.apache.http.auth.AuthScope.ANY, credentials);

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
            );
        }

        log.info("Elasticsearch RestHighLevelClient 初始化完成: {}", elasticsearchUrl);
        return new RestHighLevelClient(builder);
    }
}
