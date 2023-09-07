package com.mycompany.gatewayservices;

import com.mycompany.gatewayservices.configuration.RedisHashComponent;
import com.mycompany.gatewayservices.constant.AppConstant;
import com.mycompany.gatewayservices.domain.ApiKey;
import com.mycompany.gatewayservices.filter.ApiKeyFilter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
public class GatewayServicesApplication {

	@Autowired
	RedisHashComponent redisHashComponent;
	public static void main(String[] args) {
		SpringApplication.run(GatewayServicesApplication.class, args);
	}

	@PostConstruct
	public void initData() {
		List<ApiKey> apiKeys = new ArrayList<>();
		apiKeys.add(new ApiKey("343C-ED0B-4137-B27E", List.of(AppConstant.AUTHENTICATION_SERVICE_KEY)));
		apiKeys.add(new ApiKey("FA48-EF0C-427E-8CCF", List.of(AppConstant.PRODUCT_SERVICE_KEY)));
		List<Object> list = redisHashComponent.hashValues(AppConstant.RECORD_KEY);
		if(list.isEmpty()){
			apiKeys.forEach(k -> redisHashComponent.hashSet(AppConstant.RECORD_KEY,k.getKey() , k));
		}
	}

}
