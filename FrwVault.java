package com.jpmc.kcg.frw;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.management.openmbean.InvalidKeyException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class FrwVault implements InitializingBean {

	private final SystemProperties systemProperties;
	private Map<String, Entry<byte[], byte[]>> vKeyMap = new TreeMap<>();
	private ThreadLocal<Map<String, Cipher>> threadLocal = ThreadLocal.withInitial(TreeMap::new);
	private Cipher newCipher(int opmode, String key) throws GeneralSecurityException {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// SEED ///////////////////////////////////////////////////////////////////
		Provider provider = Security.getProvider("BC");
		if (null == provider) {
			Security.addProvider(new BouncyCastleProvider());
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		String str = StringUtils.join(String.valueOf(opmode), key);
		Map<String, Cipher> map = threadLocal.get();
		Cipher cipher = map.get(str);
		if (null == cipher) {
			Entry<byte[], byte[]> entry = vKeyMap.get(key);
			if (null == entry) {
				throw new InvalidKeyException(key);
			}
			String transformation = "AES/CBC/PKCS5Padding";
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// SEED ///////////////////////////////////////////////////////////////
			if (32 > entry.getKey().length) {
				transformation = "SEED/CBC/PKCS5Padding";
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			cipher = Cipher.getInstance(transformation);
			cipher.init(opmode, new SecretKeySpec(entry.getKey(), "AES"), new IvParameterSpec(entry.getValue()));
			map.put(str, cipher);
		}
		return cipher;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 볼트키초기화작업 ///////////////////////////////////////////////////////
		log.debug("afterPropertiesSet");
		systemProperties.getVaultKey().entrySet().forEach(entry -> {
			log.debug("{}", entry);
			byte[] key;
			byte[] iv;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 볼트키암호화키복호화 ///////////////////////////////////////////////
			String[] stringArray = StringUtils.split(entry.getValue(), '&');
			key = Base64.decodeBase64(stringArray[0]);
			if (1 < stringArray.length) {
				iv = Base64.decodeBase64(stringArray[1]);;
			} else {
				iv = Arrays.copyOf(key, 16);
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			vKeyMap.put(entry.getKey(), Map.entry(key, iv));
		});
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
	}

	public String encB64Str(String key, String str) {
		String enc = str;
		try {
			enc = Base64.encodeBase64String(newCipher(Cipher.ENCRYPT_MODE, key).doFinal(StringUtils.getBytes(str, "EUC-KR")));
		} catch (Throwable t) {
			log.error("encB64Str {}, {}", key, str);
		}
		return enc;
	}

	public String decB64Str(String key, String str) {
		String dec = str;
		try {
			dec = IOUtils.toString(newCipher(Cipher.DECRYPT_MODE, key).doFinal(Base64.decodeBase64(str)), "EUC-KR");
		} catch (Throwable t) {
			log.error("decB64Str {}, {}", key, str);
		}
		return dec;
	}

	public String encHexStr(String key, String str) {
		String enc = str;
		try {
			enc = Hex.encodeHexString(newCipher(Cipher.ENCRYPT_MODE, key).doFinal(StringUtils.getBytes(str, "EUC-KR")));
		} catch (Throwable t) {
			log.error("encHexStr {}, {}", key, str);
		}
		return enc;
	}

	public String decHexStr(String key, String str) {
		String dec = str;
		try {
			dec = IOUtils.toString(newCipher(Cipher.DECRYPT_MODE, key).doFinal(Hex.decodeHex(str)), "EUC-KR");
		} catch (Throwable t) {
			log.error("decHexStr {}, {}", key, str);
		}
		return dec;
	}

}
