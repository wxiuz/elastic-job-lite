package com.xiuz.stux.elastic.job.lite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

@ImportResource("classpath:elastic-job.xml")
@SpringBootApplication
public class ElasticJobLiteStuxApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElasticJobLiteStuxApplication.class, args);
    }

}
