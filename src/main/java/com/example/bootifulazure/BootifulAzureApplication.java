package com.example.bootifulazure;

import com.microsoft.azure.servicebus.*;
import com.microsoft.azure.spring.data.cosmosdb.core.mapping.Document;
import com.microsoft.azure.spring.data.cosmosdb.repository.DocumentDbRepository;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.data.annotation.Id;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@SpringBootApplication
public class BootifulAzureApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootifulAzureApplication.class, args);
	}
}

@Log4j2
@Component
class SqlServerDemo {

	private final JdbcTemplate jdbcTemplate;

	SqlServerDemo(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void sqlServerDemo() {

		List<Customer> customerList = this.jdbcTemplate
			.query("select top 10 * from SalesLT.Customer",
				(rs, rowNum) -> new Customer(rs.getLong("customerid"), rs.getString("firstname"), rs.getString("lastname")));

		customerList.forEach(log::info);
	}
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Customer {
	private Long id;
	private String firstName, lastName;
}

@Log4j2
@Component
class ServiceBusDemo {

	private final ITopicClient topicClient;
	private final ISubscriptionClient subscriptionClient;

	ServiceBusDemo(ITopicClient tc, ISubscriptionClient sc) {
		this.topicClient = tc;
		this.subscriptionClient = sc;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void serviceBusDemo() throws Exception {

		this.subscriptionClient.registerMessageHandler(new IMessageHandler() {

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
	public void objectStorageServiceDemo() throws Exception {

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
	public void cosmosDbDemo() throws Exception {

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

@RestController
class GreetingsRestController {

	@GetMapping("/greetings")
	String greet() {
		return "hello, world!";
	}
}