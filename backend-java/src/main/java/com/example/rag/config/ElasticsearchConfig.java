package com.example.rag.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置类 - Spring Data ES (ES 8.x Client)
 *
 * <p>使用 ES 8.x 原生 Java API Client 替代已废弃的 RestHighLevelClient。</p>
 *
 * <p>注意：不能继承 {@code ElasticsearchConfiguration}，其父类自带同名的
 * {@code elasticsearchClient} 工厂方法，会导致 Bean 定义冲突（Ambiguous factory method）。</p>
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
public class ElasticsearchConfig {

    @Value("${spring.data.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUrl;

    @Value("${spring.data.elasticsearch.username:}")
    private String username;

    @Value("${spring.data.elasticsearch.password:}")
    private String password;

    /**
     * 创建 ES 8.x 原生 Transport（供 ElasticsearchClient 使用）
     */
    @Bean
    public RestClient restClient() {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(
                        elasticsearchUrl.replace("http://", "").replace("https://", "").split(":")[0],
                        Integer.parseInt(elasticsearchUrl.replace("http://", "").replace("https://", "").split(":")[1]),
                        "http"
                )
        );

        if (username != null && !username.isEmpty()) {
            builder.setHttpClientConfigCallback(hcb ->
                    hcb.setDefaultCredentialsProvider(
                            new org.apache.http.impl.client.BasicCredentialsProvider() {{
                                setCredentials(
                                        org.apache.http.auth.AuthScope.ANY,
                                        new org.apache.http.auth.UsernamePasswordCredentials(username, password)
                                );
                            }}
                    )
            );
        }

        builder.setRequestConfigCallback(rcb ->
                rcb.setConnectTimeout(10_000).setSocketTimeout(30_000)
        );

        log.info("Elasticsearch RestClient 初始化完成: {}", elasticsearchUrl);
        return builder.build();
    }

    /**
     * ES 8.x 原生 Client（推荐）
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(mapper)
        );
        ElasticsearchClient client = new ElasticsearchClient(transport);
        log.info("Elasticsearch ElasticsearchClient 初始化完成: {}", elasticsearchUrl);
        return client;
    }
}
