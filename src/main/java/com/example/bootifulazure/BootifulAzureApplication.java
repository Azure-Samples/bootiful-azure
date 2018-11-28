package com.example.bootifulazure;

import java.net.URISyntaxException;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.ISubscriptionClient;
import com.microsoft.azure.servicebus.ITopicClient;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.spring.data.cosmosdb.core.mapping.Document;
import com.microsoft.azure.spring.data.cosmosdb.repository.DocumentDbRepository;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.annotation.Id;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

@SpringBootApplication
public class BootifulAzureApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootifulAzureApplication.class, args);
	}
}

@RestController
class GreetingsRestController {

	@PreAuthorize("hasRole('Users')")
	@GetMapping("/greetings")
	String greet(@AuthenticationPrincipal Principal principal) {
		return "hello " + principal.getName() + "!";
	}
}

@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
class WebSecurity extends WebSecurityConfigurerAdapter {

	private final OAuth2UserService<OidcUserRequest, OidcUser> auth2UserService;

	WebSecurity(OAuth2UserService<OidcUserRequest, OidcUser> auth2UserService) {
		this.auth2UserService = auth2UserService;
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {

		http
			.authorizeRequests().anyRequest().authenticated()
			.and()
			.oauth2Login()
			.userInfoEndpoint().oidcUserService(this.auth2UserService);

	}
}

@Log4j2
@Component
class ServiceBusDemo {

	private final ITopicClient topicClient;
	private final ISubscriptionClient iSubscriptionClient;

	ServiceBusDemo(ITopicClient topicClient, ISubscriptionClient iSubscriptionClient) {
		this.topicClient = topicClient;
		this.iSubscriptionClient = iSubscriptionClient;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void serviceBus() throws Exception {

		this.iSubscriptionClient.registerMessageHandler(new IMessageHandler() {

			@Override
			public CompletableFuture<Void> onMessageAsync(IMessage message) {

				log.info(String.format("new message having body '%s' and id '%s'",
					new String(message.getBody()), message.getMessageId()));

				return CompletableFuture.completedFuture(null);
			}

			@Override
			public void notifyException(Throwable exception, ExceptionPhase phase) {
				log.error("eek!", exception);
			}
		});

		Thread.sleep(1000);

		this.topicClient
			.send(new Message("Hello @ " + Instant.now().toString()));

	}
}

@Component
@Log4j2
class ObjectStorageServiceDemo {

	private final CloudStorageAccount cloudStorageAccount;
	private final Resource resource;
	private final CloudBlobContainer files;

	ObjectStorageServiceDemo(
		CloudStorageAccount cloudStorageAccount,
		@Value("classpath:/cat.jpg") Resource resource) throws URISyntaxException, StorageException {
		this.cloudStorageAccount = cloudStorageAccount;
		this.resource = resource;
		this.files = this.cloudStorageAccount
			.createCloudBlobClient()
			.getContainerReference("files");
	}

	@EventListener(ApplicationReadyEvent.class)
	public void objectStorageService() throws Exception {

		CloudBlockBlob cbb = this.files.getBlockBlobReference("cat-" + UUID.randomUUID().toString() + ".jpg");
		cbb.upload(this.resource.getInputStream(), this.resource.contentLength());
		log.info("uploaded blockblob to " + cbb.getStorageUri());
	}
}
@Component
@Log4j2
class CosmosDbDemo {

	private final ReservationRepository reservationRepository;

	CosmosDbDemo(ReservationRepository reservationRepository) {
		this.reservationRepository = reservationRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void cosmosDb() throws Exception {

		this.reservationRepository.deleteAll();

		Stream.of("A", "B", "C")
			.map(name -> new Reservation(null, name))
			.map(this.reservationRepository::save)
			.forEach(log::info);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "reservations")
class Reservation {

	@Id
	private String id;
	private String reservationName;
}

interface ReservationRepository extends DocumentDbRepository<Reservation, String> {
}

