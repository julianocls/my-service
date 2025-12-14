package com.kubernetes.my_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/", produces = "application/json")
public class TestController {

    private static final String HELLO_MESSAGE = "Hello Kubernetes";

    @GetMapping(path = {"/hello", "/hello/"})
    @ResponseStatus(HttpStatus.OK)
    public String getHello() {
        return HELLO_MESSAGE;
    }

}