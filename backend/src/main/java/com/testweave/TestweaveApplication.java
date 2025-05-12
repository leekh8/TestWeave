// main class
package com.testweave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TestweaveApplication {

    public static void main(String[] args) {
        //        메모리에서 동작하는 DB
        //        JDBC URL: jdbc:h2:mem:testdb
        System.out.println("Test Weave Start");
        SpringApplication.run(TestweaveApplication.class, args);
    }

}
