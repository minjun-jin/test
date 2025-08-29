package entropy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.ip.dsl.Tcp;
import org.springframework.integration.ip.tcp.serializer.TcpCodecs;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.Db2JdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.MySqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.OracleJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.SqlServerJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import com.github.jknack.handlebars.helper.ConditionalHelpers;
import com.github.jknack.handlebars.springmvc.HandlebarsViewResolver;

import entropy.handlebars.SpringSecurityHelper;
import entropy.handlebars.TableHelper;
import entropy.handlebars.UriHelper;
import entropy.jpa.entity.E목록;
import entropy.jpa.entity.E빅터;
import entropy.jpa.entity.E빅터$;
import entropy.jpa.entity.E서버;
import entropy.jpa.entity.E시스템;
import entropy.jpa.entity.E전역;
import entropy.jpa.entity.QE목록;
import entropy.jpa.entity.QE시스템;
import entropy.repository.R목록;
import entropy.repository.R서버;
import entropy.repository.R시스템;
import entropy.repository.R전역;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

@Slf4j
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class EntropyConfig {

	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
	@Lazy(false)
	CriteriaBuilderFactory createCriteriaBuilderFactory() {
		CriteriaBuilderConfiguration criteriaBuilderConfiguration = Criteria.getDefault();
		return criteriaBuilderConfiguration.createCriteriaBuilderFactory(entityManagerFactory);
	}

	@Configuration
	static class ConditionHelpersAutoConfiguration {

		@Autowired
		private HandlebarsViewResolver handlebarsViewResolver;

		@PostConstruct
		public void registerHelpers() {
			handlebarsViewResolver.registerHelpers(ConditionalHelpers.class);
		}

	}

	@Configuration
	static class SpringSecurityHelperAutoConfiguration {

		@Autowired
		private HandlebarsViewResolver handlebarsViewResolver;
		@Autowired
		private SpringSecurityHelper springSecurityHelper;

		@PostConstruct
		public void registerHelpers() {
			handlebarsViewResolver.registerHelper("authorize", springSecurityHelper);
		}

	}

	@Configuration
	static class TableHelperAutoConfiguration {

		@Autowired
		private HandlebarsViewResolver handlebarsViewResolver;
		@Autowired
		private TableHelper tableHelper;

		@PostConstruct
		public void registerHelpers() {
			handlebarsViewResolver.registerHelper("width", tableHelper);
		}

	}

	@Configuration
	static class UrlHelperAutoConfiguration {

		@Autowired
		private HandlebarsViewResolver handlebarsViewResolver;

		@PostConstruct
		public void registerHelpers() {
			handlebarsViewResolver.registerHelpers(UriHelper.class);
		}

	}

	@Bean
	SwaggerIndexTransformer swaggerIndexTransformer(
		SwaggerUiConfigProperties swaggerUiConfigProperties,
		SwaggerUiOAuthProperties swaggerUiOAuthProperties,
		SwaggerWelcomeCommon swaggerWelcomeCommon,
		ObjectMapperProvider objectMapperProvider) {
		return new SwaggerIndexPageTransformer(swaggerUiConfigProperties, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider) {

			@Override
			public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain transformerChain) throws IOException {
				String str = resource.getFilename();
				if (Strings.CS.equals(str, "swagger-ui.css")) {
					return new TransformedResource(resource, StringUtils.getBytes(StringUtils.join(
						"@import url('/webjars/d2coding/1.3.2/d2coding-full.css');",
						System.lineSeparator(),
						Strings.CS.replace(Strings.CS.replace(resource.getContentAsString(StandardCharsets.UTF_8),
							"font-family:",
							"font-family:D2Coding,"
						),
							"font-family:D2Coding,inherit",
							"font-family:inherit"
						),
						System.lineSeparator(),
						".swagger-ui .parameter__name {white-space: nowrap;}"
					), StandardCharsets.UTF_8));
				}
				return super.transform(request, resource, transformerChain);
			}

		};
	}

	@Bean
	OpenAPI openAPI(@Value("${spring.application.name}") String applicationName) {
		return new OpenAPI().components(new Components()).info(new Info()
			.title(StringUtils.capitalize(applicationName))
			.version(SpringBootVersion.getVersion())
			.description(StringUtils.join(StringUtils.capitalize(applicationName), " API"))
		);
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsManager userDetailsManager(DataSource dataSource) {
		JdbcUserDetailsManager jdbcUserDetailsManager = new JdbcUserDetailsManager(dataSource);
		jdbcUserDetailsManager.setAuthenticationManager(new AuthenticationManager() {

			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				return authentication;
			}

		});
		return jdbcUserDetailsManager;
	}

	@Bean
	PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
		JdbcTokenRepositoryImpl jdbcTokenRepositoryImpl = new JdbcTokenRepositoryImpl();
		jdbcTokenRepositoryImpl.setDataSource(dataSource);
		return jdbcTokenRepositoryImpl;
	}

	@SneakyThrows
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity, PersistentTokenRepository persistentTokenRepository) {
		return httpSecurity.csrf(csrfConfigurer -> csrfConfigurer.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
			.ignoringRequestMatchers("/shared/**")
			.ignoringRequestMatchers("/결함")
			.ignoringRequestMatchers("/콜백")
		).authorizeHttpRequests(authorizeHttpRequestsConfigurer -> authorizeHttpRequestsConfigurer
			.requestMatchers("/*.ico")
				.permitAll()
			.requestMatchers("/*.png")
				.permitAll()
			.requestMatchers("/*.svg")
				.permitAll()
			.requestMatchers("/*.xml")
				.permitAll()
			.requestMatchers("/site.webmanifest")
				.permitAll()
			.requestMatchers("/actuator/health")
				.permitAll()
			.requestMatchers("/webjars/**")
				.permitAll()
			.requestMatchers("/shared/**")
				.permitAll()
			.requestMatchers("/결함")
				.permitAll()
			.requestMatchers("/목록")
				.permitAll()
			.requestMatchers("/목록/**")
				.permitAll()
			.requestMatchers("/문서/**")
				.permitAll()
			.requestMatchers("/설정")
				.permitAll()
			.requestMatchers("/설정/**")
				.permitAll()
			.requestMatchers("/첨부/**")
				.permitAll()
			.requestMatchers("/콜백")
				.permitAll()
			.requestMatchers("/필터")
				.permitAll()
			.anyRequest()
				.authenticated()
		).formLogin(formLoginConfigurer -> formLoginConfigurer
			.loginPage("/login").defaultSuccessUrl("/", true)
				.permitAll()
		).logout(logoutConfigurer -> logoutConfigurer
			.permitAll()
		).rememberMe(rememberMeConfigurer -> rememberMeConfigurer
			.tokenRepository(persistentTokenRepository)
		).build();
	}

	@SneakyThrows
	@Bean
	SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionRepositoryCustomizer(DataSource dataSource) {
		DatabaseDriver databaseDriver = DatabaseDriver.fromProductName(JdbcUtils.commonDatabaseName(JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName)));
		if (DatabaseDriver.DB2.equals(databaseDriver)) {
			return new Db2JdbcIndexedSessionRepositoryCustomizer();
		}
		if (DatabaseDriver.MARIADB.equals(databaseDriver) ||
			DatabaseDriver.MYSQL.equals(databaseDriver)) {
			return new MySqlJdbcIndexedSessionRepositoryCustomizer();
		}
		if (DatabaseDriver.ORACLE.equals(databaseDriver)) {
			return new OracleJdbcIndexedSessionRepositoryCustomizer();
		}
		if (DatabaseDriver.POSTGRESQL.equals(databaseDriver)) {
			return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
		}
		if (DatabaseDriver.SQLSERVER.equals(databaseDriver)) {
			return new SqlServerJdbcIndexedSessionRepositoryCustomizer();
		}
		return new SessionRepositoryCustomizer<>() {

			@Override
			public void customize(JdbcIndexedSessionRepository jdbcIndexedSessionRepository) {
			}

		};
	}

	@Bean
	Map<String, String> 전역(R전역 r전역) {
		return r전역.findAll(
		).stream().collect(Collectors.toMap(E전역::get이름, e전역 -> {
			return StringUtils.defaultString(e전역.get내용());
		}, ObjectUtils::getIfNull, TreeMap::new));
	}

	@Bean
	Map<String, List<String>> 목록(R목록 r목록) {
		return r목록.findAll(
			QE목록.e목록.이름.asc(),
			QE목록.e목록.순서.asc()
		).stream().collect(Collectors.groupingBy(E목록::get이름, TreeMap::new, Collectors.mapping(E목록::get내용, Collectors.toList())));
	}

	@Bean
	IntegrationFlow integrationSock(EntropyProperties properties,
		EntropyControllerVirtor m빅터,
		R서버 r서버, R시스템 r시스템) {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 시스템복원 ///////////////////////////////////////////////////////////////
		for (E시스템 e시스템 : r시스템.findAll(
			QE시스템.e시스템.시간.after(LocalDateTime.now().minus(600000L, ChronoUnit.MILLIS))
		,
			QE시스템.e시스템.시간.asc(),
			QE시스템.e시스템.이름.asc()
		)) {
			E빅터 e빅터 = e시스템.get빅터();
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 빅터추가 ///////////////////////////////////////////////////////////
			E빅터$ e빅터$ = m빅터.get(e빅터.getName());
			if (null == e빅터$) {
				e빅터$ = new E빅터$();
//				e빅터$.setName(e빅터.getName());
				m빅터.put(e빅터.getName(), e빅터$);
				Optional<E서버> o서버 = r서버.findById(e빅터.getName());
				if (o서버.isEmpty()) {
					E서버 e서버 = new E서버();
					e서버.set이름(e빅터.getName());
					e서버.set별명(e빅터.getName());
					e서버.set표시(StringUtils.EMPTY);
					r서버.save(e서버);
				}
			}
			e빅터$.offer(e빅터);
//			e빅터$.setTimestamp(e빅터.getTimestamp());
//			e빅터$.setPrecision(e빅터.getPrecision());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		return IntegrationFlow.from(Tcp.inboundGateway(Tcp.nioServer(properties.getPort()).soTcpNoDelay(true)
			.deserializer(TcpCodecs.lengthHeader4(65535)).serializer(TcpCodecs.lengthHeader4(65535)))
		).transform(Transformers.fromJson(E빅터.class)).<E빅터>handle((e빅터, 헤더) -> {
			if (log.isDebugEnabled()) {
				log.debug("{}", e빅터.getName());
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 빅터추가 ///////////////////////////////////////////////////////////
			E빅터$ e빅터$ = m빅터.get(e빅터.getName());
			if (null == e빅터$) {
				e빅터$ = new E빅터$();
//				e빅터$.setName(e빅터.getName());
				m빅터.put(e빅터.getName(), e빅터$);
				Optional<E서버> o서버 = r서버.findById(e빅터.getName());
				if (o서버.isEmpty()) {
					E서버 e서버 = new E서버();
					e서버.set이름(e빅터.getName());
					e서버.set별명(e빅터.getName());
					e서버.set표시(StringUtils.EMPTY);
					r서버.save(e서버);
				}
			}
			e빅터$.offer(e빅터);
//			e빅터$.setTimestamp(e빅터.getTimestamp());
//			e빅터$.setPrecision(e빅터.getPrecision());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 빅터제거 ///////////////////////////////////////////////////////////
			long l = e빅터.getTimestamp() - 600000L;
			Iterator<E빅터> i빅터 = e빅터$.iterator();
			while (i빅터.hasNext()) {
				if (l < i빅터.next().getTimestamp()) {
					break;
				}
				i빅터.remove();
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 시스템저장 ///////////////////////////////////////////////////////////
//			E시스템 e시스템 = new E시스템();
//			e시스템.set시간(EntropyUtils.toLocalDateTime(e빅터.getTimestamp()));
//			e시스템.set이름(e빅터.getName());
//			e시스템.set빅터(e빅터);
//			r시스템.save(e시스템);
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			return MessageBuilder.withPayload("OK").build();
		}).get();
	}

//	@Bean
//	Encoder encoder() {
//		return ESAPI.encoder();
//	}
//
//	@Bean
//	Codec<Character> codec() {
//		return SystemUtils.IS_OS_WINDOWS ? new WindowsCodec() : new UnixCodec();
//	}

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
//		return new JdbcLockProvider(dataSource);
		return new JdbcTemplateLockProvider(
			JdbcTemplateLockProvider.Configuration.builder()
			.withJdbcTemplate(new JdbcTemplate(dataSource))
			.usingDbTime() // Works on Postgres, MySQL, MariaDb, MS SQL, Oracle, DB2, HSQL and H2
			.build()
		);
	}

}
