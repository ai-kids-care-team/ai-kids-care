package com.ai_kids_care.v1.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Bean
    public Driver neo4jDriver(
            @Value("${NEO4J_URI}") String uri,
            @Value("${NEO4J_USERNAME}") String username,
            @Value("${NEO4J_PASSWORD}") String password
    ) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}