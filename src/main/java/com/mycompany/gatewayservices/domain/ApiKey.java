package com.mycompany.gatewayservices.domain;


import lombok.*;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@ToString
@EqualsAndHashCode
public class ApiKey {
    private String key;
    private List<String> services;
}
