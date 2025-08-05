package com.jpmc.kcg.com.utils;

import org.apache.commons.lang3.StringUtils;

import com.jpmc.kcg.frw.FrwDestination;

public class ComUtils {

	public static String getSubByteString(String str, int byteSize) {
		String rtnStr = str;
		byte[] src = str.getBytes();
		
		if (src.length > byteSize) {
			int tByteSize = byteSize + 1;
			byte[] trgt = new byte[tByteSize];
			System.arraycopy(src, 0, trgt, 0, tByteSize);
			String result = new String(trgt);
			rtnStr = result.substring(0, result.length() -1);
		}
		
		return rtnStr;
	}
	
	public static String subErrorContents(Throwable throwable) {
    	Throwable rootCause = getRootCause(throwable);
        if (null != rootCause) {
        	StringBuilder sb = new StringBuilder();
    		sb.append(rootCause.toString()).append(System.lineSeparator());
    		Throwable thr = rootCause;
    		if (null != rootCause.getCause()) {
    			thr = rootCause.getCause();
    		}

    		for(StackTraceElement item : thr.getStackTrace()) {
    			sb.append(item.toString()).append(System.lineSeparator());
    		}
    		return getSubByteString(sb.toString(), 4000);
    	}
        return "";
    }
    
	public static Throwable getRootCause(Throwable throwable) {
    	Throwable sThrowable = throwable;
    	if (null != throwable.getCause()) {
    		sThrowable = getRootCause(throwable.getCause());
    	}
    	return sThrowable;
    }

	public static String getFldErrTlg(String tlgCtt, int fldNo) {
		StringBuilder sb = new StringBuilder(tlgCtt);
		String msgType = StringUtils.left(tlgCtt, 6);
		sb.replace( 0,  3, StringUtils.right(msgType, 3));
		sb.replace( 3,  6, StringUtils.left (msgType, 3));
		sb.setCharAt(30, '1');
		sb.replace(38, 41, StringUtils.leftPad(String.valueOf(fldNo + 1), 3, '0'));
		return sb.toString();
	}

}
