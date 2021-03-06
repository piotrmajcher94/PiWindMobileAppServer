package com.piotrmajcher.piwind.mobileappserver;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import com.piotrmajcher.piwind.mobileappserver.domain.UserEntity;
import com.piotrmajcher.piwind.mobileappserver.domain.VerificationToken;
import com.piotrmajcher.piwind.mobileappserver.repository.UserRepository;
import com.piotrmajcher.piwind.mobileappserver.services.UserService;

@Component
public class DevelopmentDataLoader implements ApplicationRunner {

	private UserService userService;
	
	private UserRepository userRepository;
	
	private boolean enabled = false;
	
	@Autowired
	public DevelopmentDataLoader(UserService userService, UserRepository userRepository) {
		this.userRepository = userRepository;
		this.userService = userService;
		AnnotationConfigApplicationContext ctx= new AnnotationConfigApplicationContext(EnviromentConfig.class);
		DevelopmentDataLoaderConfigurer config = ctx.getBean(DevelopmentDataLoaderConfigurer.class);
		enabled = config.isAddTestUser();
	}
	
	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (enabled) {
			UserEntity user = new UserEntity();
			user.setEmail("example@email.com");
			user.setPassword("password");
			user.setUsername("username");
			user.setEnabled(false);
			
	        VerificationToken token = userService.createAndSaveVerificationToken();
	        user.setToken(token);
	        
	        userRepository.save(user);
	        userService.confirmUser(token.getToken());
		}
	}

}
