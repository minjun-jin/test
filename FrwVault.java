package com.jpmc.kcg.frw;

import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.Security;
import java.util.Map;
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
	private Map<String, byte[]> vKeyMap = new TreeMap<>();
	private ThreadLocal<Map<String, Cipher>> threadLocal = ThreadLocal.withInitial(TreeMap::new);
	private Cipher newCipher(int opmode, String key) throws GeneralSecurityException {
		String str = StringUtils.join(String.valueOf(opmode), key);
		Map<String, Cipher> map = threadLocal.get();
		Cipher cipher = map.get(str);
		if (null == cipher) {
			byte[] byteArray = vKeyMap.get(key);
			if (null == byteArray) {
				throw new InvalidKeyException(key);
			}
			if (16 < byteArray.length) { // AES 256
				cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
				cipher.init(opmode, new SecretKeySpec(byteArray, "AES"), new IvParameterSpec(IOUtils.byteArray(16)));
			} else { // SEED 128
				cipher = Cipher.getInstance("SEED/ECB/ISO7816-4Padding");
				cipher.init(opmode, new SecretKeySpec(byteArray, "SEED"));
			}
			map.put(str, cipher);
		}
		return cipher;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// Bouncy Castle //////////////////////////////////////////////////////////
		Provider provider = Security.getProvider("BC");
		if (null == provider) {
			Security.addProvider(new BouncyCastleProvider());
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// 볼트키초기화작업 ///////////////////////////////////////////////////////
		log.debug("afterPropertiesSet");
		systemProperties.getVaultKey().entrySet().forEach(entry -> {
			log.debug("{}", entry);
			byte[] byteArray = null;
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 볼트키암호화키복호화 ///////////////////////////////////////////////
			byteArray = Base64.decodeBase64(entry.getValue());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			vKeyMap.put(entry.getKey(), byteArray);
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
			log.error("encB64Str", t);
		}
		return enc;
	}

	public String decB64Str(String key, String str) {
		String dec = str;
		try {
			dec = IOUtils.toString(newCipher(Cipher.DECRYPT_MODE, key).doFinal(Base64.decodeBase64(str)), "EUC-KR");
		} catch (Throwable t) {
			log.error("decB64Str {}, {}", key, str);
			log.error("decB64Str", t);
		}
		return dec;
	}

	public String encHexStr(String key, String str) {
		String enc = str;
		try {
			enc = Hex.encodeHexString(newCipher(Cipher.ENCRYPT_MODE, key).doFinal(StringUtils.getBytes(str, "EUC-KR")));
		} catch (Throwable t) {
			log.error("encHexStr {}, {}", key, str);
			log.error("encHexStr", t);
		}
		return enc;
	}

	public String decHexStr(String key, String str) {
		String dec = str;
		try {
			dec = IOUtils.toString(newCipher(Cipher.DECRYPT_MODE, key).doFinal(Hex.decodeHex(str)), "EUC-KR");
		} catch (Throwable t) {
			log.error("decHexStr {}, {}", key, str);
			log.error("decHexStr", t);
		}
		return dec;
	}

}
