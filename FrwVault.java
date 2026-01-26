package com.jpmc.kcg.frw;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.DefaultBufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.SEEDEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
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
	private ThreadLocal<Map<String, BufferedBlockCipher>> threadLocal = ThreadLocal.withInitial(TreeMap::new);
	private BufferedBlockCipher newBufferedBlockCipher(boolean forEncryption, String key) throws GeneralSecurityException {
		String str = StringUtils.join(String.valueOf(BooleanUtils.toInteger(forEncryption, Cipher.ENCRYPT_MODE, Cipher.DECRYPT_MODE)), key);
		Map<String, BufferedBlockCipher> map = threadLocal.get();
		BlockCipher blockCipher = CBCBlockCipher.newInstance(AESEngine.newInstance());
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////// SEED ///////////////////////////////////////////////////////////////////
		if (Strings.CS.startsWith(key, "S")) {
			blockCipher = CBCBlockCipher.newInstance(new SEEDEngine());
		}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
		BufferedBlockCipher bufferedBlockCipher = map.get(str);
		if (null == bufferedBlockCipher) {
			bufferedBlockCipher = new DefaultBufferedBlockCipher(blockCipher);
			Entry<byte[], byte[]> entry = vKeyMap.get(key);
//			bufferedBlockCipher.init(forEncryption, new KeyParameter(entry.getKey()));
			bufferedBlockCipher.init(forEncryption, new ParametersWithIV(new KeyParameter(entry.getKey()), entry.getValue()));
			map.put(str, bufferedBlockCipher);
		}
		return bufferedBlockCipher;
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
			BufferedBlockCipher bufferedBlockCipher = newBufferedBlockCipher(true, key);
			byte[] in = StringUtils.getBytes(str, "EUC-KR");
			byte[] out = IOUtils.byteArray(bufferedBlockCipher.getOutputSize(in.length));
			int outOff = bufferedBlockCipher.processBytes(in, 0, in.length, out, 0);
			bufferedBlockCipher.doFinal(out, outOff);
			enc = Base64.encodeBase64String(out);
		} catch (Throwable t) {
			log.error("encB64Str {}, {}", key, str);
		}
		return enc;
	}

	public String decB64Str(String key, String str) {
		String dec = str;
		try {
			BufferedBlockCipher bufferedBlockCipher = newBufferedBlockCipher(false, key);
			byte[] in = Base64.decodeBase64(str);
			byte[] out = IOUtils.byteArray(bufferedBlockCipher.getOutputSize(in.length));
			int outOff = bufferedBlockCipher.processBytes(in, 0, in.length, out, 0);
			bufferedBlockCipher.doFinal(out, outOff);
			dec = IOUtils.toString(out, "EUC-KR");
		} catch (Throwable t) {
			log.error("decB64Str {}, {}", key, str);
		}
		return dec;
	}

	public String encHexStr(String key, String str) {
		String enc = str;
		try {
			BufferedBlockCipher bufferedBlockCipher = newBufferedBlockCipher(true, key);
			byte[] in = StringUtils.getBytes(str, "EUC-KR");
			byte[] out = IOUtils.byteArray(bufferedBlockCipher.getOutputSize(in.length));
			int outOff = bufferedBlockCipher.processBytes(in, 0, in.length, out, 0);
			bufferedBlockCipher.doFinal(out, outOff);
			enc = Hex.encodeHexString(out);
		} catch (Throwable t) {
			log.error("encHexStr {}, {}", key, str);
		}
		return enc;
	}

	public String decHexStr(String key, String str) {
		String dec = str;
		try {
			BufferedBlockCipher bufferedBlockCipher = newBufferedBlockCipher(false, key);
			byte[] in = Hex.decodeHex(str);
			byte[] out = IOUtils.byteArray(bufferedBlockCipher.getOutputSize(in.length));
			int outOff = bufferedBlockCipher.processBytes(in, 0, in.length, out, 0);
			bufferedBlockCipher.doFinal(out, outOff);
			dec = IOUtils.toString(out, "EUC-KR");
		} catch (Throwable t) {
			log.error("decHexStr {}, {}", key, str);
		}
		return dec;
	}

}
