package com.jpmc.kcg.frw;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class FrwVaultTests {

	@SneakyThrows
	@Test
	void test() {
		SystemProperties systemProperties = new SystemProperties();
		systemProperties.setVaultKey(Map.of(
			"test1", Base64.encodeBase64String(StringUtils.getBytes("11111111111111111111111111111111", StandardCharsets.UTF_8)),
			"test2", Base64.encodeBase64String(StringUtils.getBytes("22222222222222222222222222222222", StandardCharsets.UTF_8))
//			"test1", Base64.encodeBase64String(StringUtils.getBytes("1111111111111111", StandardCharsets.UTF_8)),
//			"test2", Base64.encodeBase64String(StringUtils.getBytes("2222222222222222", StandardCharsets.UTF_8))
		));
		FrwVault frwVault = new FrwVault(systemProperties);
		frwVault.afterPropertiesSet();
		String str = "test";
		log.debug("{}", str);
		str = frwVault.encB64Str("test1", str);
		log.debug("{}", str);
		str = frwVault.decB64Str("test1", str);
		log.debug("{}", str);
		str = frwVault.encHexStr("test2", str);
		log.debug("{}", str);
		str = frwVault.decHexStr("test2", str);
		log.debug("{}", str);
	}

}
